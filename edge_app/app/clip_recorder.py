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
        # 버퍼 크기: PRE_SEC + (abnormal_enter ~ level1 대기 시간) + EVENT_POST_SEC + 여유
        # abnormal_enter부터 level1까지 최대 10초 + 여유 5초
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
        동적 해상도 기반 VideoWriter 생성 (다중 코덱 재시도)
        
        Args:
            path: 저장 경로
            fps: 프레임레이트
            frame_shape: (H, W, C) 형태의 프레임 형상
        
        Returns:
            cv2.VideoWriter 인스턴스
        """
        import logging
        
        h, w = frame_shape[:2]
        
        # 유효성 검사
        if w <= 0 or h <= 0:
            logging.error(f"[CLIP] Invalid frame dimensions: {w}x{h}")
            raise ValueError(f"Invalid frame dimensions: {w}x{h}")
        
        # fps 범위 검증
        fps = float(fps)
        if fps <= 0 or fps > 240:
            logging.warning(f"[CLIP] FPS out of range: {fps}, clamping to [5, 240]")
            fps = max(5.0, min(240.0, fps))
        
        # 코덱 재시도 목록 (우선순위 순)
        codecs = [
            ("mp4v", "mp4v"),
            ("MJPG", "mjpg"),
            ("XVID", "xvid"),
            ("DIVX", "divx"),
            ("MPEG", "mpeg"),
        ]
        
        for codec_name, codec_code in codecs:
            try:
                fourcc = cv2.VideoWriter_fourcc(*codec_code)
                writer = cv2.VideoWriter(path, fourcc, fps, (w, h))
                
                # VideoWriter 유효성 검증
                if writer.isOpened():
                    logging.info(f"[CLIP] VideoWriter opened with codec '{codec_name}': {w}x{h} @ {fps}fps")
                    return writer
                else:
                    logging.warning(f"[CLIP] Codec '{codec_name}' failed to open: {w}x{h} @ {fps}fps")
                    writer.release()
            except Exception as e:
                logging.warning(f"[CLIP] Codec '{codec_name}' error: {e}")
                continue
        
        logging.error(f"[CLIP] All codecs failed for {w}x{h} @ {fps}fps. Creating dummy writer.")
        # 마지막 시도: 기본 코덱
        writer = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*"mp4v"), fps, (w, h))
        return writer

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
            
            import logging
            
            # VideoWriter 검증
            if not writer.isOpened():
                logging.error(
                    f"[CLIP] VideoWriter failed to open: path={path}, "
                    f"dimensions={first_frame.shape[1]}x{first_frame.shape[0]}, fps={eff_fps}"
                )
                writer.release()
                return None
            
            # writer.get()이 정확하지 않을 수 있으므로 frame shape 사용
            writer_w = first_frame.shape[1]  # 너비
            writer_h = first_frame.shape[0]  # 높이
            
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

            return path
