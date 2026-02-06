import asyncio
import uvicorn
import os
import logging
import aiohttp
import shutil
from app.config import AP_IFACE, HOST, PORT, DEVICE_PATH, DEFAULT_DEVICE, STA_IFACE, TOKEN_PATH, DB_PATH, DEFAULT_TTS_PATH, DEFAULT_TTS_MESSAGE, OFFLINE_AFTER_SEC, OFFLINE_CHECK_INTERVAL_SEC, PROFILE_PATH
from app.state import MonitorState
from app.api import create_app
from app.json_store import JsonStateStore
from app.device_store import DeviceStore
from app.db import AppDB, AlbumRepo, VoiceRepo, MemberRepo   
from app.album_sync import load_album_cache_from_db
from app.slideshow import rebuild_playlist
from app.tts_service import ensure_tts_mp3, _safe_slug
from app.stt_service import FasterWhisperSTT
from app.member_cache import load_member_cache_from_db
from app.voice_player import voice_downloader_loop

async def preload_stt_engine(model_size: str):
    """
    요구사항:
    - env 기반(download_root=HF_HOME) 모델 캐싱
    - 부팅시 최초는 로컬 캐시 시도
    - cpu_threads=2로 속도 보장
    """

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

    state = MonitorState()

    # ---- download concurrency gate ----
    dl_conc = int(os.getenv("DL_CONCURRENCY", "3"))
    state.download_sem = asyncio.Semaphore(dl_conc)
    logger.info("[BOOT] download concurrency=%s", dl_conc)

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
    
    rebuild_playlist(state)
    logger.info(
        "[BOOT] album cache=%d playlist=%d",
        len(state.album_cache),
        len(state.slide_playlist),
    )
    # DEFAULT 문구 캐시 준비(없으면 생성 시도)
    for msg in (DEFAULT_TTS_MESSAGE or []):
        r = await ensure_tts_mp3(msg, DEFAULT_TTS_PATH, timeout_sec=5.0)
        if r.ok and r.path:
            slug = _safe_slug(msg)
            legacy = os.path.join(DEFAULT_TTS_PATH, f"{slug}.mp3")
            if not os.path.exists(legacy):
                shutil.copyfile(r.path, legacy)
    state.tasks["voice_downloader"] = asyncio.create_task(voice_downloader_loop(state, interval_sec=1.0, batch_limit=10))
    try:
        logger.info("[HTTP] server starting")
        await server.serve()
    finally:
        # ---- best-effort cleanup (covers: crash before lifespan shutdown) ----
        # 1) background tasks spawned in main.py (state.tasks)
        try:
            tasks = list((getattr(state, "tasks", {}) or {}).values())
            # cancel pending tasks
            for t in tasks:
                try:
                    if t and (not t.done()):
                        t.cancel()
                except Exception:
                    pass
            if tasks:
                await asyncio.gather(*tasks, return_exceptions=True)
            # clear task registry
            try:
                state.tasks.clear()
            except Exception:
                pass
        except Exception:
            logger.debug("[CLEANUP] state.tasks cancel failed (ignore)", exc_info=True)

        # 2) stop audio manager if running (best-effort)
        try:
            if getattr(state, "audio", None):
                await state.audio.stop()
        except Exception:
            logger.debug("[CLEANUP] audio stop failed (ignore)", exc_info=True)

        # 3) shared aiohttp session
        try:
            if getattr(state, "http_session", None) and (not state.http_session.closed):
                await state.http_session.close()
        except Exception:
            logger.debug("[CLEANUP] http_session close failed (ignore)", exc_info=True)

        # 4) connector close (may still exist even if session failed mid-init)
        try:
            if connector is not None and (not connector.closed):
                await connector.close()
        except Exception:
            logger.debug("[CLEANUP] connector close failed (ignore)", exc_info=True)

        # 5) DB close
        try:
            if getattr(state, "db", None):
                state.db.close()
        except Exception:
            logger.debug("[CLEANUP] db close failed (ignore)", exc_info=True)

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
