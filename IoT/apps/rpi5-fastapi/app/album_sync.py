# app/album_sync.py
import asyncio
import logging
import time
from typing import Any, Dict, List, Tuple

from .config import API_BASE_URL, ALBUM_SYNC_PATH
from .state import MonitorState
from .http import async_http_get_json
from .sync_utils import is_ok_status

logger = logging.getLogger(__name__)

DEFAULT_SYNC_PATH = "/api/iot/device/sync/album"

def _get_sync_url() -> str | None:
    base = (API_BASE_URL or "").strip().rstrip("/")
    if not base:
        return None
    path = (ALBUM_SYNC_PATH or DEFAULT_SYNC_PATH).strip()
    if not path.startswith("/"):
        path = "/" + path
    return base + path


def _extract_data(resp_json: Dict[str, Any]) -> Dict[str, Any]:
    status = resp_json.get("statusCode")
    ok = is_ok_status(status)
    msg = (resp_json.get("message") or resp_json.get("msg") or "").strip()

    # 실패는 반드시 실패로 처리(지금까지의 치명 버그 포인트)
    if not ok:
        logger.warning("[album_sync] not-ok status=%r msg=%r body=%s", status, msg, resp_json)
        raise ValueError(msg or "sync failed")

    # ok인데 메시지가 수상하면 경고만
    if msg and "fail" in msg.lower():
        logger.warning("[album_sync] ok-status but suspicious msg=%r body=%s", msg, resp_json)

    data = resp_json.get("data")
    if not isinstance(data, dict):
        logger.info("[album_sync] empty/noop delta (data missing or not dict). body=%s", resp_json)
        data = {}

    data.setdefault("added", [])
    data.setdefault("deleted", [])

    if not isinstance(data["added"], list):
        data["added"] = []
    if not isinstance(data["deleted"], list):
        data["deleted"] = []

    if "lastLogId" in data and "log_id" not in data:
        data["log_id"] = data.get("lastLogId")

    return data

def _apply_delta_to_cache(state: MonitorState, delta: Dict[str, Any]) -> Tuple[int, int]:
    """
    delta를 메모리 캐시에 반영.
    returns: (added_count, deleted_count)
    """
    added: List[Dict[str, Any]] = delta.get("added") or []
    deleted: List[int] = delta.get("deleted") or []

    add_cnt = 0
    del_cnt = 0

    # deleted 먼저
    for pid in deleted:
        try:
            pid_i = int(pid)
        except Exception:
            continue
        if pid_i in state.album_cache:
            state.album_cache.pop(pid_i, None)
            del_cnt += 1

    # added upsert
    for it in added:
        if not isinstance(it, dict) or "id" not in it:
            continue
        pid = int(it["id"])
        state.album_cache[pid] = {
            "id": pid,
            "url": it.get("url"),
            "description": it.get("description"),
            "takenAt": it.get("takenAt"),
            "user_id": it.get("userId") or it.get("user_id"),  # 추가
            "local_path": None,
        }
        add_cnt += 1

    state.album_cache_ts = time.time()
    state.album_cache_loaded = True
    return add_cnt, del_cnt

def load_album_cache_from_db(state: MonitorState) -> int:
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
            "user_id": r.get("user_id"),  # 추가
            "local_path": (r.get("local_path") if r.get("dl_status") == "done" else None),
        }
    state.album_cache_loaded = True
    state.album_cache_ts = time.time()
    return len(rows)

async def async_sync_album_once(state: MonitorState) -> Dict[str, Any]:
    """
    - 부팅 1회 / MQTT 신호 시 호출되는 단발성 sync
    - DB 반영 + 캐시 반영
    """
    if not state.album_repo:
        return {"ok": False, "message": "album_repo not initialized"}

    if not state.device_store:
        return {"ok": False, "message": "device_store not initialized"}

    token = state.device_store.get_token()
    if not token:
        return {"ok": False, "message": "token missing"}

    sync_url = _get_sync_url()
    if not sync_url:
        logger.warning("[album_sync] API disabled (API_BASE_URL missing/empty)")
        return {"ok": False, "message": "api disabled"}

    # 여러 트리거(부팅/MQTT/HTTP) 동시 실행 방지
    async with state.album_lock:
        last_log_id = state.album_repo.get_last_log_id()

        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        params = {"lastLogId": last_log_id}

        t0 = time.time()
        try:
            resp_json = await async_http_get_json(state, sync_url, headers=headers, params=params, timeout_sec=10.0)
            data = _extract_data(resp_json)

            if not data.get("added") and not data.get("deleted"):
                state.album_last_sync_ts = time.time()
                state.album_last_sync_ok = True
                logger.info("[album_sync] noop (no added/deleted) last_log_id=%s", last_log_id)
                return {"ok": True, "skipped": True, "last_log_id": last_log_id, "message": "noop"}
            # log_id 방어: 서버가 안 주면 기존 유지
            try:
                data["log_id"] = int(data.get("log_id"))
            except Exception:
                data["log_id"] = last_log_id

            # DB 반영
            new_log_id, db_add_cnt, db_del_cnt = state.album_repo.apply_sync_delta(data)

            # 캐시 반영
            cache_add_cnt, cache_del_cnt = _apply_delta_to_cache(state, data)
            state.album_last_sync_ts = time.time()
            state.album_last_sync_ok = True
            dt = state.album_last_sync_ts - t0
            logger.info(
                "[album_sync] ok last=%s new=%s db(add=%s del=%s) cache(add=%s del=%s) dt=%.2fs",
                last_log_id, new_log_id, db_add_cnt, db_del_cnt, cache_add_cnt, cache_del_cnt, dt
            )
            return {
                "ok": True,
                "last_log_id": last_log_id,
                "new_log_id": new_log_id,
                "db": {"added": db_add_cnt, "deleted": db_del_cnt},
                "cache": {"added": cache_add_cnt, "deleted": cache_del_cnt},
                "dt_sec": dt,
            }

        except Exception as e:
            state.album_last_sync_ts = time.time()
            state.album_last_sync_ok = False
            dt = state.album_last_sync_ts - t0
            logger.exception("[album_sync] failed dt=%.2fs err=%s", dt, e)
            return {"ok": False, "message": str(e), "dt_sec": dt}
