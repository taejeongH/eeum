import logging
import re
from typing import Optional
from app.config import AP_IFACE, AP_PROFILE
from app.sh import async_sh

logger = logging.getLogger(__name__)

_IPV4_PATTERN = re.compile(r"\binet\s+(\d+\.\d+\.\d+\.\d+)/\d+")

async def async_ap_up() -> None:
    """
    nmcli를 이용해 AP 프로필을 올립니다.
    
    :return: None
    """
    try:
        logger.info("[ap] bringing up profile=%s", AP_PROFILE)
        await async_sh(
            ["sudo", "-n", "nmcli", "-w", "8", "connection", "up", AP_PROFILE],
            timeout=20.0,
        )
        logger.info("[ap] up ok profile=%s", AP_PROFILE)
    except Exception:
        logger.exception("[ap] up failed profile=%s", AP_PROFILE)
        raise

async def async_get_ipv4_addr(iface: str = AP_IFACE) -> Optional[str]:
    """
    주어진 인터페이스에 할당된 IPv4 주소를 반환합니다.

    :param iface: 네트워크 인터페이스 이름
    :return: IPv4 문자열 또는 None
    """
    result = await async_sh(["ip", "-4", "addr", "show", iface], check=False, timeout=5.0)

    match = _IPV4_PATTERN.search(result.stdout or "")
    if not match:
        logger.debug(
            "[ap] no ipv4 on iface=%s rc=%s out=%r err=%r",
            iface,
            result.returncode,
            result.stdout,
            result.stderr,
        )
        return None

    ip_addr = match.group(1)
    logger.debug("[ap] ipv4 iface=%s ip=%s", iface, ip_addr)
    return ip_addr