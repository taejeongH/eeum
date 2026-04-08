import asyncio
from dataclasses import dataclass, field
from typing import Any, Dict, List, Optional, Tuple
import aiohttp
from app.audio_manager import AudioManager
from app.config import CLIENT_ID
from app.sync_utils import now_ts

@dataclass
class Event:
    """
    센서/디바이스 이벤트 모델.
    """
    kind: str
    device_id: str
    data: dict[str, Any]
    detected_at: float = field(default_factory=lambda: now_ts())

@dataclass
class Command:
    """
    MQTT 등에서 수신한 커맨드 모델.
    """
    topic: str
    payload: Dict[str, Any]
    received_at: float = field(default_factory=lambda: now_ts())

class MonitorState:
    """
    서버 전역 상태 컨테이너.
    FastAPI 핸들러와 모든 백그라운드 루프가 공유합니다.
    """

    def __init__(self):
        # -----------------------------------------------------------------
        # lifecycle flags
        # -----------------------------------------------------------------
        self.alert: bool = False
        self.shutting_down: bool = False

        # -----------------------------------------------------------------
        # queues
        # -----------------------------------------------------------------
        # 센서 이벤트 처리용
        self.queue: asyncio.Queue[Event] = asyncio.Queue(maxsize=16)
        # MQTT command 처리용
        self.cmd_queue: asyncio.Queue[Command] = asyncio.Queue(maxsize=64)
        # paho thread → asyncio 브릿지
        self.mqtt_inbound: asyncio.Queue[Optional[Tuple[str, Dict[str, Any]]]] = asyncio.Queue(maxsize=256)

        self._http_connector = None
        self._bg_tasks: list[asyncio.Task] = []

        self.device_id: str = CLIENT_ID or ""
        self.loop = None

        # -----------------------------------------------------------------
        # STT / voice
        # -----------------------------------------------------------------
        self.voice_done_sent = set()
        self.voice_done_lock = asyncio.Lock()

        self.stt_engine = None
        self.stt_cache_missing: bool = False
        self.stt_cache_attempted: bool = False
        self.stt_lock = asyncio.Lock()
        self.stt_busy: bool = False

        # -----------------------------------------------------------------
        # DB / repos
        # -----------------------------------------------------------------
        self.db = None
        self.album_repo = None
        self.album_lock = asyncio.Lock()
        self.member_repo = None

        # member cache
        self.member_cache: dict[int, dict[str, Any]] = {}
        self.member_cache_ts: float = 0.0
        self.member_cache_loaded: bool = False

        # album cache
        self.album_cache: dict[int, dict[str, Any]] = {}
        self.album_cache_ts: float = 0.0
        self.album_cache_loaded: bool = False
        self.album_last_sync_ts: float = 0.0
        self.album_last_sync_ok: bool = False

        # -----------------------------------------------------------------
        # slideshow
        # -----------------------------------------------------------------
        self.slide_playing: bool = True
        self.slide_interval_sec: float = 60.0
        self.slide_mode: str = "sequential"

        self.slide_playlist: list[int] = []
        self.slide_index: int = 0

        self.slide_seq: int = 0
        self.slide_subscribers: set[asyncio.Queue] = set()
        self.slide_lock = asyncio.Lock()
        self.slide_timer_task: Optional[asyncio.Task] = None
        self.slide_tick_event: asyncio.Event = asyncio.Event()

        # -----------------------------------------------------------------
        # shared http
        # -----------------------------------------------------------------
        self.http_session: Optional[aiohttp.ClientSession] = None

        # -----------------------------------------------------------------
        # audio
        # -----------------------------------------------------------------
        self.audio = AudioManager()

        # 무거운 작업(STT/fall 등) 중 nmcli 같은 작업을 막기 위한 플래그
        self.heavy_ops_pause: bool = False
        self.heavy_ops_lock = asyncio.Lock()

        # -----------------------------------------------------------------
        # voice
        # -----------------------------------------------------------------
        self.voice_subscribers: set[asyncio.Queue] = set()
        self.voice_repo = None
        self.voice_duration_cache: dict[int, float] = {}
        self.voice_ack_locks: dict[int, asyncio.Lock] = {}
        self.voice_dl_locks: dict[int, asyncio.Lock] = {}

        # -----------------------------------------------------------------
        # alarm / sse
        # -----------------------------------------------------------------
        self.alert_subscribers: set[asyncio.Queue] = set()
        self.alarm_last_tts_ts: dict[str, float] = {}

        # -----------------------------------------------------------------
        # mqtt / device state
        # -----------------------------------------------------------------
        self.mqtt = None
        self.last_event_by_device: dict[str, dict] = {}
        self.device_store = None

        # -----------------------------------------------------------------
        # occupancy / presence
        # -----------------------------------------------------------------
        self.occupancy_present: bool = False
        self.occupancy_dev: Optional[str] = None
        self.occupancy_kind: Optional[str] = None
        self.occupancy_since_ts: Optional[float] = None
        self.last_pir_ts: Optional[float] = None
        self.last_vision_ts: Optional[float] = None
        self.vision_active: bool = False

        # -----------------------------------------------------------------
        # fall pipeline state (디버깅/상태 추적용)
        # -----------------------------------------------------------------
        self.fall_active: bool = False
        self.fall_level: int = 0
        self.fall_stage: str = "IDLE"
        self.fall_started_ts: float = 0.0
        self.fall_last_stage_ts: float = 0.0
        self.fall_device: Optional[str] = None
        self.fall_answer_text: Optional[str] = None

        # -----------------------------------------------------------------
        # misc async tasks
        # -----------------------------------------------------------------
        self.tasks: Dict[str, asyncio.Task] = {}

        # -----------------------------------------------------------------
        # wifi
        # -----------------------------------------------------------------
        self.wifi_active: Optional[str] = None
        self.wifi_scan: List[Dict[str, Any]] = []
        self.wifi_profiles: List[Dict[str, Any]] = []

        self.wifi_active_ts: float = 0.0
        self.wifi_scan_ts: float = 0.0
        self.wifi_profiles_ts: float = 0.0

        self.wifi_ui_last_ping: float = 0.0
        self.wifi_busy: bool = False
        self.wifi_cache_lock = asyncio.Lock()
