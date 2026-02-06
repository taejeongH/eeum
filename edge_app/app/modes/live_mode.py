"""
Live 모드
사람 감지, 낙상 감시, 이벤트 전송 로직 통합
"""

import time
import logging
import cv2
import os
from typing import Optional, Tuple, Dict, Any

from .base_mode import BaseMode
from ..engine import LivePipeline
from ..core import Level1Params, Level1Engine, PresenceParams, PresenceEngine, ClipRecorder
from ..api.server_client import ServerClient
from ..state.device_state import get_device_state
from ..config import JPEG_QUALITY, DEVICE_ID, LOCATION_ID

logger = logging.getLogger(__name__)

class LiveMode(BaseMode):
    """
    실시간 사람 감지 및 낙상 분석 모드
    
    Responsibilities:
    1. YOLO Pipeline 실행 (Frame -> Keypoints)
    2. Presence Check (사람 유무)
    3. Fall Detection (Level1 Engine)
    4. Event Reporting (Server/RPI)
    5. Clip Recording
    """
    
    def __init__(self, model, cap: cv2.VideoCapture, jpeg_quality: int = 80):
        super().__init__("LiveMode")
        self.model = model
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.pipeline: Optional[LivePipeline] = None
        
        # Engines
        from ..config import ABNORMAL_TIMEOUT_S
        self.presence_engine = PresenceEngine(PresenceParams(
            enter_hits=5, exit_hits=10, min_quality=0.10, cool_down_s=0.5
        ))
        self.level1_engine = Level1Engine(Level1Params(
            abnormal_timeout_s=ABNORMAL_TIMEOUT_S,
            ghost_timeout_s=10.0  # 테스트용: abnormal_timeout과 동일하게 설정
        ))
        self.clip_recorder = ClipRecorder()
        
        # Clients
        self.server_client = ServerClient()
        self.device_state = get_device_state()
        
        # State
        self.last_level1_event: Optional[Dict[str, Any]] = None
        self.local_incident_ts: Optional[float] = None
        self.local_last_level1_event: Optional[Dict[str, Any]] = None
    
    def setup(self) -> bool:
        """모드 초기화"""
        try:
            logger.info("Initializing Live Mode Pipeline")
            self.pipeline = LivePipeline(
                model=self.model,
                cap=self.cap,
                jpeg_quality=self.jpeg_quality,
                source_id="cam0"
            )
            # 상태 초기화
            self.level1_engine.reset_all()
            self.presence_engine.reset()
            self.last_level1_event = None
            self.local_incident_ts = None
            
            self.is_running = True
            return True
        except Exception as e:
            logger.error(f"Live Mode setup failed: {e}")
            return False
    
    def cleanup(self):
        """모드 정리"""
        if self.pipeline:
             pass 
        self.is_running = False
        self.pipeline = None
        logger.info("Live Mode cleaned up")

    def get_status(self) -> Dict[str, Any]:
        """현재 상태 반환 (API 노출용)"""
        st = self.clip_recorder.status()
        return {
            "last_level1_event": self.last_level1_event,
            **st
        }

    def step(self) -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any], Optional[Any]]:
        if self.pipeline is None:
            return None, None, None, None

        # 변경: raw_frame을 같이 받는다
        obs, overlay_jpg, raw_frame = self.pipeline.step(overlay="smooth")

        if obs is None:
            return None, overlay_jpg, raw_frame, None

        ts_now = float(obs["ts"])

        pe = self.presence_engine.step(obs)
        if pe is not None:
            self._handle_presence_event(pe, ts_now)

        # Clip은 "원본"으로 저장하는 게 보통 더 좋다 (서버/디버깅 관점)
        if raw_frame is not None:
            meta = {
                "frame_index": int(obs.get("frame_index", -1)),
                "state": self.level1_engine.state,
                "ghost": self.level1_engine.is_ghost_mode,
                "bbox": obs.get("tracks")[0].get("bbox") if obs.get("tracks") else None,
                "quality": obs.get("tracks")[0].get("quality_score") if obs.get("tracks") else None,
            }
            self.clip_recorder.push(ts_now, raw_frame, meta=meta)

        ev = self.level1_engine.step(obs)
        if ev is not None:
            self._handle_fall_event(ev, obs, ts_now)

        # raw_frame을 컨트롤러가 raw_jpg로 인코딩해서 /stream_raw로 뿌리게 된다
        return obs, overlay_jpg, raw_frame, None

    def _handle_presence_event(self, pe: Dict[str, Any], ts_now: float):
        """재실 이벤트 처리"""
        ptype = pe["data"]["event"]
        event_name = "enter" if ptype == "enter" else "exit"
        
        payload = {
            "kind": "vision",
            "device_id": DEVICE_ID,
            "data": {
                "location_id": LOCATION_ID,
                "event": event_name
            },
            "ts": ts_now,
        }
        # RPI로 전송
        self.server_client.send_event_rpi(payload)

    def _handle_fall_event(self, ev: Dict[str, Any], obs: Dict[str, Any], ts_now: float):
        """낙상/이상행동 이벤트 처리"""
        et = ev.get("type")
        
        if et == "abnormal_enter":
            self.local_incident_ts = float(ev.get("ts", ts_now))
            logger.info(f"[ABNORMAL START] ts={self.local_incident_ts:.2f}")

        elif et == "abnormal_exit":
            self.local_incident_ts = None
            logger.info("[ABNORMAL END] Recovered")

        elif et == "level1":
            logger.warning(f"[FALL DETECTED] {ev}")
            self.last_level1_event = ev # API 노출용 저장
            
            # 토큰 확인
            access_token = self.device_state.ensure_valid_token(self.server_client)
            if not access_token:
                logger.error("Token invalid. Cannot send fall event to Server.")
                access_token = "" # 전송 실패하더라도 로직 진행

            # Payload 준비
            current_event_id = ev.get("event_id") or f"level1_{time.strftime('%Y%m%d_%H%M%S')}"
            ev["event_id"] = current_event_id
            
            t0 = (obs.get("tracks") or [{}])[0]
            payload = {
                "kind": "fall",
                "device_id": DEVICE_ID,
                "detected_at": float(ts_now),
                "data": {
                    "location_id": LOCATION_ID,
                    "event": "fall_detected",
                    "level": 1,
                    "has_person": bool(t0.get("has_person", False)),
                    "confidence": ev.get("values", {}).get("confidence_score", 0.0),
                },
            }
            
            # 1. RPI 전송
            try:
                self.server_client.send_event_rpi(payload)
            except Exception as e:
                logger.error(f"Failed to send to RPI: {e}")

            # 2. Server 전송 (Presigned URL 획득)
            presigned_url, video_path = None, None
            if access_token:
                try:
                    res = self.server_client.send_event_server(payload, access_token=access_token)
                    # [방어 로직] 반환값이 튜플/리스트이고 길이가 2인지 확인하여 Unpacking Error 방지
                    if res and isinstance(res, (tuple, list)) and len(res) == 2:
                        presigned_url, video_path = res
                    else:
                        logger.error(f"Unexpected response from send_event_server: {res}")
                except Exception as e:
                    logger.error(f"Failed to send to Server: {e}")

            # 3. Clip 저장 및 업로드
            if self.local_incident_ts is not None:
                from ..config import CLIP_PRE_SEC, CLIP_POST_SEC

                level1_ts = float(ev.get("ts", ts_now))
                abn_ts = float(self.local_incident_ts)  # 이미 None 아님

                t_start = abn_ts - float(CLIP_PRE_SEC)
                t_end = abn_ts + float(CLIP_POST_SEC)

                clip_path = self.clip_recorder.save_range(
                    t_start=t_start,
                    t_end=t_end,
                    filename_prefix="incident"
                )

                if not clip_path:
                    logger.warning("Clip save skipped/failed (cooldown or no frames in range)")
                    return  # 또는 그냥 넘어가도 됨

                if clip_path and presigned_url and video_path:
                    logger.info(f"Uploading clip: {clip_path} -> {video_path}")
                    try:
                        self.server_client.upload_clip_via_presigned_put(
                            presigned_url=presigned_url,
                            clip_path=clip_path,
                            timeout=120.0
                        )

                        # 업로드 성공 알림(토큰 있을 때만)
                        if access_token:
                            self.server_client.send_video_upload_success(
                                video_path=video_path,
                                access_token=access_token
                            )

                        logger.info("Clip upload complete")

                    except Exception as e:
                        logger.error(f"Failed to upload clip: {e}")

            self.local_incident_ts = None
