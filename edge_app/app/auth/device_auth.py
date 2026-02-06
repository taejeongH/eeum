"""
기기 인증 & 토큰 관리
"""

import secrets
import hashlib
import time
from typing import Optional, Tuple
import logging

logger = logging.getLogger(__name__)


class DeviceAuth:
    """
    기기 인증 로직
    - 토큰 생성/검증
    - QR 코드 토큰 생성
    """
    
    TOKEN_EXPIRY = 180  # 3분
    TOKEN_LENGTH = 32
    
    @staticmethod
    def generate_qr_token() -> str:
        """QR 토큰 생성 (서버에서 발급)"""
        return secrets.token_hex(DeviceAuth.TOKEN_LENGTH // 2)
    
    @staticmethod
    def generate_registration_token() -> str:
        """등록 토큰 생성"""
        return secrets.token_hex(32)
    
    @staticmethod
    def hash_token(token: str) -> str:
        """토큰 해싱 (저장용)"""
        return hashlib.sha256(token.encode()).hexdigest()
    
    @staticmethod
    def verify_token(provided_token: str, stored_hash: str) -> bool:
        """토큰 검증"""
        provided_hash = DeviceAuth.hash_token(provided_token)
        # 상수 시간 비교로 타이밍 공격 방지
        return secrets.compare_digest(provided_hash, stored_hash)
    
    @staticmethod
    def is_token_valid(token_created_at: float) -> bool:
        """토큰 유효성 확인 (만료 시간 기준)"""
        return (time.time() - token_created_at) < DeviceAuth.TOKEN_EXPIRY


# 임시 토큰 저장소 (실제로는 Redis 권장)
_qr_token_store = {}  # token -> (created_at, device_id)


def create_qr_token() -> Tuple[str, float]:
    """
    QR 토큰 생성 및 저장
    
    Returns:
        (token, created_at)
    """
    token = DeviceAuth.generate_qr_token()
    created_at = time.time()
    _qr_token_store[token] = {
        "created_at": created_at,
        "device_id": None,
        "verified": False
    }
    logger.info(f"QR token created: {token[:8]}...")
    return token, created_at


def verify_qr_token(token: str) -> bool:
    """QR 토큰 검증"""
    if token not in _qr_token_store:
        logger.warning(f"QR token not found: {token}")
        return False
    
    token_data = _qr_token_store[token]
    if not DeviceAuth.is_token_valid(token_data["created_at"]):
        logger.warning(f"QR token expired: {token}")
        del _qr_token_store[token]
        return False
    
    return True


def get_qr_token(token: str) -> Optional[dict]:
    """QR 토큰 데이터 조회"""
    return _qr_token_store.get(token)


def complete_qr_token(token: str, device_id: str) -> bool:
    """QR 토큰 완료 (기기가 수신함)"""
    if token not in _qr_token_store:
        return False
    
    _qr_token_store[token]["device_id"] = device_id
    _qr_token_store[token]["verified"] = True
    logger.info(f"QR token verified: {device_id}")
    return True


def cleanup_expired_tokens():
    """만료된 토큰 정리 (제거)"""
    current_time = time.time()
    expired = [
        token for token, data in _qr_token_store.items()
        if (current_time - data["created_at"]) > DeviceAuth.TOKEN_EXPIRY
    ]
    for token in expired:
        del _qr_token_store[token]
    
    if expired:
        logger.info(f"Cleaned up {len(expired)} expired tokens")
