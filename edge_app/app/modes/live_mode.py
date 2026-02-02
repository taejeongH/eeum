"""
Live 모드 (기존 live.py 통합)

사람 감지 및 낙상 감시
"""

import cv2
import time
from typing import Optional, Tuple, Dict, Any
import logging

from .base_mode import BaseMode
from ..engine import LivePipeline
from ..config import JPEG_QUALITY

logger = logging.getLogger(__name__)


class LiveMode(BaseMode):
    """
    실시간 사람 감지 및 분석
    
    기존 LivePipeline을 BaseMode 인터페이스로 래핑
    """
    
    def __init__(self, model, cap: cv2.VideoCapture, jpeg_quality: int = 80):
        super().__init__("LiveMode")
        self.model = model
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.pipeline = None
    
    def setup(self) -> bool:
        """모드 초기화"""
        try:
            logger.info("Initializing Live Mode")
            self.pipeline = LivePipeline(
                model=self.model,
                cap=self.cap,
                jpeg_quality=self.jpeg_quality,
                source_id="cam0"
            )
            self.is_running = True
            return True
        except Exception as e:
            logger.error(f"Live Mode setup failed: {e}")
            return False
    
    def cleanup(self):
        """모드 정리"""
        try:
            if self.cap:
                self.cap.release()
        except Exception:
            pass
        
        self.is_running = False
        self.pipeline = None
        logger.info("Live Mode cleaned up")
    
    def step(self) -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any]]:
        """
        한 프레임 처리
        
        LivePipeline.step()을 직접 호출
        
        Returns:
            (obs, jpg, frame)
        """
        if self.pipeline is None:
            return None, None, None
        
        return self.pipeline.step(overlay="smooth")
