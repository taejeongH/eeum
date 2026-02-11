import asyncio
import logging
from app.album_sync import async_sync_album_once
from app.audio_manager import AudioJob, AudioPrio
from app.config import ALARM_TTS_DEBOUNCE_SEC
from app.monitor import refresh_wifi_active
from app.slideshow import rebuild_playlist, emit_slide, build_sender
from app.sync_utils import now_ts
from app.tts_service import get_default_tts_path
from app.voice_player import download_then_emit_new
from app.voice_sync import async_sync_voice_once

logger = logging.getLogger(__name__)

async def _wait_wifi_active(state, timeout_sec: float = 60.0, poll_sec: float = 1.0) -> bool:
    """
    Wi-Fi 활성화를 기다립니다.

    :param state: 전역 상태
    :param timeout_sec: 최대 대기 시간(초)
    :param poll_sec: 폴링 간격(초)
    :return: 활성화 성공 여부
    """
    deadline = now_ts() + float(timeout_sec)

    while now_ts() < deadline and (not getattr(state, "shutting_down", False)):
        try:
            await refresh_wifi_active(state)
        except Exception:
            pass

        if getattr(state, "wifi_active", False):
            return True

        await asyncio.sleep(float(poll_sec))

    return False

def _should_play_default_tts(state, kind: str) -> bool:
    now = now_ts()
    last_ts = state.alarm_last_tts_ts.get(kind, 0.0)
    debounce_sec = float(ALARM_TTS_DEBOUNCE_SEC or 0)
    return (now - last_ts) >= debounce_sec

async def _play_voice_default_tts_once(state, *, has_new_voice: bool, reason: str) -> None:
    if not has_new_voice:
        return

    kind = "voice"
    if not _should_play_default_tts(state, kind):
        return

    state.alarm_last_tts_ts[kind] = now_ts()
    path = get_default_tts_path(kind)
    if not path:
        logger.info("[voice_default] (%s) skip missing_default", reason)
        return

    await state.audio.enqueue(
        AudioJob(
            prio=int(AudioPrio.VOICE),
            kind="voice",
            path=path,
            ttl_sec=300.0,
            replace_key="voice.default.latest",
        )
    )
    logger.debug("[voice_default] (%s) play path=%s", reason, path)

async def _download_and_emit_new_voices(state, inserted_ids: list[int]) -> None:
    for vid in inserted_ids:
        v = state.voice_repo.get(int(vid)) if state.voice_repo else None
        if not v:
            continue

        url = str(v.get("url") or "")
        desc = str(v.get("description") or "")

        uid = v.get("user_id")
        sender = await build_sender(state, uid)
        await download_then_emit_new(state, int(vid), url, desc, sender)

async def initial_sync_worker(state, *, timeout_sec: float = 60.0) -> None:
    """
    부팅/토큰 설정 직후 수행하는 초기 동기화 워커입니다.

    순서:
    1) Wi-Fi 활성화 대기
    2) album/voice 단발 sync 수행
    3) 신규 voice가 있으면 다운로드 후 SSE emit
    4) album sync 성공 시 슬라이드 playlist rebuild + 현재 슬라이드 emit
    5) 신규 voice가 있으면 기본 TTS(디바운스 적용) 1회 재생

    :param state: 전역 상태(MonitorState)
    :param timeout_sec: Wi-Fi 활성 대기 최대 시간(초)
    :returns: None
    """
    try:
        ok_wifi = await _wait_wifi_active(state, timeout_sec=timeout_sec, poll_sec=1.0)
        if not ok_wifi:
            logger.warning("[initial_sync] wifi not active within timeout=%ss -> skip", timeout_sec)
            return

        res_album = await async_sync_album_once(state)
        res_voice = await async_sync_voice_once(state)
        inserted_ids = res_voice.get("inserted_ids") or []

        if inserted_ids:
            try:
                await _download_and_emit_new_voices(state, [int(x) for x in inserted_ids])
            except Exception:
                logger.exception("[initial_sync] voice download/emit failed (non-fatal)")

        if res_album.get("ok"):
            rebuild_playlist(state)
            try:
                await emit_slide(state, reason="sync_update")
            except Exception:
                logger.exception("[initial_sync] emit_slide failed")

        try:
            await _play_voice_default_tts_once(state, has_new_voice=bool(inserted_ids), reason="initial_sync")
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
    """
    초기 동기화를 1회 스케줄합니다(중복 실행 방지).
    - 이미 시작됐으면 False
    - token이 없으면 False
    - 스케줄 성공 시 task 생성 후 True

    :param state: 전역 상태(MonitorState)
    :param timeout_sec: Wi-Fi 활성 대기 최대 시간(초)
    :returns: 스케줄 성공 여부
    """ 
    if getattr(state, "initial_sync_started", False):
        return False

    ds = getattr(state, "device_store", None)
    token = ds.get_token() if ds else None
    if not token:
        return False

    state.initial_sync_started = True
    state.initial_sync_task = asyncio.create_task(initial_sync_worker(state, timeout_sec=timeout_sec))
    return True