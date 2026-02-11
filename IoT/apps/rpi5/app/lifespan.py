import asyncio
import logging
import os
import shutil
from contextlib import asynccontextmanager
from typing import Optional, Tuple
import aiohttp
from fastapi import FastAPI
from app.album_downloader import download_album_loop
from app.album_sync import load_album_cache_from_db
from app.audio_keepalive import audio_keepalive_loop
from app.config import (
    DB_PATH,
    DEFAULT_DEVICE,
    DEFAULT_TTS_MESSAGE,
    DEFAULT_TTS_PATH,
    DEVICE_PATH,
    OFFLINE_AFTER_SEC,
    OFFLINE_CHECK_INTERVAL_SEC,
    PROFILE_PATH,
    TOKEN_PATH,
    ALARM_TTS_DEBOUNCE_SEC,
)
from app.consumer import consume_commands, consume_events, consume_mqtt_inbound
from app.db import AlbumRepo, AppDB, MemberRepo, VoiceRepo
from app.device_store import DeviceStore
from app.json_store import JsonStateStore
from app.member_cache import load_member_cache_from_db
from app.monitor import (
    device_offline_loop,
    refresh_wifi_active,
    refresh_wifi_scan,
    wifi_active_loop,
    wifi_scan_loop,
)
from app.slideshow import init_slide_seq_from_db, rebuild_playlist, slideshow_timer_loop
from app.state import MonitorState
from app.stt_service import FasterWhisperSTT
from app.tts_service import _safe_slug, ensure_tts_mp3
from app.voice_player import voice_downloader_loop

logger = logging.getLogger(__name__)

def _cancel_task(task: Optional[asyncio.Task]) -> None:
    """
    태스크를 안전하게 cancel 합니다(이미 종료된 경우 무시).
    :param task: asyncio.Task 또는 None
    :return: None
    """
    if not task:
        return
    try:
        task.cancel()
    except Exception:
        pass

async def _try_put_none(queue: Optional[asyncio.Queue]) -> None:
    """
    큐에 None을 넣어 consumer 종료를 유도합니다.
    :param queue: asyncio.Queue 또는 None
    :return: None
    """
    if queue is None:
        return
    try:
        await queue.put(None)
    except Exception:
        pass

async def _try_close_async(obj) -> None:
    """
    close() 가능한 비동기 객체를 안전하게 close 합니다.
    :param obj: awaitable close()를 제공하는 객체
    :return: None
    """
    if not obj:
        return
    try:
        await obj.close()
    except Exception:
        pass

def _load_stt_engine_sync(model_size: str):
    hf_cache_root = (os.getenv("HF_HOME") or "").strip() or None
    return FasterWhisperSTT(
        model_size=model_size,
        device="cpu",
        compute_type="int8",
        local_files_only=True,
        download_root=hf_cache_root,
        cpu_threads=2,
    )

async def _preload_stt_engine(model_size: str):
    """
    STT 엔진을 로컬 캐시(local_files_only)로만 로딩합니다.
    :param model_size: tiny/base 등 모델 크기
    :return: 로딩 성공 시 엔진, 실패 시 None
    """
    try:
        engine = await asyncio.to_thread(_load_stt_engine_sync, model_size)
        logger.info("[STT] cache ready model=%s", model_size)
        return engine
    except Exception as e:
        logger.warning("[STT] cache missing model=%s err=%r", model_size, e)
        return None
    
async def _init_state(state: MonitorState) -> None:
    """
    FastAPI 요청 처리 전에 필요한 리소스(DB/세션/캐시)를 초기화합니다.
    :param state: MonitorState
    :return: None
    """
    os.makedirs(PROFILE_PATH or "./profile", exist_ok=True)

    download_concurrency = int(os.getenv("DL_CONCURRENCY", "3"))
    state.download_sem = asyncio.Semaphore(download_concurrency)

    http_connector = aiohttp.TCPConnector(limit=50, limit_per_host=10, ttl_dns_cache=300)
    state._http_connector = http_connector
    state.http_session = aiohttp.ClientSession(
        connector=http_connector,
        timeout=aiohttp.ClientTimeout(total=15),
        raise_for_status=False,
    )

    stt_model_size = os.getenv("EEUM_STT_MODEL", "base")
    state.stt_engine = await _preload_stt_engine(stt_model_size)
    state.stt_cache_missing = state.stt_engine is None
    state.stt_cache_attempted = False

    device_store = JsonStateStore(DEVICE_PATH, default=DEFAULT_DEVICE)
    token_store = JsonStateStore(TOKEN_PATH, default={"token": None})
    state.device_store = DeviceStore(device_store, token_store=token_store)

    db = AppDB(DB_PATH)
    db.open()
    db.init_schema()
    state.db = db
    state.album_repo = AlbumRepo(db.conn)
    state.voice_repo = VoiceRepo(db.conn)
    state.member_repo = MemberRepo(db.conn)

    init_slide_seq_from_db(state)
    load_member_cache_from_db(state)
    load_album_cache_from_db(state)
    rebuild_playlist(state)

    for message in DEFAULT_TTS_MESSAGE or []:
        result = await ensure_tts_mp3(message, DEFAULT_TTS_PATH, timeout_sec=5)
        if result.ok and result.path:
            slug = _safe_slug(message)
            legacy_path = os.path.join(DEFAULT_TTS_PATH, f"{slug}.mp3")
            if not os.path.exists(legacy_path):
                shutil.copyfile(result.path, legacy_path)

async def _start_core_tasks(
    state: MonitorState,
) -> Tuple[asyncio.Task, asyncio.Task, asyncio.Task, asyncio.Task, asyncio.Task]:
    """
    서버 실행과 동시에 항상 동작해야 하는 코어 태스크를 시작합니다.
    :param state: MonitorState
    :return: (audio_task, slide_timer_task, consumer_task, mqtt_in_task, cmd_task)
    """
    state.loop = asyncio.get_running_loop()

    audio_task = state.audio.start()
    slide_timer_task = asyncio.create_task(slideshow_timer_loop(state))
    consumer_task = asyncio.create_task(consume_events(state))
    mqtt_inbound_task = asyncio.create_task(consume_mqtt_inbound(state))
    command_task = asyncio.create_task(consume_commands(state, alarm_tts_debounce_sec=ALARM_TTS_DEBOUNCE_SEC))

    state.slide_timer_task = slide_timer_task
    return audio_task, slide_timer_task, consumer_task, mqtt_inbound_task, command_task

async def _ensure_master_volume() -> None:
    """
    부팅 초기에 마스터 볼륨을 100으로 맞춥니다.
    :return: None
    """
    try:
        await asyncio.sleep(0.2)
        from app.audio_utils import ensure_master_volume_100

        await ensure_master_volume_100()
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[BOOT] ensure_master_volume_100 failed (non-fatal)")

async def _bring_up_ap_with_retry() -> None:
    """
    AP 인터페이스를 올립니다. 실패 시 3회 재시도합니다.
    :return: None
    """
    try:
        await asyncio.sleep(0.2)
        from app.ap_manager import async_ap_up

        last_error = None
        for attempt in range(3):
            try:
                await async_ap_up()
                last_error = None
                break
            except asyncio.CancelledError:
                raise
            except Exception as e:
                last_error = e
                logger.warning("[AP] ap_up failed (try %d/3): %s", attempt + 1, e)
                await asyncio.sleep(1.0)

        if last_error is not None:
            logger.error("[AP] ap_up ultimately failed: %s", last_error)
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[AP] ap_up worker unexpected error")

async def _init_mqtt_if_token_exists(state: MonitorState) -> None:
    """
    token이 있을 때만 MQTT를 초기화하고 initial sync를 예약합니다.
    :param state: MonitorState
    :return: None
    """
    try:
        token = state.device_store.get_token() if state.device_store else None
        if not token:
            logger.info("[MQTT] disabled (token_present=False)")
            return

        from app.mqtt_client import MqttClient
        from app.sync_gate import schedule_initial_sync

        if state.mqtt is None:
            state.mqtt = MqttClient(
                inbound_queue=state.mqtt_inbound,
                loop=state.loop,
                token=token,
                link_getter=state.device_store.build_pir_link,
            )
        else:
            state.mqtt.set_token(token)

        state.mqtt.activate()
        scheduled = schedule_initial_sync(state, timeout_sec=60.0)
        logger.info("[BOOT] initial_sync scheduled=%s", scheduled)
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[MQTT] init failed (non-fatal)")

async def _init_wifi_cache(state: MonitorState) -> None:
    """
    Wi-Fi 상태/스캔 캐시를 초기화합니다.
    :param state: MonitorState
    :return: None
    """
    try:
        await asyncio.sleep(0.2)
        await refresh_wifi_active(state)
        await refresh_wifi_scan(state, force_rescan=False)
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[wifi] initial refresh failed (non-fatal)")

def _start_background_loops(state: MonitorState) -> None:
    """
    부팅 이후 지속 동작하는 백그라운드 루프들을 시작합니다.
    :param state: MonitorState
    :return: None
    """
    wifi_active_task = asyncio.create_task(wifi_active_loop(state, interval_sec=5.0))
    wifi_scan_task = asyncio.create_task(
        wifi_scan_loop(
            state,
            scan_interval_sec=10.0,
            profiles_interval_sec=30.0,
            ui_recent_sec=15.0,
        )
    )

    state.offline_after_sec = OFFLINE_AFTER_SEC
    offline_task = asyncio.create_task(
        device_offline_loop(state, interval_sec=OFFLINE_CHECK_INTERVAL_SEC)
    )

    album_download_task = asyncio.create_task(
        download_album_loop(state, interval_sec=3.0, batch_limit=5)
    )
    audio_keepalive_task = asyncio.create_task(audio_keepalive_loop(state))

    state.tasks["voice_downloader"] = asyncio.create_task(
        voice_downloader_loop(state, interval_sec=1.0, batch_limit=10)
    )

    state._bg_tasks = [
        wifi_active_task,
        wifi_scan_task,
        offline_task,
        album_download_task,
        audio_keepalive_task,
    ]

async def _delayed_start(state: MonitorState) -> None:
    """
    부팅 피크를 분산하기 위해 일부 작업을 지연 시작합니다.
    :param state: MonitorState
    :return: None
    """
    await _ensure_master_volume()
    await _bring_up_ap_with_retry()
    await _init_mqtt_if_token_exists(state)
    await _init_wifi_cache(state)
    _start_background_loops(state)

async def _shutdown(
    state: MonitorState,
    audio_task: asyncio.Task,
    delayed_task: asyncio.Task,
    consumer_task: asyncio.Task,
    mqtt_inbound_task: asyncio.Task,
    command_task: asyncio.Task,
    slide_timer_task: asyncio.Task,
) -> None:
    """
    lifespan 종료 시 태스크 취소/리소스 정리를 수행합니다.
    :param state: MonitorState
    :param audio_task: 오디오 태스크
    :param delayed_task: 지연 시작 태스크
    :param consumer_task: 이벤트 consumer 태스크
    :param mqtt_inbound_task: mqtt inbound consumer 태스크
    :param command_task: 커맨드 consumer 태스크
    :param slide_timer_task: 슬라이드 타이머 태스크
    :return: None
    """
    logger.info("[SHUTDOWN] begin")
    state.shutting_down = True

    cancel_requested = False

    async def _run_best_effort(awaitable):
        nonlocal cancel_requested
        try:
            return await awaitable
        except asyncio.CancelledError:
            cancel_requested = True
        except Exception:
            pass
        return None

    await _run_best_effort(_try_put_none(state.queue))
    await _run_best_effort(_try_put_none(state.mqtt_inbound))
    await _run_best_effort(_try_put_none(state.cmd_queue))

    if state.mqtt:
        try:
            state.mqtt.deactivate()
        except Exception:
            pass

    for task in (state._bg_tasks or []):
        _cancel_task(task)
    for task in (audio_task, delayed_task, consumer_task, mqtt_inbound_task, command_task, slide_timer_task):
        _cancel_task(task)

    registry_tasks = list((state.tasks or {}).values())
    for task in registry_tasks:
        _cancel_task(task)

    await _run_best_effort(
        asyncio.gather(
            audio_task,
            delayed_task,
            consumer_task,
            mqtt_inbound_task,
            command_task,
            slide_timer_task,
            *(state._bg_tasks or []),
            *registry_tasks,
            return_exceptions=True,
        )
    )

    state.tasks.clear()
    state._bg_tasks = []

    await _run_best_effort(state.audio.stop())
    await _run_best_effort(_try_close_async(state.http_session))
    await _run_best_effort(_try_close_async(state._http_connector))

    try:
        if state.db:
            state.db.close()
    except Exception:
        pass

    logger.info("[SHUTDOWN] done")

    if cancel_requested:
        raise asyncio.CancelledError

def build_lifespan(state: MonitorState):
    """
    FastAPI lifespan 함수를 생성합니다.
    :param state: MonitorState
    :return: lifespan asynccontextmanager
    """
    @asynccontextmanager
    async def lifespan(app: FastAPI):
        await _init_state(state)

        audio_task, slide_timer_task, consumer_task, mqtt_inbound_task, command_task = await _start_core_tasks(state)
        delayed_task = asyncio.create_task(_delayed_start(state))

        try:
            yield
        finally:
            await _shutdown(
                state,
                audio_task=audio_task,
                delayed_task=delayed_task,
                consumer_task=consumer_task,
                mqtt_inbound_task=mqtt_inbound_task,
                command_task=command_task,
                slide_timer_task=slide_timer_task,
            )

    return lifespan