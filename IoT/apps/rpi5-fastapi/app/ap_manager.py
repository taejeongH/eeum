import subprocess
import re
from .sh import async_sh
from .config import AP_PROFILE, AP_IFACE
from typing import Optional

async def async_ap_up():
    await async_sh(["sudo", "-n", "nmcli", "connection", "up", AP_PROFILE], timeout=20.0)

async def async_get_ipv4_addr(iface: str = AP_IFACE) -> Optional[str]:
    r = await async_sh(["ip", "-4", "addr", "show", iface], check=False, timeout=5.0)
    m = re.search(r"\binet\s+(\d+\.\d+\.\d+\.\d+)/\d+", r.stdout or "")
    if not m:
        return None 
    return m.group(1)
