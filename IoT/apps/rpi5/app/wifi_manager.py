import asyncio
import logging
import subprocess
import time
from dataclasses import dataclass
from typing import Dict, List, Optional
from app.config import STA_IFACE
from app.sh import async_sh

logger = logging.getLogger(__name__)

@dataclass(frozen=True)
class WifiProfile:
    """저장된 Wi-Fi 프로필 요약"""
    name: str
    ssid: Optional[str]
    iface: Optional[str]
    autoconnect: Optional[bool]
    active_device: Optional[str]

@dataclass(frozen=True)
class ProvisionResult:
    """Wi-Fi 프로비저닝(연결 시도) 결과"""
    ok: bool
    message: str
    new_profile: Optional[str] = None

_wifi_lock = asyncio.Lock()

_SCAN_CACHE_TTL_SEC = 3.0
_RESCAN_INTERVAL_SEC = 30.0

_last_scan_ts_mono: float = 0.0
_last_scan_result: List[Dict[str, object]] = []
_last_rescan_ts_mono: float = 0.0

async def _run_nmcli(cmd: list[str], *, check: bool, timeout: float):
    """
    nmcli/네트워크 관련 명령 실행을 락으로 직렬화합니다.

    :param cmd: 실행할 명령(list[str])
    :param check: 비정상 종료 시 예외 발생 여부
    :param timeout: 타임아웃(초)
    :return: CmdResult(async_sh 반환)
    """
    async with _wifi_lock:
        return await async_sh(cmd, check=check, timeout=timeout)

def _parse_yes_no(value: str) -> Optional[bool]:
    """
    nmcli의 yes/no/true/false/1/0 값을 bool로 변환합니다.

    :param value: 문자열
    :return: True/False/None(해석 불가)
    """
    if not value:
        return None

    normalized = value.strip().lower()
    if normalized in ("yes", "true", "1"):
        return True
    if normalized in ("no", "false", "0"):
        return False
    return None

def _summarize_nmcli_error(err: BaseException) -> str:
    """
    nmcli 실행 실패를 사용자에게 노출 가능한 메시지로 정리합니다.

    :param err: 예외
    :return: 메시지 문자열
    """
    if isinstance(err, subprocess.CalledProcessError):
        stderr = (getattr(err, "stderr", "") or "").strip()
        stdout = (getattr(err, "output", "") or "").strip()
        return stderr or stdout or f"nmcli failed (rc={err.returncode})"

    if isinstance(err, asyncio.TimeoutError):
        return "nmcli timeout"

    return str(err) or "unknown error"

def _dedupe_and_sort_aps(nmcli_text: str) -> List[Dict[str, object]]:
    """
    nmcli wifi list 결과를 파싱해 SSID 기준 dedup + 정렬합니다.

    입력 라인 포맷:
      IN-USE:SSID:SIGNAL:SECURITY

    정렬 기준:
      1) in_use(True) 우선
      2) signal 내림차순

    :param nmcli_text: nmcli 출력 텍스트
    :return: AP 리스트(dict: ssid/signal/security/in_use)
    """
    best_by_ssid: Dict[str, Dict[str, object]] = {}

    for line in (nmcli_text or "").splitlines():
        parts = line.split(":", 3)
        if len(parts) != 4:
            continue

        inuse, ssid, signal_str, security = parts
        ssid = ssid.strip()
        if not ssid:
            continue

        in_use = inuse.strip() == "*"
        try:
            signal = int(signal_str.strip())
        except ValueError:
            signal = 0

        ap = best_by_ssid.get(ssid)
        if ap is None or signal > int(ap["signal"]):  # type: ignore[index]
            best_by_ssid[ssid] = {
                "ssid": ssid,
                "signal": signal,
                "security": security.strip(),
                "in_use": in_use,
            }
        elif in_use:
            ap["in_use"] = True

    aps = list(best_by_ssid.values())
    aps.sort(key=lambda x: (0 if x["in_use"] else 1, -int(x["signal"])))
    return aps

async def async_get_active_on_wlan0() -> Optional[str]:
    """
    STA_IFACE(wlan0)에 바인딩된 현재 활성 연결 이름을 반환합니다.

    :return: connection name 또는 None
    """
    result = await _run_nmcli(
        ["nmcli", "-w", "3", "-g", "GENERAL.CONNECTION", "device", "show", STA_IFACE],
        check=False,
        timeout=5.0,
    )

    connection_name = (result.stdout or "").strip()
    if not connection_name or connection_name == "--":
        return None
    return connection_name

async def async_list_wifi_profiles_wlan0() -> List[WifiProfile]:
    """
    저장된 Wi-Fi 프로필 중 STA_IFACE에 연결될 수 있는 것만 반환합니다.
    (다른 인터페이스에 강제 bind된 프로필은 제외)

    :return: WifiProfile 리스트
    """
    active_name = await async_get_active_on_wlan0() or ""

    conn_list = await _run_nmcli(
        ["nmcli", "-t", "-f", "NAME,TYPE", "connection", "show"],
        check=False,
        timeout=10.0,
    )

    wifi_profile_names: List[str] = []
    for line in (conn_list.stdout or "").splitlines():
        name_type = line.split(":")
        if len(name_type) >= 2 and name_type[1] == "802-11-wireless":
            wifi_profile_names.append(name_type[0])

    profiles: List[WifiProfile] = []
    for profile_name in wifi_profile_names:
        detail = await _run_nmcli(
            [
                "nmcli",
                "-g",
                "802-11-wireless.ssid,connection.interface-name,connection.autoconnect",
                "connection",
                "show",
                profile_name,
            ],
            check=False,
            timeout=10.0,
        )

        lines = (detail.stdout or "").splitlines()
        ssid = lines[0].strip() if len(lines) > 0 and lines[0].strip() else None
        iface_bind = lines[1].strip() if len(lines) > 1 and lines[1].strip() else None
        autoconnect = _parse_yes_no(lines[2]) if len(lines) > 2 else None

        if iface_bind and iface_bind != STA_IFACE:
            continue

        profiles.append(
            WifiProfile(
                name=profile_name,
                ssid=ssid,
                iface=iface_bind,
                autoconnect=autoconnect,
                active_device=(STA_IFACE if profile_name == active_name else None),
            )
        )

    def sort_key(profile: WifiProfile) -> tuple[int, int]:
        active_score = 0 if profile.active_device == STA_IFACE else 1
        bind_score = 0 if profile.iface == STA_IFACE else 1
        return (active_score, bind_score)

    profiles.sort(key=sort_key)
    return profiles

def _should_use_scan_cache(now_mono: float) -> bool:
    """
    짧은 TTL 동안 동일한 scan 결과를 재사용할지 결정합니다.

    :param now_mono: time.monotonic()
    :return: 캐시 사용 여부
    """
    if not _last_scan_result:
        return False
    return (now_mono - _last_scan_ts_mono) < _SCAN_CACHE_TTL_SEC

def _should_rescan(now_mono: float, force_rescan: bool) -> bool:
    """
    rescan 수행 여부를 결정합니다.

    :param now_mono: time.monotonic()
    :param force_rescan: UI 등에서 강제 요청 여부
    :return: rescan 여부
    """
    if force_rescan:
        return True
    return (now_mono - _last_rescan_ts_mono) >= _RESCAN_INTERVAL_SEC

async def async_scan_wifi_wlan0(force_rescan: bool = False) -> List[Dict[str, object]]:
    """
    Wi-Fi AP 목록을 스캔합니다.

    정책:
    - 자주 호출되는 상황을 고려하여 TTL 캐시를 적용합니다.
    - 실제 rescan은 강제 요청 또는 일정 주기(기본 30초)에만 수행합니다.

    :param force_rescan: True면 rescan을 강제로 수행합니다.
    :return: AP 리스트(dict)
    """
    global _last_scan_ts_mono, _last_scan_result, _last_rescan_ts_mono

    now_mono = time.monotonic()
    if _should_use_scan_cache(now_mono):
        return _last_scan_result

    if _should_rescan(now_mono, force_rescan):
        _last_rescan_ts_mono = now_mono
        await _run_nmcli(
            ["sudo", "-n", "nmcli", "-w", "5", "dev", "wifi", "rescan", "ifname", STA_IFACE],
            check=False,
            timeout=8.0,
        )
        await asyncio.sleep(0.3)

    result = await _run_nmcli(
        [
            "sudo",
            "-n",
            "nmcli",
            "-w",
            "5",
            "-t",
            "-f",
            "IN-USE,SSID,SIGNAL,SECURITY",
            "device",
            "wifi",
            "list",
            "ifname",
            STA_IFACE,
            "--rescan",
            "no",
        ],
        check=False,
        timeout=6.0,
    )

    aps = _dedupe_and_sort_aps(result.stdout or "")
    _last_scan_result = aps
    _last_scan_ts_mono = time.monotonic()
    return aps

async def async_bind_profile_to_wlan0(profile_name: str) -> None:
    """
    프로필을 STA_IFACE에 바인딩합니다.

    :param profile_name: connection name
    :return: None
    """
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "5", "connection", "modify", profile_name, "connection.interface-name", STA_IFACE],
        check=True,
        timeout=10.0,
    )

async def async_delete_profile(profile_name: str) -> None:
    """
    지정한 프로필을 삭제합니다.

    :param profile_name: connection name
    :return: None
    """
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "5", "connection", "delete", "id", profile_name],
        check=True,
        timeout=10.0,
    )

async def _ensure_profile_exists(profile_name: str, ssid: str) -> bool:
    """
    프로필 존재 여부를 확인합니다.

    :param profile_name: 확인할 connection id
    :param ssid: 동일 값이면 생성 시 사용할 SSID
    :return: 이미 존재하면 True, 없어서 새로 만들어야 하면 False
    """
    result = await _run_nmcli(
        ["nmcli", "-w", "3", "-g", "connection.id", "connection", "show", "id", profile_name],
        check=False,
        timeout=5.0,
    )
    return result.returncode == 0 and (result.stdout or "").strip() == profile_name

async def async_ensure_profile_named_as_ssid(ssid: str) -> None:
    """
    SSID와 동일한 이름의 프로필을 보장합니다.
    - 존재하면 STA_IFACE 바인딩만 보장합니다.
    - 없으면 새 프로필을 만들고 STA_IFACE로 바인딩합니다.

    :param ssid: SSID 문자열(동시에 connection name으로 사용)
    :return: None
    """
    exists = await _ensure_profile_exists(ssid, ssid)
    if exists:
        await async_bind_profile_to_wlan0(ssid)
        return

    await _run_nmcli(
        [
            "sudo",
            "-n",
            "nmcli",
            "-w",
            "8",
            "connection",
            "add",
            "type",
            "wifi",
            "ifname",
            STA_IFACE,
            "con-name",
            ssid,
            "ssid",
            ssid,
        ],
        check=True,
        timeout=15.0,
    )
    await async_bind_profile_to_wlan0(ssid)

async def async_set_profile_password(profile_name: str, password: str) -> None:
    """
    WPA-PSK 비밀번호를 설정합니다.

    :param profile_name: connection name
    :param password: Wi-Fi 비밀번호
    :return: None
    """
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "5", "connection", "modify", profile_name, "802-11-wireless-security.key-mgmt", "wpa-psk"],
        check=True,
        timeout=10.0,
    )
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "5", "connection", "modify", profile_name, "802-11-wireless-security.psk", password],
        check=True,
        timeout=10.0,
    )

async def async_up_profile_on_wlan0(profile_name: str) -> None:
    """
    지정한 프로필로 STA_IFACE 연결을 시도합니다.

    :param profile_name: connection name
    :return: None
    """
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "8", "connection", "up", "id", profile_name, "ifname", STA_IFACE],
        check=True,
        timeout=15.0,
    )

async def async_down_profile(profile_name: str) -> None:
    """
    지정한 프로필 연결을 내립니다(실패해도 예외를 강제하지 않습니다).

    :param profile_name: connection name
    :return: None
    """
    await _run_nmcli(
        ["sudo", "-n", "nmcli", "-w", "5", "connection", "down", "id", profile_name],
        check=False,
        timeout=10.0,
    )

async def async_provision_connect_wlan0(ssid: str, password: str) -> ProvisionResult:
    """
    SSID/PW 입력을 받아 해당 Wi-Fi로 연결을 시도합니다.

    처리 순서:
    1) SSID 이름의 프로필 보장(없으면 생성)
    2) 비밀번호 설정
    3) 연결(up) 시도

    :param ssid: SSID 문자열
    :param password: 비밀번호
    :return: ProvisionResult
    """
    logger.info("[wifi] provision start ssid=%s", ssid)

    try:
        await async_ensure_profile_named_as_ssid(ssid)
        await async_set_profile_password(ssid, password)
        await async_up_profile_on_wlan0(ssid)

        logger.info("[wifi] provision ok ssid=%s", ssid)
        return ProvisionResult(ok=True, message="connected", new_profile=ssid)

    except Exception as err:
        try:
            await async_down_profile(ssid)
        except Exception:
            pass

        message = _summarize_nmcli_error(err)
        logger.warning("[wifi] provision failed ssid=%s msg=%s", ssid, message)
        return ProvisionResult(ok=False, message=message, new_profile=ssid)