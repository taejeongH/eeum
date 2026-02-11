import hashlib
import os
from urllib.parse import urlparse, urlunparse
import aiohttp
from app.config import PROFILE_PATH

def _guess_ext(url: str) -> str:
    """
    URL 경로에서 이미지 확장자를 추출합니다.

    :param url: 원본 URL
    :returns: 추출된 확장자(.jpg 등) 또는 기본값 ".img"
    """
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

def cache_key_url(url: str) -> str:
    """
    캐시 키 생성을 위해 URL을 정규화합니다.
    presigned URL의 query/fragment는 제거합니다.

    :param url: 원본 URL
    :returns: query/fragment가 제거된 정규화 URL
    """
    u = (url or "").strip()
    if not u:
        return u

    if u.startswith("/profile/"):
        return u

    try:
        p = urlparse(u)
        if p.scheme in ("http", "https"):
            return urlunparse((p.scheme, p.netloc, p.path, "", "", ""))
    except Exception:
        pass

    return u

def profile_cache_filename(url: str) -> str:
    """
    URL을 기반으로 캐시 파일명을 생성합니다.

    :param url: 원본 URL
    :returns: 해시 기반 파일명 (예: <sha1>.jpg)
    """
    key = cache_key_url(url)
    h = hashlib.sha1(key.encode("utf-8")).hexdigest()
    return f"{h}{_guess_ext(key)}"

def profile_cache_local_path(url: str) -> str:
    """
    캐시 파일의 로컬 저장 경로를 반환합니다.

    :param url: 원본 URL
    :returns: 로컬 파일 시스템 경로
    """
    fn = profile_cache_filename(url)
    base = PROFILE_PATH or "./profile"
    return os.path.join(base, fn)

def profile_cache_public_url(url: str) -> str:
    """
    캐시 파일의 public URL(/profile/...)을 반환합니다.

    :param url: 원본 URL
    :returns: public 접근 경로
    """
    fn = profile_cache_filename(url)
    return f"/profile/{fn}"

def _safe_write_file(path: str, data: bytes) -> bool:
    """
    파일을 안전하게 저장합니다. (tmp → replace)

    :param path: 저장할 최종 경로
    :param data: 저장할 바이트 데이터
    :returns: 성공 여부
    """
    try:
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(data)
        os.replace(tmp, path)
        return True
    except Exception:
        try:
            if os.path.exists(path + ".tmp"):
                os.remove(path + ".tmp")
        except Exception:
            pass
        return False

def _safe_remove(path: str) -> None:
    """
    파일을 안전하게 삭제합니다. (best-effort)

    :param path: 삭제할 파일 경로
    :returns: None
    """
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass

def remove_profile_cached(remote_url: str) -> bool:
    """
    원격 URL에 대응하는 캐시 파일을 삭제합니다.

    :param remote_url: 원본 프로필 이미지 URL
    :returns: 삭제 성공 여부
    """
    u = (remote_url or "").strip()
    if not u:
        return False

    dst = profile_cache_local_path(u)
    try:
        if os.path.exists(dst):
            _safe_remove(dst)
            _safe_remove(dst + ".tmp")
            return True
    except Exception:
        pass
    return False

def remove_profile_cached_by_public_url(public_url: str) -> bool:
    """
    public URL(/profile/...) 기준으로 캐시 파일을 삭제합니다.

    :param public_url: /profile/<filename> 형태의 URL
    :returns: 삭제 성공 여부
    """
    p = (public_url or "").strip()
    if not p.startswith("/profile/"):
        return False

    fn = p.split("/profile/", 1)[-1].strip()
    if not fn:
        return False

    base = PROFILE_PATH or "./profile"
    dst = os.path.join(base, fn)

    try:
        if os.path.exists(dst):
            _safe_remove(dst)
            _safe_remove(dst + ".tmp")
            return True
    except Exception:
        pass
    return False

async def ensure_profile_cached(
    session: aiohttp.ClientSession,
    remote_url: str,
    timeout_sec: float = 10.0,
) -> str:
    """
    원격 프로필 이미지를 로컬에 캐싱합니다.

    :param session: aiohttp 세션
    :param remote_url: 원본 프로필 이미지 URL
    :param timeout_sec: 다운로드 타임아웃(초)
    :returns: 성공 시 /profile/<filename>, 실패 시 원본 URL
    """
    u = (remote_url or "").strip()
    if not u:
        return remote_url

    if u.startswith("/profile/"):
        return u

    base_dir = PROFILE_PATH or "./profile"
    os.makedirs(base_dir, exist_ok=True)

    dst = profile_cache_local_path(u)
    pub = profile_cache_public_url(u)

    try:
        if os.path.exists(dst) and os.path.getsize(dst) > 0:
            return pub
    except Exception:
        pass

    if session is None or session.closed:
        return remote_url

    try:
        async with session.get(u, timeout=aiohttp.ClientTimeout(total=float(timeout_sec))) as r:
            if r.status < 200 or r.status >= 300:
                return remote_url
            data = await r.read()
            if not data:
                return remote_url

        ok = _safe_write_file(dst, data)
        return pub if ok else remote_url

    except Exception:
        return remote_url