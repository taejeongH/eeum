# device_store.py
import asyncio
import logging
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)

class DeviceStore:
    """
    JsonStateStore를 감싸는 얇은 래퍼.
    정책:
      - last_seen_ts, online=True 같은 '관측 값'은 메모리만 갱신(저장 X)
      - online, token가 '실제로 바뀔 때만' save()
    """
    def __init__(self, store):
        self.store = store
        self._save_lock = asyncio.Lock()
        self._id2kind = self._build_index()

    def _build_index(self) -> dict[str, str]:
        doc = self.store.get()
        out = {}
        for kind, m in doc.get("devices", {}).items():
            for device_id in m.keys():
                out[device_id] = kind
        return out

    def doc(self) -> Dict[str, Any]:
        return self.store.get()
    # ---------- read ----------
    def get_device_id(self):
        return self.doc().get("device_id", None)
    def get_kind(self, device_id: str) -> Optional[str]:
        return self._id2kind.get(device_id)
    
    def get_online(self, device_id: str) -> Optional[bool]:
        kind = self.get_kind(device_id)
        if not kind:
            logger.warning("[DeviceStore] unknown kind of sensor=%s", kind)
            return None
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if not dev:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return None
        return bool(dev.get("online"))
    
    def get_token(self) -> str | None:
        return self.doc().get("token")
    
    def build_pir_link(self) -> list[dict]:
        pir_map = (self.doc().get("devices", {}).get("pir", {}) or {})
        link = []
        for dev_id, info in pir_map.items():
            link.append({
                "id": dev_id,
                "alive": bool(info.get("online")),
            })
        return link
    # ---------- write (save only when changed) ----------
    async def async_mark_seen(self, device_id: str, ts: float) -> None:
        """이벤트/핑 수신 시 호출: online=True, last_seen_ts 갱신"""
        kind = self.get_kind(device_id)
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev is None:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return None
        
        dev["last_seen_ts"] = ts
        if dev.get("online") is not True:
            dev["online"] = True
            await self._async_save()
            
    async def async_set_offline(self, device_id: str) -> bool | None:
        kind = self.get_kind(device_id)
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev == None:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return None
        if dev.get("online") is False:
            return False
        dev["online"] = False
        await self._async_save()
        return True

    async def async_set_token(self, token: str) -> None:
        doc = self.doc()
        if doc.get("token") == token:
            return
        doc["token"] = token
        await self._async_save()

    async def _async_save(self) -> None:
        # 저장 충돌 방지
        async with self._save_lock:
            await asyncio.to_thread(self.store.save)
