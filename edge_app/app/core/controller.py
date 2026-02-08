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
    - latest_jpeg_overlay: 스켈레톤/오버레이 포함 스트림
    - latest_jpeg_raw: 선 없는 원본 스트림
    """
    def __init__(self, model_factory):
        self.model_factory = model_factory

        self.camera_manager = CameraManager()
        self.server_client = ServerClient()
        self.device_state = get_device_state()

        self.stop_event = threading.Event()
        self.background_thread: Optional[threading.Thread] = None
        self.state_lock = threading.Lock()

        self.processing_mode: str = "initial"
        self.latest_obs: Optional[Dict[str, Any]] = None

        # ✅ 두 개로 분리
        self.latest_jpeg_overlay: Optional[bytes] = None
        self.latest_jpeg_raw: Optional[bytes] = None

        # ✅ 스트림도 각각 카운터/condition 분리 (간단/안전)
        self.frame_count_overlay = 0
        self.frame_count_raw = 0
        self.cond_overlay = threading.Condition()
        self.cond_raw = threading.Condition()

        self.current_mode: Optional[BaseMode] = None

        # Replay
        self.replay_thread = None
        self.replay_stop_event = threading.Event()
        self.replay_running = False

        # Recording
        self.record_lock = threading.Lock()
        self.record_fp = None
        self.record_path = None

        _register_temp_for_windows()

    def start(self):
        if self.background_thread and self.background_thread.is_alive():
            return

        self.stop_event.clear()
        self.camera_manager.ensure_opened()

        logger.info("Starting background processing loop")
        self.background_thread = threading.Thread(target=self._processing_loop, daemon=True)
        self.background_thread.start()

    def stop(self):
        logger.info("Stopping background processing loop")
        self.stop_event.set()
        if self.background_thread:
            self.background_thread.join(timeout=5)

        self.stop_replay()

        if self.current_mode:
            self.current_mode.cleanup()
            self.current_mode = None

        self.camera_manager.release()

    def _initialize_mode_instance(self):
        cap = self.camera_manager.ensure_opened() and self.camera_manager.get_cap()
        if not cap:
            logger.error("Camera setup failed")
            return None

        is_registered = self.device_state.is_registered()

        from ..modes import QRMode
        if QRMode is not None and not is_registered:
            logger.info("Device not registered. Switching to QRMode.")
            self.camera_manager.configure(width=640, height=480)
            self.camera_manager.try_autofocus()
            mode = QRMode(cap=cap, jpeg_quality=JPEG_QUALITY)
            self.set_processing_mode("qr")
            return mode

        logger.info("Switching to LiveMode")
        try:
            model = self.model_factory()
        except Exception as e:
            logger.error(f"Failed to load model: {e}")
            return None

        self.camera_manager.configure(width=1920, height=1080)
        from ..modes import LiveMode
        mode = LiveMode(model=model, cap=cap, jpeg_quality=JPEG_QUALITY)
        self.set_processing_mode("live")
        return mode

    def _processing_loop(self):
        while not self.stop_event.is_set():
            if self.replay_running:
                time.sleep(0.1)
                continue

            if self.current_mode is None:
                self.current_mode = self._initialize_mode_instance()
                if self.current_mode:
                    if not self.current_mode.setup():
                        logger.error("Mode setup failed")
                        self.current_mode = None
                        time.sleep(1.0)
                        continue

            if self.processing_mode == "qr" and self.device_state.is_registered():
                logger.info("Device registered in QRMode. Switching to LiveMode.")
                self.current_mode.cleanup()
                self.current_mode = None
                continue

            try:
                if self.current_mode:
                    # ✅ LiveMode.step()가 (obs, overlay_jpg, raw_frame, frame) 형태로 리턴한다고 가정
                    #    (너가 LiveMode를 그렇게 바꿔야 함)
                    obs, overlay_jpg, raw_frame, _ = self.current_mode.step()

                    # raw_frame -> raw_jpg 인코딩
                    raw_jpg = None
                    if raw_frame is not None:
                        ok, enc = cv2.imencode(
                            ".jpg",
                            raw_frame,
                            [int(cv2.IMWRITE_JPEG_QUALITY), JPEG_QUALITY]
                        )
                        if ok:
                            raw_jpg = enc.tobytes()

                    with self.state_lock:
                        if obs is not None:
                            self.latest_obs = obs

                    # ✅ overlay jpeg 갱신
                    if overlay_jpg is not None:
                        with self.state_lock:
                            self.latest_jpeg_overlay = overlay_jpg
                        with self.cond_overlay:
                            self.frame_count_overlay += 1
                            self.cond_overlay.notify_all()

                    # ✅ raw jpeg 갱신
                    if raw_jpg is not None:
                        with self.state_lock:
                            self.latest_jpeg_raw = raw_jpg
                        with self.cond_raw:
                            self.frame_count_raw += 1
                            self.cond_raw.notify_all()

                    if obs is not None:
                        self._write_record(obs)
                else:
                    time.sleep(1.0)

            except Exception as e:
                logger.error(f"Error in processing loop: {e}")
                time.sleep(0.5)
                if self.current_mode:
                    self.current_mode.cleanup()
                    self.current_mode = None

            time.sleep(0.001)

    def set_processing_mode(self, mode: str):
        with self.state_lock:
            self.processing_mode = mode

    def get_latest_obs(self):
        with self.state_lock:
            return self.latest_obs

    # ✅ overlay용 wait
    def wait_for_overlay_frame(self, last_count: int, timeout: float = 1.0) -> Tuple[Optional[bytes], int]:
        with self.cond_overlay:
            if self.frame_count_overlay <= last_count:
                if not self.cond_overlay.wait(timeout=timeout):
                    return None, last_count
        with self.state_lock:
            return self.latest_jpeg_overlay, self.frame_count_overlay

    # ✅ raw용 wait
    def wait_for_raw_frame(self, last_count: int, timeout: float = 1.0) -> Tuple[Optional[bytes], int]:
        with self.cond_raw:
            if self.frame_count_raw <= last_count:
                if not self.cond_raw.wait(timeout=timeout):
                    return None, last_count
        with self.state_lock:
            return self.latest_jpeg_raw, self.frame_count_raw

    # --- Recording (기존 그대로) ---
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

    # --- Replay (기존 그대로) ---
    def start_replay(self, path: str, fps: float):
        if self.replay_running:
            return False

        self.stop_replay()
        self.replay_stop_event.clear()

        def on_frame(obs, jpg):
            with self.state_lock:
                self.latest_obs = obs
                # replay는 overlay만 있다고 가정
                self.latest_jpeg_overlay = jpg
            with self.cond_overlay:
                self.frame_count_overlay += 1
                self.cond_overlay.notify_all()

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

            if self.current_mode:
                mode_name = "live" if isinstance(self.current_mode, LiveMode) else "qr"
                self.set_processing_mode(mode_name)
            else:
                self.set_processing_mode("initial")

            return True
        return False
