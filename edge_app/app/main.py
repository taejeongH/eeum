
import os
import sys
import time
import logging
import threading
from typing import Optional, Dict, Any

import torch
from fastapi import FastAPI, Query
from fastapi.responses import StreamingResponse, JSONResponse
from ultralytics import YOLO

from .config import (
    MODEL_PATH, MODEL_IOU, MODEL_DET, DETERMINISTIC, 
    RUNS_DIR, DEVICE_ID, LOCATION_ID
)
from .core.controller import AppController
from .core.streamer import WebSocketStreamer


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


if DETERMINISTIC:
    torch.manual_seed(0)
    torch.cuda.manual_seed_all(0)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False


def create_yolo_model():
    logger.info(f"Loading YOLO model from {MODEL_PATH}")
    model = YOLO(MODEL_PATH)
    model.iou = MODEL_IOU
    model.max_det = MODEL_DET
    return model

from .core.controller import AppController
from .core.streamer import WebSocketStreamer


controller = AppController(model_factory=create_yolo_model)
streamer = WebSocketStreamer(controller=controller)
app = FastAPI()


@app.on_event("startup")
async def startup_event():
    logger.info("[STARTUP] Starting Application Controller and Streamer")
    controller.start()
    streamer.start()

@app.on_event("shutdown")
async def shutdown_event():
    logger.info("[SHUTDOWN] Stopping Application Controller and Streamer")
    streamer.stop()
    controller.stop()



@app.get("/health")
def health():
    return {
        "status": "ok",
        **controller.get_status()
    }

@app.get("/level1/status")
def level1_status():
    
    
    st = controller.get_status()
    
    
    return st

@app.post("/mode/live")
def set_mode_live():
    
    return {"status": "auto_mode_active", "info": "모드는 자동으로 선택됩니다 (QR 미등록 / Live 등록)"}

@app.post("/mode/replay")
def set_mode_replay():
    
    
    return {"status": "use /replay/start to enter replay mode"}

@app.post("/record/start")
def record_start():
    path = controller.start_recording()
    return {"recording": True, "path": path}

@app.post("/record/stop")
def record_stop():
    path = controller.stop_recording()
    return {"recording": False, "path": path}

@app.post("/replay/start")
def replay_start(path: str = Query(...), fps: float = Query(15.0)):
    if not os.path.exists(path):
        return JSONResponse(status_code=404, content={"error": "file not found", "path": path})
    
    success = controller.start_replay(path, fps)
    if not success:
         return JSONResponse(status_code=409, content={"error": "replay already running"})
         
    return {"replay": True, "path": path, "fps": fps}

@app.post("/replay/stop")
def replay_stop():
    stopped = controller.stop_replay()
    return {"replay": False, "stopped": stopped}

@app.get("/pose")
def pose():
    obs = controller.get_latest_obs()
    if obs is None:
        return JSONResponse(status_code=503, content={"error": "no observation yet"})
    return obs

@app.get("/api/iot/device/falls/stream_overlay")
def stream():
    boundary = "frame"

    def gen():
        last_count = 0
        while True:
            
            jpg, current_count = controller.wait_for_overlay_frame(last_count, timeout=1.0)
            
            if jpg is None:
                continue
            
            last_count = current_count

            yield (
                b"--" + boundary.encode() + b"\r\n"
                b"Content-Type: image/jpeg\r\n"
                b"Content-Length: " + str(len(jpg)).encode() + b"\r\n\r\n"
                + jpg + b"\r\n"
            )

    return StreamingResponse(gen(), media_type="multipart/x-mixed-replace; boundary=frame")


@app.get("/api/iot/device/falls/stream")
def stream_raw():
    boundary = "frame"
    def gen():
        last = 0
        while True:
            jpg, last = controller.wait_for_raw_frame(last, timeout=1.0)
            if jpg is None:
                continue
            yield (
                b"--" + boundary.encode() + b"\r\n"
                b"Content-Type: image/jpeg\r\n"
                b"Content-Length: " + str(len(jpg)).encode() + b"\r\n\r\n"
                + jpg + b"\r\n"
            )
    return StreamingResponse(gen(), media_type="multipart/x-mixed-replace; boundary=frame")
