import asyncio
from typing import Any, Set

def _try_drop_oldest(q: asyncio.Queue) -> None:
    try:
        q.get_nowait()
    except Exception:
        pass

def _try_put_with_one_drop(q: asyncio.Queue, envelope: Any) -> bool:
    """
    큐에 envelope을 넣습니다.
    - 1차 put_nowait 성공: True
    - QueueFull이면 oldest 1개 drop 후 1회 재시도
    - 재시도도 실패하면 False

    :param q: subscriber 큐
    :param envelope: 전송할 데이터
    :return: 성공 여부
    """
    try:
        q.put_nowait(envelope)
        return True
    except asyncio.QueueFull:
        _try_drop_oldest(q)
        try:
            q.put_nowait(envelope)
            return True
        except Exception:
            return False
    except Exception:
        return False

def fanout_nowait(subscribers: Set[asyncio.Queue], envelope: Any) -> int:
    """
    subscribers 각 큐에 envelope을 비동기로 fanout합니다.

    규칙:
    - put 성공: 유지
    - QueueFull: oldest 1개 drop 후 1회 재시도
    - 재시도도 실패하거나 기타 예외면 해당 subscriber 제거

    :param subscribers: 구독자 큐 집합
    :param envelope: 전송할 데이터
    :return: 전달 성공한 subscriber 수
    """
    delivered_count = 0

    for q in list(subscribers):
        ok = _try_put_with_one_drop(q, envelope)
        if ok:
            delivered_count += 1
            continue
        subscribers.discard(q)

    return delivered_count