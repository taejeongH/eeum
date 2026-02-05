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
    aspect_abs_th: float = 1.0     # 1.2 -> 1.0 (앉았을 때 오탐지 방지, 확실히 누운 경우만)
    aspect_ratio_th: float = 0.55

    # ⭐ 핵심: Level 0에서 일정 시간 이상 정상 회복 못하면 Level 1 승격
    sustain_s: float = 20.0  # 정지 상태 누적 시간 (10s -> 20s)
    abnormal_timeout_s: float = 30.0  # Level 0 상태 타임아웃 (10s -> 30s)

    # Recovery to NORMAL - 5초에서 3초로 변경 (너무 엄격한 조건은 독거 노인에게 맞지 않음)
    # [수정] 3.0초 -> 2.0초로 더 빠르게 복귀 & 임계값 완화
    recover_s: float = 2.0
    aspect_recover_ratio: float = 0.9  # baseline의 90% 정도만 회복해도 인정
    recover_center_move_th: float = 0.10  # 매우 큰 움직임만 "회복"으로 인정

    # Stillness thresholds
    still_center_th: float = 0.010
    still_kp_th: float = 0.020
    still_need_kp: bool = True
    recover_need_move: bool = True

    # Partial body (Near camera)
    partial_min_y2: float = 0.95   # bbox y2가 이보다 크면 하단 잘림 의심
    partial_conf_th: float = 0.3   # 하체 키포인트 신뢰도 임계값
    partial_vy_relax: float = 0.6  # 상반신만 보일 때 허용할 완화된 속도 계수 (비율 상향: 0.4 -> 0.6)

    # Low Posture Persistence (Forced ABNORMAL entry for slow collapse)
    force_abnormal_aspect_th: float = 0.7  # 이 비율보다 낮으면 "매우 낮은 자세"로 간주
    force_abnormal_frames: int = 45        # N프레임(약 2~3초) 이상 유지되면 즉시 위험 진입 (15 -> 45)

    # Ghost Tracking (Occlusion/Exit during Abnormal)
    ghost_timeout_s: float = 15.0    # 소실 후 이 시간 동안 Level1 진입 대기 (5s -> 15s)
    screen_edge_margin: float = 0.05 # 화면 가장자리 판정 비율 (5%)


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
        
        # Head drop continuity counter
        self.head_drop_cnt = 0
        
        # Ghost Tracking State
        self.ghost_start_ts: Optional[float] = None  # 소실 시작 시간
        self.is_ghost_mode: bool = False           # 고스트 모드 활성화 여부
        self.last_known_bbox: Optional[List[float]] = None # 소실 직전 좌표

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
        self.head_drop_cnt = 0
        
        self.ghost_start_ts = None
        self.is_ghost_mode = False
        self.last_known_bbox = None

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

    def _check_on_edge(self, bbox: List[float], width: float = 1.0, height: float = 1.0) -> bool:
        """
        BBox가 화면 가장자리에 닿았는지 확인 (단순 퇴장 구분용)
        좌표계는 0.0 ~ 1.0 정규화 가정
        """
        if not bbox: return False
        x1, y1, x2, y2 = bbox
        m = self.p.screen_edge_margin
        
        # 좌, 우, 상, 하 (하단 제외? 낙상은 바닥으로 가니까)
        # 보통 퇴장은 좌/우/상단(멀어짐)
        on_left = x1 < m
        on_right = x2 > (1.0 - m)
        on_top = y1 < m
        on_bottom = y2 > (1.0 - m)
        
        return on_left or on_right or on_top or on_bottom

    def _is_vertical_torso(self, kps_list: List[Dict[str, Any]]) -> bool:
        """
        상체(어깨-골반)가 수직에 가까운지 확인 (앉아있는 자세 판별용)
        True: 수직에 가까움 (Sitting/Standing)
        False: 수평에 가깝거나 기울어짐 (Lying/Collapsed/Leaning)
        """
        def get_kp(id):
            return next((k for k in kps_list if int(k.get("id", -1)) == id), None)

        # Shoulders (5, 6), Hips (11, 12)
        ls, rs = get_kp(5), get_kp(6)
        lh, rh = get_kp(11), get_kp(12)
        
        # 4개 점 중 3개 이상 있어야 판단 가능
        valid_cnt = sum([1 for k in [ls, rs, lh, rh] if k is not None])
        if valid_cnt < 3:
            return False # 정보 부족하면 일단 False (보수적)

        # Mid-Shoulder
        sx, sy, sc = 0.0, 0.0, 0
        if ls: sx += float(ls["x"]); sy += float(ls["y"]); sc += 1
        if rs: sx += float(rs["x"]); sy += float(rs["y"]); sc += 1
        if sc == 0: return False
        ms_x, ms_y = sx / sc, sy / sc

        # Mid-Hip
        hx, hy, hc = 0.0, 0.0, 0
        if lh: hx += float(lh["x"]); hy += float(lh["y"]); hc += 1
        if rh: hx += float(rh["x"]); hy += float(rh["y"]); hc += 1
        if hc == 0: return False
        mh_x, mh_y = hx / hc, hy / hc

        if mh_y <= ms_y: # 골반이 어깨보다 위에 있거나 같으면? (물구나무 or 누움) -> Not Vertical
             return False

        dx = abs(ms_x - mh_x)
        dy = abs(ms_y - mh_y)
        
        # 수직이면 dy가 dx보다 훨씬 커야 함
        # 각도 45도 기준: dy > dx
        # 각도 30도 기준: dy > 1.73 * dx (더 엄격)
        # Sitting: 거의 0~20도 -> dy >> dx
        # Slumped: 45도 이상 기울어짐 -> dy ~ dx or dy < dx
        
        # [판단 기준] dy > dx * 1.5 (약 33도 이내)
        return dy > (dx * 1.5)

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

        # 4. 근접 페널티 (Partial Penalty)
        partial_cnt = stats.get("partial_count", 0)
        ratio_partial = partial_cnt / cnt if cnt > 0 else 0.0
        penalty = 0.0
        if ratio_partial > 0.5:
             # 절반 이상이 상반신만 보였다면 의심스럽기 때문에 점수 차감
             penalty = 20.0 * ratio_partial

        # 가중치 합산
        # 신호 40%, 정지 상태 40%, 품질 20% - 페널티
        final_score = (score_signal * 0.4) + (score_still * 0.4) + (score_qual * 0.2) - penalty
        final_score = max(0.0, final_score)
        
        detail = {
            "score_signal": score_signal,
            "score_still": score_still,
            "score_qual": score_qual,
            "stats": stats.copy()  # 통계 복사
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
        
        # --- 고스트 트래킹 (객체 소실 대응) ---
        tracks = obs.get("tracks") or []
        
        # 트랙이 없거나(소실), 품질이 너무 낮으면(사실상 없는 셈)
        is_lost = False
        if not tracks:
            is_lost = True
        elif not tracks[0].get("has_person", False):
            is_lost = True
        elif float(tracks[0].get("quality_score") or 0.0) < self.p.min_quality:
            is_lost = True
            
        if is_lost:
            # 1. 고스트 모드 진입 조건: 이미 ABNORMAL 상태여야 함
            if self.state == "ABNORMAL":
                if not self.is_ghost_mode:
                    self.is_ghost_mode = True
                    self.ghost_start_ts = ts
                    import logging
                    logging.getLogger(__name__).info(f"[Ghost] Object lost in ABNORMAL state. Tracking started at {ts:.2f}")

                # 2. 고스트 타이머 체크
                time_in_ghost = ts - self.ghost_start_ts if self.ghost_start_ts else 0.0
                
                # 타임아웃 도달 시 -> 최종 낙상(Level1)으로 확정
                # (장애물 뒤로 넘어져서 안 일어나는 상황으로 간주)
                if time_in_ghost >= self.p.ghost_timeout_s and not self.level1_fired:
                    self.level1_fired = True
                    self.state = "LEVEL1"
                    
                    # [Fix] Ghost 타임아웃 시에도 신뢰도 계산 포함
                    conf_score, conf_detail = self._compute_confidence()
                    
                    return {
                        "type": "level1",
                        "ts": ts,
                        "frame_index": frame_index,
                        "reason": "ghost_timeout_while_abnormal",
                        "values": {
                             "time_in_ghost": time_in_ghost,
                             "last_bbox": self.last_known_bbox,
                             "confidence_score": conf_score,
                             "confidence_detail": conf_detail
                        }
                    }
                return None # 계속 대기
            
            else:
                # Normal 상태에서 소실되었지만, 낙상하면서 나간 경우인지 체크 (Boundary Fall)
                # 1) 마지막 위치가 테두리 근처
                is_edge_exit = self._check_on_edge(self.last_known_bbox) if self.last_known_bbox else False
                # 2) 최근 급격한 하강 신호가 있었는지 (drop_hits에 기록이 남아있는지)
                recent_drop = len(self.drop_hits) > 0

                if is_edge_exit and recent_drop:
                    # [Boundary Fall Trigger]
                    # 낙상하면서 화면 밖으로 나간 것으로 의심 -> 강제 ABNORMAL & Ghost 진입
                    self.state = "ABNORMAL"
                    self.is_ghost_mode = True
                    self.ghost_start_ts = ts
                    self.abnormal_start_ts = ts  # ABNORMAL 시작 시간 기록
                    
                    import logging
                    logger = logging.getLogger(__name__)
                    logger.info(f"[Boundary Fall] Object exited at edge with drop signal. Force ABNORMAL & Ghost at {ts:.2f}")
                    
                    # [수정] 녹화 시작을 위해 abnormal_enter 이벤트 반환
                    return {
                        "type": "abnormal_enter",
                        "ts": ts,
                        "frame_index": frame_index,
                        "reason": "boundary_fall_trigger",
                        "values": {
                             "last_bbox": self.last_known_bbox
                        }
                    }
                
                # 그 외 단순 퇴장 -> 리셋
                self.reset_all()
                return None

        # --- 객체/형체 존재 시 로직 진행 ---
        # 고스트 해제 (다시 나타남)
        if self.is_ghost_mode:
            self.is_ghost_mode = False
            self.ghost_start_ts = None
            import logging
            logging.getLogger(__name__).info("[Ghost] Object reappeared. Resume tracking.")

        t0 = tracks[0]
        quality = float(t0.get("quality_score") or 0.0)
        # (이하 기존 t0 처리 로직) -> 제거 필요 (위에서 이미 체크함)
        
        bbox = t0.get("bbox")
        self.last_known_bbox = bbox # 마지막 위치 기억
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

        # 머리 위치 하강 여부 logic with BBox Fallback
        head_down = False
        
        # Keypoints Check: 관절이 충분히 잡혀있는가?
        has_kps = (len(kps_list) > 5)
        
        if has_kps:
            # [기존 로직] 스켈레톤 기반 정밀 분석
            if self.prev_kps:
                def get_kp(id):
                    return next((k for k in kps_list if int(k.get("id", -1)) == id), None)

                curr_nose = get_kp(0)
                prev_nose = self.prev_kps.get(0)
                
                # --- 고개 흔들림/끄덕임 오탐지 방지 로직 ---
                # (중략) 기존 로직 유지
                if curr_nose and prev_nose:
                    ny, py = float(curr_nose["y"]), float(prev_nose[1])
                    nx, px = float(curr_nose["x"]), float(prev_nose[0])
                    dy = ny - py
                    dx = abs(nx - px)
                    
                    is_shaking = dx > abs(dy) * 1.5
                    is_nodding = False
                    
                    curr_ls, curr_rs = get_kp(5), get_kp(6)
                    prev_ls, prev_rs = self.prev_kps.get(5), self.prev_kps.get(6)
                    shoulder_dy_sum = 0.0
                    shoulder_cnt = 0
                    if curr_ls and prev_ls:
                        shoulder_dy_sum += (float(curr_ls["y"]) - float(prev_ls[1]))
                        shoulder_cnt += 1
                    if curr_rs and prev_rs:
                        shoulder_dy_sum += (float(curr_rs["y"]) - float(prev_rs[1]))
                        shoulder_cnt += 1
                    
                    if shoulder_cnt > 0:
                        avg_s_dy = shoulder_dy_sum / shoulder_cnt
                        if dy > 0.02 and avg_s_dy < 0.005:
                            is_nodding = True

                    valid_drop = (dy > 0.03) and (not is_shaking) and (not is_nodding)
                    
                    if valid_drop:
                        self.head_drop_cnt += 1
                    else:
                        self.head_drop_cnt = max(0, self.head_drop_cnt - 1)
                    
                    if self.head_drop_cnt >= 3:
                        head_down = True
                else:
                    self.head_drop_cnt = 0
        else:
            # [BBox Fallback] 관절이 없으면 박스 상단(y1)과 중심(cy)으로 추정
            # 스켈레톤 없이도 분석을 지속하기 위함 (어두운 곳 등)
            if drop_signal and posture_signal:
                 # 박스가 찌그러지고(posture) 하강(drop)했으면 head_down으로 간주
                 head_down = True
                 import logging
                 logging.getLogger(__name__).debug(f"[Fallback] Keypoints missng. Used BBox signals: {aspect:.2f}, {vy:.2f}")

        # stillness (needs prev values; do before updating prev_ts/cx/cy)

        # stillness (needs prev values; do before updating prev_ts/cx/cy)
        center_move, kp_move, is_still = self._compute_stillness(cx, cy, kps_list)

        # update prev values (after computing deltas)
        self.prev_ts = ts
        self.prev_cx = cx
        self.prev_cy = cy

        # ---- STATE MACHINE ----
        if self.state == "NORMAL":
            # 속도가 느려도, 자세가 매우 낮게 깔린 상태가 지속되면 위험으로 간주
            # 단, 근접 상황(is_partial)에서는 상반신만 보여 종횡비가 왜곡되므로 제외
            if not is_partial and aspect < self.p.force_abnormal_aspect_th:
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
                # 속도가 어느 정도 있으면서(relaxed_vy_th) 머리가 확실히 내려갔을 때만 진입
                # 근접 상황에서는 aspect ratio 기반인 posture_signal은 오탐지가 많아 제외
                if (vy is not None and vy > relaxed_vy_th) and head_down:
                    trigger = True
            # (C) 일반적인 경우 (Normal Distance)
            else:
                if drop_signal or posture_signal:
                    trigger = True

            if trigger:
                self.state = "ABNORMAL"  # Level0
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
                
                # [추가] Sitting vs Slumped/Fallen 구분
                # aspect가 1.0 이상(애매함)인데 상체가 수직(Vertical)이라면 -> "앉아있는 것"으로 간주하여 발동 막음
                # 단, baseline 대비 55% 이하로 찌그러졌다면(rotate_rel) 수직이라도 위험할 수 있음
                is_vertical = self._is_vertical_torso(kps_list)
                is_low_aspect = (aspect < 1.0) # aspect_abs_th 미만
                is_severe_rel = (baseline is not None and aspect < baseline * 0.55)
                
                # 발동 허용 조건:
                # 1) 자세가 매우 낮거나 (is_low_aspect)
                # 2) 상대적으로 매우 많이 무너졌거나 (is_severe_rel - 웅크림)
                # 3) 상체가 수직이 아니거나 (기울어짐/쓰러짐)
                should_fire = is_low_aspect or is_severe_rel or (not is_vertical)
                
                if should_fire:
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
                            "torso_vertical": is_vertical
                        },
                    }
                else:
                    # 앉아있는 것으로 판단 -> Level1 발동 안 함 (하지만 Abnormal 상태는 유지? 아니면 회복?)
                    # log only once every second
                    if int(ts) % 5 == 0:
                        import logging
                        logging.getLogger(__name__).debug(f"[Sitting Detected] Still({self.still_acc_s:.1f}s) but Torso Vertical. suppress Level1.")

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
            
            # [조건 1] 자세 회복 (Aspect Ratio)
            recovered_posture = (
                baseline is not None
                and aspect > baseline * self.p.aspect_recover_ratio
            )
            
            # [조건 2] 움직임 (Center Move) - 0.10 이상
            recovered_move = (
                center_move is not None
                and center_move > self.p.recover_center_move_th
            )

            # [조건 3] 상승 속도 (Upward Velocity) - vy가 음수이고 일정 크기 이상일 때
            # -0.3 (약간 빠른 상승) 정도
            recovered_upward = (vy is not None and vy < -0.3)

            # AND 조건 개선:
            # 1) 자세와 움직임이 동시에 충족되거나 (기존)
            # 2) 자세가 회복되었고, 확실히 일어나고 있는 중(상승 속도)이거나
            recovered_now = (recovered_posture and recovered_move) or (recovered_posture and recovered_upward)
            
            # [추가] 로그: 회복 시도 감지
            if recovered_now:
                import logging
                logging.getLogger(__name__).info(f"[Recovery Signal] aspect={aspect:.2f}, vy={vy if vy else 0:.2f}, acc={self.recover_acc_s:.1f}/{self.p.recover_s}")

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