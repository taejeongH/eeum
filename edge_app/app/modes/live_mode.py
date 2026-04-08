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
        
        
        from ..config import ABNORMAL_TIMEOUT_S
        self.presence_engine = PresenceEngine(PresenceParams(
            enter_hits=5, exit_hits=10, min_quality=0.10, cool_down_s=0.5
        ))
        self.level1_engine = Level1Engine(Level1Params(
            abnormal_timeout_s=ABNORMAL_TIMEOUT_S,
            ghost_timeout_s=10.0  
        ))
        self.clip_recorder = ClipRecorder()
        
        
        self.server_client = ServerClient()
        self.device_state = get_device_state()
        
        
        self.last_level1_event: Optional[Dict[str, Any]] = None
        self.local_incident_ts: Optional[float] = None
        self.local_last_level1_event: Optional[Dict[str, Any]] = None
    
    def setup(self) -> bool:
        """분석 파이프라인 및 엔진 상태를 초기화합니다."""
        try:
            logger.info("Initializing Live Mode Pipeline")
            self.pipeline = LivePipeline(
                model=self.model,
                cap=self.cap,
                jpeg_quality=self.jpeg_quality,
                source_id="cam0"
            )
            
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
        self.is_running = False
        
        if self.pipeline:
             pass 
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

        
        obs, overlay_jpg, raw_frame = self.pipeline.step(overlay="smooth")

        if obs is None:
            return None, overlay_jpg, raw_frame, None

        
        

        ts_now = float(obs["ts"])

        pe = self.presence_engine.step(obs)
        if pe is not None:
            self._handle_presence_event(pe, ts_now)

        
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
            self.last_level1_event = ev 
            
            
            access_token = self.device_state.ensure_valid_token(self.server_client)
            if not access_token:
                logger.error("Token invalid. Cannot send fall event to Server.")
                access_token = "" 

            
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
            
            
            try:
                self.server_client.send_event_rpi(payload)
            except Exception as e:
                logger.error(f"Failed to send to RPI: {e}")

            
            presigned_url, video_path = None, None
            if access_token:
                try:
                    res = self.server_client.send_event_server(payload, access_token=access_token)
                    
                    if res and isinstance(res, (tuple, list)) and len(res) == 2:
                        presigned_url, video_path = res
                    else:
                        logger.error(f"Unexpected response from send_event_server: {res}")
                except Exception as e:
                    logger.error(f"Failed to send to Server: {e}")

            
            if self.local_incident_ts is not None:
                from ..config import CLIP_PRE_SEC, CLIP_POST_SEC

                level1_ts = float(ev.get("ts", ts_now))
                abn_ts = float(self.local_incident_ts)  

                t_start = abn_ts - float(CLIP_PRE_SEC)
                t_end = abn_ts + float(CLIP_POST_SEC)

                clip_path = self.clip_recorder.save_range(
                    t_start=t_start,
                    t_end=t_end,
                    filename_prefix="incident"
                )

                if not clip_path:
                    logger.warning("Clip save skipped/failed (cooldown or no frames in range)")
                    return  

                if clip_path and presigned_url and video_path:
                    logger.info(f"Uploading clip: {clip_path} -> {video_path}")
                    try:
                        self.server_client.upload_clip_via_presigned_put(
                            presigned_url=presigned_url,
                            clip_path=clip_path,
                            timeout=120.0
                        )

                        
                        if access_token:
                            self.server_client.send_video_upload_success(
                                video_path=video_path,
                                access_token=access_token
                            )

                        logger.info("Clip upload complete")

                    except Exception as e:
                        logger.error(f"Failed to upload clip: {e}")

            self.local_incident_ts = None
