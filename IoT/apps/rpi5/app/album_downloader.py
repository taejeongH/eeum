import asyncio
import hashlib
import logging
import os
from urllib.parse import urlparse
import aiohttp
from app.config import ALBUM_PATH
from app.sync_utils import calc_backoff_sec, now_ts

logger = logging.getLogger(__name__)

def _ext_from_url(url: str) -> str:
    try:
        parsed = urlparse(url)
        filename = os.path.basename(parsed.path)
        _, ext = os.path.splitext(filename)
        ext = (ext or "").lower()
        if ext in (".jpg", ".jpeg", ".png", ".webp"):
            return ext
    except Exception:
        pass
    return ".jpg"

def _make_paths(root: str, pid: int, url: str) -> tuple[str, str]:
    ext = _ext_from_url(url)
    tmp_path = os.path.join(root, f"{pid}{ext}.tmp")
    final_path = os.path.join(root, f"{pid}{ext}")
    return tmp_path, final_path

async def _fetch_to_file(session: aiohttp.ClientSession, url: str, tmp_path: str) -> tuple[int, str]:
    """
    url을 다운로드해서 tmp_path에 저장하고 sha256을 계산합니다.

    :param session: aiohttp 세션
    :param url: 다운로드 URL
    :param tmp_path: 임시 파일 경로
    :return: (bytes, sha256_hex)
    """
    async with session.get(url, timeout=aiohttp.ClientTimeout(total=20.0)) as resp:
        resp.raise_for_status()

        sha256 = hashlib.sha256()
        total_bytes = 0

        with open(tmp_path, "wb") as f:
            async for chunk in resp.content.iter_chunked(64 * 1024):
                if not chunk:
                    continue
                f.write(chunk)
                total_bytes += len(chunk)
                sha256.update(chunk)

    return total_bytes, sha256.hexdigest()

def _safe_remove(path: str) -> None:
    try:
        if path and os.path.exists(path):
            os.remove(path)
    except Exception:
        pass

def _cleanup_other_ext_files(root: str, pid: int, keep_path: str) -> None:
    """
    pid 기반으로 생성된 앨범 파일 중 keep_path를 제외한 나머지를 제거합니다.
    (예: URL 변경으로 확장자가 바뀌면 pid.jpg, pid.png 등이 공존할 수 있음)

    :param root: 앨범 저장 루트
    :param pid: photo id
    :param keep_path: 유지해야 하는 최종 파일 경로
    :return: None
    """
    try:
        prefix = os.path.join(root, f"{pid}")
        for fn in os.listdir(root):
            p = os.path.join(root, fn)
            if not p.startswith(prefix):
                continue
            if p == keep_path or p == keep_path + ".tmp":
                continue
            if os.path.isfile(p):
                _safe_remove(p)
    except Exception:
        return

def _mark_downloading(repo, pid: int, final_path: str) -> None:
    try:
        repo.set_download_status(
            pid,
            "downloading",
            local_path=final_path,
            last_error=None,
            inc_retry=False,
            next_try_at=None,
        )
    except Exception:
        pass

def _mark_done(repo, pid: int, final_path: str, *, bytes_: int, sha256: str) -> None:
    repo.set_download_status(
        pid,
        "done",
        local_path=final_path,
        bytes_=bytes_,
        sha256=sha256,
        last_error=None,
        inc_retry=False,
        next_try_at=0.0,
    )

def _mark_failed(repo, it: dict, pid: int, final_path: str, err: Exception) -> None:
    try:
        backoff = calc_backoff_sec(it.get("retry_count"))
        next_try_at = now_ts() + float(backoff)
        repo.set_download_status(
            pid,
            "failed",
            local_path=final_path,
            last_error=str(err),
            inc_retry=True,
            next_try_at=next_try_at,
        )
    except Exception:
        pass

async def _download_one(state, it: dict, *, root: str, sem: asyncio.Semaphore | None) -> None:
    """
    album_downloads의 항목 1개를 다운로드하여 로컬 파일로 저장하고 상태를 갱신합니다.

    - repo download_status를 downloading -> done/failed로 업데이트합니다.
    - 성공 시 state.album_cache[pid]["local_path"]를 갱신합니다(있을 때만).

    :param state: 전역 상태(MonitorState)
    :param it: 다운로드 항목(dict). 최소 {"id", "url"} 포함
    :param root: 저장 루트 디렉토리
    :param sem: 동시 다운로드 제한 세마포어(None이면 제한 없음)
    :return: None
    """
    repo = state.album_repo
    session = state.http_session

    pid = int(it["id"])
    url = str(it["url"])
    tmp_path, final_path = _make_paths(root, pid, url)

    _mark_downloading(repo, pid, final_path)

    async def _run() -> None:
        t0 = now_ts()
        bytes_, sha256 = await _fetch_to_file(session, url, tmp_path)
        os.replace(tmp_path, final_path)

        # URL 변경으로 확장자가 바뀌면 이전 pid.* 파일이 남을 수 있으므로 정리
        _cleanup_other_ext_files(root, pid, final_path)

        _mark_done(repo, pid, final_path, bytes_=bytes_, sha256=sha256)

        cached = state.album_cache.get(pid)
        if cached is not None:
            cached["local_path"] = final_path

        logger.info("[album_dl] done id=%s bytes=%s dt=%.2fs", pid, bytes_, now_ts() - t0)

    try:
        if sem is None:
            await _run()
            return

        async with sem:
            await _run()

    except Exception as e:
        _safe_remove(tmp_path)
        _mark_failed(repo, it, pid, final_path, e)
        logger.warning("[album_dl] failed id=%s err=%s", pid, e)

async def download_album_loop(state, interval_sec: float = 1.0, batch_limit: int = 10):
    """
    album_downloads 루프:
    - pending/failed(+stuck downloading) 항목을 조회하여 파일 저장
    - status: pending/failed/downloading(stuck) -> downloading -> done 또는 failed
    - 동시 다운로드는 state.download_sem으로 제한

    :param state: 전역 상태
    :param interval_sec: 폴링 간격(초)
    :param batch_limit: 한 번에 처리할 최대 개수
    :return: 없음
    """
    root = ALBUM_PATH or "./album"
    os.makedirs(root, exist_ok=True)

    while not getattr(state, "shutting_down", False):
        await asyncio.sleep(float(interval_sec))

        repo = getattr(state, "album_repo", None)
        if not repo:
            continue

        session = getattr(state, "http_session", None)
        if session is None or session.closed:
            logger.error("[album_dl] http_session missing/closed (skip tick)")
            continue

        try:
            items = repo.list_pending_downloads(limit=batch_limit, now=now_ts())
            if not items:
                continue

            sem = getattr(state, "download_sem", None)
            tasks = [
                asyncio.create_task(_download_one(state, it, root=root, sem=sem))
                for it in items
            ]
            await asyncio.gather(*tasks, return_exceptions=True)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[album_dl] unexpected error")