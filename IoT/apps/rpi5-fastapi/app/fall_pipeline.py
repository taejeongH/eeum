# app/fall_pipeline.py
import time
import asyncio
import logging
import os
import json
from .config import DEFAULT_TTS_PATH, DEFAULT_TTS_MESSAGE, AUDIO_IN_DEVICE
from .tts_service import ensure_tts_mp3
from .stt_service import record_with_vad, FasterWhisperSTT
from .audio_manager import AudioJob, AudioPrio

logger = logging.getLogger(__name__)

STT_TMP_DIR = "./stt"
STT_TMP_WAV = os.path.join(STT_TMP_DIR, "fall_answer.wav")

def _truncate(s: str, n: int = 200) -> str:
    s = (s or "")
    return s if len(s) <= n else (s[:n] + f"...(len={len(s)})")

async def run_fall_tts_stt_pipeline(state, *, deadline_sec: float = 35.0) -> None:
    """
    state.fall_stage를 진행시키며:
      ASK_TTS -> WAIT_STT -> DONE
    실패/타임아웃이어도 예외로 시스템 멈추지 않게.
    """
    t0 = time.time()
    def remaining():
        return max(0.1, deadline_sec - (time.time() - t0))

    device_id = state.fall_device or state.device_id
    
    def stage(to: str, *, why: str):
        prev = getattr(state, "fall_stage", None)
        state.fall_stage = to
        state.fall_last_stage_ts = time.time()
        logger.info(
            "[fall_stage] %s -> %s why=%s remain=%.1fs dev=%s level=%s",
            prev, to, why, remaining(),
            getattr(state, "fall_device", None),
            getattr(state, "fall_level", None),
        )

    # 1) TTS
    stage("ASK_TTS", why="start")

    ask_text = (DEFAULT_TTS_MESSAGE[0] if DEFAULT_TTS_MESSAGE else "괜찮으세요? 도와드릴까요?")
    tts = await ensure_tts_mp3(ask_text, DEFAULT_TTS_PATH, timeout_sec=min(6.0, remaining()))
    if tts.ok and tts.path:
        logger.info("[fall] tts ok msg=%s path=%s generated=%s", tts.message, tts.path, tts.generated)
        done = asyncio.Event()
        await state.audio.enqueue(AudioJob(
            prio=int(AudioPrio.FALL),
            kind="fall",
            path=tts.path,
            ttl_sec=60.0,
            replace_key="fall.ask",
            done_event=done,
        ))
        # 질문 재생 끝날 때까지 대기
        try:
            await asyncio.wait_for(done.wait(), timeout=min(10.0, remaining()))
        except Exception:
            pass
    else:
        logger.info("[fall] tts skipped reason=%s", tts.message)

    await asyncio.sleep(0.6)
    # 2) STT(녹음+인식)
    stage("WAIT_STT", why="record_with_vad")

    # 이미 STT 중이면 즉시 종료
    if state.stt_busy:
        logger.info("[fall] stt skipped: already busy")
        stage("DONE", why="stt_busy")
        state.fall_answer_text = None
        return

    state.stt_busy = True
    async with state.stt_lock:
        try:
            ok_rec, rec_msg = await asyncio.wait_for(
                record_with_vad(
                    STT_TMP_WAV,
                    device=(AUDIO_IN_DEVICE or "plughw:CARD=Audio,DEV=0"),
                    max_sec=min(8.0, remaining()),
                    end_silence_sec=0.9,
                    min_speech_sec=0.25,
                    vad_level=1,
                    discard_head_sec=0.05
                ),
                timeout=min(10.0, remaining()), 
            )

            stt_text = ""
            stt_ok = False
            if ok_rec:
                logger.info("[fall] record ok wav=%s", STT_TMP_WAV)
                engine = getattr(state, "stt_engine", None)
                if engine is None:
                    stt_ok = False
                    stt_text = ""
                    rec_msg = "stt_engine_missing"
                    logger.warning("[fall] record failed reason=%s", rec_msg)
                else:
                    res = await engine.transcribe(STT_TMP_WAV, timeout_sec=min(12.0, remaining()))
                    stt_ok = res.ok and bool(res.text.strip())
                    stt_text = (res.text or "").strip()
                    if not stt_ok:
                        rec_msg = res.message or "stt_failed"
                    logger.info("[fall] stt %s dt=%.2fs text_len=%d text=%r",
                                "ok" if stt_ok else "empty", res.dt_sec, len(stt_text), stt_text[:80])
            else:
                rec_msg = rec_msg or "no_speech"
                logger.info("[fall] record failed reason=%s", rec_msg)
        finally:
            state.stt_busy = False

    # 3) MQTT publish (없어도 FSM은 끝까지)
    stage("DONE", why="publish_response")
    state.fall_answer_text = stt_text if stt_ok else None

    payload = {
        "event": "response",
        "fall": True,
        "fall_level": int(getattr(state, "fall_level", 1) or 1),
        "stt_ok": bool(stt_ok),
        "stt_content": stt_text if stt_ok else "",
        "stt_error": "" if stt_ok else rec_msg,
        "detected_at": time.time(),
        "token": state.device_store.get_token() if state.device_store else None,
    }

    try:
        log_payload = dict(payload)
        log_payload["stt_content"] = _truncate(str(log_payload.get("stt_content") or ""), 200)
        logger.info(
            "[fall] publish_response payload=%s",
            json.dumps(log_payload, ensure_ascii=False, separators=(",", ":"))
        )
    except Exception:
        logger.exception("[fall] payload log failed")

    if state.mqtt:
        try:
            state.mqtt.publish_response(payload)
            logger.info("[fall] mqtt response published stt_ok=%s dt=%.2fs", stt_ok, time.time() - t0)
        except Exception:
            logger.exception("[fall] mqtt publish failed")
    else:
        logger.info("[fall] mqtt disabled; response payload=%s", payload)

    state.audio.block_below_prio = None
    logger.info("[fall] done total_dt=%.2fs unblock_audio=1", time.time() - t0)