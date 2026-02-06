# app/db/album_repo.py
import time
from typing import Any, Dict, List, Optional, Tuple

_LAST_LOG_KEY = "album.last_log_id"

VALID_DL_STATUS = {"pending", "downloading", "done", "failed"}

class AlbumRepo:
    """
    album 관련 SQL은 전부 여기로.
    호출부(서비스/루프/api)는 state.album_lock으로 동시성만 제어하면 됨.
    """
    def __init__(self, conn):
        self.conn = conn

    # ---- sync cursor ----
    def get_last_log_id(self) -> int:
        row = self.conn.execute("SELECT value FROM kv WHERE key=?", (_LAST_LOG_KEY,)).fetchone()
        if not row:
            return 0
        try:
            return int(str(row["value"]))
        except Exception:
            return 0

    def set_last_log_id(self, log_id: int) -> None:
        with self.conn:
            self.conn.execute(
                "INSERT INTO kv(key,value) VALUES(?,?) "
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                (_LAST_LOG_KEY, str(int(log_id))),
            )

    # ---- queries ----
    def get_photo(self, photo_id: int) -> Optional[Dict[str, Any]]:
        row = self.conn.execute(
            "SELECT p.id, p.url, p.description, p.taken_at, p.updated_at, p.user_id, "
            "d.status, d.local_path, d.bytes, d.sha256, d.retry_count, d.last_error, d.updated_at AS dl_updated_at "
            "FROM album_photos p "
            "LEFT JOIN album_downloads d ON d.photo_id = p.id "
            "WHERE p.id=?",
            (int(photo_id),),
        ).fetchone()
        return dict(row) if row else None

    def list_photos(self, limit: int = 200, offset: int = 0) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT p.id, p.url, p.description, p.taken_at, p.updated_at, p.user_id, "
            "d.status, d.local_path, d.bytes, d.sha256, d.retry_count, d.last_error "
            "FROM album_photos p "
            "LEFT JOIN album_downloads d ON d.photo_id = p.id "
            "ORDER BY (p.taken_at IS NULL) ASC, p.taken_at DESC, p.id DESC "
            "LIMIT ? OFFSET ?",
            (int(limit), int(offset)),
        ).fetchall()
        return [dict(r) for r in rows]

    def list_pending_downloads(
        self,
        limit: int = 50,
        now: float | None = None,
        *,
        rescue_sec: float = 600.0,   # stuck downloading rescue (10min)
    ) -> List[Dict[str, Any]]:
        if now is None:
            now = time.time()

        rescue_before = float(now) - float(rescue_sec)

        rows = self.conn.execute(
            "SELECT p.id, p.url, p.user_id, d.status, d.retry_count, d.last_error, d.next_try_at, d.updated_at "
            "FROM album_photos p "
            "JOIN album_downloads d ON d.photo_id = p.id "
            "WHERE ("
            "  d.status IN ('pending','failed') "
            "  OR (d.status='downloading' AND d.updated_at <= ?) "
            ") "
            "AND (d.next_try_at IS NULL OR d.next_try_at <= ?) "
            "ORDER BY d.updated_at ASC, p.id ASC "
            "LIMIT ?",
            (rescue_before, float(now), int(limit)),
        ).fetchall()
        return [dict(r) for r in rows]

    def list_all_photos_for_cache(self) -> list[dict]:
        rows = self.conn.execute(
            "SELECT p.id, p.url, p.description, p.taken_at, p.user_id, "
            "d.status AS dl_status, d.local_path "
            "FROM album_photos p "
            "LEFT JOIN album_downloads d ON d.photo_id = p.id "
            "ORDER BY (p.taken_at IS NULL) ASC, p.taken_at DESC, p.id DESC"
        ).fetchall()
        return [dict(r) for r in rows]

    # ---- download state updates ----
    def set_download_status(
        self,
        photo_id: int,
        status: str,
        local_path: Optional[str] = None,
        bytes_: Optional[int] = None,
        sha256: Optional[str] = None,
        last_error: Optional[str] = None,
        inc_retry: bool = False,
        next_try_at: Optional[float] = None,
    ) -> None:
        if status not in VALID_DL_STATUS:
            raise ValueError(f"invalid download status: {status}")

        now = time.time()

        with self.conn:
            # row 보장(사진이 존재해야 함)
            p = self.conn.execute("SELECT id FROM album_photos WHERE id=?", (int(photo_id),)).fetchone()
            if not p:
                raise KeyError(f"photo not found: {photo_id}")

            d = self.conn.execute("SELECT retry_count FROM album_downloads WHERE photo_id=?", (int(photo_id),)).fetchone()
            retry_count = int(d["retry_count"]) if d else 0
            if inc_retry:
                retry_count += 1

            if d is None:
                self.conn.execute(
                    "INSERT INTO album_downloads(photo_id,status,local_path,bytes,sha256,retry_count,last_error,updated_at,next_try_at) "
                    "VALUES(?,?,?,?,?,?,?,?,?)",
                    (
                        int(photo_id),
                        status,
                        local_path,
                        bytes_,
                        sha256,
                        retry_count,
                        last_error,
                        now,
                        next_try_at,
                    ),
                )
            else:
                self.conn.execute(
                    "UPDATE album_downloads SET status=?, local_path=COALESCE(?,local_path), "
                    "bytes=COALESCE(?,bytes), sha256=COALESCE(?,sha256), retry_count=?, "
                    "last_error=?, updated_at=?, next_try_at=COALESCE(?,next_try_at) "
                    "WHERE photo_id=?",
                    (
                        status,
                        local_path,
                        bytes_,
                        sha256,
                        retry_count,
                        last_error,
                        now,
                        next_try_at,
                        int(photo_id),
                    ),
                )

    # ---- core: apply sync delta ----
    def apply_sync_delta(self, sync_data: Dict[str, Any]) -> Tuple[int, int, int]:
        """
        서버 payload(data)를 받아 DB에 반영.
        expected:
          sync_data = {"log_id": <int>, "added": [..], "deleted": [..]}

        returns: (new_log_id, added_count, deleted_count)

        정책:
          - deleted는 album_photos에서 삭제(다운로드 row는 FK로 자동 삭제)
          - added는 upsert
          - url 변경 시, 기존 다운로드가 done 이더라도 pending으로 되돌려 재다운로드 유도(보수적)
          - last_log_id는 같은 트랜잭션에서 갱신
        """
        now = time.time()
        new_log_id = int(sync_data.get("log_id") or 0)
        added: List[Dict[str, Any]] = sync_data.get("added") or []
        deleted: List[int] = sync_data.get("deleted") or []

        add_cnt = 0
        del_cnt = 0

        with self.conn:  # transaction
            # 1) deleted
            if deleted:
                q = ",".join(["?"] * len(deleted))
                cur = self.conn.execute(f"DELETE FROM album_photos WHERE id IN ({q})", [int(x) for x in deleted])
                del_cnt = int(cur.rowcount or 0)

            # 2) added upsert
            for it in added:
                pid = int(it["id"])
                url = str(it["url"])
                desc = it.get("description")
                taken_at = it.get("takenAt")

                # user_id normalize
                uid = it.get("userId")
                if uid is None:
                    uid = it.get("user_id")
                try:
                    uid = int(uid) if uid is not None else None
                except Exception:
                    uid = None

                old = self.conn.execute("SELECT url FROM album_photos WHERE id=?", (pid,)).fetchone()
                old_url = old["url"] if old else None

                self.conn.execute(
                    "INSERT INTO album_photos(id,url,description,taken_at,updated_at,user_id) "
                    "VALUES(?,?,?,?,?,?) "
                    "ON CONFLICT(id) DO UPDATE SET "
                    "url=excluded.url, description=excluded.description, taken_at=excluded.taken_at, "
                    "updated_at=excluded.updated_at, user_id=excluded.user_id",
                    (pid, url, desc, taken_at, now, uid),
                )
                add_cnt += 1

                # download row 보장 + url 변경 시 pending
                d = self.conn.execute("SELECT status FROM album_downloads WHERE photo_id=?", (pid,)).fetchone()
                if d is None:
                    self.conn.execute(
                        "INSERT INTO album_downloads(photo_id,status,updated_at) VALUES(?,?,?)",
                        (pid, "pending", now),
                    )
                else:
                    if old_url is not None and old_url != url:
                        self.conn.execute(
                            "UPDATE album_downloads SET status='pending', last_error=NULL, updated_at=? WHERE photo_id=?",
                            (now, pid),
                        )

            # 3) cursor update
            if new_log_id > 0:
                self.set_last_log_id(new_log_id)

        return new_log_id, add_cnt, del_cnt
