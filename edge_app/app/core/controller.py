
import threading
import time
import logging
import cv2
import sys
from typing import Optional, Dict, Any, Tuple

from ..utils.camera import CameraManager
from ..api.server_client import ServerClient
from ..state.device_state import get_device_state
from ..config import DEVICE_ID, LOCATION_ID, RUNS_DIR, DETERMINISTIC, JPEG_QUALITY
from ..modes.base_mode import BaseMode
from ..modes.qr_mode import QRMode
from ..modes.live_mode import LiveMode
from ..utils import start_replay_thread

logger = logging.getLogger(__name__)

# 임시 테스트 등록 (Windows)
def _register_temp_for_windows():
    device_state = get_device_state()
    if sys.platform == "win32" and not device_state.is_registered():
        logger.info("[TEST] Windows environment - Creating temporary registration")
        device_state.register(
            device_id="TEST_DEVICE_001",
            access_token="test_access_token_xxx",
            refresh_token="test_refresh_token_xxx",
            group_id=1,
            serial_number="TEST-001"
        )

class AppController:
    """
    애플리케이션의 메인 제어 흐름과 상태를 관리하는 컨트롤러
    - 카메라 및 모드 관리
    - 백그라운드 루프 실행
    - API와 공유할 상태 (latest_obs, latest_jpeg 등) 관리
    """
    def __init__(self, model_factory):
        self.model_factory = model_factory  # YOLO 모델 생성 함수 또는 인스턴스
        
        # Managers & Clients
        self.camera_manager = CameraManager()
        self.server_client = ServerClient()
        self.device_state = get_device_state()
        self.clip_recorder = None # LiveMode에서 접근 또는 여기서 관리? 
                                  # 기존 구조상 LiveMode/LivePipeline 안에 있음. 
                                  # 하지만 상태 조회(health)를 위해 참조가 필요할 수 있음.
                                  # 현재는 LiveMode 인스턴스를 통해 접근하도록 함.

        # Threading
        self.stop_event = threading.Event()
        self.background_thread: Optional[threading.Thread] = None
        self.state_lock = threading.Lock()

        # Shared State
        self.processing_mode: str = "initial" # initial/qr/live/replay
        self.latest_obs: Optional[Dict[str, Any]] = None
        self.latest_jpeg: Optional[bytes] = None
        self.frame_count = 0
        self.frame_condition = threading.Condition()
        
        self.current_mode: Optional[BaseMode] = None

        # Replay logic
        self.replay_thread = None
        self.replay_stop_event = threading.Event()
        self.replay_running = False

        # Recording
        self.record_lock = threading.Lock()
        self.record_fp = None
        self.record_path = None

        # Windows Test
        _register_temp_for_windows()

    def start(self):
        """백그라운드 스레드 시작"""
        if self.background_thread and self.background_thread.is_alive():
            return
        
        self.stop_event.clear()
        self.camera_manager.ensure_opened()
        
        logger.info("Starting background processing loop")
        self.background_thread = threading.Thread(target=self._processing_loop, daemon=True)
        self.background_thread.start()

    def stop(self):
        """백그라운드 스레드 중지"""
        logger.info("Stopping background processing loop")
        self.stop_event.set()
        if self.background_thread:
            self.background_thread.join(timeout=5)
        
        # Cleanup replay
        self.stop_replay()
        
        # 모드 정리
        if self.current_mode:
            self.current_mode.cleanup()
            self.current_mode = None
            
        # 카메라 해제
        self.camera_manager.release()

    def _initialize_mode_instance(self):
        """현재 상태에 맞는 모드 인스턴스 생성"""
        # 카메라 확인
        cap = self.camera_manager.ensure_opened() and self.camera_manager.get_cap()
        if not cap:
            logger.error("Camera setup failed")
            return None

        is_registered = self.device_state.is_registered()
        
        # 1. QR 모드 (미등록 상태)
        if QRMode is not None and not is_registered:
            logger.info("Device not registered. Switching to QRMode.")
            self.camera_manager.configure(width=640, height=480)
            mode = QRMode(cap=cap, jpeg_quality=JPEG_QUALITY)
            self.set_processing_mode("qr")
            return mode
        
        # 2. Live 모드 (등록 상태 or QR 불가)
        else:
            reason = "Registered" if is_registered else "QRMode unavailable"
            logger.info(f"Switching to LiveMode ({reason})")
            
            # LiveMode용 모델 준비
            try:
                model = self.model_factory() # Factory 호출
                # 모델 파라미터 설정을 여기서 할 수도 있고 factory에서 할 수도 있음
            except Exception as e:
                logger.error(f"Failed to load model: {e}")
                return None
                
            self.camera_manager.configure(width=1920, height=1080) # Config 값 참조하는게 좋음
            mode = LiveMode(model=model, cap=cap, jpeg_quality=JPEG_QUALITY)
            
            # LiveMode 인스턴스에서 clip_recorder 참조가 필요하면 여기서 연결
            # (현재 구조에서는 LiveMode 내부 pipeline이 가지고 있음)
            
            self.set_processing_mode("live")
            return mode

    def _processing_loop(self):
        """메인 루프"""
        while not self.stop_event.is_set():
            # 리플레이 중이면 루프 일시 중지 (또는 별도 처리)
            if self.replay_running:
                time.sleep(0.1)
                continue

            # 모드 초기화 또는 전환 체크
            if self.current_mode is None:
                self.current_mode = self._initialize_mode_instance()
                if self.current_mode:
                    if not self.current_mode.setup():
                        logger.error("Mode setup failed")
                        self.current_mode = None
                        time.sleep(1.0)
                        continue
            
            # QR -> Live 자동 전환 체크
            if self.processing_mode == "qr" and self.device_state.is_registered():
                logger.info("Device registered in QRMode. Switching to LiveMode.")
                self.current_mode.cleanup()
                self.current_mode = None # 다음 루프에서 LiveMode로 재생성
                continue
            
            # Step 실행
            try:
                if self.current_mode:
                    obs, jpg, frame = self.current_mode.step()
                    
                    # 프레임이 있지만 jpg가 없는 경우 (인코딩 실패 등)
                    if jpg is None and frame is not None:
                        # 비상용 인코딩
                         ok, enc = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY])
                         if ok: jpg = enc.tobytes()

                    # 상태 업데이트
                    with self.state_lock:
                        if obs is not None:
                            self.latest_obs = obs
                        if jpg is not None:
                            self.latest_jpeg = jpg
                            with self.frame_condition:
                                self.frame_count += 1
                                self.frame_condition.notify_all()
                    
                    # 녹화
                    if obs is not None:
                        self._write_record(obs)
                else:
                    time.sleep(1.0)
                    
            except Exception as e:
                logger.error(f"Error in processing loop: {e}")
                time.sleep(0.5)
                # 치명적 오류 시 모드 재시작 시도
                if self.current_mode:
                    self.current_mode.cleanup()
                    self.current_mode = None
            
            time.sleep(0.001)

    # --- State Accessors ---
    def get_latest_jpeg(self):
        with self.state_lock:
            return self.latest_jpeg

    def get_latest_obs(self):
        with self.state_lock:
            return self.latest_obs

    def wait_for_frame(self, last_count: int, timeout: float = 1.0) -> Tuple[Optional[bytes], int]:
        """새로운 프레임이 준비될 때까지 대기"""
        with self.frame_condition:
            if self.frame_count <= last_count:
                if not self.frame_condition.wait(timeout=timeout):
                    return None, last_count
            
            # 최신 JPEG 가져오기 (이미 lock 안임)
            return self.latest_jpeg, self.frame_count

    def get_status(self):
        # 모드별 세부 상태
        clip_status = {}
        if isinstance(self.current_mode, LiveMode):
             # LiveMode -> Pipeline -> ClipRecorder 접근 필요
             # 리팩토링 시 LiveMode에 get_status() 메소드 추가 권장
             if self.current_mode.pipeline and self.current_mode.pipeline.clip_recorder:
                 clip_status = self.current_mode.pipeline.clip_recorder.status()

        return {
            "processing_mode": self.processing_mode,
            "is_recording": self.record_fp is not None,
            "replay_running": self.replay_running,
            **clip_status
        }
        
    def set_processing_mode(self, mode: str):
        with self.state_lock:
            self.processing_mode = mode

    # --- Recording ---
    def start_recording(self):
        import os
        os.makedirs(RUNS_DIR, exist_ok=True)
        fname = time.strftime("obs_%Y%m%d_%H%M%S.jsonl")
        path = os.path.join(RUNS_DIR, fname)
        
        with self.record_lock:
            if self.record_fp:
                self.record_fp.close()
            self.record_fp = open(path, "a", encoding="utf-8")
            self.record_path = path
        return path

    def stop_recording(self):
        path = None
        with self.record_lock:
            if self.record_fp:
                self.record_fp.close()
                self.record_fp = None
            path = self.record_path
            self.record_path = None
        return path

    def _write_record(self, obs):
        with self.record_lock:
            if self.record_fp:
                import json
                self.record_fp.write(json.dumps(obs, ensure_ascii=False) + "\n")
                self.record_fp.flush()

    # --- Replay ---
    def start_replay(self, path: str, fps: float):
        if self.replay_running:
            return False
            
        self.stop_replay()
        self.replay_stop_event.clear()
        
        def on_frame(obs, jpg):
            with self.state_lock:
                self.latest_obs = obs
                self.latest_jpeg = jpg
        
        self.replay_thread = start_replay_thread(
            path=path,
            fps=fps,
            jpeg_quality=JPEG_QUALITY,
            stop_event=self.replay_stop_event,
            on_frame=on_frame
        )
        self.replay_running = True
        self.set_processing_mode("replay")
        return True

    def stop_replay(self):
        if self.replay_running:
            self.replay_stop_event.set()
            if self.replay_thread:
                self.replay_thread.join(timeout=2.0)
            self.replay_running = False
            
            # Live로 복귀
            if self.current_mode: 
                # 모드는 그대로지만 processing_mode 태그 업데이트
                mode_name = "live" if isinstance(self.current_mode, LiveMode) else "qr"
                self.set_processing_mode(mode_name)
            else:
                 self.set_processing_mode("initial")
                 
            return True
        return False
