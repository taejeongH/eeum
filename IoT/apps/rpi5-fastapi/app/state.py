from dataclasses import dataclass, field
from typing import Optional, Any, Dict
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

