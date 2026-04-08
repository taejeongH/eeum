import re
import logging
from .sh import async_sh
from .config import AP_PROFILE, AP_IFACE
from typing import Optional

logger = logging.getLogger(__name__)

async def async_ap_up():
    try:
        logger.info("[ap] bringing up profile=%s", AP_PROFILE)
        await async_sh(["sudo", "-n", "nmcli", "-w", "8", "connection", "up", AP_PROFILE], timeout=20.0)
        logger.info("[ap] up ok profile=%s", AP_PROFILE)
    except Exception:
        logger.exception("[ap] up failed profile=%s", AP_PROFILE)
        raise

async def async_get_ipv4_addr(iface: str = AP_IFACE) -> Optional[str]:
    r = await async_sh(["ip", "-4", "addr", "show", iface], check=False, timeout=5.0)
    m = re.search(r"\binet\s+(\d+\.\d+\.\d+\.\d+)/\d+", r.stdout or "")
    if not m:
        logger.debug("[ap] no ipv4 on iface=%s rc=%s out=%r err=%r", iface, r.returncode, r.stdout, r.stderr)
        return None
    ip = m.group(1)
    logger.debug("[ap] ipv4 iface=%s ip=%s", iface, ip)
    return ip
