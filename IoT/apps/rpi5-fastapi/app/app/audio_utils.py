# app/audio_utils.py
import logging
from .sh import async_sh

logger = logging.getLogger(__name__)

async def ensure_master_volume_100():
    """
    시스템 Master 볼륨을 100%로 고정
    (aplay / ffmpeg 증폭 전제)
    """
    try:
        await async_sh(
            ["amixer", "sset", "Master", "100%"],
            check=False,
            timeout=3.0,
        )
        logger.info("[audio] Master volume set to 100%")
    except Exception as e:
        logger.warning("[audio] failed to set Master volume: %s", e)
