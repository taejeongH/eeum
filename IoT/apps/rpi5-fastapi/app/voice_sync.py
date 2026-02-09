
import logging
import os
from typing import Any, Dict, List
import time
from .config import API_BASE_URL, VOICE_SYNC_PATH
from .state import MonitorState
from .http import async_http_get_json
from .sync_utils import is_ok_status
from .voice_player import voice_path

logger = logging.getLogger(__name__)

DEFAULT_SYNC_PATH = "/api/iot/device/sync/voice"

def _get_voice_sync_url() -> str | None:
    base = (API_BASE_URL or "").strip().rstrip("/")
    if not base:
        return None
    path = (VOICE_SYNC_PATH or DEFAULT_SYNC_PATH).strip()
    if not path.startswith("/"):
        path = "/" + path
    return base + path

def _extract_data(resp_json: Dict[str, Any]) -> Dict[str, Any]:
    status = resp_json.get("statusCode")
    ok = is_ok_status(status)
    if not ok:
        msg = (resp_json.get("message") or resp_json.get("msg") or "voice sync failed").strip()
        logger.warning("[voice_sync] not-ok status=%r msg=%r body=%s", status, msg, resp_json)
        raise ValueError(msg or "voice sync failed")

    data = resp_json.get("data")
    if not isinstance(data, dict):
        data = {}

    if "added" not in data or not isinstance(data.get("added"), list):
        data["added"] = []
    if "deleted" not in data or not isinstance(data.get("deleted"), list):
        data["deleted"] = []

    if "lastLogId" in data and "log_id" not in data:
        data["log_id"] = data.get("lastLogId")

    return data

def _remove_local_voice_file(vid: int) -> None:
    p = voice_path(vid)
    tmp = p + ".tmp"
    for fp in (p, tmp):
        try:
            if os.path.exists(fp):
                os.remove(fp)
        except Exception:
            pass

def _normalize_members(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    out: List[Dict[str, Any]] = []
    members = data.get("members") or []
    if not isinstance(members, list):
        return out

    for m in members:
        if not isinstance(m, dict):
            continue
        uid = m.get("user_id") or m.get("userId")
        try:
            uid = int(uid)
        except Exception:
            continue

        out.append({
            "user_id": uid,
            "name": str(m.get("name") or ""),
            "profile_image_url": str(m.get("profile_image_url") or m.get("profileImageUrl") or ""),
        })
    return out

async def async_sync_voice_once(state: MonitorState) -> Dict[str, Any]:
    if not state.voice_repo:
        return {"ok": False, "message": "voice_repo not initialized"}
    if not state.device_store:
        return {"ok": False, "message": "device_store not initialized"}

    token = state.device_store.get_token()
    if not token:
        return {"ok": False, "message": "token missing"}

    url = _get_voice_sync_url()
    if not url:
        logger.warning("[voice_sync] API disabled (API_BASE_URL missing/empty)")
        return {"ok": False, "message": "api disabled"}

    repo = state.voice_repo
    last_log_id = repo.get_last_log_id()

    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    params = {"lastLogId": last_log_id}

    resp = await async_http_get_json(state, url, headers=headers, params=params, timeout_sec=10.0)
    data = _extract_data(resp)

    
    if state.member_repo:
        ms = _normalize_members(data)
        if ms:
            try:
                state.member_repo.upsert_many_with_change_detection(ms)
            except Exception:
                logger.exception("[voice_sync] member upsert failed")

            try:
                for m in ms:
                    uid = int(m["user_id"])
                    state.member_cache[uid] = {
                        "user_id": uid,
                        "name": str(m.get("name") or ""),
                        "profile_image_url": str(m.get("profile_image_url") or ""),
                        "updated_at": time.time(),
                    }
                state.member_cache_loaded = True
                state.member_cache_ts = time.time()
            except Exception:
                logger.exception("[voice_sync] member_cache update failed")

    
    normalized_added: List[Dict[str, Any]] = []
    for v in data.get("added", []):
        if not isinstance(v, dict) or "id" not in v or "url" not in v:
            continue

        uid = v.get("userId") or v.get("user_id")
        try:
            uid = int(uid) if uid is not None else None
        except Exception:
            uid = None

        normalized_added.append({
            "id": int(v["id"]),
            "url": str(v["url"]),
            "description": str(v.get("description") or ""),
            "userId": uid,
        })

    sync_delta = {
        "log_id": data.get("log_id") or data.get("lastLogId") or last_log_id,
        "added": normalized_added,
        "deleted": data.get("deleted") or [],
    }

    
    new_log, add_cnt, del_cnt, deleted_ids, inserted_ids = repo.apply_sync_delta(sync_delta)

    for vid in deleted_ids:
        _remove_local_voice_file(int(vid))

    added_ids = [int(x["id"]) for x in normalized_added]

    return {
        "ok": True,
        "added": add_cnt,
        "added_ids": added_ids,
        "deleted": del_cnt,
        "deleted_ids": deleted_ids,
        "inserted_ids": inserted_ids,  
        "last_log_id": last_log_id,
        "new_log_id": new_log,
    }
