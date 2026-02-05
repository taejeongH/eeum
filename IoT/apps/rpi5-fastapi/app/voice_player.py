# app/voice_player.py
import asyncio
import os
import aiohttp
import time
import logging
from .config import VOICE_PATH
from .audio_manager import AudioJob, AudioPrio
from .voice_duration import get_mp3_duration_sec

logger = logging.getLogger(__name__)

VOICE_DIR = VOICE_PATH or "./voice"
os.makedirs(VOICE_DIR, exist_ok=True)

def voice_path(vid: int) -> str:
    return os.path.join(VOICE_DIR, f"{int(vid)}.mp3")

async def _emit_event(state, event_name: str, data: dict):
    """
    voice_stream에서 event/data를 그대로 내보내기 위해
    큐에 {"_event": "...", "data": {...}} 형태로 넣는다.
    """
    envelope = {"_event": event_name, "data": data}
    dead = []
    for q in list(state.voice_subscribers):
        try:
            q.put_nowait(envelope)
        except Exception:
            dead.append(q)
    for q in dead:
        state.voice_subscribers.discard(q)

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
    """
    SSE 보내는 즈음에 다운로드. 성공하면 로컬 mp3 경로 반환.
    """
    path = voice_path(vid)
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path

    s = getattr(state, "http_session", None)
    if s is None or s.closed:
        raise RuntimeError("http_session missing/closed")

    tmp = path + ".tmp"
    try:
        async with s.get(url, timeout=aiohttp.ClientTimeout(total=20.0)) as r:
            r.raise_for_status()

            total = 0
            with open(tmp, "wb") as f:
                async for chunk in r.content.iter_chunked(64 * 1024):
                    if not chunk:
                        continue
                    f.write(chunk)
                    total += len(chunk)

        os.replace(tmp, path)
        logger.info("[voice] downloaded id=%s bytes=%d path=%s", int(vid), total, path)
        return path

    except Exception:
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
        except Exception:
            pass
        raise

async def download_then_emit_new(state, vid: int, url: str, desc: str, sender: dict | None = None):
    """
    1) 다운로드
    2) (가능하면) duration 사전 계산 로그만 (ACK때 다시 계산해도 됨)
    3) SSE voice 이벤트 emit
    """
    path = await download_voice_to_local(state, vid, url)
    try:
        dur = await get_mp3_duration_sec(path)
        logger.info("[voice] downloaded id=%s dur=%.2fs", vid, dur)
    except Exception:
        logger.info("[voice] downloaded id=%s (dur unknown)", vid)
    await emit_voice_new(state, vid, desc, sender=sender)

async def play_voice_loop(state):
    """
    기존 자동재생 루프는 스펙(ACK로 play/skip)과 충돌하므로,
    여기서는 "자동 재생"을 하지 않는다.
    필요하면 나중에 "pending 다운로드 캐시 루프"로 바꿔 쓸 수 있음.
    """
    while True:
        await asyncio.sleep(5.0)