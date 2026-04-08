import asyncio
import logging
import os
from typing import Any, Dict, List
from app.audio_manager import AudioPrio
from app.fall_pipeline import run_fall_tts_stt_pipeline
from app.state import Event, MonitorState
from app.stt_service import FasterWhisperSTT
from app.sync_utils import now_ts
from app.wifi_manager import (
    async_get_active_on_wlan0,
    async_list_wifi_profiles_wlan0,
    async_scan_wifi_wlan0,
)
from .config import PIR_ABSENCE_SEC, VISION_EXIT_ABSENCE_SEC, DEBUG_DIV

logger = logging.getLogger(__name__)
logger.info("[monitor] DEBUG_DIV=%s (EEUM_DEBUG=%s)", DEBUG_DIV, os.getenv("EEUM_DEBUG"))

def _cancel_task(task: asyncio.Task | None) -> None:
    """
    task가 살아 있으면 cancel 합니다.

    :param task: asyncio Task
    :return: None
    """
    if task and not task.done():
        task.cancel()

def cancel_task(state: MonitorState, key: str) -> None:
    """
    state.tasks에서 key로 task를 꺼내 취소합니다.

    :param state: MonitorState
    :param key: 작업 키
    :return: None
    """
    task = state.tasks.pop(key, None)
    _cancel_task(task)

def start_task(state: MonitorState, key: str, coro) -> None:
    """
    동일 key의 작업이 있으면 취소 후 새 작업을 시작합니다.

    :param state: MonitorState
    :param key: 작업 키
    :param coro: 실행할 코루틴
    :return: None
    """
    cancel_task(state, key)
    state.tasks[key] = asyncio.create_task(coro)

async def async_record_event(state: MonitorState, event: Event) -> None:
    """
    이벤트를 state에 기록하고, device_store에 last_seen을 반영합니다.
    offline->online 전이가 발생하면 mqtt online 상태를 갱신합니다.

    :param state: MonitorState
    :param event: Event
    :return: None
    """
    ts = event.detected_at
    device_id = event.device_id

    state.last_event_by_device[device_id] = {
        "kind": event.kind,
        "device_id": device_id,
        "data": event.data,
        "detected_at": ts,
    }

    device_store = state.device_store
    if not device_store:
        return

    became_online = await device_store.async_mark_seen(device_id, ts)
    if became_online and state.mqtt:
        try:
            state.mqtt.publish_online(retain=True)
        except Exception:
            logger.exception("[mqtt] publish_online on mark_seen failed")

def _set_present(state: MonitorState, event: Event) -> None:
    """
    occupancy를 present로 설정합니다.

    :param state: MonitorState
    :param event: Event
    :return: None
    """
    ts = event.detected_at
    kind = event.kind
    device_id = event.device_id

    device_store = state.device_store
    if not device_store:
        return

    devinfo = device_store.doc().get("devices", {}).get(kind, {}).get(device_id, {})
    if not devinfo:
        return

    if not state.occupancy_present:
        state.occupancy_since_ts = ts
        logger.info("[OCCUPANCY] absent -> present at=%s", ts)

    state.occupancy_present = True
    state.occupancy_dev = device_id
    state.occupancy_kind = kind

    if kind == "vision":
        state.last_vision_ts = ts
        state.vision_active = True
    elif kind == "pir":
        state.last_pir_ts = ts

def _publish_absence_event(state: MonitorState, kind: str, device_id: str, started_at: float, detected_at: float) -> None:
    """
    MQTT로 absence/no_motion 이벤트를 publish 합니다.

    :param state: MonitorState
    :param kind: 디바이스 종류
    :param device_id: 디바이스 ID
    :param started_at: 기준 시각(예: exit/motion 마지막 시각)
    :param detected_at: 평가/발행 시각
    :return: None
    """
    if not state.mqtt:
        return

    try:
        logger.info("[OCCUPANCY] MQTT publish kind=%s did=%s at=%s", kind, device_id, detected_at)
        state.mqtt.publish_event(
            {
                "kind": kind,
                "serial_number": device_id,
                "event": "absence" if kind == "vision" else "no_motion",
                "started_at": started_at,
                "detected_at": detected_at,
            }
        )
    except Exception:
        logger.exception("[OCCUPANCY] mqtt publish failed")

def _set_absent(
    state: MonitorState,
    device_id: str,
    base_detected_at: float,
    reason: str,
    evaluated_at: float | None = None,
) -> None:
    """
    occupancy를 absent로 전환하고 MQTT 이벤트를 발행합니다.

    :param state: MonitorState
    :param device_id: 디바이스 ID
    :param base_detected_at: 기준 시각(마지막 exit/motion 시각)
    :param reason: 로그용 이유 문자열
    :param evaluated_at: 평가 시각(없으면 now_ts())
    :return: None
    """
    evaluated_at = evaluated_at if evaluated_at is not None else now_ts()

    device_store = state.device_store
    if not device_store:
        return

    kind = device_store.get_kind(device_id)
    if kind is None:
        return

    if state.occupancy_present:
        logger.info("[OCCUPANCY] present -> absent reason=%s at=%s", reason, evaluated_at)
        state.occupancy_present = False
        state.occupancy_since_ts = None

    state.occupancy_kind = kind
    state.occupancy_dev = device_id

    _publish_absence_event(state, kind, device_id, base_detected_at, evaluated_at)

async def pir_absence_timer(state: MonitorState, device_id: str, base_ts: float) -> None:
    """
    PIR motion 이후 일정 시간 동안 추가 motion이 없으면 absent 처리합니다.

    :param state: MonitorState
    :param device_id: 디바이스 ID
    :param base_ts: 기준 시각
    :return: None
    """
    try:
        await asyncio.sleep(PIR_ABSENCE_SEC)

        if state.last_pir_ts is not None and state.last_pir_ts > base_ts:
            return

        if state.vision_active:
            return

        _set_absent(state, device_id, base_ts, "pir_no_motion")

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[pir_absence_timer] unexpected error")

async def vision_exit_absence_timer(state: MonitorState, device_id: str, base_ts: float) -> None:
    """
    vision exit 이후 일정 시간 동안 enter/motion이 없으면 absent 처리합니다.

    :param state: MonitorState
    :param device_id: 디바이스 ID
    :param base_ts: 기준 시각
    :return: None
    """
    try:
        await asyncio.sleep(VISION_EXIT_ABSENCE_SEC)

        if state.last_vision_ts is not None and state.last_vision_ts > base_ts:
            return

        if state.last_pir_ts is not None and state.last_pir_ts > base_ts:
            return

        _set_absent(state, device_id, base_ts, "vision_exit_absence")

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[vision_exit_absence_timer] unexpected error")

def handle_pir_motion(state: MonitorState, event: Event) -> None:
    """
    PIR motion 이벤트를 처리합니다.

    :param state: MonitorState
    :param event: Event
    :return: None
    """
    ts = event.detected_at
    state.last_pir_ts = ts
    _set_present(state, event)

    if not state.vision_active:
        start_task(state, "pir_no_motion", pir_absence_timer(state, event.device_id, base_ts=ts))

def handle_vision(state: MonitorState, event: Event) -> None:
    """
    vision enter/exit/fall_detected 이벤트를 처리합니다.

    :param state: MonitorState
    :param event: Event
    :return: None
    """
    ts = event.detected_at
    state.last_vision_ts = ts

    data = event.data or {}
    device_id = event.device_id
    vision_event = data.get("event")
    if not vision_event:
        return

    if vision_event == "enter":
        state.vision_active = True
        _set_present(state, event)
        cancel_task(state, "pir_no_motion")
        cancel_task(state, "vision_exit_absence")
        return

    if vision_event == "exit":
        state.vision_active = False
        start_task(state, "vision_exit_absence", vision_exit_absence_timer(state, device_id, base_ts=ts))
        return

    if vision_event == "fall_detected":
        _handle_fall_trigger(state, event, ts)

def _handle_fall_trigger(state: MonitorState, event: Event, ts: float) -> None:
    """
    fall_detected 트리거 처리(중복 방지, 오디오 차단, 파이프라인 시작).

    :param state: MonitorState
    :param event: Event
    :param ts: 감지 시각
    :return: None
    """
    if state.fall_active:
        logger.info("[FALL] ignored: already active")
        return

    state.audio.block_below_prio = int(AudioPrio.FALL)
    state.fall_active = True
    state.fall_stage = "ASK_TTS"
    state.fall_started_ts = ts
    state.fall_last_stage_ts = ts
    state.fall_device = event.device_id

    data = event.data or {}
    try:
        state.fall_level = int(data.get("level") or 1)
    except Exception:
        state.fall_level = 1

    _set_present(state, event)

    logger.info(
        "[FALL] triggered level=%s device=%s at=%s block_below_prio=%s",
        state.fall_level,
        event.device_id,
        ts,
        state.audio.block_below_prio,
    )

    start_task(state, "fall_pipeline", run_fall_tts_stt_pipeline(state, deadline_sec=35.0))

async def device_offline_loop(state: MonitorState, interval_sec: float = 10.0) -> None:
    """
    주기적으로 last_seen_ts를 기준으로 offline 전이를 감지하고 저장/발행합니다.

    :param state: MonitorState
    :param interval_sec: 체크 주기(초)
    :return: None
    """
    try:
        logger.info("[device_offline_loop] started interval=%s", interval_sec)

        if not hasattr(state, "_dev_online_cache"):
            state._dev_online_cache = {}

        while True:
            if getattr(state, "shutting_down", False):
                return

            await _check_offline_transitions(state)
            await asyncio.sleep(interval_sec)

    except asyncio.CancelledError:
        logger.info("[device_offline_loop] cancelled")
        raise
    except Exception:
        logger.exception("[device_offline_loop] unexpected error")
        await asyncio.sleep(1.0)

async def _check_offline_transitions(state: MonitorState) -> None:
    """
    device_store의 문서를 순회하며 online/offline 전이를 처리합니다.

    :param state: MonitorState
    :return: None
    """
    device_store = state.device_store
    if not device_store:
        return

    now = now_ts()
    doc = device_store.doc() or {}
    devices = doc.get("devices") or {}

    for kind, device_map in devices.items():
        if not isinstance(device_map, dict):
            continue

        for device_id, info in device_map.items():
            if not isinstance(info, dict):
                continue

            last_seen = info.get("last_seen_ts")
            if last_seen is None:
                continue

            try:
                last_seen_ts = float(last_seen)
            except Exception:
                continue

            should_be_online = (now - last_seen_ts) <= float(state.offline_after_sec)
            is_online = bool(info.get("online", False))

            prev_online = state._dev_online_cache.get(device_id)
            if prev_online is None:
                state._dev_online_cache[device_id] = is_online
                continue

            if is_online and not should_be_online:
                await _set_device_offline_and_publish(state, device_store, device_id, kind, last_seen_ts, now)
                is_online = False

            if (not prev_online) and is_online and should_be_online:
                _publish_online_state_if_possible(state, device_id, kind, last_seen_ts, now)

            state._dev_online_cache[device_id] = is_online

async def _set_device_offline_and_publish(
    state: MonitorState,
    device_store,
    device_id: str,
    kind: str,
    last_seen_ts: float,
    now: float,
) -> None:
    """
    online -> offline 전이를 저장하고, mqtt online 상태(link)를 갱신 발행합니다.

    :param state: MonitorState
    :param device_store: DeviceStore
    :param device_id: 디바이스 ID
    :param kind: 디바이스 kind
    :param last_seen_ts: 마지막 관측 시각
    :param now: 현재 시각
    :return: None
    """
    try:
        changed = await device_store.async_set_offline(device_id)
        if not changed:
            return

        if state.mqtt:
            state.mqtt.publish_online(retain=True)

        logger.warning(
            "[device] offline did=%s kind=%s last_seen=%s now=%s",
            device_id,
            kind,
            last_seen_ts,
            now,
        )

    except Exception:
        logger.exception("[device] set_offline failed did=%s", device_id)

def _publish_online_state_if_possible(state: MonitorState, device_id: str, kind: str, last_seen_ts: float, now: float) -> None:
    """
    offline -> online 전이를 감지했을 때 mqtt online 상태를 갱신 발행합니다.

    :param state: MonitorState
    :param device_id: 디바이스 ID
    :param kind: 디바이스 kind
    :param last_seen_ts: 마지막 관측 시각
    :param now: 현재 시각
    :return: None
    """
    if not state.mqtt:
        return

    try:
        state.mqtt.publish_online(retain=True)
        logger.info(
            "[device] online detected did=%s kind=%s last_seen=%s now=%s",
            device_id,
            kind,
            last_seen_ts,
            now,
        )
    except Exception:
        logger.exception("[device] publish_online failed did=%s", device_id)

async def maybe_cache_stt_on_wifi_up(state: MonitorState) -> None:
    """
    Wi-Fi가 붙는 순간, STT 모델 캐시가 없던 경우에 한해 1회 다운로드를 시도합니다.

    조건:
    - state.stt_cache_missing=True
    - state.stt_cache_attempted=False
    - EEUM_STT_OFFLINE != "1"
    - state.wifi_active가 존재(SSID 연결됨)

    :param state: MonitorState
    :return: None
    """
    if not getattr(state, "stt_cache_missing", False):
        return
    if getattr(state, "stt_cache_attempted", False):
        return
    if os.getenv("EEUM_STT_OFFLINE", "0").strip() == "1":
        return
    if not getattr(state, "wifi_active", None):
        return

    state.stt_cache_attempted = True

    model_size = os.getenv("EEUM_STT_MODEL", "tiny")
    hf_root = (os.getenv("HF_HOME") or "").strip() or None

    try:
        logger.info("[STT] wifi up -> start caching once model=%s root=%s", model_size, hf_root)
        engine = FasterWhisperSTT(
            model_size=model_size,
            device="cpu",
            compute_type="int8",
            local_files_only=False,
            download_root=hf_root,
            cpu_threads=2,
        )

        state.stt_engine = engine
        state.stt_cache_missing = False
        logger.info("[STT] caching done -> engine swapped")

    except Exception as e:
        logger.warning("[STT] caching failed (one-shot) err=%r", e)

async def refresh_wifi_active(state: MonitorState) -> None:
    """
    STA_IFACE의 active connection(SSID)을 갱신합니다.

    :param state: MonitorState
    :return: None
    """
    try:
        state.wifi_active = await async_get_active_on_wlan0()
        state.wifi_active_ts = now_ts()
    except asyncio.TimeoutError:
        state.wifi_active_ts = now_ts()
        logger.info("[wifi] active check timeout (keep last=%r)", state.wifi_active)
    except Exception:
        logger.exception("[refresh_wifi_active] unexpected error")

async def refresh_wifi_scan(state: MonitorState, *, force_rescan: bool = False) -> List[Dict[str, Any]]:
    """
    Wi-Fi scan 캐시를 갱신합니다.

    :param state: MonitorState
    :param force_rescan: True면 rescan 수행
    :return: AP 리스트
    """
    async with state.wifi_cache_lock:
        if state.wifi_busy:
            return state.wifi_scan

        aps = await async_scan_wifi_wlan0(force_rescan=force_rescan)
        state.wifi_scan = aps
        state.wifi_scan_ts = now_ts()
        return aps

async def refresh_wifi_profiles(state: MonitorState) -> List[Dict[str, Any]]:
    """
    Wi-Fi profiles 캐시를 갱신합니다.

    :param state: MonitorState
    :return: profiles 리스트(dict)
    """
    async with state.wifi_cache_lock:
        if state.wifi_busy:
            return state.wifi_profiles

        profiles = await async_list_wifi_profiles_wlan0()
        state.wifi_profiles = [p.__dict__ for p in profiles]
        state.wifi_profiles_ts = now_ts()
        return state.wifi_profiles

def _is_heavy_ops_allowed(state: MonitorState) -> bool:
    """
    현재 nmcli 같은 무거운 작업을 수행해도 되는 상태인지 판단합니다.

    정책:
    - FALL 파이프라인 중에는 무거운 작업을 피합니다.
    - STT가 바쁘거나 heavy_ops_pause가 켜져 있으면 피합니다.

    :param state: MonitorState
    :return: 허용이면 True
    """
    try:
        fall_mode = bool(getattr(state, "fall_active", False)) or (
            int(getattr(state.audio, "block_below_prio", 0) or 0) >= int(AudioPrio.FALL)
        )
    except Exception:
        fall_mode = bool(getattr(state, "fall_active", False))

    stt_busy = bool(getattr(state, "stt_busy", False))
    paused = bool(getattr(state, "heavy_ops_pause", False))
    return (not fall_mode) and (not stt_busy) and (not paused) and (not state.wifi_busy)

async def wifi_active_loop(state: MonitorState, interval_sec: float = 3.0) -> None:
    """
    active SSID를 갱신하고, wifi가 새로 붙는 순간 1회 STT 캐싱을 시도합니다.
    UI 활성 여부에 따라 sleep 주기를 조절합니다.

    :param state: MonitorState
    :param interval_sec: (사용하지 않음) 호환용 파라미터
    :return: None
    """
    try:
        logger.info("[wifi_active_loop] started interval=%s", interval_sec)

        prev_active: str | None = None
        while True:
            if getattr(state, "shutting_down", False):
                return

            now = now_ts()
            ui_on = (now - float(getattr(state, "wifi_ui_last_ping", 0.0))) < 15.0

            if _is_heavy_ops_allowed(state):
                await refresh_wifi_active(state)
            else:
                logger.debug(
                    "[wifi_active_loop] skip active ui_on=%s wifi_busy=%s fall_active=%s stt_busy=%s paused=%s",
                    ui_on,
                    state.wifi_busy,
                    getattr(state, "fall_active", False),
                    getattr(state, "stt_busy", False),
                    getattr(state, "heavy_ops_pause", False),
                )

            cur_active = state.wifi_active
            if (not prev_active) and cur_active:
                try:
                    await maybe_cache_stt_on_wifi_up(state)
                except Exception:
                    logger.exception("[STT] maybe_cache_stt_on_wifi_up failed")
            prev_active = cur_active

            sleep_sec = 2.0 if ui_on else 15.0
            if state.wifi_busy:
                sleep_sec = max(sleep_sec, 5.0)
            await asyncio.sleep(sleep_sec)

    except asyncio.CancelledError:
        logger.info("[wifi_active_loop] cancelled")
        raise
    except Exception:
        logger.exception("[wifi_active_loop] unexpected error")
        raise

async def wifi_scan_loop(
    state: MonitorState,
    scan_interval_sec: float = 5.0,
    profiles_interval_sec: float = 15.0,
    ui_recent_sec: float = 10.0,
) -> None:
    """
    UI가 wifi 설정 화면을 보고 있을 때만 scan/profiles 갱신합니다.

    :param state: MonitorState
    :param scan_interval_sec: scan 주기
    :param profiles_interval_sec: profiles 갱신 주기
    :param ui_recent_sec: UI 활성 판단 기준(초)
    :return: None
    """
    last_profiles_refresh_ts = 0.0
    prev_ui_on = False

    try:
        logger.info("[wifi_scan_loop] started interval=%s", scan_interval_sec)

        while True:
            if getattr(state, "shutting_down", False):
                return

            now = now_ts()
            ui_on = (now - state.wifi_ui_last_ping) < ui_recent_sec

            if ui_on and not prev_ui_on:
                logger.info("[wifi_ui] UI active (scan enabled)")
                last_profiles_refresh_ts = 0.0
                try:
                    await refresh_wifi_scan(state, force_rescan=True)
                except Exception:
                    logger.exception("[wifi] force_rescan on ui enter failed")

            if (not ui_on) and prev_ui_on:
                logger.info("[wifi_ui] UI inactive (scan paused)")

            prev_ui_on = ui_on

            if ui_on and _is_heavy_ops_allowed(state):
                await refresh_wifi_scan(state, force_rescan=False)

                if (now - last_profiles_refresh_ts) >= profiles_interval_sec:
                    await refresh_wifi_profiles(state)
                    last_profiles_refresh_ts = now

            elif ui_on:
                logger.debug(
                    "[wifi_scan_loop] skip scan ui_on=1 wifi_busy=%s fall_active=%s stt_busy=%s paused=%s",
                    state.wifi_busy,
                    getattr(state, "fall_active", False),
                    getattr(state, "stt_busy", False),
                    getattr(state, "heavy_ops_pause", False),
                )

            await asyncio.sleep(scan_interval_sec)

    except asyncio.CancelledError:
        logger.info("[wifi_scan_loop] cancelled")
        raise
    except Exception:
        logger.exception("[wifi_scan_loop] unexpected error")
        await asyncio.sleep(1.0)