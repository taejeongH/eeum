import asyncio
import json
import logging
import os
from app.audio_manager import AudioJob, AudioPrio
from app.config import AUDIO_IN_DEVICE, DEFAULT_TTS_MESSAGE, DEFAULT_TTS_PATH, FALL_ECHO_GUARD_SEC
from app.stt_service import record_with_vad
from app.tts_service import ensure_tts_mp3, get_default_tts_path
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

STT_TMP_DIR = "./stt"
STT_TMP_WAV = os.path.join(STT_TMP_DIR, "fall_answer.wav")

def _truncate(text: str, n: int = 200) -> str:
    s = (text or "")
    return s if len(s) <= n else (s[:n] + f"...(len={len(s)})")

def _set_state_flag(state, name: str, value) -> None:
    try:
        setattr(state, name, value)
    except Exception:
        pass

def _remaining_sec(t0: float, deadline_sec: float) -> float:
    return max(0.1, float(deadline_sec) - (now_ts() - t0))

def _set_stage(state, to: str, *, why: str, t0: float, deadline_sec: float) -> None:
    prev = getattr(state, "fall_stage", None)
    state.fall_stage = to
    state.fall_last_stage_ts = now_ts()
    logger.info(
        "[fall_stage] %s -> %s why=%s remain=%.1fs dev=%s level=%s",
        prev,
        to,
        why,
        _remaining_sec(t0, deadline_sec),
        getattr(state, "fall_device", None),
        getattr(state, "fall_level", None),
    )

async def _play_tts_blocking(state, text: str, *, kind: str, replace_key: str, timeout_sec: float) -> None:
    """
    TTS 생성(또는 default) 후 AudioManager로 재생 완료까지 대기합니다.

    :param state: 전역 상태
    :param text: 재생할 문장
    :param kind: TTS 종류
    :param replace_key: 교체 키
    :param timeout_sec: 완료 대기 타임아웃(초)
    :return: 없음
    """
    tts = await ensure_tts_mp3(text, DEFAULT_TTS_PATH, timeout_sec=min(6.0, float(timeout_sec)))
    path = (tts.path if (tts.ok and tts.path) else get_default_tts_path(kind))
    if not path:
        logger.warning("[fall] no tts audio available text=%r", text)
        return

    done = asyncio.Event()
    await state.audio.enqueue(
        AudioJob(
            prio=int(AudioPrio.FALL),
            kind="fall",
            path=path,
            ttl_sec=60.0,
            replace_key=replace_key,
            done_event=done,
        )
    )
    try:
        await asyncio.wait_for(done.wait(), timeout=float(timeout_sec))
    except Exception:
        pass

def _build_fall_payload(state, *, stt_ok: bool, stt_text: str, stt_error: str) -> dict:
    payload = {
        "event": "response",
        "fall": True,
        "fall_level": int(getattr(state, "fall_level", 1) or 1),
        "stt_ok": bool(stt_ok),
        "stt_content": stt_text if stt_ok else "",
        "stt_error": "" if stt_ok else (stt_error or ""),
        "detected_at": now_ts(),
        "token": state.device_store.get_token() if state.device_store else None,
    }
    return payload

async def run_fall_tts_stt_pipeline(state, *, deadline_sec: float = 35.0) -> None:
    """
    낙상 발생 시 TTS 안내 후 사용자 응답을 VAD+STT로 수집하여 MQTT로 게시합니다.

    :param state: 전역 상태
    :param deadline_sec: 전체 파이프라인 데드라인(초)
    :return: 없음
    """
    t0 = now_ts()

    stt_text = ""
    stt_ok = False
    stt_error = ""

    try:
        await state.audio.cancel_many(kinds=["schedule", "medication", "voice"])
        state.audio.block_below_prio = int(AudioPrio.FALL)

        _set_state_flag(state, "heavy_ops_pause", True)
        try:
            state.slide_playing = False
            state.slide_tick_event.set()
        except Exception:
            pass

        _set_stage(state, "ASK_TTS", why="start", t0=t0, deadline_sec=deadline_sec)

        ask_text = (DEFAULT_TTS_MESSAGE[0] if DEFAULT_TTS_MESSAGE else "괜찮으세요? 도와드릴까요?")
        await _play_tts_blocking(
            state,
            ask_text,
            kind="fall",
            replace_key="fall.ask",
            timeout_sec=min(10.0, _remaining_sec(t0, deadline_sec)),
        )

        await asyncio.sleep(0.02)

        _set_stage(state, "WAIT_STT", why="record_with_vad", t0=t0, deadline_sec=deadline_sec)

        if getattr(state, "stt_busy", False):
            logger.info("[fall] stt skipped: already busy")
            _set_stage(state, "DONE", why="stt_busy", t0=t0, deadline_sec=deadline_sec)
            state.fall_answer_text = None
            return

        state.stt_busy = True
        async with state.stt_lock:
            try:
                ok_rec, rec_msg = await asyncio.wait_for(
                    record_with_vad(
                        STT_TMP_WAV,
                        device=(AUDIO_IN_DEVICE or "hw:CARD=Audio,DEV=0"),
                        max_sec=min(9.0, _remaining_sec(t0, deadline_sec)),
                        end_silence_sec=0.7,
                        min_speech_sec=0.12,
                        vad_level=2,
                        sample_rate=48000,
                        frame_ms=20,
                        discard_head_sec=0.0,
                        pre_roll_sec=0.70,
                        start_speech_streak=1,
                        max_speech_sec=5.5,
                        start_guard_sec=float(FALL_ECHO_GUARD_SEC or 0.0),
                    ),
                    timeout=min(12.0, _remaining_sec(t0, deadline_sec)),
                )

                if ok_rec:
                    logger.info("[fall] record ok wav=%s", STT_TMP_WAV)
                    engine = getattr(state, "stt_engine", None)
                    if engine is None:
                        stt_ok = False
                        stt_error = "stt_engine_missing"
                        logger.warning("[fall] stt engine missing")
                    else:
                        res = await engine.transcribe(STT_TMP_WAV, timeout_sec=min(16.0, _remaining_sec(t0, deadline_sec)))
                        stt_text = (res.text or "").strip()
                        stt_ok = bool(res.ok and stt_text)
                        if not stt_ok:
                            stt_error = res.message or "stt_failed"

                        logger.info(
                            "[fall] stt %s dt=%.2fs text_len=%d text=%r",
                            "ok" if stt_ok else "empty",
                            res.dt_sec,
                            len(stt_text),
                            stt_text[:80],
                        )
                else:
                    stt_ok = False
                    stt_error = rec_msg or "no_speech"
                    logger.info("[fall] record failed reason=%s", stt_error)

            finally:
                state.stt_busy = False

        _set_stage(state, "DONE", why="publish_response", t0=t0, deadline_sec=deadline_sec)
        state.fall_answer_text = stt_text if stt_ok else None

        payload = _build_fall_payload(state, stt_ok=stt_ok, stt_text=stt_text, stt_error=stt_error)

        try:
            log_payload = dict(payload)
            log_payload["stt_content"] = _truncate(str(log_payload.get("stt_content") or ""), 200)
            logger.info("[fall] publish_response payload=%s", json.dumps(log_payload, ensure_ascii=False, separators=(",", ":")))
        except Exception:
            logger.exception("[fall] payload log failed")

        if getattr(state, "mqtt", None):
            try:
                state.mqtt.publish_response(payload)
                logger.info("[fall] mqtt response published stt_ok=%s dt=%.2fs", stt_ok, now_ts() - t0)
            except Exception:
                logger.exception("[fall] mqtt publish failed")
        else:
            logger.info("[fall] mqtt disabled; response payload=%s", payload)

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[fall] pipeline crashed")
    finally:
        try:
            state.slide_playing = True
            state.slide_tick_event.set()
        except Exception:
            pass

        _set_state_flag(state, "heavy_ops_pause", False)

        state.audio.block_below_prio = None
        state.fall_active = False
        state.stt_busy = False
        ts = now_ts()
        state.fall_last_stage_ts = ts
        logger.info("[fall] done total_dt=%.2fs unblock_audio=1", ts - t0)