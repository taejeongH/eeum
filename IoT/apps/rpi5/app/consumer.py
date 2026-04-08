import asyncio
import logging
import uuid
from dataclasses import dataclass
from typing import Any, Dict, Optional
from app.album_sync import async_sync_album_once
from app.api_common import queue_put_drop_oldest
from app.audio_manager import AudioJob, AudioPrio
from app.monitor import async_record_event, handle_pir_motion, handle_vision
from app.profile_cache import ensure_profile_cached
from app.slideshow import emit_slide, rebuild_playlist
from app.sse_fanout import fanout_nowait
from app.state import Command, Event, MonitorState
from app.sync_utils import now_ts
from app.tts_service import get_default_tts_path
from app.voice_player import download_then_emit_new
from app.voice_sync import async_sync_voice_once

logger = logging.getLogger(__name__)

@dataclass(frozen=True)
class DeviceCommandTopic:
    """eeum/device/{device_id}/{action}[/{tail...}] 토픽 파싱 결과"""
    device_id: str
    action: str  # "update" | "alarm"
    tail: str

def _is_pir_motion_event(event: Event) -> bool:
    return (
        event.kind == "pir"
        and event.data.get("event") == "motion"
        and event.data.get("value") in (1, True)
    )

def _is_vision_family_event(event: Event) -> bool:
    return (
        event.kind in ("vision", "fall")
        and (event.data or {}).get("event") in ("fall_detected", "enter", "exit")
    )

def parse_device_topic(topic: str) -> Optional[DeviceCommandTopic]:
    """
    eeum/device/{device_id}/{action}[/{tail...}] 형태의 토픽을 파싱합니다.

    :param topic: MQTT topic 문자열
    :return: 파싱 성공 시 DeviceCommandTopic, 실패 시 None
    """
    parts = (topic or "").strip("/").split("/")
    if len(parts) < 4:
        return None
    if parts[0] != "eeum" or parts[1] != "device":
        return None

    device_id = parts[2]
    action = parts[3]
    if action not in ("update", "alarm"):
        return None

    tail = "/".join(parts[4:]) if len(parts) > 4 else ""
    return DeviceCommandTopic(device_id=device_id, action=action, tail=tail)

def _extract_alarm_text(payload: Dict[str, Any]) -> str:
    """
    알람 payload에서 읽을 텍스트를 최대한 안정적으로 추출합니다.

    :param payload: 알람 payload
    :return: 알람 텍스트(없으면 빈 문자열)
    """
    if not isinstance(payload, dict):
        return ""

    for key in ("content", "message", "description", "text", "title"):
        value = payload.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()

    data = payload.get("data")
    if isinstance(data, dict):
        for key in ("content", "message", "description", "text", "title"):
            value = data.get(key)
            if isinstance(value, str) and value.strip():
                return value.strip()

    return ""

def _normalize_alert_envelope(payload: Dict[str, Any]) -> Dict[str, Any] | None:
    """
    알람 envelope를 정규화합니다.

    기대 형태:
      {
        "msg_id": "uuid-string",
        "kind": "medication" | "schedule",
        "sent_at": epoch(float),
        "content": "...",
        "data": {}
      }

    :param payload: raw payload
    :return: 정규화된 envelope 또는 None
    """
    if not isinstance(payload, dict):
        return None

    kind = payload.get("kind")
    if kind not in ("medication", "schedule"):
        return None

    msg_id = payload.get("msg_id") or str(uuid.uuid4())

    sent_at_raw = payload.get("sent_at")
    try:
        sent_at = float(sent_at_raw) if sent_at_raw is not None else now_ts()
    except Exception:
        sent_at = now_ts()

    content = payload.get("content")
    if not isinstance(content, str) or not content.strip():
        default_text = "복약 알림이 있어요" if kind == "medication" else "일정 알림이 있어요"
        content = _extract_alarm_text(payload) or default_text

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

def _should_play_debounced_tts(
    state: MonitorState,
    kind: str,
    *,
    debounce_sec: float,
    now: float | None = None,
) -> bool:
    """
    같은 kind의 기본 TTS가 연속 재생되지 않도록 디바운싱합니다.

    정책:
    - debounce_sec <= 0 이면 항상 허용
    - 시스템 시각이 역행한 경우에는 안전하게 허용(동시에 last_ts를 now로 보정)

    :param state: MonitorState
    :param kind: "medication" | "schedule" | "voice" 등
    :param debounce_sec: 디바운스(초)
    :param now: 현재 시각(epoch seconds). None이면 now_ts()
    :return: 재생해야 하면 True
    """
    now_v = float(now_ts() if now is None else now)
    debounce = float(debounce_sec or 0.0)

    last_ts = float(state.alarm_last_tts_ts.get(kind, 0.0) or 0.0)

    if debounce <= 0:
        state.alarm_last_tts_ts[kind] = now_v
        return True

    dt = now_v - last_ts
    if dt < 0:
        state.alarm_last_tts_ts[kind] = now_v
        return True

    if dt < debounce:
        return False

    state.alarm_last_tts_ts[kind] = now_v
    return True

async def consume_events(state: MonitorState) -> None:
    """
    state.queue(Event)를 소비하여 이벤트를 기록하고, 종류에 따라 핸들러를 호출합니다.

    :param state: MonitorState
    :return: None
    """
    logger.info("[consumer] started")

    while True:
        event = await state.queue.get()
        if event is None:
            logger.info("[consumer] stop signal")
            return

        try:
            await async_record_event(state, event)

            if _is_pir_motion_event(event):
                logger.debug("[PIR] motion device=%s at=%s", event.device_id, event.detected_at)
                handle_pir_motion(state, event)
                continue

            if _is_vision_family_event(event):
                raw_event = (event.data or {}).get("event")

                # fall 이벤트도 vision 스트림으로 통일해서 처리하되,
                # 원본 Event를 변조하지 않기 위해 새 Event를 만들어 넘깁니다.
                normalized = Event(
                    kind="vision",
                    device_id=event.device_id,
                    data=dict(event.data or {}),
                    detected_at=float(event.detected_at),
                )

                if raw_event == "fall_detected":
                    logger.debug(
                        "[VISION] event=%s level=%s location=%s device=%s at=%s",
                        raw_event,
                        (normalized.data or {}).get("level"),
                        (normalized.data or {}).get("location_id"),
                        normalized.device_id,
                        normalized.detected_at,
                    )
                else:
                    logger.debug(
                        "[VISION] event=%s location=%s device=%s at=%s",
                        raw_event,
                        (normalized.data or {}).get("location_id"),
                        normalized.device_id,
                        normalized.detected_at,
                    )

                handle_vision(state, normalized)

        except Exception:
            logger.exception("[consumer] unexpected error while handling ev=%s", event)

async def consume_mqtt_inbound(state: MonitorState) -> None:
    """
    paho thread에서 들어오는 inbound 큐를 받아 state.cmd_queue로 전달합니다.
    cmd_queue가 꽉 차면 가장 오래된 항목을 버립니다.

    :param state: MonitorState
    :return: None
    """
    while True:
        try:
            item = await state.mqtt_inbound.get()
            if item is None:
                return

            topic, payload = item
            logger.debug("[mqtt_inbound] topic=%s payload=%s", topic, payload)

            cmd = Command(topic=topic, payload=payload)
            queue_put_drop_oldest(state.cmd_queue, cmd)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[mqtt_inbound] unexpected error")

async def _handle_album_update(state: MonitorState, payload: Dict[str, Any], info: DeviceCommandTopic) -> None:
    """
    이미지 업데이트 트리거가 오면 앨범을 동기화하고 슬라이드 재생목록을 갱신합니다.

    :param state: MonitorState
    :param payload: update payload
    :param info: 파싱된 topic 정보
    :return: None
    """
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

    result = await async_sync_album_once(state)
    if result.get("ok"):
        rebuild_playlist(state)
        await emit_slide(state, reason="sync_update")

    logger.info("[commands] album sync result=%s", result)

async def _build_voice_sender(state: MonitorState, user_id: int | None) -> Dict[str, Any]:
    """
    member_repo + profile_cache를 이용해 sender 정보를 구성합니다.

    :param state: MonitorState
    :param user_id: 사용자 ID
    :return: sender dict
    """
    name = ""
    profile_url = ""

    if user_id is not None and state.member_repo:
        member = state.member_repo.get(user_id) or {}
        name = str(member.get("name") or "")
        profile_url = str(member.get("profile_image_url") or "")

    if profile_url and state.http_session and not state.http_session.closed:
        try:
            profile_url = await ensure_profile_cached(state.http_session, profile_url, timeout_sec=8.0)
        except Exception:
            pass

    return {"user_id": user_id, "name": name, "profile_image_url": profile_url}

async def _download_and_emit_voice_if_possible(state: MonitorState, voice_id: int | str) -> None:
    """
    voice_repo에서 정보 조회 후 다운로드 + 신규 voice emit을 수행합니다.

    :param state: MonitorState
    :param voice_id: 음성 ID
    :return: None
    """
    try:
        voice_id_int = int(voice_id)
    except Exception:
        return

    if not state.voice_repo:
        return

    try:
        voice_row = state.voice_repo.get(voice_id_int)
    except Exception:
        voice_row = None

    if not voice_row:
        return

    url = str(voice_row.get("url") or "")
    desc = str(voice_row.get("description") or "")

    user_id_raw = voice_row.get("user_id")
    try:
        user_id = int(user_id_raw) if user_id_raw is not None else None
    except Exception:
        user_id = None

    sender = await _build_voice_sender(state, user_id)

    try:
        await download_then_emit_new(state, voice_id_int, url, desc, sender)
    except Exception as e:
        logger.warning("[voice] download/emit failed id=%s err=%s", voice_id_int, e)

async def _handle_voice_update(state: MonitorState, *, alarm_tts_debounce_sec: float) -> None:
    """
    음성 업데이트 트리거를 처리합니다.
    - voice 동기화 수행
    - 신규 항목만 다운로드/emit
    - 신규가 있을 때만 기본 TTS를 알람과 동일한 디바운스 정책으로 재생

    :param state: MonitorState
    :param alarm_tts_debounce_sec: 기본 TTS 디바운스(초) - alarm과 동일 정책
    :return: None
    """
    result = await async_sync_voice_once(state)
    logger.info("[voice] sync result=%s", result)

    if not result.get("ok"):
        return

    inserted_ids = result.get("inserted_ids") or []
    if not inserted_ids:
        return

    for vid in inserted_ids:
        await _download_and_emit_voice_if_possible(state, vid)

    if not _should_play_debounced_tts(state, "voice", debounce_sec=float(alarm_tts_debounce_sec)):
        logger.info("[voice_default] debounce skip kind=voice")
        return

    tts_path = get_default_tts_path("voice")
    if not tts_path:
        return

    await state.audio.enqueue(
        AudioJob(
            prio=int(AudioPrio.VOICE),
            kind="voice",
            path=tts_path,
            ttl_sec=300.0,
            replace_key="voice.default.latest",
        )
    )
    logger.debug("[voice_default] play path=%s", tts_path)

async def _handle_alarm(state: MonitorState, payload: Dict[str, Any], *, alarm_tts_debounce_sec: float) -> None:
    """
    alarm 명령을 처리합니다.
    - SSE로 알람 전송
    - 디바운싱 조건 만족 시 기본 TTS 재생

    :param state: MonitorState
    :param payload: alarm payload
    :param alarm_tts_debounce_sec: 기본 TTS 디바운스(초)
    :return: None
    """
    env = _normalize_alert_envelope(payload)
    if env is None:
        logger.warning("[alarm] invalid payload (no kind/unsupported) payload=%s", payload)
        fanout_nowait(
            state.alert_subscribers,
            {"_event": "error", "data": {"code": "ALERT_PARSE_ERROR", "message": "invalid payload"}},
        )
        return

    delivered = fanout_nowait(state.alert_subscribers, {"_event": "alert", "data": env})
    logger.debug(
        "[sse_alert] delivered=%d kind=%s msg_id=%s",
        int(delivered),
        str(env.get("kind")),
        str(env.get("msg_id")),
    )
    logger.info("[alarm] emitted kind=%s msg_id=%s", env.get("kind"), env.get("msg_id"))

    kind = str(env.get("kind") or "schedule")

    if not _should_play_debounced_tts(state, kind, debounce_sec=float(alarm_tts_debounce_sec)):
        logger.info("[alarm_default] debounce skip kind=%s", kind)
        return

    tts_path = get_default_tts_path(kind)
    if not tts_path:
        logger.info("[alarm_default] skip missing_default kind=%s", kind)
        return

    prio = int(AudioPrio.MEDICATION) if kind == "medication" else int(AudioPrio.SCHEDULE)
    replace_key = "alarm.medication.latest" if kind == "medication" else "alarm.schedule.latest"

    await state.audio.enqueue(
        AudioJob(
            prio=prio,
            kind=kind,
            path=tts_path,
            ttl_sec=300.0,
            replace_key=replace_key,
        )
    )
    logger.debug("[alarm_default] play kind=%s path=%s", kind, tts_path)

async def _handle_update_command(
    state: MonitorState,
    info: DeviceCommandTopic,
    payload: Dict[str, Any],
    *,
    alarm_tts_debounce_sec: float,
) -> None:
    """
    update 명령을 처리합니다.

    :param state: MonitorState
    :param info: 파싱된 topic 정보
    :param payload: update payload
    :param alarm_tts_debounce_sec: 알람/기본 TTS 디바운스(초)
    :return: None
    """
    kind = (payload or {}).get("kind")

    if kind == "image":
        await _handle_album_update(state, payload, info)
        return

    if kind == "voice":
        await _handle_voice_update(state, alarm_tts_debounce_sec=float(alarm_tts_debounce_sec))
        return

    logger.info("[commands] update ignored kind=%r payload=%s", kind, payload)

async def _handle_alarm_command(state: MonitorState, payload: Dict[str, Any], *, alarm_tts_debounce_sec: float) -> None:
    """
    alarm 명령을 처리합니다.

    :param state: MonitorState
    :param payload: alarm payload
    :param alarm_tts_debounce_sec: 알람/기본 TTS 디바운스(초)
    :return: None
    """
    try:
        await _handle_alarm(state, payload, alarm_tts_debounce_sec=float(alarm_tts_debounce_sec))
    except Exception as e:
        logger.warning("[alarm] handle failed err=%s payload=%s", e, payload)

async def consume_commands(state: MonitorState, *, alarm_tts_debounce_sec: float) -> None:
    """
    cmd_queue를 소비하며 update/alarm 명령을 처리합니다.

    :param state: MonitorState
    :param alarm_tts_debounce_sec: 알람/기본 TTS 디바운스(초) (alarm/voice 동일 정책)
    :return: None
    """
    my_device_id = state.device_id

    while True:
        try:
            cmd = await state.cmd_queue.get()
            if cmd is None:
                return

            info = parse_device_topic(cmd.topic)
            if info is None:
                logger.debug("[commands] ignore topic=%s payload=%s", cmd.topic, cmd.payload)
                continue

            if my_device_id and info.device_id != my_device_id:
                logger.debug(
                    "[commands] ignore other device device_id=%s my_id=%s topic=%s",
                    info.device_id,
                    my_device_id,
                    cmd.topic,
                )
                continue

            payload = cmd.payload or {}

            if info.action == "update":
                await _handle_update_command(
                    state,
                    info,
                    payload,
                    alarm_tts_debounce_sec=float(alarm_tts_debounce_sec),
                )
                continue

            if info.action == "alarm":
                await _handle_alarm_command(
                    state,
                    payload,
                    alarm_tts_debounce_sec=float(alarm_tts_debounce_sec),
                )
                continue

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[commands] unexpected error")