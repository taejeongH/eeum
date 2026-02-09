
import time
from .state import MonitorState

def load_member_cache_from_db(state: MonitorState) -> int:
    repo = getattr(state, "member_repo", None)
    if not repo:
        return 0
    rows = repo.list_all()
    state.member_cache.clear()
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
    state.member_cache_loaded = True
    state.member_cache_ts = time.time()
    return len(rows)
