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

def _update_adaptive_gain(state, rec_meta: dict | None) -> None:
    """
    rec_meta(rms/peak)로 다음 STT 녹음 gain_db를 자동 조절
    - peak가 너무 높으면 clipping 위험 -> gain down
    - rms가 너무 낮으면(말소리 약함) -> gain up
    """
    if not rec_meta:
        return
    try:
        peak = int(rec_meta.get("peak") or 0)
        rms = int(rec_meta.get("rms") or 0)
    except Exception:
        return

    g = float(getattr(state, "stt_gain_db", 6.0) or 6.0)
    gmin = float(getattr(state, "stt_gain_db_min", 0.0) or 0.0)
    gmax = float(getattr(state, "stt_gain_db_max", 12.0) or 12.0)

    # 보수적으로: peak 30000 이상이면 다음번 gain 줄이기
    if peak >= 30000:
        g = max(gmin, g - 3.0)
    # rms가 너무 낮으면(환경에 따라 조절): 250~500 사이면 굉장히 작은 편
    elif rms > 0 and rms < 350:
        g = min(gmax, g + 3.0)

    state.stt_gain_db = g

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
        await _play_tts_blocking(
            state,
            ask_text,
            kind="fall",
            replace_key="fall.ask.1",
            timeout_sec=min(10.0, remaining()),
        )

        await asyncio.sleep(0.6)
        # 2) STT(녹음+인식)
        stage("WAIT_STT", why="record_with_vad")

        if remaining() < 6.0:
            logger.info("[fall] skip stt: insufficient remaining=%.2fs", remaining())
            stage("DONE", why="no_time_for_stt")
            state.fall_answer_text = None
            return

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
                async def _one_stt_attempt() -> tuple[bool, str, dict, bool, str]:
                    """
                    return: (ok_rec, rec_msg, rec_meta, stt_ok, stt_text)
                    """
                    gain_db = float(getattr(state, "stt_gain_db", 3.0) or 3.0)
                    try:
                        ok_rec, rec_msg, rec_meta = await asyncio.wait_for(
                            record_with_vad(
                                STT_TMP_WAV,
                                device=(AUDIO_IN_DEVICE or "plughw:CARD=Audio,DEV=0"),
                                max_sec=min(14.0, remaining()),
                                end_silence_sec=1.5,
                                gain_db=gain_db,
                                min_speech_sec=0.2,
                                vad_level=1,
                                frame_ms=20,
                                discard_head_sec=0.15,
                            ),
                            timeout=min(16.0, remaining()),
                        )
                    except asyncio.TimeoutError:
                        return (False, "rec_timeout", {}, False, "")
                    # adaptive gain 업데이트(다음 시도용)
                    _update_adaptive_gain(state, rec_meta)

                    stt_text = ""
                    stt_ok = False

                    # clipping 감지: "녹음 실패"로 보지 말고, 경고로만 남긴다.
                    clipped = False
                    peak = 0
                    try:
                        peak = int((rec_meta or {}).get("peak") or 0)
                        if ok_rec and peak >= 30000:
                            clipped = True
                    except Exception:
                        clipped = False

                    # rec_msg는 STT 실패시에만 에러로 쓰고,
                    # STT 성공 시에는 경고를 payload에 따로 실어도 됨(아래에서 처리)
                    if ok_rec and clipped and (not rec_msg or rec_msg == "ok"):
                        rec_msg = f"clipping_detected(peak={peak})"

                    if ok_rec:
                        engine = getattr(state, "stt_engine", None)
                        if engine is None:
                            return (False, "stt_engine_missing", rec_meta or {}, False, "")
                        res = await engine.transcribe(STT_TMP_WAV, timeout_sec=min(12.0, remaining()))
                        stt_text = (res.text or "").strip()
                        stt_ok = res.ok and bool(stt_text)
                        if not stt_ok:
                            rec_msg = res.message or "stt_failed"
                    else:
                        rec_msg = rec_msg or "no_speech"
                    return (ok_rec, rec_msg, rec_meta or {}, stt_ok, stt_text)

                # ---- 1차(단일) 시도 ----
                ok_rec, rec_msg, rec_meta, stt_ok, stt_text = await _one_stt_attempt()
                clipped = ("clipping_detected" in (rec_msg or ""))

                logger.info("[fall] stt ok=%s rec_msg=%s text=%r gain_now=%.1f",
                             stt_ok, rec_msg, (stt_text[:80] if stt_text else ""), float(getattr(state, "stt_gain_db", 0.0)))

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
            # 경고: STT 성공/실패와 무관하게 품질 이슈 남기기
            "stt_warning": (rec_msg if clipped else ""),
            "stt_gain_db": float(getattr(state, "stt_gain_db", 0.0) or 0.0),
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