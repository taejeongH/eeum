# app/voice_player.py
import asyncio
import os
import aiohttp
import time
import logging
import hashlib
from .config import VOICE_PATH
from .voice_duration import get_mp3_duration_sec
from .sse_fanout import fanout_nowait
from typing import Optional

logger = logging.getLogger(__name__)

VOICE_DIR = VOICE_PATH or "./voice"
os.makedirs(VOICE_DIR, exist_ok=True)

def voice_path(vid: int) -> str:
    return os.path.join(VOICE_DIR, f"{int(vid)}.mp3")

def _calc_backoff_sec(retry_count: int) -> float:
    try:
        rc = max(0, int(retry_count))
    except Exception:
        rc = 0
    return min(300.0, float(2 ** rc))

async def _emit_event(state, event_name: str, data: dict):
    """
    voice_stream에서 event/data를 그대로 내보내기 위해
    큐에 {"_event": "...", "data": {...}} 형태로 넣는다.
    """
    envelope = {"_event": event_name, "data": data}
    delivered = fanout_nowait(state.voice_subscribers, envelope)
    # 구독자 없으면 delivered=0 (즉 "보내긴 했는데 받을 사람이 없음"을 로그로 확정)
    try:
        logger.debug("[sse_voice] event=%s delivered=%d", event_name, int(delivered))
    except Exception:
        pass

async def emit_voice_new(state, vid: int, desc: str, sender: dict | None = None):
    payload = {
        "id": int(vid),
        "description": str(desc or ""),
        "ts": float(time.time()),
    }
    if sender:
        payload["sender"] = sender
    await _emit_event(state, "voice", payload)

async def emit_voice_done(state, vid: int, result: str):
    # result: "done" | "skipped"
    await _emit_event(state, "voice_done", {
        "id": int(vid),
        "result": str(result),
        "ts": float(time.time()),
    })

async def download_voice_to_local(state, vid: int, url: str) -> str:
    # ---- vid 단위 직렬화 락: downloader_loop vs consumer(update_voice) 경쟁 방지 ----
    lock: Optional[asyncio.Lock] = None
    try:
        lock = (getattr(state, "voice_dl_locks", None) or {}).get(int(vid))
        if lock is None:
            lock = asyncio.Lock()
            if getattr(state, "voice_dl_locks", None) is None:
                state.voice_dl_locks = {}
            state.voice_dl_locks[int(vid)] = lock
    except Exception:
        lock = None

    async def _inner() -> str:
        path = voice_path(vid)
        if os.path.exists(path) and os.path.getsize(path) > 0:
            # 이미 있으면 done 마크(일관성)
            try:
                if state.voice_repo:
                    state.voice_repo.set_download_status(
                        int(vid), "done",
                        local_path=path,
                        last_error=None,
                        inc_retry=False,
                        next_try_at=0.0
                    )
            except Exception:
                pass
            return path

        s = getattr(state, "http_session", None)
        if s is None or s.closed:
            raise RuntimeError("http_session missing/closed")

        repo = getattr(state, "voice_repo", None)
        sem = getattr(state, "download_sem", None)
        tmp = path + ".tmp"

        # 시작 마크
        if repo:
            try:
                repo.set_download_status(
                    int(vid), "downloading",
                    local_path=path,
                    last_error=None,
                    inc_retry=False,
                    next_try_at=None
                )
            except Exception:
                pass

        async def _do() -> str:
            try:
                async with s.get(url, timeout=aiohttp.ClientTimeout(total=20.0)) as r:
                    r.raise_for_status()
                    total = 0
                    h = hashlib.sha256()
                    with open(tmp, "wb") as f:
                        async for chunk in r.content.iter_chunked(64 * 1024):
                            if not chunk:
                                continue
                            f.write(chunk)
                            total += len(chunk)
                            h.update(chunk)
                os.replace(tmp, path)

                # 성공 마크
                if repo:
                    try:
                        repo.set_download_status(
                            int(vid), "done",
                            local_path=path,
                            bytes_=int(total),
                            sha256=h.hexdigest(),
                            last_error=None,
                            inc_retry=False,
                            next_try_at=0.0,
                        )
                    except Exception:
                        pass

                logger.info("[voice] downloaded id=%s bytes=%d path=%s", int(vid), total, path)
                return path

            except Exception as e:
                try:
                    if os.path.exists(tmp):
                        os.remove(tmp)
                except Exception:
                    pass

                # 실패 마크(백오프)
                if repo:
                    try:
                        d = repo.get_download(int(vid)) if hasattr(repo, "get_download") else None
                        rc = (d or {}).get("retry_count", 0)
                        backoff = _calc_backoff_sec(int(rc or 0))
                    except Exception:
                        backoff = 2.0
                    try:
                        next_try = time.time() + float(backoff)
                        repo.set_download_status(
                            int(vid), "failed",
                            local_path=path,
                            last_error=str(e),
                            inc_retry=True,
                            next_try_at=next_try,
                        )
                    except Exception:
                        pass
                raise

        # 다운로드 동시성 제한 (album과 공유)
        if sem:
            async with sem:
                return await _do()
        return await _do()

    if lock:
        async with lock:
            return await _inner()
    return await _inner()


async def download_then_emit_new(state, vid: int, url: str, desc: str, sender: dict | None = None):
    """
    1) 다운로드
    2) (가능하면) duration 사전 계산 로그만 (ACK때 다시 계산해도 됨)
    3) SSE voice 이벤트 emit
    """
    path = await download_voice_to_local(state, vid, url)
    # ffprobe(duration)는 오디오 재생/ STT 중에는 경합이 심하니 쉬기
    try:
        audio_busy = bool(getattr(state, "audio", None) and getattr(state.audio, "is_playing", False))
        stt_busy = bool(getattr(state, "stt_busy", False))
        paused = bool(getattr(state, "heavy_ops_pause", False))
        if (not audio_busy) and (not stt_busy) and (not paused):
            dur = await get_mp3_duration_sec(path)
            logger.info("[voice] downloaded id=%s dur=%.2fs", vid, dur)
            # ffprobe는 무거우므로, ACK에서 재호출하지 않게 캐시
            try:
                state.voice_duration_cache[int(vid)] = float(dur)
            except Exception:
                pass
        else:
            logger.info("[voice] downloaded id=%s (dur skipped busy audio=%s stt=%s paused=%s)", vid, audio_busy, stt_busy, paused)
    except Exception:
        logger.info("[voice] downloaded id=%s (dur unknown)", vid)
    await emit_voice_new(state, vid, desc, sender=sender)

async def download_only(state, vid: int, url: str) -> str:
    """
    다운로드만 수행 (SSE emit 없음)
    - 중복 알림 방지: emit은 consumer(update_voice)에서만 담당
    """
    return await download_voice_to_local(state, vid, url)

async def voice_downloader_loop(state, interval_sec: float = 1.0, batch_limit: int = 10):
    """
    - voice_downloads 기반으로 pending/failed(+stuck downloading) 다운로드
    - DL_CONCURRENCY 세마포어로 동시 다운로드 제한
    """
    repo = getattr(state, "voice_repo", None)
    if repo is None:
        logger.warning("[voice_dl] voice_repo missing -> loop disabled")
        return

    while True:
        if getattr(state, "shutting_down", False):
            return

        await asyncio.sleep(float(interval_sec))

        # 오디오 재생 중 / STT 중 / heavy_ops_pause면 다운로드로 I/O 경쟁하지 않게 쉬기
        try:
            audio_busy = bool(getattr(state, "audio", None) and getattr(state.audio, "is_playing", False))
        except Exception:
            audio_busy = False
        stt_busy = bool(getattr(state, "stt_busy", False))
        paused = bool(getattr(state, "heavy_ops_pause", False))
        wifi_busy = bool(getattr(state, "wifi_busy", False))
        if audio_busy or stt_busy or paused or wifi_busy:
            continue


        s = getattr(state, "http_session", None)
        if s is None or s.closed:
            continue

        try:
            items = repo.list_pending_downloads(limit=batch_limit, now=time.time())
            if not items:
                continue

            async def _handle_one(it: dict):
                vid = int(it["id"])
                url = str(it["url"])
                # 여기서는 다운로드만 수행
                # SSE emit(voice)은 consumer._handle_update_voice()에서만 1회 수행해야 중복이 안 생김.
                await download_only(state, vid, url)

            tasks = [asyncio.create_task(_handle_one(it)) for it in items]
            await asyncio.gather(*tasks, return_exceptions=True)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[voice_dl] unexpected error")