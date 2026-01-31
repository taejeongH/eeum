AP_PROFILE = "A105_AP"
AP_IFACE = "wlan1"
HOST = "0.0.0.0"
PORT = 8080
STA_IFACE = "wlan0"
VISION_IP = "10.10.0.2"
SERVER_HOST = "i14a105.p.ssafy.io"
SERVER_PORT = 8888
CLIENT_ID = "EEUM-R105"
USERNAME = "eeum_device"
PASSWORD = "OJhsnNN1+YuREJ53zcdehVavQu1jsxG+"
PUB_TOPIC = {
    "no_motion": "eeum/event",
    "absence": "eeum/event",
    "response": "eeum/response",
    "image": "eeum/update",
    "voice": "eeum/voice",
    "online": "eeum/status",
    "offline": "eeum/status"
}
SUB_TOPICS = ["eeum/device/EEUM-R105/update", "eeum/device/EEUM-R105/alarm"]

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
    },
    "USERNAME": None,
    "PASSWORD": None,
    "token": None,
    "device_id": "EEUM-R105",
    "type": "main",
    "updated_at": None
}
DEVICE_PATH = "./device.json"