"""
QR 페어링 모드
카메라에서 QR 코드를 인식하고 서버와의 장치 페어링을 수행합니다.
"""

import cv2
import time
import numpy as np
from typing import Optional, Tuple, Dict, Any
import sys
try:
    if sys.platform == "win32":
        
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

try:
    if sys.platform == "win32":
        # Windows 환경에서는 libzbar DLL 의존성 문제로 기본 비활성화 처리
        PYZBAR_AVAILABLE = False
    else:
        from pyzbar.pyzbar import decode, ZBarSymbol
        PYZBAR_AVAILABLE = True
except (ImportError, OSError):
    PYZBAR_AVAILABLE = False
    decode = None
    ZBarSymbol = None

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
    장치 등록을 위한 QR 코드 인식 모드입니다.
    
    작동 흐름:
    1. 실시간 카메라 프레임 캡처
    2. 강력한 영상 처리 알고리즘을 통한 QR 코드 감지 (디블러링, 샤프닝 등)
    3. QR 토큰 추출 및 서버 검증/등록 요청
    4. 등록 성공 시 액세스 토큰을 확보하고 장치 상태를 업데이트
    """
    
    def __init__(self, cap: cv2.VideoCapture, jpeg_quality: int = 80):
        super().__init__("QRMode")
        self.cap = cap
        self.jpeg_quality = jpeg_quality
        self.frame_index = 0
        
        self.server_client = ServerClient()
        self.device_state = get_device_state()
        
        
        self.last_qr_time = 0
        self.qr_cooldown = 1.0  
    
    def setup(self) -> bool:
        """모드를 시작하기 전 필요한 초기 설정을 수행합니다."""
        try:
            logger.info("Initializing QR Mode")
            self.is_running = True
            return True
        except Exception as e:
            logger.error(f"QR Mode setup failed: {e}")
            return False
    
    def cleanup(self):
        """모드 종료 시 리소스를 정리합니다."""
        self.is_running = False
        logger.info("QR Mode cleaned up")
    
    def step(self) -> Tuple[Optional[Dict[str, Any]], Optional[bytes], Optional[Any], Optional[Any]]:
        """
        한 프레임에 대해 QR 코드 감지를 시도합니다.
        
        Returns:
            (None, jpg_데이터, bgr_프레임, None)
        """
        ok, frame = self.cap.read()
        if not ok:
            return None, None, None, None
        
        
        qr_data = self._detect_qr(frame)
        
        
        jpg = self._encode_jpeg(frame)
        
        if qr_data:
            self._handle_qr(qr_data)
        
        self.frame_index += 1
        
        
        return None, jpg, frame, None
    
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
        
        
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
        gray_enhanced = clahe.apply(gray)
        
        
        blur = cv2.GaussianBlur(gray_enhanced, (0, 0), 2)
        sharpened = cv2.addWeighted(gray_enhanced, 2.5, blur, -1.5, 0)
        
        
        deblurred = self._richardson_lucy_deblur(gray_enhanced) if SCIPY_AVAILABLE else sharpened
        
        
        darkened = cv2.convertScaleAbs(gray_enhanced, alpha=0.6, beta=0)
        
        
        bilateral = cv2.bilateralFilter(gray_enhanced, 9, 75, 75)
        
        
        candidates = [
            ("Deblur", deblurred),  
            ("Sharpen", sharpened),
            ("Base", gray_enhanced),
            ("Darkened", darkened),
            ("Bilateral", bilateral),
        ]
        
        
        scales = [1.0, 0.7, 0.85, 1.15, 1.3, 1.5]
        
        qrs = []
        found_method = ""
        
        
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (2, 2))
        
        
        for name, img in candidates:
            
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

        
        if qrs:
            logger.info(f"[QR] Detected via {found_method}")
        else:
            logger.debug("[QR] no symbol")

        
        for qr in qrs:
            (x, y, w, h) = qr.rect
            
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
            
            psf = np.zeros((psf_size, psf_size))
            psf[psf_size//2, psf_size//2] = 1
            psf = gaussian_filter(psf, sigma=1.5)
            psf /= psf.sum()
            
            
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
    
    def _richardson_lucy_deblur(self, img, iterations=10, psf_size=5):
        """ Richardson-Lucy 디콘볼루션을 통해 영상의 흐림 현상을 개선합니다. """
        if not SCIPY_AVAILABLE: return img
        try:
            psf = np.zeros((psf_size, psf_size))
            psf[psf_size//2, psf_size//2] = 1
            psf = gaussian_filter(psf, sigma=1.5)
            psf /= psf.sum()
            
            img_float = img.astype(np.float64) / 255.0
            img_float = np.maximum(img_float, 1e-10)
            
            estimated = img_float.copy()
            for _ in range(iterations):
                reblurred = convolve2d(estimated, psf, mode='same', boundary='symm')
                reblurred = np.maximum(reblurred, 1e-10)
                correction = convolve2d(img_float / reblurred, psf[::-1, ::-1], mode='same', boundary='symm')
                estimated *= correction
                estimated = np.maximum(estimated, 1e-10)
            
            return np.clip(estimated * 255, 0, 255).astype(np.uint8)
        except Exception:
            return img
    
    def _handle_qr(self, qr_token: str):
        """인식된 QR 토큰을 서버에 보내 장치 등록을 시도합니다."""
        current_time = time.time()
        
        
        if (current_time - self.last_qr_time) < self.qr_cooldown:
            return
        
        self.last_qr_time = current_time
        
        
        logger.info(f"QR detected: {qr_token[:8]}...")
        print(f"[QRMode] QR detected: {qr_token}...")

        
        try:
            
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
                
                
                try:
                    rpi_result = self.server_client.send_access_token_to_rpi(access_token)
                    if rpi_result and rpi_result.get("ok") == True:
                        logger.info("Access token sent to RPI successfully")
                    else:
                        logger.error("Failed to send access token to RPI")
                except Exception as e:
                    logger.error(f"Error sending access token to RPI: {e}")
                    
                
                if self.device_state.register(
                    device_id=DEVICE_ID,
                    access_token=access_token,
                    refresh_token=refresh_token,
                    group_id=group_id,
                    serial_number=serial_number
                ):
                    logger.info(f"Device registered successfully: {DEVICE_ID} (group: {group_id})")
                    
                    return True
        except Exception as e:
            logger.error(f"Registration error: {e}")
        
        return False
    
    @property
    def is_pairing_complete(self) -> bool:
        """현재 장치가 서버에 페어링되었는지 여부를 확인합니다."""
        return self.device_state.is_registered()
