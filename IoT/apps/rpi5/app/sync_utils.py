import re
import time
from typing import Any, Dict, Tuple

def now_ts() -> float:
    """
    현재 UNIX 타임스탬프(초)를 반환합니다.
    :return: 현재 시간(초)
    """
    return time.time()

def calc_backoff_sec(retry_count: Any, *, base: float = 2.0, cap_sec: float = 300.0) -> float:
    """
    retry_count 기반 지수 백오프를 계산합니다. (base^n, 최대 cap)

    :param retry_count: 재시도 횟수(정수 변환 시도)
    :param base: 밑(base)
    :param cap_sec: 최대 백오프(초)
    :return: 백오프(초)
    """
    try:
        normalized_retry = max(0, int(retry_count or 0))
    except Exception:
        normalized_retry = 0

    backoff = float(base) ** normalized_retry
    return min(float(cap_sec), backoff)

def _extract_status_code_from_text(text: str) -> int | None:
    m = re.search(r"\d{3}", text)
    if not m:
        return None
    try:
        return int(m.group(0))
    except Exception:
        return None

def is_ok_status(status: Any) -> bool:
    """
    서버 statusCode가 흔들리는 케이스를 모두 2xx로 판정합니다.

    허용 예:
    - 200 (int)
    - "200"
    - "OK"
    - "200 OK"
    - "HTTP 200" 등 문자열 내 3자리 코드 포함

    :param status: 서버 statusCode 원본
    :return: 성공(2xx 또는 OK) 여부
    """
    if status is None:
        return False

    if isinstance(status, int):
        return 200 <= status < 300

    if isinstance(status, str):
        normalized = status.strip()
        if normalized.upper() == "OK":
            return True

        code = _extract_status_code_from_text(normalized)
        if code is None:
            return False
        return 200 <= code < 300

    return False

def build_api_url(base_url: Any, path: Any, default_path: str) -> str | None:
    """
    API_BASE_URL + (PATH or default_path) 를 안전하게 결합합니다.

    :param base_url: 베이스 URL(비어있으면 API 비활성)
    :param path: 사용자 지정 path
    :param default_path: 기본 path
    :return: 결합된 URL 또는 None
    """
    base = str(base_url or "").strip().rstrip("/")
    if not base:
        return None

    merged_path = str(path or default_path).strip()
    if not merged_path.startswith("/"):
        merged_path = "/" + merged_path

    return base + merged_path


def _ensure_list_field(data: Dict[str, Any], key: str) -> None:
    if not isinstance(data.get(key), list):
        data[key] = []


def _normalize_log_id_key(data: Dict[str, Any]) -> None:
    if "log_id" not in data and "lastLogId" in data:
        data["log_id"] = data.get("lastLogId")

def normalize_sync_response(resp_json: Dict[str, Any], *, service: str) -> Tuple[Dict[str, Any], str, Any]:
    """
    sync API 공통 응답을 정규화합니다.

    처리:
    - statusCode 판단(ok 아니면 ValueError)
    - message 추출
    - data(dict) 보장
    - added/deleted(list) 보장
    - log_id 키 통일(lastLogId -> log_id)

    :param resp_json: API 응답 JSON
    :param service: 서비스 이름(에러 메시지에 사용)
    :return: (data, message, status_raw)
    """
    status_raw = resp_json.get("statusCode")
    message = str(resp_json.get("message") or resp_json.get("msg") or "").strip()

    if not is_ok_status(status_raw):
        raise ValueError(message or f"{service} sync failed")

    data = resp_json.get("data")
    if not isinstance(data, dict):
        data = {}

    _ensure_list_field(data, "added")
    _ensure_list_field(data, "deleted")
    _normalize_log_id_key(data)

    return data, message, status_raw