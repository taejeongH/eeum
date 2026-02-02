from dataclasses import dataclass
from typing import Optional, Dict, Any, List, Tuple
from ..config import DEVICE_ID, LOCATION_ID

def bbox_aspect(bbox: Optional[List[float]]) -> Optional[float]:
    if not bbox or len(bbox) != 4:
        return None
    x1, y1, x2, y2 = bbox
    w = max(1e-6, x2 - x1)
    h = max(1e-6, y2 - y1)
    return h / w

def bbox_center_y(bbox: Optional[List[float]]) -> Optional[float]:
    if not bbox or len(bbox) != 4:
        return None
    _, y1, _, y2 = bbox
    return (y1 + y2) * 0.5

def bbox_center_x(bbox: Optional[List[float]]) -> Optional[float]:
    if not bbox or len(bbox) != 4:
        return None
    x1, _, x2, _ = bbox
    return (x1 + x2) * 0.5

@dataclass
class Level1Params:
    # Basic quality gate
    min_quality: float = 0.15

    # Fast drop (vertical velocity) signal
    drop_window_s: float = 0.5
    vy_th: float = 0.9
    drop_hits_k: int = 2

    # Posture collapse (aspect ratio) signal
    baseline_window_s: float = 3.0
    aspect_abs_th: float = 1.2
    aspect_ratio_th: float = 0.55

    # ⭐ 핵심: Level 0에서 10초 이상 정상 회복 못하면 Level 1 승격
    sustain_s: float = 10.0  # 정지 상태 누적 시간
    abnormal_timeout_s: float = 10.0  # Level 0 상태 타임아웃 (움직여도 10초면 Level 1)

    # Recovery to NORMAL - 5초에서 3초로 변경 (너무 엄격한 조건은 독거 노인에게 맞지 않음)
    recover_s: float = 3.0
    aspect_recover_ratio: float = 1.0  # 정확히 baseline까지 완전 회복
    recover_center_move_th: float = 0.10  # 매우 큰 움직임만 "회복"으로 인정

    # Stillness thresholds
    still_center_th: float = 0.010
    still_kp_th: float = 0.020
    still_need_kp: bool = True
    recover_need_move: bool = True

    # Partial body (Near camera)
    partial_min_y2: float = 0.95   # bbox y2가 이보다 크면 하단 잘림 의심
    partial_conf_th: float = 0.3   # 하체 키포인트 신뢰도 임계값
    partial_vy_relax: float = 0.4  # 상반신만 보일 때 허용할 완화된 속도 계수 (비율)

    # Low Posture Persistence (Forced ABNORMAL entry for slow collapse)
    force_abnormal_aspect_th: float = 0.7  # 이 비율보다 낮으면 "매우 낮은 자세"로 간주
    force_abnormal_frames: int = 15        # N프레임(약 0.5~1초) 이상 유지되면 즉시 위험 진입


class Level1Engine:
    """
    목표 동작:
    - NORMAL -> ABNORMAL(Level0): drop_signal OR posture_signal
    - ABNORMAL(Level0) 상태에서 '큰 움직임 없음(is_still)'이 sustain_s(기본 10초) 누적되면 LEVEL1 승격
    - 회복 조건(자세 회복)이 recover_s 동안 지속되면 NORMAL 복귀
    """

    def __init__(self, p: Level1Params):
        from collections import deque
        self.p = p

        self.state = "NORMAL"
        self.abnormal_start_ts: Optional[float] = None
        self.level1_fired: bool = False

        # Stats for confidence score
        self.abnormal_stats: Dict[str, Any] = {}

        # For recovery logic
        self.recover_acc_s: float = 0.0

        # For stillness promotion logic
        self.still_acc_s: float = 0.0

        # Previous frame values
        self.prev_ts: Optional[float] = None
        self.prev_cx: Optional[float] = None
        self.prev_cy: Optional[float] = None
        self.prev_kps: Optional[Dict[int, Tuple[float, float, float]]] = None  # id -> (x,y,conf)

        # Rolling windows
        self.drop_hits = deque()     # (ts, hit)
        self.aspect_hist = deque()   # (ts, aspect)
        
        # Low posture persistence counter
        self.low_posture_cnt = 0

    def reset_all(self):
        self.state = "NORMAL"
        self.abnormal_start_ts = None
        self.level1_fired = False
        self.abnormal_stats = {}

        self.recover_acc_s = 0.0
        self.still_acc_s = 0.0

        self.prev_ts = None
        self.prev_cx = None
        self.prev_cy = None
        self.prev_kps = None

        self.drop_hits.clear()
        self.aspect_hist.clear()
        self.low_posture_cnt = 0

    def _compute_baseline_aspect(self, ts: float) -> Optional[float]:
        # keep only baseline_window_s
        while self.aspect_hist and ts - self.aspect_hist[0][0] > self.p.baseline_window_s:
            self.aspect_hist.popleft()

        if len(self.aspect_hist) < 5:
            return None

        vals = sorted(a for _, a in self.aspect_hist)
        # robust "standing-ish" baseline: 80 percentile
        return vals[int(0.8 * (len(vals) - 1))]

    def _compute_drop_signal(self, ts: float, cy: float) -> Tuple[Optional[float], float, bool]:
        """
        반환: (vy, dt, drop_signal)
        vy: bbox center y velocity (정규화된 단위 / 초)
        """
        vy = None
        dt = 0.0

        if self.prev_ts is not None and self.prev_cy is not None:
            dt = max(1e-6, ts - self.prev_ts)
            vy = (cy - self.prev_cy) / dt

        hit = (vy is not None and vy > self.p.vy_th)
        self.drop_hits.append((ts, hit))
        while self.drop_hits and ts - self.drop_hits[0][0] > self.p.drop_window_s:
            self.drop_hits.popleft()

        drop_signal = (sum(1 for _, h in self.drop_hits if h) >= self.p.drop_hits_k)
        return vy, dt, drop_signal

    def _check_partial_body(self, bbox: List[float], kps_list: List[Dict[str, Any]]) -> bool:
        """
        상반신만 근접해서 보이는지 판단
        조건:
        1. BBox 하단이 화면 끝에 거의 닿음 (y2 > 0.95)
        2. 하체 키포인트(무릎, 발목)가 거의 안 보임
        """
        if not bbox:
            return False
        
        # 1. BBox bottom check
        _, _, _, y2 = bbox
        if y2 < self.p.partial_min_y2:
            return False

        # 2. Lower body keypoints check
        # COCO Keypoints: 13(Left Knee), 14(Right Knee), 15(Left Ankle), 16(Right Ankle)
        lower_ids = {13, 14, 15, 16}
        visible_lower = 0
        
        for k in kps_list:
            kid = int(k.get("id", -1))
            conf = float(k.get("conf", 0.0))
            if kid in lower_ids and conf > self.p.partial_conf_th:
                visible_lower += 1
        
        # 무릎/발목이 거의 다(3개 이상) 없으면 상반신만 있는 것으로 간주
        return visible_lower < 2

    def _compute_confidence(self) -> Tuple[float, Dict[str, Any]]:
        """
        신뢰도 점수 산출 (0~100)
        - 신호 강도 (40%): 최대 수직 속도(max_vy) 또는 최소 종횡비(min_aspect) 기준
        - 정지 상태 (40%): 전체 프레임 중 정지 상태 비율
        - 탐지 품질 (20%): 평균 YOLO 신뢰도 점수
        """
        stats = self.abnormal_stats
        if not stats:
            return 50.0, {}

        # 1. 신호 점수 (Signal Score)
        # vy(수직 속도): 0.5 미만이면 0점, 2.0 이상이면 100점
        max_vy = stats.get("max_vy", 0.0)
        score_vy = min(100.0, max(0.0, (max_vy - 0.5) / 1.5 * 100.0))

        # aspect(종횡비): 1.0 이상이면 0점, 0.2 이하면 100점
        min_aspect = stats.get("min_aspect", 1.0)
        score_asp = min(100.0, max(0.0, (1.0 - min_aspect) / 0.8 * 100.0))
        
        score_signal = max(score_vy, score_asp)

        # 2. 정지 상태 점수 (Stillness Score)
        cnt = stats.get("count", 1)
        still_cnt = stats.get("still_count", 0)
        ratio_still = still_cnt / cnt if cnt > 0 else 0.0
        score_still = ratio_still * 100.0

        # 3. 탐지 품질 점수 (Quality Score)
        sum_q = stats.get("quality_sum", 0.0)
        avg_q = sum_q / cnt if cnt > 0 else 0.0
        # 0.2 미만이면 0점, 0.8 이상이면 100점
        score_qual = min(100.0, max(0.0, (avg_q - 0.2) / 0.6 * 100.0))

        score_qual = min(100.0, max(0.0, (avg_q - 0.2) / 0.6 * 100.0))

        # 4. 근접 페널티 (Partial Penalty)
        partial_cnt = stats.get("partial_count", 0)
        ratio_partial = partial_cnt / cnt if cnt > 0 else 0.0
        penalty = 0.0
        if ratio_partial > 0.5:
             # 절반 이상이 상반신만 보였다면 의심스러움. 점수 깎기
             penalty = 20.0 * ratio_partial

        # 가중치 합산
        # 신호 40%, 정지 상태 40%, 품질 20% - 페널티
        final_score = (score_signal * 0.4) + (score_still * 0.4) + (score_qual * 0.2) - penalty
        final_score = max(0.0, final_score)
        
        detail = {
            "score_signal": score_signal,
            "score_still": score_still,
            "score_qual": score_qual,
            "stats": stats
        }
        return float(final_score), detail

    def _compute_stillness(
        self,
        cx: float,
        cy: float,
        kps_list: List[Dict[str, Any]],
    ) -> Tuple[Optional[float], Optional[float], bool]:
        """
        반환: (center_move, kp_move, is_still)
        center_move: bbox 중심 이동 크기 (정규화 단위) 프레임 사이
        kp_move: 평균 키포인트 이동 크기 (정규화 단위) 프레임 사이
        """
        center_move = None
        kp_move = None

        if self.prev_cx is not None and self.prev_cy is not None:
            dcx = cx - self.prev_cx
            dcy = cy - self.prev_cy
            center_move = (dcx * dcx + dcy * dcy) ** 0.5

        # keypoints
        if self.p.still_need_kp and kps_list:
            curr_kps: Dict[int, Tuple[float, float, float]] = {}
            for k in kps_list:
                kid = int(k.get("id", -1))
                if kid < 0:
                    continue
                curr_kps[kid] = (float(k.get("x", 0.0)), float(k.get("y", 0.0)), float(k.get("conf", 0.0)))

            if self.prev_kps is not None:
                dsum = 0.0
                cnt = 0
                for kid, (x, y, conf) in curr_kps.items():
                    if conf < self.p.min_quality:
                        continue
                    if kid in self.prev_kps:
                        px, py, pconf = self.prev_kps[kid]
                        if pconf < self.p.min_quality:
                            continue
                        dx = x - px
                        dy = y - py
                        dsum += (dx * dx + dy * dy) ** 0.5
                        cnt += 1
                kp_move = (dsum / cnt) if cnt > 0 else None

            self.prev_kps = curr_kps
        else:
            self.prev_kps = None

        still_center_ok = (center_move is not None and center_move < self.p.still_center_th)
        still_kp_ok = (not self.p.still_need_kp) or (kp_move is not None and kp_move < self.p.still_kp_th)
        is_still = still_center_ok and still_kp_ok

        return center_move, kp_move, is_still

    def step(self, obs: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        ts = float(obs.get("ts", 0.0))
        frame_index = int(obs.get("frame_index", -1))

        tracks = obs.get("tracks") or []
        if not tracks:
            self.reset_all()
            return None

        t0 = tracks[0]
        if not t0.get("has_person", False):
            self.reset_all()
            return None

        quality = float(t0.get("quality_score") or 0.0)
        if quality < self.p.min_quality:
            self.reset_all()
            return None

        bbox = t0.get("bbox")
        aspect = bbox_aspect(bbox)
        cx = bbox_center_x(bbox)
        cy = bbox_center_y(bbox)
        if aspect is None or cx is None or cy is None:
            self.reset_all()
            return None

        # keypoints: prefer smooth
        kps_list = t0.get("keypoints_smooth") or t0.get("keypoints") or []

        # update aspect history
        self.aspect_hist.append((ts, aspect))
        baseline = self._compute_baseline_aspect(ts)

        # signals
        vy, dt, drop_signal = self._compute_drop_signal(ts, cy)

        rotate_abs = (aspect < self.p.aspect_abs_th)
        rotate_rel = (baseline is not None and aspect < baseline * self.p.aspect_ratio_th)
        posture_signal = rotate_abs or rotate_rel

        # 상반신 근접 여부 체크
        is_partial = self._check_partial_body(bbox, kps_list)

        # 머리 위치 하강 여부 (Nose: 0)
        head_down = False
        if self.prev_kps and kps_list:
            curr_nose = next((k for k in kps_list if int(k.get("id", -1)) == 0), None)
            prev_nose = self.prev_kps.get(0)
            if curr_nose and prev_nose:
                # y값이 증가하면 아래로 이동한 것
                if float(curr_nose["y"]) > float(prev_nose[1]) + 0.01:
                    head_down = True

        # stillness (needs prev values; do before updating prev_ts/cx/cy)
        center_move, kp_move, is_still = self._compute_stillness(cx, cy, kps_list)

        # update prev values (after computing deltas)
        self.prev_ts = ts
        self.prev_cx = cx
        self.prev_cy = cy

        # ---- STATE MACHINE ----
        if self.state == "NORMAL":
            # 1. 저자세 지속성 체크 (Low Posture Persistence)
            # 속도가 느려도, 자세가 매우 낮게 깔린 상태가 지속되면 위험으로 간주
            if aspect < self.p.force_abnormal_aspect_th:
                self.low_posture_cnt += 1
            else:
                self.low_posture_cnt = 0

            # 2. Trigger Logic
            trigger = False
            
            # (A) 강제 진입: 매우 낮은 자세가 오래 유지됨
            if self.low_posture_cnt >= self.p.force_abnormal_frames:
                trigger = True
            
            # (B) 상반신 근접 시 (Partial Body)
            elif is_partial:
                relaxed_vy_th = self.p.vy_th * self.p.partial_vy_relax
                # 속도가 아예 없지는 않으면서 머리가 내려가거나, 자세가 무너졌을 때
                if (vy is not None and vy > relaxed_vy_th) and (head_down or posture_signal):
                    trigger = True
            # (C) 일반적인 경우 (Normal Distance)
            else:
                if drop_signal or posture_signal:
                    trigger = True

            if trigger:
                self.state = "ABNORMAL"  # Level0
                self.abnormal_start_ts = ts
                self.abnormal_start_ts = ts
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
                self.low_posture_cnt = 0  # Reset
                self.level1_fired = False
                return {
                    "type": "abnormal_enter",
                    "ts": ts,
                    "frame_index": frame_index,
                    "signals": {"drop": drop_signal, "posture": posture_signal},
                    "values": {
                        "aspect": aspect,
                        "baseline_aspect": baseline,
                        "vy": vy,
                        "quality": quality,
                        "center_move": center_move,
                        "kp_move": kp_move,
                    },
                }

        if self.state == "ABNORMAL":
            # 통계 데이터 업데이트
            if not self.abnormal_stats:
                self.abnormal_stats = {
                    "max_vy": 0.0,
                    "min_aspect": 10.0,
                    "quality_sum": 0.0,
                    "still_count": 0,
                    "count": 0,
                    "partial_count": 0,
                }
            
            s = self.abnormal_stats
            if vy is not None:
                s["max_vy"] = max(s["max_vy"], vy)
            s["min_aspect"] = min(s["min_aspect"], aspect)
            s["quality_sum"] += quality
            s["count"] += 1
            if is_partial:
                s["partial_count"] += 1
            time_since_abnormal = ts - self.abnormal_start_ts if self.abnormal_start_ts is not None else 0.0
            
            # ⭐ Level 0 로직:
            # 1) 정지 상태(is_still=True)가 10초 누적되면 Level 1
            # 2) 또는 Level 0에서 10초 이상 있으면 자동으로 Level 1 (움직임 무관)
            
            # Stillness accumulate
            if is_still:
                self.still_acc_s += dt
                if self.abnormal_stats:
                    self.abnormal_stats["still_count"] += 1
            else:
                self.still_acc_s = 0.0

            # 조건 1: 정지 누적으로 Level 1
            if self.still_acc_s >= self.p.sustain_s and not self.level1_fired:
                self.level1_fired = True
                self.state = "LEVEL1"
                return {
                    "type": "level1",
                    "ts": ts,
                    "frame_index": frame_index,
                    "reason": "still_for_sustain_s",
                    "values": {
                        "still_acc_s": self.still_acc_s,
                        "time_since_abnormal": time_since_abnormal,
                        "confidence_score": self._compute_confidence()[0],
                        "confidence_detail": self._compute_confidence()[1],
                    },
                }

            # 조건 2: 타임아웃으로 Level 1 (움직여도 10초면 위험)
            if time_since_abnormal >= self.p.abnormal_timeout_s and not self.level1_fired:
                self.level1_fired = True
                self.state = "LEVEL1"
                return {
                    "type": "level1",
                    "ts": ts,
                    "frame_index": frame_index,
                    "reason": "abnormal_timeout",
                    "values": {
                        "time_since_abnormal": time_since_abnormal,
                        "center_move": center_move,
                        "kp_move": kp_move,
                        "confidence_score": self._compute_confidence()[0],
                        "confidence_detail": self._compute_confidence()[1],
                    },
                }
            
            # Level 0에서 벗어나기: 완전히 일어난 상태 확인
            # (매우 엄격한 조건으로 쉽게 나가지 못하도록)
            recovered_posture = (
                baseline is not None
                and aspect > baseline * self.p.aspect_recover_ratio
            )

            recovered_move = (
                center_move is not None
                and center_move > self.p.recover_center_move_th
            )

            # AND 조건: 자세도 정상 + 움직임도 명확해야 회복 가능
            recovered_now = recovered_posture and recovered_move

            if recovered_now:
                self.recover_acc_s += dt
            else:
                self.recover_acc_s = 0.0

            # 5초 이상 완전 회복 상태 유지 → Normal로 돌아감
            if self.recover_acc_s >= self.p.recover_s:
                self.state = "NORMAL"
                self.abnormal_start_ts = None
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
                self.level1_fired = False
                return {
                    "type": "abnormal_exit",
                    "ts": ts,
                    "frame_index": frame_index,
                    "reason": "recovered",
                    "values": {
                        "aspect": aspect,
                        "baseline_aspect": baseline,
                        "center_move": center_move,
                    },
                }

        # Optional: in LEVEL1, you might keep returning None to avoid spamming.
        # If you want "recover from LEVEL1", define policy here.
        return None


@dataclass
class PresenceParams:
    enter_hits: int = 5          # 연속 N프레임 "있음"이면 enter 확정
    exit_hits: int = 10          # 연속 N프레임 "없음"이면 exit 확정
    min_quality: float = 0.10    # has_person True여도 quality 낮으면 absent 취급
    cool_down_s: float = 0.5     # 이벤트 연속 발사 방지(선택)

class PresenceEngine:
    def __init__(self, p: PresenceParams):
        self.p = p
        self.state = "ABSENT"   # ABSENT | PRESENT
        self.present_cnt = 0
        self.absent_cnt = 0
        self.last_emit_ts: float = 0.0

    def reset(self):
        self.state = "ABSENT"
        self.present_cnt = 0
        self.absent_cnt = 0
        self.last_emit_ts = 0.0

    def _is_present(self, obs: Dict[str, Any]) -> bool:
        tracks = obs.get("tracks") or []
        if not tracks:
            return False
        t0 = tracks[0]
        if not t0.get("has_person", False):
            return False
        q = float(t0.get("quality_score") or 0.0)
        if q < self.p.min_quality:
            return False
        return True

    def step(self, obs: Dict[str, Any]) -> Optional[Dict[str, Any]]:
        ts = float(obs.get("ts", 0.0))
        frame_index = int(obs.get("frame_index", -1))

        present = self._is_present(obs)

        if present:
            self.present_cnt += 1
            self.absent_cnt = 0
        else:
            self.absent_cnt += 1
            self.present_cnt = 0

        # 쿨다운(선택)
        if self.p.cool_down_s > 0 and (ts - self.last_emit_ts) < self.p.cool_down_s:
            return None

        if self.state == "ABSENT" and self.present_cnt >= self.p.enter_hits:
            self.state = "PRESENT"
            self.last_emit_ts = ts
            return {
                "kind": "vision",
                "device_id": DEVICE_ID,
                "data": {
                    "location_id": LOCATION_ID,
                    "event": "enter",
                },
                "ts": ts,
            }

        if self.state == "PRESENT" and self.absent_cnt >= self.p.exit_hits:
            self.state = "ABSENT"
            self.last_emit_ts = ts
            return {
                "kind": "vision",
                "device_id": DEVICE_ID,
                "data": {
                    "location_id": LOCATION_ID,
                    "event": "exit",
                },
                "ts": ts,
            }

        return None