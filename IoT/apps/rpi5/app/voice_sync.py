import logging
import os
from typing import Any, Dict, List
from app.config import API_BASE_URL, VOICE_SYNC_PATH
from app.http import async_http_get_json
from app.profile_cache import remove_profile_cached
from app.state import MonitorState
from app.sync_utils import build_api_url, normalize_sync_response, now_ts
from app.voice_player import voice_path

logger = logging.getLogger(__name__)

DEFAULT_SYNC_PATH = "/api/iot/device/sync/voice"

def _safe_remove(path: str) -> None:
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass

def _remove_local_voice_files(vid: int) -> None:
    """
    로컬에 저장된 voice 파일을 제거합니다.

    :param vid: voice id
    :return: 없음
    """
    base = voice_path(vid)
    _safe_remove(base)
    _safe_remove(base + ".tmp")

def _normalize_members(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    members 필드를 내부 표준 형태로 정규화합니다.

    :param data: sync data
    :return: 정규화된 members 리스트
    """
    members = data.get("members") or []
    if not isinstance(members, list):
        return []

    out: List[Dict[str, Any]] = []
    for m in members:
        if not isinstance(m, dict):
            continue
        uid_raw = m.get("user_id") or m.get("userId")
        try:
            uid = int(uid_raw)
        except Exception:
            continue

        out.append(
            {
                "user_id": uid,
                "name": str(m.get("name") or ""),
                "profile_image_url": str(
                    m.get("profile_image_url") or m.get("profileImageUrl") or ""
                ),
            }
        )
    return out

def _upsert_members_and_cache(state: MonitorState, members: List[Dict[str, Any]]) -> None:
    """
    members를 DB에 upsert하고, 메모리 캐시도 갱신합니다.
    profile_image_url 변경 시, 이전 캐시 파일은 best-effort로 제거합니다.

    :param state: 전역 상태
    :param members: 정규화된 members 리스트
    :return: None
    """
    if not members:
        return

    if state.member_repo:
        try:
            def _on_changed(old_profile_url: str | None) -> None:
                # old_profile_url은 "이전 URL"이므로, 그 URL에 해당하는 캐시 파일 제거
                try:
                    if old_profile_url:
                        remove_profile_cached(str(old_profile_url))
                except Exception:
                    pass

            state.member_repo.upsert_many_with_change_detection(members, on_changed=_on_changed)
        except Exception:
            logger.exception("[voice_sync] member upsert failed")

    try:
        for m in members:
            uid = int(m["user_id"])
            state.member_cache[uid] = {
                "user_id": uid,
                "name": str(m.get("name") or ""),
                "profile_image_url": str(m.get("profile_image_url") or ""),
                "updated_at": now_ts(),
            }
        state.member_cache_loaded = True
        state.member_cache_ts = now_ts()
    except Exception:
        logger.exception("[voice_sync] member_cache update failed")

def _normalize_added_voices(data: Dict[str, Any]) -> List[Dict[str, Any]]:
    """
    added voice 리스트를 내부 표준 형태로 정규화합니다.

    :param data: sync data
    :return: 정규화된 added 리스트
    """
    added = data.get("added") or []
    if not isinstance(added, list):
        return []

    out: List[Dict[str, Any]] = []
    for v in added:
        if not isinstance(v, dict) or "id" not in v or "url" not in v:
            continue

        uid_raw = v.get("userId") or v.get("user_id")
        try:
            uid = int(uid_raw) if uid_raw is not None else None
        except Exception:
            uid = None

        out.append(
            {
                "id": int(v["id"]),
                "url": str(v["url"]),
                "description": str(v.get("description") or ""),
                "userId": uid,
            }
        )

    return out

def _sync_prerequisite_check(state: MonitorState) -> tuple[bool, dict]:
    if not state.voice_repo:
        return False, {"ok": False, "message": "voice_repo not initialized"}
    if not state.device_store:
        return False, {"ok": False, "message": "device_store not initialized"}

    token = state.device_store.get_token()
    if not token:
        return False, {"ok": False, "message": "token missing"}

    url = build_api_url(API_BASE_URL, VOICE_SYNC_PATH, DEFAULT_SYNC_PATH)
    if not url:
        logger.warning("[voice_sync] API disabled (API_BASE_URL missing/empty)")
        return False, {"ok": False, "message": "api disabled"}

    return True, {"token": token, "url": url}

async def async_sync_voice_once(state: MonitorState) -> Dict[str, Any]:
    """
    단발성 voice sync를 수행합니다.
    - members upsert 및 캐시 갱신(있을 때만)
    - added/deleted 반영 및 로컬 파일 삭제

    :param state: 전역 상태
    :return: sync 결과 dict
    """
    ok, info = _sync_prerequisite_check(state)
    if not ok:
        return info

    token = info["token"]
    url = info["url"]

    repo = state.voice_repo
    last_log_id = repo.get_last_log_id()

    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    params = {"lastLogId": last_log_id}

    resp = await async_http_get_json(state, url, headers=headers, params=params, timeout_sec=10.0)

    data, _, _ = normalize_sync_response(resp, service="voice")

    members = _normalize_members(data)
    _upsert_members_and_cache(state, members)

    normalized_added = _normalize_added_voices(data)

    sync_delta = {
        "log_id": data.get("log_id") or last_log_id,
        "added": normalized_added,
        "deleted": data.get("deleted") or [],
    }

    new_log, add_cnt, del_cnt, deleted_ids, inserted_ids = repo.apply_sync_delta(sync_delta)

    for vid in deleted_ids:
        _remove_local_voice_files(int(vid))

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
