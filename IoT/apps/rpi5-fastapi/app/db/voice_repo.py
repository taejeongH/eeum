# app/db/voice_repo.py
import time
from typing import Optional, List, Dict, Any, Tuple

VALID_VOICE_DL_STATUS = {"pending", "downloading", "done", "failed"}

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

    def get_download(self, voice_id: int) -> Optional[Dict[str, Any]]:
        row = self.conn.execute(
            "SELECT voice_id, status, local_path, bytes, sha256, retry_count, last_error, updated_at, next_try_at "
            "FROM voice_downloads WHERE voice_id=?",
            (int(voice_id),),
        ).fetchone()
        return dict(row) if row else None

    def list_pending(self, limit: int = 100, offset: int = 0):
        """
        UI용: pending voice + 다운로드 상태 join
        """
        rows = self.conn.execute(
            "SELECT v.id, v.description, v.created_at, v.user_id, "
            "d.status AS download_status, d.local_path, d.retry_count, d.last_error, d.next_try_at, d.updated_at AS dl_updated_at "
            "FROM voice_messages v "
            "LEFT JOIN voice_downloads d ON d.voice_id = v.id "
            "WHERE v.status='pending' "
            "ORDER BY v.created_at DESC, v.id DESC "
            "LIMIT ? OFFSET ?",
            (int(limit), int(offset))
        ).fetchall()
        return [dict(r) for r in rows]

    def list_pending_downloads(
        self,
        limit: int = 20,
        now: float | None = None,
        *,
        rescue_sec: float = 600.0,  # 10min
    ) -> List[Dict[str, Any]]:
        if now is None:
            now = time.time()
        rescue_before = float(now) - float(rescue_sec)

        rows = self.conn.execute(
            "SELECT v.id, v.url, v.description, v.user_id, "
            "d.status, d.retry_count, d.last_error, d.next_try_at, d.updated_at "
            "FROM voice_messages v "
            "JOIN voice_downloads d ON d.voice_id = v.id "
            "WHERE ("
            "  d.status IN ('pending','failed') "
            "  OR (d.status='downloading' AND d.updated_at <= ?) "
            ") "
            "AND (d.next_try_at IS NULL OR d.next_try_at <= ?) "
            "ORDER BY d.updated_at ASC, v.id ASC "
            "LIMIT ?",
            (rescue_before, float(now), int(limit)),
        ).fetchall()
        return [dict(r) for r in rows]

    def set_download_status(
        self,
        voice_id: int,
        status: str,
        *,
        local_path: Optional[str] = None,
        bytes_: Optional[int] = None,
        sha256: Optional[str] = None,
        last_error: Optional[str] = None,
        inc_retry: bool = False,
        next_try_at: Optional[float] = None,
    ) -> None:
        if status not in VALID_VOICE_DL_STATUS:
            raise ValueError(f"invalid voice download status: {status}")

        now = time.time()

        with self.conn:
            v = self.conn.execute("SELECT id FROM voice_messages WHERE id=?", (int(voice_id),)).fetchone()
            if not v:
                raise KeyError(f"voice not found: {voice_id}")

            d = self.conn.execute("SELECT retry_count FROM voice_downloads WHERE voice_id=?", (int(voice_id),)).fetchone()
            retry_count = int(d["retry_count"]) if d else 0
            if inc_retry:
                retry_count += 1

            if d is None:
                self.conn.execute(
                    "INSERT INTO voice_downloads(voice_id,status,local_path,bytes,sha256,retry_count,last_error,updated_at,next_try_at) "
                    "VALUES(?,?,?,?,?,?,?,?,?)",
                    (int(voice_id), status, local_path, bytes_, sha256, retry_count, last_error, now, next_try_at),
                )
            else:
                self.conn.execute(
                    "UPDATE voice_downloads SET status=?, local_path=COALESCE(?,local_path), "
                    "bytes=COALESCE(?,bytes), sha256=COALESCE(?,sha256), retry_count=?, "
                    "last_error=?, updated_at=?, next_try_at=COALESCE(?,next_try_at) "
                    "WHERE voice_id=?",
                    (status, local_path, bytes_, sha256, retry_count, last_error, now, next_try_at, int(voice_id)),
                )

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

    def apply_sync_delta(self, sync_data: Dict[str, Any]) -> Tuple[int, int, int, List[int], List[int]]:
        """
        expected:
          sync_data = {"log_id": <int>, "added": [..], "deleted": [..]}

        returns:
          (new_log_id, added_count, deleted_count, deleted_ids, inserted_ids)

        inserted_ids:
          이번 sync에서 "실제로 새로 추가된" voice id 목록
          => SSE 중복 emit 방지용
        """
        new_log_id = int(sync_data.get("log_id") or 0)
        added: List[Dict[str, Any]] = sync_data.get("added") or []
        deleted: List[int] = sync_data.get("deleted") or []

        add_cnt = 0
        del_cnt = 0
        deleted_ids: List[int] = []
        inserted_ids: List[int] = []

        now = time.time()

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

            # 2) added upsert + inserted 판정
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

                # 신규 여부 판정: INSERT OR IGNORE 후 rowcount 체크
                cur = self.conn.execute(
                    "INSERT OR IGNORE INTO voice_messages("
                    "id,url,description,status,created_at,user_id"
                    ") VALUES (?,?,?,?,?,?)",
                    (vid, url, desc, "pending", now, uid),
                )
                if int(cur.rowcount or 0) > 0:
                    inserted_ids.append(vid)
                else:
                    # 기존이면 내용만 갱신(created_at/status는 유지)
                    self.conn.execute(
                        "UPDATE voice_messages SET url=?, description=?, user_id=? WHERE id=?",
                        (url, desc, uid, vid),
                    )

                add_cnt += 1

                # download row 보장
                d = self.conn.execute("SELECT status FROM voice_downloads WHERE voice_id=?", (vid,)).fetchone()
                if d is None:
                    self.conn.execute(
                        "INSERT INTO voice_downloads(voice_id,status,updated_at) VALUES(?,?,?)",
                        (vid, "pending", now),
                    )

            # 3) cursor update
            if new_log_id > 0:
                self.set_last_log_id(new_log_id)

        return new_log_id, add_cnt, del_cnt, deleted_ids, inserted_ids
