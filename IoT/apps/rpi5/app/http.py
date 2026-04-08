import logging
from typing import Any, Dict, Optional
import aiohttp
from app.state import MonitorState
logger = logging.getLogger(__name__)

def _get_shared_session(state: MonitorState) -> aiohttp.ClientSession:
    session = getattr(state, "http_session", None)
    if session is None or getattr(session, "closed", True):
        logger.error(
            "[http] shared session missing/closed (BUG) session=%s closed=%s",
            session,
            getattr(session, "closed", None),
        )
        raise RuntimeError("state.http_session is not initialized or already closed")
    return session

def _build_timeout(timeout_sec: float) -> aiohttp.ClientTimeout:
    try:
        sec = float(timeout_sec)
        if sec <= 0:
            sec = 10.0
    except Exception:
        sec = 10.0
    return aiohttp.ClientTimeout(total=sec)

async def _read_text_safely(resp: aiohttp.ClientResponse, *, limit: int = 500) -> str:
    try:
        text = await resp.text(errors="ignore")
    except Exception:
        return ""
    return (text or "")[: int(limit)]

async def async_http_get_json(
    state: MonitorState,
    url: str,
    headers: Optional[Dict[str, str]] = None,
    params: Optional[Dict[str, Any]] = None,
    timeout_sec: float = 10.0,
) -> Dict[str, Any]:
    """
    공유 aiohttp 세션(state.http_session)을 사용하여 GET 요청 후 JSON(dict)을 반환합니다.

    정책:
    - state.http_session이 없거나 closed면 RuntimeError로 실패 처리합니다. (임시 세션 생성 금지)
    - HTTP 4xx/5xx면 body 일부를 로깅하고 resp.raise_for_status()로 예외를 발생시킵니다.
    - JSON 파싱 실패 시 body 일부를 로깅하고 예외를 발생시킵니다.

    :param state: MonitorState (http_session 보유)
    :param url: 요청 URL
    :param headers: 요청 헤더
    :param params: 쿼리 파라미터
    :param timeout_sec: 타임아웃(초)
    :return: JSON dict
    """
    session = _get_shared_session(state)
    headers = headers or {}
    params = params or {}
    timeout = _build_timeout(timeout_sec)

    async with session.get(url, headers=headers, params=params, timeout=timeout) as resp:
        if resp.status >= 400:
            body = await _read_text_safely(resp, limit=500)
            logger.error(
                "[http] error status=%s url=%s body=%r",
                resp.status,
                url,
                body,
            )
            resp.raise_for_status()

        try:
            data = await resp.json(content_type=None)
        except Exception:
            body = await _read_text_safely(resp, limit=500)
            logger.error("[http] invalid json url=%s body=%r", url, body)
            raise

        if isinstance(data, dict):
            return data
        return {"_raw": data}