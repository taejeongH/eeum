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

ALARM_TTS_DEBOUNCE_SEC = float(_get_env("ALARM_TTS_DEBOUNCE_SEC", "60"))

DEFAULT_TTS_MESSAGE = [
    "괜찮으세요? 도와드릴까요?",
    "일정 알림이 있어요",
    "복약 알림이 있어요",
    "음성 메시지가 있어요"
]

DEFAULT_TTS_BY_KIND = {
    "fall_detected": "괜찮으세요? 도와드릴까요?",
    "schedule": "일정 알림이 있어요",
    "medication": "복약 알림이 있어요",
    "voice": "음성 메시지가 있어요"
}

AUDIO_IN_DEVICE = _get_env("AUDIO_IN_DEVICE", "")  # 예: plughw:CARD=Audio,DEV=0
OFFLINE_AFTER_SEC = float(_get_env("OFFLINE_AFTER_SEC", "1800"))  # 30분
OFFLINE_CHECK_INTERVAL_SEC = float(_get_env("OFFLINE_CHECK_INTERVAL_SEC", "10"))