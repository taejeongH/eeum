import re
from typing import Any

def is_ok_status(status: Any) -> bool:
    """
    서버 statusCode가 아래처럼 흔들리는 케이스를 모두 2xx로 판정:
      - 200 (int)
      - "200"
      - "OK"
      - "200 OK"
      - 기타 문자열에 3자리 코드 포함("HTTP 200", etc)
    """
    if status is None:
        return False
    if isinstance(status, int):
        return 200 <= status < 300
    if isinstance(status, str):
        s = status.strip()
        if s.upper() == "OK":
            return True
        m = re.search(r"\d{3}", s)
        if m:
            try:
                code = int(m.group(0))
                return 200 <= code < 300
            except Exception:
                return False
    return False