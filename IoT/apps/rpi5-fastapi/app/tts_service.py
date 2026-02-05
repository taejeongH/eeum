# app/tts_service.py
import os
import re
import time
import asyncio
import logging
from dataclasses import dataclass
from typing import Optional
from app.config import DEFAULT_TTS_PATH, DEFAULT_TTS_BY_KIND

logger = logging.getLogger(__name__)

from gtts import gTTS  # 인터넷 필요

logger = logging.getLogger(__name__)

def _safe_slug(text: str, max_len: int = 64) -> str:
    s = re.sub(r"\s+", "_", (text or "").strip())
    s = re.sub(r"[^0-9A-Za-z가-힣_]+", "", s)
    return (s[:max_len] or "tts")

@dataclass
class TTSResult:
    ok: bool
    path: Optional[str] = None
    message: str = ""
    generated: bool = False

async def ensure_tts_mp3(
    text: str,
    out_dir: str,
    *,
    lang: str = "ko",
    timeout_sec: float = 8.0,
) -> TTSResult:
    """
    - out_dir에 text 기반 파일이 있으면 재사용
    - 없으면 gTTS로 생성 시도
    - 실패해도 예외 밖으로 던지지 않고 ok=False로 반환
    """
    os.makedirs(out_dir, exist_ok=True)
    slug = _safe_slug(text)
    path = os.path.join(out_dir, f"{slug}.mp3")

    if os.path.exists(path) and os.path.getsize(path) > 0:
        return TTSResult(ok=True, path=path, message="cache_hit", generated=False)

    def _blocking_generate():
        t0 = time.time()
        tts = gTTS(text=text, lang=lang)
        tts.save(path)
        return time.time() - t0

    try:
        dt = await asyncio.wait_for(asyncio.to_thread(_blocking_generate), timeout=timeout_sec)
        logger.info("[tts] generated slug=%s dt=%.2fs", slug, dt)
        return TTSResult(ok=True, path=path, message="generated", generated=True)
    except Exception as e:
        # 인터넷/Wi-Fi 끊김 포함
        logger.warning(
            "[tts] generate failed slug=%s path=%s text=%r err=%s",
            slug, path, text, e,
            exc_info=True,
        )
        return TTSResult(ok=False, path=None, message=str(e), generated=False)

def get_default_tts_path(kind: str) -> str | None:
    msg = DEFAULT_TTS_BY_KIND.get(kind)
    if not msg:
        return None
    slug = _safe_slug(msg)
    path = os.path.join(DEFAULT_TTS_PATH, f"{slug}.mp3")
    if not os.path.exists(path):
        logger.warning("[default_tts] missing file kind=%s msg=%r path=%s", kind, msg, path)
        return None
    return path