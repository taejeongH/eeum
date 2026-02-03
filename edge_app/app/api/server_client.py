"""
서버 통신 클라이언트

QR 페어링, 기기 등록, 이벤트 전송 등 모든 서버 통신 처리
"""

import requests
import logging
from typing import Optional, Dict, Any
import mimetypes
import os

logger = logging.getLogger(__name__)


class ServerClient:
    """
    서버와의 API 통신을 담당
    """
    
    def __init__(self, server_url: str = None, rpi_url: str = None, timeout: int = 10):
        from ..config import SERVER_URL, RPI_URL
        self.server_url = server_url or SERVER_URL
        self.rpi_url = rpi_url or RPI_URL
        self.timeout = timeout
    
    # ===== QR & 등록 =====

    def register_device(self, pairing_code: str, device_id: str) -> Optional[Dict[str, Any]]:
        """
        기기 등록 (QR 토큰으로 등록 요청)
        
        Returns:
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
        try:
            url = f"{self.server_url}/api/iot/auth/pairing"
            # 기본 하위 장치 목록
            # TODO: 설정 파일이나 Env에서 가져오도록 개선 가능
            child_devices = [
                {"serial_number": device_id, "device_type": "JETSON"},
                {"serial_number": "EEUM-R105", "device_type": "RPI"},
                {"serial_number": "EEUM-E105-1", "device_type": "ESP32"}
            ]

            payload = {
                "pairing_code": pairing_code,
                "master_serial": device_id,
                "devices": child_devices
            }
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()
            result = resp.json()
            
            if result.get("status") == 200:
                logger.info(f"Device registered: {device_id}")
            else:
                logger.warning(f"Registration failed: {result.get('status')}")
            
            return result
        except Exception as e:
            logger.error(f"Device registration failed: {e}")
            return None
    
    # ===== 액세스 토큰 라즈베리파이에 전송 =====
    def send_access_token_to_rpi(self, access_token: str) -> bool:
        """
        액세스 토큰을 라즈베리파이에 전송
        
        Args:
            access_token: 서버에서 발급받은 액세스 토큰
        """
        try:
            url = f"{self.rpi_url}/token"
            payload = {
                "token": access_token,
            }
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()
            logger.info("Refresh token sent to RPI")
            return True
        except Exception as e:
            logger.error(f"Refresh token send to RPI failed: {e}")
            return False

    # ===== 토큰 갱신 =====
    def get_access_token(self, refresh_token: str) -> Optional[Dict[str, Any]]:
        """
        리프레시 토큰으로 새로운 토큰 발급
        
        Args:
            serial_number: 기기 시리얼 번호
            refresh_token: 현재 리프레시 토큰
            
        Returns:
            {
                "access_token": "...",
                "refresh_token": "...",
                "serial_number": "...",
                "group_id": 1
            }
        """
        try:
            url = f"{self.server_url}/api/iot/auth/refresh"
            payload = {
                "serial_number": os.getenv("DEVICE_ID", "EEUM-J105"),
                "refresh_Token": refresh_token
            }
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()
            result = resp.json()
            print(result)

            if result.get("statusCode") == "200 OK":
                data = result.get("data", {})
                logger.info("Token refreshed successfully")
                return data
            else:
                logger.warning(f"Token refresh failed: {result.get('message')}")
                return None
        except Exception as e:
            logger.error(f"Token refresh request failed: {e}")
            return None

    # ===== 이벤트 전송 =====
    
    def send_event_rpi(self, payload: Dict[str, Any]) -> bool:
        """
        낙상 이벤트 전송
        
        Args:
            payload: {
                "kind": "vision",
                "device_id": "EEUM-J105",
                "data": {"event": "fall", "level": 1, ...},
                "detected_at": timestamp,
            }
        """
        try:
            url = f"{self.rpi_url}/event"
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()
            logger.info(f"Event sent: {payload.get('data', {}).get('event')}")
            return True
        except Exception as e:
            logger.error(f"Event send failed: {e}")
            return False
        
    def send_event_server(self, payload: Dict[str, Any], access_token: str) -> Optional[str]:
        """
        낙상 이벤트 전송
        
        Args:
            payload: {
                "kind": "vision",
                "device_id": "EEUM-J105",
                "data": {"event": "fall", "level": 1, ...},
                "detected_at": timestamp,
            }
        """
        try:
            url = f"{self.server_url}/api/iot/device/falls/detection"
            headers = {
                "Authorization": f"Bearer {access_token}"
            }
            resp = requests.post(url, json=payload, headers=headers, timeout=self.timeout)
            resp.raise_for_status()
            result = resp.json()
            presignedUrl = result.get("data", {}).get("presignedUrl")
            videoPath = result.get("data", {}).get("videoPath")
            logger.info(f"Event sent: {payload.get('data', {}).get('event')}, videoPath: {videoPath}")
            return presignedUrl, videoPath
        except Exception as e:
            logger.error(f"Event send failed: {e}")
            return None
    
    # ===== 영상 서버 업로드 =====
    def upload_clip_via_presigned_put(self, presigned_url: str, clip_path: str, timeout: float = 120.0):
        if not os.path.exists(clip_path):
            raise FileNotFoundError(clip_path)

        with open(clip_path, "rb") as f:
            resp = requests.put(
                presigned_url,
                data=f,
                headers={"Content-Type": "video/mp4"},  # ★ 매우 중요
                timeout=timeout,
            )

        # 성공
        if resp.status_code in (200, 201, 204):
            return

        print("[DBG] status", resp.status_code, "headers", dict(resp.headers), "body", resp.text[:500], flush=True)

        # 실패 → 여기서 진짜 원인을 던진다
        raise RuntimeError(
            f"S3 upload failed | "
            f"status={resp.status_code} | "
            f"response={resp.text[:1000]}"
        )

    # ===== 영상 업로드 성공 =====
    def send_video_upload_success(self, video_path: str, access_token: str) -> None:
        """
        낙상 영상 업로드 성공 이벤트 전송
        
        Args:
            payload: {
                "videoPath":"falls/group_1/20260129_120000.mp4"// S3 내 실제 파일 경로
            }
        """
        try:
            url = f"{self.server_url}/api/iot/device/falls/upload-success"
            payload = {
                "videoPath": video_path
            }
            headers = {
                "Authorization": f"Bearer {access_token}"
            }
            resp = requests.post(url, json=payload, headers=headers, timeout=self.timeout)
            resp.raise_for_status()
            logger.info(f"Event success sent: {payload}")

        except Exception as e:
            logger.error(f"Event success send failed: {e}")

    # ===== presigned_url 요청 =====
    def get_presigned_url(self, event_id: str, filename: str, groupId: int, access_token: str) -> Optional[str]:
        """
        낙상 영상 업로드용 presigned_url 요청
        
        Args:
            event_id: 이벤트 ID
            filename: 업로드할 파일명
            group_id: 그룹 ID
            access_token: 액세스 토큰
            
        Returns:
            presigned_url or None
        """
        try:
            url = f"{self.server_url}/api/iot/device/falls/presigned-url"
            params = {
                "event_id": event_id,
                "filename": filename,
                "groupId": groupId
            }
            headers = {
                "Authorization": f"Bearer {access_token}"
            }
            resp = requests.get(url, params=params, headers=headers, timeout=self.timeout)
            resp.raise_for_status()
            result = resp.json()
            presigned_url = result.get("presigned_url")
            logger.info(f"Presigned URL obtained for event: {event_id}")
            return presigned_url
        except Exception as e:
            logger.error(f"Get presigned URL failed: {e}")
            return None

    def send_clip(self, event_id: str, clip_path: str, payload: Dict[str, Any]) -> bool:
        """
        낙상 영상 클립 전송
        
        Args:
            event_id: 이벤트 ID
            clip_path: 로컬 클립 파일 경로
            payload: 메타데이터
        """
        try:
            url = f"{self.server_url}/clips"
            
            with open(clip_path, "rb") as f:
                files = {"file": f}
                data = {
                    "event_id": event_id,
                    "payload": str(payload),
                }
                resp = requests.post(url, files=files, data=data, timeout=self.timeout)
                resp.raise_for_status()
            
            logger.info(f"Clip sent: {event_id}")
            return True
        except Exception as e:
            logger.error(f"Clip send failed: {e}")
            return False
    
    # ===== 헬스체크 =====
    
    def ping(self) -> bool:
        """서버 연결 상태 확인"""
        try:
            url = f"{self.server_url}/health"
            resp = requests.get(url, timeout=self.timeout)
            return resp.status_code == 200
        except Exception as e:
            logger.debug(f"Server ping failed: {e}")
            return False
