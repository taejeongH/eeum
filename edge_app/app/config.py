import os

# 기본 설정
CAM_INDEX = int(os.getenv("CAM_INDEX", "0"))
FRAME_W = int(os.getenv("FRAME_W", "640"))
FRAME_H = int(os.getenv("FRAME_H", "480"))
JPEG_QUALITY = int(os.getenv("JPEG_QUALITY", "80"))
DEFAULT_CONF = float(os.getenv("DEFAULT_CONF", "0.35"))

DETERMINISTIC = os.getenv("DETERMINISTIC", "1") == "1"
USE_HALF = False if DETERMINISTIC else (os.getenv("USE_HALF", "1") == "1")

MODEL_PATH = os.getenv("MODEL_PATH", "yolov8n-pose.pt")
DEVICE_ID = os.getenv("DEVICE_ID", "edge_device_01")
LOCATION_ID = os.getenv("LOCATION_ID", "location_01")
SERVER_URL = os.getenv("SERVER_URL", "http://example.com/api/notify")
RPI_URL = os.getenv("RPI_URL", "http://rpi.local/api/notify")  

# JSONL / Clip 저장 경로
RUNS_DIR = os.getenv("RUNS_DIR", "runs")
CLIP_DIR = os.getenv("CLIP_DIR", f"{RUNS_DIR}/clips")

# 클립 저장 파라미터
CLIP_FPS = int(os.getenv("CLIP_FPS", "30"))
CLIP_PRE_SEC = int(os.getenv("CLIP_PRE_SEC", "6"))
CLIP_POST_SEC = int(os.getenv("CLIP_POST_SEC", "6"))
CLIP_COOLDOWN_S = float(os.getenv("CLIP_COOLDOWN_S", "30.0"))
CLIP_EVENT_POST_SEC = int(os.getenv("CLIP_EVENT_POST_SEC", "7"))

# EMA 스무딩
KP_EMA_ENABLE = os.getenv("KP_EMA_ENABLE", "1") == "1"
KP_EMA_ALPHA = float(os.getenv("KP_EMA_ALPHA", "0.75"))
KP_MIN_CONF_FOR_SMOOTH = float(os.getenv("KP_MIN_CONF", "0.30"))
