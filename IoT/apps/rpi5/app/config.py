import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------
# env helpers
# ---------------------------------------------------------------------

def _get_env_str(name: str, default: Optional[str] = None, *, required: bool = False) -> Optional[str]:
    """
    환경변수를 문자열로 읽습니다.
    값이 없거나 빈 문자열이면 default를 반환합니다.
    """
    value = os.getenv(name)
    if value is None or value == "":
        if required:
            logger.warning("[config] missing required env: %s", name)
        return default
    return value

def _get_env_bool(name: str, default: bool = False) -> bool:
    """
    환경변수를 bool로 해석합니다.
    1/true/yes/on → True
    0/false/no/off → False
    """
    raw = (_get_env_str(name, None) or "").strip().lower()
    if raw in ("1", "true", "yes", "y", "on"):
        return True
    if raw in ("0", "false", "no", "n", "off"):
        return False
    return default

def _get_env_int(name: str, default: int, *, required: bool = False) -> int:
    """
    환경변수를 int로 해석합니다.
    파싱 실패 시 default로 fallback 합니다.
    """
    raw = _get_env_str(name, None, required=required)
    if raw is None or raw == "":
        return int(default)
    try:
        return int(str(raw).strip())
    except Exception:
        logger.warning("[config] invalid int env: %s=%r fallback=%s", name, raw, int(default))
        return int(default)

def _get_env_float(name: str, default: float, *, required: bool = False) -> float:
    """
    환경변수를 float로 해석합니다.
    """
    raw = _get_env_str(name, None, required=required)
    if raw is None or raw == "":
        return float(default)
    try:
        return float(str(raw).strip())
    except Exception:
        logger.warning("[config] invalid float env: %s=%r fallback=%s", name, raw, float(default))
        return float(default)

def _build_sub_topics(client_id: Optional[str]) -> list[str]:
    """
    CLIENT_ID 기준으로 MQTT subscribe 토픽을 구성합니다.
    CLIENT_ID가 없으면 subscribe 자체를 비활성화합니다.
    """
    cid = (client_id or "").strip()
    if not cid:
        logger.warning("[config] CLIENT_ID missing -> MQTT subscribe disabled")
        return []

    return [
        f"eeum/device/{cid}/update",
        f"eeum/device/{cid}/alarm",
    ]

def _get_debug_div() -> int:
    """
    DEBUG_DIV 계산 규칙:
    - DEBUG_DIV 환경변수 명시 시 해당 값 사용
    - 그렇지 않으면 EEUM_DEBUG=1 → 30, 아니면 1
    - 0 이하 값은 1로 보정
    """
    eeum_debug = EEUM_DEBUG
    default = 30 if eeum_debug else 1
    value = _get_env_int("DEBUG_DIV", default)

    if value <= 0:
        logger.warning("[config] DEBUG_DIV <= 0 is invalid. fallback to 1")
        return 1

    return value

# ---------------------------------------------------------------------
# network / device identity
# ---------------------------------------------------------------------

AP_PROFILE = "A105_AP"
AP_IFACE = "wlan1"
STA_IFACE = "wlan0"

HOST = _get_env_str("HOST", "0.0.0.0")
PORT = _get_env_int("PORT", 8080)

# API_BASE_URL이 비어있으면 sync 계열 기능은 사실상 비활성
API_BASE_URL = (_get_env_str("API_BASE_URL", "") or "").rstrip("/")
ALBUM_SYNC_PATH = _get_env_str("ALBUM_SYNC_PATH", "/api/iot/device/sync/album")
VOICE_SYNC_PATH = _get_env_str("VOICE_SYNC_PATH", "/api/iot/device/sync/voice")

SERVER_HOST = _get_env_str("SERVER_HOST")
SERVER_PORT = _get_env_int("SERVER_PORT", 8888)

CLIENT_ID = _get_env_str("CLIENT_ID")
USERNAME = _get_env_str("MQTT_USERNAME")
PASSWORD = _get_env_str("MQTT_PASSWORD")

PUB_TOPIC = {
    "no_motion": "eeum/event",
    "absence": "eeum/event",
    "response": "eeum/response",
    "online": "eeum/status",
    "offline": "eeum/status",
}

SUB_TOPICS = _build_sub_topics(CLIENT_ID)

# ---------------------------------------------------------------------
# local state files / paths
# ---------------------------------------------------------------------

DEFAULT_DEVICE = {
    "devices": {
        "vision": {"EEUM-J105": {"online": True, "last_seen_ts": None}},
        "pir": {"EEUM-E105-1": {"online": False, "last_seen_ts": None}},
    }
}

DEVICE_PATH = _get_env_str("DEVICE_PATH", "./device.json")
TOKEN_PATH = _get_env_str("TOKEN_PATH", "./token.json")
DB_PATH = _get_env_str("DB_PATH", "./app.db")

VOICE_PATH = _get_env_str("VOICE_PATH", "./voice")
ALBUM_PATH = _get_env_str("ALBUM_PATH", "./album")

DEFAULT_TTS_PATH = _get_env_str("DEFAULT_TTS_PATH", "./tts_voice")
PROFILE_PATH = _get_env_str("PROFILE_PATH", "./profile")
WEB_DIST_PATH = _get_env_str("WEB_DIST_PATH", "./dist")

# ---------------------------------------------------------------------
# alarm / tts
# ---------------------------------------------------------------------

# 기본 알람 TTS 재생 간격 (kind별 debounce는 state에서 관리)
ALARM_TTS_DEBOUNCE_SEC = _get_env_float("ALARM_TTS_DEBOUNCE_SEC", 8.0)

DEFAULT_TTS_MESSAGE = [
    "지금 괜찮으세요? 도와드릴까요?",
    "새로운 일정 알림이 있어요",
    "새로운 복약 알림이 있어요",
    "새로운 음성 메시지가 있어요",
]

DEFAULT_TTS_BY_KIND = {
    "fall": "지금 괜찮으세요? 도와드릴까요?",
    "schedule": "새로운 일정 알림이 있어요",
    "medication": "새로운 복약 알림이 있어요",
    "voice": "새로운 음성 메시지가 있어요",
}

# ---------------------------------------------------------------------
# offline / absence timing
# ---------------------------------------------------------------------

OFFLINE_AFTER_SEC = _get_env_float("OFFLINE_AFTER_SEC", 1800.0)
OFFLINE_CHECK_INTERVAL_SEC = _get_env_float("OFFLINE_CHECK_INTERVAL_SEC", 10.0)

# ---------------------------------------------------------------------
# audio tuning (HDMI 안정화 위주)
# ---------------------------------------------------------------------

AUDIO_IN_DEVICE = _get_env_str("AUDIO_IN_DEVICE", "plughw:CARD=Audio,DEV=0")
AUDIO_OUT_DEVICE = _get_env_str("AUDIO_OUT_DEVICE", "hdmi:CARD=vc4hdmi0,DEV=0")

AUDIO_RATE_HZ = _get_env_int("AUDIO_RATE_HZ", 48000)
AUDIO_CHANNELS = _get_env_int("AUDIO_CHANNELS", 2)

# 재생 시작 지연 / 프리롤 (첫 음절 잘림 방지)
AUDIO_START_DELAY_MS = _get_env_int("AUDIO_START_DELAY_MS", 100)
AUDIO_PREROLL_MS = _get_env_int("AUDIO_PREROLL_MS", 800)

# drain 보정 (ffmpeg/aplay 종료 타이밍 튜닝)
AUDIO_DRAIN_FUDGE_SEC = _get_env_float("AUDIO_DRAIN_FUDGE_SEC", 0.4)

# HDMI wake / keepalive 관련
AUDIO_WARMUP_MS = _get_env_int("AUDIO_WARMUP_MS", 700)
AUDIO_REWARM_IDLE_SEC = _get_env_float("AUDIO_REWARM_IDLE_SEC", 60.0)
AUDIO_KEEPALIVE_SEC = _get_env_float("AUDIO_KEEPALIVE_SEC", 30.0)
AUDIO_KEEPALIVE_MS = _get_env_int("AUDIO_KEEPALIVE_MS", 160)

# ALSA 버퍼 튜닝 (단위: us)
AUDIO_APLAY_BUFFER_TIME_US = _get_env_int("AUDIO_APLAY_BUFFER_TIME_US", 900000)
AUDIO_APLAY_PERIOD_TIME_US = _get_env_int("AUDIO_APLAY_PERIOD_TIME_US", 180000)

AUDIO_LOG_STDERR = _get_env_bool("AUDIO_LOG_STDERR", True)
AUDIO_SOFT_STOP = _get_env_bool("AUDIO_SOFT_STOP", True)

# ffmpeg/ffprobe 옵션은 문자열 그대로 사용 ("256k" 등 허용)
AUDIO_FFPROBE_PROBESIZE = _get_env_str("AUDIO_FFPROBE_PROBESIZE", "256k")
AUDIO_FFMPEG_ANALYZE_DURATION = _get_env_str("AUDIO_FFMPEG_ANALYZE_DURATION", "200k")

AUDIO_PREEMPT_ONLY_FALL = _get_env_bool("AUDIO_PREEMPT_ONLY_FALL", True)

# ---------------------------------------------------------------------
# STT tuning
# ---------------------------------------------------------------------

# fall TTS 직후 에코로 인한 오탐 방지
FALL_ECHO_GUARD_SEC = _get_env_float("FALL_ECHO_GUARD_SEC", 0.35)

# STT 오탐/미탐 트레이드오프 파라미터
STT_NO_SPEECH_THRESHOLD = _get_env_float("STT_NO_SPEECH_THRESHOLD", 0.60)
STT_LOG_PROB_THRESHOLD = _get_env_float("STT_LOG_PROB_THRESHOLD", -0.80)
STT_COMPRESSION_RATIO_THRESHOLD = _get_env_float("STT_COMPRESSION_RATIO_THRESHOLD", 2.40)

STT_BEAM_SIZE = _get_env_int("STT_BEAM_SIZE", 5)
STT_BEST_OF = _get_env_int("STT_BEST_OF", 5)

# 프레임 RMS 기준 (너무 낮으면 잡음으로 판단)
STT_MIN_FRAME_RMS = _get_env_int("STT_MIN_FRAME_RMS", 140)

# ---------------------------------------------------------------------
# debug / timing scale
# ---------------------------------------------------------------------

EEUM_DEBUG = _get_env_bool("EEUM_DEBUG", False)
DEBUG_DIV = _get_debug_div()

# 디버그 모드에서 부재 판정 시간을 단축하기 위해 DEBUG_DIV로 나눔
PIR_ABSENCE_SEC = (2 * 60 * 60) / DEBUG_DIV
VISION_EXIT_ABSENCE_SEC = (60 * 60) / DEBUG_DIV
