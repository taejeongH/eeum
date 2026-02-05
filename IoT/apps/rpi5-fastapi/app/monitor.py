import asyncio
import time
import logging
import os
from typing import List, Dict, Any
from .state import MonitorState, Event
from .wifi_manager import (
    async_get_active_on_wlan0,
    async_scan_wifi_wlan0,
    async_list_wifi_profiles_wlan0,
)
from .fall_pipeline import run_fall_tts_stt_pipeline
from .audio_manager import AudioPrio
from .stt_service import FasterWhisperSTT

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
        became_online = await ds.async_mark_seen(dev, ts)

        # offline->online 전이일 때만 status(link) 갱신
        if became_online and state.mqtt:
            try:
                state.mqtt.publish_online(retain=True)
            except Exception:
                logger.exception("[mqtt] publish_online on mark_seen failed")
      
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
        logger.info("[OCCUPANCY] present -> absent reason=%s at=%s", reason, evaluated_at)
        state.occupancy_present = False
        state.occupancy_since_ts = None

    state.occupancy_kind = kind
    state.occupancy_dev = device_id
    
    # MQTT Publish Event
    if state.mqtt:
        try:
            logger.info("[OCCUPANCY] MQTT publish reason=%s at=%s", reason, evaluated_at)
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
        if state.fall_active:
            logger.info("[FALL] ignored: already active")
            return
        cancel_task(state, "tts_alarm")  # 알람 TTS 재생/대기 중이면 즉시 취소
        state.audio.block_below_prio = int(AudioPrio.FALL) # fall 중에는 fall만 재생
        state.fall_active = True
        state.fall_stage = "ASK_TTS"
        state.fall_started_ts = ts
        state.fall_last_stage_ts = ts
        state.fall_device = ev.device_id
        state.fall_level = int(data.get("level") or 1)
        _set_present(state, ev)

        logger.info(
            "[FALL] triggered level=%s device=%s at=%s block_below_prio=%s",
            state.fall_level, ev.device_id, ts, state.audio.block_below_prio
        )

        start_task(state, "fall_pipeline", run_fall_tts_stt_pipeline(state, deadline_sec=35.0))

async def device_offline_loop(state: MonitorState, interval_sec: float = 10.0) -> None:
    try:
        logger.info("[device_offline_loop] started interval=%s", interval_sec)

        # did -> last published online(bool) cache
        if not hasattr(state, "_dev_online_cache"):
            state._dev_online_cache = {}

        while True:
            if getattr(state, "shutting_down", False):
                return

            ds = state.device_store
            if ds:
                now = time.time()
                doc = ds.doc() or {}
                devices = (doc.get("devices") or {})

                for kind, m in devices.items():
                    if not isinstance(m, dict):
                        continue
                    for did, info in m.items():
                        if not isinstance(info, dict):
                            continue

                        last = info.get("last_seen_ts")
                        if last is None:
                            continue
                        try:
                            last_f = float(last)
                        except Exception:
                            continue

                        should_be_online = (now - last_f) <= float(state.offline_after_sec)

                        # json에 저장된 online을 "진짜 상태"로 사용
                        is_online = bool(info.get("online", False))

                        # 캐시에 저장된 이전 상태(없으면 최초 관측)
                        prev_online = state._dev_online_cache.get(did)
                        if prev_online is None:
                            state._dev_online_cache[did] = is_online
                            continue

                        # 1) online -> offline 전이: persist + publish
                        if is_online and (not should_be_online):
                            try:
                                changed = await ds.async_set_offline(did)
                                if changed:
                                    # doc 안의 online은 ds.async_set_offline에서 False로 바뀜
                                    if state.mqtt:
                                        state.mqtt.publish_online(retain=True)
                                    logger.warning(
                                        "[device] offline did=%s kind=%s last_seen=%s now=%s",
                                        did, kind, last_f, now
                                    )
                                    is_online = False  # 캐시 업데이트용
                            except Exception:
                                logger.exception("[device] set_offline failed did=%s", did)

                        # 2) offline -> online 전이: (persist는 mark_seen이 함) publish만
                        # - 여기서는 should_be_online이 True이고, json online도 True로 바뀐 경우를 잡는다
                        # - 외부에서 online을 올려주거나 mark_seen이 이미 올려둔 상태를 "감지"하는 용도
                        if (not prev_online) and is_online and should_be_online:
                            if state.mqtt:
                                try:
                                    state.mqtt.publish_online(retain=True)
                                    logger.info(
                                        "[device] online detected did=%s kind=%s last_seen=%s now=%s",
                                        did, kind, last_f, now
                                    )
                                except Exception:
                                    logger.exception("[device] publish_online failed did=%s", did)

                        state._dev_online_cache[did] = is_online

            await asyncio.sleep(interval_sec)

    except asyncio.CancelledError:
        logger.info("[device_offline_loop] cancelled")
        raise
    except Exception:
        logger.exception("[device_offline_loop] unexpected error")
        await asyncio.sleep(1.0)

async def maybe_cache_stt_on_wifi_up(state: MonitorState) -> None:
    """
    조건:
    - 부팅 시 캐시 없었음(state.stt_cache_missing=True)
    - 아직 시도 안 했음(state.stt_cache_attempted=False)
    - offline=0
    - wifi_active가 truthy(SSID 연결됨)
    동작:
    - 1회만 다운로드(캐싱) 시도
    - 성공하면 state.stt_engine 교체
    """
    if not getattr(state, "stt_cache_missing", False):
        return
    if getattr(state, "stt_cache_attempted", False):
        return
    if (os.getenv("EEUM_STT_OFFLINE", "0").strip() == "1"):
        return
    if not getattr(state, "wifi_active", None):
        return

    state.stt_cache_attempted = True  # 딱 1회만
    model_size = os.getenv("EEUM_STT_MODEL", "tiny")
    hf_root = (os.getenv("HF_HOME") or "").strip() or None

    try:
        logger.info("[STT] wifi up -> start caching once model=%s root=%s", model_size, hf_root)
        engine = FasterWhisperSTT(
            model_size=model_size,
            device="cpu",
            compute_type="int8",
            local_files_only=False,     # 여기서만 다운로드 허용(캐싱)
            download_root=hf_root,
            cpu_threads=2,
        )

        state.stt_engine = engine
        state.stt_cache_missing = False
        logger.info("[STT] caching done -> engine swapped")
    except Exception as e:
        logger.warning("[STT] caching failed (one-shot) err=%r", e)

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

async def refresh_wifi_scan(state: MonitorState) -> List[Dict[str, Any]]:
    # scan 캐시 갱신
    async with state.wifi_cache_lock:
        if state.wifi_busy:
            return state.wifi_scan
        aps = await async_scan_wifi_wlan0()
        state.wifi_scan = aps
        state.wifi_scan_ts = time.time()
        return aps

async def refresh_wifi_profiles(state: MonitorState) -> List[Dict[str, Any]]:
    # profiles 캐시 갱신
    async with state.wifi_cache_lock:
        if state.wifi_busy:
            return state.wifi_profiles
        profiles = await async_list_wifi_profiles_wlan0()
        state.wifi_profiles = [p.__dict__ for p in profiles]
        state.wifi_profiles_ts = time.time()
        return state.wifi_profiles

async def wifi_active_loop(state: MonitorState, interval_sec: float = 3.0) -> None:
    try:
        logger.info("[wifi_active_loop] started interval=%s", interval_sec)
        prev_active = None
        while True:
            if getattr(state, "shutting_down", False):
                return

            try:
                await refresh_wifi_active(state)
            except Exception:
                logger.exception("[wifi_active_loop] refresh failed")

            # wifi가 "새로" 붙는 순간(없던 SSID -> SSID) 1회 STT 캐싱 시도
            cur = state.wifi_active
            if (not prev_active) and cur:
                try:
                    await maybe_cache_stt_on_wifi_up(state)
                except Exception:
                    logger.exception("[STT] maybe_cache_stt_on_wifi_up failed")
            prev_active = cur

            # busy면 더 천천히, 아니면 기본 주기
            await asyncio.sleep(1.0 if state.wifi_busy else interval_sec)

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
    UI가 wifi 설정 화면을 보고 있을 때만 scan/profiles 갱신
    - scan: scan_interval_sec
    - profiles: profiles_interval_sec
    """
    last_profiles_ts = 0.0
    prev_ui_on = False
    try:
        logger.info("[wifi_scan_loop] started interval=%s", scan_interval_sec)
        while True:
            if getattr(state, "shutting_down", False):
                return
            now = time.time()
            ui_on = (now - state.wifi_ui_last_ping) < ui_recent_sec
            if ui_on and not prev_ui_on:
                logger.info("[wifi_ui] UI active (scan enabled)")
                # UI가 새로 켜짐: profiles 즉시 1회
                last_profiles_ts = 0.0
            elif not ui_on and prev_ui_on:
                logger.info("[wifi_ui] UI inactive (scan paused)")
                
            prev_ui_on = ui_on
            if ui_on and not state.wifi_busy:
                # scan은 매 tick
                await refresh_wifi_scan(state)

                # profiles는 더 긴 주기
                if (now - last_profiles_ts) >= profiles_interval_sec:
                    await refresh_wifi_profiles(state)
                    last_profiles_ts = now

            await asyncio.sleep(scan_interval_sec)
    except asyncio.CancelledError:
        logger.info("[wifi_scan_loop] cancelled")
        raise
    except Exception:
        logger.exception("[wifi_scan_loop] unexpected error")
        await asyncio.sleep(1.0)
        return