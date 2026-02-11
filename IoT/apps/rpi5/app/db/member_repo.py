from typing import Any, Dict, List, Optional
from app.profile_cache import cache_key_url
from app.sync_utils import now_ts

class MemberRepo:
    def __init__(self, conn):
        self.conn = conn

    def get(self, user_id: int) -> Optional[Dict[str, Any]]:
        """
        members 단건을 조회합니다.

        :param user_id: 사용자 ID
        :return: 레코드(dict) 또는 None
        """
        row = self.conn.execute(
            "SELECT user_id, name, profile_image_url, updated_at FROM members WHERE user_id=?",
            (int(user_id),),
        ).fetchone()
        return dict(row) if row else None

    def list_all(self) -> List[Dict[str, Any]]:
        """
        members 전체를 조회합니다.

        :return: 목록
        """
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
        변경(이름/프로필URL)이 있는 항목만 upsert 합니다.
        변경이 발생하면 on_changed(old_profile_url)를 호출할 수 있습니다.

        :param members: [{"user_id":int,"name":str,"profile_image_url":str}, ...]
        :param on_changed: 변경 시 호출할 콜백(이전 profile_image_url 전달)
        :return: 변경되어 반영된 개수
        """
        now = now_ts()
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
                    old_key = cache_key_url(old_purl)
                    new_key = cache_key_url(purl)

                    if old_name == name and old_key == new_key:
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