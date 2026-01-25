from dataclasses import dataclass, field
from typing import Optional, Any, Dict, List
import asyncio
import time

@dataclass
class Event:
    kind: str
    device: str
    data: dict[str, Any]
    ts: float = field(default_factory=lambda: time.time())

class MonitorState:
    def __init__(self):
        self.last_pir_ts: Optional[float] = None
        self.alert: bool = False

        self._timer_task: Optional[asyncio.Task] = None
        self.queue: asyncio.Queue[Event] = asyncio.Queue(maxsize=2)

        # ---- Wi-Fi cache ----
        self.wifi_active: Optional[str] = None
        self.wifi_scan: List[Dict[str, Any]] = []
        self.wifi_profiles: List[Dict[str, Any]] = []

        self.wifi_active_ts: float = 0.0
        self.wifi_cache_ts: float = 0.0

        # UI가 wifi 설정 화면을 보고 있는지 판단 (ping 갱신)
        self.wifi_ui_last_ping: float = 0.0

        # connect/delete 같은 write 작업 중 scan 방지용
        self.wifi_busy: bool = False

        # 캐시 갱신 동시 실행 방지
        self.wifi_cache_lock = asyncio.Lock()
