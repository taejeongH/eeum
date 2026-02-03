"""
운영 모드의 기본 클래스 (추상)

QR 모드와 Live 모드가 상속받을 기본 구조
"""

from abc import ABC, abstractmethod
from typing import Optional, Tuple, Dict, Any
import cv2


class BaseMode(ABC):
    """
    모드의 기본 인터페이스
    """
    
    def __init__(self, name: str):
        self.name = name
        self.is_running = False
    
    @abstractmethod
    def setup(self) -> bool:
        """모드 초기화"""
        pass
    
    @abstractmethod
    def cleanup(self):
        """모드 정리"""
        pass
    
    @abstractmethod
    def step(self) -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any]]:
        """
        한 프레임 처리
        
        Returns:
            (obs, jpg, frame) 또는 (None, None, None)
        """
        pass
    
    def is_ready(self) -> bool:
        """모드 준비 상태"""
        return self.is_running
    
    def __enter__(self):
        self.setup()
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.cleanup()
