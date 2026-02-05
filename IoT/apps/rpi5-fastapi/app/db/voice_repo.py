# app/db/voice_repo.py
import time
from typing import Optional, List, Dict, Any, Tuple

class VoiceRepo:
    def __init__(self, conn):
        self.conn = conn

    def get_last_log_id(self) -> int:
        row = self.conn.execute(
            "SELECT value FROM kv WHERE key='voice.last_log_id'"
        ).fetchone()
        return int(row["value"]) if row else 0

    def set_last_log_id(self, log_id: int):
        with self.conn:
            self.conn.execute(
                "INSERT INTO kv(key,value) VALUES(?,?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                ("voice.last_log_id", str(int(log_id))),
            )

    def add_voice(
        self,
        vid: int,
        url: str,
        desc: str,
        user_id: Optional[int] = None,
    ):
        with self.conn:
            self.conn.execute(
                "INSERT OR IGNORE INTO voice_messages("
                "id,url,description,status,created_at,user_id"
                ") VALUES (?,?,?,?,?,?)",
                (
                    int(vid),
                    str(url),
                    str(desc),
                    "pending",
                    time.time(),
                    int(user_id) if user_id is not None else None,
                )
            )

    def get(self, vid: int):
        row = self.conn.execute(
            "SELECT * FROM voice_messages WHERE id=?",
            (int(vid),)
        ).fetchone()
        return dict(row) if row else None

    def list_pending(self, limit: int = 100, offset: int = 0):
        rows = self.conn.execute(
            "SELECT id, description, created_at, user_id "
            "FROM voice_messages "
            "WHERE status='pending' "
            "ORDER BY created_at DESC, id DESC "
            "LIMIT ? OFFSET ?",
            (int(limit), int(offset))
        ).fetchall()
        return [dict(r) for r in rows]

    def mark_playing(self, vid: int):
        with self.conn:
            self.conn.execute(
                "UPDATE voice_messages SET status='playing' WHERE id=?",
                (int(vid),)
            )

    def delete(self, vid: int):
        with self.conn:
            self.conn.execute(
                "DELETE FROM voice_messages WHERE id=?",
                (int(vid),)
            )

    def apply_sync_delta(self, sync_data: Dict[str, Any]) -> Tuple[int, int, int, List[int]]:
        """
        expected:
          sync_data = {"log_id": <int>, "added": [..], "deleted": [..]}

        returns:
          (new_log_id, added_count, deleted_count, deleted_ids)
        """
        new_log_id = int(sync_data.get("log_id") or 0)
        added: List[Dict[str, Any]] = sync_data.get("added") or []
        deleted: List[int] = sync_data.get("deleted") or []

        add_cnt = 0
        del_cnt = 0
        deleted_ids: List[int] = []

        with self.conn:  # transaction
            # 1) deleted
            if deleted:
                ids = [int(x) for x in deleted]
                q = ",".join(["?"] * len(ids))
                cur = self.conn.execute(
                    f"DELETE FROM voice_messages WHERE id IN ({q})",
                    ids
                )
                del_cnt = int(cur.rowcount or 0)
                deleted_ids = ids

            # 2) added upsert
            for it in added:
                if not isinstance(it, dict) or "id" not in it or "url" not in it:
                    continue

                vid = int(it["id"])
                url = str(it["url"])
                desc = str(it.get("description") or "")

                uid = it.get("userId")
                if uid is None:
                    uid = it.get("user_id") or it.get("sender_user_id")
                try:
                    uid = int(uid) if uid is not None else None
                except Exception:
                    uid = None

                now = time.time()

                self.conn.execute(
                    "INSERT INTO voice_messages("
                    "id,url,description,status,created_at,user_id"
                    ") VALUES (?,?,?,?,?,?) "
                    "ON CONFLICT(id) DO UPDATE SET "
                    "url=excluded.url, "
                    "description=excluded.description, "
                    "user_id=excluded.user_id, "
                    # url 바뀌면 pending으로
                    "status=CASE WHEN voice_messages.url != excluded.url THEN 'pending' ELSE voice_messages.status END, "
                    # created_at은 유지
                    "created_at=voice_messages.created_at",
                    (vid, url, desc, "pending", now, uid),
                )
                add_cnt += 1

            # 3) cursor update
            if new_log_id > 0:
                self.set_last_log_id(new_log_id)

        return new_log_id, add_cnt, del_cnt, deleted_ids
