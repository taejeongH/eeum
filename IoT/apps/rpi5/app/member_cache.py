from app.state import MonitorState
from app.sync_utils import now_ts

def load_member_cache_from_db(state: MonitorState) -> int:
    """
    DB에서 멤버 캐시를 로딩합니다.

    :param state: 전역 상태
    :return: 로딩된 row 수
    """
    repo = getattr(state, "member_repo", None)
    if not repo:
        return 0

    rows = repo.list_all()
    state.member_cache.clear()

    count = 0
    for r in rows:
        try:
            uid = int(r["user_id"])
        except Exception:
            continue

        state.member_cache[uid] = {
            "user_id": uid,
            "name": str(r.get("name") or ""),
            "profile_image_url": str(r.get("profile_image_url") or ""),
            "updated_at": r.get("updated_at"),
        }
        count += 1

    state.member_cache_loaded = True
    state.member_cache_ts = now_ts()
    return count