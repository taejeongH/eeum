# app/stt_service.py
import os
import wave
import time
import asyncio
import logging
import audioop
from dataclasses import dataclass
from typing import Optional
from .sh import async_sh
import webrtcvad
from faster_whisper import WhisperModel

logger = logging.getLogger(__name__)

@dataclass
class STTResult:
    ok: bool
    text: str = ""
    message: str = ""
    wav_path: Optional[str] = None
    dt_sec: float = 0.0

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
        # faster-whisper 버전별로 cpu_threads/num_workers 지원 여부가 다를 수 있어 방어적으로 처리
        kw = dict(
            device=device,
            compute_type=compute_type,
            download_root=download_root,
            local_files_only=local_files_only,
        )
        # 가능한 경우만 적용
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
        마이크 감도 낮음/음량 부족 + 노이즈 대응:
        - 증폭(volume)
        - 간단 노이즈 감소(afftdn)
        - 대역 제한(highpass/lowpass)
        결과를 /tmp 쪽으로 생성해서 반환
        """
        out = f"/tmp/eeum_stt_proc_{int(time.time()*1000)}.wav"

        af = (
            "highpass=f=80,lowpass=f=8000,"
            "afftdn=nf=-25,"
            "acompressor=threshold=-22dB:ratio=3:attack=10:release=200,"
            "volume=6dB"
        )

        r = await async_sh(
            ["ffmpeg", "-loglevel", "error", "-y", "-i", wav_path, "-af", af, out],
            check=False,
            timeout=4.0,
        )
        if r.returncode != 0:
            # 전처리 실패하면 원본으로 fallback
            return wav_path
        return out

    def _postprocess_text(self, text: str) -> str:
        t = (text or "").strip()
        # 너무 기본적인 후처리만(원하면 여기 더 세게 가능)
        t = t.replace("\n", " ").strip()
        # 연속 공백 정리
        while "  " in t:
            t = t.replace("  ", " ")
        return t
        
    async def transcribe(self, wav_path: str, *, lang: str = "ko", timeout_sec: float = 12.0) -> STTResult:
        t0 = time.time()
        logger.info("[stt] transcribe start wav=%s timeout=%.1fs", wav_path, float(timeout_sec))
        
        def _blocking():
            segments, info = self.model.transcribe(
                wav_path,
                language=lang,
                vad_filter=False,  # 여기서는 webrtcvad로 이미 컷팅/타임아웃할 거라 OFF
                beam_size=1,
            )
            txt = "".join([seg.text for seg in segments]).strip()
            return txt

        try:
            proc_wav = await self._preprocess_wav(wav_path)
            # blocking 함수가 proc_wav를 쓰도록 캡처
            def _blocking2():
                kw = dict(
                    language=lang,
                    vad_filter=False,
                    beam_size=1,
                    best_of=1,
                    temperature=0.0,
                    condition_on_previous_text=False,
                )
                segments, info = self.model.transcribe(proc_wav, **kw)
                txt = "".join([seg.text for seg in segments]).strip()
                return txt

            text = await asyncio.wait_for(asyncio.to_thread(_blocking2), timeout=timeout_sec)
            text = self._postprocess_text(text)
            logger.info("[stt] transcribe done ok=1 dt=%.2fs text_len=%d text=%r",
            time.time() - t0, len(text), text[:80])
            return STTResult(ok=True, text=text, wav_path=wav_path, dt_sec=time.time() - t0)
        except Exception as e:
            logger.warning("[stt] transcribe failed type=%s err=%r wav=%s dt=%.2fs",
               type(e).__name__, e, wav_path, time.time() - t0)
            return STTResult(ok=False, text="", message=repr(e), wav_path=wav_path, dt_sec=time.time() - t0)
        finally:
            # 전처리 wav 정리 (원본이면 지우면 안 됨)
            try:
                if "proc_wav" in locals() and proc_wav != wav_path and proc_wav.startswith("/tmp/"):
                    os.remove(proc_wav)
            except Exception:
                pass

async def record_with_vad(
    out_wav: str,
    *,
    device: str | None = None,
    max_sec: float = 8.0,
    sample_rate: int = 16000,
    frame_ms: int = 30,
    vad_level: int = 1,
    min_speech_sec: float = 0.25,
    end_silence_sec: float = 0.9,
    discard_head_sec: float = 0.05,
) -> tuple[bool, str]:
    """
    - arecord로 PCM을 실시간으로 받고
    - webrtcvad로 speech 탐지
    - speech 시작 후 silence가 end_silence_sec 지속되면 종료
    - max_sec 넘으면 종료
    """
    os.makedirs(os.path.dirname(out_wav) or ".", exist_ok=True)

    logger.info(
        "[stt_rec] start out=%s dev=%s max_sec=%.1f sr=%d frame_ms=%d vad=%d min_speech=%.2f end_silence=%.2f",
        out_wav, device, max_sec, sample_rate, frame_ms, vad_level, min_speech_sec, end_silence_sec
    )

    vad = webrtcvad.Vad(vad_level)
    bytes_per_sample = 2  # s16le
    frame_bytes = int(sample_rate * (frame_ms / 1000.0) * bytes_per_sample)

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

            # arecord가 stalled 되면 readexactly가 무기한 대기할 수 있어 timeout을 둔다
            try:
                chunk = await asyncio.wait_for(
                    proc.stdout.readexactly(frame_bytes),
                    timeout=0.5,
                )
                frames_seen += 1

                # 추가: 녹음 시작 직후 잔향/팝노이즈 구간 버리기
                if frames_seen <= discard_frames:
                    continue
            except asyncio.TimeoutError:
                continue
            is_speech = vad.is_speech(chunk, sample_rate)

            now = time.time()
            if is_speech:
                voice_frames += 1
                if not speech_started:
                    speech_started = True
                last_voice_ts = now
                speech_bytes.extend(chunk)
            else:
                # speech 시작 후엔 무성도 조금은 붙여줘야 자연스러움
                if speech_started:
                    speech_bytes.extend(chunk)

            if speech_started and last_voice_ts is not None:
                if (now - last_voice_ts) >= end_silence_sec:
                    break

        logger.info(
            "[stt_rec] stop speech_started=%s voice_frames=%d elapsed=%.2f last_voice_delta=%s",
            speech_started, voice_frames, (time.time() - t0),
            (None if last_voice_ts is None else round(time.time() - last_voice_ts, 2))
        )

        # 최소 발화량 체크
        min_frames = int((min_speech_sec * 1000) / frame_ms)
        if voice_frames < min_frames:
            return (False, "no_speech")

        # wav 저장
        with wave.open(out_wav, "wb") as wf:
            wf.setnchannels(1)
            wf.setsampwidth(bytes_per_sample)
            wf.setframerate(sample_rate)
            wf.writeframes(bytes(speech_bytes))

        pcm = bytes(speech_bytes)
        rms = audioop.rms(pcm, 2)          # 16-bit -> width=2
        peak = audioop.max(pcm, 2)
        logger.info("[stt_rec] pcm stats rms=%d peak=%d bytes=%d", rms, peak, len(pcm))

        total_frames = frames_seen - discard_frames if frames_seen > discard_frames else frames_seen
        logger.info("[stt_rec] vad ratio voice_frames=%d total_frames=%d ratio=%.2f",
                    voice_frames, total_frames, (voice_frames / max(1, total_frames)))
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
