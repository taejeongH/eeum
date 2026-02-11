import asyncio
import audioop
import logging
import os
import wave
from collections import deque
from dataclasses import dataclass
from typing import Optional
import webrtcvad
from faster_whisper import WhisperModel
from app.config import (
    STT_BEAM_SIZE,
    STT_BEST_OF,
    STT_COMPRESSION_RATIO_THRESHOLD,
    STT_LOG_PROB_THRESHOLD,
    STT_MIN_FRAME_RMS,
    STT_NO_SPEECH_THRESHOLD,
)
from app.sh import async_sh
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

@dataclass
class STTResult:
    ok: bool
    text: str = ""
    message: str = ""
    wav_path: Optional[str] = None
    dt_sec: float = 0.0

def _looks_like_garbage(text: str) -> bool:
    t = (text or "").strip()
    if not t:
        return True
    if len(t) <= 1:
        return True
    if len(set(t)) == 1:
        return True
    return False

def _postprocess_text(text: str) -> str:
    t = (text or "").strip().replace("\n", " ").strip()
    while "  " in t:
        t = t.replace("  ", " ")
    return t

def _clamp_int(value, default: int, min_v: int, max_v: int) -> int:
    try:
        v = int(value)
    except Exception:
        v = int(default)
    return max(min_v, min(max_v, v))

def _clamp_float(value, default: float, min_v: float, max_v: float) -> float:
    try:
        v = float(value)
    except Exception:
        v = float(default)
    return max(min_v, min(max_v, v))

class FasterWhisperSTT:
    """faster-whisper 기반 STT 래퍼. WhisperModel 로드 및 transcribe 인터페이스 제공."""
    def __init__(
        self,
        model_size: str = "base",
        device: str = "cpu",
        compute_type: str = "int8",
        *,
        download_root: str | None = None,
        local_files_only: bool = False,
        cpu_threads: int = 1,
    ):
        kw = dict(
            device=device,
            compute_type=compute_type,
            download_root=download_root,
            local_files_only=local_files_only,
        )
        try:
            kw["cpu_threads"] = int(cpu_threads)
        except Exception:
            pass
        try:
            kw["num_workers"] = 1
        except Exception:
            pass

        self.model = WhisperModel(model_size, **kw)

    async def _preprocess_wav(self, wav_path: str) -> str:
        """
        STT 전용 최소 전처리를 수행합니다.
        - band 제한(highpass/lowpass)
        - 무음 게이트(agate)
        - 16k/mono/pcm_s16le 강제

        :param wav_path: 입력 wav 경로
        :returns: 전처리된 wav 경로(실패 시 원본)
        """
        out = f"/tmp/eeum_stt_proc_{int(now_ts()*1000)}.wav"
        af = "highpass=f=180,lowpass=f=3400,agate=threshold=-45dB:attack=5:release=500"

        r = await async_sh(
            [
                "ffmpeg",
                "-loglevel",
                "error",
                "-y",
                "-i",
                wav_path,
                "-af",
                af,
                "-ar",
                "16000",
                "-ac",
                "1",
                "-c:a",
                "pcm_s16le",
                out,
            ],
            check=False,
            timeout=4.0,
        )
        if r.returncode != 0:
            return wav_path
        return out

    def _build_whisper_base_kwargs(self, *, lang: str) -> dict:
        return dict(
            language=lang,
            vad_filter=False,
            beam_size=max(1, int(STT_BEAM_SIZE or 1)),
            best_of=max(1, int(STT_BEST_OF or 1)),
            temperature=0.0,
            condition_on_previous_text=False,
            compression_ratio_threshold=float(STT_COMPRESSION_RATIO_THRESHOLD or 2.40),
        )

    def _run_transcribe_pass(self, wav_path: str, *, lang: str, no_speech_th: float, logprob_th: float):
        base_kw = self._build_whisper_base_kwargs(lang=lang)
        kw = dict(base_kw)
        kw["no_speech_threshold"] = float(no_speech_th)
        kw["log_prob_threshold"] = float(logprob_th)

        segments, _ = self.model.transcribe(wav_path, **kw)
        segs = list(segments)
        text = "".join([s.text for s in segs]).strip()

        ns = []
        lp = []
        for s in segs:
            p = getattr(s, "no_speech_prob", None)
            if isinstance(p, (int, float)):
                ns.append(float(p))
            a = getattr(s, "avg_logprob", None)
            if isinstance(a, (int, float)):
                lp.append(float(a))

        avg_no_speech = (sum(ns) / len(ns)) if ns else None
        avg_logprob = (sum(lp) / len(lp)) if lp else None
        return text, avg_no_speech, avg_logprob

    def _score_candidate(self, text: str, avg_no_speech, avg_logprob) -> int:
        t = (text or "").strip()
        if not t:
            return -10_000

        score = max(len(t), 6)

        if any(k in t for k in ("도와", "살려", "도움", "119", "응급")):
            score += 50

        if isinstance(avg_logprob, (int, float)):
            if avg_logprob < -1.2:
                score -= 30
            elif avg_logprob < -1.0:
                score -= 15

        if isinstance(avg_no_speech, (int, float)) and avg_no_speech > 0.6:
            score -= 40

        return score

    async def transcribe(self, wav_path: str, *, lang: str = "ko", timeout_sec: float = 16.0) -> STTResult:
        """
        wav 파일을 Whisper로 인식합니다. 내부적으로 2회 패스를 돌려 더 나은 결과를 선택합니다.

        :param wav_path: 입력 wav 경로
        :param lang: 언어 코드
        :param timeout_sec: 인식 타임아웃(초)
        :returns: STTResult
        """
        t0 = now_ts()
        logger.info("[stt] transcribe start wav=%s timeout=%.1fs", wav_path, float(timeout_sec))

        proc_wav = wav_path
        try:
            proc_wav = await self._preprocess_wav(wav_path)

            def _blocking():
                strict_text, strict_ns, strict_lp = self._run_transcribe_pass(
                    proc_wav,
                    lang=lang,
                    no_speech_th=float(STT_NO_SPEECH_THRESHOLD or 0.60),
                    logprob_th=float(STT_LOG_PROB_THRESHOLD or -0.80),
                )
                relaxed_text, relaxed_ns, relaxed_lp = self._run_transcribe_pass(
                    proc_wav,
                    lang=lang,
                    no_speech_th=0.80,
                    logprob_th=-1.30,
                )

                strict_score = self._score_candidate(strict_text, strict_ns, strict_lp)
                relaxed_score = self._score_candidate(relaxed_text, relaxed_ns, relaxed_lp)

                if relaxed_score > strict_score:
                    return relaxed_text, relaxed_ns
                return strict_text, strict_ns

            text, avg_no_speech = await asyncio.wait_for(asyncio.to_thread(_blocking), timeout=timeout_sec)
            text = _postprocess_text(text)

            if _looks_like_garbage(text):
                text = ""

            ok = bool(text)
            dt_sec = now_ts() - t0
            logger.info(
                "[stt] transcribe done ok=%s dt=%.2fs text_len=%d text=%r",
                1 if ok else 0,
                dt_sec,
                len(text),
                text[:80],
            )
            return STTResult(ok=ok, text=text, wav_path=wav_path, dt_sec=dt_sec)

        except Exception as e:
            dt_sec = now_ts() - t0
            logger.warning(
                "[stt] transcribe failed type=%s err=%r wav=%s dt=%.2fs",
                type(e).__name__,
                e,
                wav_path,
                dt_sec,
            )
            return STTResult(ok=False, text="", message=repr(e), wav_path=wav_path, dt_sec=dt_sec)

        finally:
            try:
                if proc_wav != wav_path and proc_wav.startswith("/tmp/"):
                    os.remove(proc_wav)
            except Exception:
                pass

async def record_with_vad(
    out_wav: str,
    *,
    device: str | None = None,
    max_sec: float = 10.0,
    sample_rate: int = 48000,
    frame_ms: int = 20,
    vad_level: int = 2,
    min_speech_sec: float = 0.25,
    end_silence_sec: float = 1.3,
    discard_head_sec: float = 0.0,
    pre_roll_sec: float = 0.20,
    start_speech_streak: int = 2,
    max_speech_sec: float = 5.0,
    start_guard_sec: float = 0.0,
) -> tuple[bool, str]:
    """
    VAD 기반 녹음을 수행합니다.
    - arecord raw PCM 수신
    - webrtcvad로 speech 탐지
    - speech 시작 후 무음이 end_silence_sec 지속되면 종료
    - 결과를 wav로 저장

    :param out_wav: 출력 wav 경로
    :param device: 입력 장치
    :param max_sec: 최대 녹음 시간(초)
    :param sample_rate: 샘플레이트
    :param frame_ms: 프레임 길이(ms)
    :param vad_level: VAD 민감도(0~3)
    :param min_speech_sec: 최소 speech 길이(초)
    :param end_silence_sec: 종료로 판단할 무음 지속 시간(초)
    :param discard_head_sec: 시작 부분 버리는 시간(초)
    :param pre_roll_sec: 프리롤(초)
    :param start_speech_streak: 연속 speech로 시작 확정
    :param max_speech_sec: 말 시작 후 최대 길이 캡(초)
    :param start_guard_sec: 시작 직후 VAD 판단 무시(초)
    :returns: (ok, msg)
    """
    os.makedirs(os.path.dirname(out_wav) or ".", exist_ok=True)

    frame_ms = _clamp_int(frame_ms, 20, 10, 60)
    vad_level = _clamp_int(vad_level, 2, 0, 3)
    max_sec = _clamp_float(max_sec, 10.0, 0.5, 60.0)
    end_silence_sec = _clamp_float(end_silence_sec, 1.3, 0.1, 10.0)
    min_speech_sec = _clamp_float(min_speech_sec, 0.25, 0.0, 10.0)
    pre_roll_sec = _clamp_float(pre_roll_sec, 0.2, 0.0, 3.0)
    start_guard_sec = _clamp_float(start_guard_sec, 0.0, 0.0, 3.0)

    logger.info(
        "[stt_rec] start out=%s dev=%s max_sec=%.1f sr=%d frame_ms=%d vad=%d min_speech=%.2f end_silence=%.2f",
        out_wav,
        device,
        max_sec,
        sample_rate,
        frame_ms,
        vad_level,
        min_speech_sec,
        end_silence_sec,
    )

    vad = webrtcvad.Vad(vad_level)
    bytes_per_sample = 2
    frame_bytes = int(sample_rate * (frame_ms / 1000.0) * bytes_per_sample)

    pre_frames = max(0, int((pre_roll_sec * 1000.0) / frame_ms))
    prebuf = deque(maxlen=pre_frames) if pre_frames > 0 else deque(maxlen=0)

    start_speech_streak = max(1, int(start_speech_streak or 2))
    speech_streak = 0

    max_speech_sec = max(0.0, float(max_speech_sec or 0.0))
    discard_frames = max(0, int((float(discard_head_sec or 0.0) * 1000) / frame_ms))

    cmd = ["arecord"]
    if device:
        cmd += ["-D", device]
    cmd += ["-q", "-f", "S16_LE", "-c", "1", "-r", str(sample_rate), "-t", "raw"]

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    speech_started = False
    speech_start_ts: float | None = None
    speech_bytes = bytearray()
    t0 = now_ts()
    last_voice_ts: float | None = None
    voice_frames = 0
    frames_seen = 0

    try:
        while True:
            if (now_ts() - t0) > max_sec:
                break

            try:
                chunk = await asyncio.wait_for(proc.stdout.readexactly(frame_bytes), timeout=0.5)
                frames_seen += 1

                if frames_seen <= discard_frames:
                    continue
            except asyncio.TimeoutError:
                continue

            if pre_frames > 0:
                prebuf.append(chunk)

            try:
                fr_rms = int(audioop.rms(chunk, 2))
            except Exception:
                fr_rms = 0

            now = now_ts()

            if (not speech_started) and start_guard_sec > 0 and ((now - t0) < start_guard_sec):
                continue

            is_speech = False
            if fr_rms >= int(STT_MIN_FRAME_RMS or 0):
                is_speech = vad.is_speech(chunk, sample_rate)

            if is_speech:
                speech_streak += 1
            else:
                speech_streak = 0

            started_this_frame = False
            if (not speech_started) and (speech_streak >= start_speech_streak):
                speech_started = True
                speech_start_ts = now
                last_voice_ts = now
                started_this_frame = True

                if pre_frames > 0 and len(prebuf) > 0:
                    for fr in prebuf:
                        speech_bytes.extend(fr)
                try:
                    prebuf.clear()
                except Exception:
                    pass

            if speech_started:
                if not started_this_frame:
                    speech_bytes.extend(chunk)

                if is_speech:
                    voice_frames += 1
                    last_voice_ts = now

                if last_voice_ts is not None and (now - last_voice_ts) >= end_silence_sec:
                    break

                if max_speech_sec > 0 and speech_start_ts is not None and (now - speech_start_ts) >= max_speech_sec:
                    break

        min_frames = max(1, int((min_speech_sec * 1000) / frame_ms))
        if voice_frames < min_frames:
            if not (speech_started and voice_frames > 0):
                return (False, "no_speech")

        with wave.open(out_wav, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(bytes_per_sample)
            wf.setframerate(sample_rate)
            wf.writeframes(bytes(speech_bytes))

        try:
            tmp_out = f"/tmp/eeum_gate_{int(now_ts()*1000)}.wav"
            af_tail = "agate=threshold=-38dB:attack=10:release=900"
            r2 = await async_sh(
                ["ffmpeg", "-loglevel", "error", "-y", "-i", out_wav, "-af", af_tail, tmp_out],
                check=False,
                timeout=3.0,
            )
            if r2.returncode == 0:
                os.replace(tmp_out, out_wav)
            else:
                try:
                    os.remove(tmp_out)
                except Exception:
                    pass
        except Exception:
            logger.debug("[stt_rec] tail gate failed (ignore)", exc_info=True)

        try:
            pcm = bytes(speech_bytes)
            rms = audioop.rms(pcm, 2)
            peak = audioop.max(pcm, 2)
            logger.info("[stt_rec] pcm stats rms=%d peak=%d bytes=%d", rms, peak, len(pcm))
        except Exception:
            pass

        return (True, "ok")

    except asyncio.IncompleteReadError:
        err = ""
        try:
            if proc.stderr:
                err = (await proc.stderr.read()).decode(errors="replace").strip()
        except Exception:
            pass
        rc = None
        try:
            rc = proc.returncode
            if rc is None:
                rc = await proc.wait()
        except Exception:
            pass
        if err:
            return (False, f"audio_incomplete(rc={rc}): {err}")
        return (False, f"audio_incomplete(rc={rc})")

    except Exception as e:
        logger.warning("[stt_rec] record_with_vad failed err=%s", e)
        return (False, str(e))

    finally:
        try:
            proc.terminate()
        except Exception:
            pass
        try:
            await proc.wait()
        except Exception:
            pass