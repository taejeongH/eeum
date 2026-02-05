import os
import time
import socket
import requests
import json
import logging

from pathlib import Path

# from fastapi import FastAPI  # 여기서는 필요 없어서 주석 처리 (원하면 유지해도 됨)

logger = logging.getLogger("ip_reporter")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

state_file = Path("runs/device_state.json")

device_state = {}
if state_file.exists():
    try:
        with open(state_file, "r", encoding="utf-8") as f:
            device_state = json.load(f) or {}
    except Exception as e:
        logger.error(f"Failed to load state: {e}")
        device_state = {}

SERVER_URL = os.getenv("SERVER_URL", "https://i14a105.p.ssafy.io").rstrip("/")
DEVICE_ID = device_state.get("device_id") or os.getenv("IP_REPORTER_DEVICE_ID", "")
TOKEN = device_state.get("access_token") or os.getenv("IP_REPORTER_TOKEN", "")
FAMILIES_ID = device_state.get("group_id")
if FAMILIES_ID is None:
    FAMILIES_ID = int(os.getenv("IP_REPORTER_FAMILIES_ID", "0"))

POLL_SEC = float(os.getenv("IP_POLL_SEC", "5"))


def get_local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    finally:
        s.close()


def report_ip(ipAddress: str):
    headers = {}
    if TOKEN:
        headers["Authorization"] = f"Bearer {TOKEN}"

    payload = {"ipAddress": ipAddress}

    url = f"{SERVER_URL}/api/iot/device/{FAMILIES_ID}/streaming-ip"

    # ✅ POST -> PATCH
    r = requests.patch(url, json=payload, headers=headers, timeout=3)

    # 실패하면 로그에 남기기 (디버깅에 매우 유용)
    try:
        r.raise_for_status()
        logger.info(f"Reported IP via PATCH: {ipAddress} -> {url} (status={r.status_code})")
    except requests.HTTPError:
        logger.error(
            f"PATCH failed status={r.status_code} body={r.text} url={url}"
        )
        raise


def main():
    prev_ip = None
    while True:
        try:
            ipAddress = get_local_ip()
            if ipAddress != prev_ip:
                report_ip(ipAddress)
                prev_ip = ipAddress
        except Exception as e:
            logger.error(f"ip reporter error: {e}")
        time.sleep(POLL_SEC)


if __name__ == "__main__":
    main()
