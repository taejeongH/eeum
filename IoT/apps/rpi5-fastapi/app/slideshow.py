# app/slideshow.py
import os
import asyncio
import time
import logging
from typing import Any, Optional
from .profile_cache import ensure_profile_cached
from .sse_fanout import fanout_nowait

logger = logging.getLogger(__name__)

def _sort_key(item: dict) -> tuple:
    # takenAt이 None이면 뒤로
    taken = item.get("takenAt")
    taken_none = (taken is None)
    # 최신 먼저 원하면 역정렬 필요하지만 MVP는 단순 stable
    return (taken_none, str(taken or ""), -int(item.get("id") or 0))

def rebuild_playlist(state) -> None:
    """
    state.album_cache 기반으로 playlist 구성
    - cache가 비었으면 playlist=[]
    - takenAt/ID 기준 정렬(원하는 정책 있으면 여기만 바꾸면 됨)
    """
    items = list(state.album_cache.values())
    items.sort(key=_sort_key)
    state.slide_playlist = [int(x["id"]) for x in items if "id" in x]

    # index 보정
    if not state.slide_playlist:
        state.slide_index = 0
        logger.warning(
            "[slideshow] playlist empty (album_cache=%d)",
            len(state.album_cache),
        )
    else:
        if state.slide_index < 0:
            state.slide_index = 0
        if state.slide_index >= len(state.slide_playlist):
            state.slide_index = 0
        logger.info(
            "[slideshow] playlist rebuilt count=%d",
            len(state.slide_playlist),
        )

def get_current_item(state) -> Optional[dict]:
    if not state.slide_playlist:
        return None
    pid = state.slide_playlist[state.slide_index]
    return state.album_cache.get(int(pid))

def normalize_item(item: dict | None) -> dict | None:
    if not item:
        return None

    local_path = item.get("local_path")
    if local_path:
        # local_path가 절대/상대든 상관없이 파일명만 뽑아서 /album 아래로 매핑
        filename = os.path.basename(str(local_path))
        url = f"/album/{filename}"
    else:
        url = item.get("url")

    return {
        "id": item.get("id"),
        "url": url,
        "description": item.get("description"),
        "takenAt": item.get("takenAt"),
    }

async def build_sender(state, uid: Any) -> dict:
    """
    cache-first sender builder:
      1) member_cache 우선
      2) 없으면 member_repo(DB) fallback + 캐시에 저장
      3) profile_image_url은 가능하면 /profile/... 로컬 캐시로 치환

    명세: sender는 항상 dict (값은 null/빈문자열 가능)
    """
    try:
        uid_i = int(uid) if uid is not None else None
    except Exception:
        uid_i = None

    if uid_i is None:
        return {"user_id": None, "name": "", "profile_image_url": ""}

    # 1) member_cache 우선
    m = None
    try:
        m = (getattr(state, "member_cache", None) or {}).get(uid_i)
    except Exception:
        m = None

    # 2) 없으면 DB fallback
    if m is None and getattr(state, "member_repo", None):
        try:
            m = state.member_repo.get(uid_i) or None
        except Exception:
            m = None
        if m is not None:
            # 캐시에 저장
            try:
                if getattr(state, "member_cache", None) is None:
                    state.member_cache = {}
                state.member_cache[uid_i] = dict(m)
            except Exception:
                pass

    name = str((m or {}).get("name") or "")
    profile_url = str((m or {}).get("profile_image_url") or "")

    # 3) 프로필 캐싱(/profile/... 치환)
    if profile_url and getattr(state, "http_session", None) and not state.http_session.closed:
        try:
            profile_url = await ensure_profile_cached(
                state.http_session, profile_url, timeout_sec=8.0
            )
        except Exception:
            # 실패 시 원본 유지
            pass

    return {"user_id": uid_i, "name": name, "profile_image_url": profile_url}

async def build_album_item(state, raw: dict | None) -> dict | None:
    """
    슬라이드/상태/부트 모두 동일 포맷 AlbumItem 생성:
      {id,url,description,takenAt,sender}
    """
    item = normalize_item(raw)
    if item is None:
        return None
    uid = raw.get("user_id") if raw else None
    item["sender"] = await build_sender(state, uid)
    return item

async def emit_slide(state, reason: str) -> None:
    """
    SSE slide 이벤트를 모든 subscriber에게 push
    """
    async with state.slide_lock:
        state.slide_seq += 1
        seq = state.slide_seq
        raw = get_current_item(state)
        item = await build_album_item(state, raw)

        if item is None:
            logger.debug(
                "[slideshow] emit_slide item=None reason=%s playlist_len=%d",
                reason,
                len(state.slide_playlist),
            )

        payload = {
            "ts": time.time(),
            "seq": seq,
            "item": item,
            "reason": reason,
        }

        # fanout: QueueFull이면 oldest drop 후 1회 재시도, 그래도 실패하면 subscriber 제거
        fanout_nowait(state.slide_subscribers, payload)
        
    # 어떤 이유로든 슬라이드 이벤트가 나가면 타이머 루프를 깨워 drift 줄이기
    try:
        state.slide_tick_event.set()
    except Exception:
        pass

def _advance_index(state, delta: int) -> None:
    if not state.slide_playlist:
        state.slide_index = 0
        return
    n = len(state.slide_playlist)
    state.slide_index = (state.slide_index + delta) % n

async def next_slide(state, reason: str = "next") -> None:
    async with state.slide_lock:
        _advance_index(state, +1)
    await emit_slide(state, reason)

async def prev_slide(state, reason: str = "prev") -> None:
    async with state.slide_lock:
        _advance_index(state, -1)
    await emit_slide(state, reason)

async def set_playing(state, playing: bool, interval_sec: Optional[float] = None) -> None:
    async with state.slide_lock:
        state.slide_playing = bool(playing)
        if interval_sec is not None:
            try:
                v = float(interval_sec)
                if v > 0:
                    state.slide_interval_sec = v
            except Exception:
                pass
    # play/pause/interval 변경 즉시 타이머 반영
    state.slide_tick_event.set()

async def slideshow_timer_loop(state) -> None:
    while True:
        if getattr(state, "shutting_down", False):
            return
        # 재생이 아니면 '깨우기' 이벤트 올 때까지 대기
        if not state.slide_playing:
            await state.slide_tick_event.wait()
            state.slide_tick_event.clear()
            continue

        interval = float(state.slide_interval_sec or 60.0)

        # interval 동안 기다리되, 중간에 제어 이벤트가 오면 즉시 깨어남
        try:
            await asyncio.wait_for(state.slide_tick_event.wait(), timeout=interval)
            state.slide_tick_event.clear()
            # 제어/동기화로 깬 거면 타이머 전환은 하지 않고 다음 루프로
            continue
        except asyncio.TimeoutError:
            pass

        if getattr(state, "shutting_down", False):
            return
        if not state.slide_playing:
            continue
        await next_slide(state, reason="timer")
