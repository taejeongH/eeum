AP_PROFILE = "A105_AP"
AP_IFACE = "wlan1"
HOST = "0.0.0.0"
PORT = 8080
STA_IFACE = "wlan0"
VISION_IP = "10.10.0.2"
SERVER_HOST = "i14a105.p.ssafy.io"
SERVER_PORT = 8888
DEFAULT_DEVICE = {
    "devices": {
        "vision": {
            "EEUM_J105": {
                "online": True,
                "location": None,
                "last_seen_ts": None
            }},
        "pir": {
            "EEUM_E105_1": {
                "online": False,
                "location": None,
                "last_seen_ts": None
            }}
    },
    "device_id": "EEUM_R105",
    "type": "main",
    "group_id": None,
    "updated_at": None
}
DEVICE_PATH = "./device.json"