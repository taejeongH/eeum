import os
import cv2
import time
import threading
import collections
from datetime import datetime
from typing import Optional, Deque, Tuple, Any

from .config import (
    FRAME_W, FRAME_H,
    CLIP_DIR, CLIP_FPS, CLIP_PRE_SEC, CLIP_POST_SEC,
    CLIP_COOLDOWN_S, CLIP_EVENT_POST_SEC
)

os.makedirs(CLIP_DIR, exist_ok=True)

class ClipRecorder:
    """
    Level1 발생 시 "과거 PRE_SEC + 이후 POST_SEC" 프레임을 mp4로 저장하는 클래스.
    - 내부에 링버퍼를 가지고 있다.
    - start() 호출 시 버퍼에 있는 과거 프레임을 먼저 저장한다.
    - update()는 매 프레임 호출되며, 녹화중이면 POST 프레임을 저장한다.
    
    개선사항:
    - save_segment()에 중복 감지 필터링 추가
    - 같은 사건이 반복 저장되는 것 방지
    """

    def __init__(self):
        self.lock = threading.Lock()
        buf_size = CLIP_FPS * (CLIP_PRE_SEC + 25 + CLIP_EVENT_POST_SEC + 2)

        self.buffer: Deque[Tuple[float, Any]] = collections.deque(maxlen=buf_size)

        self.recording = False
        self.writer = None
        self.post_remaining = 0
        self.clip_path: Optional[str] = None
        self.last_started_ts: float = 0.0
        
        # 중복 저장 방지용
        self.last_saved_segment_ts: float = 0.0  # 마지막 저장된 segment의 시작 시간
        self.save_segment_cooldown: float = 3.0   # 3초 내 중복 저장 방지

    def push(self, ts: float, frame_bgr):
        with self.lock:
            self.buffer.append((ts, frame_bgr.copy()))

    def _open_writer(self, path: str, fps: float, frame_shape: Tuple[int, int, int]):
        """
        동적 해상도 기반 VideoWriter 생성
        
        Args:
            path: 저장 경로
            fps: 프레임레이트
            frame_shape: (H, W, C) 형태의 프레임 형상
        
        Returns:
            cv2.VideoWriter 인스턴스
        """
        h, w = frame_shape[:2]
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        return cv2.VideoWriter(path, fourcc, float(fps), (w, h))

    def start(self, ts_now: float) -> Optional[str]:
        """
        ⚠️ NOTE: 현재 시스템에서는 사용되지 않음
        
        이 메서드는 연속 녹화형 인터페이스 (start → update → stop 흐름)
        현재 구현은 save_segment() 기반 이벤트 클립 저장만 사용
        
        향후 라이브 녹화 기능 추가 시 활용 예정
        """
        with self.lock:
            if (ts_now - self.last_started_ts) < CLIP_COOLDOWN_S:
                return None
            if self.recording:
                return None
            if len(self.buffer) == 0:
                return None

            self.last_started_ts = ts_now
            self.recording = True
            self.post_remaining = int(CLIP_POST_SEC * CLIP_FPS)

            clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            self.clip_path = os.path.join(CLIP_DIR, f"level1_{clip_id}.mp4")
            
            # 첫 프레임 기준으로 writer 생성 (해상도 동적 결정)
            _, first_frame = list(self.buffer)[0]
            self.writer = self._open_writer(self.clip_path, CLIP_FPS, first_frame.shape)

            # 과거 프레임 저장
            for _, fr in list(self.buffer):
                if fr.shape[1] != self.writer.get(cv2.CAP_PROP_FRAME_WIDTH) or \
                   fr.shape[0] != self.writer.get(cv2.CAP_PROP_FRAME_HEIGHT):
                    h, w = self.writer.get(cv2.CAP_PROP_FRAME_HEIGHT), self.writer.get(cv2.CAP_PROP_FRAME_WIDTH)
                    fr = cv2.resize(fr, (int(w), int(h)))
                self.writer.write(fr)

            return self.clip_path

    def update(self, frame_bgr) -> Optional[str]:
        """
        ⚠️ NOTE: 현재 시스템에서는 사용되지 않음
        
        연속 녹화 중 POST 프레임을 저장하는 메서드
        현재는 save_segment() 기반만 사용
        """
        with self.lock:
            if not self.recording or self.writer is None:
                return None

            fr = frame_bgr
            if fr.shape[1] != self.writer.get(cv2.CAP_PROP_FRAME_WIDTH) or \
               fr.shape[0] != self.writer.get(cv2.CAP_PROP_FRAME_HEIGHT):
                w = int(self.writer.get(cv2.CAP_PROP_FRAME_WIDTH))
                h = int(self.writer.get(cv2.CAP_PROP_FRAME_HEIGHT))
                fr = cv2.resize(fr, (w, h))

            self.writer.write(fr)
            self.post_remaining -= 1

            if self.post_remaining <= 0:
                try:
                    self.writer.release()
                except Exception:
                    pass
                self.writer = None
                self.recording = False

                finished_path = self.clip_path
                self.clip_path = None
                return finished_path

            return None

    def status(self):
        with self.lock:
            return {
                "clip_recording": self.recording,
                "clip_post_frames_remaining": self.post_remaining,
                "clip_path": self.clip_path,
                "clip_last_started_ts": self.last_started_ts,
                "clip_last_saved_segment_ts": self.last_saved_segment_ts,
                "buffer_len": len(self.buffer),
            }

    def save_segment(self, incident_ts: float, pre_sec: float, post_sec: float,
                 filename_prefix: str = "incident") -> Optional[str]:
        """
        버퍼에서 [incident_ts - pre_sec, incident_ts + post_sec] 범위 추출 후 저장
        
        개선사항:
        - 중복 저장 방지: 3초 내에 같은 구간이 저장되면 무시
        - 실제 낙상 시작 시간(incident_ts) 기준으로 영상 저장
        - 첫 프레임 기준으로 동적 해상도 결정
        
        Args:
            incident_ts: 낙상 의심 시작 시간 (abnormal_enter 시점) - 가장 정확한 기준
            pre_sec: 사건 전 기록 시간
            post_sec: 사건 후 기록 시간
            filename_prefix: 저장 파일명 접두사
        
        Returns:
            저장된 파일 경로 또는 None (중복/실패 시)
        """

        with self.lock:
            if len(self.buffer) == 0:
                return None

            # ---------------------------------------------------
            # 중복 저장 방지: 실시간 기준 3초 내 재저장 차단
            # (더 안정적인 기준: incident_ts 대신 현재 실시간 사용)
            # ---------------------------------------------------
            now = time.time()
            time_since_last_save = now - self.last_saved_segment_ts
            if time_since_last_save < self.save_segment_cooldown:
                import logging
                logging.warning(
                    f"[CLIP] Duplicate save attempt ignored: "
                    f"last_saved={self.last_saved_segment_ts:.2f}, "
                    f"now={now:.2f}, "
                    f"cooldown={self.save_segment_cooldown}s"
                )
                return None
            
            self.last_saved_segment_ts = now

            t_start = incident_ts - float(pre_sec)
            t_end = incident_ts + float(post_sec)

            picked = []  # (ts, frame)
            for ts, fr in list(self.buffer):
                if t_start <= ts <= t_end:
                    picked.append((ts, fr))

            if not picked:
                import logging
                buf_ts_start = self.buffer[0][0]
                buf_ts_end = self.buffer[-1][0]
                logging.warning(
                    f"[CLIP] No frames in range [{t_start:.2f}, {t_end:.2f}] "
                    f"(buffer available: [{buf_ts_start:.2f}, {buf_ts_end:.2f}])"
                )
                return None

            # 구간의 "실제 fps" 추정 (재생 길이 보존 목적)
            ts0 = picked[0][0]
            ts1 = picked[-1][0]
            span = max(1e-6, ts1 - ts0)
            eff_fps = len(picked) / span

            # 너무 튀지 않게 클램프(선택)
            eff_fps = max(5.0, min(float(CLIP_FPS), eff_fps))

            clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.mp4")

            # 첫 프레임 기준으로 writer 생성 (동적 해상도)
            first_frame = picked[0][1]
            writer = self._open_writer(path, eff_fps, first_frame.shape)
            writer_w = int(writer.get(cv2.CAP_PROP_FRAME_WIDTH))
            writer_h = int(writer.get(cv2.CAP_PROP_FRAME_HEIGHT))

            import logging
            logging.info(
                f"[CLIP] Saving segment: "
                f"incident_ts={incident_ts:.2f} "
                f"range=[{t_start:.2f}, {t_end:.2f}] "
                f"frames={len(picked)} "
                f"eff_fps={eff_fps:.1f} "
                f"resolution={writer_w}x{writer_h} "
                f"path={path}"
            )

            try:
                for _, fr in picked:
                    # 동적으로 결정된 writer 해상도에 맞춰 resize
                    if fr.shape[1] != writer_w or fr.shape[0] != writer_h:
                        fr = cv2.resize(fr, (writer_w, writer_h))
                    writer.write(fr)
            finally:
                try:
                    writer.release()
                except Exception:
                    pass

            return path
