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

        self.voice_done_sent = set()
        self.voice_done_lock = asyncio.Lock()

        self.stt_engine = None
        self.stt_cache_missing: bool = False
        self.stt_cache_attempted: bool = False
        self.stt_lock = asyncio.Lock()
        self.stt_busy: bool = False

        
        self.db = None
        self.album_repo = None
        self.album_lock = asyncio.Lock()
        self.member_repo = None
        
        self.member_cache: dict[int, dict[str, Any]] = {}   
        self.member_cache_ts: float = 0.0
        self.member_cache_loaded: bool = False
        
        self.album_cache: dict[int, dict[str, Any]] = {}
        self.album_cache_ts = 0.0      
        self.album_cache_loaded = False
        self.album_last_sync_ts: float = 0.0
        self.album_last_sync_ok: bool = False

        
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

        
        self.http_session: Optional[aiohttp.ClientSession] = None

        
        self.audio = AudioManager()
        
        
        
        self.heavy_ops_pause: bool = False  
        self.heavy_ops_lock = asyncio.Lock()

        
        self.voice_subscribers: set[asyncio.Queue] = set()
        self.voice_repo = None
        
        self.voice_duration_cache: dict[int, float] = {}   
        
        self.voice_ack_locks: dict[int, asyncio.Lock] = {}
        
        self.voice_dl_locks: dict[int, asyncio.Lock] = {}

        
        self.alert_subscribers: set[asyncio.Queue] = set()
        self.alarm_last_tts_ts: dict[str, float] = {}

        
        self.cmd_queue: asyncio.Queue[Command] = asyncio.Queue(maxsize=64)
        self.mqtt = None
        self.mqtt_inbound: asyncio.Queue[Optional[Tuple[str, Dict[str, Any]]]] = asyncio.Queue(maxsize=256)
        
        self.last_event_by_device: dict[str, dict] = {}
        self.device_store = None

        
        self.occupancy_present: bool = False
        self.occupancy_dev: Optional[str] = None     
        self.occupancy_kind: Optional[str] = None
        self.occupancy_since_ts: Optional[float] = None
        self.last_pir_ts: Optional[float] = None
        self.last_vision_ts: Optional[float] = None
        self.vision_active: bool = False

        
        self.fall_active: bool = False
        self.fall_level: int = 0
        self.fall_stage: str = "IDLE"                   
        self.fall_started_ts: float = 0.0
        self.fall_last_stage_ts: float = 0.0
        self.fall_device: Optional[str] = None
        self.fall_answer_text: Optional[str] = None

        
        self.tasks: Dict[str, asyncio.Task] = {}         
        
        
        self.wifi_active: Optional[str] = None
        self.wifi_scan: List[Dict[str, Any]] = []
        self.wifi_profiles: List[Dict[str, Any]] = []

        self.wifi_active_ts: float = 0.0
        self.wifi_scan_ts: float = 0.0
        self.wifi_profiles_ts: float = 0.0

        
        self.wifi_ui_last_ping: float = 0.0

        
        self.wifi_busy: bool = False

        
        self.wifi_cache_lock = asyncio.Lock()
