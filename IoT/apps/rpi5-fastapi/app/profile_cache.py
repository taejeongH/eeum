# app/profile_cache.py
import os
import hashlib
from urllib.parse import urlparse
import aiohttp
from .config import PROFILE_PATH

def _guess_ext(url: str) -> str:
    try:
        path = urlparse(url).path or ""
        base = os.path.basename(path)
        _, ext = os.path.splitext(base)
        ext = (ext or "").lower()
        if ext in (".jpg", ".jpeg", ".png", ".webp", ".gif"):
            return ext
    except Exception:
        pass
    return ".img"

def profile_cache_filename(url: str) -> str:
    u = (url or "").strip()
    h = hashlib.sha1(u.encode("utf-8")).hexdigest()
    return f"{h}{_guess_ext(u)}"

def profile_cache_local_path(url: str) -> str:
    fn = profile_cache_filename(url)
    base = PROFILE_PATH or "./profile"
    return os.path.join(base, fn)

def profile_cache_public_url(url: str) -> str:
    fn = profile_cache_filename(url)
    return f"/profile/{fn}"

async def ensure_profile_cached(session: aiohttp.ClientSession, remote_url: str, timeout_sec: float = 10.0) -> str:
    """
    remote_url을 PROFILE_PATH에 캐싱한다.
    성공/이미존재: /profile/<fn>
    실패: 원본 remote_url 그대로 반환 (스키마 변경 없이 값만 fallback)
    """
    u = (remote_url or "").strip()
    if not u:
        return remote_url

    os.makedirs(PROFILE_PATH or "./profile", exist_ok=True)

    dst = profile_cache_local_path(u)
    pub = profile_cache_public_url(u)

    # 이미 있으면 그대로 반환
    if os.path.exists(dst) and os.path.getsize(dst) > 0:
        return pub

    if session is None or session.closed:
        return remote_url

    tmp = dst + ".tmp"
    try:
        async with session.get(u, timeout=aiohttp.ClientTimeout(total=float(timeout_sec))) as r:
            if r.status < 200 or r.status >= 300:
                return remote_url
            data = await r.read()
            if not data:
                return remote_url
            with open(tmp, "wb") as f:
                f.write(data)
        os.replace(tmp, dst)
        return pub
    except Exception:
        # tmp 정리
        try:
            if os.path.exists(tmp):
                os.remove(tmp)
        except Exception:
            pass
        return remote_url
