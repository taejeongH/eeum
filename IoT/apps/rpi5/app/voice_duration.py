import json
from app.sh import async_sh

async def ffprobe_duration_sec(path: str, *, timeout_sec: float) -> float:
    """
    ffprobe로 오디오 duration을 조회합니다.

    :param path: 파일 경로
    :param timeout_sec: 타임아웃(초)
    :return: duration(초)
    """
    result = await async_sh(
        [
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "json",
            path,
        ],
        check=False,
        timeout=float(timeout_sec),
    )
    if result.returncode != 0:
        raise RuntimeError("ffprobe failed")

    payload = json.loads(result.stdout or "{}")
    duration = float((payload.get("format") or {}).get("duration") or 0.0)
    return duration

async def verify_mp3_quick(path: str, *, timeout_sec: float = 1.2, min_dur_sec: float = 0.05) -> float:
    """
    빠른 무결성 체크를 수행합니다.
    - ffprobe로 duration을 읽어서 min_dur_sec 초과인지 확인
    - 실패하거나 0에 가깝다면 예외

    :param path: mp3 파일 경로
    :param timeout_sec: ffprobe 타임아웃(초)
    :param min_dur_sec: 최소 허용 duration(초)
    :return: duration(초)
    """
    duration = await ffprobe_duration_sec(path, timeout_sec=timeout_sec)
    if duration <= float(min_dur_sec):
        raise RuntimeError(f"invalid duration: {duration}")
    return duration