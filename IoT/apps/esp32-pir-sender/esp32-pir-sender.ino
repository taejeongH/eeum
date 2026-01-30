#include <WiFi.h>
#include <HTTPClient.h>
#include <esp_wifi.h>

// ====== AP 정보 ======
static const char* AP_SSID = "A105-RPI-PROV";
static const char* AP_PASS = "A1051234";

// ====== 서버 ======
static const char* SERVER_HOST = "192.168.4.1";
static const uint16_t SERVER_PORT = 8080;
static const char* EVENT_ENDPOINT = "/eeum/event";

// ====== 디바이스 ======
static const char* DEVICE_NAME = "EEUM-E105-1";

// ====== PIR ======
static const int PIR_PIN = 27;
static const int DEBUG_DIV = 20;
// ====== 정책 ======
static const uint32_t WARMUP_MS = 60 * 1000UL;              // 초기 60초 무시
static const uint32_t MIN_POST_INTERVAL_MS = 10 * 60 * 1000UL / DEBUG_DIV; // 최소 간격 10분
static const uint32_t WIFI_CONNECT_TIMEOUT_MS = 15000;

// ====== ISR 플래그 ======
volatile bool pirRiseFlag = false;
volatile uint32_t pirIsrMs = 0;

void IRAM_ATTR onPirRise() {
  pirIsrMs = millis();
  pirRiseFlag = true;
}

static void setupWifiPowerSave() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(true);
  esp_wifi_set_ps(WIFI_PS_MIN_MODEM); // MAX_MODEM 고려
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

static bool postPir1() {
  ensureWifiConnected();

  String url = String("http://") + SERVER_HOST + ":" + SERVER_PORT + EVENT_ENDPOINT;
  String body = "{";
  body += "\"kind\":\"pir\",";
  body += "\"device_id\":\"" + String(DEVICE_NAME) + "\",";
  body += "\"data\":{";
  body += "\"event\":\"motion\",";
  body += "\"value\":1";
  body += "}}";

  HTTPClient http;
  http.setConnectTimeout(3000);
  http.setTimeout(5000);

  if (!http.begin(url)) return false;
  http.addHeader("Content-Type", "application/json");

  int code = http.POST((uint8_t*)body.c_str(), body.length());
  http.end();

  return (code > 0 && code < 400);
}

void setup() {
  Serial.begin(115200);
  delay(200);

  pinMode(PIR_PIN, INPUT);

  ensureWifiConnected();

  attachInterrupt(digitalPinToInterrupt(PIR_PIN), onPirRise, RISING);

  Serial.println("[BOOT] started");
}

void loop() {
  static uint32_t nextAllowedMs = 0; // 다음 전송 허용 시각

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
    uint32_t now = millis();

    // 1) 워밍업 무시
    if (now < WARMUP_MS) {
      Serial.println("[PIR] ignored (warmup)");
      delay(10);
      return;
    }

    // 2) rate limit: 10분에 1번만
    if ((int32_t)(now - nextAllowedMs) < 0) {
      Serial.println("[PIR] ignored (rate limit)");
      delay(10);
      return;
    }

    // 3) 핀 상태 확인으로 노이즈 컷
    if (digitalRead(PIR_PIN) != HIGH) {
      Serial.println("[PIR] ignored (pin not HIGH)");
      delay(10);
      return;
    }

    bool ok = postPir1();
    Serial.printf("[PIR] sent=1 ok=%d (isr=%lu)\n", ok ? 1 : 0, (unsigned long)isrT);

    // 다음 허용 시각 갱신
    nextAllowedMs = now + MIN_POST_INTERVAL_MS;
  }

  delay(20);
}
