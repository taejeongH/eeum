# app/audio_play.py
import asyncio
import logging
from typing import Any
from dataclasses import dataclass

logger = logging.getLogger(__name__)

@dataclass
class AudioPlayback:
    ffmpeg: asyncio.subprocess.Process
    aplay: asyncio.subprocess.Process
    pump_task: asyncio.Task | None = None

    async def stop(self):
        logger.debug("[audio_play] stop() called")
        # pump 먼저 중단
        if self.pump_task and not self.pump_task.done():
            logger.debug("[audio_play] cancelling pump_task")
            self.pump_task.cancel()
            try:
                await self.pump_task
            except asyncio.CancelledError:
                logger.debug("[audio_play] pump_task cancelled")
            except Exception as e:
                logger.debug("[audio_play] pump_task cancel wait err=%r", e)

        for name, p in (("aplay", self.aplay), ("ffmpeg", self.ffmpeg)):
            try:
                if p and p.returncode is None:
                    logger.debug("[audio_play] terminate %s pid=%s", name, getattr(p, "pid", None))
                    p.terminate()
            except Exception as e:
                logger.debug("[audio_play] terminate %s err=%r", name, e)

        await asyncio.sleep(0.05)

        for name, p in (("aplay", self.aplay), ("ffmpeg", self.ffmpeg)):
            try:
                if p and p.returncode is None:
                    logger.debug("[audio_play] kill %s pid=%s", name, getattr(p, "pid", None))
                    p.kill()
            except Exception as e:
                logger.debug("[audio_play] kill %s err=%r", name, e)

        for name, p in (("aplay", self.aplay), ("ffmpeg", self.ffmpeg)):
            try:
                rc = await p.wait()
                logger.debug("[audio_play] %s wait done rc=%s", name, rc)
            except Exception as e:
                logger.debug("[audio_play] %s wait err=%r", name, e)

    async def wait(self):
        logger.debug(
            "[audio_play] wait() start ffmpeg_pid=%s aplay_pid=%s",
            getattr(self.ffmpeg, "pid", None),
            getattr(self.aplay, "pid", None),
        )
        try:
            if self.pump_task:
                try:
                    await self.pump_task
                    logger.debug("[audio_play] pump_task done")
                except asyncio.CancelledError:
                    logger.debug("[audio_play] wait() pump_task cancelled")
                    raise
                except Exception as e:
                    logger.warning("[audio_play] pump_task failed err=%r", e)

            out, err = await self.aplay.communicate()
            rc_aplay = self.aplay.returncode
            
            if err:
                logger.warning("[audio_play] aplay stderr: %s", err.decode("utf-8", "ignore").strip())
            logger.debug("[audio_play] aplay exited rc=%s", rc_aplay)

        finally:
            # ffmpeg 정리
            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    logger.debug("[audio_play] terminate ffmpeg pid=%s", getattr(self.ffmpeg, "pid", None))
                    self.ffmpeg.terminate()
            except Exception as e:
                logger.debug("[audio_play] terminate ffmpeg err=%r", e)

            try:
                rc_ff = await self.ffmpeg.wait()
                logger.debug("[audio_play] ffmpeg exited rc=%s", rc_ff)
            except Exception as e:
                logger.debug("[audio_play] ffmpeg wait err=%r", e)

        logger.debug("[audio_play] wait() done")

async def _pump(reader: asyncio.StreamReader, writer: Any):
    total = 0
    chunks = 0
    logger.debug("[audio_play] pump start")
    try:
        while True:
            chunk = await reader.read(8192)
            if not chunk:
                logger.debug("[audio_play] pump EOF total_bytes=%d chunks=%d", total, chunks)
                break
            writer.write(chunk)
            await writer.drain()
            total += len(chunk)
            chunks += 1
            if chunks % 200 == 0:
                # 너무 스팸되지 않게 200 chunk마다 한 번만
                logger.debug("[audio_play] pump progress bytes=%d chunks=%d", total, chunks)
    except asyncio.CancelledError:
        logger.debug("[audio_play] pump cancelled total_bytes=%d chunks=%d", total, chunks)
        raise
    except Exception as e:
        logger.warning("[audio_play] pump error err=%r total_bytes=%d chunks=%d", e, total, chunks)
        raise
    finally:
        try:
            writer.close()
            await writer.wait_closed()
            logger.debug("[audio_play] pump writer closed")
        except Exception as e:
            logger.debug("[audio_play] pump writer close err=%r", e)

async def start_mp3_playback(path: str, *, volume: float = 1.0) -> AudioPlayback:
    volume = max(0.0, min(volume, 2.0))
    logger.debug("[audio_play] start_mp3_playback path=%s volume=%.2f", path, volume)

    ffmpeg = await asyncio.create_subprocess_exec(
        "ffmpeg", "-loglevel", "error",
        "-i", path,
        "-filter:a", f"volume={volume:.2f}",
        "-f", "s16le",
        "-acodec", "pcm_s16le",
        "-ac", "2",
        "-ar", "44100",
        "-",
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.DEVNULL,
    )
    logger.debug("[audio_play] ffmpeg started pid=%s", getattr(ffmpeg, "pid", None))

    aplay = await asyncio.create_subprocess_exec(
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
    logger.debug("[audio_play] aplay started pid=%s", getattr(aplay, "pid", None))

    if ffmpeg.stdout is None:
        logger.error("[audio_play] ffmpeg.stdout is None (unexpected)")
        raise RuntimeError("ffmpeg.stdout is None")
    if aplay.stdin is None:
        logger.error("[audio_play] aplay.stdin is None (unexpected)")
        raise RuntimeError("aplay.stdin is None")

    pump_task = asyncio.create_task(_pump(ffmpeg.stdout, aplay.stdin))
    logger.debug("[audio_play] pump_task created")

    return AudioPlayback(ffmpeg=ffmpeg, aplay=aplay, pump_task=pump_task)
