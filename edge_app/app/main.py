import os
import sys
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
    DEVICE_ID, LOCATION_ID, SERVER_URL, RPI_URL,
    MODEL_IOU, MODEL_DET
)

from .core import Level1Params, Level1Engine, PresenceParams, PresenceEngine, ClipRecorder
from .engine import LivePipeline
from .modes import BaseMode, LiveMode, QRMode
from .state import get_device_state
from .utils import start_replay_thread
from .api.server_client import ServerClient

app = FastAPI()
server_client = ServerClient()

# ---------- deterministic 옵션 ----------
if DETERMINISTIC:
    torch.manual_seed(0)
    torch.cuda.manual_seed_all(0)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

# ---------- model ----------
model = YOLO(MODEL_PATH)

# ---------- model setting ----------
model.iou = MODEL_IOU
model.max_det = MODEL_DET

# ---------- camera ----------
cap: Optional[cv2.VideoCapture] = None  # 전역 단일 카메라 핸들(프로세스당 1개만!)

def open_camera() -> cv2.VideoCapture:
    """OS에 맞는 backend로 카메라 1개를 열어서 반환"""
    cam_device = os.getenv("CAM_DEVICE", "").strip()  # 예: /dev/video1
    cam_index = int(os.getenv("CAM_INDEX", str(CAM_INDEX)))

    if sys.platform.startswith("win"):
        c = cv2.VideoCapture(cam_index, cv2.CAP_DSHOW)
        if not c.isOpened():
            c.release()
            c = cv2.VideoCapture(cam_index, cv2.CAP_MSMF)
    else:
        if cam_device:
            c = cv2.VideoCapture(cam_device, cv2.CAP_V4L2)
        else:
            c = cv2.VideoCapture(cam_index, cv2.CAP_V4L2)

    # 설정
    c.set(cv2.CAP_PROP_FPS, 15)
    c.set(cv2.CAP_PROP_BUFFERSIZE, 1)
    c.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_W)
    c.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_H)
    return c

def ensure_camera_opened() -> bool:
    """cap이 열려있도록 보장. 열기 실패 시 False."""
    global cap
    if cap is not None and cap.isOpened():
        return True
    if cap is not None:
        try:
            cap.release()
        except Exception:
            pass
    cap = open_camera()
    print("[CAM] platform=", sys.platform, "CAM_DEVICE=", os.getenv("CAM_DEVICE"), "CAM_INDEX=", os.getenv("CAM_INDEX", CAM_INDEX))
    print("[CAM] opened=", cap.isOpened())
    return cap.isOpened()

# LivePipeline은 모드에 따라 생성됨
live = None  # type: Optional[LivePipeline]

# ---------- shared state ----------
state_lock = threading.Lock()
latest_obs: Optional[Dict[str, Any]] = None
latest_jpeg: Optional[bytes] = None
processing_mode: str = "initial"  # initial/qr/live/replay
current_mode_instance: Optional[BaseMode] = None
mode_stop_event = threading.Event()

# jsonl recording ----------
record_lock = threading.Lock()
record_fp = None
record_path = None

def configure_camera_for_mode(mode_name: str):
    """모드별 카메라 설정을 강제한다."""
    global cap
    if cap is None:
        return

    if mode_name == "qr":
        w, h = 640, 480
    else:
        w, h = FRAME_W, FRAME_H

    cap.set(cv2.CAP_PROP_FPS, 15)
    cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
    cap.set(cv2.CAP_PROP_FRAME_WIDTH, int(w))
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, int(h))

    # 일부 장치는 set이 무시되기도 해서 실제값 로그 남기기
    aw = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
    ah = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
    print(f"[CAM] mode={mode_name} requested=({w}x{h}) actual=({aw}x{ah})")


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
presence_engine = PresenceEngine(PresenceParams(
    enter_hits=5,
    exit_hits=10,
    min_quality=0.10,
    cool_down_s=0.5,
))
level1_engine = Level1Engine(Level1Params())
clip_recorder = ClipRecorder()
last_level1_event: Optional[Dict[str, Any]] = None

# ---------- mode dispatcher & background processing ----------

def initialize_mode():
    """시작 시 장치 등록 상태에 따라 QRMode 또는 LiveMode 초기화 (카메라는 재사용)"""
    global processing_mode, current_mode_instance, live, cap

    device_state = get_device_state()

    # Windows 테스트용: 임시 등록 상태 생성
    if sys.platform == "win32" and not device_state.is_registered():
        print("[TEST] Windows environment - Creating temporary registration")
        device_state.register(
            device_id="TEST_DEVICE_001",
            access_token="test_access_token_xxx",
            refresh_token="test_refresh_token_xxx",
            group_id=1,
            serial_number="TEST-001"
        )

    if not ensure_camera_opened():
        # 카메라가 열리지 않으면 QR/LIVE 어느 것도 진행 불가
        print("[ERROR] Camera not opened. Check device mapping/permissions.")
        time.sleep(1.0)

    is_registered = device_state.is_registered()

    qrmode_available = QRMode is not None
    print(f"[DEBUG] QRMode available: {qrmode_available}, Device registered: {is_registered}")

    # QRMode 사용 가능 + 미등록: QRMode
    if qrmode_available and not is_registered:
        print("[MODE] Device not registered - Starting QRMode")
        configure_camera_for_mode("qr")
        mode = QRMode(cap=cap, jpeg_quality=JPEG_QUALITY)
        processing_mode = "qr"
    else:
        device_id = device_state.get_device_id() or "UNKNOWN"
        reason = "device registered" if is_registered else "QRMode not available"
        print(f"[MODE] Starting LiveMode (device_id={device_id}, reason={reason})")
        configure_camera_for_mode("live")
        mode = LiveMode(model=model, cap=cap, jpeg_quality=JPEG_QUALITY)
        processing_mode = "live"

    current_mode_instance = mode
    live = None
    return mode

def mode_processing_loop():
    global processing_mode, current_mode_instance, latest_obs, latest_jpeg, live

    local_incident_ts = None
    local_last_level1_event = None

    device_state = get_device_state()

    try:
        current_mode = initialize_mode()
        current_mode.setup()
        print(f"[LOOP] Mode processing loop started (mode={processing_mode})")

        while not mode_stop_event.is_set():
            # QRMode에서 등록되면 LiveMode로 전환(카메라 재사용)
            if processing_mode == "qr" and device_state.is_registered():
                print("[MODE] Device now registered - Switching to LiveMode")
                current_mode.cleanup()

                configure_camera_for_mode("live")
                current_mode = LiveMode(model=model, cap=cap, jpeg_quality=JPEG_QUALITY)
                current_mode.setup()
                processing_mode = "live"
                local_incident_ts = None
                local_last_level1_event = None

            try:
                obs, jpg, frame = current_mode.step()
            except Exception as e:
                print(f"[ERROR] Mode step failed: {e}")
                time.sleep(0.2)
                continue

            if obs is None or jpg is None:
                # QR/LIVE 어느 모드든 최신 jpg가 없으면 스트림이 멈추니,
                # 프레임만이라도 있으면 여기서 JPEG로 만들어 올려준다.
                if frame is not None:
                    ok, enc = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), int(JPEG_QUALITY)])
                    if ok:
                        with state_lock:
                            latest_obs = obs if obs is not None else latest_obs
                            latest_jpeg = enc.tobytes()
                time.sleep(0.02)
                continue

            if processing_mode == "qr":
                # QR 가이드 오버레이(필요하면 끄면 됨)
                if frame is not None:
                    h, w = frame.shape[:2]
                    # 중앙 스캔 박스
                    box_w, box_h = int(w * 0.6), int(h * 0.6)
                    x1 = (w - box_w) // 2
                    y1 = (h - box_h) // 2
                    x2 = x1 + box_w
                    y2 = y1 + box_h
                    cv2.rectangle(frame, (x1, y1), (x2, y2), (0, 255, 0), 2)
                    cv2.putText(frame, "Scan QR to register", (20, 30),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)

                    ok, enc = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), int(JPEG_QUALITY)])
                    if ok:
                        jpg = enc.tobytes()

                with state_lock:
                    latest_obs = obs
                    latest_jpeg = jpg

            elif processing_mode == "live":
                ts_now = float(obs["ts"])

                pe = presence_engine.step(obs)
                if pe is not None:
                    ptype = pe["data"]["event"]
                    event_name = "enter" if ptype == "enter" else "exit"
                    payload = {
                        "kind": "vision",
                        "device_id": DEVICE_ID,
                        "data": {
                            "location_id": LOCATION_ID,
                            "event": event_name
                        },
                        "ts": float(ts_now),
                    }
                    server_client.send_event_rpi(payload)

                clip_recorder.push(ts_now, frame)

                ev = level1_engine.step(obs)
                if ev is not None:
                    print(
                        f"[LEVEL EVT] {ev.get('type')} "
                        f"ts={ev.get('ts'):.2f} "
                        f"frame={ev.get('frame_index')} "
                        f"reason={ev.get('reason', '')}"
                    )
                    et = ev.get("type")

                    if et == "abnormal_enter":
                        local_incident_ts = float(ev.get("ts", ts_now))

                    elif et == "abnormal_exit":
                        local_incident_ts = None

                    elif et == "level1":
                        device_state = get_device_state()
                        
                        # 토큰 유효성 확인 및 갱신
                        access_token = device_state.ensure_valid_token(server_client)
                        if not access_token:
                             print("[ERROR] Token invalid and refresh failed. Cannot send fall event.")
                             # 토큰이 없으면 전송 불가, 로깅만 하고 스킵? 
                             # 그래도 RPI에는 보낼 수 있으면 보내야 할까? 
                             # 일단 서버 전송은 실패하더라도 다음 로직 진행
                             access_token = ""

                        local_last_level1_event = ev

                        current_event_id = ev.get("event_id") or f"level1_{time.strftime('%Y%m%d_%H%M%S')}"
                        ev["event_id"] = current_event_id

                        t0 = (obs.get("tracks") or [{}])[0]
                        payload = {
                            "kind": "fall",
                            "device_id": DEVICE_ID,
                            "detected_at": float(ts_now),
                            "data": {
                                "location_id": LOCATION_ID,
                                "event": "fall_detected",
                                "level": 1,
                                "has_person": bool(t0.get("has_person", False)),
                                "confidence": ev.get("values", {}).get("confidence_score", 0.0),
                            },
                        }
                        try:
                            server_client.send_event_rpi(payload)
                        except Exception as e:
                            print(f"Failed to send rpi: {e}")

                        presigned_url, video_path = "", ""
                        try:
                            presigned_url, video_path = server_client.send_event_server(payload, access_token=access_token)
                        except Exception as e:
                            print(f"Failed to send server: {e}")

                        if local_incident_ts is not None:
                            clip_path = clip_recorder.save_segment(
                                incident_ts=local_incident_ts,
                                pre_sec=7.0,
                                post_sec=4.0,
                                filename_prefix="incident"
                            )
                            if clip_path and presigned_url:
                                # print("[DBG] clip_path =", clip_path, flush=True)
                                # print("[DBG] exists =", os.path.exists(clip_path), "size =", (os.path.getsize(clip_path) if os.path.exists(clip_path) else None), flush=True)
                                # time.sleep(0.2)  # 아주 짧게(파일 시스템 flush 여유)
                                try:
                                    server_client.upload_clip_via_presigned_put(presigned_url=presigned_url, clip_path=clip_path, timeout=120.0)
                                except Exception as e:
                                    print(f"Failed to upload clip: {e}")

                                try:
                                    server_client.send_video_upload_success(video_path=video_path, access_token=access_token)
                                except Exception as e:
                                    print(f"Failed to send video upload success: {e}")

                        local_incident_ts = None

                with state_lock:
                    latest_obs = obs
                    latest_jpeg = jpg

                write_obs(obs)

            time.sleep(0.001)

    finally:
        if current_mode_instance:
            try:
                current_mode_instance.cleanup()
            except Exception:
                pass
        print("[LOOP] Mode processing loop stopped")

# ---------- background thread ----------
mode_thread = None

@app.on_event("startup")
async def startup_event():
    global mode_thread, mode_stop_event
    mode_stop_event.clear()

    # 카메라는 startup에서 딱 한 번만 오픈
    ensure_camera_opened()

    print("[STARTUP] Starting mode processing background thread")
    mode_thread = threading.Thread(target=mode_processing_loop, daemon=True)
    mode_thread.start()

@app.on_event("shutdown")
async def shutdown_event():
    global mode_thread, mode_stop_event, cap

    print("[SHUTDOWN] Stopping mode processing background thread")
    mode_stop_event.set()
    if mode_thread:
        mode_thread.join(timeout=5)

    # 앱 종료 시 cap release
    if cap is not None:
        try:
            cap.release()
        except Exception:
            pass
        cap = None

# ---------- replay ----------
replay_stop_event = threading.Event()
replay_thread = None
replay_running = False

def on_replay_frame(obs: Dict[str, Any], jpg: bytes):
    global latest_obs, latest_jpeg, processing_mode
    with state_lock:
        processing_mode = "replay"
        latest_obs = obs
        latest_jpeg = jpg

# ---------------- API ----------------
@app.get("/health")
def health():
    with state_lock:
        st = clip_recorder.status()
        return {
            "status": "ok",
            "processing_mode": processing_mode,
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
    return {"status": "auto_mode_active", "info": "모드는 자동으로 선택됩니다 (QR 미등록/Live 등록)"}

@app.post("/mode/replay")
def set_mode_replay():
    global processing_mode
    with state_lock:
        processing_mode = "replay"
    return {"processing_mode": "replay"}

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
    global replay_thread, replay_running, processing_mode

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
        processing_mode = "replay"
    return {"replay": True, "path": path, "fps": float(fps)}

@app.post("/replay/stop")
def replay_stop():
    global replay_running, processing_mode
    replay_stop_event.set()
    replay_running = False
    with state_lock:
        processing_mode = "live"
    return {"replay": False}

@app.get("/pose")
def pose():
    with state_lock:
        if latest_obs is None:
            return JSONResponse(status_code=503, content={"error": "no observation yet"})
        return latest_obs

@app.get("/stream")
def stream():
    boundary = "frame"

    def gen():
        while True:
            with state_lock:
                jpg = latest_jpeg

            if jpg is None:
                time.sleep(0.05)
                continue

            yield (
                b"--" + boundary.encode() + b"\r\n"
                b"Content-Type: image/jpeg\r\n"
                b"Content-Length: " + str(len(jpg)).encode() + b"\r\n\r\n"
                + jpg + b"\r\n"
            )

    return StreamingResponse(gen(), media_type="multipart/x-mixed-replace; boundary=frame")
