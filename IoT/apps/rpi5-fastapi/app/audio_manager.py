# app/audio_manager.py
import asyncio
import time
import logging
from dataclasses import dataclass, field
from enum import IntEnum
from typing import Optional, Callable, Iterable

from .audio_play import AudioPlayback, start_mp3_playback, ensure_aplay_started, _sink  

logger = logging.getLogger(__name__)


class AudioPrio(IntEnum):
    NORMAL = 10
    SCHEDULE = 20
    VOICE = 30
    MEDICATION = 40
    FALL = 50

VOLUME_BY_KIND = {
    "normal": 1.5,
    "schedule": 1.5,
    "voice": 1.5,
    "medication": 1.5,
    "fall": 1.5,
}

@dataclass
class AudioJob:
    prio: int
    kind: str                 # "normal" | "voice" | "schedule" | "medication" | "fall"
    path: str                 # local mp3 path
    created_at: float = field(default_factory=lambda: time.time())

    ttl_sec: float = 180.0
    replace_key: Optional[str] = None   # 같은 키는 최신만 유지
    on_done: Optional[Callable[[], None]] = None
    done_event: Optional[asyncio.Event] = None  # 완료 대기용(특히 fall에서 필요)

def _now() -> float:
    return time.time()


class AudioManager:
    """
    단일 오디오 재생 관리자:
      - enqueue(AudioJob)
      - 우선순위 높은 job이 오면 현재 재생을 중단(프리엠션)
      - block_below_prio 설정 시 그 미만 prio는 재생을 막고 큐에만 유지
    """
    def __init__(self):
        self._cv = asyncio.Condition()
        self._queue: list[AudioJob] = []
        self._current: Optional[AudioJob] = None
        self._playback: Optional[AudioPlayback] = None
        self._task: Optional[asyncio.Task] = None
        self._stopping = False

        # 예: FALL 진행 중에는 FALL만 재생
        self.block_below_prio: Optional[int] = None

    def _dump(self, reason: str) -> None:
        if not logger.isEnabledFor(logging.DEBUG):
            return
        cur = None
        if self._current:
            cur = f"{self._current.kind}:{int(self._current.prio)}"
        q = [f"{j.kind}:{int(j.prio)}" + (f"[{j.replace_key}]" if j.replace_key else "")
             for j in self._queue]
        logger.debug(
            "[audio_dump] reason=%s block_below=%s current=%s qlen=%d queue=%s",
            reason,
            self.block_below_prio,
            cur,
            len(self._queue),
            q,
        )

    def start(self) -> asyncio.Task:
        if self._task and not self._task.done():
            return self._task
        self._task = asyncio.create_task(self._run_loop())
        return self._task

    async def stop(self):
        self._stopping = True
        async with self._cv:
            self._cv.notify_all()
        await self.stop_current()
        # 서비스 종료 시 aplay까지 종료
        try:
            await _sink.stop()
        except Exception:
            pass

    async def stop_current(self):
        pb = self._playback
        if pb:
            try:
                await pb.stop()
            except Exception:
                pass

    async def enqueue(self, job: AudioJob):
        # enqueue 시점 TTL 만료면 버림
        if job.ttl_sec > 0 and (_now() - job.created_at) > job.ttl_sec:
            return

        async with self._cv:
            # replace_key 정책(최신만 유지)
            if job.replace_key:
                self._queue = [j for j in self._queue if j.replace_key != job.replace_key]

            self._queue.append(job)
            self._dump(f"enqueue kind={job.kind} prio={int(job.prio)}")

            # 프리엠션: 현재 재생 중인데 더 높은 prio가 들어오면 중단
            if self._current and int(job.prio) > int(self._current.prio):
                logger.info(
                    "[audio] preempt cur=%s(%s) -> new=%s(%s)",
                    self._current.kind, self._current.prio, job.kind, job.prio,
                )
                asyncio.create_task(self.stop_current())

            self._cv.notify_all()

    def _pop_next(self) -> Optional[AudioJob]:
        if not self._queue:
            return None

        # block_below_prio 반영 + (prio 내림차순, created_at 오름차순)으로 선택
        best_idx = None
        best_prio = None
        best_created = None
        for i, j in enumerate(self._queue):
            if self.block_below_prio is not None and int(j.prio) < int(self.block_below_prio):
                continue
            p = int(j.prio)
            c = float(j.created_at)
            if best_idx is None:
                best_idx, best_prio, best_created = i, p, c
                continue
            # 더 높은 prio 우선
            if p > best_prio:
                best_idx, best_prio, best_created = i, p, c
                continue
            # 같은 prio면 더 오래된 것(FIFO)
            if p == best_prio and c < best_created:
                best_idx, best_prio, best_created = i, p, c

        if best_idx is None:
            return None
        return self._queue.pop(best_idx)

    async def _run_loop(self):
        logger.info("[audio] manager started")
        while not self._stopping:
            # 다음 job 대기
            async with self._cv:
                while not self._stopping:
                    nxt = self._pop_next()
                    if nxt:
                        self._current = nxt
                        self._dump(f"pop_next -> {nxt.kind}:{int(nxt.prio)}")
                        break
                    # block_below_prio 때문에 계속 못 꺼내는 상태 추적
                    self._dump("wait_no_playable_job")
                    await self._cv.wait()

            if self._stopping:
                break

            job = self._current
            if not job:
                continue

            # 실행 직전 TTL 재확인
            if job.ttl_sec > 0 and (_now() - job.created_at) > job.ttl_sec:
                logger.info("[audio] drop expired kind=%s", job.kind)
                if job.done_event:
                    job.done_event.set()
                self._current = None
                continue

            try:
                volume = float(VOLUME_BY_KIND.get(job.kind, 1.0))
                logger.debug(
                    "[audio] start kind=%s prio=%s path=%s volume=%.2f",
                    job.kind, int(job.prio), job.path, volume
                )

                self._playback = await start_mp3_playback(job.path, volume=volume)
                await self._playback.wait()
            except asyncio.CancelledError:
                raise
            except Exception as e:
                logger.warning("[audio] play failed kind=%s err=%s", job.kind, e)
            finally:
                logger.debug("[audio] done kind=%s prio=%s", job.kind, int(job.prio))
                self._playback = None
                self._current = None

                if job.on_done:
                    try:
                        job.on_done()
                    except Exception:
                        pass
                if job.done_event:
                    try:
                        job.done_event.set()
                    except Exception:
                        pass

        logger.info("[audio] manager stopped")
    
    async def cancel(
        self,
        *,
        kind: str | None = None,
        replace_key: str | None = None,
    ) -> dict:
        """
        kind / replace_key 기준으로:
        - 큐에서 제거
        - 현재 재생 중이면 stop_current
        return: {"removed": int, "stopped_current": bool}
        """
        removed = 0
        stopped_current = False

        # 1) 큐에서 제거는 cv 락 안에서
        async with self._cv:
            before = len(self._queue)

            def _match(j: AudioJob) -> bool:
                if kind is not None and j.kind != kind:
                    return False
                if replace_key is not None and j.replace_key != replace_key:
                    return False
                return True

            self._queue = [j for j in self._queue if not _match(j)]
            removed = before - len(self._queue)
            self._dump(f"cancel kind={kind} rk={replace_key} removed={removed}")

            # 2) 현재 재생 중인 job도 기준에 맞으면 stop 예약
            cur = self._current
            if cur and _match(cur):
                stopped_current = True
                # cv 락 안에서 await stop_current 하면 데드락/지연 위험 -> task로
                asyncio.create_task(self.stop_current())

            self._cv.notify_all()

        return {"removed": int(removed), "stopped_current": bool(stopped_current)}

    async def cancel_many(
        self,
        *,
        kinds: Iterable[str] | None = None,
        replace_keys: Iterable[str] | None = None,
    ) -> dict:
        kinds_set = set(kinds or [])
        rks_set = set(replace_keys or [])

        removed = 0
        stopped_current = False

        async with self._cv:
            before = len(self._queue)

            def _match(j: AudioJob) -> bool:
                if kinds_set and j.kind not in kinds_set:
                    return False
                if rks_set and (j.replace_key not in rks_set):
                    return False
                return True

            self._queue = [j for j in self._queue if not _match(j)]
            removed = before - len(self._queue)
            self._dump(f"cancel_many kinds={list(kinds_set)} rks={list(rks_set)} removed={removed}")

            cur = self._current
            if cur and _match(cur):
                stopped_current = True
                asyncio.create_task(self.stop_current())

            self._cv.notify_all()

        return {"removed": int(removed), "stopped_current": bool(stopped_current)}
