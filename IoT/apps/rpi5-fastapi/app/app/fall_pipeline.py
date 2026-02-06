# app/fall_pipeline.py
import time
import asyncio
import logging
import os
import json
from .config import DEFAULT_TTS_PATH, DEFAULT_TTS_MESSAGE, AUDIO_IN_DEVICE
from .tts_service import ensure_tts_mp3, get_default_tts_path
from .stt_service import record_with_vad
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

    try:
        await state.audio.cancel_many(kinds=["schedule", "medication", "voice"])
        state.audio.block_below_prio = int(AudioPrio.FALL)
        # Fall 파이프라인 동안(특히 STT) 무거운 작업(nmcli scan/ffprobe/다운로드 등) 쉬게
        # wifi_scan_loop는 heavy_ops_pause/stt_busy를 보고 이미 스킵함
        state.heavy_ops_pause = True
        # fall 시작 직전에
        state.slide_playing = False
        state.slide_tick_event.set()

        def remaining():
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
        tts = await ensure_tts_mp3(ask_text, DEFAULT_TTS_PATH, timeout_sec=min(6.0, remaining()))
        path = (tts.path if (tts.ok and tts.path) else get_default_tts_path("fall"))

        if path:
            done = asyncio.Event()
            await state.audio.enqueue(AudioJob(
                prio=int(AudioPrio.FALL),
                kind="fall",
                path=path,
                ttl_sec=60.0,
                replace_key="fall.ask",
                done_event=done,
            ))
            try:
                await asyncio.wait_for(done.wait(), timeout=min(10.0, remaining()))
            except Exception:
                pass
        else:
            logger.warning("[fall] no tts audio available (online fail + no default file)")

        if tts.ok:
            logger.info("[fall] tts ok msg=%s path=%s generated=%s", tts.message, tts.path, tts.generated)
        else:
            logger.info("[fall] tts skipped reason=%s", tts.message)

        await asyncio.sleep(1.2)
        # 2) STT(녹음+인식)
        stage("WAIT_STT", why="record_with_vad")

        # 이미 STT 중이면 즉시 종료
        if state.stt_busy:
            logger.info("[fall] stt skipped: already busy")
            stage("DONE", why="stt_busy")
            state.fall_answer_text = None
            return

        # STT 시작 전 플래그를 먼저 올려서 다른 루프들이 즉시 쉬도록
        state.stt_busy = True
        async with state.stt_lock:
            try:
                ok_rec, rec_msg, rec_meta = await asyncio.wait_for(
                    record_with_vad(
                        STT_TMP_WAV,
                        device=(AUDIO_IN_DEVICE or "plughw:CARD=Audio,DEV=0"),
                        max_sec=min(12.0, remaining()),
                        end_silence_sec=1.5,
                        # 약한 수음 보완 튜닝:
                        # - gain_db: 녹음 단계에서 증폭 + VAD도 증폭 신호로 판정
                        # - vad_level: 0이 가장 관대(약한 음성 놓칠 확률↓)
                        # - min_speech_sec 완화(너무 짧다고 no_speech 뜨는 경우 감소)
                        gain_db=0.0,
                        min_speech_sec=0.20,
                        vad_level=1,
                        discard_head_sec=0.15
                    ),
                    timeout=min(14.0, remaining()), 
                )

                stt_text = ""
                stt_ok = False
                # 클리핑 감지: peak가 너무 크면 STT 진행하지 않고 실패 처리
                try:
                    peak = int((rec_meta or {}).get("peak") or 0)
                    if ok_rec and peak >= 32000:
                        ok_rec = False
                        rec_msg = f"clipping_detected(peak={peak})"
                except Exception:
                    pass
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
    except asyncio.CancelledError:
        # 태스크 취소되어도 상태가 고착되지 않게
        raise
    except Exception:
        logger.exception("[fall] pipeline crashed")
    finally:
        state.slide_playing = True
        state.slide_tick_event.set()
        state.audio.block_below_prio = None
        state.fall_active = False
        state.stt_busy = False
        state.heavy_ops_pause = False
        state.fall_last_stage_ts = time.time()
        logger.info("[fall] done total_dt=%.2fs unblock_audio=1", time.time() - t0)