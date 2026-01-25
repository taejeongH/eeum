import asyncio
import time
from .state import MonitorState
from .wifi_manager import (
    async_get_active_on_wlan0,
    async_scan_wifi_wlan0,
    async_list_wifi_profiles_wlan0,
)

PIR_TIMER_SEC = 2 * 60 * 60  # 2 hours

def _cancel(task: asyncio.Task | None):
    if task and not task.done():
        task.cancel()

async def _timer_worker(state: MonitorState):
    try:
        await asyncio.sleep(PIR_TIMER_SEC)
        state.alert = True
        print("[ALERT] No events for 2 hours after PIR!")
    except asyncio.CancelledError:
        pass

def pir_timer_reset(state: MonitorState):
    # 경고는 일단 끄고(새 타이머 시작)
    state.alert = False
    _cancel(state._timer_task)
    state._timer_task = asyncio.create_task(_timer_worker(state))

def pir_timer_off(state: MonitorState):
    _cancel(state._timer_task)
    state._timer_task = None
    state.alert = False

# ---------------- Wi-Fi refresh ----------------

async def refresh_wifi_active(state: MonitorState) -> None:
    try:
        state.wifi_active = await async_get_active_on_wlan0()
        state.wifi_active_ts = time.time()
    except Exception:
        pass

async def refresh_wifi_cache(state: MonitorState) -> None:
    # wifi scan/profiles 갱신
    async with state.wifi_cache_lock:
        if state.wifi_busy:
            return
        try:
            aps = await async_scan_wifi_wlan0()
            profiles = await async_list_wifi_profiles_wlan0()
            state.wifi_scan = aps
            state.wifi_profiles = [p.__dict__ for p in profiles]
            state.wifi_cache_ts = time.time()
        except Exception:
            pass

async def wifi_active_loop(state: MonitorState, interval_sec: float = 1.0) -> None:
    try:
        while True:
            await refresh_wifi_active(state)
            await asyncio.sleep(interval_sec)
    except asyncio.CancelledError:
        return

async def wifi_scan_loop(
    state: MonitorState,
    interval_sec: float = 3.0,
    ui_recent_sec: float = 10.0,
) -> None:
    """
    UI가 wifi 설정 화면을 보고 있을 때만 scan/profiles 갱신
    """
    try:
        while True:
            now = time.time()
            if (now - state.wifi_ui_last_ping) < ui_recent_sec:
                await refresh_wifi_cache(state)
            await asyncio.sleep(interval_sec)
    except asyncio.CancelledError:
        return