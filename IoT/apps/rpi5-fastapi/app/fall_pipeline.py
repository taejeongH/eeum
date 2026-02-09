# app/fall_pipeline.py
import time
import asyncio
import logging
import os
import json

from .config import DEFAULT_TTS_PATH, DEFAULT_TTS_MESSAGE, AUDIO_IN_DEVICE, FALL_ECHO_GUARD_SEC
from .tts_service import ensure_tts_mp3, get_default_tts_path
from .stt_service import record_with_vad
from .audio_manager import AudioJob, AudioPrio

logger = logging.getLogger(__name__)

STT_TMP_DIR = "./stt"
STT_TMP_WAV = os.path.join(STT_TMP_DIR, "fall_answer.wav")


def _truncate(s: str, n: int = 200) -> str:
    s = (s or "")
    return s if len(s) <= n else (s[:n] + f"...(len={len(s)})")


async def _play_tts_blocking(state, text: str, *, kind: str, replace_key: str, timeout_sec: float) -> None:
    """
    TTS 생성(or default) 후 AudioManager로 재생 완료까지 대기
    """
    tts = await ensure_tts_mp3(text, DEFAULT_TTS_PATH, timeout_sec=min(6.0, timeout_sec))
    path = (tts.path if (tts.ok and tts.path) else get_default_tts_path(kind))
    if not path:
        logger.warning("[fall] no tts audio available text=%r", text)
        return

    done = asyncio.Event()
    await state.audio.enqueue(AudioJob(
        prio=int(AudioPrio.FALL),
        kind="fall",
        path=path,
        ttl_sec=60.0,
        replace_key=replace_key,
        done_event=done,
    ))
    try:
        await asyncio.wait_for(done.wait(), timeout=timeout_sec)
    except Exception:
        pass


async def run_fall_tts_stt_pipeline(state, *, deadline_sec: float = 35.0) -> None:
    t0 = time.time()

    try:
        await state.audio.cancel_many(kinds=["schedule", "medication", "voice"])
        state.audio.block_below_prio = int(AudioPrio.FALL)

        # Fall 파이프라인 동안 무거운 작업 쉬게
        try:
            state.heavy_ops_pause = True
        except Exception:
            pass
        try:
            state.slide_playing = False
            state.slide_tick_event.set()
        except Exception:
            pass

        def remaining() -> float:
            return max(0.1, deadline_sec - (time.time() - t0))

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
        await _play_tts_blocking(
            state,
            ask_text,
            kind="fall",
            replace_key="fall.ask",
            timeout_sec=min(10.0, remaining()),
        )

        # NOTE: 여기서 오래 쉬면 사용자가 빨리 말할 때 앞 음절이 "녹음 시작 전"에 나가서 잘림
        # 에코는 record_with_vad(start_guard_sec)에서 VAD 판단만 무시하게 처리한다.
        await asyncio.sleep(0.02)

        # 2) STT(녹음+인식)
        stage("WAIT_STT", why="record_with_vad")

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
                        max_sec=min(9.0, remaining()),
                        end_silence_sec=0.7,
                        min_speech_sec=0.12,
                        vad_level=2,
                        sample_rate=48000,
                        frame_ms=20,
                        discard_head_sec=0.0,
                        pre_roll_sec=0.70,
                        start_speech_streak=1,
                        max_speech_sec=5.5,
                        # 에코 가드: 시작 후 N초는 VAD판단만 무시(오디오는 prebuf로 보존)
                        start_guard_sec=float(FALL_ECHO_GUARD_SEC or 0.0),
                    ),
                    timeout=min(12.0, remaining()),
                )

                stt_text = ""
                stt_ok = False

                if ok_rec:
                    logger.info("[fall] record ok wav=%s", STT_TMP_WAV)
                    engine = getattr(state, "stt_engine", None)
                    if engine is None:
                        stt_ok = False
                        rec_msg = "stt_engine_missing"
                        logger.warning("[fall] stt engine missing")
                    else:
                        res = await engine.transcribe(STT_TMP_WAV, timeout_sec=min(16.0, remaining()))
                        stt_text = (res.text or "").strip()
                        stt_ok = res.ok and bool(stt_text)
                        if not stt_ok:
                            rec_msg = res.message or "stt_failed"

                        logger.info(
                            "[fall] stt %s dt=%.2fs text_len=%d text=%r",
                            "ok" if stt_ok else "empty",
                            res.dt_sec,
                            len(stt_text),
                            stt_text[:80],
                        )
                else:
                    rec_msg = rec_msg or "no_speech"
                    logger.info("[fall] record failed reason=%s", rec_msg)

            finally:
                state.stt_busy = False

        # 3) MQTT publish
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

    except asyncio.CancelledError:
        raise
    except Exception:
        logger.exception("[fall] pipeline crashed")
    finally:
        # 슬라이드/무거운작업/오디오 게이트 원복
        try:
            state.slide_playing = True
            state.slide_tick_event.set()
        except Exception:
            pass
        try:
            state.heavy_ops_pause = False
        except Exception:
            pass

        state.audio.block_below_prio = None
        state.fall_active = False
        state.stt_busy = False
        state.fall_last_stage_ts = time.time()
        logger.info("[fall] done total_dt=%.2fs unblock_audio=1", time.time() - t0)
