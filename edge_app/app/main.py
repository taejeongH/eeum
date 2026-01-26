import os
import time
import threading
from typing import Optional, Dict, Any

import cv2
import torch
from fastapi import FastAPI, Query
from fastapi.responses import StreamingResponse, JSONResponse
from ultralytics import YOLO

from .config import (
    CAM_INDEX, FRAME_W, FRAME_H, JPEG_QUALITY,
    DETERMINISTIC, MODEL_PATH, RUNS_DIR, 
    DEVICE_ID, LOCATION_ID, SERVER_URL, RPI_URL
)
from .level1 import Level1Params, Level1Engine
from .clip_recorder import ClipRecorder
from .replay import start_replay_thread
from .live import LivePipeline
from .notifier import Notifier

app = FastAPI()

# ---------- deterministic 옵션 ----------
if DETERMINISTIC:
    torch.manual_seed(0)
    torch.cuda.manual_seed_all(0)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

# ---------- model/camera ----------
model = YOLO(MODEL_PATH)

cap = cv2.VideoCapture(CAM_INDEX)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_W)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_H)

live = LivePipeline(model=model, cap=cap, jpeg_quality=JPEG_QUALITY, source_id="cam0")

# ---------- shared state ----------
state_lock = threading.Lock()
latest_obs: Optional[Dict[str, Any]] = None
latest_jpeg: Optional[bytes] = None
mode = "live"  # live/replay

# ---------- jsonl recording ----------
record_lock = threading.Lock()
record_fp = None
record_path = None

notifier = Notifier(server_url=SERVER_URL, rpi_url=RPI_URL)
current_event_id = None
current_event_detected_at = None

incident_ts = None
incident_event_id = None

def start_recording():
    global record_fp, record_path
    os.makedirs(RUNS_DIR, exist_ok=True)
    fname = time.strftime("obs_%Y%m%d_%H%M%S.jsonl")
    record_path = os.path.join(RUNS_DIR, fname)
    with record_lock:
        record_fp = open(record_path, "a", encoding="utf-8")
    return record_path

def stop_recording():
    global record_fp, record_path
    with record_lock:
        if record_fp:
            record_fp.close()
        record_fp = None
        p = record_path
        record_path = None
    return p

def write_obs(obs: Dict[str, Any]):
    with record_lock:
        if record_fp is None:
            return
        import json
        record_fp.write(json.dumps(obs, ensure_ascii=False) + "\n")
        record_fp.flush()

# ---------- level1 + clip ----------
level1_engine = Level1Engine(Level1Params())
clip_recorder = ClipRecorder()
last_level1_event: Optional[Dict[str, Any]] = None

# ---------- replay ----------
replay_stop_event = threading.Event()
replay_thread = None
replay_running = False

def on_replay_frame(obs: Dict[str, Any], jpg: bytes):
    global latest_obs, latest_jpeg, mode
    with state_lock:
        mode = "replay"
        latest_obs = obs
        latest_jpeg = jpg

# ---------------- API ----------------
@app.get("/health")
def health():
    with state_lock:
        st = clip_recorder.status()
        return {
            "status": "ok",
            "mode": mode,
            "recording_jsonl": record_fp is not None,
            "replay_running": replay_running,
            **st,
        }

@app.get("/level1/status")
def level1_status():
    st = clip_recorder.status()
    return {"last_level1_event": last_level1_event, **st}

@app.post("/mode/live")
def set_mode_live():
    global mode
    with state_lock:
        mode = "live"
    return {"mode": "live"}

@app.post("/mode/replay")
def set_mode_replay():
    global mode
    with state_lock:
        mode = "replay"
    return {"mode": "replay"}

@app.post("/record/start")
def record_start():
    path = start_recording()
    return {"recording": True, "path": path}

@app.post("/record/stop")
def record_stop():
    path = stop_recording()
    return {"recording": False, "path": path}

@app.post("/replay/start")
def replay_start(path: str = Query(...), fps: float = Query(15.0)):
    global replay_thread, replay_running, mode

    if replay_thread and replay_thread.is_alive():
        return JSONResponse(status_code=409, content={"error": "replay already running"})

    if not os.path.exists(path):
        return JSONResponse(status_code=404, content={"error": "file not found", "path": path})

    replay_stop_event.clear()
    replay_thread = start_replay_thread(
        path=path,
        fps=fps,
        jpeg_quality=JPEG_QUALITY,
        stop_event=replay_stop_event,
        on_frame=on_replay_frame
    )
    replay_running = True

    with state_lock:
        mode = "replay"
    return {"replay": True, "path": path, "fps": float(fps)}

@app.post("/replay/stop")
def replay_stop():
    global replay_running
    replay_stop_event.set()
    replay_running = False
    return {"replay": False}

@app.get("/pose")
def pose():
    with state_lock:
        if latest_obs is None:
            return JSONResponse(status_code=503, content={"error": "no observation yet"})
        return latest_obs

from fastapi import Query

@app.get("/stream")
def stream(overlay: str = Query("smooth", pattern="^(raw|smooth|both)$")):
    def gen():
        boundary = "frame"
        global latest_obs, latest_jpeg, mode, last_level1_event
        global incident_ts, incident_event_id

        while True:
            with state_lock:
                current_mode = mode

            if current_mode == "live":
                obs, jpg, frame = live.step(overlay=overlay)
                if obs is None or jpg is None or frame is None:
                    time.sleep(0.02)
                    continue

                ts_now = float(obs["ts"])

                # 링버퍼 업데이트(클립 저장용)
                clip_recorder.push(ts_now, frame)

                # level1 이벤트 체크
                ev = level1_engine.step(obs)
                if ev is not None:
                    print(
                        f"[LEVEL EVT] {ev.get('type')} "
                        f"ts={ev.get('ts'):.2f} "
                        f"frame={ev.get('frame_index')} "
                        f"reason={ev.get('reason', '')}"
                    )

                    et = ev.get("type")

                    # 1) Level0 진입: 사건 시작 시각만 기억 (녹화 X)
                    if et == "abnormal_enter":
                        incident_ts = float(ev.get("ts", ts_now))
                        incident_event_id = f"incident_{time.strftime('%Y%m%d_%H%M%S')}"
                        # (원하면) 여기서 "의심" 단계 서버 전송도 가능하지만 지금은 X

                    # 2) 회복: 사건 폐기 (저장 X)
                    elif et == "abnormal_exit":
                        incident_ts = None
                        incident_event_id = None

                    # 3) Level1 확정: 여기서만 저장 + 전송
                    elif et == "level1":
                        last_level1_event = ev

                        # event_id 정리
                        current_event_id = ev.get("event_id") or f"level1_{time.strftime('%Y%m%d_%H%M%S')}"
                        current_event_detected_at = ts_now
                        ev["event_id"] = current_event_id

                        # (A) 서버/RPi에 level1 이벤트 JSON 전송
                        t0 = (obs.get("tracks") or [{}])[0]
                        payload = {
                            "device_id": DEVICE_ID,
                            "has_person": bool(t0.get("has_person", False)),
                            "location": LOCATION_ID,
                            "detected_at": float(ts_now),
                            "level": 1,
                            "event_id": current_event_id,
                            # "clip_status": "saving",  # 지금은 버퍼에서 저장 중 # 전송할거면 무조건 저장하니까 굳이 상태 표시 X
                        }
                        notifier.send_event(payload)

                        # (B) 사건 시작 시각 기준으로 전후 구간만 저장
                        if incident_ts is not None:
                            # 원하는 전후 길이
                            pre_sec = 6.0
                            post_sec = 7.0

                            clip_path = clip_recorder.save_segment(
                                incident_ts=incident_ts,
                                pre_sec=pre_sec,
                                post_sec=post_sec,
                                filename_prefix="incident"
                            )

                            if clip_path:
                                payload2 = dict(payload)
                                payload2["clip_status"] = "ready"
                                notifier.send_clip(event_id=current_event_id, clip_path=clip_path, payload=payload2)

                        # (C) 사건 정보 초기화(선택)
                        incident_ts = None
                        incident_event_id = None

                # 최신 상태 업데이트
                with state_lock:
                    latest_obs = obs
                    latest_jpeg = jpg

                # JSONL 기록(옵션)
                write_obs(obs)

            else:
                with state_lock:
                    jpg = latest_jpeg
                if jpg is None:
                    time.sleep(0.02)
                    continue

            data = jpg
            yield (
                b"--" + boundary.encode() + b"\r\n"
                b"Content-Type: image/jpeg\r\n"
                b"Content-Length: " + str(len(data)).encode() + b"\r\n\r\n"
                + data + b"\r\n"
            )

    return StreamingResponse(gen(), media_type="multipart/x-mixed-replace; boundary=frame")
