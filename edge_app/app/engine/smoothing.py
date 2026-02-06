from typing import Dict, Any, Optional
import math
import logging

logger = logging.getLogger(__name__)

# ----------------------------
# Config imports
# ----------------------------
from ..config import (
    KP_EMA_ENABLE,          # EMA 스무딩 전체 on/off
    KP_EMA_ALPHA,           # 기본 EMA alpha (fallback용)
    KP_MIN_CONF_FOR_SMOOTH, # 이 conf 이하면 업데이트 안 함(hold)
    # 관절별 alpha 테이블
    KP_ALPHA_BY_ID,
    KP_ALPHA_DEFAULT,
    # confidence 구간 정의
    KP_CONF_HOLD,
    KP_CONF_MID,
    # confidence에 따른 alpha 배율
    KP_ALPHA_MUL_LOW,
    KP_ALPHA_MUL_MID,
    KP_ALPHA_MUL_HIGH,
    # 점프(속도 이상치) 게이팅 관련
    KP_JUMP_ENABLE,
    KP_JUMP_RATIO,
    KP_JUMP_RATIO_EXT,
    KP_JUMP_ALPHA_MUL,
    KP_JUMP_HOLD_IF_LOWCONF,
)

# ----------------------------
# 관절 ID 상수 정의
# ----------------------------
# COCO 포즈 모델: 손(9,10), 발(15,16)
JOINT_IDS_EXTENDED_MOVEMENT = {9, 10, 15, 16}  # 손/발은 이동량 허용
CONFIDENCE_JUMP_THRESHOLD = 0.5  # 점프 게이팅 시 낮은 신뢰도 임계값

# -------------------------------------------------
# 이전 프레임 EMA 결과를 보관하는 전역 상태
# 단일 사람만 인식하므로 단순 구조 유지
# kid -> {"x": float, "y": float}
# -------------------------------------------------
_prev_kp_ema: Optional[Dict[int, Dict[str, float]]] = None
_prev_frame_shape: Optional[tuple] = None  # (h, w)
_prev_person_id: Optional[int] = None  # 이전 프레임 사람 ID (사람 변경 감지용)

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
    
    # track_id가 None이면 "ID 제공 없음"으로 간주 → 사람 변경 아님
    # (카메라 오류 등으로 일시적으로 None이 나올 수 있음)
    if track_id is None:
        return False
    
    # track_id가 숫자면: 이전과 다르면 사람 변경
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
    
    # 정규화 좌표 체크: 모두 0~1 범위인지 확인
    # 범위 벗어나면 pixel 좌표로 간주 → 디버깅 경고 + 기본값 반환
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
    # 관절별 기본 alpha
    base = float(
        KP_ALPHA_BY_ID.get(
            kid,
            KP_ALPHA_DEFAULT if KP_ALPHA_DEFAULT is not None else KP_EMA_ALPHA
        )
    )

    # confidence 낮을수록 더 부드럽게(이전값 유지 강화)
    if conf < KP_CONF_HOLD:
        alpha = base * KP_ALPHA_MUL_LOW
    elif conf < KP_CONF_MID:
        alpha = base * KP_ALPHA_MUL_MID
    else:
        # confidence 높으면 반응 조금 빠르게
        alpha = base * KP_ALPHA_MUL_HIGH
    
    # 안전 범위 클램프: [0, 0.99]
    # 값이 1 이상이면 (1-alpha)가 음수 이하가 되어 수식 파괴
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

    # ----------------------------
    # 스무딩 비활성화 시 바로 반환
    # ----------------------------
    if not KP_EMA_ENABLE:
        return obs

    tracks = obs.get("tracks") or []
    if not tracks:
        _reset_ema_state()
        return obs

    t0 = tracks[0]  # 단일 사람만 처리

    # ----------------------------
    # 사람이 없으면 EMA 리셋
    # ----------------------------
    if not t0.get("has_person", False):
        _reset_ema_state()
        return obs

    # ----------------------------
    # 사람이 바뀌면 EMA 리셋 (track_id 기반)
    # ----------------------------
    current_track_id = t0.get("track_id")
    if _check_person_changed(current_track_id):
        _reset_ema_state()
    _prev_person_id = current_track_id

    # ----------------------------
    # 프레임 크기 변화 감지해서 EMA 리셋
    # ----------------------------
    frame_shape = t0.get("frame_shape")
    if frame_shape is not None:
        frame_shape = tuple(frame_shape)
        if _prev_frame_shape is None:
            _prev_frame_shape = frame_shape
        elif frame_shape != _prev_frame_shape:
            _reset_ema_state()
            _prev_frame_shape = frame_shape
    else:
        # frame_shape가 없으면 정규화 좌표 검증 불가 → 경고
        logger.warning(
            "t0['frame_shape'] not provided → "
            "cannot validate frame resolution changes. "
            "Consider adding frame_shape to obs for better stability."
        )

    # ----------------------------
    # raw keypoints 확보 및 유효성 검사
    # ----------------------------
    kps_raw = t0.get("keypoints_raw")
    if kps_raw is None:
        kps_raw = t0.get("keypoints")
        if kps_raw:
            # 필요한 필드만 복사 (deepcopy 오버헤드 제거)
            kps_raw = [{"id": kp.get("id"), "x": kp.get("x"), "y": kp.get("y"), "conf": kp.get("conf")} 
                       for kp in kps_raw]
        else:
            kps_raw = []
        t0["keypoints_raw"] = kps_raw

    if len(kps_raw) == 0:
        _reset_ema_state()
        t0["keypoints_smooth"] = []
        return obs

    # ----------------------------
    # 현재 프레임 keypoints 정리 (유효성 검사 포함)
    # ----------------------------
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
            # 잘못된 데이터는 스킵
            continue

    if not curr:
        _reset_ema_state()
        t0["keypoints_smooth"] = []
        return obs

    # ----------------------------
    # 첫 프레임: smooth = raw
    # ----------------------------
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

    # ----------------------------
    # bbox 스케일 계산(점프 판정용)
    # ----------------------------
    diag = _bbox_diag(t0)
    out_list = []

    # ----------------------------
    # 관절별 EMA 업데이트
    # ----------------------------
    for kid in sorted(curr.keys()):
        c = curr[kid]
        conf = c["conf"]

        # 이전 값 없으면 초기화
        if kid not in _prev_kp_ema:
            _prev_kp_ema[kid] = {"x": c["x"], "y": c["y"], "jump_cnt": 0}

        px = _prev_kp_ema[kid]["x"]
        py = _prev_kp_ema[kid]["y"]
        p_jump_cnt = _prev_kp_ema[kid].get("jump_cnt", 0)

        # 거리 계산 (Jump 판정용)
        dist = math.hypot(c["x"] - px, c["y"] - py)
        
        # 손/발은 허용 이동량 더 큼
        thr = (
            KP_JUMP_RATIO_EXT if kid in JOINT_IDS_EXTENDED_MOVEMENT
            else KP_JUMP_RATIO
        ) * diag
        
        is_jump = (dist > thr)
        
        # [Persistence Counter] 점프가 지속되는지 확인
        if is_jump:
            p_jump_cnt += 1
        else:
            p_jump_cnt = 0 # 정상 범위 들어오면 리셋
            
        # 상태 저장 (다음 프레임을 위해)
        _prev_kp_ema[kid]["jump_cnt"] = p_jump_cnt

        # ------------------------
        # 강제 업데이트 조건:
        # 점프가 4프레임 이상 지속되면 -> "실제 이동"으로 간주하여
        # Low Confidence나 Jump Gating을 무시하고 업데이트 허용
        # ------------------------
        force_update = (p_jump_cnt >= 4)

        # ------------------------
        # confidence 낮으면 hold
        # (단, force_update면 스킵)
        # ------------------------
        if conf < KP_MIN_CONF_FOR_SMOOTH and not force_update:
            # [개선] 완전 Hold 대신 아~~주 조금은 따라가게 할까?
            # 아니다, 신뢰도 낮은 노이즈가 계속 들어오면 떨림 발생함.
            # 일단 Hold 유지하되, force_update로 탈출구 마련.
            out_list.append({"id": kid, "x": px, "y": py, "conf": conf})
            continue

        # ------------------------
        # 관절 + confidence 기반 alpha 결정
        # ------------------------
        alpha = _alpha_for(kid, conf)

        # ------------------------
        # 점프(이상 이동) 게이팅
        # 예상 이동량보다 크면 처리
        # (단, force_update면 페널티 없이 반영)
        # ------------------------
        if KP_JUMP_ENABLE and is_jump and not force_update:
            # conf도 낮으면 아예 hold
            if KP_JUMP_HOLD_IF_LOWCONF and conf < CONFIDENCE_JUMP_THRESHOLD:
                out_list.append({"id": kid, "x": px, "y": py, "conf": conf})
                continue

            # 아니면 alpha를 키워 더 부드럽게 (천천히 따라가기)
            alpha = min(0.99, alpha * KP_JUMP_ALPHA_MUL)
        
        # force_update 상황이면 alpha를 조금 낮춰서(빠르게) 반응할 수도 있지만
        # 급격한 변화일 수 있으므로 기본 alpha 사용 (안전)

        # 점프 게이팅 이후 alpha가 조정될 수 있으므로
        # 최종 안전 범위 클램프
        alpha = min(0.99, max(0.0, alpha))

        # ------------------------
        # EMA 적용
        # smooth = alpha * prev + (1 - alpha) * curr
        # alpha ↑ = 이전값 유지 ↑ (더 부드러움)
        # alpha=0.5 → 현재값 50% + 이전값 50%
        # alpha=0.9 → 현재값 10% + 이전값 90%
        # ------------------------
        sx = alpha * px + (1.0 - alpha) * c["x"]
        sy = alpha * py + (1.0 - alpha) * c["y"]

        # 상태 업데이트
        _prev_kp_ema[kid]["x"] = sx
        _prev_kp_ema[kid]["y"] = sy

        out_list.append({"id": kid, "x": sx, "y": sy, "conf": conf})

    # ----------------------------
    # 결과 반영
    # ----------------------------
    t0["keypoints_smooth"] = out_list
    t0["keypoints"] = out_list  # 판단 로직은 스무딩 기준
    t0["quality_score"] = (
        float(sum(k["conf"] for k in out_list) / len(out_list))
        if out_list else 0.0
    )

    return obs
