
import os
import wave
import time
import asyncio
import logging
import audioop
import math
from collections import deque
from dataclasses import dataclass
from typing import Optional

import webrtcvad
from faster_whisper import WhisperModel

from .sh import async_sh
from .config import (
    STT_NO_SPEECH_THRESHOLD,
    STT_LOG_PROB_THRESHOLD,
    STT_COMPRESSION_RATIO_THRESHOLD,
    STT_BEAM_SIZE,
    STT_BEST_OF,
    STT_MIN_FRAME_RMS,
)

logger = logging.getLogger(__name__)

@dataclass
class STTResult:
    ok: bool
    text: str = ""
    message: str = ""
    wav_path: Optional[str] = None
    dt_sec: float = 0.0

def _looks_like_garbage(t: str) -> bool:
    t = (t or "").strip()
    if not t:
        return True
    if len(t) <= 1:
        return True
    
    if len(set(t)) == 1:
        return True
    return False

class FasterWhisperSTT:
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
        STT 전용 "최소/안전" 전처리(인식률 우선):
        - band 제한(highpass/lowpass)
        - 무음 구간만 게이트(agate): 팬/환경소음이 무음에서 텍스트로 새는 것 방지
        - 16k/mono/pcm_s16le 로 강제(Whisper 입력 안정화)

        NOTE:
        - afftdn/comp/limiter/loudnorm는 자음/어택을 망가뜨려 STT에 불리한 경우가 많아서 제거
        """
        out = f"/tmp/eeum_stt_proc_{int(time.time()*1000)}.wav"

        
        af = "highpass=f=180,lowpass=f=3400,agate=threshold=-45dB:attack=5:release=500"
        r = await async_sh(
            [
                "ffmpeg",
                "-loglevel", "error",
                "-y",
                "-i", wav_path,
                "-af", af,
                
                "-ar", "16000",
                "-ac", "1",
                "-c:a", "pcm_s16le",
                out,
            ],
            check=False,
            timeout=4.0,
        )
        if r.returncode != 0:
            return wav_path
        return out

    def _postprocess_text(self, text: str) -> str:
        t = (text or "").strip()
        t = t.replace("\n", " ").strip()
        while "  " in t:
            t = t.replace("  ", " ")
        return t

    async def transcribe(self, wav_path: str, *, lang: str = "ko", timeout_sec: float = 16.0) -> STTResult:
        t0 = time.time()
        logger.info("[stt] transcribe start wav=%s timeout=%.1fs", wav_path, float(timeout_sec))

        try:
            proc_wav = await self._preprocess_wav(wav_path)

            def _blocking2():
                base_kw = dict(
                    language=lang,
                    vad_filter=False,
                    beam_size=max(1, int(STT_BEAM_SIZE or 1)),
                    best_of=max(1, int(STT_BEST_OF or 1)),
                    temperature=0.0,
                    condition_on_previous_text=False,
                    compression_ratio_threshold=float(STT_COMPRESSION_RATIO_THRESHOLD or 2.40),
                )

                def run_pass(no_speech_th, logprob_th):
                    kw = dict(base_kw)
                    kw["no_speech_threshold"] = float(no_speech_th)
                    kw["log_prob_threshold"] = float(logprob_th)

                    segments, _ = self.model.transcribe(proc_wav, **kw)
                    segs = list(segments)
                    txt = "".join([s.text for s in segs]).strip()

                    
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
                    return txt, avg_no_speech, avg_logprob

                def score(text, avg_no_speech, avg_logprob):
                    t = (text or "").strip()
                    if not t:
                        return -10_000

                    s = max(len(t), 6)   

                    
                    if any(k in t for k in ("도와", "살려", "도움", "119", "응급")):
                        s += 50

                    
                    if isinstance(avg_logprob, (int, float)):
                        if avg_logprob < -1.2:
                            s -= 30
                        elif avg_logprob < -1.0:
                            s -= 15

                    
                    if isinstance(avg_no_speech, (int, float)) and avg_no_speech > 0.6:
                        s -= 40

                    return s

                
                t1, ns1, lp1 = run_pass(STT_NO_SPEECH_THRESHOLD or 0.60, STT_LOG_PROB_THRESHOLD or -0.80)
                
                t2, ns2, lp2 = run_pass(0.80, -1.30)

                
                if score(t2, ns2, lp2) > score(t1, ns1, lp1):
                    txt, avg_no_speech = t2, ns2
                else:
                    txt, avg_no_speech = t1, ns1

                return (txt, avg_no_speech)

            text, avg_no_speech = await asyncio.wait_for(asyncio.to_thread(_blocking2), timeout=timeout_sec)
            text = self._postprocess_text(text)

            if _looks_like_garbage(text):
                text = ""

            logger.info(
                "[stt] transcribe done ok=%s dt=%.2fs text_len=%d text=%r",
                1 if text else 0, time.time() - t0, len(text), text[:80]
            )
            ok = bool(text)
            return STTResult(ok=ok, text=text, wav_path=wav_path, dt_sec=time.time() - t0)

        except Exception as e:
            logger.warning(
                "[stt] transcribe failed type=%s err=%r wav=%s dt=%.2fs",
                type(e).__name__, e, wav_path, time.time() - t0
            )
            return STTResult(ok=False, text="", message=repr(e), wav_path=wav_path, dt_sec=time.time() - t0)

        finally:
            try:
                if "proc_wav" in locals() and proc_wav != wav_path and proc_wav.startswith("/tmp/"):
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
    단순 VAD 녹음:
      - arecord raw PCM 수신
      - webrtcvad로 speech 탐지
      - speech 시작 후 무음이 end_silence_sec 지속되면 종료
      - 결과를 wav로 저장
    return: (ok, msg)
    """
    os.makedirs(os.path.dirname(out_wav) or ".", exist_ok=True)

    logger.info(
        "[stt_rec] start out=%s dev=%s max_sec=%.1f sr=%d frame_ms=%d vad=%d min_speech=%.2f end_silence=%.2f",
        out_wav, device, max_sec, sample_rate, frame_ms, vad_level, min_speech_sec, end_silence_sec
    )

    vad = webrtcvad.Vad(vad_level)
    bytes_per_sample = 2  
    frame_bytes = int(sample_rate * (frame_ms / 1000.0) * bytes_per_sample)

    
    try:
        pre_frames = int((float(pre_roll_sec) * 1000.0) / float(frame_ms))
    except Exception:
        pre_frames = 0
    pre_frames = max(0, pre_frames)
    prebuf = deque(maxlen=max(1, pre_frames) if pre_frames > 0 else 0)

    
    try:
        start_speech_streak = int(start_speech_streak)
    except Exception:
        start_speech_streak = 2
    start_speech_streak = max(1, start_speech_streak)
    speech_streak = 0
    speech_start_ts: float | None = None

    
    try:
        max_speech_sec = float(max_speech_sec)
    except Exception:
        max_speech_sec = 0.0
    if max_speech_sec < 0:
        max_speech_sec = 0.0

    
    try:
        start_guard_sec = float(start_guard_sec)
    except Exception:
        start_guard_sec = 0.0
    if start_guard_sec < 0:
        start_guard_sec = 0.0

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
    speech_bytes = bytearray()
    t0 = time.time()
    last_voice_ts = None
    voice_frames = 0

    discard_frames = int((discard_head_sec * 1000) / frame_ms)
    frames_seen = 0

    try:
        while True:
            if (time.time() - t0) > max_sec:
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

            is_speech = False
            if fr_rms >= int(STT_MIN_FRAME_RMS or 0):
                is_speech = vad.is_speech(chunk, sample_rate)
            now = time.time()

            
            
            if (not speech_started) and start_guard_sec > 0 and ((now - t0) < start_guard_sec):
                continue

            
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

            if speech_started and last_voice_ts is not None:
                if (now - last_voice_ts) >= end_silence_sec:
                    break

            
            if speech_started and speech_start_ts is not None and max_speech_sec > 0:
                if (now - speech_start_ts) >= max_speech_sec:
                    break
        logger.info(
            "[stt_rec] stop speech_started=%s voice_frames=%d elapsed=%.2f last_voice_delta=%s",
            speech_started, voice_frames, (time.time() - t0),
            (None if last_voice_ts is None else round(time.time() - last_voice_ts, 2))
        )

        min_frames = max(1, int((min_speech_sec * 1000) / frame_ms))
        
        if voice_frames < min_frames:
            if speech_started and voice_frames > 0:
                
                pass
            else:
                return (False, "no_speech")

        with wave.open(out_wav, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(bytes_per_sample)
            wf.setframerate(sample_rate)
            wf.writeframes(bytes(speech_bytes))

        
        try:
            tmp_out = f"/tmp/eeum_gate_{int(time.time()*1000)}.wav"
            af_tail = "agate=threshold=-38dB:attack=10:release=900"
            r2 = await async_sh(
                ["ffmpeg", "-loglevel", "error", "-y", "-i", out_wav, "-af", af_tail, tmp_out],
                check=False,
                timeout=3.0,
            )
            if r2.returncode == 0:
                os.replace(tmp_out, out_wav)
            else:
                try: os.remove(tmp_out)
                except Exception: pass
        except Exception:
            logger.debug("[stt_rec] tail gate failed (ignore)", exc_info=True)

        pcm = bytes(speech_bytes)
        rms = audioop.rms(pcm, 2)
        peak = audioop.max(pcm, 2)
        logger.info("[stt_rec] pcm stats rms=%d peak=%d bytes=%d", rms, peak, len(pcm))

        total_frames = frames_seen - discard_frames if frames_seen > discard_frames else frames_seen
        logger.info(
            "[stt_rec] vad ratio voice_frames=%d total_frames=%d ratio=%.2f",
            voice_frames, total_frames, (voice_frames / max(1, total_frames))
        )

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
        return (False, f"audio_incomplete(rc={rc}): {err}" if err else f"audio_incomplete(rc={rc})")

    except Exception as e:
        logger.warning("[stt] record_with_vad failed err=%s", e)
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
