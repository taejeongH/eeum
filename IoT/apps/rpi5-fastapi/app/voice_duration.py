# ./app/voice_duration.py
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
