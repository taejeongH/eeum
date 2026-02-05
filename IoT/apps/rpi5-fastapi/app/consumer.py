import asyncio
import logging
import time
from typing import Optional, Any
from dataclasses import dataclass
import uuid
from .state import MonitorState, Event, Command
from .monitor import (
    async_record_event,
    handle_pir_motion,
    handle_vision,
)
from .album_sync import async_sync_album_once
from .slideshow import rebuild_playlist, emit_slide
from .voice_sync import async_sync_voice_once
from .tts_service import get_default_tts_path
from .config import ALARM_TTS_DEBOUNCE_SEC
from .audio_manager import AudioJob, AudioPrio
from .voice_player import download_then_emit_new
from .profile_cache import ensure_profile_cached

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

def _extract_alarm_text(payload: dict) -> str:
    """
    알람 payload에서 읽을 텍스트를 뽑는다.
    서버 스펙이 아직 흔들릴 수 있으니 여러 키를 fallback.
    """
    if not isinstance(payload, dict):
        return ""
    for k in ("content", "message", "description", "text", "title"):
        v = payload.get(k)
        if isinstance(v, str) and v.strip():
            return v.strip()
    # 혹시 data 안에 들어오는 케이스 방어
    data = payload.get("data")
    if isinstance(data, dict):
        for k in ("content", "message", "description", "text", "title"):
            v = data.get(k)
            if isinstance(v, str) and v.strip():
                return v.strip()
    return ""

def _normalize_alert_envelope(payload: dict) -> dict | None:
    """
    명세 Envelope:
      {
        "msg_id": "uuid-string",
        "kind": "medication" | "schedule",
        "sent_at": epoch(float),
        "content": "...",
        "data": {}
      }

    - 서버 스펙 흔들림 대비: payload 자체가 이미 envelope면 최대한 그대로 사용
    - 누락 필드는 보정
    - kind가 아니면 None 반환
    """
    if not isinstance(payload, dict):
        return None

    kind = payload.get("kind")
    if kind not in ("medication", "schedule"):
        return None

    msg_id = payload.get("msg_id") or str(uuid.uuid4())
    sent_at = payload.get("sent_at")
    try:
        sent_at = float(sent_at) if sent_at is not None else float(time.time())
    except Exception:
        sent_at = float(time.time())

    # content: 명세 필수. 없으면 extract로 생성
    content = payload.get("content")
    if not isinstance(content, str) or not content.strip():
        content = _extract_alarm_text(payload) or ("복약 알림이 있어요" if kind == "medication" else "일정 알림이 있어요")

    # data: dict 보장
    data = payload.get("data")
    if not isinstance(data, dict):
        data = {}

    return {
        "msg_id": str(msg_id),
        "kind": str(kind),
        "sent_at": float(sent_at),
        "content": str(content),
        "data": data,
    }

def _should_play_alarm_tts(state, kind: str, now: float | None = None) -> bool:
    now = now or time.time()
    last = state.alarm_last_tts_ts.get(kind, 0.0)
    if (now - last) < float(ALARM_TTS_DEBOUNCE_SEC or 0):
        return False
    state.alarm_last_tts_ts[kind] = now
    return True

async def _handle_update_image(state: MonitorState, payload: dict, info: DeviceCommandTopic) -> None:
    update_cnt = (payload or {}).get("update_cnt")
    try:
        if update_cnt is not None and int(update_cnt) <= 0:
            logger.info("[commands] album update kind=image but update_cnt<=0, skip")
            return
    except Exception:
        pass

    logger.info(
        "[commands] album update trigger device=%s tail=%s payload=%s",
        info.device_id,
        info.tail,
        payload,
    )
    res = await async_sync_album_once(state)
    if res.get("ok"):
        rebuild_playlist(state)
        await emit_slide(state, reason="sync_update")
    logger.info("[commands] album sync result=%s", res)

async def _handle_update_voice(state: MonitorState, payload: dict, info: DeviceCommandTopic) -> None:
    res = await async_sync_voice_once(state)
    logger.info("[voice] sync result=%s", res)

    if not (res.get("ok") and int(res.get("added") or 0) > 0):
        return

    added_ids = res.get("added_ids") or []

    # SSE 보내는 즈음에 다운로드
    for vid in added_ids:
        try:
            v = state.voice_repo.get(int(vid))
            if not v:
                continue
            url = str(v.get("url") or "")
            desc = str(v.get("description") or "")

            uid = v.get("user_id")
            try:
                uid = int(uid) if uid is not None else None
            except Exception:
                uid = None

            name = ""
            profile_url = ""

            if uid is not None and state.member_repo:
                m = state.member_repo.get(uid) or {}
                name = str(m.get("name") or "")
                profile_url = str(m.get("profile_image_url") or "")

            # 프로필 캐시 (실패하면 원본 유지)
            if profile_url and state.http_session and not state.http_session.closed:
                profile_url = await ensure_profile_cached(
                    state.http_session,
                    profile_url,
                    timeout_sec=8.0,
                )

            sender = {
                "user_id": uid,
                "name": name,
                "profile_image_url": profile_url,
            }

            await download_then_emit_new(state, int(vid), url, desc, sender)
        except Exception as e:
            logger.warning("[voice] download/emit failed id=%s err=%s", vid, e)

    # added가 있을 때만 default TTS 1회(디바운스)
    kind = "voice"
    if _should_play_alarm_tts(state, kind):
        path = get_default_tts_path(kind)
        if path:
            await state.audio.enqueue(AudioJob(
                prio=int(AudioPrio.VOICE),
                kind="voice",
                path=path,
                ttl_sec=300.0,
                replace_key="voice.default.latest",
            ))
            logger.debug("[voice_default] play path=%s", path)
        else:
            logger.info("[voice_default] skip missing_default")

async def _handle_alarm(state: MonitorState, payload: dict) -> None:
    env = _normalize_alert_envelope(payload)
    if env is None:
        logger.warning("[alarm] invalid payload (no kind/unsupported) payload=%s", payload)
        return

    for q in list(state.alert_subscribers):
        try:
            q.put_nowait(env)
        except Exception:
            state.alert_subscribers.discard(q)

    logger.info("[alarm] emitted kind=%s msg_id=%s", env.get("kind"), env.get("msg_id"))

    kind = (env.get("kind") if isinstance(env, dict) else payload.get("kind")) or "schedule"

    if _should_play_alarm_tts(state, kind):
        path = get_default_tts_path(kind)
        if path:
            prio = int(AudioPrio.MEDICATION) if kind == "medication" else int(AudioPrio.SCHEDULE)
            rk = "alarm.medication.latest" if kind == "medication" else "alarm.schedule.latest"
            await state.audio.enqueue(AudioJob(
                prio=prio,
                kind=kind,
                path=path,
                ttl_sec=300.0,
                replace_key=rk,
            ))
            logger.debug("[alarm_default] play kind=%s path=%s", kind, path)
        else:
            logger.info("[alarm_default] skip missing_default kind=%s", kind)
    else:
        logger.info("[alarm_default] debounce skip kind=%s", kind)

async def consume_commands(state: MonitorState):
    my_id = state.device_id

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
                payload = cmd.payload or {}
                kind = payload.get("kind")
                if kind == "image":
                    await _handle_update_image(state, payload, info)
                elif kind == "voice":
                    await _handle_update_voice(state, payload, info)
                else:
                    logger.info("[commands] update ignored kind=%r payload=%s", kind, payload)
                continue

            elif info.action == "alarm":
                try:
                    await _handle_alarm(state, cmd.payload or {})
                except Exception as e:
                    logger.warning("[alarm] handle failed err=%s payload=%s", e, cmd.payload)
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