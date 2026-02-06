from dataclasses import dataclass, field
from typing import Optional, Any, Dict, List, Tuple
import asyncio
import time
import aiohttp
from .config import CLIENT_ID
from .audio_manager import AudioManager

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
        self.device_id = CLIENT_ID or ""
        self.loop = None

        self.stt_engine = None
        self.stt_cache_missing: bool = False
        self.stt_cache_attempted: bool = False
        self.stt_lock = asyncio.Lock()
        self.stt_busy: bool = False
        
        # ---- DB ----
        self.db = None
        self.album_repo = None
        self.album_lock = asyncio.Lock()
        self.member_repo = None
        
        self.member_cache: dict[int, dict[str, Any]] = {}   # user_id -> {user_id,name,profile_image_url,updated_at}
        self.member_cache_ts: float = 0.0
        self.member_cache_loaded: bool = False
        # 메모리 캐시: id -> photo dict
        self.album_cache: dict[int, dict[str, Any]] = {}
        self.album_cache_ts = 0.0      # 마지막 갱신 시각 (time.time())
        self.album_cache_loaded = False
        self.album_last_sync_ts: float = 0.0
        self.album_last_sync_ok: bool = False

        # ---- Slideshow ----
        self.slide_playing: bool = True
        self.slide_interval_sec: float = 60.0
        self.slide_mode: str = "sequential"

        self.slide_playlist: list[int] = []   # photo_id list
        self.slide_index: int = 0

        self.slide_seq: int = 0
        self.slide_subscribers: set[asyncio.Queue] = set()
        self.slide_lock = asyncio.Lock()
        self.slide_timer_task: Optional[asyncio.Task] = None
        self.slide_tick_event: asyncio.Event = asyncio.Event()

        # ---- shared http session ----
        self.http_session: Optional[aiohttp.ClientSession] = None

        # ---- audio manager ----
        self.audio = AudioManager()
        
        # ---- voice ----
        self.voice_subscribers: set[asyncio.Queue] = set()
        self.voice_repo = None

        # ---- alarm ----
        self.alert_subscribers: set[asyncio.Queue] = set()
        self.alarm_last_tts_ts: dict[str, float] = {}

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
        self.wifi_scan_ts: float = 0.0
        self.wifi_profiles_ts: float = 0.0

        # UI가 wifi 설정 화면을 보고 있는지 판단 (ping 갱신)
        self.wifi_ui_last_ping: float = 0.0

        # connect/delete 같은 write 작업 중 scan 방지용
        self.wifi_busy: bool = False

        # 캐시 갱신 동시 실행 방지
        self.wifi_cache_lock = asyncio.Lock()
