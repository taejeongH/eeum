import time
from typing import Optional, Dict, Any, Tuple

import cv2
import torch
from ultralytics import YOLO

from .config import FRAME_W, FRAME_H, DEFAULT_CONF, USE_HALF
from .obs_schema import build_observation
from .smoothing import ema_smooth_keypoints_inplace

# EMA smoothing 한 결과를 직접 그리려고 넣은 함수
COCO17_EDGES = [
    (0, 1), (0, 2),
    (1, 3), (2, 4),
    (5, 6),
    (5, 7), (7, 9),
    (6, 8), (8, 10),
    (5, 11), (6, 12),
    (11, 12),
    (11, 13), (13, 15),
    (12, 14), (14, 16),
]

def draw_bbox_norm(frame, bbox_norm, color=(0, 255, 0), thickness=2):
    if not bbox_norm:
        return
    h, w = frame.shape[:2]
    x1, y1, x2, y2 = bbox_norm
    p1 = (int(x1 * w), int(y1 * h))
    p2 = (int(x2 * w), int(y2 * h))
    cv2.rectangle(frame, p1, p2, color, thickness)

def draw_skeleton_norm(frame, keypoints, conf_thr=0.3, edges=COCO17_EDGES,
                       color=(0, 255, 0), pt_radius=3, line_thickness=2):
    if not keypoints:
        return
    h, w = frame.shape[:2]
    pts = {}
    for kp in keypoints:
        c = float(kp.get("conf", 0.0))
        if c < conf_thr:
            continue
        i = int(kp["id"])
        px = int(float(kp["x"]) * w)
        py = int(float(kp["y"]) * h)
        pts[i] = (px, py)
        cv2.circle(frame, (px, py), pt_radius, color, -1)

    for a, b in edges:
        if a in pts and b in pts:
            cv2.line(frame, pts[a], pts[b], color, line_thickness)

def keep_top1_person(r):
    if r.boxes is None or len(r.boxes) == 0:
        return r
    top = int(torch.argmax(r.boxes.conf).item())
    r.boxes = r.boxes[top:top + 1]
    if getattr(r, "keypoints", None) is not None and r.keypoints is not None:
        r.keypoints = r.keypoints[top:top + 1]
    return r

def encode_jpeg(img, jpeg_quality: int) -> Optional[bytes]:
    ok, jpg = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), int(jpeg_quality)])
    if not ok:
        return None
    return jpg.tobytes()

class LivePipeline:
    """
    카메라 -> YOLO -> obs 생성(+EMA) -> annotated jpg 생성
    """
    def __init__(self, model: YOLO, cap: cv2.VideoCapture, jpeg_quality: int, source_id: str = "cam0"):
        self.model = model
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.source_id = source_id
        self.frame_index = 0

    def step(self, overlay: str = "smooth") -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any]]:
        ok, frame = self.cap.read()
        if not ok:
            return None, None, None

        results = self.model.predict(
            frame,
            device=0,
            half=USE_HALF,
            conf=DEFAULT_CONF,
            verbose=False,
        )
        r0 = keep_top1_person(results[0])

        ts = time.time()
        obs = build_observation(r0, FRAME_W, FRAME_H, ts, self.frame_index, source_id=self.source_id)
        obs = ema_smooth_keypoints_inplace(obs)

        self.frame_index += 1

        # overlay 선택: raw | smooth | both
        t0 = (obs.get("tracks") or [{}])[0]
        bbox_norm = t0.get("bbox")

        kps_raw = t0.get("keypoints_raw") or []
        kps_smooth = t0.get("keypoints_smooth") or t0.get("keypoints") or []

        annotated = frame.copy()
        draw_bbox_norm(annotated, bbox_norm)

        ov = (overlay or "smooth").lower().strip()
        if ov == "raw":
            draw_skeleton_norm(annotated, kps_raw, conf_thr=0.3, color=(0, 0, 255))
        elif ov == "both":
            # raw: red, smooth: green
            draw_skeleton_norm(annotated, kps_raw, conf_thr=0.3, color=(0, 0, 255))
            draw_skeleton_norm(annotated, kps_smooth, conf_thr=0.3, color=(0, 255, 0))
        else:
            # default smooth
            draw_skeleton_norm(annotated, kps_smooth, conf_thr=0.3, color=(0, 255, 0))

        jpg = encode_jpeg(annotated, self.jpeg_quality)
        if jpg is None:
            return None, None, None

        return obs, jpg, frame
