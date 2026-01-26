import json
import queue
import threading
import time
from typing import Any, Dict, Optional, Tuple

import requests

class Notifier:
    def __init__(
        self,
        server_url: str,
        rpi_url: str,
        timeout_sec: float = 2.0,
        max_queue: int = 200,
    ):
        self.server_url = server_url.rstrip("/")
        self.rpi_url = rpi_url.rstrip("/")
        self.timeout_sec = timeout_sec
        self.q: "queue.Queue[Tuple[str, Dict[str, Any], Optional[str]]]" = queue.Queue(maxsize=max_queue)
        self._stop = threading.Event()
        self.t = threading.Thread(target=self._worker, daemon=True)
        self.t.start()

    def stop(self):
        self._stop.set()

    def send_event(self, payload: Dict[str, Any]):
        self._put(("event", payload, None))

    def send_event_rpi_only(self, payload: Dict[str, Any]):
        self._put(("event_rpi", payload, None))  # 추가: rpi만

    def send_clip(self, event_id: str, clip_path: str, payload: Dict[str, Any]):
        payload2 = dict(payload)
        payload2["event_id"] = event_id
        self._put(("clip", payload2, clip_path))

    def _put(self, item):
        try:
            self.q.put_nowait(item)
        except queue.Full:
            pass

    def _post_json(self, url: str, payload: Dict[str, Any]):
        try:
            requests.post(url, json=payload, timeout=self.timeout_sec)
        except Exception:
            return

    def _post_clip(self, url: str, payload: Dict[str, Any], clip_path: str):
        try:
            with open(clip_path, "rb") as f:
                files = {"file": ("clip.mp4", f, "video/mp4")}
                data = {"meta": json.dumps(payload, ensure_ascii=False)}
                requests.post(url, data=data, files=files, timeout=max(10.0, self.timeout_sec * 10))
        except Exception:
            return

    def _worker(self):
        while not self._stop.is_set():
            try:
                kind, payload, clip_path = self.q.get(timeout=0.2)
            except queue.Empty:
                continue

            if kind == "event":
                self._post_json(f"{self.server_url}/event", payload)
                self._post_json(f"{self.rpi_url}/event", payload)

            elif kind == "event_rpi":
                self._post_json(f"{self.rpi_url}/event", payload)

            elif kind == "clip":
                self._post_clip(f"{self.server_url}/clips", payload, clip_path)
                self._post_clip(f"{self.rpi_url}/clips", payload, clip_path)
            self.q.task_done()
