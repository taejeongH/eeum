import asyncio
import logging
import os
from typing import Any, Optional

from app.profile_cache import ensure_profile_cached
from app.sse_fanout import fanout_nowait
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

_SLIDE_SEQ_KV_KEY = "slideshow.last_seq"

def _db_kv_get(state, key: str) -> Optional[str]:
    """
    state.db(AppDB)의 kv_get을 안전하게 호출합니다.

    :param state: MonitorState (state.db 보유)
    :param key: 조회할 KV 키
    :returns: 저장된 값(str) 또는 None
    """
    db = getattr(state, "db", None)
    if not db:
        return None
    try:
        return db.kv_get(key)
    except Exception:
        return None

def _db_kv_set(state, key: str, value: str) -> None:
    """
    state.db(AppDB)의 kv_set을 안전하게 호출합니다. (best-effort)

    :param state: MonitorState (state.db 보유)
    :param key: 저장할 KV 키
    :param value: 저장할 값
    :returns: None
    """
    db = getattr(state, "db", None)
    if not db:
        return
    try:
        db.kv_set(key, value)
    except Exception:
        logger.exception("[slideshow_seq] kv_set failed key=%s value=%r", key, value)

def init_slide_seq_from_db(state) -> int:
    """
    DB에 저장된 slideshow seq를 state.slide_seq로 복구합니다.
    서버 부팅 시 1회 호출을 전제로 합니다.

    :param state: MonitorState
    :returns: 복구된 seq (정수)
    """
    raw = _db_kv_get(state, _SLIDE_SEQ_KV_KEY)
    try:
        seq = int(raw or 0)
    except Exception:
        seq = 0

    try:
        state.slide_seq = int(seq)
    except Exception:
        pass

    logger.info("[slideshow_seq] loaded seq=%s", seq)
    return int(seq)

def _persist_slide_seq(state, seq: int) -> None:
    """
    최신 seq를 DB에 저장합니다. (best-effort)

    :param state: MonitorState
    :param seq: 저장할 seq 값
    :returns: None
    """
    _db_kv_set(state, _SLIDE_SEQ_KV_KEY, str(int(seq)))

def _build_slide_sort_key(item: dict) -> tuple:
    """
    슬라이드 정렬 키를 생성합니다.
    - takenAt이 None이면 뒤로
    - 그 외 takenAt 오름차순(문자열), id 내림차순

    :param item: album item dict
    :returns: 정렬 키(tuple)
    """
    taken_at = item.get("takenAt")
    taken_at_is_none = taken_at is None
    photo_id = int(item.get("id") or 0)
    return (taken_at_is_none, str(taken_at or ""), -photo_id)

def rebuild_playlist(state) -> None:
    """
    state.album_cache를 기준으로 slide_playlist를 재구성합니다.

    :param state: MonitorState
    :returns: None
    """
    items = list((getattr(state, "album_cache", None) or {}).values())
    items.sort(key=_build_slide_sort_key)

    playlist = []
    for it in items:
        if isinstance(it, dict) and "id" in it:
            try:
                playlist.append(int(it["id"]))
            except Exception:
                continue

    state.slide_playlist = playlist

    if not state.slide_playlist:
        state.slide_index = 0
        logger.warning("[slideshow] playlist empty (album_cache=%d)", len(items))
        return

    if state.slide_index < 0 or state.slide_index >= len(state.slide_playlist):
        state.slide_index = 0

    logger.info("[slideshow] playlist rebuilt count=%d", len(state.slide_playlist))

def get_current_item(state) -> Optional[dict]:
    """
    현재 slide_index가 가리키는 album raw item을 반환합니다.

    :param state: MonitorState
    :returns: album item dict 또는 None
    """
    if not state.slide_playlist:
        return None

    try:
        photo_id = int(state.slide_playlist[state.slide_index])
    except Exception:
        return None

    return (getattr(state, "album_cache", None) or {}).get(photo_id)

def _map_local_path_to_public_url(local_path: str) -> str:
    """
    로컬 파일 경로를 /album/<filename> 형태의 public URL로 변환합니다.

    :param local_path: 로컬 파일 경로
    :returns: public URL 문자열
    """
    filename = os.path.basename(str(local_path))
    return f"/album/{filename}"

def normalize_item(raw: dict | None) -> dict | None:
    """
    album raw dict를 슬라이드 공통 포맷으로 정규화합니다.

    :param raw: album item raw dict
    :returns: {id, url, description, takenAt} 또는 None
    """
    if not raw:
        return None

    local_path = raw.get("local_path")
    if local_path:
        url = _map_local_path_to_public_url(str(local_path))
    else:
        url = raw.get("url")

    return {
        "id": raw.get("id"),
        "url": url,
        "description": raw.get("description"),
        "takenAt": raw.get("takenAt"),
    }

def _normalize_user_id(user_id: Any) -> Optional[int]:
    """
    user_id를 int로 정규화합니다.

    :param user_id: 원본 user_id
    :returns: 정규화된 int 또는 None
    """
    try:
        return int(user_id) if user_id is not None else None
    except Exception:
        return None

def _get_member_from_cache(state, user_id: int) -> Optional[dict]:
    """
    state.member_cache에서 user_id에 해당하는 멤버를 조회합니다.

    :param state: MonitorState
    :param user_id: 사용자 ID
    :returns: member dict 또는 None
    """
    cache = getattr(state, "member_cache", None) or {}
    try:
        member = cache.get(user_id)
        return dict(member) if isinstance(member, dict) else None
    except Exception:
        return None

def _put_member_to_cache(state, user_id: int, member: dict) -> None:
    """
    state.member_cache에 멤버 정보를 저장합니다. (best-effort)

    :param state: MonitorState
    :param user_id: 사용자 ID
    :param member: member dict
    :returns: None
    """
    try:
        if getattr(state, "member_cache", None) is None:
            state.member_cache = {}
        state.member_cache[user_id] = dict(member)
    except Exception:
        return

async def _ensure_profile_url_cached_if_possible(state, profile_url: str) -> str:
    """
    profile_url을 가능하면 로컬 캐시(/profile/...)로 치환합니다.

    :param state: MonitorState (http_session 보유)
    :param profile_url: 원본 프로필 이미지 URL
    :returns: 캐시된 public URL 또는 원본 URL
    """
    session = getattr(state, "http_session", None)
    if not profile_url:
        return profile_url
    if session is None or getattr(session, "closed", True):
        return profile_url

    try:
        logger.info("[profile_cache] in url=%s", profile_url)
        out = await ensure_profile_cached(session, profile_url, timeout_sec=8.0)
        logger.info("[profile_cache] out url=%s", out)
        return out
    except Exception:
        return profile_url

async def build_sender(state, user_id: Any) -> dict:
    """
    sender 정보를 구성합니다. (cache-first)

    :param state: MonitorState
    :param user_id: 사용자 ID(정수 변환 시도)
    :returns: {"user_id": int|None, "name": str, "profile_image_url": str}
    """
    uid = _normalize_user_id(user_id)
    if uid is None:
        return {"user_id": None, "name": "", "profile_image_url": ""}

    member = _get_member_from_cache(state, uid)

    if member is None and getattr(state, "member_repo", None):
        try:
            member = state.member_repo.get(uid) or None
        except Exception:
            member = None

        if isinstance(member, dict):
            _put_member_to_cache(state, uid, member)

    name = str((member or {}).get("name") or "")
    profile_url = str((member or {}).get("profile_image_url") or "")
    profile_url = await _ensure_profile_url_cached_if_possible(state, profile_url)

    logger.info("[sender] uid=%s name=%s profile_url=%s", uid, name, profile_url)

    return {"user_id": uid, "name": name, "profile_image_url": profile_url}

async def build_album_item(state, raw: dict | None) -> dict | None:
    """
    슬라이드/부트/상태 조회에서 공통으로 쓰는 AlbumItem을 생성합니다.

    :param state: MonitorState
    :param raw: album raw dict
    :returns: {id, url, description, takenAt, sender} 또는 None
    """
    item = normalize_item(raw)
    if item is None:
        return None

    uid = raw.get("user_id") if raw else None
    item["sender"] = await build_sender(state, uid)
    return item

async def _build_slide_payload_under_lock(state, reason: str) -> dict:
    """
    slide_lock 보유 상태에서 slide payload를 생성합니다.
    seq는 단조 증가를 유지하기 위해 매번 DB에 저장합니다.

    :param state: MonitorState
    :param reason: 이벤트 발생 이유
    :returns: {"ts": float, "seq": int, "item": dict|None, "reason": str}
    """
    try:
        state.slide_seq = int(getattr(state, "slide_seq", 0) or 0) + 1
    except Exception:
        state.slide_seq = 1

    seq = int(state.slide_seq)
    _persist_slide_seq(state, seq)

    raw = get_current_item(state)
    item = await build_album_item(state, raw)

    if item is None:
        logger.debug(
            "[slideshow] emit_slide item=None reason=%s playlist_len=%d",
            reason,
            len(state.slide_playlist),
        )

    return {"ts": now_ts(), "seq": seq, "item": item, "reason": reason}

def _wake_slide_timer(state) -> None:
    """
    slide_tick_event를 set하여 타이머 루프를 즉시 깨웁니다. (best-effort)

    :param state: MonitorState
    :returns: None
    """
    try:
        state.slide_tick_event.set()
    except Exception:
        return

async def emit_slide(state, reason: str) -> None:
    """
    slide 이벤트를 모든 subscriber에게 fanout 합니다.

    :param state: MonitorState
    :param reason: 이벤트 발생 이유
    :returns: None
    """
    async with state.slide_lock:
        payload = await _build_slide_payload_under_lock(state, reason)
        fanout_nowait(state.slide_subscribers, payload)

    _wake_slide_timer(state)

def _advance_index(state, delta: int) -> None:
    """
    slide_index를 delta만큼 순환 이동합니다.

    :param state: MonitorState
    :param delta: 이동량(+1 next, -1 prev)
    :returns: None
    """
    if not state.slide_playlist:
        state.slide_index = 0
        return

    n = len(state.slide_playlist)
    state.slide_index = (state.slide_index + int(delta)) % n

async def next_slide(state, reason: str = "next") -> None:
    """
    다음 슬라이드로 이동 후 slide 이벤트를 emit 합니다.

    :param state: MonitorState
    :param reason: 이벤트 발생 이유
    :returns: None
    """
    async with state.slide_lock:
        _advance_index(state, +1)
    await emit_slide(state, reason)

async def prev_slide(state, reason: str = "prev") -> None:
    """
    이전 슬라이드로 이동 후 slide 이벤트를 emit 합니다.

    :param state: MonitorState
    :param reason: 이벤트 발생 이유
    :returns: None
    """
    async with state.slide_lock:
        _advance_index(state, -1)
    await emit_slide(state, reason)

async def set_playing(state, playing: bool, interval_sec: Optional[float] = None) -> None:
    """
    슬라이드 재생/일시정지 상태와 재생 간격을 설정합니다.

    :param state: MonitorState
    :param playing: 재생 여부
    :param interval_sec: 재생 간격(초). None이면 변경하지 않음
    :returns: None
    """
    async with state.slide_lock:
        state.slide_playing = bool(playing)

        if interval_sec is not None:
            try:
                v = float(interval_sec)
                if v > 0:
                    state.slide_interval_sec = v
            except Exception:
                pass

    _wake_slide_timer(state)

async def slideshow_timer_loop(state) -> None:
    """
    슬라이드 자동 전환 타이머 루프입니다.
    - play 상태: interval마다 next (reason="timer")
    - pause 상태: 이벤트가 올 때까지 대기
    - slide_tick_event set 시 즉시 wake하여 drift를 줄임

    :param state: MonitorState
    :returns: None
    """
    while True:
        if getattr(state, "shutting_down", False):
            return

        if not state.slide_playing:
            await state.slide_tick_event.wait()
            state.slide_tick_event.clear()
            continue

        interval = float(state.slide_interval_sec or 60.0)

        try:
            await asyncio.wait_for(state.slide_tick_event.wait(), timeout=interval)
            state.slide_tick_event.clear()
            continue
        except asyncio.TimeoutError:
            pass

        if getattr(state, "shutting_down", False):
            return
        if not state.slide_playing:
            continue

        await next_slide(state, reason="timer")