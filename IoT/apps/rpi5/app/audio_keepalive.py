import asyncio
import logging
import app.audio_play as ap
from app.audio_play import _warmup_output_device
from app.config import (
    AUDIO_OUT_DEVICE,
    AUDIO_RATE_HZ,
    AUDIO_CHANNELS,
    AUDIO_KEEPALIVE_SEC,
    AUDIO_KEEPALIVE_MS,
)
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

def _should_skip_keepalive(state) -> bool:
    """
    keepalive tick을 스킵해야 하는지 판단합니다.
    
    :param state: 전역 상태
    :returns: 스킵 여부(True면 keepalive 수행하지 않음)
    """
    if getattr(state, "shutting_down", False):
        return True
    if getattr(state, "fall_active", False):
        return True

    try:
        if getattr(state, "audio", None) and getattr(state.audio, "is_playing", False):
            return True
    except Exception:
        pass

    if getattr(state, "stt_busy", False):
        return True
    if getattr(state, "heavy_ops_pause", False):
        return True
    if getattr(state, "wifi_busy", False):
        return True

    return False

async def audio_keepalive_loop(state) -> None:
    """
    오디오 출력 장치를 주기적으로 깨워 HDMI/출력 드랍을 완화합니다.

    설정:
    - AUDIO_KEEPALIVE_SEC <= 0 이면 비활성화
    - 출력 디바이스/샘플레이트/채널/웜업 길이는 config 값 사용

    :param state: 전역 상태
    :returns: None
    """
    sec = float(AUDIO_KEEPALIVE_SEC or 0)
    if sec <= 0:
        logger.info("[audio_keepalive] disabled")
        return

    out_dev = (AUDIO_OUT_DEVICE or "default").strip() or "default"
    rate = int(AUDIO_RATE_HZ or 48000)
    ch = int(AUDIO_CHANNELS or 2)
    if ch not in (1, 2):
        ch = 1
    ms = int(AUDIO_KEEPALIVE_MS or 160)

    logger.info("[audio_keepalive] started interval=%.1fs ms=%d dev=%s", sec, ms, out_dev)

    while not getattr(state, "shutting_down", False):
        await asyncio.sleep(sec)

        if _should_skip_keepalive(state):
            continue

        try:
            await _warmup_output_device(out_dev=out_dev, rate_hz=rate, channels=ch, ms=ms)
            ap._WARMED = True
            ap._LAST_OUT_TS = now_ts()
            logger.debug("[audio_keepalive] tick ok")
        except Exception as e:
            logger.debug("[audio_keepalive] tick fail err=%r", e)
