import asyncio
import json
from typing import Optional
from fastapi import FastAPI
from pydantic import BaseModel
from app.api_common import sse_response
from app.slideshow import build_album_item, emit_slide, get_current_item, next_slide, prev_slide, set_playing
from app.state import MonitorState
from app.sync_utils import now_ts

class PlayReq(BaseModel):
    interval_sec: Optional[float] = None

def register(app: FastAPI, state: MonitorState) -> None:
    """
    슬라이드쇼 관련 라우트를 등록합니다.
    :param app: FastAPI
    :param state: MonitorState
    :return: None
    """
    @app.get("/api/slideshow/state")
    async def slideshow_state():
        current_id = get_current_item(state)
        current = await build_album_item(state, current_id)
        return {
            "ok": True,
            "ts": now_ts(),
            "playing": bool(state.slide_playing),
            "interval_sec": float(state.slide_interval_sec or 60),
            "mode": state.slide_mode or "sequential",
            "current": current,
        }

    @app.get("/api/slideshow/stream")
    async def slideshow_stream():
        subscriber_queue: asyncio.Queue = asyncio.Queue(maxsize=32)
        state.slide_subscribers.add(subscriber_queue)

        async def _boot_slide_payload() -> dict:
            async with state.slide_lock:
                state.slide_seq += 1
                seq = state.slide_seq
                current_id = get_current_item(state)
                item = await build_album_item(state, current_id)
                return {"ts": now_ts(), "seq": seq, "item": item, "reason": "boot"}

        async def gen():
            try:
                boot = await _boot_slide_payload()
                yield f"event: slide\ndata: {json.dumps(boot, ensure_ascii=False)}\n\n"
                while True:
                    ev = await subscriber_queue.get()
                    yield f"event: slide\ndata: {json.dumps(ev, ensure_ascii=False)}\n\n"
            except asyncio.CancelledError:
                raise
            finally:
                state.slide_subscribers.discard(subscriber_queue)

        return sse_response(gen())

    @app.post("/api/slideshow/play")
    async def slideshow_play(body: PlayReq):
        await set_playing(state, True, interval_sec=body.interval_sec)
        await emit_slide(state, reason="play")
        return {"ok": True}

    @app.post("/api/slideshow/pause")
    async def slideshow_pause():
        await set_playing(state, False)
        return {"ok": True}

    @app.post("/api/slideshow/next")
    async def slideshow_next():
        await next_slide(state, reason="next")
        return {"ok": True}

    @app.post("/api/slideshow/prev")
    async def slideshow_prev():
        await prev_slide(state, reason="prev")
        return {"ok": True}