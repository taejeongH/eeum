import asyncio
import logging
import os
import uvicorn
from app.api import create_app
from app.config import HOST, PORT
from app.state import MonitorState

logger = logging.getLogger(__name__)

def _get_log_level() -> str:
    level = (os.getenv("LOG_LEVEL", "INFO") or "INFO").strip().upper()
    return level or "INFO"

def setup_logging() -> None:
    """
    환경변수 LOG_LEVEL에 따라 로깅 레벨과 포맷을 설정합니다.

    :return: 없음
    """
    level = _get_log_level()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

async def async_main() -> None:
    """
    애플리케이션 비동기 진입점입니다.

    :return: 없음
    """
    setup_logging()

    state = MonitorState()
    app = create_app(state)

    config = uvicorn.Config(
        app,
        host=HOST,
        port=PORT,
        log_level=_get_log_level().lower(),
        access_log=False,
    )
    server = uvicorn.Server(config)
    await server.serve()

def main() -> None:
    """
    애플리케이션 동기 진입점입니다.

    :return: 없음
    """
    try:
        asyncio.run(async_main())
    except KeyboardInterrupt:
        return
    except Exception:
        logger.exception("[FATAL] unexpected error")
        raise

if __name__ == "__main__":
    main()