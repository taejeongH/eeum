# app/http.py
import aiohttp
import logging
from typing import Any, Dict
from .state import MonitorState

logger = logging.getLogger(__name__)

async def async_http_get_json(
    state: MonitorState,
    url: str,
    headers: Dict[str, str] | None = None,
    params: Dict[str, Any] | None = None,
    timeout_sec: float = 10.0,
) -> Dict[str, Any]:
    headers = headers or {}
    params = params or {}

    session = getattr(state, "http_session", None)
    if session is None or session.closed:
        # 여기서 임시 세션 만들면 정책이 깨짐 -> 바로 실패 처리
        logger.error(
            "[http] shared session missing/closed (BUG) url=%s session=%s closed=%s",
            url,
            session,
            getattr(session, "closed", None),
        )
        raise RuntimeError("state.http_session is not initialized or already closed")

    async with session.get(
        url,
        headers=headers,
        params=params,
        timeout=aiohttp.ClientTimeout(total=timeout_sec),
    ) as resp:
        if resp.status >= 400:
            try:
                body = await resp.text()
            except Exception:
                body = ""
            logger.error("[http] error status=%s url=%s body=%r", resp.status, url, (body or "")[:500])
            resp.raise_for_status()

        try:
            return await resp.json(content_type=None)
        except Exception:
            text = await resp.text()
            logger.error("[http] invalid json url=%s body=%r", url, (text or "")[:500])
            raise
