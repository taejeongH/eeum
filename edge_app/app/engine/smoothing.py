from typing import Dict, Any, Optional
import math
import logging

logger = logging.getLogger(__name__)




from ..config import (
    KP_EMA_ENABLE,          
    KP_EMA_ALPHA,           
    KP_MIN_CONF_FOR_SMOOTH, 
    
    KP_ALPHA_BY_ID,
    KP_ALPHA_DEFAULT,
    
    KP_CONF_HOLD,
    KP_CONF_MID,
    
    KP_ALPHA_MUL_LOW,
    KP_ALPHA_MUL_MID,
    KP_ALPHA_MUL_HIGH,
    
    KP_JUMP_ENABLE,
    KP_JUMP_RATIO,
    KP_JUMP_RATIO_EXT,
    KP_JUMP_ALPHA_MUL,
    KP_JUMP_HOLD_IF_LOWCONF,
)





JOINT_IDS_EXTENDED_MOVEMENT = {9, 10, 15, 16}  
CONFIDENCE_JUMP_THRESHOLD = 0.5  






_prev_kp_ema: Optional[Dict[int, Dict[str, float]]] = None
_prev_frame_shape: Optional[tuple] = None  
_prev_person_id: Optional[int] = None  

def _reset_ema_state() -> None:
    """
    EMA 상태를 초기화한다.
    사람이 사라지거나 바뀔 때 호출.
    """
    global _prev_kp_ema, _prev_frame_shape, _prev_person_id
    _prev_kp_ema = None
    _prev_frame_shape = None
    _prev_person_id = None


def _check_person_changed(track_id: Optional[int]) -> bool:
    """
    이전 프레임과 다른 사람인지 확인
    (YOLO가 인식한 사람이 바뀐 경우 감지)
    
    단일 인물 가정: track_id가 None이면 "사람이 없음"이 아니라
    "track_id 추적 중단"으로 봄 → 이전 ID 유지하여 안정성 향상
    
    Args:
        track_id: 현재 사람의 track_id (None이면 track_id 미제공)
    
    Returns:
        사람이 실제로 바뀌었으면 True, 아니면 False
    """
    global _prev_person_id
    
    
    
    if track_id is None:
        return False
    
    
    return _prev_person_id is not None and _prev_person_id != track_id


def _bbox_diag(t0: Dict[str, Any]) -> float:
    """
    bbox 대각선 길이를 계산한다.
    - 좌표는 정규화(0~1) 기준 전제
    - 점프(velocity) 판정의 기준 스케일로 사용
    - 만약 pixel 단위로 들어오면 게이팅이 망가지므로 방어 필요
    """
    b = t0.get("bbox")
    if not b or len(b) != 4:
        return 1.0
    
    try:
        x1, y1, x2, y2 = map(float, b)
    except (ValueError, TypeError):
        return 1.0
    
    
    
    if not (0 <= x1 <= 1 and 0 <= y1 <= 1 and 0 <= x2 <= 1 and 0 <= y2 <= 1):
        logger.warning(f"bbox out of normalized range: {b} → treating as pixel coords")
        return 1.0
    
    diag = math.hypot(x2 - x1, y2 - y1)
    return max(1e-6, diag)


def _alpha_for(kid: int, conf: float) -> float:
    """
    관절 ID + confidence를 기반으로 EMA alpha 결정

    현재 EMA 정의:
        smooth = alpha * prev + (1 - alpha) * curr

    → alpha가 클수록 이전값을 더 유지 = 더 강한 스무딩
    
    반환값은 안전 범위 [0, 0.99]로 클램프됨
    (alpha >= 1이면 1-alpha가 음수/0이 되어 EMA 식이 깨짐)
    """
    
    base = float(
        KP_ALPHA_BY_ID.get(
            kid,
            KP_ALPHA_DEFAULT if KP_ALPHA_DEFAULT is not None else KP_EMA_ALPHA
        )
    )

    
    if conf < KP_CONF_HOLD:
        alpha = base * KP_ALPHA_MUL_LOW
    elif conf < KP_CONF_MID:
        alpha = base * KP_ALPHA_MUL_MID
    else:
        
        alpha = base * KP_ALPHA_MUL_HIGH
    
    
    
    return min(0.99, max(0.0, alpha))


def ema_smooth_keypoints_inplace(obs: Dict[str, Any]) -> Dict[str, Any]:
    """
    keypoints EMA 스무딩을 obs 구조를 유지한 채 in-place 적용
    
    단일 사람(가장 높은 정확도) 기준 처리:
    1. 스무딩 활성화 확인
    2. 사람 인식 여부 확인 (없으면 리셋)
    3. 사람 변경 감지 (다른 사람이면 리셋)
    4. 프레임 크기 변화 감지 (리셋)
    5. keypoints 유효성 확인
    6. 첫 프레임 처리 (raw = smooth)
    7. 관절별 EMA + confidence 기반 처리
    8. 점프 게이팅 (속도 이상치 감지)
    """
    global _prev_kp_ema, _prev_frame_shape, _prev_person_id

    
    
    
    if not KP_EMA_ENABLE:
        return obs

    tracks = obs.get("tracks") or []
    if not tracks:
        _reset_ema_state()
        return obs

    t0 = tracks[0]  

    
    
    
    if not t0.get("has_person", False):
        _reset_ema_state()
        return obs

    
    
    
    current_track_id = t0.get("track_id")
    if _check_person_changed(current_track_id):
        _reset_ema_state()
    _prev_person_id = current_track_id

    
    
    
    frame_shape = t0.get("frame_shape")
    if frame_shape is not None:
        frame_shape = tuple(frame_shape)
        if _prev_frame_shape is None:
            _prev_frame_shape = frame_shape
        elif frame_shape != _prev_frame_shape:
            _reset_ema_state()
            _prev_frame_shape = frame_shape
    else:
        
        logger.warning(
            "t0['frame_shape'] not provided → "
            "cannot validate frame resolution changes. "
            "Consider adding frame_shape to obs for better stability."
        )

    
    
    
    kps_raw = t0.get("keypoints_raw")
    if kps_raw is None:
        kps_raw = t0.get("keypoints")
        if kps_raw:
            
            kps_raw = [{"id": kp.get("id"), "x": kp.get("x"), "y": kp.get("y"), "conf": kp.get("conf")} 
                       for kp in kps_raw]
        else:
            kps_raw = []
        t0["keypoints_raw"] = kps_raw

    if len(kps_raw) == 0:
        _reset_ema_state()
        t0["keypoints_smooth"] = []
        return obs

    
    
    
    curr: Dict[int, Dict[str, float]] = {}
    for kp in kps_raw:
        try:
            kid = int(kp.get("id", -1))
            if kid < 0:
                continue
            curr[kid] = {
                "x": float(kp.get("x", 0.0)),
                "y": float(kp.get("y", 0.0)),
                "conf": float(kp.get("conf", 0.0)),
            }
        except (ValueError, TypeError):
            
            continue

    if not curr:
        _reset_ema_state()
        t0["keypoints_smooth"] = []
        return obs

    
    
    
    if _prev_kp_ema is None:
        _prev_kp_ema = {kid: {"x": v["x"], "y": v["y"]} for kid, v in curr.items()}
        
        smooth_list = sorted(
            [{"id": kid, "x": v["x"], "y": v["y"], "conf": v["conf"]} for kid, v in curr.items()],
            key=lambda d: d["id"]
        )
        
        t0["keypoints_smooth"] = smooth_list
        t0["keypoints"] = smooth_list
        t0["quality_score"] = (
            float(sum(k["conf"] for k in smooth_list) / len(smooth_list))
            if smooth_list else 0.0
        )
        return obs

    
    
    
    diag = _bbox_diag(t0)
    out_list = []

    
    
    
    for kid in sorted(curr.keys()):
        c = curr[kid]
        conf = c["conf"]

        
        if kid not in _prev_kp_ema:
            _prev_kp_ema[kid] = {"x": c["x"], "y": c["y"], "jump_cnt": 0}

        px = _prev_kp_ema[kid]["x"]
        py = _prev_kp_ema[kid]["y"]
        p_jump_cnt = _prev_kp_ema[kid].get("jump_cnt", 0)

        
        dist = math.hypot(c["x"] - px, c["y"] - py)
        
        
        thr = (
            KP_JUMP_RATIO_EXT if kid in JOINT_IDS_EXTENDED_MOVEMENT
            else KP_JUMP_RATIO
        ) * diag
        
        is_jump = (dist > thr)
        
        
        if is_jump:
            p_jump_cnt += 1
        else:
            p_jump_cnt = 0 
            
        
        _prev_kp_ema[kid]["jump_cnt"] = p_jump_cnt

        
        
        
        
        
        force_update = (p_jump_cnt >= 4)

        
        
        
        
        if conf < KP_MIN_CONF_FOR_SMOOTH and not force_update:
            
            
            
            out_list.append({"id": kid, "x": px, "y": py, "conf": conf})
            continue

        
        
        
        alpha = _alpha_for(kid, conf)

        
        
        
        
        
        if KP_JUMP_ENABLE and is_jump and not force_update:
            
            if KP_JUMP_HOLD_IF_LOWCONF and conf < CONFIDENCE_JUMP_THRESHOLD:
                out_list.append({"id": kid, "x": px, "y": py, "conf": conf})
                continue

            
            alpha = min(0.99, alpha * KP_JUMP_ALPHA_MUL)
        
        
        

        
        
        alpha = min(0.99, max(0.0, alpha))

        
        
        
        
        
        
        
        sx = alpha * px + (1.0 - alpha) * c["x"]
        sy = alpha * py + (1.0 - alpha) * c["y"]

        
        _prev_kp_ema[kid]["x"] = sx
        _prev_kp_ema[kid]["y"] = sy

        out_list.append({"id": kid, "x": sx, "y": sy, "conf": conf})

    
    
    
    t0["keypoints_smooth"] = out_list
    t0["keypoints"] = out_list  
    t0["quality_score"] = (
        float(sum(k["conf"] for k in out_list) / len(out_list))
        if out_list else 0.0
    )

    return obs
