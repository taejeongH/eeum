import os
import logging

logger = logging.getLogger(__name__)
import cv2
import time
import numpy as np
import threading
import collections
import json
from datetime import datetime
from typing import Optional, Deque, Tuple, Any, Dict, List  
import subprocess
from pathlib import Path

from ..config import (
    FRAME_W, FRAME_H,
    CLIP_DIR, CLIP_FPS, CLIP_PRE_SEC, CLIP_POST_SEC,
    CLIP_COOLDOWN_S, CLIP_EVENT_POST_SEC,
    CLIP_RESIZE_WIDTH, CLIP_RESIZE_HEIGHT,
    ABNORMAL_TIMEOUT_S, JPEG_QUALITY  
)

os.makedirs(CLIP_DIR, exist_ok=True)

class ClipRecorder:
    """
    Level1 발생 시 "과거 PRE_SEC + 이후 POST_SEC" 프레임을 mp4로 저장하는 클래스.
    - 내부에 링버퍼를 가지고 있다. ([timestamp, jpeg_bytes])
    - push() 시 JPEG 인코딩하여 메모리를 절약한다. (800MB -> 50MB)
    """

    def __init__(self):
        self.lock = threading.Lock()
        
        
        
        
        
        required_duration = ABNORMAL_TIMEOUT_S + CLIP_PRE_SEC + CLIP_POST_SEC + 10.0
        safety_fps = 60.0 
        buf_size = int(safety_fps * required_duration)
        
        self.buffer: Deque[Tuple[float, bytes]] = collections.deque(maxlen=buf_size)
        self.last_push_ts = 0.0
        self.min_push_interval = 1.0 / 24.0 

        
        self.meta_buffer: Deque[Tuple[float, Dict[str, Any]]] = collections.deque(maxlen=buf_size)

        self.recording = False
        self.writer = None
        self.post_remaining = 0
        self.clip_path: Optional[str] = None
        self.last_started_ts: float = 0.0
        
        
        self.last_saved_segment_ts: float = 0.0  
        self.save_segment_cooldown: float = 3.0   

    def push(self, ts: float, frame_bgr, meta: Optional[Dict[str, Any]] = None):
        
        """
        프레임과 (선택) 메타를 버퍼에 쌓는다.
        meta에는 "측정에 쓰이는 값 + 측정값"만 최소로 넣는 것을 권장.

        meta 예시(최소):
            {
              "frame_index": 123,
              "state": "NORMAL|ABNORMAL|LEVEL1",
              "ghost": False,
              "quality": 0.71,
              "bbox": [x1,y1,x2,y2],
              "aspect": 1.85,
              "baseline": 2.40,          # 또는 baseline_at_enter
              "cy": 0.52,
              "vy": 0.36,
              "drop": True,
              "posture": False,
              "is_partial": False,
              "is_still": True
            }
        """
        with self.lock:
            if ts - self.last_push_ts < self.min_push_interval:
                return
            self.last_push_ts = ts

        if frame_bgr is None:
            return

        
        h, w = frame_bgr.shape[:2]
        if w != CLIP_RESIZE_WIDTH or h != CLIP_RESIZE_HEIGHT:
            frame_bgr = cv2.resize(frame_bgr, (CLIP_RESIZE_WIDTH, CLIP_RESIZE_HEIGHT))

        
        
        ok, jpg_bytes = cv2.imencode(".jpg", frame_bgr, [int(cv2.IMWRITE_JPEG_QUALITY), 60])
        
        if ok:
            with self.lock:
                self.buffer.append((ts, jpg_bytes.tobytes()))
                if meta is not None:
                    
                    self.meta_buffer.append((ts, meta))

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
        h, w = frame_shape[:2]
        
        
        if w <= 0 or h <= 0:
            logger.error(f"[CLIP] 프레임 해상도 유효하지 않음: {w}x{h}")
            raise ValueError(f"프레임 해상도 유효하지 않음: {w}x{h}")
        
        
        fps = float(fps)
        if fps <= 0 or fps > 240:
            logger.warning(f"[CLIP] FPS 범위 벗어남: {fps}, [5, 240]로 조정 중")
            fps = max(5.0, min(240.0, fps))
        
        
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
                
                
                if writer.isOpened():
                    logger.info(f"[CLIP] 코덱으로 VideoWriter 열기 '{codec_name}': {w}x{h} @ {fps}fps")
                    return writer
                else:
                    logger.warning(f"[CLIP] 코덱 '{codec_name}' 열기 실패: {w}x{h} @ {fps}fps")
                    writer.release()
            except Exception as e:
                logger.warning(f"[CLIP] 코덱 '{codec_name}' 오류: {e}")
                continue
        
        logger.error(f"[CLIP] 모든 코덱이 실패함 {w}x{h} @ {fps}fps. 기본 writer 생성 중.")
        
        writer = cv2.VideoWriter(path, cv2.VideoWriter_fourcc(*"mp4v"), fps, (w, h))
        return writer

    def status(self):
        with self.lock:
            return {
                "clip_recording": self.recording,
                "clip_post_frames_remaining": self.post_remaining,
                "clip_path": self.clip_path,
                "clip_last_started_ts": self.last_started_ts,
                "clip_last_saved_segment_ts": self.last_saved_segment_ts,
                "buffer_len": len(self.buffer),
                "meta_buffer_len": len(self.meta_buffer),
            }


    def _dump_jsonl(self, jsonl_path: str, records: List[Dict[str, Any]]) -> None:
        """
        jsonl 저장(실패해도 영상 저장은 유지)
        """
        try:
            with open(jsonl_path, "w", encoding="utf-8") as f:
                for rec in records:
                    f.write(json.dumps(rec, ensure_ascii=False) + "\n")
        except Exception:
            pass

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

            
            
            
            
            now = time.time()
            time_since_last_save = now - self.last_saved_segment_ts
            if time_since_last_save < self.save_segment_cooldown:
                logger.warning(
                    f"[CLIP] Duplicate save attempt ignored: "
                    f"last_saved={self.last_saved_segment_ts:.2f}, "
                    f"now={now:.2f}, "
                    f"cooldown={self.save_segment_cooldown}s"
                )
                return None
            
            self.last_saved_segment_ts = now

            t_start = incident_ts - float(pre_sec)
            t_end = incident_ts + float(post_sec)

            picked = []  
            for ts, jpg_bytes in list(self.buffer):
                if t_start <= ts <= t_end:
                    picked.append((ts, jpg_bytes))

            if not picked:
                buf_ts_start = self.buffer[0][0]
                buf_ts_end = self.buffer[-1][0]
                logger.warning(
                    f"[CLIP] No frames in range [{t_start:.2f}, {t_end:.2f}] "
                    f"(buffer available: [{buf_ts_start:.2f}, {buf_ts_end:.2f}])"
                )
                return None

            
            ts0 = picked[0][0]
            ts1 = picked[-1][0]
            span = max(1e-6, ts1 - ts0)
            eff_fps = len(picked) / span

            
            
            eff_fps = max(1.0, min(float(CLIP_FPS), eff_fps))

            clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            mp4_path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.mp4")
            jsonl_path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.jsonl")

            
            first_frame = cv2.imdecode(np.frombuffer(picked[0][1], np.uint8), cv2.IMREAD_COLOR)
            if first_frame is None:
                logger.error("[CLIP] failed to decode first frame")
                return None

            writer = self._open_writer(mp4_path, eff_fps, first_frame.shape)
            if not writer.isOpened():
                logger.error(f"[CLIP] writer open failed: {mp4_path}")
                try:
                    writer.release()
                except Exception:
                    pass
                return None

            writer_w, writer_h = first_frame.shape[1], first_frame.shape[0]
            logger.info(
                f"[CLIP] saving segment incident={incident_ts:.2f} "
                f"range=[{t_start:.2f},{t_end:.2f}] frames={len(picked)} "
                f"eff_fps={eff_fps:.1f} size={writer_w}x{writer_h} path={mp4_path}"
            )

        
        try:
            for _, jpg_bytes in picked:
                fr = cv2.imdecode(np.frombuffer(jpg_bytes, np.uint8), cv2.IMREAD_COLOR)
                if fr is None:
                    continue
                if fr.shape[1] != writer_w or fr.shape[0] != writer_h:
                    fr = cv2.resize(fr, (writer_w, writer_h))
                writer.write(fr)
        finally:
            try:
                writer.release()
            except Exception:
                pass

        mp4_path = self.transcode_mp4_for_web(mp4_path)

        
        
        with self.lock:
            meta_picked: List[Dict[str, Any]] = []
            for mts, m in list(self.meta_buffer):
                if t_start <= mts <= t_end:
                    
                    
                    KEEP_KEYS = {
                        "frame_index",
                        "state",
                        "ghost",
                        "quality",
                        "bbox",
                        "aspect",
                        "baseline",
                        "cy",
                        "vy",
                        "drop",
                        "posture",
                        "is_partial",
                        "is_still",
                    }
                    rec = {k: m.get(k) for k in KEEP_KEYS if k in m}
                    rec["ts"] = float(mts)
                    meta_picked.append(rec)

        if meta_picked:
            self._dump_jsonl(jsonl_path, meta_picked)

        return mp4_path

    def save_range(self, t_start: float, t_end: float, filename_prefix: str = "incident") -> Optional[str]:
        """
        버퍼에서 [t_start, t_end] 범위를 추출해서 저장한다.
        사고 '진입~확정' 같이 비대칭 구간 저장이 필요할 때 사용.
        """

        if t_end <= t_start:
            return None

        with self.lock:
            if len(self.buffer) == 0:
                return None

            
            now = time.time()
            if (now - self.last_saved_segment_ts) < self.save_segment_cooldown:
                logging.warning(
                    f"[CLIP] Duplicate save attempt ignored: last_saved={self.last_saved_segment_ts:.2f}, now={now:.2f}"
                )
                return None
            self.last_saved_segment_ts = now

            picked = []
            for ts, jpg_bytes in list(self.buffer):
                if t_start <= ts <= t_end:
                    picked.append((ts, jpg_bytes))

            if not picked:
                buf_ts_start = self.buffer[0][0]
                buf_ts_end = self.buffer[-1][0]
                logging.warning(
                    f"[CLIP] No frames in range [{t_start:.2f}, {t_end:.2f}] "
                    f"(buffer available: [{buf_ts_start:.2f}, {buf_ts_end:.2f}])"
                )
                return None

        
        ts0 = picked[0][0]
        ts1 = picked[-1][0]
        span = max(1e-6, ts1 - ts0)
        eff_fps = len(picked) / span
        eff_fps = max(1.0, min(float(CLIP_FPS), eff_fps))

        clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
        path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.mp4")

        first_frame = cv2.imdecode(np.frombuffer(picked[0][1], np.uint8), cv2.IMREAD_COLOR)
        if first_frame is None:
            logging.error("[CLIP] Failed to decode first frame. Abort.")
            return None

        writer = self._open_writer(path, eff_fps, first_frame.shape)
        if not writer.isOpened():
            logging.error(f"[CLIP] VideoWriter failed to open: path={path}")
            try:
                writer.release()
            except Exception:
                pass
            return None

        writer_w = first_frame.shape[1]
        writer_h = first_frame.shape[0]

        logging.info(
            f"[CLIP] saving range start={t_start:.2f} end={t_end:.2f} "
            f"frames={len(picked)} eff_fps={eff_fps:.1f} size={writer_w}x{writer_h} path={path}"
        )

        try:
            for _, jpg_bytes in picked:
                fr = cv2.imdecode(np.frombuffer(jpg_bytes, np.uint8), cv2.IMREAD_COLOR)
                if fr is None:
                    continue
                if fr.shape[1] != writer_w or fr.shape[0] != writer_h:
                    fr = cv2.resize(fr, (writer_w, writer_h))
                writer.write(fr)
        finally:
            try:
                writer.release()
            except Exception:
                pass

        path = self.transcode_mp4_for_web(path)
        return path

    def transcode_mp4_for_web(self, in_path: str) -> str:
        """
        브라우저 호환(H.264/AAC/yuv420p) + faststart로 재인코딩.
        """
        p = Path(in_path)
        out_path = str(p.with_suffix(".web.tmp.mp4"))

        cmd = [
            "ffmpeg",
            "-y",
            "-i", str(p),
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-movflags", "+faststart",
            out_path,
        ]

        try:
            subprocess.run(cmd, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            os.replace(out_path, str(p))
            return str(p)
        except FileNotFoundError:
            return str(p)
        except subprocess.CalledProcessError:
            try:
                if os.path.exists(out_path):
                    os.remove(out_path)
            except Exception:
                pass
            return str(p)
