import asyncio
import json
import logging
import os
import signal
from dataclasses import dataclass
from typing import Optional
from app.config import (
    AUDIO_OUT_DEVICE,
    AUDIO_RATE_HZ,
    AUDIO_CHANNELS,
    AUDIO_PREROLL_MS,
    AUDIO_APLAY_BUFFER_TIME_US,
    AUDIO_APLAY_PERIOD_TIME_US,
    AUDIO_LOG_STDERR,
    AUDIO_SOFT_STOP,
    AUDIO_FFPROBE_PROBESIZE,
    AUDIO_FFMPEG_ANALYZE_DURATION,
    AUDIO_WARMUP_MS,
    AUDIO_REWARM_IDLE_SEC,
)
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

_WARMED = False
_LAST_OUT_TS = 0.0

async def _probe_audio_duration_sec(path: str, *, timeout_sec: float = 2.0) -> Optional[float]:
    """
    ffprobe로 duration을 조회합니다. 실패하면 None을 반환합니다.

    :param path: 오디오 파일 경로
    :param timeout_sec: 타임아웃(초)
    :return: duration(초) 또는 None
    """
    try:
        p = await asyncio.create_subprocess_exec(
            "ffprobe",
            "-v",
            "error",
            "-show_entries",
            "format=duration",
            "-of",
            "json",
            path,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.DEVNULL,
        )
        out, _ = await asyncio.wait_for(p.communicate(), timeout=timeout_sec)
        if p.returncode != 0:
            return None

        payload = json.loads((out or b"{}").decode(errors="replace") or "{}")
        duration = float((payload.get("format") or {}).get("duration") or 0.0)
        if duration <= 0:
            return None
        return duration
    except Exception:
        return None

async def _warmup_output_device(*, out_dev: str, rate_hz: int, channels: int, ms: int = 200) -> None:
    """
    출력 장치를 깨워 첫 음절 잘림을 완화합니다.
    짧은 무음 PCM을 aplay로 흘려 장치 오픈/클럭 lock을 유도합니다.

    :param out_dev: ALSA 출력 디바이스
    :param rate_hz: 샘플레이트
    :param channels: 채널 수
    :param ms: 재생 시간(ms)
    :return: 없음
    """
    frames = int(rate_hz * (ms / 1000.0))
    bytes_len = frames * channels * 2  # s16le
    silent = b"\x00" * bytes_len

    p = await asyncio.create_subprocess_exec(
        "aplay",
        "-q",
        "-D",
        out_dev,
        "-f",
        "S16_LE",
        "-c",
        str(channels),
        "-r",
        str(rate_hz),
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

async def _drain_stderr(prefix: str, reader: Optional[asyncio.StreamReader], *, max_lines: int = 200) -> None:
    if reader is None:
        return

    lines = 0
    fp = None
    try:
        os.makedirs("./logs", exist_ok=True)
        fp = open(
            f"./logs/audio_{prefix}_{int(now_ts()*1000)}.log",
            "a",
            encoding="utf-8",
            errors="replace",
        )
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

async def _pump(reader: asyncio.StreamReader, writer: asyncio.StreamWriter) -> None:
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

def _should_rewarm(now: float) -> bool:
    global _WARMED, _LAST_OUT_TS
    idle_sec = float(AUDIO_REWARM_IDLE_SEC or 15)
    if not _WARMED:
        return True
    if _LAST_OUT_TS > 0 and (now - _LAST_OUT_TS) >= idle_sec:
        return True
    return False

def _update_warm_state(now: float) -> None:
    global _WARMED, _LAST_OUT_TS
    _WARMED = True
    _LAST_OUT_TS = now

def _normalize_channels(channels: int) -> int:
    return channels if channels in (1, 2) else 1

@dataclass
class AudioPlayback:
    ffmpeg: asyncio.subprocess.Process
    aplay: asyncio.subprocess.Process
    pump_task: asyncio.Task
    ffmpeg_stderr_task: Optional[asyncio.Task] = None
    aplay_stderr_task: Optional[asyncio.Task] = None
    src_duration_sec: Optional[float] = None
    """
    ffmpeg -> aplay 파이프라인 기반 오디오 재생 핸들.

    :param ffmpeg: ffmpeg 디코딩 프로세스
    :param aplay: ALSA 출력 프로세스
    :param pump_task: ffmpeg stdout → aplay stdin 전달 태스크
    :param ffmpeg_stderr_task: ffmpeg stderr drain 태스크 (옵션)
    :param aplay_stderr_task: aplay stderr drain 태스크 (옵션)
    :param src_duration_sec: 소스 오디오 길이(초, 없으면 None)
    """
    async def _close_aplay_stdin_eof(self) -> None:
        try:
            if self.aplay and self.aplay.stdin:
                try:
                    self.aplay.stdin.close()
                except Exception:
                    pass
        except Exception:
            pass

    async def _soft_stop(self) -> None:
        """
        HDMI 안정성 우선 stop:
        - pump_task 중지
        - aplay stdin EOF로 자연 종료 유도
        - ffmpeg는 SIGTERM 후 짧게 대기
        """
        if self.pump_task and not self.pump_task.done():
            self.pump_task.cancel()
            try:
                await self.pump_task
            except Exception:
                pass

        await self._close_aplay_stdin_eof()

        try:
            if self.ffmpeg and self.ffmpeg.returncode is None:
                try:
                    self.ffmpeg.send_signal(signal.SIGTERM)
                except Exception:
                    self.ffmpeg.terminate()
        except Exception:
            pass

    async def stop(self) -> None:
        """
        재생 중단을 수행합니다.
        기본값은 HDMI drop 완화 목적의 soft stop을 사용합니다.

        :return: 없음
        """
        soft = AUDIO_SOFT_STOP

        if soft:
            await self._soft_stop()
        else:
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

        if not soft:
            for p in (self.ffmpeg, self.aplay):
                try:
                    if p and p.returncode is None:
                        p.terminate()
                except Exception:
                    pass
        else:
            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    self.ffmpeg.terminate()
            except Exception:
                pass

        try:
            if self.ffmpeg:
                await asyncio.wait_for(self.ffmpeg.wait(), timeout=1.0)
        except Exception:
            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    self.ffmpeg.kill()
            except Exception:
                pass

        try:
            if self.aplay:
                if soft:
                    await asyncio.wait_for(self.aplay.wait(), timeout=2.5)
                else:
                    await asyncio.wait_for(self.aplay.wait(), timeout=1.0)
        except Exception:
            try:
                if self.aplay and self.aplay.returncode is None:
                    self.aplay.terminate()
            except Exception:
                pass
            try:
                if self.aplay:
                    await asyncio.wait_for(self.aplay.wait(), timeout=1.0)
            except Exception:
                try:
                    if self.aplay and self.aplay.returncode is None:
                        self.aplay.kill()
                except Exception:
                    pass

    async def wait(self) -> None:
        """
        재생 완료까지 대기합니다.
        aplay는 stdin EOF 후에도 버퍼 draining이 필요하므로 충분히 기다립니다.

        :return: 없음
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

            try:
                if self.ffmpeg and self.ffmpeg.returncode is None:
                    await asyncio.wait_for(self.ffmpeg.wait(), timeout=2.0)
            except Exception:
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

            try:
                if self.aplay and self.aplay.returncode is None:
                    if self.src_duration_sec and self.src_duration_sec > 0:
                        timeout_sec = float(self.src_duration_sec) + 8.0
                        timeout_sec = max(15.0, min(180.0, timeout_sec))
                    else:
                        timeout_sec = 60.0
                    await asyncio.wait_for(self.aplay.wait(), timeout=timeout_sec)
            except Exception:
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

def _build_ffmpeg_audio_filter(*, channels: int, preroll_ms: int, volume: float) -> str:
    base = "aresample=async=1:first_pts=0"

    if channels == 2:
        delay = f"{preroll_ms}|{preroll_ms}"
    else:
        delay = f"{preroll_ms}"

    if preroll_ms > 0:
        return f"adelay={delay},{base},afade=t=in:ss=0:d=0.03,volume={volume:.2f}"
    return f"{base},volume={volume:.2f}"

async def start_mp3_playback(path: str, *, volume: float = 1.0, start_delay_ms: int = 100) -> AudioPlayback:
    """
    mp3 파일을 ffmpeg -> pcm -> aplay 파이프로 재생합니다.

    :param path: mp3 파일 경로
    :param volume: 볼륨 배율(0.0~2.0)
    :param start_delay_ms: 시작 지연(ms)
    :return: AudioPlayback 핸들
    """
    volume = max(0.0, min(float(volume), 2.0))

    if start_delay_ms and start_delay_ms > 0:
        await asyncio.sleep(start_delay_ms / 1000.0)

    # 재생 안정성용 duration(실패해도 무방)
    dur_sec = await _probe_audio_duration_sec(path, timeout_sec=2.0)

    out_dev = (AUDIO_OUT_DEVICE or "default").strip() or "default"
    rate_hz = int(AUDIO_RATE_HZ or 48000)
    channels = _normalize_channels(int(AUDIO_CHANNELS or 2))

    preroll_ms = AUDIO_PREROLL_MS
    preroll_ms = max(0, min(preroll_ms, 5000))

    log_stderr = AUDIO_LOG_STDERR

    now = now_ts()
    if _should_rewarm(now):
        try:
            await _warmup_output_device(
                out_dev=out_dev,
                rate_hz=rate_hz,
                channels=channels,
                ms=int(AUDIO_WARMUP_MS or 700),
            )
            logger.info(
                "[audio] warmup done dev=%s rate=%d ch=%d ms=%d",
                out_dev,
                rate_hz,
                channels,
                int(AUDIO_WARMUP_MS or 700),
            )
        except Exception as e:
            logger.info("[audio] warmup skipped err=%r", e)
        _update_warm_state(now)
    else:
        _update_warm_state(now)

    buffer_time = AUDIO_APLAY_BUFFER_TIME_US
    period_time = AUDIO_APLAY_PERIOD_TIME_US

    aplay = await asyncio.create_subprocess_exec(
        "aplay",
        "-q",
        "-D",
        out_dev,
        "-f",
        "S16_LE",
        "-c",
        str(channels),
        "-r",
        str(rate_hz),
        f"--buffer-time={buffer_time}",
        f"--period-time={period_time}",
        "-",
        stdin=asyncio.subprocess.PIPE,
        stdout=asyncio.subprocess.DEVNULL,
        stderr=(asyncio.subprocess.PIPE if log_stderr else asyncio.subprocess.DEVNULL),
    )
    if aplay.stdin is None:
        raise RuntimeError("aplay.stdin is None")

    probesize = AUDIO_FFPROBE_PROBESIZE
    analyzedur = AUDIO_FFMPEG_ANALYZE_DURATION

    af = _build_ffmpeg_audio_filter(channels=channels, preroll_ms=preroll_ms, volume=volume)

    ffmpeg = await asyncio.create_subprocess_exec(
        "ffmpeg",
        "-nostdin",
        "-loglevel",
        "error",
        "-probesize",
        probesize,
        "-analyzeduration",
        analyzedur,
        "-threads",
        "1",
        "-vn",
        "-sn",
        "-dn",
        "-i",
        path,
        "-filter:a",
        af,
        "-f",
        "s16le",
        "-acodec",
        "pcm_s16le",
        "-ac",
        str(channels),
        "-ar",
        str(rate_hz),
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