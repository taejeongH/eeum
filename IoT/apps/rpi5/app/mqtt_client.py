import asyncio
import json
import logging
import ssl
import uuid
from typing import Any, Callable, Dict, List, Optional, Tuple
import paho.mqtt.client as mqtt
from app.config import CLIENT_ID, PASSWORD, SERVER_HOST, SERVER_PORT, SUB_TOPICS, USERNAME
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

class TTLCache:
    """
    msg_id 기반 중복 처리(드랍)를 위한 TTL 캐시입니다.
    """

    def __init__(self, ttl_seconds: int = 120, max_size: int = 5000):
        """
        :param ttl_seconds: 중복으로 간주할 TTL(초)
        :param max_size: 캐시 최대 키 수
        """
        self.ttl_seconds = ttl_seconds
        self.max_size = max_size
        self._expire_at_by_key: Dict[str, float] = {}

    def seen(self, key: Optional[str]) -> bool:
        """
        key가 TTL 내에 이미 처리된 적이 있는지 확인합니다.

        :param key: msg_id
        :return: True면 중복(드랍), False면 신규(처리)
        """
        if not key:
            return False

        now = now_ts()
        expire_at = self._expire_at_by_key.get(key)
        if expire_at is not None and expire_at >= now:
            return True

        self._expire_at_by_key[key] = now + self.ttl_seconds

        if len(self._expire_at_by_key) > self.max_size:
            self._prune(now)

        return False

    def _prune(self, now: float) -> None:
        """
        만료 항목을 제거하고, 여전히 크면 오래된 것부터 제거합니다.

        :param now: 현재 시각
        :return: None
        """
        expired_keys = [k for k, exp in self._expire_at_by_key.items() if exp < now]
        for k in expired_keys:
            self._expire_at_by_key.pop(k, None)

        if len(self._expire_at_by_key) <= self.max_size:
            return

        overflow = len(self._expire_at_by_key) - self.max_size
        for k, _ in sorted(self._expire_at_by_key.items(), key=lambda x: x[1])[:overflow]:
            self._expire_at_by_key.pop(k, None)

class MqttClient:
    """
    - 네트워크 처리는 paho 내부 스레드(loop_start)에서 수행합니다.
    - on_message에서는 큐에 넣기만 하고, 파싱/처리는 asyncio 쪽에서 수행합니다.
    - QoS1 중복 가능성을 고려해 msg_id 기반 TTL dedupe를 지원합니다.
    """

    def __init__(
        self,
        inbound_queue: "asyncio.Queue[Tuple[str, Dict[str, Any]]]",
        loop: asyncio.AbstractEventLoop,
        broker: str = SERVER_HOST,
        port: int = SERVER_PORT,
        username: str = USERNAME,
        password: str = PASSWORD,
        client_id: str = CLIENT_ID,
        subscribe_topics: Optional[List[str]] = None,
        token: Optional[str] = None,
        link_getter: Optional[Callable[[], List[Dict[str, Any]]]] = None,
        status_topic: str = "eeum/status",
        cafile: Optional[str] = None,
        keepalive: int = 60,
        dedupe_ttl_sec: int = 120,
        dedupe_max_size: int = 5000,
    ):
        """
        :param inbound_queue: (topic, payload) 수신 큐
        :param loop: asyncio event loop
        :param broker: MQTT broker host
        :param port: MQTT broker port
        :param username: MQTT username
        :param password: MQTT password
        :param client_id: client id
        :param subscribe_topics: 구독 토픽 리스트
        :param token: device token
        :param link_getter: online payload에 포함할 link 생성 함수
        :param status_topic: online/offline 상태 publish 토픽
        :param cafile: TLS CA 파일 경로
        :param keepalive: keepalive seconds
        :param dedupe_ttl_sec: dedupe TTL
        :param dedupe_max_size: dedupe 최대 크기
        """
        self.broker = broker
        self.port = port
        self.subscribe_topics = subscribe_topics or list(SUB_TOPICS)
        self.inbound_queue = inbound_queue
        self.loop = loop
        self.keepalive = keepalive

        self.token = token
        self.status_topic = status_topic.rstrip("/")
        self.client_id = client_id
        self.link_getter = link_getter

        self._active = False
        self._started = False
        self._connected = False

        self.client = mqtt.Client(
            client_id=client_id,
            protocol=mqtt.MQTTv311,
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
        )
        self.client.enable_logger()
        self.client.username_pw_set(username, password)

        if cafile:
            self.client.tls_set(ca_certs=cafile, tls_version=ssl.PROTOCOL_TLSv1_2)
        else:
            self.client.tls_set(tls_version=ssl.PROTOCOL_TLSv1_2)

        self.client.reconnect_delay_set(min_delay=1, max_delay=30)
        self.client.will_set(
            topic=self.status_topic,
            payload=json.dumps(self._build_offline_payload(), ensure_ascii=False),
            qos=1,
            retain=True,
        )

        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        self.dedupe = TTLCache(ttl_seconds=dedupe_ttl_sec, max_size=dedupe_max_size)

    def set_token(self, token: str) -> None:
        """
        토큰을 갱신합니다. 연결 중이라면 online 상태를 즉시 갱신합니다.

        :param token: 새 토큰
        :return: None
        """
        self.token = token
        if self._connected:
            self.publish_online(retain=True)

    def activate(self) -> None:
        """
        MQTT 연결을 시작합니다(토큰이 있을 때만 활성화).

        :return: None
        """
        if self.token is None or self._active:
            return

        self._active = True
        if self._started:
            return

        self.client.connect_async(self.broker, self.port, self.keepalive)
        self.client.loop_start()
        self._started = True
        logger.info("[mqtt] activate: connect_async started broker=%s port=%s", self.broker, self.port)

    def deactivate(self) -> None:
        """
        MQTT 연결을 중지합니다.

        :return: None
        """
        self._active = False
        if not self._started:
            return

        try:
            self.client.disconnect()
        except Exception:
            pass

        self.client.loop_stop(force=True)
        self._started = False
        self._connected = False
        logger.info("[mqtt] deactivated")

    def publish_json(self, topic: str, payload: Dict[str, Any], qos: int = 1, retain: bool = False) -> str:
        """
        payload에 msg_id가 없으면 생성해서 publish 합니다.

        :param topic: publish topic
        :param payload: payload dict
        :param qos: QoS
        :param retain: retain 여부
        :return: 사용된 msg_id
        """
        safe_payload = dict(payload or {})
        msg_id = safe_payload.get("msg_id") or str(uuid.uuid4())
        safe_payload["msg_id"] = msg_id

        data = json.dumps(safe_payload, ensure_ascii=False).encode("utf-8")
        self.client.publish(topic, data, qos=qos, retain=retain)
        return str(msg_id)

    def publish_online(self, retain: bool = True) -> None:
        """
        online 상태를 발행합니다.

        :param retain: retain 여부
        :return: None
        """
        if self.token is None:
            return

        try:
            self.publish_json(self.status_topic, self._build_online_payload(), qos=1, retain=retain)
        except Exception:
            logger.exception("[mqtt] publish_online failed")

    def publish_event(self, payload: dict) -> str:
        """
        이벤트 토픽으로 payload를 발행합니다.

        :param payload: 이벤트 payload
        :return: msg_id
        """
        payload.setdefault("detected_at", now_ts())
        payload.setdefault("token", self.token)
        payload.setdefault("serial_number", self.client_id)
        return self.publish_json("eeum/event", payload, qos=1, retain=False)

    def publish_response(self, payload: dict) -> str:
        """
        response 토픽으로 payload를 발행합니다.

        :param payload: response payload
        :return: msg_id
        """
        payload.setdefault("detected_at", now_ts())
        payload.setdefault("token", self.token)
        payload.setdefault("serial_number", self.client_id)
        return self.publish_json("eeum/response", payload, qos=1, retain=False)

    def _build_offline_payload(self) -> Dict[str, Any]:
        """
        LWT(비정상 종료 시) 발행될 offline payload를 생성합니다.

        :return: payload dict
        """
        return {"serial_number": self.client_id, "status": "offline", "detected_at": now_ts()}

    def _build_online_payload(self) -> Dict[str, Any]:
        """
        online payload를 생성합니다.

        :return: payload dict
        """
        try:
            link = self.link_getter() if self.link_getter else []
        except Exception:
            link = []

        return {
            "serial_number": self.client_id,
            "status": "online",
            "link": link,
            "detected_at": now_ts(),
            "token": self.token,
        }

    def _on_connect(self, client, userdata, flags, reason_code, properties):
        self._connected = (reason_code == 0)
        logger.info("[mqtt] connected rc=%s", reason_code)

        if not self._active:
            client.disconnect()
            return

        if reason_code != 0:
            logger.info("[mqtt] connect failed rc=%s", reason_code)
            return

        self.publish_online(retain=True)
        for topic in self.subscribe_topics:
            client.subscribe(topic, qos=1)
            logger.debug("[mqtt] subscribed: %s", topic)

    def _on_disconnect(self, client, userdata, reason_code, properties=None, *args):
        self._connected = False
        logger.info("[mqtt] disconnected rc=%s", reason_code)

    def _on_message(self, client, userdata, msg):
        if not self.loop.is_running():
            return

        try:
            payload = json.loads(msg.payload.decode("utf-8"))
        except Exception:
            logger.warning("[mqtt] bad json topic=%s", msg.topic, exc_info=True)
            return

        msg_id = payload.get("msg_id")
        if msg_id and self.dedupe.seen(str(msg_id)):
            logger.debug("[mqtt] duplicate drop msg_id=%s topic=%s", msg_id, msg.topic)
            return

        self._enqueue_inbound((msg.topic, payload))

    def _enqueue_inbound(self, item: Tuple[str, Dict[str, Any]]) -> None:
        """
        paho thread -> asyncio queue로 안전하게 enqueue 합니다.
        큐가 꽉 차면 가장 오래된 1개를 버리고 넣습니다.

        :param item: (topic, payload)
        :return: None
        """

        def put_nowait_drop_oldest() -> None:
            try:
                self.inbound_queue.put_nowait(item)
                return
            except asyncio.QueueFull:
                try:
                    self.inbound_queue.get_nowait()
                except Exception:
                    pass
                try:
                    self.inbound_queue.put_nowait(item)
                except Exception:
                    pass
            except Exception:
                pass

        try:
            self.loop.call_soon_threadsafe(put_nowait_drop_oldest)
        except Exception:
            logger.warning("[mqtt] enqueue failed", exc_info=True)