import subprocess
from fastapi import FastAPI, Query
from pydantic import BaseModel
from app.config import STA_IFACE
from app.monitor import refresh_wifi_profiles, refresh_wifi_scan
from app.state import MonitorState
from app.sync_utils import now_ts
from app.wifi_manager import (
    async_bind_profile_to_wlan0,
    async_delete_profile,
    async_provision_connect_wlan0,
    async_up_profile_on_wlan0,
)

class WifiConnectIn(BaseModel):
    ssid: str
    password: str

class WifiProfileConnectIn(BaseModel):
    name: str

class WifiDeleteProfileIn(BaseModel):
    name: str

def register(app: FastAPI, state: MonitorState) -> None:
    """
    Wi-Fi 관련 API 라우트를 등록합니다.
    
    :param app: FastAPI
    :param state: MonitorState
    :return: None
    """
    @app.post("/api/wifi/ui/ping")
    async def wifi_ui_ping():
        state.wifi_ui_last_ping = now_ts()
        return {"ok": True, "ts": state.wifi_ui_last_ping}

    @app.get("/api/wifi/scan")
    async def wifi_scan(scan: bool = Query(False, description="True면 실제 rescan+list scan 수행")):
        import logging

        log = logging.getLogger(__name__)
        log.info("[API] wifi scan requested scan=%s", scan)

        if scan:
            if state.wifi_busy:
                return {
                    "ok": True,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "aps": state.wifi_scan,
                    "ts": state.wifi_scan_ts,
                    "skipped": True,
                    "message": "wifi busy",
                }
            try:
                await refresh_wifi_scan(state, force_rescan=True)
            except Exception as e:
                log.exception("[API] wifi scan failed")
                return {
                    "ok": False,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "aps": state.wifi_scan,
                    "ts": state.wifi_scan_ts,
                    "error": str(e),
                }

        return {
            "ok": True,
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "aps": state.wifi_scan,
            "ts": state.wifi_scan_ts,
        }

    @app.get("/api/wifi/active")
    async def wifi_active():
        return {"iface": STA_IFACE, "ssid": state.wifi_active, "ts": state.wifi_active_ts}

    @app.get("/api/wifi/profiles")
    async def wifi_profiles(refresh: bool = Query(False, description="True면 nmcli로 profiles 재수집")):
        import logging

        log = logging.getLogger(__name__)

        if refresh:
            if state.wifi_busy:
                return {
                    "ok": True,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "profiles": state.wifi_profiles,
                    "ts": state.wifi_profiles_ts,
                    "skipped": True,
                    "message": "wifi busy",
                }
            try:
                await refresh_wifi_profiles(state)
            except Exception as e:
                log.exception("[API] wifi profiles refresh failed")
                return {
                    "ok": False,
                    "iface": STA_IFACE,
                    "active_ssid": state.wifi_active,
                    "profiles": state.wifi_profiles,
                    "ts": state.wifi_profiles_ts,
                    "error": str(e),
                }

        return {
            "ok": True,
            "iface": STA_IFACE,
            "active_ssid": state.wifi_active,
            "profiles": state.wifi_profiles,
            "ts": state.wifi_profiles_ts,
        }

    @app.post("/api/wifi/connect")
    async def wifi_connect(body: WifiConnectIn):
        import logging

        log = logging.getLogger(__name__)
        ssid = body.ssid.strip()
        log.info("[API] wifi connect requested ssid=%s", ssid if ssid is not None else "<NULL>")

        if not ssid:
            return {"ok": False, "code": "bad_request", "message": "ssid is required"}

        if state.wifi_active == ssid:
            return {"ok": True, "skipped": True, "ssid": ssid, "message": "already connected"}

        state.wifi_busy = True
        try:
            result = await async_provision_connect_wlan0(ssid, body.password)
            if not result.ok:
                log.warning("[API] wifi connect failed ssid=%s msg=%s", ssid, result.message)
                return {"ok": False, "code": "wifi_connect_failed", "message": result.message, "new_profile": result.new_profile}
            log.info("[API] wifi connect ok ssid=%s", ssid)
            return {"ok": True, "iface": STA_IFACE, "ssid": ssid, "message": result.message}
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {"ok": False, "code": "wifi_connect_error", "message": msg}
        except Exception as e:
            log.exception("[API] wifi connect unexpected error")
            return {"ok": False, "code": "wifi_connect_error", "message": str(e)}
        finally:
            state.wifi_busy = False

    @app.post("/api/wifi/profile/connect")
    async def wifi_profile_connect(body: WifiProfileConnectIn):
        profile_name = body.name.strip()
        if not profile_name:
            return {"ok": False, "code": "bad_request", "message": "name is required"}

        if state.wifi_active == profile_name:
            return {"ok": True, "skipped": True, "requested": profile_name, "message": "already connected"}

        state.wifi_busy = True
        try:
            await async_bind_profile_to_wlan0(profile_name)
            await async_up_profile_on_wlan0(profile_name)
            return {"ok": True, "requested": profile_name, "message": "connect requested"}
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {"ok": False, "code": "wifi_profile_connect_failed", "message": msg}
        except Exception as e:
            return {"ok": False, "code": "wifi_profile_connect_error", "message": str(e)}
        finally:
            state.wifi_busy = False

    @app.post("/api/wifi/profile/delete")
    async def wifi_profile_delete(body: WifiDeleteProfileIn):
        profile_name = body.name.strip()
        if not profile_name:
            return {"ok": False, "code": "bad_request", "message": "name is required"}

        state.wifi_busy = True
        try:
            await async_delete_profile(profile_name)
            return {"ok": True, "deleted": profile_name, "message": "deleted"}
        except subprocess.CalledProcessError as e:
            msg = (e.stderr or e.output or "").strip() or f"nmcli failed (exit={e.returncode})"
            return {"ok": False, "code": "wifi_profile_delete_failed", "message": msg}
        except Exception as e:
            return {"ok": False, "code": "wifi_profile_delete_error", "message": str(e)}
        finally:
            state.wifi_busy = False