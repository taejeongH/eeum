import time
from typing import Optional, Dict, Any, Tuple

import cv2
import torch
from ultralytics import YOLO

from ..config import FRAME_W, FRAME_H, DEFAULT_CONF, USE_HALF
from ..core import build_observation
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
    """정규화 좌표 기반 스켈레톤 그리기"""
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

def calculate_iou(box1, box2):
    """
    Calculate Intersection over Union (IoU) of two bounding boxes.
    box format: [x1, y1, x2, y2]
    """
    x1 = max(box1[0], box2[0])
    y1 = max(box1[1], box2[1])
    x2 = min(box1[2], box2[2])
    y2 = min(box1[3], box2[3])

    inter_area = max(0, x2 - x1) * max(0, y2 - y1)
    box1_area = (box1[2] - box1[0]) * (box1[3] - box1[1])
    box2_area = (box2[2] - box2[0]) * (box2[3] - box2[1])

    union_area = box1_area + box2_area - inter_area
    if union_area <= 0:
        return 0.0
    return inter_area / union_area

def select_best_person(results, last_bbox=None, iou_thresh=0.3):
    """
    Select the best person to track.
    Prioritize IoU overlap with last_bbox if available.
    Otherwise, pick the highest confidence detection.
    """
    if results.boxes is None or len(results.boxes) == 0:
        return results

    best_idx = 0
    
    if last_bbox is not None:
        best_iou = -1.0
        # box: xyxy format
        boxes = results.boxes.xyxy.cpu().numpy()
        
        for i, box in enumerate(boxes):
            iou = calculate_iou(last_bbox, box)
            if iou > best_iou:
                best_iou = iou
                best_idx = i
        
        # If overlap is too small, fallback to highest confidence
        if best_iou < iou_thresh:
            best_idx = int(torch.argmax(results.boxes.conf).item())
    else:
        best_idx = int(torch.argmax(results.boxes.conf).item())
        
    results.boxes = results.boxes[best_idx:best_idx + 1]
    if getattr(results, "keypoints", None) is not None and results.keypoints is not None:
        results.keypoints = results.keypoints[best_idx:best_idx + 1]
        
    return results

def encode_jpeg(img, jpeg_quality: int) -> Optional[bytes]:
    ok, jpg = cv2.imencode(".jpg", img, [int(cv2.IMWRITE_JPEG_QUALITY), int(jpeg_quality)])
    if not ok:
        return None
    return jpg.tobytes()

class LivePipeline:
    """
    카메라 -> YOLO -> obs 생성(+EMA) -> annotated jpg 생성
    
    실제 카메라 해상도를 자동으로 감지하고 사용합니다.
    """
    def __init__(self, model: YOLO, cap: cv2.VideoCapture, jpeg_quality: int, source_id: str = "cam0"):
        self.model = model
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.source_id = source_id
        self.frame_index = 0
        
        # 카메라 해상도는 첫 step() 호출 시 감지
        self.actual_w = FRAME_W
        self.actual_h = FRAME_H
        self._first_frame = True
        
        # 추적용 이전 bbox (x1, y1, x2, y2)
        self.last_bbox = None

    def step(self, overlay: str = "smooth") -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any]]:
        ok, frame = self.cap.read()
        if not ok:
            return None, None, None
        
        # 첫 프레임에서 실제 카메라 해상도 감지
        if self._first_frame:
            frame_h, frame_w = frame.shape[:2]
            self.actual_h = frame_h
            self.actual_w = frame_w
            self._first_frame = False
        
        # 프레임이 해상도와 다르면 업데이트
        frame_h, frame_w = frame.shape[:2]
        if frame_w != self.actual_w or frame_h != self.actual_h:
            self.actual_h = frame_h
            self.actual_w = frame_w

        results = self.model.predict(
            frame,
            device=0,
            half=USE_HALF,
            conf=DEFAULT_CONF,
            verbose=False,
        )
        
        # IoU 기반 추적
        r0 = select_best_person(results[0], last_bbox=self.last_bbox)
        
        # update last_bbox
        if r0.boxes is not None and len(r0.boxes) > 0:
            self.last_bbox = r0.boxes.xyxy[0].cpu().numpy()
        else:
            self.last_bbox = None

        ts = time.time()
        # 실제 카메라 해상도 사용
        obs = build_observation(r0, self.actual_w, self.actual_h, ts, self.frame_index, source_id=self.source_id)
        obs = ema_smooth_keypoints_inplace(obs)

        self.frame_index += 1

        # 오버레이 선택: raw(원본) | smooth(스무딩) | both(둘 다)
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
            # raw: 빨강색, smooth: 초록색
            draw_skeleton_norm(annotated, kps_raw, conf_thr=0.3, color=(0, 0, 255))
            draw_skeleton_norm(annotated, kps_smooth, conf_thr=0.3, color=(0, 255, 0))
        else:
            # 기본값: 스무딩
            draw_skeleton_norm(annotated, kps_smooth, conf_thr=0.3, color=(0, 255, 0))

        jpg = encode_jpeg(annotated, self.jpeg_quality)
        if jpg is None:
            return None, None, None

        return obs, jpg, frame
