import asyncio
import json
from fastapi import FastAPI
from app.api_common import sse_response
from app.state import MonitorState
from app.sync_utils import now_ts

def register(app: FastAPI, state: MonitorState) -> None:
    """
    알림 SSE 스트림 라우트를 등록합니다.
    :param app: FastAPI
    :param state: MonitorState
    :return: None
    """
    @app.get("/api/alerts/stream")
    async def alerts_stream():
        subscriber_queue = asyncio.Queue(maxsize=32)
        state.alert_subscribers.add(subscriber_queue)

        async def gen():
            try:
                last_ping_ts = now_ts()
                while True:
                    now = now_ts()
                    # 25초마다 ping 이벤트를 흘려서 프록시/브라우저 타임아웃을 방지합니다.
                    if (now - last_ping_ts) >= 25.0:
                        last_ping_ts = now
                        yield f"event: ping\ndata: {json.dumps({'ts': now}, ensure_ascii=False)}\n\n"

                    try:
                        # subscriber_queue는 0.5초 타임아웃으로 폴링하여 ping과 이벤트 전송을 함께 처리합니다.
                        envelope = await asyncio.wait_for(subscriber_queue.get(), timeout=0.5)
                        event_type = (envelope or {}).get("_event") or "alert"
                        data = (envelope or {}).get("data") or {}
                        yield f"event: {event_type}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"
                    except asyncio.TimeoutError:
                        pass
            finally:
                state.alert_subscribers.discard(subscriber_queue)

        return sse_response(gen())