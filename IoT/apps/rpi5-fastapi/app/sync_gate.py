# app/sync_gate.py
import asyncio
import logging
import time

from .monitor import refresh_wifi_active
from .album_sync import async_sync_album_once
from .voice_sync import async_sync_voice_once
from .slideshow import rebuild_playlist, emit_slide

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

        # state.wifi_active: SSID 문자열(연결되면 truthy)
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

        # wifi OK -> sync 1회
        res_album = await async_sync_album_once(state)
        res_voice = await async_sync_voice_once(state)

        # album sync 성공이면 playlist 갱신 + 현재 슬라이드 emit (원래 부팅/토큰 로직과 동일 의도)
        if res_album.get("ok"):
            rebuild_playlist(state)
            try:
                await emit_slide(state, reason="sync_update")
            except Exception:
                logger.exception("[initial_sync] emit_slide failed")

        logger.info("[initial_sync] done album=%s voice=%s", res_album, res_voice)

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[initial_sync] unexpected error")
    finally:
        # 1회 작업 종료 표시
        state.initial_sync_done = True

def schedule_initial_sync(state, *, timeout_sec: float = 60.0) -> bool:
    if getattr(state, "initial_sync_started", False):
        return False

    # 토큰 없으면 시작 자체를 하지 않음
    ds = getattr(state, "device_store", None)
    token = ds.get_token() if ds else None
    if not token:
        return False

    state.initial_sync_started = True
    state.initial_sync_task = asyncio.create_task(
        initial_sync_worker(state, timeout_sec=timeout_sec)
    )
    return True