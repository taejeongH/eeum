from dataclasses import dataclass
from typing import Optional, Dict, Any, List, Tuple

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

    # Promote to LEVEL1 when still for sustain_s while in ABNORMAL (Level0)
    sustain_s: float = 10.0

    # Recovery to NORMAL
    recover_s: float = 2.0
    aspect_recover_ratio: float = 0.80

    # Stillness thresholds (normalized coordinates 0~1)
    still_center_th: float = 0.010   # bbox center movement per frame (norm)
    still_kp_th: float = 0.008       # mean keypoint movement per frame (norm)
    still_need_kp: bool = True       # use keypoint movement in stillness 판단

    recover_center_move_th: float = 0.03  # 이 이상 움직여야 '회복 움직임'
    recover_need_move: bool = True

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

    def reset_all(self):
        self.state = "NORMAL"
        self.abnormal_start_ts = None
        self.level1_fired = False

        self.recover_acc_s = 0.0
        self.still_acc_s = 0.0

        self.prev_ts = None
        self.prev_cx = None
        self.prev_cy = None
        self.prev_kps = None

        self.drop_hits.clear()
        self.aspect_hist.clear()

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
        Returns: (vy, dt, drop_signal)
        vy: bbox center y velocity (normalized units per second)
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

    def _compute_stillness(
        self,
        cx: float,
        cy: float,
        kps_list: List[Dict[str, Any]],
    ) -> Tuple[Optional[float], Optional[float], bool]:
        """
        Returns: (center_move, kp_move, is_still)
        center_move: bbox center movement magnitude (norm units) between frames
        kp_move: mean keypoint movement magnitude (norm units) between frames
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

        # stillness (needs prev values; do before updating prev_ts/cx/cy)
        center_move, kp_move, is_still = self._compute_stillness(cx, cy, kps_list)

        # update prev values (after computing deltas)
        self.prev_ts = ts
        self.prev_cx = cx
        self.prev_cy = cy

        # ---- STATE MACHINE ----
        if self.state == "NORMAL":
            if drop_signal or posture_signal:
                self.state = "ABNORMAL"  # Level0
                self.abnormal_start_ts = ts
                self.recover_acc_s = 0.0
                self.still_acc_s = 0.0
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
            # Stillness accumulate (10초 정지면 LEVEL1)
            if is_still:
                self.still_acc_s += dt
            else:
                self.still_acc_s = 0.0

            # Recovery accumulate (자세 회복이면 NORMAL 복귀)
            recovered_posture = (
                baseline is not None
                and aspect > baseline * self.p.aspect_recover_ratio
            )

            recovered_move = (
                center_move is not None
                and center_move > self.p.recover_center_move_th
            )

            recovered_now = recovered_posture and (
                recovered_move if self.p.recover_need_move else True
            )

            if recovered_now:
                self.recover_acc_s += dt
            else:
                self.recover_acc_s = 0.0

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
                        "vy": vy,
                        "quality": quality,
                        "center_move": center_move,
                        "kp_move": kp_move,
                        "still_acc_s": self.still_acc_s,
                    },
                }

            # Promote to LEVEL1 when still for sustain_s
            if self.still_acc_s >= self.p.sustain_s and not self.level1_fired:
                self.level1_fired = True
                self.state = "LEVEL1"
                return {
                    "type": "level1",
                    "ts": ts,
                    "frame_index": frame_index,
                    "reason": "still_after_abnormal",
                    "values": {
                        "aspect": aspect,
                        "baseline_aspect": baseline,
                        "vy": vy,
                        "quality": quality,
                        "center_move": center_move,
                        "kp_move": kp_move,
                        "still_acc_s": self.still_acc_s,
                    },
                }

        # Optional: in LEVEL1, you might keep returning None to avoid spamming.
        # If you want "recover from LEVEL1", define policy here.
        return None
