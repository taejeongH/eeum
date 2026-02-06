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
    ABNORMAL_TIMEOUT_S, JPEG_QUALITY  # Import newly added config
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
        
        # [Buffer Size Calculation]
        # 낙상 판단 대기 시간(30s) + 사전/사후(12s) + 여유(10s) = 약 52초
        # ⚠️ 중요: 사람이 소실되면 YOLO를 건너뛰어 루프 속도가 60 FPS 이상으로 빨라집니다.
        # 따라서 CLIP_FPS(15) 대신 안전한 최대 속도(60)를 기준으로 버퍼 크기를 잡아야 과거 영상이 밀려나지 않습니다.
        required_duration = ABNORMAL_TIMEOUT_S + CLIP_PRE_SEC + CLIP_POST_SEC + 10.0
        safety_fps = 60.0 
        buf_size = int(safety_fps * required_duration)
        
        self.buffer: Deque[Tuple[float, bytes]] = collections.deque(maxlen=buf_size)
        self.last_push_ts = 0.0
        self.min_push_interval = 1.0 / 24.0 # 최대 24 FPS로 제한 (버퍼 밀림 방지)

        # (추가) 분석용 메타 링버퍼 (ts, meta)
        self.meta_buffer: Deque[Tuple[float, Dict[str, Any]]] = collections.deque(maxlen=buf_size)

        self.recording = False
        self.writer = None
        self.post_remaining = 0
        self.clip_path: Optional[str] = None
        self.last_started_ts: float = 0.0
        
        # 중복 저장 방지용
        self.last_saved_segment_ts: float = 0.0  # 마지막 저장된 segment의 시작 시간
        self.save_segment_cooldown: float = 3.0   # 3초 내 중복 저장 방지

    def push(self, ts: float, frame_bgr, meta: Optional[Dict[str, Any]] = None):
        # [Rate Limit] 버퍼가 너무 빨리 밀리는 것을 방지하기 위해 최대 24 FPS로 제한
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

        # 1. Resize
        h, w = frame_bgr.shape[:2]
        if w != CLIP_RESIZE_WIDTH or h != CLIP_RESIZE_HEIGHT:
            frame_bgr = cv2.resize(frame_bgr, (CLIP_RESIZE_WIDTH, CLIP_RESIZE_HEIGHT))

        # 2. JPEG Encode (Memory Optimization)
        # 퀄리티는 config의 JPEG_QUALITY 사용 (또는 70~80 정도 적절히)
        ok, jpg_bytes = cv2.imencode(".jpg", frame_bgr, [int(cv2.IMWRITE_JPEG_QUALITY), 60])
        
        if ok:
            with self.lock:
                self.buffer.append((ts, jpg_bytes.tobytes()))
                if meta is not None:
                    # meta는 JSON 직렬화 가능한 dict만 넣어야 함
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
        
        # 유효성 검사
        if w <= 0 or h <= 0:
            logger.error(f"[CLIP] 프레임 해상도 유효하지 않음: {w}x{h}")
            raise ValueError(f"프레임 해상도 유효하지 않음: {w}x{h}")
        
        # fps 범위 검증
        fps = float(fps)
        if fps <= 0 or fps > 240:
            logger.warning(f"[CLIP] FPS 범위 벗어남: {fps}, [5, 240]로 조정 중")
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
                    logger.info(f"[CLIP] 코덱으로 VideoWriter 열기 '{codec_name}': {w}x{h} @ {fps}fps")
                    return writer
                else:
                    logger.warning(f"[CLIP] 코덱 '{codec_name}' 열기 실패: {w}x{h} @ {fps}fps")
                    writer.release()
            except Exception as e:
                logger.warning(f"[CLIP] 코덱 '{codec_name}' 오류: {e}")
                continue
        
        logger.error(f"[CLIP] 모든 코덱이 실패함 {w}x{h} @ {fps}fps. 기본 writer 생성 중.")
        # 마지막 시도: 기본 코덱
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

            # ---------------------------------------------------
            # 중복 저장 방지: 실시간 기준 3초 내 재저장 차단
            # (더 안정적인 기준: incident_ts 대신 현재 실시간 사용)
            # ---------------------------------------------------
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

            picked = []  # (ts, jpeg_bytes)
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

            # 구간의 "실제 fps" 추정 (재생 길이 보존 목적)
            ts0 = picked[0][0]
            ts1 = picked[-1][0]
            span = max(1e-6, ts1 - ts0)
            eff_fps = len(picked) / span

            # 너무 튀지 않게 클램프(선택)
            # [수정] 최소 FPS 제한을 1.0으로 낮춰서, 실제 촬영 속도가 낮더라도 원래 시간 길이를 유지하도록 함
            eff_fps = max(1.0, min(float(CLIP_FPS), eff_fps))

            clip_id = datetime.now().strftime("%Y%m%d_%H%M%S")
            mp4_path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.mp4")
            jsonl_path = os.path.join(CLIP_DIR, f"{filename_prefix}_{clip_id}.jsonl")

            # writer 생성용 첫 프레임 디코드
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

        # (중요) 디코드/인코딩은 락 밖에서 수행 (프레임 루프 막힘 방지)
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

        # -------- jsonl 저장(최소 필드만) --------
        # meta도 같은 구간으로 필터링
        with self.lock:
            meta_picked: List[Dict[str, Any]] = []
            for mts, m in list(self.meta_buffer):
                if t_start <= mts <= t_end:
                    # 최소 필드만 남기기(너가 원하는 "측정값/측정에 쓰이는 값" 중심)
                    # 여기에서 키를 더 빼고 싶으면 KEEP_KEYS에서 제거하면 됨.
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

            # 중복 저장 방지(기존 save_segment와 동일 정책)
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

        # 구간 FPS 추정(재생 길이 보존)
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
