import asyncio
import json
import logging
import os
from typing import Any, List, Literal, Optional
from fastapi import FastAPI, Query
from pydantic import BaseModel
from app.api_common import fail, fail_voice, ok, ok_voice, sse_response
from app.audio_manager import AudioJob, AudioPrio
from app.config import AUDIO_DRAIN_FUDGE_SEC, AUDIO_PREROLL_MS, AUDIO_START_DELAY_MS
from app.profile_cache import ensure_profile_cached
from app.state import MonitorState
from app.sync_utils import now_ts
from app.voice_duration import verify_mp3_quick
from app.voice_player import emit_voice_done, voice_path

logger = logging.getLogger(__name__)

class AckTarget(BaseModel):
    type: Literal["voice"]
    id: int

class AckReq(BaseModel):
    target: AckTarget
    action: Literal["play", "skip"]

class AckBatchItem(BaseModel):
    target: AckTarget
    action: Optional[Literal["play", "skip"]] = None

class AckBatchReq(BaseModel):
    mode: Literal["sequential"]
    default_action: Literal["play", "skip"]
    items: List[AckBatchItem]

def _safe_int(value: Any) -> Optional[int]:
    try:
        return int(value) if value is not None else None
    except Exception:
        return None

def _calc_delay_sec() -> float:
    return (AUDIO_PREROLL_MS / 1000.0) + (AUDIO_START_DELAY_MS / 1000.0) + float(AUDIO_DRAIN_FUDGE_SEC or 0.4)

def _watchdog_timeout_sec(duration_sec: Optional[float]) -> float:
    """
    voice 재생 watchdog 타임아웃을 계산합니다.

    :param duration_sec: 음성 길이(초) 또는 None
    :return: watchdog 타임아웃(초)
    """
    max_timeout = 120.0
    if duration_sec is None or duration_sec <= 0:
        return max_timeout
    return min(max_timeout, float(duration_sec) + _calc_delay_sec() + 3.0)

async def _build_sender(state: MonitorState, user_id: Any) -> dict:
    """
    member_cache/DB를 이용해 sender 정보를 구성합니다.
    profile_image_url은 가능하면 로컬 캐시(/profile/...)로 치환합니다.

    :param state: 전역 상태
    :param user_id: 사용자 ID
    :return: sender dict
    """
    user_id_int = _safe_int(user_id)
    if user_id_int is None:
        return {"user_id": None, "name": "", "profile_image_url": ""}

    member = None
    try:
        member = (state.member_cache or {}).get(user_id_int)
    except Exception:
        member = None

    if member is None and getattr(state, "member_repo", None):
        try:
            member = state.member_repo.get(user_id_int) or None
        except Exception:
            member = None
        if member is not None:
            try:
                state.member_cache[user_id_int] = dict(member)
            except Exception:
                pass

    name = str((member or {}).get("name") or "")
    profile_url = str((member or {}).get("profile_image_url") or "")

    if profile_url and state.http_session and not state.http_session.closed:
        try:
            profile_url = await ensure_profile_cached(state.http_session, profile_url, timeout_sec=8.0)
        except Exception:
            pass

    return {"user_id": user_id_int, "name": name, "profile_image_url": profile_url}

async def _emit_done_once(state: MonitorState, voice_id: int, result: str) -> None:
    """
    voice done SSE를 동일 voice_id에 대해 1회만 emit합니다.

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :param result: "done" | "skipped"
    :return: None
    """
    try:
        async with state.voice_done_lock:
            if int(voice_id) in state.voice_done_sent:
                return
            state.voice_done_sent.add(int(voice_id))
    except Exception:
        pass

    await emit_voice_done(state, int(voice_id), result)

def _get_ack_lock(state: MonitorState, voice_id: int) -> asyncio.Lock:
    """
    voice_id별 ACK 직렬화 락을 반환합니다(없으면 생성).

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :return: asyncio.Lock
    """
    lock = state.voice_ack_locks.get(int(voice_id))
    if lock is None:
        lock = asyncio.Lock()
        state.voice_ack_locks[int(voice_id)] = lock
    return lock

def _remove_file_best_effort(path: str) -> None:
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass

def _cleanup_voice_artifacts(state: MonitorState, voice_id: int, local_path: str) -> None:
    """
    voice 관련 DB/파일/캐시 정리를 수행합니다.

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :param local_path: 로컬 파일 경로
    :return: None
    """
    try:
        if state.voice_repo:
            state.voice_repo.delete(int(voice_id))
    except Exception:
        pass

    _remove_file_best_effort(local_path)
    _remove_file_best_effort(local_path + ".tmp")

    try:
        state.voice_duration_cache.pop(int(voice_id), None)
    except Exception:
        pass


def _file_ready(local_path: str) -> bool:
    try:
        return bool(local_path and os.path.exists(local_path) and os.path.getsize(local_path) > 0)
    except Exception:
        return False

async def _verify_voice_or_mark_failed(state: MonitorState, voice_id: int, local_path: str) -> Optional[float]:
    """
    재생 전 mp3 검증을 수행합니다.
    실패하면 파일을 삭제하고 download 상태를 failed로 갱신하려고 시도합니다.

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :param local_path: 로컬 mp3 경로
    :return: 성공 시 duration_sec, 실패 시 None
    """
    try:
        duration_sec = await verify_mp3_quick(local_path, timeout_sec=0.8, min_dur_sec=0.05)
        return float(duration_sec)
    except Exception as e:
        _remove_file_best_effort(local_path)

        try:
            if state.voice_repo and hasattr(state.voice_repo, "get_download"):
                download_row = state.voice_repo.get_download(int(voice_id))
                retry_count = (download_row or {}).get("retry_count", 0)
                backoff_sec = min(300.0, float(2 ** int(retry_count or 0)))
                next_try_at = now_ts() + float(backoff_sec)

                state.voice_repo.set_download_status(
                    int(voice_id),
                    "failed",
                    local_path=local_path,
                    last_error=f"preplay_verify_failed: {e}",
                    inc_retry=True,
                    next_try_at=next_try_at,
                )
        except Exception:
            pass

        try:
            state.voice_duration_cache.pop(int(voice_id), None)
        except Exception:
            pass

        return None

async def _handle_skip(state: MonitorState, voice_id: int) -> dict:
    """
    skip 요청을 처리합니다.
    - DB/파일 정리
    - done emit(1회 보장)

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :return: API 응답(dict)
    """
    local_path = voice_path(int(voice_id))
    _cleanup_voice_artifacts(state, int(voice_id), local_path)

    try:
        await _emit_done_once(state, int(voice_id), "skipped")
    except Exception:
        pass

    return ok_voice("skip", int(voice_id))

async def _enqueue_voice_playback(
    state: MonitorState,
    voice_id: int,
    local_path: str,
    duration_sec: Optional[float],
) -> dict:
    """
    오디오 큐에 voice 재생 작업을 넣습니다.

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :param local_path: 로컬 mp3 경로
    :param duration_sec: mp3 길이(초) 또는 None
    :return: API 응답(dict)
    """
    try:
        if state.voice_repo:
            state.voice_repo.mark_playing(int(voice_id))
    except Exception:
        pass

    watchdog_timeout = _watchdog_timeout_sec(duration_sec)
    watchdog_task: asyncio.Task | None = None

    async def watchdog() -> None:
        try:
            await asyncio.sleep(float(watchdog_timeout))
        except asyncio.CancelledError:
            return

        try:
            await state.audio.stop_current()
        except Exception:
            pass

        _cleanup_voice_artifacts(state, int(voice_id), local_path)

        try:
            await _emit_done_once(state, int(voice_id), "done")
        except Exception:
            pass

    watchdog_task = asyncio.create_task(watchdog())

    def on_done_cleanup() -> None:
        try:
            if watchdog_task and not watchdog_task.done():
                watchdog_task.cancel()
        except Exception:
            pass

        try:
            loop = state.loop
            if loop and loop.is_running():
                asyncio.run_coroutine_threadsafe(_emit_done_once(state, int(voice_id), "done"), loop)
        except Exception:
            pass

        _cleanup_voice_artifacts(state, int(voice_id), local_path)

    await state.audio.enqueue(
        AudioJob(
            prio=int(AudioPrio.VOICE),
            kind="voice",
            path=local_path,
            ttl_sec=300.0,
            replace_key=None,
            on_done=on_done_cleanup,
        )
    )

    delay_sec = _calc_delay_sec()
    if duration_sec is not None and duration_sec > 0:
        return ok_voice("play", int(voice_id), duration_sec=(float(duration_sec) + delay_sec))
    return ok_voice("play", int(voice_id))

async def _handle_play(state: MonitorState, voice_id: int) -> dict:
    """
    play 요청을 처리합니다.
    - DB 상태 확인
    - 로컬 파일 존재/검증
    - 재생 큐 enqueue

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :return: API 응답(dict)
    """
    local_path = voice_path(int(voice_id))

    if not state.voice_repo:
        return fail_voice("not_found", "play", int(voice_id))

    try:
        voice_row = state.voice_repo.get(int(voice_id))
    except Exception:
        voice_row = None

    if not voice_row:
        return fail_voice("not_found", "play", int(voice_id))

    try:
        if str(voice_row.get("status") or "") == "playing":
            return fail_voice("already_done", "play", int(voice_id))
    except Exception:
        pass

    if not _file_ready(local_path):
        return fail_voice("not_ready", "play", int(voice_id))

    duration_sec = await _verify_voice_or_mark_failed(state, int(voice_id), local_path)
    if duration_sec is None:
        return fail_voice("not_ready", "play", int(voice_id))

    try:
        state.voice_duration_cache[int(voice_id)] = float(duration_sec)
    except Exception:
        pass

    return await _enqueue_voice_playback(state, int(voice_id), local_path, duration_sec)

async def _handle_ack(state: MonitorState, voice_id: int, action: str) -> dict:
    """
    voice ACK(play/skip)를 처리합니다.
    voice_id별 락으로 직렬화합니다.

    :param state: 전역 상태
    :param voice_id: 음성 ID
    :param action: "play" | "skip"
    :return: API 응답(dict)
    """
    if action not in ("play", "skip"):
        return fail("bad_request", None)

    lock = _get_ack_lock(state, int(voice_id))
    async with lock:
        if action == "skip":
            return await _handle_skip(state, int(voice_id))
        return await _handle_play(state, int(voice_id))


def register(app: FastAPI, state: MonitorState) -> None:
    """
    voice 재생/ACK/SSE 관련 라우트를 등록합니다.

    :param app: FastAPI
    :param state: MonitorState
    :return: None
    """
    @app.post("/api/ack")
    async def ack(req: AckReq):
        try:
            if req.target.type != "voice":
                return fail("bad_request", None)
            return await _handle_ack(state, int(req.target.id), str(req.action))
        except Exception:
            logger.exception("[api] /api/ack failed")
            return fail("bad_request", None)

    @app.post("/api/ack/batch")
    async def ack_batch(req: AckBatchReq):
        if req.mode != "sequential":
            return fail("bad_request", None)

        results = []
        for item in req.items:
            voice_id = int(item.target.id)
            action = item.action or req.default_action

            if action not in ("play", "skip"):
                results.append({"target": {"type": "voice", "id": voice_id}, "ok": False, "reason": "bad_request"})
                continue

            try:
                r = await _handle_ack(state, voice_id, action)
                if r.get("ok") is True:
                    data = r.get("data") or {}
                    out = {
                        "target": data.get("target") or {"type": "voice", "id": voice_id},
                        "ok": True,
                        "reason": r.get("reason"),
                    }
                    if action == "play" and isinstance(data.get("duration_sec"), (int, float)) and data["duration_sec"] > 0:
                        out["duration_sec"] = float(data["duration_sec"])
                    results.append(out)
                else:
                    results.append({"target": {"type": "voice", "id": voice_id}, "ok": False, "reason": r.get("reason") or "bad_request"})
            except Exception:
                logger.exception("[api] /api/ack/batch item failed id=%s", voice_id)
                results.append({"target": {"type": "voice", "id": voice_id}, "ok": False, "reason": "bad_request"})

        return {"ok": True, "reason": None, "data": {"mode": "sequential", "default_action": req.default_action, "results": results}}

    @app.post("/api/playback/skip_current")
    async def skip_current():
        try:
            await state.audio.stop_current()
            return {"ok": True, "reason": None, "data": {"skipped": True}}
        except Exception:
            return {"ok": True, "reason": "no_current", "data": {"skipped": False}}

    @app.get("/api/voice/pending")
    async def voice_pending(limit: int = Query(100), offset: int = Query(0)):
        if not state.voice_repo:
            return ok({"items": [], "limit": limit, "offset": offset})

        items = state.voice_repo.list_pending(limit=limit, offset=offset)
        out = []

        for row in items:
            voice_id = int(row["id"])
            sender = await _build_sender(state, row.get("user_id"))

            dl_status = row.get("download_status")
            local_path = str(row.get("local_path") or "")

            ready = bool(dl_status == "done" and local_path and _file_ready(local_path))
            if not ready:
                continue

            out.append(
                {
                    "id": voice_id,
                    "description": row.get("description") or "",
                    "created_at": row.get("created_at"),
                    "sender": sender if (sender["user_id"] or sender["name"] or sender["profile_image_url"]) else None,
                    "download": {
                        "status": dl_status or "pending",
                        "ready": bool(ready),
                        "retry_count": row.get("retry_count") or 0,
                        "next_try_at": row.get("next_try_at"),
                        "last_error": row.get("last_error") or "",
                        "updated_at": row.get("dl_updated_at"),
                    },
                }
            )

        return ok({"items": out, "limit": limit, "offset": offset})

    @app.get("/api/voice/stream")
    async def voice_stream():
        subscriber_queue = asyncio.Queue(maxsize=16)
        state.voice_subscribers.add(subscriber_queue)

        async def gen():
            try:
                while True:
                    envelope = await subscriber_queue.get()
                    event_type = (envelope or {}).get("_event") or "voice"
                    data = (envelope or {}).get("data") or {}
                    yield f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
            finally:
                state.voice_subscribers.discard(subscriber_queue)

        return sse_response(gen())