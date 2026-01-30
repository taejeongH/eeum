import json
import os
import time
from pathlib import Path
from typing import Any, Dict, Optional

def load_json(path: str | Path, default: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
    path = Path(path)
    if not path.exists():
        return default if default is not None else {}
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)

def atomic_write_json(path: str | Path, data: Dict[str, Any]) -> None:
    """
    JSON 원자적 저장
    - path.tmp
    - flush + fsync
    - rename
    """
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)

    tmp_path = path.with_suffix(path.suffix + ".tmp")
    
    payload = json.dumps(data, ensure_ascii=False, indent=2, sort_keys=True)

    with open(tmp_path, "w", encoding="utf-8") as f:
        f.write(payload)
        f.write("\n")
        f.flush()
        os.fsync(f.fileno())
    
    os.replace(tmp_path, path)

class JsonStateStore:
    def __init__(self, path: str | Path, default:Optional[Dict[str, Any]] = None):
        self.path = Path(path)
        self.state: Dict[str, Any] = load_json(self.path, default=default)
    
    def get(self) -> Dict[str, Any]:
        return self.state
    
    def save(self) -> None:
        self.state["updated_at"] = int(time.time())
        atomic_write_json(self.path, self.state)