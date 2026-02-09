
import os
import sys
import cv2
import time
from typing import Optional, Tuple
import logging

from ..config import CAM_INDEX, FRAME_W, FRAME_H

logger = logging.getLogger(__name__)

class CameraManager:
    """
    카메라 장치 관리 클래스
    - 플랫폼별 카메라 열기
    - 해상도/FPS 설정
    - 카메라 객체 재사용 및 해제
    """
    
    def __init__(self):
        self.cap: Optional[cv2.VideoCapture] = None
        self.current_w = FRAME_W
        self.current_h = FRAME_H
        self.current_fps = 15
    
    def open_camera(self) -> cv2.VideoCapture:
        """
        OS에 맞는 backend로 카메라를 열어서 반환.
        이미 열려있다면 기존 핸들을 반환하거나 재설정.
        """
        if self.cap is not None and self.cap.isOpened():
            return self.cap

        cam_device = os.getenv("CAM_DEVICE", "").strip()
        cam_index = int(os.getenv("CAM_INDEX", str(CAM_INDEX)))

        logger.info(f"Opening camera: index={cam_index}, device={cam_device}, platform={sys.platform}")

        c = None
        if sys.platform.startswith("win"):
            
            c = cv2.VideoCapture(cam_index, cv2.CAP_DSHOW)
            if not c.isOpened():
                c.release()
                c = cv2.VideoCapture(cam_index, cv2.CAP_MSMF)
        else:
            
            if cam_device:
                c = cv2.VideoCapture(cam_device, cv2.CAP_V4L2)
            else:
                c = cv2.VideoCapture(cam_index, cv2.CAP_V4L2)

        if c and c.isOpened():
            self.cap = c
            
            self.configure(self.current_w, self.current_h, self.current_fps)
            
            
            aw = int(c.get(cv2.CAP_PROP_FRAME_WIDTH))
            ah = int(c.get(cv2.CAP_PROP_FRAME_HEIGHT))
            logger.info(f"Camera opened successfully. Request=({self.current_w}x{self.current_h}), Actual=({aw}x{ah})")
        else:
            logger.error("Failed to open camera.")
            self.cap = None

        return self.cap

    def ensure_opened(self) -> bool:
        """카메라가 열려있는지 확인하고, 닫혀있다면 연다."""
        if self.cap is not None and self.cap.isOpened():
            return True
        
        
        if self.cap is not None:
            try:
                self.cap.release()
            except Exception:
                pass
        
        self.open_camera()
        return self.cap is not None and self.cap.isOpened()

    def configure(self, width: int, height: int, fps: int = 15):
        """
        해상도 및 FPS 설정
        (이미 열린 카메라에 적용)
        """
        if self.cap is None or not self.cap.isOpened():
            return

        self.current_w = width
        self.current_h = height
        self.current_fps = fps

        self.cap.set(cv2.CAP_PROP_FPS, fps)
        self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)

        
        aw = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
        ah = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
        if aw != width or ah != height:
            logger.warning(f"Camera config mismatch: req({width}x{height}) != actual({aw}x{ah})")
    
    def try_autofocus(self):
        """
        카메라 오토포커스 시도 (QR 모드용)
        
        V4L2 카메라가 지원하는 경우에만 작동.
        """
        if self.cap is None or not self.cap.isOpened():
            return
        
        try:
            
            af_supported = self.cap.set(cv2.CAP_PROP_AUTOFOCUS, 1)
            if af_supported:
                logger.info("[Camera] Autofocus enabled")
            else:
                logger.debug("[Camera] Autofocus not supported")
            
            
            
            focus_set = self.cap.set(cv2.CAP_PROP_FOCUS, 180)
            if focus_set:
                logger.info("[Camera] Manual focus set to 180 (close range)")
            else:
                logger.debug("[Camera] Manual focus control not supported")
                
        except Exception as e:
            logger.warning(f"[Camera] Focus control failed: {e}")
    
    def release(self):
        """카메라 자원 해제"""
        if self.cap is not None:
            try:
                self.cap.release()
            except Exception as e:
                logger.error(f"Error releasing camera: {e}")
        self.cap = None
        logger.info("Camera released.")

    def get_cap(self) -> Optional[cv2.VideoCapture]:
        return self.cap
