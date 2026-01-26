import asyncio
import subprocess
import time
from dataclasses import dataclass
from typing import Optional, List, Dict
from .sh import async_sh
from .config import STA_IFACE

# -------- dataclass --------
@dataclass
class WifiProfile:
    name: str
    ssid: Optional[str]
    iface: Optional[str]
    autoconnect: Optional[bool]
    active_device: Optional[str]

@dataclass
class ProvisionResult:
    ok: bool
    message: str
    new_profile: Optional[str] = None

# -------- lock & TTL --------
_wifi_lock = asyncio.Lock()

_SCAN_CACHE_TTL_SEC = 3.0
_last_scan_ts: float = 0.0
_last_scan_result: List[Dict[str, object]] = []

# -------- Helpers --------
def _parse_bool(s: str) -> Optional[bool]:
    if not s:
        return None
    s = s.strip().lower()
    if s in ("yes", "true", "1"):
        return True
    if s in ("no", "false", "0"):
        return False
    return None

def _parse_wifi_list_text(text: str) -> List[Dict[str, object]]:
    """
    nmcli -t -f IN-USE,SSID,SIGNAL,SECURITY device wifi list ...
    결과를 SSID 기준으로 dedup 하고, in_use 우선 + signal 내림차순 정렬
    """
    best: Dict[str, Dict[str, object]] = {}

    for line in (text or "").splitlines():
        # IN-USE:SSID:SIGNAL:SECURITY
        parts = line.split(":", 3)
        if len(parts) != 4:
            continue

        inuse, ssid, sig, sec = parts
        ssid = ssid.strip()
        if not ssid:
            continue  # 숨김 SSID 제외

        in_use = inuse.strip() == "*"
        try:
            signal = int(sig.strip())
        except ValueError:
            signal = 0

        sec = sec.strip()

        cur = best.get(ssid)
        if cur is None or signal > int(cur["signal"]):  # type: ignore
            best[ssid] = {"ssid": ssid, "signal": signal, "security": sec, "in_use": in_use}
        else:
            if in_use:
                cur["in_use"] = True

    aps = list(best.values())
    aps.sort(key=lambda x: (0 if x["in_use"] else 1, -int(x["signal"])))
    return aps

def _nmcli_err_to_message(e: BaseException) -> str:
    # CalledProcessError / TimeoutError 등을 사용자에게 보여줄 문자열로 정리
    if isinstance(e, subprocess.CalledProcessError):
        stderr = (getattr(e, "stderr", "") or "").strip()
        stdout = (getattr(e, "output", "") or "").strip()
        return stderr or stdout or f"nmcli failed (rc={e.returncode})"
    if isinstance(e, asyncio.TimeoutError):
        return "nmcli timeout"
    return str(e) or "unknown error"

# -------- 조회 유틸 --------
async def async_get_active_on_wlan0() -> Optional[str]:
    """
    STA_IFACE에 바인딩된 현재 active connection 이름 반환
    """
    async with _wifi_lock:
        r = await async_sh(
            ["nmcli", "-g", "GENERAL.CONNECTION", "device", "show", STA_IFACE],
            check=False,
            timeout=5.0,
        )
        conn = (r.stdout or "").strip()
        return conn if conn and conn != "--" else None

async def async_list_wifi_profiles_wlan0() -> List[WifiProfile]:
    """
    저장된 Wi-Fi 프로필 목록을 읽고, STA_IFACE에 bind된 것만 필터링 후 정렬
    """
    async with _wifi_lock:
        # active connection id
        r_active = await async_sh(
            ["nmcli", "-g", "GENERAL.CONNECTION", "device", "show", STA_IFACE],
            check=False,
            timeout=5.0,
        )
        active = (r_active.stdout or "").strip()
        if not active or active == "--":
            active = ""

        # 전체 connection 목록에서 wifi 타입만 이름 수집
        r = await async_sh(["nmcli", "-t", "-f", "NAME,TYPE", "connection", "show"], check=False, timeout=10.0)
        wifi_names: List[str] = []
        for line in (r.stdout or "").splitlines():
            parts = line.split(":")
            if len(parts) >= 2 and parts[1] == "wifi":
                wifi_names.append(parts[0])

        profiles: List[WifiProfile] = []
        for name in wifi_names:
            r2 = await async_sh(
                ["nmcli", "-g",
                 "802-11-wireless.ssid,connection.interface-name,connection.autoconnect",
                 "connection", "show", name],
                check=False,
                timeout=10.0,
            )
            vals = (r2.stdout or "").splitlines()
            ssid = vals[0].strip() if len(vals) > 0 and vals[0].strip() else None
            iface_bind = vals[1].strip() if len(vals) > 1 and vals[1].strip() else None
            autoconnect = _parse_bool(vals[2]) if len(vals) > 2 else None

            # 다른 iface에 bind된 프로필은 제외
            if iface_bind and iface_bind != STA_IFACE:
                continue

            profiles.append(WifiProfile(
                name=name,
                ssid=ssid,
                iface=iface_bind,
                autoconnect=autoconnect,
                active_device=(STA_IFACE if active == name else None)
            ))

        # active / bind 우선 정렬
        def _score(p: WifiProfile) -> int:
            score = 0
            if p.active_device == STA_IFACE:
                score += 10
            if p.iface == STA_IFACE:
                score += 5
            return -score

        profiles.sort(key=_score)
        return profiles

async def async_scan_wifi_wlan0() -> List[Dict[str, object]]:
    """
    정책: rescan -> list 번들 실행
    연타/중복 호출 방지를 위해 짧은 TTL 캐시를 적용
    """
    global _last_scan_ts, _last_scan_result

    async with _wifi_lock:
        now = time.monotonic()
        if _SCAN_CACHE_TTL_SEC > 0 and _last_scan_result and (now - _last_scan_ts) < _SCAN_CACHE_TTL_SEC:
            return _last_scan_result

        # 1) rescan
        await async_sh(
            ["sudo", "-n", "nmcli", "dev", "wifi", "rescan", "ifname", STA_IFACE],
            check=False,
            timeout=10.0,
        )
        # rescan 반영 대기
        await asyncio.sleep(0.8)

        # 2) list
        r = await async_sh(
            ["sudo", "-n", "nmcli", "-t",
             "-f", "IN-USE,SSID,SIGNAL,SECURITY",
             "device", "wifi", "list",
             "ifname", STA_IFACE],
            check=False,
            timeout=10.0,
        )

        aps = _parse_wifi_list_text(r.stdout or "")

        _last_scan_result = aps
        _last_scan_ts = time.monotonic()
        return aps



# -------- 프로필 조작 --------

async def async_bind_profile_to_wlan0(name: str) -> None:
    async with _wifi_lock:
        await async_sh(["sudo", "-n", "nmcli", "connection", "modify", name, "connection.interface-name", STA_IFACE], timeout=10.0)

async def async_delete_profile(name: str) -> None:
    async with _wifi_lock:
        await async_sh(["sudo", "-n", "nmcli", "connection", "delete", "id", name], timeout=10.0)

async def async_ensure_profile_named_as_ssid(ssid: str) -> None:
    """
    SSID와 동일한 이름의 프로필이 있으면 iface bind만 보장.
    없으면 새로 만들고 bind.
    """
    async with _wifi_lock:
        r = await async_sh(["nmcli", "-g", "connection.id", "connection", "show", "id", ssid], check=False, timeout=5.0)
        exists = r.returncode == 0 and (r.stdout or "").strip() == ssid

        if exists:
            await async_sh(["sudo", "-n", "nmcli", "connection", "modify", ssid, "connection.interface-name", STA_IFACE], timeout=10.0)
            return

        await async_sh([
            "sudo", "-n", "nmcli", "connection", "add",
            "type", "wifi",
            "ifname", STA_IFACE,
            "con-name", ssid,
            "ssid", ssid
        ], timeout=15.0)

        await async_sh(["sudo", "-n", "nmcli", "connection", "modify", ssid, "connection.interface-name", STA_IFACE], timeout=10.0)


async def async_set_profile_password(name: str, password: str) -> None:
    async with _wifi_lock:
        await async_sh(["sudo", "-n", "nmcli", "connection", "modify", name, "802-11-wireless-security.key-mgmt", "wpa-psk"], timeout=10.0)
        await async_sh(["sudo", "-n", "nmcli", "connection", "modify", name, "802-11-wireless-security.psk", password], timeout=10.0)


async def async_up_profile_on_wlan0(name: str) -> None:
    async with _wifi_lock:
        await async_sh(["sudo", "-n", "nmcli", "connection", "up", "id", name, "ifname", STA_IFACE], timeout=30.0)


async def async_down_profile(name: str) -> None:
    async with _wifi_lock:
        await async_sh(["sudo", "-n", "nmcli", "connection", "down", "id", name], check=False, timeout=20.0)


# -------- 프로비저닝(SSID/PW 입력 → 연결 시도) --------

async def async_provision_connect_wlan0(ssid: str, password: str) -> ProvisionResult:
    """
    SSID/PW를 받아:
      - 프로필 보장(없으면 생성, 있으면 bind)
      - 비번 설정
      - up 시도
    """
    try:
        await async_ensure_profile_named_as_ssid(ssid)
        await async_set_profile_password(ssid, password)

        try:
            await async_up_profile_on_wlan0(ssid)
        except Exception as e:
            try:
                await async_down_profile(ssid)
            except Exception:
                pass

            return ProvisionResult(ok=False, message=_nmcli_err_to_message(e), new_profile=ssid)

        return ProvisionResult(ok=True, message="connected", new_profile=ssid)

    except Exception as e:
        return ProvisionResult(ok=False, message=_nmcli_err_to_message(e), new_profile=ssid)