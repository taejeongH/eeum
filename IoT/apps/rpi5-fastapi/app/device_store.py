
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
    def __init__(self, store, token_store):
        if token_store is None:
            logger.error("[DeviceStore] token_store is required (token.json).")
            raise RuntimeError("token_store is required (token must be stored in token.json)")
        self.store = store
        self.token_store = token_store
        self._save_lock = asyncio.Lock()
        self._token_lock = asyncio.Lock()
        self._id2kind = self._build_index()

        
        try:
            tok = self.token_store.get().get("token")
            logger.info("[DeviceStore] initialized: token_present=%s", bool(tok))
        except Exception:
            logger.exception("[DeviceStore] initialized but failed to read token_store")

    def _build_index(self) -> dict[str, str]:
        doc = self.store.get()
        out = {}
        for kind, m in doc.get("devices", {}).items():
            for device_id in m.keys():
                out[device_id] = kind
        return out

    def doc(self) -> Dict[str, Any]:
        return self.store.get()
    
    def get_kind(self, device_id: str) -> Optional[str]:
        return self._id2kind.get(device_id)
    
    def get_online(self, device_id: str) -> Optional[bool]:
        kind = self.get_kind(device_id)
        if not kind:
            logger.warning("[DeviceStore] unknown kind of device_id=%s", device_id)
            return None
        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if not dev:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return None
        return bool(dev.get("online"))
    
    def get_token(self) -> str | None:
        tok = self.token_store.get().get("token")
        if tok is None:
            logger.debug("[DeviceStore] token is None")
        return self.token_store.get().get("token")
    
    def build_pir_link(self) -> list[dict]:
        pir_map = (self.doc().get("devices", {}).get("pir", {}) or {})
        link = []
        for dev_id, info in pir_map.items():
            link.append({
                "id": dev_id,
                "alive": bool(info.get("online")),
            })
        return link
    
    async def async_set_token(self, token: str) -> None:
        async with self._token_lock:
            tdoc = self.token_store.get()
            old = tdoc.get("token")
            if old == token:
                logger.debug("[DeviceStore] token unchanged")
                return

            tdoc["token"] = token
            await asyncio.to_thread(self.token_store.save)

            logger.info(
                "[DeviceStore] token updated: was_present=%s now_present=%s len=%s",
                bool(old), True, len(token) if token else 0
            )

    async def async_mark_seen(self, device_id: str, ts: float) -> bool:
        """이벤트/핑 수신 시 호출: last_seen_ts 갱신 + (offline->online 전이면 persist)
        return: online 상태가 False/None -> True로 '전이'됐으면 True
        """
        kind = self.get_kind(device_id)
        if not kind:
            logger.warning("[DeviceStore] unknown kind of device_id=%s", device_id)
            return False

        dev = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if dev is None:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return False

        dev["last_seen_ts"] = ts

        became_online = (dev.get("online") is not True)
        if became_online:
            dev["online"] = True
            await self._async_save()

        return became_online
            
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

    async def _async_save(self) -> None:
        
        async with self._save_lock:
            await asyncio.to_thread(self.store.save)
        logger.debug("[DeviceStore] device.json saved")
