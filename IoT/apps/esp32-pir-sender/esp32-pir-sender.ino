#include <WiFi.h>
#include <HTTPClient.h>
#include <esp_wifi.h>

// ==================================================
// AP 정보
// ==================================================
static const char* AP_SSID = "A105-RPI-PROV";
static const char* AP_PASS = "A1051234";

// ==================================================
// 서버 정보
// ==================================================
static const char* SERVER_HOST = "192.168.4.1";
static const uint16_t SERVER_PORT = 8080;
static const char* EVENT_ENDPOINT = "/eeum/event";
static const char* PING_ENDPOINT  = "/api/device/ping";

// ==================================================
// 디바이스 정보
// ==================================================
static const char* DEVICE_NAME = "EEUM-E105-1";

// ==================================================
// PIR
// ==================================================
static const int PIR_PIN = 27;
static const int DEBUG_DIV = 60;

// ==================================================
// 정책
// ==================================================
static const uint32_t WARMUP_MS = 60 * 1000UL / DEBUG_DIV;
static const uint32_t MIN_PIR_INTERVAL_MS = 10 * 60 * 1000UL / DEBUG_DIV;
static const uint32_t WIFI_CONNECT_TIMEOUT_MS = 15000;

// PING 정책
static const uint32_t PING_INTERVAL_MS = 10 * 60 * 1000UL / DEBUG_DIV;
static const uint32_t PING_RETRY_COOLDOWN_MS = 5000 / DEBUG_DIV;

// ==================================================
// ISR 플래그
// ==================================================
volatile bool pirRiseFlag = false;
volatile uint32_t pirIsrMs = 0;

// ==================================================
// 전송 타이머 상태
// ==================================================
static uint32_t g_lastTxMs = 0;
static uint32_t g_nextPirAllowedMs = 0;
static uint32_t g_lastPingTryMs = 0;

// ==================================================
// PIR ISR
// ==================================================
void IRAM_ATTR onPirRise() {
  pirIsrMs = millis();
  pirRiseFlag = true;
}

// ==================================================
// Wi-Fi 유틸
// ==================================================
static void setupWifiPowerSave() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(true);
  esp_wifi_set_ps(WIFI_PS_MIN_MODEM);
}

static void ensureWifiConnected() {
  if (WiFi.status() == WL_CONNECTED) return;

  setupWifiPowerSave();
  WiFi.begin(AP_SSID, AP_PASS);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(200);
    if (millis() - start > WIFI_CONNECT_TIMEOUT_MS) {
      WiFi.disconnect(false);
      delay(200);
      WiFi.begin(AP_SSID, AP_PASS);
      start = millis();
    }
  }
}

// ==================================================
// HTTP POST 공통
// ==================================================
static bool httpPostJson(const char* endpoint, const String& body) {
  ensureWifiConnected();

  String url = String("http://") + SERVER_HOST + ":" + SERVER_PORT + endpoint;

  HTTPClient http;
  http.setConnectTimeout(3000);
  http.setTimeout(5000);

  if (!http.begin(url)) return false;
  http.addHeader("Content-Type", "application/json");

  int code = http.POST((uint8_t*)body.c_str(), body.length());
  http.end();

  return (code > 0 && code < 400);
}

// ==================================================
// PIR 전송
// ==================================================
static bool postPirMotion() {
  String body = "{";
  body += "\"kind\":\"pir\",";
  body += "\"device_id\":\"" + String(DEVICE_NAME) + "\",";
  body += "\"data\":{";
  body += "\"event\":\"motion\",";
  body += "\"value\":1";
  body += "}}";

  return httpPostJson(EVENT_ENDPOINT, body);
}

// ==================================================
// PING 전송
// ==================================================
static bool postPing() {
  String body = "{";
  body += "\"device_id\":\"" + String(DEVICE_NAME) + "\",";
  body += "\"kind\":\"pir\"";
  body += "}";

  return httpPostJson(PING_ENDPOINT, body);
}

// ==================================================
// setup()
// ==================================================
void setup() {
  pinMode(PIR_PIN, INPUT_PULLDOWN);

  ensureWifiConnected();
  attachInterrupt(digitalPinToInterrupt(PIR_PIN), onPirRise, RISING);

  postPing();
  g_lastTxMs = millis();
}

// ==================================================
// loop()
// ==================================================
void loop() {
  uint32_t now = millis();

  // ---------- PIR 처리 ----------
  bool fired = false;
  uint32_t isrT = 0;

  noInterrupts();
  if (pirRiseFlag) {
    fired = true;
    isrT = pirIsrMs;
    pirRiseFlag = false;
  }
  interrupts();

  if (fired) {
    if (now >= WARMUP_MS &&
        (int32_t)(now - g_nextPirAllowedMs) >= 0 &&
        digitalRead(PIR_PIN) == HIGH) {

      bool ok = postPirMotion();
      if (ok) {
        g_lastTxMs = now;
        g_nextPirAllowedMs = now + MIN_PIR_INTERVAL_MS;
      }
    }
  }

  // ---------- PING 처리 ----------
  if ((now - g_lastTxMs) >= PING_INTERVAL_MS) {
    if ((now - g_lastPingTryMs) >= PING_RETRY_COOLDOWN_MS) {
      g_lastPingTryMs = now;
      bool ok = postPing();
      if (ok) {
        g_lastTxMs = now;
      }
    }
  }

  delay(20);
}
