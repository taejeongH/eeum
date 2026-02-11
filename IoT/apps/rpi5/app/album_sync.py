import logging
import os
from typing import Any, Dict, List, Tuple
from app.config import API_BASE_URL, ALBUM_SYNC_PATH
from app.http import async_http_get_json
from app.state import MonitorState
from app.sync_utils import build_api_url, normalize_sync_response, now_ts

logger = logging.getLogger(__name__)

DEFAULT_SYNC_PATH = "/api/iot/device/sync/album"

def _safe_remove(path: str) -> None:
    """파일을 best-effort로 삭제합니다."""
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass

def _collect_local_paths_for_deleted(state: MonitorState, deleted_ids: List[int]) -> List[str]:
    """
    삭제될 photo_id 목록에 대해 현재 저장된 local_path를 조회합니다.

    :param state: 전역 상태
    :param deleted_ids: 삭제될 photo_id 리스트
    :return: local_path 리스트(빈 값 제외)
    """
    if not deleted_ids or not state.album_repo:
        return []

    q = ",".join(["?"] * len(deleted_ids))
    rows = state.album_repo.conn.execute(
        f"SELECT local_path FROM album_downloads WHERE photo_id IN ({q})",
        [int(x) for x in deleted_ids],
    ).fetchall()

    out: List[str] = []
    for r in rows:
        p = str(r["local_path"] or "").strip()
        if p:
            out.append(p)
    return out

def _extract_album_delta(resp_json: Dict[str, Any]) -> Dict[str, Any]:
    """
    album sync 응답을 album 전용 형태로 보정합니다.

    :param resp_json: 서버 응답
    :return: delta dict (added/deleted/log_id 포함)
    """
    data, msg, _ = normalize_sync_response(resp_json, service="album")

    if msg and "fail" in msg.lower():
        logger.warning("[album_sync] ok-status but suspicious msg=%r body=%s", msg, resp_json)

    data.setdefault("added", [])
    data.setdefault("deleted", [])

    if "lastLogId" in data and "log_id" not in data:
        data["log_id"] = data.get("lastLogId")

    return data

def _apply_delta_to_cache(state: MonitorState, delta: Dict[str, Any]) -> Tuple[int, int]:
    """
    delta를 메모리 캐시에 반영합니다.

    :param state: 전역 상태
    :param delta: 서버 delta
    :return: (added_count, deleted_count)
    """
    added_items: List[Dict[str, Any]] = delta.get("added") or []
    deleted_ids: List[int] = delta.get("deleted") or []

    added_count = 0
    deleted_count = 0

    for pid in deleted_ids:
        try:
            pid_i = int(pid)
        except Exception:
            continue
        if pid_i in state.album_cache:
            state.album_cache.pop(pid_i, None)
            deleted_count += 1

    for it in added_items:
        if not isinstance(it, dict) or "id" not in it:
            continue
        pid = int(it["id"])
        state.album_cache[pid] = {
            "id": pid,
            "url": it.get("url"),
            "description": it.get("description"),
            "takenAt": it.get("takenAt"),
            "user_id": it.get("userId") or it.get("user_id"),
            "local_path": None,
        }
        added_count += 1

    state.album_cache_ts = now_ts()
    state.album_cache_loaded = True
    return added_count, deleted_count

def load_album_cache_from_db(state: MonitorState) -> int:
    """
    DB에서 album 캐시를 로딩합니다.

    :param state: 전역 상태
    :return: 로딩한 row 수
    """
    if not state.album_repo:
        return 0

    rows = state.album_repo.list_all_photos_for_cache()
    state.album_cache.clear()

    for r in rows:
        pid = int(r["id"])
        state.album_cache[pid] = {
            "id": pid,
            "url": r.get("url"),
            "description": r.get("description"),
            "takenAt": r.get("taken_at"),
            "user_id": r.get("user_id"),
            "local_path": (r.get("local_path") if r.get("dl_status") == "done" else None),
        }

    state.album_cache_loaded = True
    state.album_cache_ts = now_ts()
    return len(rows)

def _sync_prerequisite_check(state: MonitorState) -> tuple[bool, dict]:
    if not state.album_repo:
        return False, {"ok": False, "message": "album_repo not initialized"}
    if not state.device_store:
        return False, {"ok": False, "message": "device_store not initialized"}

    token = state.device_store.get_token()
    if not token:
        return False, {"ok": False, "message": "token missing"}

    sync_url = build_api_url(API_BASE_URL, ALBUM_SYNC_PATH, DEFAULT_SYNC_PATH)
    if not sync_url:
        logger.warning("[album_sync] API disabled (API_BASE_URL missing/empty)")
        return False, {"ok": False, "message": "api disabled"}

    return True, {"token": token, "sync_url": sync_url}

def _normalize_log_id(delta: Dict[str, Any], fallback_last_log_id: int) -> int:
    """delta의 log_id를 int로 정규화하고 실패 시 fallback을 사용합니다."""
    try:
        return int(delta.get("log_id"))
    except Exception:
        return int(fallback_last_log_id)

async def async_sync_album_once(state: MonitorState) -> Dict[str, Any]:
    """
    단발성 album sync를 수행합니다.
    - DB 반영 + 캐시 반영
    - 삭제된 사진의 로컬 파일(local_path)은 best-effort로 제거합니다.

    :param state: 전역 상태
    :return: {"ok": bool, ...} 형태의 결과 dict.
             성공 시 last/new log id, db/cache 반영 개수, 제거한 파일 수, dt_sec 포함
    """
    ok, info = _sync_prerequisite_check(state)
    if not ok:
        return info

    token = info["token"]
    sync_url = info["sync_url"]

    async with state.album_lock:
        last_log_id = state.album_repo.get_last_log_id()
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        params = {"lastLogId": last_log_id}

        t0 = now_ts()
        try:
            resp_json = await async_http_get_json(
                state, sync_url, headers=headers, params=params, timeout_sec=10.0
            )
            delta = _extract_album_delta(resp_json)

            has_change = bool(delta.get("added")) or bool(delta.get("deleted"))
            if not has_change:
                state.album_last_sync_ts = now_ts()
                state.album_last_sync_ok = True
                logger.info("[album_sync] noop (no added/deleted) last_log_id=%s", last_log_id)
                return {"ok": True, "skipped": True, "last_log_id": last_log_id, "message": "noop"}

            delta["log_id"] = _normalize_log_id(delta, last_log_id)

            # 삭제될 항목의 local_path는 FK 삭제 이후엔 조회가 어려우므로, apply 전에 수집
            deleted_raw = delta.get("deleted") or []
            deleted_ids: List[int] = []
            for x in deleted_raw:
                try:
                    deleted_ids.append(int(x))
                except Exception:
                    continue
            paths_to_remove = _collect_local_paths_for_deleted(state, deleted_ids)

            new_log_id, db_add, db_del, deleted_ids2 = state.album_repo.apply_sync_delta(delta)
            cache_add, cache_del = _apply_delta_to_cache(state, delta)

            # best-effort 로컬 파일 제거
            removed = 0
            for p in paths_to_remove:
                _safe_remove(p)
                _safe_remove(p + ".tmp")
                removed += 1

            state.album_last_sync_ts = now_ts()
            state.album_last_sync_ok = True
            dt = state.album_last_sync_ts - t0

            logger.info(
                "[album_sync] ok last=%s new=%s db(add=%s del=%s) cache(add=%s del=%s) rm=%s dt=%.2fs",
                last_log_id,
                new_log_id,
                db_add,
                db_del,
                cache_add,
                cache_del,
                removed,
                dt,
            )

            return {
                "ok": True,
                "last_log_id": last_log_id,
                "new_log_id": new_log_id,
                "db": {"added": db_add, "deleted": db_del, "deleted_ids": deleted_ids2},
                "cache": {"added": cache_add, "deleted": cache_del},
                "removed_local_files": removed,
                "dt_sec": dt,
            }

        except Exception as e:
            state.album_last_sync_ts = now_ts()
            state.album_last_sync_ok = False
            dt = state.album_last_sync_ts - t0
            logger.exception("[album_sync] failed dt=%.2fs err=%s", dt, e)
            return {"ok": False, "message": str(e), "dt_sec": dt}
