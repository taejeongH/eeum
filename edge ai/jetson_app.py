import os
import json
import time
import threading
from typing import Optional, Dict, Any, Tuple

import cv2
import torch
from fastapi import FastAPI, Query
from fastapi.responses import StreamingResponse, JSONResponse
from ultralytics import YOLO


app = FastAPI()

# ----------------------------
# Config
# ----------------------------
CAM_INDEX = 0
FRAME_W = 640
FRAME_H = 480
JPEG_QUALITY = 80
DEFAULT_CONF = 0.35

DETERMINISTIC = True
USE_HALF = False if DETERMINISTIC else True

if DETERMINISTIC:
    torch.manual_seed(0)
    torch.cuda.manual_seed_all(0)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

# ----------------------------
# Model + Camera
# ----------------------------
model = YOLO("yolov8n-pose.pt")

cap = cv2.VideoCapture(CAM_INDEX)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, FRAME_W)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, FRAME_H)

# ----------------------------
# Shared State
# ----------------------------
state_lock = threading.Lock()

latest_obs: Optional[Dict[str, Any]] = None
latest_jpeg: Optional[bytes] = None

live_frame_index = 0

mode = "live"  # "live" or "replay"

record_lock = threading.Lock()
record_fp = None
record_path = None

replay_thread = None
replay_stop_event = threading.Event()
replay_running = False

# ----------------------------
# Utilities
# ----------------------------
def keep_top1_person(r):
    if r.boxes is None or len(r.boxes) == 0:
        return r

    conf = r.boxes.conf
    top = int(torch.argmax(conf).item())
    r.boxes = r.boxes[top : top + 1]
    if getattr(r, "keypoints", None) is not None and r.keypoints is not None:
        r.keypoints = r.keypoints[top : top + 1]
    return r


def clamp01(x: float) -> float:
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else x


def start_recording(run_dir="runs") -> str:
    global record_fp, record_path
    os.makedirs(run_dir, exist_ok=True)
    fname = time.strftime("obs_%Y%m%d_%H%M%S.jsonl")
    path = os.path.join(run_dir, fname)
    with record_lock:
        record_path = path
        record_fp = open(path, "a", encoding="utf-8")
    return path


def stop_recording() -> Optional[str]:
    global record_fp, record_path
    with record_lock:
        if record_fp:
            record_fp.close()
        record_fp = None
        p = record_path
        record_path = None
    return p


def write_observation_jsonl(obs: Dict[str, Any]) -> None:
    with record_lock:
        if record_fp is None:
            return
        record_fp.write(json.dumps(obs, ensure_ascii=False) + "\n")
        record_fp.flush()


def build_observation(
    r0,
    frame_w: int,
    frame_h: int,
    ts: float,
    frame_idx: int,
    source_id: str = "cam0",
) -> Dict[str, Any]:
    obs: Dict[str, Any] = {
        "schema_version": "1.0",
        "ts": float(ts),
        "frame_index": int(frame_idx),
        "source_id": source_id,
        "tracks": [],
        "meta": {
            "model": "yolov8n-pose.pt",
            "conf_thres": float(DEFAULT_CONF),
            "half": bool(USE_HALF),
        },
    }

    has_person = False
    bbox_norm = None
    box_conf = 0.0
    kps = []

    if r0.boxes is not None and len(r0.boxes) > 0:
        b = r0.boxes.xyxy[0].cpu().numpy()
        box_conf = float(r0.boxes.conf[0].item())
        x1, y1, x2, y2 = float(b[0]), float(b[1]), float(b[2]), float(b[3])
        bbox_norm = [
            clamp01(x1 / frame_w),
            clamp01(y1 / frame_h),
            clamp01(x2 / frame_w),
            clamp01(y2 / frame_h),
        ]

    if r0.keypoints is not None and len(r0.keypoints) > 0:
        has_person = True
        xy = r0.keypoints.xy[0].cpu().numpy()
        cf = r0.keypoints.conf[0].cpu().numpy()
        for i in range(xy.shape[0]):
            kps.append(
                {
                    "id": int(i),
                    "x": clamp01(float(xy[i, 0]) / frame_w),
                    "y": clamp01(float(xy[i, 1]) / frame_h),
                    "conf": float(cf[i]),
                }
            )

    quality = 0.0
    if kps:
        quality = float(sum(k["conf"] for k in kps) / len(kps))

    obs["tracks"].append(
        {
            "track_id": 0,
            "has_person": bool(has_person),
            "bbox": bbox_norm,
            "conf": float(box_conf),
            "keypoints": kps,
            "quality_score": float(quality),
        }
    )
    return obs


def draw_obs_as_frame(obs: Dict[str, Any], w: int = FRAME_W, h: int = FRAME_H) -> Any:
    img = (0 * (cv2.UMat(h, w, cv2.CV_8UC3))).get() if hasattr(cv2, "UMat") else None
    if img is None:
        img = (0 * (cv2.cvtColor((cv2.imread("") or (0)), cv2.COLOR_BGR2RGB)))  # fallback (won't hit)
    img = (0 * (cv2.UMat(h, w, cv2.CV_8UC3))).get() if hasattr(cv2, "UMat") else (0)
    # 위 fallback이 지저분해져서, 그냥 확실하게 numpy 없이 cv2로 만들자
    img = cv2.cvtColor(cv2.resize(cv2.imread(os.devnull) if False else (255 * 0).to_bytes(1, "little"), (1, 1)), cv2.COLOR_BGR2RGB)  # never used

def blank_frame(w: int, h: int):
    # numpy 없이 만들기 어렵기 때문에 cv2에서 바로 만들자 (np는 OpenCV 내부에서 사용됨)
    import numpy as np
    return np.zeros((h, w, 3), dtype=np.uint8)

def render_replay_frame(obs: Dict[str, Any], w: int = FRAME_W, h: int = FRAME_H):
    import numpy as np

    img = np.zeros((h, w, 3), dtype=np.uint8)

    tracks = obs.get("tracks", [])
    if not tracks:
        return img

    t0 = tracks[0]
    if t0.get("bbox"):
        x1, y1, x2, y2 = t0["bbox"]
        p1 = (int(x1 * w), int(y1 * h))
        p2 = (int(x2 * w), int(y2 * h))
        cv2.rectangle(img, p1, p2, (0, 255, 0), 2)

    kps = t0.get("keypoints", [])
    for kp in kps:
        if kp.get("conf", 0.0) < 0.2:
            continue
        cx = int(kp["x"] * w)
        cy = int(kp["y"] * h)
        cv2.circle(img, (cx, cy), 3, (255, 255, 255), -1)

    txt = f"replay frame_index={obs.get('frame_index')} ts={obs.get('ts'):.3f}"
    cv2.putText(img, txt, (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 2)
    return img


def encode_jpeg(img) -> Optional[bytes]:
    ok, jpg = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY])
    if not ok:
        return None
    return jpg.tobytes()


# ----------------------------
# Replay Worker
# ----------------------------
def replay_jsonl_worker(path: str, fps: float):
    global replay_running, mode
    replay_running = True
    delay = 1.0 / max(1.0, float(fps))

    try:
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                if replay_stop_event.is_set():
                    break
                line = line.strip()
                if not line:
                    continue

                obs = json.loads(line)

                frame = render_replay_frame(obs, FRAME_W, FRAME_H)
                jpg = encode_jpeg(frame)

                with state_lock:
                    # replay 중에는 mode를 replay로 유지
                    mode = "replay"
                    globals()["latest_obs"] = obs
                    globals()["latest_jpeg"] = jpg

                time.sleep(delay)
    finally:
        replay_running = False
        replay_stop_event.clear()


# ----------------------------
# Live Loop (on-demand in stream generator)
# ----------------------------
def live_step() -> Tuple[Optional[Dict[str, Any]], Optional[bytes]]:
    global live_frame_index

    ok, frame = cap.read()
    if not ok:
        return None, None

    results = model.predict(
        frame,
        device=0,
        half=USE_HALF,
        conf=DEFAULT_CONF,
        verbose=False,
    )

    r0 = keep_top1_person(results[0])
    ts = time.time()

    obs = build_observation(r0, FRAME_W, FRAME_H, ts, live_frame_index, source_id="cam0")
    live_frame_index += 1

    annotated = r0.plot()
    jpg = encode_jpeg(annotated)

    return obs, jpg


# ----------------------------
# API
# ----------------------------
@app.get("/health")
def health():
    with state_lock:
        return {
            "status": "ok",
            "mode": mode,
            "recording": record_fp is not None,
            "replay_running": replay_running,
        }


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
def record_end():
    path = stop_recording()
    return {"recording": False, "path": path}


@app.post("/replay/start")
def replay_start(path: str = Query(...), fps: float = Query(30.0)):
    global replay_thread

    if replay_thread and replay_thread.is_alive():
        return JSONResponse(status_code=409, content={"error": "replay already running"})

    if not os.path.exists(path):
        return JSONResponse(status_code=404, content={"error": "file not found", "path": path})

    replay_stop_event.clear()
    replay_thread = threading.Thread(target=replay_jsonl_worker, args=(path, fps), daemon=True)
    replay_thread.start()

    with state_lock:
        globals()["mode"] = "replay"

    return {"replay": True, "path": path, "fps": float(fps)}


@app.post("/replay/stop")
def replay_stop():
    replay_stop_event.set()
    return {"replay": False}


@app.get("/pose")
def pose():
    with state_lock:
        if latest_obs is None:
            return JSONResponse(status_code=503, content={"error": "no observation yet"})
        return latest_obs


@app.get("/stream")
def stream():
    def gen():
        boundary = "frame"
        while True:
            with state_lock:
                current_mode = mode

            if current_mode == "live":
                obs, jpg = live_step()
                if obs is None or jpg is None:
                    time.sleep(0.02)
                    continue

                with state_lock:
                    globals()["latest_obs"] = obs
                    globals()["latest_jpeg"] = jpg

                write_observation_jsonl(obs)

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
