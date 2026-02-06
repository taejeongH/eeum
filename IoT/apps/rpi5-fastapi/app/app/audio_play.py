# app/audio_play.py
import asyncio
import logging
import os
import time
import json
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)
_WARMED = False

async def _probe_audio_duration_sec(path: str, *, timeout_sec: float = 2.0) -> Optional[float]:
    """
    mp3/wav 등 duration을 ffprobe로 조회.
    실패하면 None 반환(재생 경로 영향 X)
    """
    try:
        p = await asyncio.create_subprocess_exec(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "json",
            path,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL,
        )
        out, _ = await asyncio.wait_for(p.communicate(), timeout=timeout_sec)
        if p.returncode != 0:
            return None
        j = json.loads((out or b"{}").decode(errors="replace") or "{}")
        d = float((j.get("format") or {}).get("duration") or 0.0)
        if d <= 0:
            return None
        return d
    except Exception:
        return None

async def _warmup_output_device(*, out_dev: str, rate_hz: int, channels: int, ms: int = 200):
    """
    디바이스 오픈/클럭 lock 때문에 첫 음절 잘리는 케이스 방지:
    - 짧은 무음 PCM을 aplay로 1회 흘려서 디바이스를 깨운다.
    - 비용은 0.2초 정도, 1회만 수행.
    """
    frames = int(rate_hz * (ms / 1000.0))
    # s16le: 2 bytes/sample
    bytes_len = frames * channels * 2
    silent = b"\x00" * bytes_len

    p = await asyncio.create_subprocess_exec(
        "aplay",
        "-q",
        "-D", out_dev,
        "-f", "S16_LE",
        "-c", str(channels),
        "-r", str(rate_hz),
        "-",
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=asyncio.subprocess.DEVNULL,
    )
    try:
        if p.stdin:
            p.stdin.write(silent)
            await p.stdin.drain()
            p.stdin.close()
        await asyncio.wait_for(p.wait(), timeout=2.0)
    except Exception:
        try:
            p.terminate()
        except Exception:
            pass
        try:
            await asyncio.wait_for(p.wait(), timeout=1.0)
        except Exception:
            pass

def _env_bool(name: str, default: bool) -> bool:
    v = os.getenv(name)
    if v is None:
        return default
    return v.strip().lower() not in ("0", "false", "no", "off", "")


def _env_int(name: str, default: int) -> int:
    v = os.getenv(name)
    if v is None or v.strip() == "":
        return default
    try:
        return int(v.strip())
    except Exception:
        return default


async def _drain_stderr(prefix: str, reader: Optional[asyncio.StreamReader], *, max_lines: int = 200):
    if reader is None:
        return
    lines = 0
    fp = None
    try:
        os.makedirs("./logs", exist_ok=True)
        fp = open(f"./logs/audio_{prefix}_{int(time.time()*1000)}.log", "a", encoding="utf-8", errors="replace")
    except Exception:
        fp = None
    try:
        while True:
            line = await reader.readline()
            if not line:
                break
            if lines < max_lines:
                msg = line.decode(errors="replace").rstrip()
                if msg:
                    logger.warning("[%s][stderr] %s", prefix, msg)
                    if fp:
                        fp.write(msg + "\n")
            lines += 1
    except asyncio.CancelledError:
        raise
    except Exception:
        logger.debug("[%s][stderr] drain failed (ignore)", prefix, exc_info=True)
    finally:
        try:
            if fp:
                fp.close()
        except Exception:
            pass

async def _pump(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    total = 0
    try:
        while True:
            chunk = await reader.read(8192)
            if not chunk:
                break
            try:
                writer.write(chunk)
                await writer.drain()
                total += len(chunk)
            except (BrokenPipeError, ConnectionResetError):
                break
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning("[audio] pump error err=%r total=%d", e, total)
        raise
    finally:
        try:
            writer.close()
            await writer.wait_closed()
        except Exception:
            pass


@dataclass
class AudioPlayback:
    ffmpeg: asyncio.subprocess.Process
    aplay: asyncio.subprocess.Process
    pump_task: asyncio.Task
    ffmpeg_stderr_task: Optional[asyncio.Task] = None
    aplay_stderr_task: Optional[asyncio.Task] = None
    src_duration_sec: Optional[float] = None

    async def stop(self):
        if self.pump_task and not self.pump_task.done():
            self.pump_task.cancel()
            try:
                await self.pump_task
            except Exception:
                pass

        for t in (self.ffmpeg_stderr_task, self.aplay_stderr_task):
            if t and not t.done():
                t.cancel()
        for t in (self.ffmpeg_stderr_task, self.aplay_stderr_task):
            if t:
                try:
                    await t
                except Exception:
                    pass

        for p in (self.ffmpeg, self.aplay):
            try:
                if p and p.returncode is None:
                    p.terminate()
            except Exception:
                pass

        for p in (self.ffmpeg, self.aplay):
            try:
                if p:
                    await asyncio.wait_for(p.wait(), timeout=1.0)
            except Exception:
                try:
                    if p and p.returncode is None:
                        p.kill()
                except Exception:
                    pass

    async def wait(self):
        """
        - pump_task는 ffmpeg stdout -> aplay stdin 파이프만 담당
        - pump_task가 끝났다고 해서 aplay가 재생을 끝낸 게 아니다(버퍼 draining 필요)
        - 따라서 여기서 terminate()하면 EINTR로 끊기고 무음/잘림 발생 가능
        """
        try:
            await self.pump_task
        finally:
            for t in (self.ffmpeg_stderr_task, self.aplay_stderr_task):
                if t:
                    try:
                        await asyncio.wait_for(t, timeout=1.0)
                    except Exception:
                        pass

            # ffmpeg는 보통 이미 끝나있음. 정상 종료 기다리기
            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    await asyncio.wait_for(self.ffmpeg.wait(), timeout=2.0)
            except Exception:
                # ffmpeg가 이상하게 붙어있으면 그때만 정리
                try:
                    if self.ffmpeg and self.ffmpeg.returncode is None:
                        self.ffmpeg.terminate()
                        await asyncio.wait_for(self.ffmpeg.wait(), timeout=1.0)
                except Exception:
                    try:
                        if self.ffmpeg and self.ffmpeg.returncode is None:
                            self.ffmpeg.kill()
                    except Exception:
                        pass

            # 핵심: aplay는 stdin EOF 후에도 장치 버퍼를 비우며 재생한다.
            # 따라서 terminate하지 말고 "충분히 기다린다".
            try:
                if self.aplay and self.aplay.returncode is None:
                    # duration 기반 timeout (최소 15s, 최대 180s)
                    if self.src_duration_sec and self.src_duration_sec > 0:
                        timeout_sec = float(self.src_duration_sec) + 8.0
                        timeout_sec = max(15.0, min(180.0, timeout_sec))
                    else:
                        # duration을 모르겠으면 기존보다 넉넉히(혹시 긴 파일)
                        timeout_sec = 60.0
                    await asyncio.wait_for(self.aplay.wait(), timeout=timeout_sec)
            except Exception:
                # 너무 오래 걸리면 그때만 종료
                try:
                    if self.aplay and self.aplay.returncode is None:
                        self.aplay.terminate()
                        await asyncio.wait_for(self.aplay.wait(), timeout=2.0)
                except Exception:
                    try:
                        if self.aplay and self.aplay.returncode is None:
                            self.aplay.kill()
                    except Exception:
                        pass

            logger.info(
                "[audio] playback finished rc_ffmpeg=%s rc_aplay=%s",
                getattr(self.ffmpeg, "returncode", None),
                getattr(self.aplay, "returncode", None),
            )

async def start_mp3_playback(path: str, *, volume: float = 1.0, start_delay_ms: int = 500) -> AudioPlayback:
    volume = max(0.0, min(float(volume), 2.0))

    rate_hz = _env_int("AUDIO_RATE_HZ", 48000)
    channels = _env_int("AUDIO_CHANNELS", 2)
    if channels not in (1, 2):
        channels = 1

    preroll_ms = _env_int("AUDIO_PREROLL_MS", 800)
    preroll_ms = max(0, min(preroll_ms, 5000))

    log_stderr = _env_bool("AUDIO_LOG_STDERR", True)

    if start_delay_ms and start_delay_ms > 0:
        await asyncio.sleep(start_delay_ms / 1000.0)

    # duration은 재생 안정성(기다리는 시간)용이라 실패해도 OK
    dur_sec = await _probe_audio_duration_sec(path, timeout_sec=2.0)

    out_dev = os.getenv("AUDIO_OUT_DEVICE", "default").strip() or "default"

    global _WARMED
    if not _WARMED:
        try:
            await _warmup_output_device(out_dev=out_dev, rate_hz=rate_hz, channels=channels, ms=200)
            logger.info("[audio] warmup done dev=%s rate=%d ch=%d", out_dev, rate_hz, channels)
        except Exception as e:
            logger.info("[audio] warmup skipped err=%r", e)
        _WARMED = True

    aplay = await asyncio.create_subprocess_exec(
        "aplay",
        "-q",
        "-D", out_dev,
        "-f", "S16_LE",
        "-c", str(channels),
        "-r", str(rate_hz),
        "--buffer-time=500000",
        "--period-time=100000",
        "-",
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=(asyncio.subprocess.PIPE if log_stderr else asyncio.subprocess.DEVNULL),
    )
    if aplay.stdin is None:
        raise RuntimeError("aplay.stdin is None")

    # ffmpeg filter:
    # - 프리롤: adelay로 앞부분 잘림 흡수
    # - 볼륨: volume
    # - 채널: mono 강제면 -ac 로 맞추는 게 가장 단순
    if channels == 2:
        delay = f"{preroll_ms}|{preroll_ms}"
    else:
        delay = f"{preroll_ms}"

    # resample 안정화(클럭 드리프트/언더런 완화)
    base = f"aresample=async=1:first_pts=0"
    if preroll_ms > 0:
        af = f"adelay={delay},{base},volume={volume:.2f}"
    else:
        af = f"{base},volume={volume:.2f}"

    ffmpeg = await asyncio.create_subprocess_exec(
        "ffmpeg",
        "-nostdin",
        "-loglevel", "error",
        "-probesize", "32k",
        "-analyzeduration", "0",
        "-threads", "1",
        "-vn", "-sn", "-dn",
        "-i", path,
        "-filter:a", af,
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ac", str(channels),
        "-ar", str(rate_hz),
        "-",
        stdout=asyncio.subprocess.PIPE,
        stderr=(asyncio.subprocess.PIPE if log_stderr else asyncio.subprocess.DEVNULL),
    )
    if ffmpeg.stdout is None:
        raise RuntimeError("ffmpeg.stdout is None")

    aplay_err_task = None
    ffmpeg_err_task = None
    if log_stderr:
        if aplay.stderr is not None:
            aplay_err_task = asyncio.create_task(_drain_stderr("aplay", aplay.stderr))
        if ffmpeg.stderr is not None:
            ffmpeg_err_task = asyncio.create_task(_drain_stderr("ffmpeg", ffmpeg.stderr))

    pump_task = asyncio.create_task(_pump(ffmpeg.stdout, aplay.stdin))

    logger.info(
        "[audio] playback started pid_ffmpeg=%s pid_aplay=%s path=%s vol=%.2f rate=%d ch=%d preroll_ms=%d",
        getattr(ffmpeg, "pid", None),
        getattr(aplay, "pid", None),
        path,
        volume,
        rate_hz,
        channels,
        preroll_ms,
    )

    return AudioPlayback(
        ffmpeg=ffmpeg,
        aplay=aplay,
        pump_task=pump_task,
        ffmpeg_stderr_task=ffmpeg_err_task,
        aplay_stderr_task=aplay_err_task,
        src_duration_sec=dur_sec,
    )
