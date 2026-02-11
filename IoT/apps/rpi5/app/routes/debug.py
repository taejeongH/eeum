from typing import Literal, Dict, Any
from fastapi import FastAPI
from pydantic import BaseModel
from app.api_common import queue_put_drop_oldest
from app.state import Command, Event, MonitorState
from app.sync_utils import now_ts

class DebugFallReq(BaseModel):
    level: int = 1
    device_id: str | None = None
    location_id: str | None = None

class DebugAlarmReq(BaseModel):
    kind: Literal["medication", "schedule"] = "schedule"
    content: str = "Test alarm"
    data: Dict[str, Any] = {}
    sent_at: float | None = None
    msg_id: str | None = None

def register(app: FastAPI, state: MonitorState, *, enabled: bool) -> None:
    """
    디버그 전용 라우트를 FastAPI 앱에 등록합니다.

    :param app: 라우트를 등록할 FastAPI 인스턴스
    :param state: 전역 상태(MonitorState). 내부 큐(state.queue/state.cmd_queue)에 이벤트/커맨드를 enqueue 합니다.
    :param enabled: 디버그 라우트 활성화 여부
    :returns: None
    """
    if not enabled:
        return

    @app.post("/debug/fall/trigger")
    async def debug_fall_trigger(body: DebugFallReq):
        device_id = (body.device_id or state.device_id or "EEUM-DEBUG").strip()
        now = now_ts()

        event = Event(
            kind="fall",
            device_id=device_id,
            data={"event": "fall_detected", "level": int(body.level or 1), "location_id": body.location_id},
            detected_at=now,
        )
        queue_put_drop_oldest(state.queue, event)
        return {"ok": True, "queued": True, "device_id": device_id, "ts": now}

    @app.post("/debug/alarm/trigger")
    async def debug_alarm_trigger(body: DebugAlarmReq):
        device_id = (state.device_id or "EEUM-DEBUG").strip()
        now = now_ts()

        payload = {
            "kind": body.kind,
            "content": body.content,
            "data": dict(body.data or {}),
            "sent_at": float(body.sent_at) if body.sent_at is not None else float(now),
        }
        if body.msg_id:
            payload["msg_id"] = body.msg_id

        topic = f"eeum/device/{device_id}/alarm"
        cmd = Command(topic=topic, payload=payload)
        queue_put_drop_oldest(state.cmd_queue, cmd)
        return {"ok": True, "queued": True, "topic": topic, "payload": payload}