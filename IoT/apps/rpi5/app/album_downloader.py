# app/album_downloader.py
import asyncio
import hashlib
import os
import time
import logging
from urllib.parse import urlparse

import aiohttp

from .config import ALBUM_PATH

logger = logging.getLogger(__name__)

def _calc_backoff_sec(retry_count: int) -> float:
    # 2,4,8,16,... 최대 300초
    try:
        rc = max(0, int(retry_count))
    except Exception:
        rc = 0
    return min(300.0, float(2 ** rc))

def _ext_from_url(url: str) -> str:
    try:
        p = urlparse(url)
        base = os.path.basename(p.path)
        _, ext = os.path.splitext(base)
        ext = (ext or "").lower()
        if ext in (".jpg", ".jpeg", ".png", ".webp"):
            return ext
    except Exception:
        pass
    return ".jpg"

async def _download_one_album_item(state, it, tmp_path, final_path, url, pid):
    repo = state.album_repo
    s = state.http_session

    t0 = time.time()
    async with s.get(url, timeout=aiohttp.ClientTimeout(total=20.0)) as r:
        r.raise_for_status()
        h = hashlib.sha256()
        size = 0
        with open(tmp_path, "wb") as f:
            async for chunk in r.content.iter_chunked(64 * 1024):
                if not chunk:
                    continue
                f.write(chunk)
                size += len(chunk)
                h.update(chunk)

    os.replace(tmp_path, final_path)

    repo.set_download_status(
        pid, "done",
        local_path=final_path,
        bytes_=size,
        sha256=h.hexdigest(),
        last_error=None,
        inc_retry=False,
        next_try_at=0.0,
    )

    # 캐시 반영
    c = state.album_cache.get(pid)
    if c is not None:
        c["local_path"] = final_path

    logger.info("[album_dl] done id=%s bytes=%s dt=%.2fs", pid, size, time.time() - t0)

async def download_album_loop(state, interval_sec: float = 1.0, batch_limit: int = 10):
    """
    album_downloads:
      - pending/failed(+stuck downloading) 항목을 가져와서 파일 저장
      - status: pending/failed/downloading(stuck) -> downloading -> done or failed
      - 동시 다운로드는 state.download_sem(DL_CONCURRENCY)로 제한
    """
    root = ALBUM_PATH or "./album"
    os.makedirs(root, exist_ok=True)

    while True:
        if getattr(state, "shutting_down", False):
            return

        await asyncio.sleep(interval_sec)

        repo = state.album_repo
        if not repo:
            continue

        try:
            items = repo.list_pending_downloads(limit=batch_limit, now=time.time())
            if not items:
                continue

            s = getattr(state, "http_session", None)
            if s is None or s.closed:
                logger.error("[album_dl] http_session missing/closed (skip tick)")
                continue

            sem = getattr(state, "download_sem", None)

            async def _handle_one(it):
                pid = int(it["id"])
                url = str(it["url"])

                ext = _ext_from_url(url)
                tmp_path = os.path.join(root, f"{pid}{ext}.tmp")
                final_path = os.path.join(root, f"{pid}{ext}")

                try:
                    # 시작 시점에 downloading 마크
                    try:
                        repo.set_download_status(
                            pid, "downloading",
                            local_path=final_path,
                            last_error=None,
                            inc_retry=False,
                            next_try_at=None,
                        )
                    except Exception:
                        pass

                    # 실제 동시성은 sem이 제한
                    if sem:
                        async with sem:
                            await _download_one_album_item(state, it, tmp_path, final_path, url, pid)
                    else:
                        await _download_one_album_item(state, it, tmp_path, final_path, url, pid)

                except Exception as e:
                    try:
                        os.remove(tmp_path)
                    except Exception:
                        pass

                    try:
                        backoff = _calc_backoff_sec(it.get("retry_count") or 0)
                        next_try = time.time() + backoff

                        repo.set_download_status(
                            pid,
                            "failed",
                            local_path=final_path,
                            last_error=str(e),
                            inc_retry=True,
                            next_try_at=next_try,
                        )
                    except KeyError:
                        pass
                    logger.warning("[album_dl] failed id=%s err=%s", pid, e)

            # 병렬 태스크 생성 (batch_limit만큼), sem이 동시 다운로드 제한
            tasks = [asyncio.create_task(_handle_one(it)) for it in items]
            await asyncio.gather(*tasks, return_exceptions=True)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[album_dl] unexpected error")