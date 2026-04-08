import json
import logging
import os
from pathlib import Path
from typing import Any, Dict, Optional
from app.sync_utils import now_ts

logger = logging.getLogger(__name__)

def load_json(path: str | Path, default: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    """
    JSON 파일을 읽어 dict로 반환합니다. 파일이 없으면 default를 반환합니다.

    :param path: 파일 경로
    :param default: 기본값(dict)
    :return: JSON dict
    """
    file_path = Path(path)
    if not file_path.exists():
        return default if default is not None else {}

    with open(file_path, "r", encoding="utf-8") as f:
        return json.load(f)

def atomic_write_json(path: str | Path, data: Dict[str, Any]) -> None:
    """
    JSON을 원자적으로 저장합니다.
    - tmp 파일에 write
    - flush + fsync
    - os.replace로 원자적 교체

    :param path: 저장 경로
    :param data: 저장할 dict
    :return: None
    """
    file_path = Path(path)
    file_path.parent.mkdir(parents=True, exist_ok=True)

    tmp_path = file_path.with_suffix(file_path.suffix + ".tmp")
    payload = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)

    with open(tmp_path, "w", encoding="utf-8") as f:
        f.write(payload)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())

    os.replace(tmp_path, file_path)

class JsonStateStore:
    """
    JSON 파일 기반의 간단한 상태 저장소입니다.
    """

    def __init__(self, path: str | Path, default: Optional[Dict[str, Any]] = None):
        """
        :param path: JSON 파일 경로
        :param default: 파일이 없을 때 초기 상태
        """
        self.path = Path(path)
        self.state: Dict[str, Any] = load_json(self.path, default=default)

    def get(self) -> Dict[str, Any]:
        """
        현재 메모리 상태를 반환합니다.

        :return: 상태 dict
        """
        return self.state

    def save(self) -> None:
        """
        updated_at을 갱신한 뒤 파일로 저장합니다.

        :return: None
        """
        self.state["updated_at"] = int(now_ts())
        atomic_write_json(self.path, self.state)
