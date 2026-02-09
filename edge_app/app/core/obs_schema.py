from typing import Dict, Any, List, Optional

from ..config import DEFAULT_CONF

def clamp01(x: float) -> float:
    """0과 1 사이로 값 제한"""
    return 0.0 if x < 0.0 else 1.0 if x > 1.0 else x

def build_observation(
    r0,
    frame_w: int,
    frame_h: int,
    ts: float,
    frame_idx: int,
    source_id: str = "cam0",
) -> Dict[str, Any]:
    """YOLO 결과에서 관측 객체 구축"""
    obs: Dict[str, Any] = {
        "schema_version": "1.0",
        "ts": float(ts),
        "frame_index": int(frame_idx),
        "source_id": source_id,
        "tracks": [],
        "meta": {
            "conf_thres": float(DEFAULT_CONF),
        },
    }

    has_person = False
    bbox_norm: Optional[List[float]] = None
    box_conf = 0.0
    kps: List[Dict[str, Any]] = []

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
            "keypoints_raw": kps,     
            "keypoints_smooth": None, 
            "keypoints": kps,         
            "quality_score": float(quality),
            "frame_shape": (frame_h, frame_w),  
        }
    )
    return obs
