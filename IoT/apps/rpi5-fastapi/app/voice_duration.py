
import json
from .sh import async_sh

async def get_mp3_duration_sec(path: str) -> float:
    r = await async_sh([
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-of", "json",
        path
    ], check=False, timeout=3.0)
    if r.returncode != 0:
        raise RuntimeError("ffprobe failed")
    j = json.loads(r.stdout or "{}")
    dur = float((j.get("format") or {}).get("duration") or 0.0)
    return dur

async def verify_mp3_quick(path: str, *, timeout_sec: float = 1.2, min_dur_sec: float = 0.05) -> float:
    """
    빠른 무결성 체크:
    - ffprobe로 duration 읽어서 > min_dur_sec 인지 확인
    - 실패/0에 가까우면 예외
    반환: duration(sec)
    """
    r = await async_sh([
        "ffprobe", "-v", "error",
        "-show_entries", "format=duration",
        "-of", "json",
        path
    ], check=False, timeout=float(timeout_sec))
    if r.returncode != 0:
        raise RuntimeError("ffprobe failed")
    j = json.loads(r.stdout or "{}")
    dur = float((j.get("format") or {}).get("duration") or 0.0)
    if dur <= float(min_dur_sec):
        raise RuntimeError(f"invalid duration: {dur}")
    return dur
