import asyncio
from typing import Any, AsyncIterator
from fastapi.responses import StreamingResponse

def ok(data: Any = None) -> dict:
    """
    공통 성공 응답 포맷을 반환합니다.

    :param data: 응답 데이터
    :return: {"ok": True, "reason": None, "data": data}
    """
    return {"ok": True, "reason": None, "data": data}

def fail(reason: str, data: Any = None) -> dict:
    """
    공통 실패 응답 포맷을 반환합니다.

    :param reason: 실패 사유 코드/문자열
    :param data: 추가 데이터
    :return: {"ok": False, "reason": reason, "data": data}
    """
    return {"ok": False, "reason": reason, "data": data}

def ok_voice(action: str, vid: int, *, duration_sec: float | None = None) -> dict:
    """
    voice 관련 성공 응답 포맷을 반환합니다.

    :param action: "play" 또는 "skip"
    :param vid: voice id
    :param duration_sec: 선택적으로 제공 가능한 재생 길이(초)
    :return: 공통 포맷 dict
    """
    payload = {"target": {"type": "voice", "id": int(vid)}, "action": action}
    if duration_sec is not None and duration_sec > 0:
        payload["duration_sec"] = float(duration_sec)
    return {"ok": True, "reason": None, "data": payload}

def fail_voice(reason: str, action: str, vid: int) -> dict:
    """
    voice 관련 실패 응답 포맷을 반환합니다.

    :param reason: 실패 사유 코드/문자열
    :param action: "play" 또는 "skip"
    :param vid: voice id
    :return: 공통 포맷 dict
    """
    return {
        "ok": False,
        "reason": reason,
        "data": {"target": {"type": "voice", "id": int(vid)}, "action": action},
    }

def queue_put_drop_oldest(queue: asyncio.Queue, item: Any) -> None:
    """
    큐가 가득 찬 경우 가장 오래된 항목 1개를 버리고 새 항목을 넣습니다.
    어떤 경우에도 예외를 밖으로 전파하지 않습니다.
    
    :param queue: asyncio.Queue
    :param item: 넣을 항목
    :return: None
    """
    try:
        queue.put_nowait(item)
        return
    except asyncio.QueueFull:
        pass
    except Exception:
        return

    try:
        queue.get_nowait()
    except Exception:
        pass

    try:
        queue.put_nowait(item)
    except Exception:
        pass

def sse_response(gen: AsyncIterator[str]) -> StreamingResponse:
    """
    SSE(서버 전송 이벤트)용 StreamingResponse를 생성합니다.

    :param gen: "event/data" 포맷 문자열을 yield 하는 async generator
    :return: StreamingResponse
    """
    response = StreamingResponse(gen, media_type="text/event-stream")
    response.headers["X-Accel-Buffering"] = "no"
    response.headers["Cache-Control"] = "no-cache"
    response.headers["Connection"] = "keep-alive"
    return response
