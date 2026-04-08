#include <WiFi.h>
#include <HTTPClient.h>
#include <esp_wifi.h>

/**
 * @summary ESP32 PIR 이벤트를 RPi(프로비저닝 AP)로 전송합니다.
 * - PIR 상승 에지 ISR로 이벤트 감지
 * - 소프트 디바운스로 노이즈/떨림 최소화
 * - 이벤트는 최소 간격 정책으로 전송 제한
 * - 이벤트가 없으면 주기적으로 ping 전송
 */

/* ===== Wi-Fi / 서버 설정 ===== */
static const char* apSsid = "A105-RPI-PROV";
static const char* apPass = "A1051234";

static const char* serverHost = "192.168.4.1";
static const uint16_t serverPort = 8080;

static const char* eventEndpoint = "/eeum/event";
static const char* pingEndpoint  = "/api/device/ping";

/* ===== 디바이스 식별 ===== */
static const char* deviceId = "EEUM-E105-1";
static const char* deviceKind = "pir";

/* ===== PIR 핀 ===== */
static const int pirPin = 27;

/* ===== 정책(운영/시연) ===== */
struct Policy {
  uint32_t warmupMs;        // 부팅 직후 센서 안정화 시간
  uint32_t minEventGapMs;   // 이벤트 전송 최소 간격
  uint32_t pingIntervalMs;  // 마지막 전송 이후 ping 주기
};

static const Policy demoPolicy = {
  3000,
  10000,
  30000,
};

static const Policy prodPolicy = {
  60 * 1000UL,
  10 * 60 * 1000UL,
  10 * 60 * 1000UL,
};

/* 시연 모드: true면 demoPolicy, false면 prodPolicy */
static const bool demoMode = true;

/* ===== 공통 타임아웃 ===== */
static const uint32_t wifiConnectTimeoutMs = 15000;
static const uint32_t httpConnectTimeoutMs = 3000;
static const uint32_t httpTotalTimeoutMs   = 5000;
static const uint32_t pingRetryCooldownMs  = 5000;

/* ===== 소프트 디바운스 =====
 * ISR 발생 후 너무 가까운 시점의 처리는 버립니다.
 * (센서 노이즈/짧은 떨림 방지용)
 */
static const uint32_t pirSoftDebounceMs = 150;

/* ===== ISR 플래그 ===== */
volatile bool pirRiseFlag = false;
volatile uint32_t pirIsrMs = 0;

/**
 * @summary PIR 상승 에지 ISR
 * - ISR에서는 최소 작업만 수행(시간 기록 + 플래그)
 */
void IRAM_ATTR onPirRise() {
  pirIsrMs = millis();
  pirRiseFlag = true;
}

/* ===== 상태 ===== */
static uint32_t lastTxMs = 0;
static uint32_t nextPirAllowedMs = 0;
static uint32_t lastPingTryMs = 0;

/**
 * @summary 현재 모드에 맞는 정책을 반환합니다.
 * @returns Policy 참조
 */
static const Policy& policy() {
  return demoMode ? demoPolicy : prodPolicy;
}

/**
 * @summary Wi-Fi 절전 모드를 설정합니다.
 */
static void setupWifiPowerSave() {
  WiFi.mode(WIFI_STA);
  WiFi.setSleep(true);
  esp_wifi_set_ps(WIFI_PS_MIN_MODEM);
}

/**
 * @summary AP에 연결되어 있지 않으면 연결을 보장합니다.
 */
static void ensureWifiConnected() {
  if (WiFi.status() == WL_CONNECTED) return;

  setupWifiPowerSave();
  WiFi.begin(apSsid, apPass);

  uint32_t start = millis();
  while (WiFi.status() != WL_CONNECTED) {
    delay(200);

    if (millis() - start > wifiConnectTimeoutMs) {
      WiFi.disconnect(false);
      delay(200);
      WiFi.begin(apSsid, apPass);
      start = millis();
    }
  }
}

/**
 * @summary JSON을 HTTP POST로 전송합니다.
 * @param endpoint 서버 엔드포인트 경로
 * @param body JSON 문자열
 * @returns 성공 여부(HTTP code 1~399)
 */
static bool postJson(const char* endpoint, const String& body) {
  ensureWifiConnected();

  String url = String("http://") + serverHost + ":" + serverPort + endpoint;

  HTTPClient http;
  http.setConnectTimeout(httpConnectTimeoutMs);
  http.setTimeout(httpTotalTimeoutMs);

  if (!http.begin(url)) return false;

  http.addHeader("Content-Type", "application/json");
  int code = http.POST((uint8_t*)body.c_str(), body.length());
  http.end();

  return (code > 0 && code < 400);
}

/**
 * @summary PIR 이벤트 payload를 생성합니다.
 * @returns JSON 문자열
 */
static String buildPirBody() {
  return String("{\"kind\":\"") + deviceKind +
         "\",\"device_id\":\"" + deviceId +
         "\",\"data\":{\"event\":\"motion\",\"value\":1}}";
}

/**
 * @summary ping payload를 생성합니다.
 * @returns JSON 문자열
 */
static String buildPingBody() {
  return String("{\"device_id\":\"") + deviceId +
         "\",\"kind\":\"" + deviceKind + "\"}";
}

/**
 * @summary PIR 이벤트를 전송합니다.
 * @returns 성공 여부
 */
static bool sendPirEvent() {
  return postJson(eventEndpoint, buildPirBody());
}

/**
 * @summary ping을 전송합니다.
 * @returns 성공 여부
 */
static bool sendPing() {
  return postJson(pingEndpoint, buildPingBody());
}

void setup() {
  pinMode(pirPin, INPUT_PULLDOWN);

  ensureWifiConnected();
  attachInterrupt(digitalPinToInterrupt(pirPin), onPirRise, RISING);

  sendPing();
  lastTxMs = millis();
}

void loop() {
  const uint32_t now = millis();
  const Policy& p = policy();

  bool fired = false;
  uint32_t isrMs = 0;

  noInterrupts();
  if (pirRiseFlag) {
    fired = true;
    isrMs = pirIsrMs;
    pirRiseFlag = false;
  }
  interrupts();

  if (fired) {
    const bool warmupOk = now >= p.warmupMs;
    const bool intervalOk = (int32_t)(now - nextPirAllowedMs) >= 0;
    const bool softDebounceOk = (now - isrMs) >= pirSoftDebounceMs;
    const bool pinHigh = digitalRead(pirPin) == HIGH;

    if (warmupOk && intervalOk && softDebounceOk && pinHigh) {
      if (sendPirEvent()) {
        lastTxMs = now;
        nextPirAllowedMs = now + p.minEventGapMs;
      }
    }
  }

  const bool needPing = (now - lastTxMs) >= p.pingIntervalMs;
  const bool canRetryPing = (now - lastPingTryMs) >= pingRetryCooldownMs;

  if (needPing && canRetryPing) {
    lastPingTryMs = now;
    if (sendPing()) {
      lastTxMs = now;
    }
  }

  delay(20);
}
