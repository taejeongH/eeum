# app/tts_service.py
import os
import re
import time
import asyncio
import logging
import uuid
import glob
import hashlib
from dataclasses import dataclass
from typing import Optional
from app.config import DEFAULT_TTS_PATH, DEFAULT_TTS_BY_KIND
from gtts import gTTS  # 인터넷 필요

logger = logging.getLogger(__name__)

def _safe_slug(text: str, max_len: int = 64) -> str:
    s = re.sub(r"\s+", "_", (text or "").strip())
    s = re.sub(r"[^0-9A-Za-z가-힣_]+", "", s)
    return (s[:max_len] or "tts")

def _text_hash(text: str) -> str:
    # 파일명 충돌 방지용: 짧고 안정적인 해시(앞 10자면 충분)
    h = hashlib.sha256((text or "").encode("utf-8")).hexdigest()
    return h[:10]

def _is_valid_cached_mp3(path: str, *, min_bytes: int = 1024) -> bool:
    # 깨진/빈 파일을 cache_hit로 잘못 판단하지 않도록 최소 크기 검사
    try:
        return os.path.exists(path) and os.path.getsize(path) >= int(min_bytes)
    except Exception:
        return False

def _cleanup_old_tmp(out_dir: str, *, older_than_sec: float = 300.0, max_remove: int = 20) -> None:
    """
    timeout/cancel로 남을 수 있는 *.tmp.* 파일 정리
    - 너무 공격적으로 지우면 동시 실행 중인 tmp를 건드릴 수 있으니
      '오래된 것만' 제한적으로 삭제
    """
    try:
        now = time.time()
        pat = os.path.join(out_dir, "*.mp3.tmp.*")
        removed = 0
        for fp in sorted(glob.glob(pat)):
            if removed >= max_remove:
                break
            try:
                st = os.stat(fp)
                if (now - st.st_mtime) >= older_than_sec:
                    os.remove(fp)
                    removed += 1
            except Exception:
                pass
    except Exception:
        pass

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
    _cleanup_old_tmp(out_dir, older_than_sec=300.0, max_remove=20)
    slug = _safe_slug(text)
    # ★ slug만 쓰면 서로 다른 문장이 같은 파일명으로 수렴할 수 있음 -> 해시를 붙여 충돌 방지
    h = _text_hash(text)
    path = os.path.join(out_dir, f"{slug}.{h}.mp3")
    tmp = path + f".tmp.{uuid.uuid4().hex}"

    if _is_valid_cached_mp3(path, min_bytes=1024):
        return TTSResult(ok=True, path=path, message="cache_hit", generated=False)

    def _blocking_generate():
        t0 = time.time()
        tts = gTTS(text=text, lang=lang)
        tts.save(tmp)
        # 저장 성공한 tmp만 최종 반영(원자적)
        os.replace(tmp, path)
        return time.time() - t0

    try:
        dt = await asyncio.wait_for(asyncio.to_thread(_blocking_generate), timeout=timeout_sec)
        logger.info("[tts] generated slug=%s dt=%.2fs", slug, dt)
        return TTSResult(ok=True, path=path, message="generated", generated=True)
    except asyncio.TimeoutError as e:
        logger.warning("[tts] generate timeout slug=%s timeout=%.1fs", slug, float(timeout_sec))
        return TTSResult(ok=False, path=None, message="timeout", generated=False)
    except Exception as e:
        # 인터넷/Wi-Fi 끊김 포함
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
        except Exception:
            pass
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
    # default 파일은 기존 규칙 유지(프로비저닝된 파일명과 호환)
    path = os.path.join(DEFAULT_TTS_PATH, f"{slug}.mp3")
    if not _is_valid_cached_mp3(path, min_bytes=1024):
        logger.warning("[default_tts] missing file kind=%s msg=%r path=%s", kind, msg, path)
        return None
    return path