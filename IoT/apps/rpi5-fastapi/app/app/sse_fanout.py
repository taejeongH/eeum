# app/sse_fanout.py
import asyncio
from typing import Any, Set

def fanout_nowait(subscribers: Set[asyncio.Queue], envelope: Any) -> int:
    """
    - put 성공: 유지
    - QueueFull: oldest 1개 drop 후 1회 재시도
    - 재시도도 실패하면 해당 subscriber 제거 (dead)
    - 기타 예외도 제거
    returns: delivered count
    """
    delivered = 0
    for q in list(subscribers):
        try:
            q.put_nowait(envelope)
            delivered += 1
            continue
        except asyncio.QueueFull:
            # drop oldest 한번 시도
            try:
                _ = q.get_nowait()
            except Exception:
                pass
            try:
                q.put_nowait(envelope)
                delivered += 1
                continue
            except Exception:
                subscribers.discard(q)
        except Exception:
            subscribers.discard(q)
    return delivered