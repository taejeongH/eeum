import asyncio
import glob
import hashlib
import logging
import os
import re
import uuid
from dataclasses import dataclass
from typing import Optional
from gtts import gTTS
from app.config import DEFAULT_TTS_BY_KIND, DEFAULT_TTS_PATH
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

def _safe_slug(text: str, max_len: int = 64) -> str:
    s = re.sub(r"\s+", "_", (text or "").strip())
    s = re.sub(r"[^0-9A-Za-z가-힣_]+", "", s)
    return (s[:max_len] or "tts")

def _text_hash(text: str) -> str:
    h = hashlib.sha256((text or "").encode("utf-8")).hexdigest()
    return h[:10]

def _is_valid_cached_mp3(path: str, *, min_bytes: int = 1024) -> bool:
    try:
        return os.path.exists(path) and os.path.getsize(path) >= int(min_bytes)
    except Exception:
        return False

def _cleanup_old_tmp(out_dir: str, *, older_than_sec: float = 300.0, max_remove: int = 20) -> None:
    """
    오래된 tmp 파일을 제한적으로 정리합니다.

    :param out_dir: 출력 디렉토리
    :param older_than_sec: 이 시간(초)보다 오래된 것만 삭제
    :param max_remove: 최대 삭제 개수
    :return: 없음
    """
    try:
        now = now_ts()
        pattern = os.path.join(out_dir, "*.mp3.tmp.*")
        removed = 0

        for fp in sorted(glob.glob(pattern)):
            if removed >= max_remove:
                break
            try:
                st = os.stat(fp)
                if (now - st.st_mtime) >= float(older_than_sec):
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

def _build_tts_paths(text: str, out_dir: str) -> tuple[str, str]:
    slug = _safe_slug(text)
    h = _text_hash(text)
    path = os.path.join(out_dir, f"{slug}.{h}.mp3")
    tmp = path + f".tmp.{uuid.uuid4().hex}"
    return path, tmp

async def ensure_tts_mp3(
    text: str,
    out_dir: str,
    *,
    lang: str = "ko",
    timeout_sec: float = 8.0,
) -> TTSResult:
    """
    out_dir에 text 기반 mp3가 있으면 재사용하고, 없으면 gTTS로 생성합니다.
    실패해도 예외를 밖으로 던지지 않고 ok=False로 반환합니다.

    :param text: 변환할 텍스트
    :param out_dir: 저장 디렉토리
    :param lang: 언어 코드
    :param timeout_sec: 생성 타임아웃(초)
    :return: TTSResult
    """
    os.makedirs(out_dir, exist_ok=True)
    _cleanup_old_tmp(out_dir, older_than_sec=300.0, max_remove=20)

    path, tmp = _build_tts_paths(text, out_dir)

    if _is_valid_cached_mp3(path, min_bytes=1024):
        return TTSResult(ok=True, path=path, message="cache_hit", generated=False)

    def _blocking_generate() -> float:
        t0 = now_ts()
        tts = gTTS(text=text, lang=lang)
        tts.save(tmp)
        os.replace(tmp, path)
        return now_ts() - t0

    try:
        dt = await asyncio.wait_for(asyncio.to_thread(_blocking_generate), timeout=float(timeout_sec))
        logger.info("[tts] generated slug=%s dt=%.2fs", _safe_slug(text), dt)
        return TTSResult(ok=True, path=path, message="generated", generated=True)

    except asyncio.TimeoutError:
        logger.warning("[tts] generate timeout slug=%s timeout=%.1fs", _safe_slug(text), float(timeout_sec))
        return TTSResult(ok=False, path=None, message="timeout", generated=False)

    except Exception as e:
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
        except Exception:
            pass

        logger.warning(
            "[tts] generate failed slug=%s path=%s text=%r err=%s",
            _safe_slug(text),
            path,
            text,
            e,
            exc_info=True,
        )
        return TTSResult(ok=False, path=None, message=str(e), generated=False)

def get_default_tts_path(kind: str) -> str | None:
    """
    kind에 해당하는 기본 TTS 파일 경로를 반환합니다.
    파일이 없거나 깨졌으면 None을 반환합니다.

    :param kind: 타입 키(예: voice, fall 등)
    :return: mp3 파일 경로 또는 None
    """
    msg = DEFAULT_TTS_BY_KIND.get(kind)
    if not msg:
        return None

    slug = _safe_slug(msg)
    path = os.path.join(DEFAULT_TTS_PATH, f"{slug}.mp3")

    if not _is_valid_cached_mp3(path, min_bytes=1024):
        logger.warning("[default_tts] missing file kind=%s msg=%r path=%s", kind, msg, path)
        return None

    return path