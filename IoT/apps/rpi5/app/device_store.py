import asyncio
import logging
from typing import Any, Dict, Optional, Tuple

logger = logging.getLogger(__name__)

class DeviceStore:
    """
    JsonStateStore를 감싸는 얇은 래퍼입니다.

    정책:
    - last_seen_ts는 관측 값이므로 메모리만 갱신합니다.
    - online/token이 실제로 바뀔 때만 save()를 수행합니다.
    """

    def __init__(self, store, token_store):
        """
        :param store: device.json을 담당하는 JsonStateStore
        :param token_store: token.json을 담당하는 JsonStateStore
        """
        if token_store is None:
            logger.error("[DeviceStore] token_store is required (token.json).")
            raise RuntimeError("token_store is required (token must be stored in token.json)")

        self.store = store
        self.token_store = token_store

        self._save_lock = asyncio.Lock()
        self._token_lock = asyncio.Lock()
        self._device_kind_by_id = self._build_device_kind_index()

        try:
            token = self.token_store.get().get("token")
            logger.info("[DeviceStore] initialized: token_present=%s", bool(token))
        except Exception:
            logger.exception("[DeviceStore] initialized but failed to read token_store")

    def _build_device_kind_index(self) -> dict[str, str]:
        """
        device_id -> kind 인덱스를 생성합니다.

        :return: 인덱스 dict
        """
        doc = self.store.get()
        index: dict[str, str] = {}

        for kind, devices in (doc.get("devices", {}) or {}).items():
            if not isinstance(devices, dict):
                continue
            for device_id in devices.keys():
                index[device_id] = kind

        return index

    def doc(self) -> Dict[str, Any]:
        """
        device.json 전체 문서를 반환합니다.

        :return: 문서 dict
        """
        return self.store.get()

    def get_kind(self, device_id: str) -> Optional[str]:
        """
        device_id의 kind를 반환합니다.

        :param device_id: 디바이스 ID
        :return: kind 또는 None
        """
        return self._device_kind_by_id.get(device_id)

    def _find_device_entry(self, device_id: str) -> Tuple[Optional[str], Optional[dict]]:
        """
        device_id로 (kind, device dict)를 찾습니다.

        :param device_id: 디바이스 ID
        :return: (kind, device_dict) / 실패 시 (None, None)
        """
        kind = self.get_kind(device_id)
        if not kind:
            logger.warning("[DeviceStore] unknown kind of device_id=%s", device_id)
            return None, None

        device = self.doc().get("devices", {}).get(kind, {}).get(device_id)
        if not device:
            logger.warning("[DeviceStore] unknown device_id=%s", device_id)
            return None, None

        return kind, device

    def get_online(self, device_id: str) -> Optional[bool]:
        """
        device_id의 online 상태를 반환합니다.

        :param device_id: 디바이스 ID
        :return: online 상태 또는 None(unknown)
        """
        _, device = self._find_device_entry(device_id)
        if device is None:
            return None
        return bool(device.get("online"))

    def get_token(self) -> Optional[str]:
        """
        저장된 토큰을 반환합니다.

        :return: 토큰 문자열 또는 None
        """
        token_doc = self.token_store.get()
        token = token_doc.get("token")
        if token is None:
            logger.debug("[DeviceStore] token is None")
        return token

    def build_pir_link(self) -> list[dict]:
        """
        PIR 디바이스 링크 정보를 생성합니다(online 상태만 포함).

        :return: [{"id": "...", "alive": bool}, ...]
        """
        pir_devices = (self.doc().get("devices", {}).get("pir", {}) or {})
        link: list[dict] = []

        for device_id, info in pir_devices.items():
            if not isinstance(info, dict):
                continue
            link.append({"id": device_id, "alive": bool(info.get("online"))})

        return link

    async def async_set_token(self, token: str) -> None:
        """
        토큰을 변경하고 저장합니다(변경 없으면 저장하지 않음).

        :param token: 새 토큰
        :return: None
        """
        async with self._token_lock:
            token_doc = self.token_store.get()
            old_token = token_doc.get("token")
            if old_token == token:
                logger.debug("[DeviceStore] token unchanged")
                return

            token_doc["token"] = token
            await asyncio.to_thread(self.token_store.save)

            logger.info(
                "[DeviceStore] token updated: was_present=%s now_present=%s len=%s",
                bool(old_token),
                True,
                len(token) if token else 0,
            )

    async def async_mark_seen(self, device_id: str, ts: float) -> bool:
        """
        이벤트/핑 수신 시 호출합니다.
        - last_seen_ts 갱신
        - offline -> online 전이이면 online=True로 저장(save)합니다.

        :param device_id: 디바이스 ID
        :param ts: 감지 시각(epoch seconds)
        :return: offline/unknown -> online 전이가 발생했으면 True
        """
        _, device = self._find_device_entry(device_id)
        if device is None:
            return False

        device["last_seen_ts"] = ts

        became_online = (device.get("online") is not True)
        if became_online:
            device["online"] = True
            await self._save_device_doc()

        return became_online

    async def async_set_offline(self, device_id: str) -> Optional[bool]:
        """
        device_id를 offline으로 설정하고 저장합니다.

        :param device_id: 디바이스 ID
        :return: None(unknown), False(이미 offline), True(변경됨)
        """
        _, device = self._find_device_entry(device_id)
        if device is None:
            return None

        if device.get("online") is False:
            return False

        device["online"] = False
        await self._save_device_doc()
        return True

    async def _save_device_doc(self) -> None:
        """
        device.json 저장을 직렬화합니다.

        :return: None
        """
        async with self._save_lock:
            await asyncio.to_thread(self.store.save)
        logger.debug("[DeviceStore] device.json saved")