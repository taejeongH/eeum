import logging
from app.sh import async_sh

logger = logging.getLogger(__name__)

async def ensure_master_volume_100() -> None:
    """
    시스템 Master 볼륨을 100%로 고정합니다.

    :return: 없음
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