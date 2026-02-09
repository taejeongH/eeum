import os
import logging

logger = logging.getLogger(__name__)

def _get_env(
    name: str,
    default: str | None = None,
    *,
    required: bool = False,
) -> str | None:
    v = os.getenv(name)
    if v is None or v == "":
        if required:
            logger.warning("[config] missing required env: %s", name)
        return default
    return v

AP_PROFILE = "A105_AP"
AP_IFACE = "wlan1"
HOST = _get_env("HOST", "0.0.0.0")
PORT = int(_get_env("PORT", "8080"))
STA_IFACE = "wlan0"
API_BASE_URL = (_get_env("API_BASE_URL", "") or "").rstrip("/")
ALBUM_SYNC_PATH = _get_env("ALBUM_SYNC_PATH", "/api/iot/device/sync/album")
SERVER_HOST = _get_env("SERVER_HOST")
SERVER_PORT = int(_get_env("SERVER_PORT", "8888"))
CLIENT_ID = _get_env("CLIENT_ID")
USERNAME = _get_env("MQTT_USERNAME")
PASSWORD = _get_env("MQTT_PASSWORD")

PUB_TOPIC = {
    "no_motion": "eeum/event",
    "absence": "eeum/event",
    "response": "eeum/response",
    "online": "eeum/status",
    "offline": "eeum/status"
}
if not CLIENT_ID:
    logger.warning("[config] CLIENT_ID missing -> MQTT subscribe disabled")
    SUB_TOPICS: list[str] = []
else:
    SUB_TOPICS = [
        f"eeum/device/{CLIENT_ID}/update",
        f"eeum/device/{CLIENT_ID}/alarm",
    ]
DEFAULT_DEVICE = {
    "devices": {
        "vision": {
            "EEUM-J105": {
                "online": True,
                "last_seen_ts": None
            }},
        "pir": {
            "EEUM-E105-1": {
                "online": False,
                "last_seen_ts": None
            }}
    }
}

DEVICE_PATH = _get_env("DEVICE_PATH", "./device.json")
TOKEN_PATH = _get_env("TOKEN_PATH", "./token.json")
DB_PATH = _get_env("DB_PATH", "./app.db")

VOICE_PATH = _get_env("VOICE_PATH", "./voice")
VOICE_SYNC_PATH = _get_env("VOICE_SYNC_PATH", "/api/iot/device/sync/voice")
ALBUM_PATH = _get_env("ALBUM_PATH", "./album")
DEFAULT_TTS_PATH = _get_env("DEFAULT_TTS_PATH", "./tts_voice")
PROFILE_PATH = _get_env("PROFILE_PATH", "./profile")
WEB_DIST_PATH = _get_env("WEB_DIST_PATH", "./dist")

ALARM_TTS_DEBOUNCE_SEC = float(_get_env("ALARM_TTS_DEBOUNCE_SEC", "8"))

DEFAULT_TTS_MESSAGE = [
    "지금 괜찮으세요? 도와드릴까요?",
    "새로운 일정 알림이 있어요",
    "새로운 복약 알림이 있어요",
    "새로운 음성 메시지가 있어요"
]

DEFAULT_TTS_BY_KIND = {
    "fall": "지금 괜찮으세요? 도와드릴까요?",
    "schedule": "새로운 일정 알림이 있어요",
    "medication": "새로운 복약 알림이 있어요",
    "voice": "새로운 음성 메시지가 있어요"
}

OFFLINE_AFTER_SEC = float(_get_env("OFFLINE_AFTER_SEC", "1800"))  
OFFLINE_CHECK_INTERVAL_SEC = float(_get_env("OFFLINE_CHECK_INTERVAL_SEC", "10"))

AUDIO_IN_DEVICE = _get_env("AUDIO_IN_DEVICE", "plughw:CARD=Audio,DEV=0")
AUDIO_OUT_DEVICE = _get_env("AUDIO_OUT_DEVICE", "hdmi:CARD=vc4hdmi0,DEV=0")

AUDIO_RATE_HZ = int(_get_env("AUDIO_RATE_HZ", "48000"))
AUDIO_CHANNELS = int(_get_env("AUDIO_CHANNELS", "2"))

AUDIO_START_DELAY_MS = int(_get_env("AUDIO_START_DELAY_MS", "100"))
AUDIO_PREROLL_MS = int(_get_env("AUDIO_PREROLL_MS", "800"))
AUDIO_DRAIN_FUDGE_SEC = float(_get_env("AUDIO_DRAIN_FUDGE_SEC", "0.4"))


AUDIO_WARMUP_MS = int(_get_env("AUDIO_WARMUP_MS", "700"))
AUDIO_REWARM_IDLE_SEC = float(_get_env("AUDIO_REWARM_IDLE_SEC", "60"))
AUDIO_KEEPALIVE_SEC = float(_get_env("AUDIO_KEEPALIVE_SEC", "30")) 
AUDIO_KEEPALIVE_MS = int(_get_env("AUDIO_KEEPALIVE_MS", "160"))



FALL_ECHO_GUARD_SEC = float(_get_env("FALL_ECHO_GUARD_SEC", "0.35"))





STT_NO_SPEECH_THRESHOLD = float(_get_env("STT_NO_SPEECH_THRESHOLD", "0.60"))

STT_LOG_PROB_THRESHOLD = float(_get_env("STT_LOG_PROB_THRESHOLD", "-0.80"))

STT_COMPRESSION_RATIO_THRESHOLD = float(_get_env("STT_COMPRESSION_RATIO_THRESHOLD", "2.40"))


STT_BEAM_SIZE = int(_get_env("STT_BEAM_SIZE", "5"))
STT_BEST_OF = int(_get_env("STT_BEST_OF", "5"))


STT_MIN_FRAME_RMS = int(_get_env("STT_MIN_FRAME_RMS", "140"))