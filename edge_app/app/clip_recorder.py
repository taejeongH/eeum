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

    def push(self, ts: float, frame_bgr):
        with self.lock:
            self.buffer.append((ts, frame_bgr.copy()))

    def _open_writer(self, path: str):
        fourcc = cv2.VideoWriter_fourcc(*"mp4v")
        return cv2.VideoWriter(path, fourcc, float(CLIP_FPS), (FRAME_W, FRAME_H))

    def start(self, ts_now: float) -> Optional[str]:
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
            self.writer = self._open_writer(self.clip_path)

            # 과거 프레임 저장
            for _, fr in list(self.buffer):
                if fr.shape[1] != FRAME_W or fr.shape[0] != FRAME_H:
                    fr = cv2.resize(fr, (FRAME_W, FRAME_H))
                self.writer.write(fr)

            return self.clip_path

    def update(self, frame_bgr) -> Optional[str]:
        with self.lock:
            if not self.recording or self.writer is None:
                return None

            fr = frame_bgr
            if fr.shape[1] != FRAME_W or fr.shape[0] != FRAME_H:
                fr = cv2.resize(fr, (FRAME_W, FRAME_H))

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
                self.clip_path = None  # 다음 이벤트를 위해 비워둠(원하면 유지해도 됨)
                return finished_path

            return None

    def status(self):
        with self.lock:
            return {
                "clip_recording": self.recording,
                "clip_post_frames_remaining": self.post_remaining,
                "clip_path": self.clip_path,
                "clip_last_started_ts": self.last_started_ts,
                "buffer_len": len(self.buffer),
            }

    def save_segment(
        self,
        incident_ts: float,
        pre_sec: float,
        post_sec: float,
        filename_prefix: str = "incident",
    ) -> Optional[str]:
        print(
            f"[CLIP DBG] buffer_range=({self.buffer[0][0]:.2f} ~ {self.buffer[-1][0]:.2f}) "
            f"incident_ts={incident_ts:.2f} "
            f"want=({incident_ts-pre_sec:.2f} ~ {incident_ts+post_sec:.2f}) "
            f"buffer_len={len(self.buffer)}"
        )

        with self.lock:
            if len(self.buffer) == 0:
                return None

            t_start = incident_ts - float(pre_sec)
            t_end = incident_ts + float(post_sec)

            # 버퍼에서 시간 구간에 해당하는 프레임만 추출
            frames = []
            for ts, fr in list(self.buffer):
                if t_start <= ts <= t_end:
                    if fr.shape[1] != FRAME_W or fr.shape[0] != FRAME_H:
                        fr = cv2.resize(fr, (FRAME_W, FRAME_H))
                    frames.append(fr)

            if not frames:
                return None

            clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.mp4")

            writer = self._open_writer(path)
            try:
                for fr in frames:
                    writer.write(fr)
            finally:
                try:
                    writer.release()
                except Exception:
                    pass

            return path
