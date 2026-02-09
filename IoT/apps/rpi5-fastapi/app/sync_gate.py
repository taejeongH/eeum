
import asyncio
import logging
import time

from .monitor import refresh_wifi_active
from .album_sync import async_sync_album_once
from .voice_sync import async_sync_voice_once
from .slideshow import rebuild_playlist, emit_slide
from .profile_cache import ensure_profile_cached
from .voice_player import download_then_emit_new
from .tts_service import get_default_tts_path
from .audio_manager import AudioJob, AudioPrio
from .config import ALARM_TTS_DEBOUNCE_SEC

logger = logging.getLogger(__name__)

async def _wait_wifi_active(state, timeout_sec: float = 60.0, poll_sec: float = 1.0) -> bool:
    """
    비차단 대기:
      - await/sleep 기반이라 다른 태스크(서버/MQTT/다운로더 등) 절대 안 막음
      - timeout 지나면 False
    """
    deadline = time.time() + float(timeout_sec)
    while time.time() < deadline and (not getattr(state, "shutting_down", False)):
        try:
            await refresh_wifi_active(state)
        except Exception:
            pass

        
        if state.wifi_active:
            return True

        await asyncio.sleep(float(poll_sec))
    return False

async def initial_sync_worker(state, *, timeout_sec: float = 60.0) -> None:
    """
    - wifi active 될 때까지 기다렸다가
    - album/voice sync 1회 실행
    - 끝나면 종료(태스크 종료)
    - 시작/중복 방지는 schedule_initial_sync에서 처리
    """
    try:
        ok_wifi = await _wait_wifi_active(state, timeout_sec=timeout_sec, poll_sec=1.0)
        if not ok_wifi:
            logger.warning("[initial_sync] wifi not active within timeout=%ss -> skip", timeout_sec)
            return

        
        res_album = await async_sync_album_once(state)
        res_voice = await async_sync_voice_once(state)

        
        try:
            inserted_ids = res_voice.get("inserted_ids") or []
            for vid in inserted_ids:
                v = state.voice_repo.get(int(vid)) if state.voice_repo else None
                if not v:
                    continue

                url = str(v.get("url") or "")
                desc = str(v.get("description") or "")

                uid = v.get("user_id")
                try:
                    uid = int(uid) if uid is not None else None
                except Exception:
                    uid = None

                name = ""
                profile_url = ""
                if uid is not None and getattr(state, "member_repo", None):
                    m = state.member_repo.get(uid) or {}
                    name = str(m.get("name") or "")
                    profile_url = str(m.get("profile_image_url") or "")

                if profile_url and getattr(state, "http_session", None) and (not state.http_session.closed):
                    profile_url = await ensure_profile_cached(
                        state.http_session, profile_url, timeout_sec=8.0
                    )

                sender = {"user_id": uid, "name": name, "profile_image_url": profile_url}
                await download_then_emit_new(state, int(vid), url, desc, sender)
        except Exception:
            logger.exception("[initial_sync] voice download/emit failed (non-fatal)")

        
        if res_album.get("ok"):
            rebuild_playlist(state)
            try:
                await emit_slide(state, reason="sync_update")
            except Exception:
                logger.exception("[initial_sync] emit_slide failed")

        
        try:
            
            inserted_ids = res_voice.get("inserted_ids") or []
            if inserted_ids:
                kind = "voice"
                now = time.time()
                last = state.alarm_last_tts_ts.get(kind, 0.0)
                if (now - last) >= float(ALARM_TTS_DEBOUNCE_SEC or 0):
                    state.alarm_last_tts_ts[kind] = now
                    path = get_default_tts_path(kind)
                    if path:
                        await state.audio.enqueue(AudioJob(
                            prio=int(AudioPrio.VOICE),
                            kind="voice",
                            path=path,
                            ttl_sec=300.0,
                            replace_key="voice.default.latest",
                        ))
                        logger.debug("[voice_default] (initial_sync) play path=%s", path)
                    else:
                        logger.info("[voice_default] (initial_sync) skip missing_default")
        except Exception:
            logger.exception("[initial_sync] voice default TTS failed (non-fatal)")

        logger.info("[initial_sync] done album=%s voice=%s", res_album, res_voice)

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[initial_sync] unexpected error")
    finally:
        
        state.initial_sync_done = True
        
        
        try:
            state.initial_sync_started = False
        except Exception:
            pass

def schedule_initial_sync(state, *, timeout_sec: float = 60.0) -> bool:
    if getattr(state, "initial_sync_started", False):
        return False

    
    ds = getattr(state, "device_store", None)
    token = ds.get_token() if ds else None
    if not token:
        return False

    state.initial_sync_started = True
    state.initial_sync_task = asyncio.create_task(
        initial_sync_worker(state, timeout_sec=timeout_sec)
    )
    return True