import logging
from dataclasses import dataclass
from typing import Optional, Dict, Any, List, Tuple

logger = logging.getLogger(__name__)
from ..config import DEVICE_ID, LOCATION_ID, ABNORMAL_TIMEOUT_S

def bbox_aspect(bbox: Optional[List[float]]) -> Optional[float]:
    """바운딩 박스의 가로세로비(Height/Width)를 계산합니다."""
    if not bbox or len(bbox) != 4:
        return None
    x1, y1, x2, y2 = bbox
    w = max(1e-6, x2 - x1)
    h = max(1e-6, y2 - y1)
    return h / w

def bbox_center_y(bbox: Optional[List[float]]) -> Optional[float]:
    """바운딩 박스 중심의 Y 좌표를 계산합니다."""
    if not bbox or len(bbox) != 4:
        return None
    _, y1, _, y2 = bbox
    return (y1 + y2) * 0.5

def bbox_center_x(bbox: Optional[List[float]]) -> Optional[float]:
    """바운딩 박스 중심의 X 좌표를 계산합니다."""
    if not bbox or len(bbox) != 4:
        return None
    x1, _, x2, _ = bbox
    return (x1 + x2) * 0.5

def bbox_h(bbox: Optional[List[float]]) -> Optional[float]:
    if not bbox or len(bbox) != 4:
        return None
    _, y1, _, y2 = bbox
    return max(1e-6, y2 - y1)


@dataclass
class Level1Params:
    min_quality: float = 0.15

    drop_window_s: float = 0.5
    vy_th: float = 0.3
    drop_hits_k: int = 1

    baseline_window_s: float = 3.0
    aspect_abs_th: float = 1.6
    aspect_ratio_th: float = 0.75

    sustain_s: float = 10.0
    abnormal_timeout_s: float = ABNORMAL_TIMEOUT_S

    recover_s: float = 2.0
    aspect_recover_ratio: float = 0.9
    recover_center_move_th: float = 0.10

    still_center_th: float = 0.010
    still_kp_th: float = 0.020
    still_need_kp: bool = True

    partial_min_y2: float = 0.95
    partial_conf_th: float = 0.3
    partial_vy_relax: float = 0.6

    force_abnormal_aspect_th: float = 0.7
    force_abnormal_frames: int = 45

    ghost_timeout_s: float = ABNORMAL_TIMEOUT_S
    screen_edge_margin: float = 0.05

    warmup_frames: int = 12

    partial_edge_margin: float = 0.06
    partial_big_w: float = 0.45
    partial_big_h: float = 0.70

    level1_hold_s: float = 1.0
    level1_cooldown_s: float = 25.0

    
    enter_confirm_s: float = 0.35          
    enter_confirm_need: int = 2            
    posture_persist_s: float = 0.25        
    shoulder_drop_persist_s: float = 0.20  

    
    timeout_requires_confirm: bool = True

    
    recover_grace_s: float = 0.8

    
    confirm_aspect_rel: float = 0.65       
    confirm_aspect_abs: float = 1.0        

    height_baseline_window_s: float = 3.0     
    height_ratio_th: float = 0.65             
    height_persist_s: float = 0.20            


class Level1Engine:
    def __init__(self, p: Level1Params):
        from collections import deque
        self.p = p

        self.state = "NORMAL"
        self.abnormal_start_ts: Optional[float] = None
        self.level1_fired: bool = False

        self.abnormal_stats: Dict[str, Any] = {}

        self.recover_acc_s: float = 0.0
        self.still_acc_s: float = 0.0

        self.prev_ts: Optional[float] = None
        self.prev_cx: Optional[float] = None
        self.prev_cy: Optional[float] = None
        self.prev_kps: Optional[Dict[int, Tuple[float, float, float]]] = None

        self.drop_hits = deque()
        self.aspect_hist = deque()

        self.low_posture_cnt = 0

        self.ghost_start_ts: Optional[float] = None
        self.is_ghost_mode: bool = False
        self.last_known_bbox: Optional[List[float]] = None
        self.ghost_entry_cnt = 0
        self.ghost_exit_cnt = 0
        self.lost_start_ts: Optional[float] = None

        self.prev_shoulder_y: Optional[float] = None
        self.prev_shoulder_ts: Optional[float] = None

        self.baseline_at_enter: Optional[float] = None

        self.last_drop_ts: Optional[float] = None
        self.last_drop_vy: Optional[float] = None

        self.present_streak: int = 0

        self.level1_ts: Optional[float] = None
        self.cooldown_until_ts: float = 0.0

        
        self.enter_acc_s: float = 0.0
        self.posture_acc_s: float = 0.0
        self.shoulder_acc_s: float = 0.0

        
        self.abnormal_confirmed: bool = False

        self.height_hist = deque()   
        self.height_acc_s: float = 0.0
        self.baseline_h_at_enter: Optional[float] = None

    def reset_all(self):
        """엔진의 모든 내부 상태를 초기화합니다."""
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

        self.ghost_start_ts = None
        self.is_ghost_mode = False
        self.last_known_bbox = None
        self.ghost_entry_cnt = 0
        self.ghost_exit_cnt = 0
        self.lost_start_ts = None

        self.baseline_at_enter = None

        self.prev_shoulder_y = None
        self.prev_shoulder_ts = None

        self.last_drop_ts = None
        self.last_drop_vy = None

        self.present_streak = 0

        
        cooldown_until = self.cooldown_until_ts
        self.level1_ts = None
        self.cooldown_until_ts = cooldown_until

        
        self.enter_acc_s = 0.0
        self.posture_acc_s = 0.0
        self.shoulder_acc_s = 0.0
        self.abnormal_confirmed = False

        self.height_hist.clear()
        self.height_acc_s = 0.0
        self.baseline_h_at_enter = None

    def _compute_baseline_h(self, ts: float) -> Optional[float]:
        while self.height_hist and ts - self.height_hist[0][0] > self.p.height_baseline_window_s:
            self.height_hist.popleft()
        if len(self.height_hist) < 5:
            return None
        vals = sorted(v for _, v in self.height_hist)
        return vals[int(0.8 * (len(vals) - 1))]


    def _compute_baseline_aspect(self, ts: float) -> Optional[float]:
        while self.aspect_hist and ts - self.aspect_hist[0][0] > self.p.baseline_window_s:
            self.aspect_hist.popleft()
        if len(self.aspect_hist) < 5:
            return None
        vals = sorted(a for _, a in self.aspect_hist)
        return vals[int(0.8 * (len(vals) - 1))]

    def _compute_drop_signal(self, ts: float, cy: float) -> Tuple[Optional[float], float, bool]:
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

    def _compute_shoulder_drop(self, ts: float, kps_list: List[Dict[str, Any]]) -> Tuple[Optional[float], bool]:
        def get_kp(kid: int):
            return next((k for k in kps_list if int(k.get("id", -1)) == kid), None)

        ls, rs = get_kp(5), get_kp(6)
        if not ls and not rs:
            return None, False

        sy_sum, cnt = 0.0, 0
        if ls and float(ls.get("conf", 0.0)) > 0.3:
            sy_sum += float(ls.get("y", 0.0)); cnt += 1
        if rs and float(rs.get("conf", 0.0)) > 0.3:
            sy_sum += float(rs.get("y", 0.0)); cnt += 1
        if cnt == 0:
            return None, False

        shoulder_y = sy_sum / cnt

        shoulder_vy = None
        shoulder_drop = False
        if self.prev_shoulder_y is not None and self.prev_shoulder_ts is not None:
            dt = max(1e-6, ts - self.prev_shoulder_ts)
            shoulder_vy = (shoulder_y - self.prev_shoulder_y) / dt
            shoulder_drop = (shoulder_vy > 0.4)

        self.prev_shoulder_y = shoulder_y
        self.prev_shoulder_ts = ts
        return shoulder_vy, shoulder_drop

    def _check_partial_body(self, bbox: List[float], kps_list: List[Dict[str, Any]]) -> bool:
        if not bbox or len(bbox) != 4:
            return False

        x1, y1, x2, y2 = bbox
        w = max(1e-6, x2 - x1)
        h = max(1e-6, y2 - y1)

        bottom_touch = (y2 >= self.p.partial_min_y2)

        lower_ids = {13, 14, 15, 16}
        visible_lower = 0
        for k in kps_list:
            kid = int(k.get("id", -1))
            conf = float(k.get("conf", 0.0))
            if kid in lower_ids and conf > self.p.partial_conf_th:
                visible_lower += 1

        if bottom_touch and (visible_lower < 2):
            return True

        m = self.p.partial_edge_margin
        on_side_edge = (x1 <= m) or (x2 >= (1.0 - m))
        very_big = (w >= self.p.partial_big_w) or (h >= self.p.partial_big_h)

        if on_side_edge and very_big:
            return True

        return False

    def _compute_stillness(
        self,
        cx: float,
        cy: float,
        kps_list: List[Dict[str, Any]],
    ) -> Tuple[Optional[float], Optional[float], bool]:
        center_move = None
        kp_move = None

        if self.prev_cx is not None and self.prev_cy is not None:
            dcx = cx - self.prev_cx
            dcy = cy - self.prev_cy
            center_move = (dcx * dcx + dcy * dcy) ** 0.5

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
        """한 프레임의 관측 데이터를 분석하여 상태 변화 및 이벤트를 발생시킵니다."""
        ts = float(obs.get("ts", 0.0))
        if ts < self.cooldown_until_ts:
            return None

        frame_index = int(obs.get("frame_index", -1))
        tracks = obs.get("tracks") or []

        is_lost = False
        if not tracks:
            is_lost = True
        elif not tracks[0].get("has_person", False):
            is_lost = True
        elif float(tracks[0].get("quality_score") or 0.0) < self.p.min_quality:
            is_lost = True

        if is_lost:
            self.present_streak = 0
            self.enter_acc_s = 0.0
            self.posture_acc_s = 0.0
            self.shoulder_acc_s = 0.0
            return None

        t0 = tracks[0]
        bbox = t0.get("bbox")
        self.last_known_bbox = bbox

        aspect = bbox_aspect(bbox)
        cx = bbox_center_x(bbox)
        cy = bbox_center_y(bbox)
        if aspect is None or cx is None or cy is None:
            self.reset_all()
            return None

        h = bbox_h(bbox)
        if h is None:
            self.reset_all()
            return None

        self.height_hist.append((ts, h))
        baseline_h = self._compute_baseline_h(ts)

        quality = float(t0.get("quality_score") or 0.0)
        kps_list = t0.get("keypoints_smooth") or t0.get("keypoints") or []

        self.aspect_hist.append((ts, aspect))
        baseline = self._compute_baseline_aspect(ts)

        vy, dt, drop_signal = self._compute_drop_signal(ts, cy)
        self.present_streak += 1

        if vy is not None and vy > self.p.vy_th:
            self.last_drop_ts = ts
            self.last_drop_vy = vy

        shoulder_vy, shoulder_drop = self._compute_shoulder_drop(ts, kps_list)

        if vy is not None and vy > self.p.vy_th:
            self.last_drop_ts = ts
            self.last_drop_vy = vy

        shoulder_vy, shoulder_drop = self._compute_shoulder_drop(ts, kps_list)

        # 자세 분석 (절대적 수치 또는 상대적 변화)
        rotate_abs = (aspect < self.p.aspect_abs_th)
        rotate_rel = (baseline is not None and aspect < baseline * self.p.aspect_ratio_th)
        posture_signal = rotate_abs or rotate_rel

        is_partial = self._check_partial_body(bbox, kps_list)
        center_move, kp_move, is_still = self._compute_stillness(cx, cy, kps_list)

        self.prev_ts = ts
        self.prev_cx = cx
        self.prev_cy = cy

        if self.state == "NORMAL":
            if self.present_streak < self.p.warmup_frames:
                return None

            if not is_partial and aspect < self.p.force_abnormal_aspect_th:
                self.low_posture_cnt += 1
            else:
                self.low_posture_cnt = 0

            if posture_signal:
                self.posture_acc_s += dt
            else:
                self.posture_acc_s = max(0.0, self.posture_acc_s - dt * 2.0)

            if shoulder_drop:
                self.shoulder_acc_s += dt
            else:
                self.shoulder_acc_s = max(0.0, self.shoulder_acc_s - dt * 2.0)

            height_collapse = False
            if baseline_h is not None and h < baseline_h * self.p.height_ratio_th:
                self.height_acc_s += dt
            else:
                self.height_acc_s = max(0.0, self.height_acc_s - dt * 2.0)

            if self.height_acc_s >= self.p.height_persist_s:
                height_collapse = True

            signal_hits = 0
            if drop_signal:
                signal_hits += 1
            if self.posture_acc_s >= self.p.posture_persist_s:
                signal_hits += 1
            if self.shoulder_acc_s >= self.p.shoulder_drop_persist_s:
                signal_hits += 1
            if height_collapse:
                signal_hits += 1

            strong_low_posture = (self.low_posture_cnt >= self.p.force_abnormal_frames)

            trigger = False
            trigger_reason = ""

            if strong_low_posture:
                trigger = True
                trigger_reason = "low_posture_sustained"
            elif is_partial:
                if (self.shoulder_acc_s >= self.p.shoulder_drop_persist_s) or (aspect < 0.6 and (self.posture_acc_s >= self.p.posture_persist_s)):
                    trigger = True
                    sh = shoulder_vy if shoulder_vy is not None else 0.0
                    trigger_reason = f"partial_persist (sh_vy={sh:.2f}, asp={aspect:.2f})"
            else:
                if signal_hits >= self.p.enter_confirm_need:
                    self.enter_acc_s += dt
                else:
                    self.enter_acc_s = max(0.0, self.enter_acc_s - dt * 2.0)

                if self.enter_acc_s >= self.p.enter_confirm_s:
                    trigger = True
                    trigger_reason = f"confirm_enter hits={signal_hits} acc={self.enter_acc_s:.2f}"

            if trigger:
                self.state = "ABNORMAL"
                self.abnormal_start_ts = ts
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
                self.level1_fired = False
                self.low_posture_cnt = 0

                self.baseline_at_enter = baseline
                self.abnormal_stats = {}
                self.abnormal_confirmed = False

                self.enter_acc_s = 0.0
                self.posture_acc_s = 0.0
                self.shoulder_acc_s = 0.0

                self.baseline_h_at_enter = baseline_h
                height_acc_now = self.height_acc_s
                self.height_acc_s = 0.0

                logger.info(f"[ABNORMAL ENTER] {trigger_reason}")
                return {
                    "type": "abnormal_enter",
                    "ts": ts,
                    "frame_index": frame_index,
                    "signals": {"drop": drop_signal, "posture": posture_signal, "shoulder": shoulder_drop},
                    "values": {
                        "aspect": aspect,
                        "baseline_aspect": baseline,
                        "vy": vy,
                        "quality": quality,
                        "center_move": center_move,
                        "kp_move": kp_move,
                        "reason": trigger_reason,
                        "h": h,
                        "baseline_h": baseline_h,
                        "height_acc_s": height_acc_now,
                    },
                }

            return None

        if self.state == "ABNORMAL":
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

            base = self.baseline_at_enter if self.baseline_at_enter is not None else baseline
            if (base is not None and aspect < base * self.p.confirm_aspect_rel) or (aspect < self.p.confirm_aspect_abs) or (drop_signal and (vy is not None and vy > self.p.vy_th)):
                self.abnormal_confirmed = True

            DECAY = 2.0
            if is_still:
                self.still_acc_s += dt
                self.abnormal_stats["still_count"] += 1
            else:
                self.still_acc_s = max(0.0, self.still_acc_s - dt * DECAY)

            if self.still_acc_s >= self.p.sustain_s and not self.level1_fired:
                self.level1_fired = True
                self.state = "LEVEL1"
                self.level1_ts = ts
                self.cooldown_until_ts = ts + self.p.level1_cooldown_s

                self.is_ghost_mode = False
                self.ghost_start_ts = None
                
                
                conf_score, conf_detail = self._calculate_confidence(abnormal_duration=time_since_abnormal)

                return {
                    "type": "level1",
                    "ts": ts,
                    "frame_index": frame_index,
                    "reason": "still_for_sustain_s",
                    "values": {
                        "still_acc_s": self.still_acc_s,
                        "time_since_abnormal": time_since_abnormal,
                        "confirmed": self.abnormal_confirmed,
                        "confidence_score": conf_score,
                        "confidence_detail": conf_detail,
                    },
                }

            if time_since_abnormal >= self.p.abnormal_timeout_s and not self.level1_fired:
                if (not self.p.timeout_requires_confirm) or self.abnormal_confirmed:
                    self.level1_fired = True
                    self.state = "LEVEL1"
                    self.level1_ts = ts
                    self.cooldown_until_ts = ts + self.p.level1_cooldown_s

                    self.is_ghost_mode = False
                    self.ghost_start_ts = None

                    return {
                        "type": "level1",
                        "ts": ts,
                        "frame_index": frame_index,
                        "reason": "abnormal_timeout",
                        "values": {
                            "time_since_abnormal": time_since_abnormal,
                            "confirmed": self.abnormal_confirmed,
                            "center_move": center_move,
                            "kp_move": kp_move,
                        },
                    }
                else:
                    logger.info(f"[Timeout suppressed] abnormal_s={time_since_abnormal:.1f} but not confirmed. keep ABNORMAL")

            if time_since_abnormal < self.p.recover_grace_s:
                return None

            
            base = self.baseline_at_enter if self.baseline_at_enter is not None else baseline

            
            recover_ratio = min(self.p.aspect_recover_ratio, 0.80)
            recovered_posture = (base is not None and aspect > base * recover_ratio)

            
            recovered_upward = (vy is not None and vy < -0.12)

            
            recovered_move = (center_move is not None and center_move > 0.03)

            
            
            recovered_now = recovered_posture or (recovered_upward and (recovered_posture or recovered_move))

            
            if recovered_now:
                self.recover_acc_s += dt
            else:
                
                self.recover_acc_s = max(0.0, self.recover_acc_s - dt * 1.5)


            if self.recover_acc_s >= self.p.recover_s:
                self.state = "NORMAL"
                self.abnormal_start_ts = None
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
                self.level1_fired = False

                self.enter_acc_s = 0.0
                self.posture_acc_s = 0.0
                self.shoulder_acc_s = 0.0
                self.abnormal_confirmed = False

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

            return None

        if self.state == "LEVEL1":
            if self.level1_ts is not None and (ts - self.level1_ts) >= self.p.level1_hold_s:
                self.state = "NORMAL"
                self.abnormal_start_ts = None
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
                self.level1_fired = False
                self.is_ghost_mode = False
                self.ghost_start_ts = None
                self.abnormal_confirmed = False
                self.enter_acc_s = 0.0
                self.posture_acc_s = 0.0
                self.shoulder_acc_s = 0.0
            return None

        return None

    def _calculate_confidence(self, abnormal_duration: float) -> Tuple[float, Dict[str, float]]:
        """
        낙상 신뢰도 점수 계산 (0~100점)
        
        가중치 (detail.md 기준):
          1. 신호 강도 (40%): max_vy, min_aspect
          2. 정지 상태 (40%): still_count 비율
          3. 탐지 품질 (20%): avg_quality
        """
        if not self.abnormal_stats:
            return 0.0, {}

        s = self.abnormal_stats
        
        
        
        max_vy = s.get("max_vy", 0.0)
        score_vy = min(20.0, max(0.0, (max_vy - 0.5) * 20.0))  

        
        min_aspect = s.get("min_aspect", 1.0)
        
        score_posture = min(20.0, max(0.0, (1.0 - min_aspect) * 33.3)) 
        
        score_signal = score_vy + score_posture

        
        
        total_count = s.get("count", 1)
        still_count = s.get("still_count", 0)
        ratio_still = still_count / total_count if total_count > 0 else 0.0
        score_still = ratio_still * 40.0

        
        avg_quality = s.get("quality_sum", 0.0) / total_count if total_count > 0 else 0.0
        score_qual = min(20.0, avg_quality * 20.0) 

        total_score = score_signal + score_still + score_qual
        
        detail = {
            "score_signal": round(score_signal, 1),
            "score_still": round(score_still, 1),
            "score_qual": round(score_qual, 1),
            "raw_max_vy": round(max_vy, 2),
            "raw_min_aspect": round(min_aspect, 2),
            "raw_ratio_still": round(ratio_still, 2),
        }
        
        return round(total_score, 1), detail


@dataclass
class PresenceParams:
    enter_hits: int = 5          
    exit_hits: int = 10          
    min_quality: float = 0.10    
    cool_down_s: float = 0.5     

class PresenceEngine:
    """재실(Presence) 감지 엔진: 사람의 출입을 판단합니다."""
    def __init__(self, p: PresenceParams):
        self.p = p
        self.state = "ABSENT"   
        self.present_cnt = 0
        self.absent_cnt = 0
        self.last_emit_ts: float = 0.0

    def reset(self):
        """내부 카운터를 초기화합니다."""
        self.state = "ABSENT"
        self.present_cnt = 0
        self.absent_cnt = 0
        self.last_emit_ts = 0.0

    def _is_present(self, obs: Dict[str, Any]) -> bool:
        """현재 프레임에 사람이 유효하게 존재하는지 확인합니다."""
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
        """재실 상태 전이를 판단하고 이벤트를 반환합니다."""
        ts = float(obs.get("ts", 0.0))
        present = self._is_present(obs)

        if present:
            self.present_cnt += 1
            self.absent_cnt = 0
        else:
            self.absent_cnt += 1
            self.present_cnt = 0

        
        if self.p.cool_down_s > 0 and (ts - self.last_emit_ts) < self.p.cool_down_s:
            return None

        if self.state == "ABSENT" and self.present_cnt >= self.p.enter_hits:
            self.state = "PRESENT"
            self.last_emit_ts = ts
            return {
                "kind": "vision",
                "device_id": DEVICE_ID,
                "data": {"location_id": LOCATION_ID, "event": "enter"},
                "ts": ts,
            }

        if self.state == "PRESENT" and self.absent_cnt >= self.p.exit_hits:
            self.state = "ABSENT"
            self.last_emit_ts = ts
            return {
                "kind": "vision",
                "device_id": DEVICE_ID,
                "data": {"location_id": LOCATION_ID, "event": "exit"},
                "ts": ts,
            }

        return None