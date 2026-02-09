"""
기기 상태 관리 (파일 기반 저장)

등록 정보는 JSON으로 로컬 저장:
{
    "device_id": "EEUM_J105",
    "access_token": "eyJhbG...",
    "refresh_token": "eyJhbG...",
    "group_id": 1,
    "serial_number": "JETSON-MASTER-001",
    "registered_at": 1234567890,
    "token_expiry": 1234567890
}
"""

import json
import os
from pathlib import Path
from typing import Optional, Dict, Any
import logging

logger = logging.getLogger(__name__)


class DeviceState:
    """
    기기의 등록 상태를 파일 기반으로 관리
    """
    
    def __init__(self, state_file: str = "runs/device_state.json"):
        self.state_file = Path(state_file)
        self.state_file.parent.mkdir(parents=True, exist_ok=True)
        self._state = self._load()
    
    def _load(self) -> Dict[str, Any]:
        """상태 파일 로드"""
        if self.state_file.exists():
            try:
                with open(self.state_file, "r") as f:
                    return json.load(f)
            except Exception as e:
                logger.error(f"Failed to load state: {e}")
                return {}
        return {}
    
    def _save(self):
        """상태 파일 저장"""
        try:
            with open(self.state_file, "w") as f:
                json.dump(self._state, f, indent=2)
            logger.info(f"State saved: {self.state_file}")
        except Exception as e:
            logger.error(f"Failed to save state: {e}")
    
    def is_registered(self) -> bool:
        """기기가 등록되었는가"""
        return "device_id" in self._state and "access_token" in self._state
    
    def register(self, device_id: str, access_token: str, refresh_token: str, 
                 group_id: int, serial_number: str, token_expiry: Optional[float] = None) -> bool:
        """기기 등록 (QR 인증 후 서버 응답 저장)"""
        try:
            import time
            self._state["device_id"] = device_id
            self._state["access_token"] = access_token
            self._state["refresh_token"] = refresh_token
            self._state["group_id"] = group_id
            self._state["serial_number"] = serial_number
            self._state["registered_at"] = time.time()
            
            
            if token_expiry:
                self._state["token_expiry"] = token_expiry
            else:
                self._state["token_expiry"] = time.time() + 3600 * 24 * 365
            
            self._save()
            logger.info(f"Device registered: {device_id} (group: {group_id})")
            return True
        except Exception as e:
            logger.error(f"Registration failed: {e}")
            return False
    
    def unregister(self) -> bool:
        """기기 등록 해제"""
        try:
            self._state.clear()
            self._save()
            logger.info("Device unregistered")
            return True
        except Exception as e:
            logger.error(f"Unregistration failed: {e}")
            return False
    
    def get_device_id(self) -> Optional[str]:
        """기기 ID 조회"""
        return self._state.get("device_id")
    
    def get_registration_token(self) -> Optional[str]:
        """등록 토큰 조회"""
        return self._state.get("registration_token")
    
    def get_access_token(self) -> Optional[str]:
        """액세스 토큰 조회"""
        return self._state.get("access_token")
    
    def get_refresh_token(self) -> Optional[str]:
        """리프레시 토큰 조회"""
        return self._state.get("refresh_token")
    
    def get_group_id(self) -> Optional[int]:
        """그룹 ID 조회"""
        return self._state.get("group_id")

    def is_token_expired(self) -> bool:
        """액세스 토큰이 만료되었는가"""
        import time
        token_expiry = self._state.get("token_expiry")
        if not token_expiry:
            return False
        return time.time() > token_expiry
    
    def refresh_tokens(self, new_access_token: str, new_refresh_token: str, token_expiry: Optional[float] = None) -> bool:
        """토큰 갱신 (리프레시 토큰 사용)"""
        try:
            import time
            self._state["access_token"] = new_access_token
            self._state["refresh_token"] = new_refresh_token
            if token_expiry:
                self._state["token_expiry"] = token_expiry
            else:
                self._state["token_expiry"] = time.time() + 3600
            self._save()
            logger.info("Tokens refreshed")
            return True
        except Exception as e:
            logger.error(f"Token refresh failed: {e}")
            return False
    
    def get_state(self) -> Dict[str, Any]:
        """전체 상태 조회 (읽기 전용)"""
        return dict(self._state)
    
    def update(self, **kwargs) -> bool:
        """상태 업데이트"""
        try:
            self._state.update(kwargs)
            self._save()
            return True
        except Exception as e:
            logger.error(f"Update failed: {e}")
            return False

    def ensure_valid_token(self, server_client) -> Optional[str]:
        """
        토큰 유효성 확인 및 필요 시 갱신
        
        Returns:
            valid_access_token (갱신된 경우 새 토큰, 유효하면 기존 토큰, 실패 시 None)
        """
        if not self.is_registered():
            return None

        if self.is_token_expired():
            logger.info("Access token expired. Attempting refresh...")
            refresh_token = self.get_refresh_token()
            print(refresh_token)
            if not refresh_token:
                logger.error("No refresh token available")
                return None
            
            new_data = server_client.get_access_token(refresh_token)
            if new_data:
                new_access_token = new_data.get("access_token")
                new_refresh_token = new_data.get("refresh_token")
                
                if new_access_token and new_refresh_token:
                    
                    self.refresh_tokens(new_access_token, new_refresh_token)
                    
                    
                    result = server_client.send_access_token_to_rpi(new_access_token)
                    if result and result.get("ok") == True:
                        logger.info("Access token sent to RPI successfully")
                    else:
                        logger.error("Failed to send access token to RPI")
                    
                    return new_access_token
            
            return None 
        
        return self.get_access_token()


_device_state: Optional[DeviceState] = None


def get_device_state() -> DeviceState:
    """DeviceState 싱글톤 반환"""
    global _device_state
    if _device_state is None:
        _device_state = DeviceState()
    return _device_state
