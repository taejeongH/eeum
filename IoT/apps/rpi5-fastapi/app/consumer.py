import asyncio
from typing import Optional
from dataclasses import dataclass
from .state import MonitorState, Event, Command
from .monitor import (
    async_record_event,
    handle_pir_motion,
    handle_vision,
)
@dataclass(frozen=True)
class DeviceCommandTopic:
    device_id: str
    action: str   # "update" | "alarm"
    tail: str     # action 뒤 나머지 경로(없으면 "")

def _is_pir_motion(ev: Event) -> bool:
    return (
        ev.kind == "pir"
        and ev.data.get("event") == "motion"
        and ev.data.get("value") in (1, True)
    )

def _is_vision(ev: Event) -> bool:
    return (
        ev.kind == "vision"
        and (ev.data or {}).get("event") in ("fall", "enter", "exit")
    )

async def consume_events(state: MonitorState):
    while True:
        ev = await state.queue.get()

        try:
            await async_record_event(state, ev)

            if _is_pir_motion(ev):
                print(f"[PIR] motion from {ev.device_id} at {ev.detected_at}")
                handle_pir_motion(state, ev)

            elif _is_vision(ev):
                v = (ev.data or {}).get("event")
                if v == "fall":
                    print(
                        f"[VISION] event=fall level={(ev.data or {}).get('level')} "
                        f"location={(ev.data or {}).get('location_id')} "
                        f"from {ev.device_id} at {ev.detected_at}"
                    )
                else:
                    print(
                        f"[VISION] event={v} location={(ev.data or {}).get('location_id')} "
                        f"from {ev.device_id} at {ev.detected_at}"
                    )
                handle_vision(state, ev)

        except Exception as e:
            print(f"[consumer] error: {e} (ev={ev})")

# --------- MQTT --------
async def consume_mqtt_inbound(state: MonitorState):
    loop = asyncio.get_running_loop()
    while True:
        topic, payload = await loop.run_in_executor(None, state.mqtt_inbound.get)

        cmd = Command(topic=topic, payload=payload)

        try:
            state.cmd_queue.put_nowait(cmd)
        except asyncio.QueueFull:
            # 정책: 오래된 것 하나 버리고 최신 넣기
            try:
                state.cmd_queue.get_nowait()
            except asyncio.QueueEmpty:
                pass
            state.cmd_queue.put_nowait(cmd)

async def consume_commands(state: MonitorState):
    my_id = state.device_store.doc().get("device_id") if state.device_store else None

    while True:
        cmd = await state.cmd_queue.get()

        info = parse_device_topic(cmd.topic)
        if info is None:
            # 관심 없는 토픽이면 무시
            continue

        # 내 장치가 아니면 무시(또는 로그만)
        if my_id and info.device_id != my_id:
            continue

        if info.action == "update":
            # payload.kind == image/voice 등은 로직에서 처리
            # info.tail 쓰면 "subtype" 같은 확장도 가능
            # await handle_update(state, cmd.payload, tail=info.tail)
            continue

        if info.action == "alarm":
            # payload.kind == medication/schedule 등
            # await handle_alarm(state, cmd.payload, tail=info.tail)
            continue

def parse_device_topic(topic: str) -> Optional[DeviceCommandTopic]:
    # 기대 형태: eeum/device/{device_id}/{action}[/{tail...}]
    parts = topic.strip("/").split("/")
    if len(parts) < 4:
        return None
    if parts[0] != "eeum" or parts[1] != "device":
        return None

    device_id = parts[2]
    action = parts[3]  # update or alarm
    if action not in ("update", "alarm"):
        return None

    tail = "/".join(parts[4:]) if len(parts) > 4 else ""
    return DeviceCommandTopic(device_id=device_id, action=action, tail=tail)