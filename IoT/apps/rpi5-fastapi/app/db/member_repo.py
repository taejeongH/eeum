
import time
from typing import Any, Dict, List, Optional

class MemberRepo:
    def __init__(self, conn):
        self.conn = conn

    def get(self, user_id: int) -> Optional[Dict[str, Any]]:
        row = self.conn.execute(
            "SELECT user_id, name, profile_image_url, updated_at FROM members WHERE user_id=?",
            (int(user_id),),
        ).fetchone()
        return dict(row) if row else None

    def list_all(self) -> List[Dict[str, Any]]:
        rows = self.conn.execute(
            "SELECT user_id, name, profile_image_url, updated_at FROM members"
        ).fetchall()
        return [dict(r) for r in rows]

    def upsert_many_with_change_detection(
        self,
        members: List[Dict[str, Any]],
        *,
        on_changed=None,  
    ) -> int:
        """
        members: [{"user_id":int,"name":str,"profile_image_url":str}, ...]
        변경(이름/프로필URL) 있는 것만 반영.
        변경 시 on_changed(old_profile_url) 호출해서 캐시 삭제 트리거.
        """
        now = time.time()
        changed = 0

        with self.conn:
            for m in members:
                uid = m.get("user_id")
                if uid is None:
                    continue
                try:
                    uid = int(uid)
                except Exception:
                    continue

                name = str(m.get("name") or "")
                purl = str(m.get("profile_image_url") or "")

                old = self.conn.execute(
                    "SELECT name, profile_image_url FROM members WHERE user_id=?",
                    (uid,),
                ).fetchone()

                if old:
                    old_name = str(old["name"] or "")
                    old_purl = str(old["profile_image_url"] or "")
                    if old_name == name and old_purl == purl:
                        continue  

                    
                    if on_changed:
                        on_changed(old_purl or None)

                self.conn.execute(
                    "INSERT INTO members(user_id,name,profile_image_url,updated_at) VALUES(?,?,?,?) "
                    "ON CONFLICT(user_id) DO UPDATE SET "
                    "name=excluded.name, profile_image_url=excluded.profile_image_url, updated_at=excluded.updated_at",
                    (uid, name, purl, now),
                )
                changed += 1

        return changed
