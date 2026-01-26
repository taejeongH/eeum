import json
import time
import threading
from typing import Optional, Dict, Any, Callable

import cv2

from .config import FRAME_W, FRAME_H

def encode_jpeg(img, jpeg_quality: int) -> Optional[bytes]:
    ok, jpg = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), int(jpeg_quality)])
    if not ok:
        return None
    return jpg.tobytes()

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

    for kp in t0.get("keypoints", []):
        if kp.get("conf", 0.0) < 0.2:
            continue
        cx = int(kp["x"] * w)
        cy = int(kp["y"] * h)
        cv2.circle(img, (cx, cy), 3, (255, 255, 255), -1)

    ts = float(obs.get("ts", 0.0))
    txt = f"replay frame_index={obs.get('frame_index')} ts={ts:.3f}"
    cv2.putText(img, txt, (10, 25), cv2.FONT_HERSHEY_SIMPLEX, 0.6, (200, 200, 200), 2)
    return img

def start_replay_thread(
    path: str,
    fps: float,
    jpeg_quality: int,
    stop_event: threading.Event,
    on_frame: Callable[[Dict[str, Any], bytes], None],
):
    """
    JSONL 읽어서 replay 프레임 생성 -> on_frame(obs, jpg_bytes) 콜백으로 전달
    """
    delay = 1.0 / max(1.0, float(fps))

    def worker():
        try:
            with open(path, "r", encoding="utf-8") as f:
                for line in f:
                    if stop_event.is_set():
                        break
                    line = line.strip()
                    if not line:
                        continue
                    obs = json.loads(line)
                    frame = render_replay_frame(obs, FRAME_W, FRAME_H)
                    jpg = encode_jpeg(frame, jpeg_quality)
                    if jpg is None:
                        continue
                    on_frame(obs, jpg)
                    time.sleep(delay)
        finally:
            stop_event.clear()

    t = threading.Thread(target=worker, daemon=True)
    t.start()
    return t
