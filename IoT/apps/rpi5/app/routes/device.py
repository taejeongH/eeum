from typing import Any, Dict, Optional
from fastapi import FastAPI
from pydantic import BaseModel
from app.api_common import queue_put_drop_oldest
from app.mqtt_client import MqttClient
from app.state import Event, MonitorState
from app.sync_gate import schedule_initial_sync
from app.sync_utils import now_ts

class EventIn(BaseModel):
    kind: str
    device_id: str
    data: Dict[str, Any]
    detected_at: Optional[float] = None

class TokenReq(BaseModel):
    token: str

class DevicePingIn(BaseModel):
    device_id: str
    kind: str = "pir"
    ts: float | None = None

def register(app: FastAPI, state: MonitorState) -> None:
    """
    디바이스 관련 API 라우트를 등록합니다.
    :param app: FastAPI
    :param state: MonitorState
    :return: None
    """
    @app.get("/ping")
    def ping():
        return {"ok": True}

    @app.post("/api/device/ping")
    async def device_ping(body: DevicePingIn):
        device_id = (body.device_id or "").strip()
        if not device_id:
            return {"ok": False, "code": "bad_request", "message": "device_id required"}

        now = now_ts()
        if state.device_store:
            await state.device_store.async_mark_seen(device_id, now)

        state.last_event_by_device[device_id] = {
            "kind": body.kind,
            "device_id": device_id,
            "data": {"event": "ping"},
            "detected_at": now,
        }
        return {"ok": True, "device_id": device_id, "ts": now}

    @app.post("/eeum/token")
    async def set_token(req: TokenReq):
        """토큰 저장 후 MQTT 활성화 + initial sync 예약"""
        
        await state.device_store.async_set_token(req.token)

        if state.mqtt is None:
            state.mqtt = MqttClient(
                inbound_queue=state.mqtt_inbound,
                loop=state.loop,
                token=req.token,
                link_getter=state.device_store.build_pir_link,
            )
        else:
            state.mqtt.set_token(req.token)

        state.mqtt.activate()
        schedule_initial_sync(state, timeout_sec=60.0)
        return {"ok": True}

    @app.post("/eeum/event")
    async def ingest_event(data: EventIn):
        event = Event(**data.model_dump(exclude_none=True))
        queue_put_drop_oldest(state.queue, event)
        return {"ok": True}