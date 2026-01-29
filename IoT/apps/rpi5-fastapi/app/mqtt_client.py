import json
import ssl
import time
import uuid
from queue import Queue
from typing import Callable,Optional,Dict,Any,List,Tuple
import paho.mqtt.client as mqtt
from .config import (
    SERVER_HOST,
    SERVER_PORT,
    USERNAME,
    PASSWORD,
    CLIENT_ID,
    SUB_TOPICS
)

class TTLCache:
    def __init__(self, ttl_seconds: int = 120, max_size: int = 5000):
        self.ttl = ttl_seconds
        self.max_size = max_size
        self._store: Dict[str, float] = {}  # msg_id -> expire_ts

    def seen(self, key: Optional[str]) -> bool:
        """True면 중복(드랍), False면 신규(처리)"""
        if not key:
            return False

        now = time.time()
        exp = self._store.get(key)
        if exp is not None and exp >= now:
            return True

        self._store[key] = now + self.ttl

        # 가끔 청소
        if len(self._store) > self.max_size:
            self._prune(now)

        return False

    def _prune(self, now: float) -> None:
        expired = [k for k, exp in self._store.items() if exp < now]
        for k in expired:
            self._store.pop(k, None)

        # 그래도 크면 오래된 것부터 제거
        if len(self._store) > self.max_size:
            for k, _ in sorted(self._store.items(), key=lambda x: x[1])[: len(self._store) - self.max_size]:
                self._store.pop(k, None)



class MqttClient:
    """
    - MQTT 네트워크 처리는 paho 내부 스레드(loop_start)에서 수행
    - on_message에서는 무조건 '가볍게' 큐에 넣기만 한다
    - QoS1 중복 가능 -> msg_id 기반 TTL dedupe
    - publish 시 msg_id 없으면 UUID v4 자동 추가
    """

    def __init__(
            self,
            inbound_queue: "Queue[Tuple[str, Dict[str, Any]]]",
            broker:str = SERVER_HOST,
            port:int = SERVER_PORT,
            username:str = USERNAME,
            password:str = PASSWORD,
            client_id:str = CLIENT_ID,
            subscribe_topics: Optional[List[str]] = None,
            token: Optional[str] = None,
            link_getter: Optional[Callable[[], List[Dict[str, Any]]]] = None,
            status_topic: str = "eeum/status",
            cafile: Optional[str] =None,
            keepalive:int = 60,
            dedupe_ttl_sec: int = 120,
            dedupe_max_size: int = 5000,
        ):
        self.broker = broker
        self.port = port
        self.subscribe_topics = subscribe_topics or list(SUB_TOPICS)
        self.inbound_queue = inbound_queue
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

        # TLS
        if cafile:
            self.client.tls_set(ca_certs=cafile, tls_version=ssl.PROTOCOL_TLSv1_2)
        else:
            self.client.tls_set(tls_version=ssl.PROTOCOL_TLSv1_2)

        # 재연결 백오프
        self.client.reconnect_delay_set(min_delay=1, max_delay=30)

        # LWT: 비정상 종료 시 브로커가 offline publish
        self.client.will_set(
            topic=self.status_topic,
            payload=json.dumps(self._make_offline_payload(), ensure_ascii=False),
            qos=1,
            retain=True,
        )
        
        # callbacks
        self.client.on_connect = self._on_connect
        self.client.on_message = self._on_message
        self.client.on_disconnect = self._on_disconnect

        self.dedupe = TTLCache(ttl_seconds=dedupe_ttl_sec, max_size=dedupe_max_size)

    def set_token(self, token: str) -> None:
        """토큰 갱신. 연결 중이면 online 상태를 한 번 더 갱신 발행(retain)"""
        self.token = token
        if self._connected:
            self.publish_online(retain=True)

    def activate(self) -> None:
        """연결 시작(토큰 있을 때만 state에서 호출하도록)"""
        if self.token is None:
            return
        if self._active:
            return
        self._active = True

        if not self._started:
            self.client.connect(self.broker, self.port, self.keepalive)
            self.client.loop_start()
            self._started = True

    def deactivate(self) -> None:
        """연결 종료(정상 disconnect → LWT 발동 안 함)"""
        self._active = False
        if self._started:
            self.client.loop_stop()
            self._started = False
        try:
            self.client.disconnect()
        except Exception:
            pass

    def publish_json(self, topic: str, payload: Dict[str, Any], qos: int = 1, retain: bool = False) -> str:
        """msg_id 없으면 자동 생성해서 publish"""
        msg_id = payload.get("msg_id")
        if not msg_id:
            msg_id = str(uuid.uuid4())
            payload["msg_id"] = msg_id

        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.client.publish(topic, data, qos=qos, retain=retain)
        return msg_id

    def publish_online(self, retain: bool = True) -> None:
        """현재 token 기준으로 online 상태 발행 (재연결/토큰 갱신 시 호출)"""
        if self.token is None:
            return
        payload = self._make_online_payload()
        self.publish_json(self.status_topic, payload, qos=1, retain=retain)

    def publish_event(self, payload: dict) -> str:
        payload.setdefault("detected_at", time.time())
        payload.setdefault("token", self.token)
        payload.setdefault("serial_number", self.client_id)
        return self.publish_json("eeum/event", payload, qos=1, retain=False)

    def publish_update(self, payload: dict) -> str:
        payload.setdefault("updated_at", time.time())
        payload.setdefault("token", self.token)
        return self.publish_json("eeum/update", payload, qos=1, retain=False)

    def publish_response(self, payload: dict) -> str:
        payload.setdefault("detected_at", time.time())
        payload.setdefault("token", self.token)
        payload.setdefault("serial_number", self.client_id)
        return self.publish_json("eeum/response", payload, qos=1, retain=False)
    # ---------- payload builders ----------
    def _make_offline_payload(self) -> Dict[str, Any]:
        return {
            "serial_number": self.client_id,
            "status": "offline",
            "detected_at": time.time(),
        }

    def _make_online_payload(self) -> Dict[str, Any]:
        try:
            link = self.link_getter() if self.link_getter else []
        except Exception:
            link = []

        return {
            "serial_number": self.client_id,
            "status": "online",
            "link" : link,
            "detected_at": time.time(),
            "token": self.token,
        }

    # ---------- callbacks ----------
    def _on_connect(self, client, userdata, flags, reason_code, properties):
        self._connected = (reason_code == 0)
        print("[mqtt] connected:", reason_code)

        if not self._active:
            print("[mqtt] not active -> disconnect")
            client.disconnect()
            return

        if reason_code == 0:
            self.publish_online(retain=True)

            for t in self.subscribe_topics:
                client.subscribe(t, qos=1)
                print("[mqtt] subscribed:", t)
        else:
            print("[mqtt] connect failed:", reason_code)

    def _on_disconnect(self, client, userdata, reason_code, properties=None, *args):
        self._connected = False
        print("[mqtt] disconnected:", reason_code)

    def _on_message(self, client, userdata, msg):
        try:
            payload = json.loads(msg.payload.decode("utf-8"))
        except Exception as e:
            print("[mqtt] bad json:", e, "topic:", msg.topic)
            return

        # QoS1 중복 대비 (옵션)
        if self.dedupe is not None:
            msg_id = payload.get("msg_id")
            if msg_id and self.dedupe.seen(msg_id):
                print("[mqtt] duplicate drop:", msg_id, "topic:", msg.topic)
                return

        self.inbound_queue.put((msg.topic, payload))