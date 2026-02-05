import asyncio
import uvicorn
import os
import logging
import aiohttp
import wave
import struct
from app.config import AP_IFACE, HOST, PORT, DEVICE_PATH, DEFAULT_DEVICE, STA_IFACE, TOKEN_PATH, DB_PATH, DEFAULT_TTS_PATH, DEFAULT_TTS_MESSAGE, OFFLINE_AFTER_SEC, OFFLINE_CHECK_INTERVAL_SEC, PROFILE_PATH
from app.ap_manager import async_ap_up, async_get_ipv4_addr
from app.state import MonitorState
from app.api import create_app
from app.consumer import consume_events, consume_mqtt_inbound, consume_commands
from app.monitor import (
    wifi_active_loop,
    wifi_scan_loop,
    refresh_wifi_active,
    refresh_wifi_scan,
    device_offline_loop,
)
from app.json_store import JsonStateStore
from app.device_store import DeviceStore
from app.mqtt_client import MqttClient
from app.db import AppDB, AlbumRepo, VoiceRepo, MemberRepo   
from app.album_sync import async_sync_album_once, load_album_cache_from_db
from app.voice_sync import async_sync_voice_once
from app.slideshow import rebuild_playlist, slideshow_timer_loop
from app.voice_player import play_voice_loop
from app.album_downloader import download_album_loop
from app.tts_service import ensure_tts_mp3
from app.stt_service import FasterWhisperSTT
from app.audio_utils import ensure_master_volume_100
from app.member_cache import load_member_cache_from_db
from app.sync_gate import schedule_initial_sync

def _make_silence_wav(path: str, sec: float = 0.6, sr: int = 16000):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    n = int(sec * sr)
    with wave.open(path, "wb") as wf:
        wf.setnchannels(1)
        wf.setsampwidth(2)
        wf.setframerate(sr)
        wf.writeframes(struct.pack("<" + "h" * n, *([0] * n)))

async def preload_stt_engine(model_size: str):
    """
    요구사항:
    - env 기반(download_root=HF_HOME) 모델 캐싱
    - 부팅시 최초는 로컬 캐시 시도
    - cpu_threads=2로 속도 보장
    """
    offline = (os.getenv("EEUM_STT_OFFLINE", "0").strip() == "1")

    hf_root = (os.getenv("HF_HOME") or "").strip() or None
    cpu_threads = 2

    # 부팅 시점: 다운로드 절대 안 함. 로컬 캐시로만 로딩 시도.
    try:
        engine = FasterWhisperSTT(
            model_size=model_size,
            device="cpu",
            compute_type="int8",
            local_files_only=True,
            download_root=hf_root,
            cpu_threads=cpu_threads,
        )
        logger.info("[STT] cache ready (boot local-only) model=%s root=%s", model_size, hf_root)
        return engine
    except Exception as e:
        logger.warning("[STT] cache missing at boot (no download yet) model=%s err=%r", model_size, e)
        return None
def setup_logging():
    level = os.getenv("LOG_LEVEL", "INFO").upper()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

logger = logging.getLogger(__name__)

async def async_main():
    setup_logging()

    logger.info("[BOOT] start host=%s port=%s ap=%s sta=%s", HOST, PORT, AP_IFACE, STA_IFACE)
    os.makedirs(PROFILE_PATH or "./profile", exist_ok=True)
    await ensure_master_volume_100()
    last_err = None
    for i in range(3):
        try:
            await async_ap_up()
            last_err = None
            break
        except Exception as e:
            last_err = e
            logger.warning("[AP] ap_up failed (try %d/3): %s", i+1, e)
            await asyncio.sleep(1.0)
    
    if last_err is not None:
        logger.error("[AP] ap_up ultimately failed: %s", last_err)
    
    await asyncio.sleep(1.0)
    ap_ip = await async_get_ipv4_addr(AP_IFACE)
    logger.info("[AP] iface=%s, ip=%s", AP_IFACE, ap_ip)
    
    state = MonitorState()

    # ---- shared aiohttp session ----
    # 커넥션 풀/keep-alive 재사용 + 동시 다운로드 제한 가능
    connector = aiohttp.TCPConnector(
        limit=50,              # 전체 동시 연결 수 (환경에 맞게)
        limit_per_host=10,     # 호스트당 동시 연결
        ttl_dns_cache=300,     # DNS 캐시
        enable_cleanup_closed=True,
    )
    # 세션 기본 timeout(요청에서 override 가능)
    timeout = aiohttp.ClientTimeout(total=15.0)
    state.http_session = aiohttp.ClientSession(
        connector=connector,
        timeout=timeout,
        raise_for_status=False,    # 여기서는 요청마다 처리(로그 남기고 raise)
    )

    # tiny/base 선택
    STT_MODEL = os.getenv("EEUM_STT_MODEL", "tiny")  # tiny or base
    state.stt_engine = await preload_stt_engine(STT_MODEL)
    # 캐시 없으면, Wi-Fi 붙을 때 1회 캐싱 시도하도록 플래그 세팅
    state.stt_cache_missing = (state.stt_engine is None)
    state.stt_cache_attempted = False
    logger.info("[STT] engine ready model=%s offline=%s", STT_MODEL, os.getenv("EEUM_STT_OFFLINE", "0"))

    raw_store = JsonStateStore(DEVICE_PATH, default=DEFAULT_DEVICE)
    token_store = JsonStateStore(TOKEN_PATH, default={"token": None})
    state.device_store = DeviceStore(raw_store, token_store=token_store)
    state.loop = asyncio.get_running_loop()
    db = AppDB(DB_PATH)
    db.open()
    db.init_schema()

    state.db = db
    state.album_repo = AlbumRepo(db.conn)
    state.voice_repo = VoiceRepo(db.conn)
    state.member_repo = MemberRepo(db.conn)

    mn = load_member_cache_from_db(state)
    logger.info("[BOOT] member cache loaded from db count=%d", mn)

    n = load_album_cache_from_db(state)
    logger.info("[BOOT] album cache loaded from db count=%d", n)    

    # timer loop 시작(항상 돌아도 playing 플래그로 제어)
    state.slide_timer_task = asyncio.create_task(slideshow_timer_loop(state))

    app = create_app(state)
    level = os.getenv("LOG_LEVEL", "INFO").lower()
    config = uvicorn.Config(
        app,
        host=HOST,
        port=PORT,
        log_level=level,
        access_log=False,   # 필요하면 True
    )
    server = uvicorn.Server(config)
    
    token = state.device_store.get_token()
    if token:
        logger.info("[MQTT] enabled (token_present=True)")
        state.mqtt = MqttClient(
            inbound_queue=state.mqtt_inbound,
            loop=state.loop,
            token=token,
            link_getter=state.device_store.build_pir_link,  # pir만 반영
        )
        state.mqtt.activate()

        scheduled = schedule_initial_sync(state, timeout_sec=60.0)
        logger.info("[BOOT] initial_sync scheduled=%s", scheduled)
    else:
        logger.info("[MQTT] disabled (token_present=False)")
        state.mqtt = None
    rebuild_playlist(state)
    logger.info(
        "[BOOT] album cache=%d playlist=%d",
        len(state.album_cache),
        len(state.slide_playlist),
    )
    # DEFAULT 문구 캐시 준비(없으면 생성 시도)
    for msg in (DEFAULT_TTS_MESSAGE or []):
        try:
            await ensure_tts_mp3(msg, DEFAULT_TTS_PATH, timeout_sec=5.0)
        except Exception:
            logger.debug("[TTS] default cache failed msg=%r", msg, exc_info=True)
    consumer_task = asyncio.create_task(consume_events(state))
    mqtt_in_task = asyncio.create_task(consume_mqtt_inbound(state))
    cmd_task = asyncio.create_task(consume_commands(state))
    voice_task = asyncio.create_task(play_voice_loop(state))
    album_dl_task = asyncio.create_task(download_album_loop(state, interval_sec=1.0, batch_limit=10))
    audio_task = state.audio.start()
    await refresh_wifi_active(state)
    await refresh_wifi_scan(state)
    # Wi-Fi cache loops
    wifi_active_task = asyncio.create_task(wifi_active_loop(state, interval_sec=3.0))
    wifi_scan_task = asyncio.create_task(wifi_scan_loop(state, scan_interval_sec=5.0, profiles_interval_sec=15.0, ui_recent_sec=15.0))
    state.offline_after_sec = OFFLINE_AFTER_SEC
    device_offline_task = asyncio.create_task(
        device_offline_loop(state, interval_sec=OFFLINE_CHECK_INTERVAL_SEC)
    )
    try:
        logger.info("[HTTP] server starting")
        await server.serve()
    finally:
        logger.info("[SHUTDOWN] begin")
        state.shutting_down = True

        # shared session close
        try:
            if state.http_session:
                await state.http_session.close()
        except Exception:
            pass
        
        if state.mqtt:
            state.mqtt.deactivate()
        # DB close
        try:
            if state.db:
                state.db.close()
        except Exception:
            pass
        
        await state.queue.put(None)
        await state.mqtt_inbound.put(None)
        await state.cmd_queue.put(None)
        
        # 오디오 매니저: cancel 전에 stop()로 subprocess 포함 정리 시도
        try:
            await state.audio.stop()
        except Exception:
            pass
        for t in (state.slide_timer_task, audio_task, consumer_task, mqtt_in_task, cmd_task, voice_task, album_dl_task, wifi_active_task, wifi_scan_task, device_offline_task):
            t.cancel()
        await asyncio.gather(
            state.slide_timer_task, audio_task, consumer_task, mqtt_in_task, cmd_task, voice_task, album_dl_task, wifi_active_task, wifi_scan_task, device_offline_task,
            return_exceptions=True
        )
        tasks = list(state.tasks.values())
        for t in tasks:
            t.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        state.tasks.clear()
        logger.info("[SHUTDOWN] done")

def main():
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        pass
    except Exception:
        logger.exception("[FATAL] unexpected error")
        raise

if __name__ == "__main__":
    main()
