import threading
import time
import json
import logging
import websocket
from ..config import WS_SERVER_URL, DEVICE_ID

logger = logging.getLogger(__name__)

class WebSocketStreamer:
    def __init__(self, controller):
        self.controller = controller
        self.stop_event = threading.Event()
        self.thread = None
        self.ws = None
        self.is_connected = False

    def start(self):
        if self.thread and self.thread.is_alive():
            return
        self.stop_event.clear()
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()
        logger.info("[STREAMER] WebSocket Streamer started")

    def stop(self):
        self.stop_event.set()
        if self.ws:
            self.ws.close()
        if self.thread:
            self.thread.join(timeout=2.0)
        logger.info("[STREAMER] WebSocket Streamer stopped")

    def _run(self):
        retry_delay = 1
        max_delay = 60

        while not self.stop_event.is_set():
            try:
                logger.info(f"[STREAMER] Connecting to {WS_SERVER_URL}...")
                self.ws = websocket.create_connection(WS_SERVER_URL, timeout=5)
                
                # Handshake: REGISTER_DEVICE
                register_msg = {
                    "type": "REGISTER_DEVICE",
                    "deviceId": DEVICE_ID
                }
                self.ws.send(json.dumps(register_msg))
                logger.info(f"[STREAMER] Registered as device: {DEVICE_ID}")
                
                self.is_connected = True
                retry_delay = 1 # Reset retry delay
                
                self._stream_loop()

            except Exception as e:
                self.is_connected = False
                if not self.stop_event.is_set():
                    logger.warning(f"[STREAMER] Connection error: {e}. Retrying in {retry_delay}s...")
                    time.sleep(retry_delay)
                    retry_delay = min(retry_delay * 2, max_delay)
            finally:
                if self.ws:
                    self.ws.close()
                    self.ws = None

    def _stream_loop(self):
        last_count = 0
        sent_count = 0
        while not self.stop_event.is_set() and self.is_connected:
            try:
                # Wait for raw frame (non-overlay preferred for relay)
                jpg, current_count = self.controller.wait_for_raw_frame(last_count, timeout=1.0)
                
                if jpg is None:
                    # logger.debug("[STREAMER] No frame in last 1s")
                    continue
                
                last_count = current_count
                
                # Send binary frame
                self.ws.send_binary(jpg)
                sent_count += 1
                
                if sent_count % 30 == 0:
                    logger.info(f"[STREAMER] Successfully sent {sent_count} frames to backend")
                
            except Exception as e:
                logger.error(f"[STREAMER] Error in stream loop: {e}")
                self.is_connected = False
                break
