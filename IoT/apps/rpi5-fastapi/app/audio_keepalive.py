
import asyncio
import time
import logging
import app.audio_play as ap
from .audio_play import _warmup_output_device
from .config import (
    AUDIO_OUT_DEVICE, AUDIO_RATE_HZ, AUDIO_CHANNELS,
    AUDIO_KEEPALIVE_SEC, AUDIO_KEEPALIVE_MS,
)

logger = logging.getLogger(__name__)

async def audio_keepalive_loop(state):
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

        if getattr(state, "fall_active", False):
            continue

        
        try:
            if getattr(state, "audio", None) and getattr(state.audio, "is_playing", False):
                continue
        except Exception:
            pass

        
        if getattr(state, "stt_busy", False):
            continue
        if getattr(state, "heavy_ops_pause", False):
            continue
        if getattr(state, "wifi_busy", False):
            continue

        try:
            await _warmup_output_device(out_dev=out_dev, rate_hz=rate, channels=ch, ms=ms)
            ap._WARMED = True
            ap._LAST_OUT_TS = time.time()
            logger.debug("[audio_keepalive] tick ok")
        except Exception as e:
            logger.debug("[audio_keepalive] tick fail err=%r", e)
