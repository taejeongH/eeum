"""
QR 페어링 모드

카메라에서 QR 코드를 감지하고 서버와 페어링
"""

import cv2
import time
import numpy as np
from typing import Optional, Tuple, Dict, Any
import sys
try:
    if sys.platform == "win32":
        # Windows에서는 libzbar DLL 의존성 문제로 인해 비활성화 (요구사항)
        PYZBAR_AVAILABLE = False
    else:
        from pyzbar.pyzbar import decode, ZBarSymbol
        PYZBAR_AVAILABLE = True
except (ImportError, OSError):
    PYZBAR_AVAILABLE = False
    decode = None
    ZBarSymbol = None
import logging
try:
    from scipy.signal import convolve2d
    from scipy.ndimage import gaussian_filter
    SCIPY_AVAILABLE = True
except ImportError:
    SCIPY_AVAILABLE = False

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
        
        # QR 감지 (프레임에 박스 그리기 포함)
        qr_data = self._detect_qr(frame)
        
        # JPEG 인코딩 (박스가 그려진 후 인코딩해야 화면에 보임)
        jpg = self._encode_jpeg(frame)
        
        if qr_data:
            self._handle_qr(qr_data)
        
        self.frame_index += 1
        
        # obs는 None (QR 모드에선 포즈 감지 안 함)
        return None, jpg, frame
    
    def _encode_jpeg(self, frame) -> Optional[bytes]:
        """프레임을 JPEG으로 인코딩"""
        ok, jpg = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), self.jpeg_quality])
        return jpg.tobytes() if ok else None
    
    def _detect_qr(self, frame):
        """
        [ULTRAHIGH] QR 코드 감지 (극심한 초점 불량 대응)
        
        디블러링, 샤프닝, 스케일 확장 등 모든 기법 총동원.
        """
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # 전처리 후보들 생성
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
        gray_enhanced = clahe.apply(gray)
        
        # 1. 초강력 선명화 (더 공격적인 계수)
        blur = cv2.GaussianBlur(gray_enhanced, (0, 0), 2)
        sharpened = cv2.addWeighted(gray_enhanced, 2.5, blur, -1.5, 0)
        
        # 2. Richardson-Lucy Deblurring (scipy 있을 때만)
        deblurred = self._richardson_lucy_deblur(gray_enhanced) if SCIPY_AVAILABLE else sharpened
        
        # 3. 노출 감소
        darkened = cv2.convertScaleAbs(gray_enhanced, alpha=0.6, beta=0)
        
        # 4. Bilateral Filter (노이즈 제거 + 엣지 보존)
        bilateral = cv2.bilateralFilter(gray_enhanced, 9, 75, 75)
        
        # 조합 리스트: (이름, 이미지)
        candidates = [
            ("Deblur", deblurred),  # 최우선: 디블러링
            ("Sharpen", sharpened),
            ("Base", gray_enhanced),
            ("Darkened", darkened),
            ("Bilateral", bilateral),
        ]
        
        # 스케일 후보 (극단 케이스 추가: 아주 가깝거나 먼 경우)
        scales = [1.0, 0.7, 0.85, 1.15, 1.3, 1.5]
        
        qrs = []
        found_method = ""
        
        # 공통 모폴로지 커널
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (2, 2))
        
        # 모든 조합 시도
        for name, img in candidates:
            # 기본 + 스케일 시도
            for scale in scales:
                work_img = img
                if scale != 1.0:
                    h, w = img.shape
                    work_img = cv2.resize(img, (int(w*scale), int(h*scale)))
                
                if PYZBAR_AVAILABLE:
                    qrs = decode(work_img, symbols=[ZBarSymbol.QRCODE])
                else:
                    qrs = []
                if qrs:
                    found_method = f"{name}_S{scale}"
                    self._fix_qr_rects(qrs, scale)
                    break
            if qrs: break
            
            if PYZBAR_AVAILABLE:
                adp = cv2.adaptiveThreshold(img, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, cv2.THRESH_BINARY, 21, 10)
                qrs = decode(adp, symbols=[ZBarSymbol.QRCODE])
            else:
                qrs = []
            if qrs:
                found_method = f"{name}_Adaptive"
                break
                
            if PYZBAR_AVAILABLE:
                dilated = cv2.dilate(img, kernel, iterations=1)
                qrs = decode(dilated, symbols=[ZBarSymbol.QRCODE])
            else:
                qrs = []
            if qrs:
                found_method = f"{name}_Dilate"
                break

            if PYZBAR_AVAILABLE:
                eroded = cv2.erode(img, kernel, iterations=1)
                qrs = decode(eroded, symbols=[ZBarSymbol.QRCODE])
            else:
                qrs = []
            if qrs:
                found_method = f"{name}_Erode"
                break

        # 🔴 디버깅 로그
        if qrs:
            logger.info(f"[QR] Detected via {found_method}")
        else:
            logger.debug("[QR] no symbol")

        # 🔴 화면에 박스 그리기
        for qr in qrs:
            (x, y, w, h) = qr.rect
            # 성공 시 시각 효과 강화 (형광색 두꺼운 박스)
            cv2.rectangle(frame, (x, y), (x+w, y+h), (0, 255, 0), 4)
            cv2.putText(frame, "QR DETECTED", (x, y-15), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.9, (0, 255, 0), 3)

        if not qrs:
            return None

        return qrs[0].data.decode("utf-8")

    def _fix_qr_rects(self, qrs, scale):
        """스케일 조정된 좌표를 원본으로 복구"""
        if scale == 1.0: return
        for qr in qrs:
            x, y, w, h = qr.rect
            qr.rect = (int(x/scale), int(y/scale), int(w/scale), int(h/scale))
    
    def _richardson_lucy_deblur(self, img, iterations=10, psf_size=5):
        """
        Richardson-Lucy 디콘볼루션 (초점 불량 복원)
        
        가우시안 PSF를 가정하여 흐릿함을 제거합니다.
        """
        if not SCIPY_AVAILABLE:
            return img
        
        try:
            # PSF 생성 (가우시안 블러 커널)
            psf = np.zeros((psf_size, psf_size))
            psf[psf_size//2, psf_size//2] = 1
            psf = gaussian_filter(psf, sigma=1.5)
            psf /= psf.sum()
            
            # Richardson-Lucy 반복
            img_float = img.astype(np.float64) / 255.0
            img_float = np.maximum(img_float, 1e-10)
            
            estimated = img_float.copy()
            for _ in range(iterations):
                reblurred = convolve2d(estimated, psf, mode='same', boundary='symm')
                reblurred = np.maximum(reblurred, 1e-10)
                
                ratio = img_float / reblurred
                correction = convolve2d(ratio, psf[::-1, ::-1], mode='same', boundary='symm')
                estimated *= correction
                estimated = np.maximum(estimated, 1e-10)
            
            result = np.clip(estimated * 255, 0, 255).astype(np.uint8)
            return result
        except Exception as e:
            logger.warning(f"[QR] Deblur failed: {e}")
            return img
    
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
            
            if result and result.get("statusCode") == "200 OK":
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
                    if rpi_result and rpi_result.get("ok") == True:
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
