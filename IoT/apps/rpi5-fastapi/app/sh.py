import asyncio
import subprocess
import logging
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)

@dataclass
class CmdResult:
    returncode: int
    stdout: str
    stderr: str

async def async_sh(
        cmd: list[str],
        check: bool = True,
        timeout: float = 15.0,
        debug: bool=False
) -> CmdResult:
    if debug:
        logger.debug("> %s", " ".join(cmd))
    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )
    try:
        out_b, err_b = await asyncio.wait_for(
            proc.communicate(),
            timeout=timeout
        )
    except asyncio.CancelledError:
        try:
            proc.kill()
        except ProcessLookupError:
            pass
        await proc.wait()
        raise
    except asyncio.TimeoutError:
        logger.warning("[sh] timeout cmd=%s", cmd, exc_info=True)
        try:
            proc.kill()
        except ProcessLookupError:
            pass
        await proc.wait()
        raise

    stdout = (out_b or b"").decode(errors="replace")
    stderr = (err_b or b"").decode(errors="replace")

    if check and proc.returncode != 0:
        raise subprocess.CalledProcessError(
            proc.returncode, 
            cmd, 
            output=stdout, 
            stderr=stderr
        )
    
    return CmdResult(proc.returncode, stdout, stderr)