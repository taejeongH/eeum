import asyncio
import hashlib
import logging
import os
from typing import Optional, Any
import aiohttp
from app.config import VOICE_PATH
from app.sse_fanout import fanout_nowait
from app.sync_utils import calc_backoff_sec, now_ts
from app.voice_duration import ffprobe_duration_sec, verify_mp3_quick

logger = logging.getLogger(__name__)

VOICE_DIR = VOICE_PATH or "./voice"
os.makedirs(VOICE_DIR, exist_ok=True)


def voice_path(vid: int) -> str:
    """
    voice id에 대응하는 로컬 mp3 경로를 반환합니다.

    :param vid: voice id
    :returns: 로컬 파일 경로
    """
    return os.path.join(VOICE_DIR, f"{int(vid)}.mp3")


def _get_or_create_voice_lock(state, vid: int) -> Optional[asyncio.Lock]:
    """
    vid 단위 다운로드 직렬화를 위한 락을 조회/생성합니다.

    :param state: MonitorState (voice_dl_locks 보유)
    :param vid: voice id
    :returns: asyncio.Lock 또는 None
    """
    try:
        if getattr(state, "voice_dl_locks", None) is None:
            state.voice_dl_locks = {}
        lock = state.voice_dl_locks.get(int(vid))
        if lock is None:
            lock = asyncio.Lock()
            state.voice_dl_locks[int(vid)] = lock
        return lock
    except Exception:
        return None


async def _fanout_voice_event(state, event_name: str, data: dict) -> None:
    """
    voice SSE 이벤트를 subscriber들에게 fanout합니다.

    :param state: MonitorState (voice_subscribers 보유)
    :param event_name: 이벤트명("voice", "voice_done" 등)
    :param data: payload dict
    :returns: None
    """
    envelope = {"_event": event_name, "data": data}
    delivered = fanout_nowait(state.voice_subscribers, envelope)
    logger.debug("[sse_voice] event=%s delivered=%d", event_name, int(delivered))


async def emit_voice_new(state, vid: int, desc: str, sender: dict | None = None) -> None:
    """
    새 voice 수신 이벤트를 emit 합니다.

    :param state: MonitorState
    :param vid: voice id
    :param desc: 설명/메시지
    :param sender: 발신자 정보(optional)
    :returns: None
    """
    payload = {"id": int(vid), "description": str(desc or ""), "ts": float(now_ts())}
    if sender:
        payload["sender"] = sender
    await _fanout_voice_event(state, "voice", payload)


async def emit_voice_done(state, vid: int, result: str) -> None:
    """
    voice 재생/처리 완료 이벤트를 emit 합니다.

    :param state: MonitorState
    :param vid: voice id
    :param result: 결과 문자열(예: "ok", "skipped", "error" 등)
    :returns: None
    """
    await _fanout_voice_event(
        state,
        "voice_done",
        {"id": int(vid), "result": str(result), "ts": float(now_ts())},
    )


def _mark_voice_done_if_file_exists(state, vid: int, path: str) -> None:
    """로컬 파일이 이미 있을 때 DB download_status를 done으로 best-effort 갱신합니다."""
    if not state.voice_repo:
        return
    try:
        state.voice_repo.set_download_status(
            int(vid),
            "done",
            local_path=path,
            last_error=None,
            inc_retry=False,
            next_try_at=0.0,
        )
    except Exception:
        pass


def _mark_voice_downloading(repo, vid: int, path: str) -> None:
    """다운로드 시작 상태로 best-effort 갱신합니다."""
    if not repo:
        return
    try:
        repo.set_download_status(
            int(vid),
            "downloading",
            local_path=path,
            last_error=None,
            inc_retry=False,
            next_try_at=None,
        )
    except Exception:
        pass


def _mark_voice_failed(repo, vid: int, path: str, err: Exception, retry_count: Any = None) -> None:
    """다운로드 실패 상태로 best-effort 갱신하고 backoff 기반 next_try_at을 설정합니다."""
    if not repo:
        return
    try:
        backoff = calc_backoff_sec(retry_count)
        repo.set_download_status(
            int(vid),
            "failed",
            local_path=path,
            last_error=str(err),
            inc_retry=True,
            next_try_at=now_ts() + float(backoff),
        )
    except Exception:
        pass


def _safe_remove(path: str) -> None:
    """파일이 존재하면 제거합니다."""
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass


async def _download_voice_file(session: aiohttp.ClientSession, url: str, tmp: str) -> tuple[int, str]:
    """
    URL에서 파일을 내려받아 tmp에 저장하고 sha256을 계산합니다.

    :param session: aiohttp ClientSession
    :param url: 다운로드 URL
    :param tmp: 임시 파일 경로
    :returns: (다운로드 바이트 수, sha256 hex)
    """
    async with session.get(url, timeout=aiohttp.ClientTimeout(total=20.0)) as r:
        r.raise_for_status()
        total = 0
        sha = hashlib.sha256()
        with open(tmp, "wb") as f:
            async for chunk in r.content.iter_chunked(64 * 1024):
                if not chunk:
                    continue
                f.write(chunk)
                total += len(chunk)
                sha.update(chunk)
    return total, sha.hexdigest()


async def download_voice_to_local(state, vid: int, url: str) -> str:
    """
    voice 파일을 로컬로 다운로드합니다.
    - vid 단위 락으로 downloader_loop vs 다른 consumer 경쟁을 방지합니다.
    - 이미 파일이 있으면 다운로드를 건너뛰고 상태만 보정합니다.

    :param state: MonitorState
    :param vid: voice id
    :param url: 다운로드 URL
    :returns: 로컬 파일 경로
    :raises RuntimeError: http_session이 없거나 closed인 경우
    """
    lock = _get_or_create_voice_lock(state, vid)

    async def _inner() -> str:
        path = voice_path(vid)
        if os.path.exists(path) and os.path.getsize(path) > 0:
            _mark_voice_done_if_file_exists(state, vid, path)
            return path

        session = getattr(state, "http_session", None)
        if session is None or session.closed:
            raise RuntimeError("http_session missing/closed")

        repo = getattr(state, "voice_repo", None)
        sem = getattr(state, "download_sem", None)
        tmp = path + ".tmp"

        _mark_voice_downloading(repo, vid, path)

        async def _run() -> str:
            retry_count = None
            try:
                if repo and hasattr(repo, "get_download"):
                    d = repo.get_download(int(vid))
                    retry_count = (d or {}).get("retry_count")
            except Exception:
                retry_count = None

            try:
                total, sha256_hex = await _download_voice_file(session, url, tmp)
                os.replace(tmp, path)

                duration = await verify_mp3_quick(path, timeout_sec=1.2, min_dur_sec=0.05)
                try:
                    state.voice_duration_cache[int(vid)] = float(duration)
                except Exception:
                    pass

                if repo:
                    try:
                        repo.set_download_status(
                            int(vid),
                            "done",
                            local_path=path,
                            bytes_=int(total),
                            sha256=sha256_hex,
                            last_error=None,
                            inc_retry=False,
                            next_try_at=0.0,
                        )
                    except Exception:
                        pass

                logger.info("[voice] downloaded id=%s bytes=%d path=%s", int(vid), total, path)
                return path

            except Exception as e:
                _safe_remove(tmp)
                _safe_remove(path)
                _mark_voice_failed(repo, vid, path, e, retry_count=retry_count)
                raise

        if sem is None:
            return await _run()

        async with sem:
            return await _run()

    if lock is None:
        return await _inner()

    async with lock:
        return await _inner()


def _should_probe_duration_after_download(state) -> bool:
    """
    다운로드 직후 duration probe(ffprobe)를 해도 되는지 판단합니다.
    (재생/무거운 작업 중이면 I/O 경쟁을 피하기 위해 스킵)

    :param state: MonitorState
    :returns: probe 가능 여부
    """
    audio_busy = bool(getattr(state, "audio", None) and getattr(state.audio, "is_playing", False))
    stt_busy = bool(getattr(state, "stt_busy", False))
    paused = bool(getattr(state, "heavy_ops_pause", False))
    return (not audio_busy) and (not stt_busy) and (not paused)


async def download_then_emit_new(state, vid: int, url: str, desc: str, sender: dict | None = None) -> None:
    """
    voice를 다운로드하고(필요 시) 새 voice 이벤트를 emit 합니다.
    - 상황이 허용되면 ffprobe로 duration을 갱신합니다.

    :param state: MonitorState
    :param vid: voice id
    :param url: 다운로드 URL
    :param desc: 설명/메시지
    :param sender: 발신자 정보(optional)
    :returns: None
    """
    path = await download_voice_to_local(state, vid, url)

    try:
        if _should_probe_duration_after_download(state):
            dur = await ffprobe_duration_sec(path, timeout_sec=3.0)
            logger.info("[voice] downloaded id=%s dur=%.2fs", vid, dur)
            try:
                state.voice_duration_cache[int(vid)] = float(dur)
            except Exception:
                pass
        else:
            logger.info("[voice] downloaded id=%s (dur skipped busy)", vid)
    except Exception:
        logger.info("[voice] downloaded id=%s (dur unknown)", vid)

    await emit_voice_new(state, vid, desc, sender=sender)


async def voice_downloader_loop(state, interval_sec: float = 1.0, batch_limit: int = 10) -> None:
    """
    voice_downloads 테이블을 폴링하며 pending/failed(+stuck downloading) 항목을 다운로드합니다.
    바쁜 상태(오디오/STT/heavy_ops/wifi)에서는 I/O 경쟁 방지로 스킵합니다.

    :param state: MonitorState
    :param interval_sec: 폴링 간격(초)
    :param batch_limit: 한 번에 처리할 최대 개수
    :returns: None
    """
    repo = getattr(state, "voice_repo", None)
    if repo is None:
        logger.warning("[voice_dl] voice_repo missing -> loop disabled")
        return

    while not getattr(state, "shutting_down", False):
        await asyncio.sleep(float(interval_sec))

        try:
            audio_busy = bool(getattr(state, "audio", None) and getattr(state.audio, "is_playing", False))
        except Exception:
            audio_busy = False
        stt_busy = bool(getattr(state, "stt_busy", False))
        paused = bool(getattr(state, "heavy_ops_pause", False))
        wifi_busy = bool(getattr(state, "wifi_busy", False))
        if audio_busy or stt_busy or paused or wifi_busy:
            continue

        session = getattr(state, "http_session", None)
        if session is None or session.closed:
            continue

        try:
            items = repo.list_pending_downloads(limit=int(batch_limit), now=now_ts())
            if not items:
                continue

            async def _handle_one(it: dict) -> None:
                vid = int(it["id"])
                url = str(it["url"])
                # loop에서는 다운로드만 수행
                await download_voice_to_local(state, vid, url)

            tasks = [asyncio.create_task(_handle_one(it)) for it in items]
            await asyncio.gather(*tasks, return_exceptions=True)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[voice_dl] unexpected error")
