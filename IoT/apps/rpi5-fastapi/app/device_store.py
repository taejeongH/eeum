# device_store.py
import asyncio
from typing import Any, Dict, Optional

class DeviceStore:
    """
    JsonStateStore를 감싸는 얇은 래퍼.
    정책:
      - last_seen_ts, online=True 같은 '관측 값'은 메모리만 갱신(저장 X)
      - online/location/group_id가 '실제로 바뀔 때만' save()
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
    def get_location(self, device_id: str):
        kind = self._id2kind.get(device_id)
        return self.doc().get("devices", {}).get(kind, {}).get(device_id, {}).get("location")

    def get_group_id(self) -> Optional[int]:
        return self.doc().get("group_id")

    def get_kind(self, device_id: str) -> Optional[str]:
        return self._id2kind.get(device_id)

    # ---------- write (save only when changed) ----------
    async def async_mark_seen(self, device_id: str, ts: float) -> None:
        """이벤트/핑 수신 시 호출: online=True, last_seen_ts 갱신 (저장 안 함)"""
        kind = self.get_kind(device_id)
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev is None:
            return None
        
        dev["last_seen_ts"] = ts
        if dev.get("online") is not True:
            dev["online"] = True
            await self._async_save()
            
    async def async_set_offline(self, device_id: str) -> bool | None:
        kind = self.get_kind(device_id)
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev == None:
            print(f"[DeviceStore] unknown device_id={device_id}")
            return None
        if dev.get("online") is False:
            return False
        dev["online"] = False
        await self._async_save()
        return True

    async def async_set_location(self, device_id: str, location: Optional[str]) -> bool:
        kind = self.get_kind(device_id)
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev == None:
            print(f"[DeviceStore] unknown device_id={device_id}")
            return None
        if dev.get("location") == location:
            return False
        dev["location"] = location
        await self._async_save()
        return True

    async def async_set_group_id(self, group_id):
        d = self.doc()
        if d.get("group_id") == group_id:
            return False
        d["group_id"] = group_id
        await self._async_save()
        return True

    async def set_locations_bulk(self, mapping: dict[str, Optional[str]]) -> bool:
        doc = self.doc()
        devices = doc.get("devices", {})

        changed = False
        for device_id, loc in mapping.items():
            kind = self.get_kind(device_id)
            if not kind:
                print(f"[DeviceStore] unknown device_id={device_id}")
                continue
            dev = devices.get(kind, {}).get(device_id)
            if dev is None:
                continue
            if dev.get("location") != loc:
                dev["location"] = loc
                changed = True
        if changed:
            await self._async_save()
        return changed
    
    async def _async_save(self) -> None:
        # 저장 충돌 방지
        async with self._save_lock:
            await asyncio.to_thread(self.store.save)
