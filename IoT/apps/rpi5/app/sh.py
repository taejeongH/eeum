import asyncio
import logging
import subprocess
from dataclasses import dataclass

logger = logging.getLogger(__name__)

@dataclass(frozen=True)
class CmdResult:
    """명령 실행 결과 컨테이너"""
    returncode: int
    stdout: str
    stderr: str

def _decode_output(data: bytes | None) -> str:
    """
    subprocess 출력(bytes)을 안전하게 문자열로 변환합니다.

    :param data: stdout/stderr bytes (None 가능)
    :return: 디코딩된 문자열
    """
    return (data or b"").decode(errors="replace")

async def _terminate_process(proc: asyncio.subprocess.Process) -> None:
    """
    프로세스를 안전하게 종료한 뒤 기다립니다.
    
    :param proc: asyncio subprocess 프로세스
    :return: None
    """
    try:
        proc.kill()
    except ProcessLookupError:
        pass
    await proc.wait()

async def async_sh(
    cmd: list[str],
    check: bool = True,
    timeout: float = 15.0,
    debug: bool = False,
) -> CmdResult:
    """
    비동기로 커맨드를 실행하고 stdout/stderr를 수집합니다.

    - timeout 발생 시 프로세스를 종료하고 asyncio.TimeoutError를 전파합니다.
    - check=True이고 returncode!=0이면 CalledProcessError를 발생시킵니다.

    :param cmd: 실행할 커맨드(리스트 형태)
    :param check: 비정상 종료 시 예외 발생 여부
    :param timeout: 타임아웃(초)
    :param debug: True면 실행 커맨드를 debug 로그로 남깁니다
    :return: CmdResult(returncode, stdout, stderr)
    """
    if debug:
        logger.debug("> %s", " ".join(cmd))

    proc = await asyncio.create_subprocess_exec(
        *cmd,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    try:
        stdout_bytes, stderr_bytes = await asyncio.wait_for(proc.communicate(), timeout=timeout)
    except asyncio.CancelledError:
        await _terminate_process(proc)
        raise
    except asyncio.TimeoutError:
        logger.warning("[sh] timeout cmd=%s timeout=%.1fs", " ".join(cmd), timeout)
        await _terminate_process(proc)
        raise

    stdout = _decode_output(stdout_bytes)
    stderr = _decode_output(stderr_bytes)

    if check and proc.returncode != 0:
        raise subprocess.CalledProcessError(
            proc.returncode,
            cmd,
            output=stdout,
            stderr=stderr,
        )

    return CmdResult(proc.returncode, stdout, stderr)