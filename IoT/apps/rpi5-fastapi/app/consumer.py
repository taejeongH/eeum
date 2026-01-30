import asyncio
import logging
from typing import Optional
from dataclasses import dataclass
from .state import MonitorState, Event, Command
from .monitor import (
    async_record_event,
    handle_pir_motion,
    handle_vision,
)

logger = logging.getLogger(__name__)

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
        ev.kind in ("vision", "fall")
        and (ev.data or {}).get("event") in ("fall_detected", "enter", "exit")
    )

async def consume_events(state: MonitorState):
    logger.info("[consumer] started")
    while True:
        ev = await state.queue.get()
        if ev is None:
            logger.info("[consumer] stop signal")
            return
        try:
            await async_record_event(state, ev)

            if _is_pir_motion(ev):
                logger.debug("[PIR] motion device=%s at=%s", ev.device_id, ev.detected_at)
                handle_pir_motion(state, ev)

            elif _is_vision(ev):
                v = (ev.data or {}).get("event")
                ev.kind = "vision"
                if v == "fall_detected":
                    logger.debug(
                        "[VISION] event=%s level=%s location=%s device=%s at=%s",
                        v,
                        (ev.data or {}).get("level"),
                        ev.data.get("location_id", None),
                        ev.device_id,
                        ev.detected_at,
                    )
                else:
                    logger.debug(
                        "[VISION] event=%s location=%s device=%s at=%s",
                        v,
                        ev.data.get("location_id", None),
                        ev.device_id,
                        ev.detected_at,
                    )
                handle_vision(state, ev)

        except Exception as e:
            logger.exception("[consumer] unexpected error while handling ev=%s", ev)

# --------- MQTT --------
async def consume_mqtt_inbound(state: MonitorState):
    loop = asyncio.get_running_loop()
    while True:
        try:
            item = await state.mqtt_inbound.get()
            if item is None:
                return

            topic, payload = item
            
            logger.debug(
                "[mqtt_inbound] topic=%s payload=%s",
                topic,
                payload,
            )
            
            cmd = Command(topic=topic, payload=payload)

            try:
                state.cmd_queue.put_nowait(cmd)
            except asyncio.QueueFull:
                try:
                    state.cmd_queue.get_nowait()
                except asyncio.QueueEmpty:
                    pass
                state.cmd_queue.put_nowait(cmd)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[mqtt_inbound] unexpected error")

async def consume_commands(state: MonitorState):
    my_id = state.device_store.doc().get("device_id") if state.device_store else None

    while True:
        try:
            cmd = await state.cmd_queue.get()
            if cmd is None:
                return

            info = parse_device_topic(cmd.topic)
            if info is None:
                # 관심 없는 토픽이면 무시
                logger.debug("[commands] ignore topic=%s payload=%s", cmd.topic, cmd.payload)
                continue

            # 내 장치가 아니면 무시(또는 로그만)
            if my_id and info.device_id != my_id:
                logger.debug(
                    "[commands] ignore other device device_id=%s my_id=%s topic=%s",
                    info.device_id,
                    my_id,
                    cmd.topic,
                )
                continue

            if info.action == "update":
                # payload.kind == image/voice 등은 로직에서 처리
                # info.tail 쓰면 "subtype" 같은 확장도 가능
                # await handle_update(state, cmd.payload, tail=info.tail)
                logger.info(
                    "[commands] received update device=%s tail=%s payload=%s",
                    info.device_id,
                    info.tail,
                    cmd.payload,
                )
                continue

            if info.action == "alarm":
                # payload.kind == medication/schedule 등
                # await handle_alarm(state, cmd.payload, tail=info.tail)
                logger.info(
                    "[commands] received alarm device=%s tail=%s payload=%s",
                    info.device_id,
                    info.tail,
                    cmd.payload,
                )
                continue
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[commands] unexpected error")

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