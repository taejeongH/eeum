from dataclasses import dataclass, field
from typing import Optional, Any, Dict, List, Tuple
import asyncio
import time

@dataclass
class Event:
    kind: str
    device_id: str
    data: dict[str, Any]
    detected_at: float = field(default_factory=lambda: time.time())

@dataclass
class Command:
    topic: str
    payload: Dict[str, Any]
    received_at: float = field(default_factory=lambda: time.time())

class MonitorState:
    def __init__(self):
        self.alert: bool = False
        self.shutting_down = False
        self.queue: asyncio.Queue[Event] = asyncio.Queue(maxsize=16)

        # ---- MQTT ----
        self.cmd_queue: asyncio.Queue[Command] = asyncio.Queue(maxsize=64)
        self.mqtt = None
        self.mqtt_inbound: asyncio.Queue[Optional[Tuple[str, Dict[str, Any]]]] = asyncio.Queue(maxsize=256)
        # ---- 최근 이벤트/디바이스 상태 ----
        self.last_event_by_device: dict[str, dict] = {}
        self.device_store = None

        # ---- Occupancy(사람 존재) ----
        self.occupancy_present: bool = False
        self.occupancy_dev: Optional[str] = None     # device_id
        self.occupancy_kind: Optional[str] = None
        self.occupancy_since_ts: Optional[float] = None
        self.last_pir_ts: Optional[float] = None
        self.last_vision_ts: Optional[float] = None
        self.vision_active: bool = False

        # ---- Fall FSM ----
        self.fall_active: bool = False
        self.fall_level: int = 0
        self.fall_stage: str = "IDLE"                   # IDLE/ASK_TTS/WAIT_SST/LLM/DONE
        self.fall_started_ts: float = 0.0
        self.fall_last_stage_ts: float = 0.0
        self.fall_device: Optional[str] = None
        self.fall_answer_text: Optional[str] = None

        # ---- 타이머/작업 관리 ----
        self.tasks: Dict[str, asyncio.Task] = {}         # key -> Task
        
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
