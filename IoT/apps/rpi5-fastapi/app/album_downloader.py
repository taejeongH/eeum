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

async def download_album_loop(state, interval_sec: float = 1.0, batch_limit: int = 10):
    """
    album_downloads:
      - pending/failed 항목을 가져와서 파일 저장
      - status: pending/failed -> downloading -> done or failed
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

            for it in items:
                pid = int(it["id"])
                url = str(it["url"])

                ext = _ext_from_url(url)
                tmp_path = os.path.join(root, f"{pid}{ext}.tmp")
                final_path = os.path.join(root, f"{pid}{ext}")

                # downloading 마크 (삭제 레이스 방어)
                try:
                    repo.set_download_status(
                        pid,
                        "downloading",
                        local_path=final_path,
                        last_error=None,
                        inc_retry=False,
                        next_try_at=0.0,   # COALESCE 때문에 0.0이면 덮어써짐. (NULL로 하고 싶으면 UPDATE를 별도로 짜야 함)
                    )
                except KeyError:
                    # sync가 이미 사진을 지운 케이스: 조용히 스킵
                    continue

                try:
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

                    try:
                        repo.set_download_status(
                            pid,
                            "done",
                            local_path=final_path,
                            bytes_=size,
                            sha256=h.hexdigest(),
                            last_error=None,
                            inc_retry=False,
                            next_try_at=0.0,
                        )
                    except KeyError:
                        # done 마크 전에 sync가 지운 케이스: 파일만 정리하고 스킵
                        try:
                            os.remove(final_path)
                        except Exception:
                            pass
                        continue

                    # 실행 중 캐시에도 즉시 반영하면 UI가 바로 local_path를 씀
                    try:
                        c = state.album_cache.get(pid)
                        if c is not None:
                            c["local_path"] = final_path
                    except Exception:
                        pass
                    logger.info("[album_dl] done id=%s bytes=%s dt=%.2fs", pid, size, time.time() - t0)

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
                        # 이미 삭제된 경우면 상태 업데이트 불필요
                        pass
                    logger.warning("[album_dl] failed id=%s err=%s", pid, e)

        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("[album_dl] unexpected error")
