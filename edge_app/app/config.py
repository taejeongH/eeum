import os

# 기본 설정
CAM_INDEX = int(os.getenv("CAM_INDEX", "0"))
FRAME_W = int(os.getenv("FRAME_W", "1920"))
FRAME_H = int(os.getenv("FRAME_H", "1080"))
JPEG_QUALITY = int(os.getenv("JPEG_QUALITY", "80"))
DEFAULT_CONF = float(os.getenv("DEFAULT_CONF", "0.50"))
# 자꾸 젯슨 다운되는 문제 때문에 모델 식별 값 조정
MODEL_IOU = float(os.getenv("MODEL_IOU", "0.50"))
MODEL_DET = float(os.getenv("MODEL_DET", "30"))

DETERMINISTIC = os.getenv("DETERMINISTIC", "1") == "1"
USE_HALF = False if DETERMINISTIC else (os.getenv("USE_HALF", "1") == "1")

MODEL_PATH = os.getenv("MODEL_PATH", "yolov8s-pose.engine")
DEVICE_ID = os.getenv("DEVICE_ID", "EEUM-J105")
DEVICE_NAME = os.getenv("DEVICE_NAME", "Jetson-Orin-Nano")
LOCATION_ID = os.getenv("LOCATION_ID", "LivingRoom")
SERVER_URL = os.getenv("SERVER_URL", "https://i14a105.p.ssafy.io")
RPI_URL = os.getenv("RPI_URL", "http://10.10.0.1:8080/eeum")# ("RPI_URL", "http://70.12.245.104:8080/eeum")

# JSONL / Clip 저장 경로
RUNS_DIR = os.getenv("RUNS_DIR", "runs")
CLIP_DIR = os.getenv("CLIP_DIR", f"{RUNS_DIR}/clips")

# 클립 저장 파라미터
CLIP_FPS = int(os.getenv("CLIP_FPS", "15"))
CLIP_RESIZE_WIDTH = int(os.getenv("CLIP_RESIZE_WIDTH", "640"))
CLIP_RESIZE_HEIGHT = int(os.getenv("CLIP_RESIZE_HEIGHT", "480"))
CLIP_PRE_SEC = int(os.getenv("CLIP_PRE_SEC", "6"))
CLIP_POST_SEC = int(os.getenv("CLIP_POST_SEC", "6"))
CLIP_COOLDOWN_S = float(os.getenv("CLIP_COOLDOWN_S", "30.0"))
CLIP_EVENT_POST_SEC = int(os.getenv("CLIP_EVENT_POST_SEC", "7"))

# EMA 스무딩
KP_EMA_ENABLE = os.getenv("KP_EMA_ENABLE", "1") == "1"
KP_EMA_ALPHA = float(os.getenv("KP_EMA_ALPHA", "0.75"))
KP_MIN_CONF_FOR_SMOOTH = float(os.getenv("KP_MIN_CONF", "0.30"))

# 인덱스 별 EMA 스무딩
KP_ALPHA_BY_ID = {
    # COCO17 기준 예시(너 id 매핑이 COCO17이면 그대로)
    # 중심부 크게(더 부드럽게) / 끝단 작게(더 부드럽게) 는 정의에 따라 다름
    # 현재 수식(alpha*prev + (1-alpha)*curr) 기준: alpha가 클수록 더 부드러움
    11: 0.55, 12: 0.55,  # hips
    5: 0.50, 6: 0.50,    # shoulders
    7: 0.45, 8: 0.45,    # elbows
    13: 0.45, 14: 0.45,  # knees
    9: 0.35, 10: 0.35,   # wrists
    15: 0.35, 16: 0.35,  # ankles
}
KP_ALPHA_DEFAULT = 0.45

# confidence 구간별 alpha multiplier
KP_CONF_HOLD = 0.20
KP_CONF_MID = 0.50
KP_ALPHA_MUL_LOW = 1.2   # conf 낮으면 더 부드럽게(이전값 유지 강화)
KP_ALPHA_MUL_MID = 1.0
KP_ALPHA_MUL_HIGH = 0.9  # conf 높으면 반응 조금 빠르게

# bbox 정규화
KP_JUMP_ENABLE = True
KP_JUMP_RATIO = 0.12      # bbox_diag 대비 이동비율
KP_JUMP_RATIO_EXT = 0.18  # 손/발 같은 끝단은 허용치 더 크게(선택)
KP_JUMP_ALPHA_MUL = 1.4   # 점프면 더 부드럽게(이전값 가중↑)
KP_JUMP_HOLD_IF_LOWCONF = True