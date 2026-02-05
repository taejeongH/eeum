"""
QR 페어링 모드

카메라에서 QR 코드를 감지하고 서버와 페어링
"""

import cv2
import time
from typing import Optional, Tuple, Dict, Any
from pyzbar.pyzbar import decode, ZBarSymbol
import logging

from .base_mode import BaseMode
from ..state.device_state import get_device_state
from ..api.server_client import ServerClient
from ..config import FRAME_W, FRAME_H, JPEG_QUALITY, DEVICE_ID

logger = logging.getLogger(__name__)


class QRMode(BaseMode):
    """
    QR 코드 인식 및 기기 페어링 모드
    
    흐름:
    1. 카메라에서 프레임 캡처
    2. QR 코드 감지
    3. 서버와 토큰 검증
    4. 등록 완료 시 LiveMode로 전환
    """
    
    def __init__(self, cap: cv2.VideoCapture, jpeg_quality: int = 80):
        super().__init__("QRMode")
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.frame_index = 0
        
        self.server_client = ServerClient()
        self.device_state = get_device_state()
        
        # QR 감지 후처리
        self.last_qr_time = 0
        self.qr_cooldown = 1.0  # 1초 내 중복 감지 방지
    
    def setup(self) -> bool:
        """모드 초기화"""
        try:
            logger.info("Initializing QR Mode")
            self.is_running = True
            return True
        except Exception as e:
            logger.error(f"QR Mode setup failed: {e}")
            return False
    
    def cleanup(self):
        """모드 정리"""
        self.is_running = False
        logger.info("QR Mode cleaned up")
    
    def step(self) -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any]]:
        """
        한 프레임 처리: QR 감지 시도
        
        Returns:
            (None, jpg, frame) - obs는 QR 모드에선 의미 없음
        """
        ok, frame = self.cap.read()
        if not ok:
            return None, None, None
        
        # JPEG 인코딩
        jpg = self._encode_jpeg(frame)
        
        # QR 감지
        qr_data = self._detect_qr(frame)
        
        if qr_data:
            self._handle_qr(qr_data)
        
        self.frame_index += 1
        
        # obs는 None (QR 모드에선 포즈 감지 안 함)
        return None, jpg, frame
    
    def _encode_jpeg(self, frame) -> Optional[bytes]:
        """프레임을 JPEG으로 인코딩"""
        ok, jpg = cv2.imencode(".jpg", frame, [cv2.IMWRITE_JPEG_QUALITY, self.jpeg_quality])
        return jpg.tobytes() if ok else None
    
    def _detect_qr(self, frame):
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)

        # 대비 강화 (QR 인식률 크게 올라감)
        gray = cv2.equalizeHist(gray)

        qrs = decode(gray, symbols=[ZBarSymbol.QRCODE])

        # 🔴 디버깅 로그
        if qrs:
            logger.info(f"[QR] detected {len(qrs)} symbols")
        else:
            logger.debug("[QR] no symbol")

        # 🔴 화면에 박스 그리기
        for qr in qrs:
            (x, y, w, h) = qr.rect
            cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 0, 255), 2)

        if not qrs:
            return None

        return qrs[0].data.decode("utf-8")
    
    def _handle_qr(self, qr_token: str):
        """
        QR 코드 처리
        
        1. 토큰 검증
        2. 서버에 등록 요청
        3. 서버 응답 저장:
           {
               "status": 200,
               "data": {
                   "access_token": "eyJhbG...",
                   "refresh_token": "eyJhbG...",
                   "group_id": 1,
                   "serial_number": "JETSON-MASTER-001"
               }
           }
        """
        current_time = time.time()
        
        # 쿨다운 체크 (1초 내 중복 감지 방지)
        if (current_time - self.last_qr_time) < self.qr_cooldown:
            return
        
        self.last_qr_time = current_time
        
        
        logger.info(f"QR detected: {qr_token[:8]}...")
        print(f"[QRMode] QR detected: {qr_token}...")

        # 서버에 등록 요청
        try:
            # QR 토큰 내용이 바로 pairing_code라고 가정
            result = self.server_client.register_device(qr_token, DEVICE_ID)
            
            if result and result.get("status") == "200 OK":
                data = result.get("data", {})
                
                access_token = data.get("access_token")
                refresh_token = data.get("refresh_token")
                group_id = data.get("group_id")
                serial_number = data.get("serial_number")
                
                if not all([access_token, refresh_token, group_id, serial_number]):
                    logger.error("Missing required fields in server response")
                    return False
                
                # 액세스 토큰 성공적으로 받아지면 라즈베리파이에 전달
                try:
                    rpi_result = self.server_client.send_access_token_to_rpi(access_token)
                    if rpi_result and rpi_result.get("status") == "200 OK":
                        logger.info("Access token sent to RPI successfully")
                    else:
                        logger.error("Failed to send access token to RPI")
                except Exception as e:
                    logger.error(f"Error sending access token to RPI: {e}")
                    
                # 로컬 상태 저장
                if self.device_state.register(
                    device_id=DEVICE_ID,
                    access_token=access_token,
                    refresh_token=refresh_token,
                    group_id=group_id,
                    serial_number=serial_number
                ):
                    logger.info(f"Device registered successfully: {DEVICE_ID} (group: {group_id})")
                    # complete_qr_token(qr_token, DEVICE_ID) # 로컬 스토어 미사용 시 제거
                    return True
                else:
                    logger.error("Failed to save device state")
                    return False
            else:
                logger.error(f"Server registration failed: {result}")
                return False
        except Exception as e:
            logger.error(f"Registration error: {e}")
        
        return False
    
    @property
    def is_pairing_complete(self) -> bool:
        """페어링 완료 확인"""
        return self.device_state.is_registered()
