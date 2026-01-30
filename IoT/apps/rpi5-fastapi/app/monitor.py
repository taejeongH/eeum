import asyncio
import time
import logging
from .state import MonitorState, Event
from .wifi_manager import (
    async_get_active_on_wlan0,
    async_scan_wifi_wlan0,
    async_list_wifi_profiles_wlan0,
)

logger = logging.getLogger(__name__)
DEBUG_DIV = 30
PIR_ABSENCE_SEC = 2 * 60 * 60 / DEBUG_DIV        # 2h
VISION_EXIT_ABSENCE_SEC = 60 * 60 / DEBUG_DIV    # 1h
DAY = 24 * 60 * 60 / (4 * DEBUG_DIV)                   # 1d

def _cancel(task: asyncio.Task | None) -> None:
    if task and not task.done():
        task.cancel()

def cancel_task(state: MonitorState, key: str) -> None:
    t = state.tasks.pop(key, None)
    _cancel(t)

def start_task(state: MonitorState, key: str, coro) -> None:
    cancel_task(state, key)
    state.tasks[key] = asyncio.create_task(coro)

async def async_record_event(state: MonitorState, ev: Event) -> None:
    ts = ev.detected_at
    dev = ev.device_id

    state.last_event_by_device[dev] = {
        "kind": ev.kind,
        "device_id": dev,
        "data": ev.data,
        "detected_at": ts,
    }

    ds = state.device_store
    if ds:
        await ds.async_mark_seen(dev, ts)

def _set_present(state: MonitorState, ev: Event) -> None:
    ts = ev.detected_at
    kind = ev.kind
    dev = ev.device_id
    ds = state.device_store
    devinfo = ds.doc().get("devices", {}).get(kind, {}).get(dev,{})
    if not devinfo:
        return
    
    if not state.occupancy_present:
        state.occupancy_since_ts = ts
    logger.info("[OCCUPANCY] absent -> present at=%s", ts)
    state.occupancy_present = True
    state.occupancy_dev = dev
    state.occupancy_kind = kind
    if kind == "vision":
        state.last_vision_ts = ts
        state.vision_active = True
    elif kind == "pir":
        state.last_pir_ts = ts

def _set_absent(state: MonitorState, device_id: str, detected_at: float, reason: str, evaluated_at: float | None = None) -> None:
    if evaluated_at is None:
        evaluated_at = time.time()

    kind = state.device_store.get_kind(device_id)
    if kind is None:
        return
    
    if state.occupancy_present:
        state.occupancy_present = False
        state.occupancy_since_ts = None

    state.occupancy_kind = kind
    state.occupancy_dev = device_id
    
    # MQTT Publish Event
    logger.info("[OCCUPANCY] present -> absent reason=%s at=%s", reason, evaluated_at)
    if state.mqtt:
        try:
            state.mqtt.publish_event({
                "kind": kind,
                "serial_number": device_id,
                "event": "absence" if kind == "vision" else "no_motion",
                "started_at": detected_at,
                "detected_at": evaluated_at
            })
        except Exception:
            logger.exception("[OCCUPANCY] mqtt publish failed")

async def pir_absence_timer(state: MonitorState, device_id: str, base_ts: float) -> None:
    try:
        await asyncio.sleep(PIR_ABSENCE_SEC)

        # 더 최근 PIR motion이 있으면 무시
        if state.last_pir_ts is not None and state.last_pir_ts > base_ts:
            return

        # vision active면 PIR로 absence 내리지 않음
        if state.vision_active:
            return

        _set_absent(state, device_id, base_ts, "pir_no_motion")
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[pir_absence_timer] unexpected error")

    
async def vision_exit_absence_timer(state: MonitorState, device_id: str, base_ts: float) -> None:
    try:
        await asyncio.sleep(VISION_EXIT_ABSENCE_SEC)

        # 더 최근 vision 이벤트가 있으면 무시
        if state.last_vision_ts is not None and state.last_vision_ts > base_ts:
            return

        # exit 이후 PIR motion 있으면 사람 있음
        if state.last_pir_ts is not None and state.last_pir_ts > base_ts:
            return

        _set_absent(state, device_id, base_ts, "vision_exit_absence")
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[vision_exit_absence_timer] unexpected error")

def handle_pir_motion(state: MonitorState, ev: Event) -> None:
    ts = ev.detected_at
    state.last_pir_ts = ts
    dev = ev.device_id
    _set_present(state, ev)

    if not state.vision_active:
        start_task(state, "pir_no_motion", pir_absence_timer(state, dev, base_ts=ts))

def handle_vision(state: MonitorState, ev: Event) -> None:
    ts = ev.detected_at
    state.last_vision_ts = ts
    data = ev.data or {}
    dev = ev.device_id
    v_ev = data.get("event")
    if not v_ev:
        return

    if v_ev == "enter":
        state.vision_active = True
        _set_present(state, ev)

        cancel_task(state, "pir_no_motion")
        cancel_task(state, "vision_exit_absence")
        return

    if v_ev == "exit":
        state.vision_active = False
        start_task(state, "vision_exit_absence", vision_exit_absence_timer(state, dev, base_ts=ts))
        return

    if v_ev == "fall_detected":
        state.fall_active = True
        state.fall_stage = "ASK_TTS"
        state.fall_started_ts = ts
        state.fall_last_stage_ts = ts
        state.fall_device = ev.device_id
        state.fall_level = int(data.get("level") or 1)
        _set_present(state, ev)
        logger.info("[FALL] triggered level=%s device=%s at=%s", state.fall_level, ev.device_id, ts)
        """
        tts -> stt 로직 추가 이후 MQTT 로직 (테스트는 임의로)
        """
        logger.info("[fall_detected] stage IDLE -> TTS")
        logger.info("[fall_detected] stage TTS -> STT")
        if state.mqtt:
            try:
                state.mqtt.publish_response({
                    "serial_number": state.device_store.get_device_id(),
                    "event": "response",
                    "stt_content": "괜찮다 괜찮아",
                    "detected_at": ts,
                    "token": state.device_store.get_token()
                })
            except Exception:
                logger.exception("[RESPONSE] mqtt publish failed")
        return

# ---------------- Wi-Fi refresh ----------------

async def refresh_wifi_active(state: MonitorState) -> None:
    try:
        state.wifi_active = await async_get_active_on_wlan0()
        state.wifi_active_ts = time.time()
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[refresh_wifi_active] unexpected error")
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
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[refresh_wifi_cache] unexpected error")

async def wifi_active_loop(state: MonitorState, interval_sec: float = 1.0) -> None:
    try:
        logger.info("[wifi_active_loop] started interval=%s", interval_sec)
        while True:
            if getattr(state, "shutting_down", False):
                return
            await refresh_wifi_active(state)
            await asyncio.sleep(interval_sec)
    except asyncio.CancelledError:
        logger.info("[wifi_active_loop] cancelled")
        raise
    except Exception:
        logger.exception("[wifi_active_loop] unexpected error")
        raise

async def wifi_scan_loop(
    state: MonitorState,
    interval_sec: float = 3.0,
    ui_recent_sec: float = 10.0,
) -> None:
    """
    UI가 wifi 설정 화면을 보고 있을 때만 scan/profiles 갱신
    """
    try:
        logger.info("[wifi_scan_loop] started interval=%s", interval_sec)
        while True:
            if getattr(state, "shutting_down", False):
                return
            now = time.time()
            if (now - state.wifi_ui_last_ping) < ui_recent_sec:
                await refresh_wifi_cache(state)
            await asyncio.sleep(interval_sec)
    except asyncio.CancelledError:
        logger.info("[wifi_scan_loop] cancelled")
        raise
    except Exception:
        logger.exception("[wifi_scan_loop] unexpected error")
        await asyncio.sleep(1.0)
        return