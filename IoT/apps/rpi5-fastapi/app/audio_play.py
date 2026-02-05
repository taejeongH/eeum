# app/audio_play.py
import asyncio
import logging
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)

class PersistentAplaySink:
    def __init__(self):
        self.proc: Optional[asyncio.subprocess.Process] = None
        self.lock = asyncio.Lock()

    async def ensure_started(self):
        async with self.lock:
            if self.proc and self.proc.returncode is None and self.proc.stdin:
                return

            self.proc = await asyncio.create_subprocess_exec(
                "aplay",
                "-q",
                "-f", "S16_LE",
                "-c", "2",
                "-r", "44100",
                "--buffer-time=200000",
                "--period-time=50000",
                "-",
                stdin=asyncio.subprocess.PIPE,
                stdout=asyncio.subprocess.DEVNULL,
                stderr=asyncio.subprocess.PIPE,
            )
            logger.info("[audio] persistent aplay started pid=%s", getattr(self.proc, "pid", None))

    def writer(self):
        if not self.proc or not self.proc.stdin:
            raise RuntimeError("aplay not started")
        return self.proc.stdin

    async def stop(self):
        async with self.lock:
            p = self.proc
            self.proc = None
        if not p:
            return
        try:
            p.terminate()
        except Exception:
            pass
        try:
            await asyncio.wait_for(p.wait(), timeout=1.0)
        except Exception:
            try:
                p.kill()
            except Exception:
                pass

_sink = PersistentAplaySink()

async def ensure_aplay_started():
    await _sink.ensure_started()
    return _sink

async def _pump(reader: asyncio.StreamReader, writer):
    # 여기서 writer.close() 하면 안 됨 (aplay를 살려놔야 함)
    total = 0
    try:
        while True:
            chunk = await reader.read(8192)
            if not chunk:
                break
            writer.write(chunk)
            await writer.drain()
            total += len(chunk)
    except asyncio.CancelledError:
        raise
    except Exception as e:
        logger.warning("[audio] pump error err=%r total=%d", e, total)
        raise

@dataclass
class AudioPlayback:
    ffmpeg: asyncio.subprocess.Process
    pump_task: asyncio.Task

    async def stop(self):
        if self.pump_task and not self.pump_task.done():
            self.pump_task.cancel()
            try:
                await self.pump_task
            except Exception:
                pass
        try:
            if self.ffmpeg and self.ffmpeg.returncode is None:
                self.ffmpeg.terminate()
        except Exception:
            pass
        try:
            await asyncio.wait_for(self.ffmpeg.wait(), timeout=1.0)
        except Exception:
            try:
                self.ffmpeg.kill()
            except Exception:
                pass

    async def wait(self):
        try:
            await self.pump_task
        finally:
            # ffmpeg만 정리, aplay는 유지
            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    self.ffmpeg.terminate()
            except Exception:
                pass
            try:
                await self.ffmpeg.wait()
            except Exception:
                pass

async def start_mp3_playback(path: str, *, volume: float = 1.0) -> AudioPlayback:
    volume = max(0.0, min(volume, 2.0))
    sink = await ensure_aplay_started()

    # preroll은 이제 줄이거나 0으로 해도 됨(취향)
    preroll_ms = 50

    ffmpeg = await asyncio.create_subprocess_exec(
        "ffmpeg", "-loglevel", "error",
        "-i", path,
        "-filter:a", f"adelay={preroll_ms}|{preroll_ms},volume={volume:.2f}",
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ac", "2",
        "-ar", "44100",
        "-",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )

    if ffmpeg.stdout is None:
        raise RuntimeError("ffmpeg.stdout is None")

    pump_task = asyncio.create_task(_pump(ffmpeg.stdout, sink.writer()))
    return AudioPlayback(ffmpeg=ffmpeg, pump_task=pump_task)
