package com.example.eeum

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 심박수 데이터 수신 서비스 (Wear OS -> Mobile)
 *
 * Wear OS 앱에서 전송하는 실시간 심박수 데이터를 수신합니다.
 * 수신된 데이터는 UI 업데이트를 위해 브로드캐스트되며, 측정 요청이 있는 경우 버퍼링 후 백엔드로 전송됩니다.
 */
class HeartRateListenerService : WearableListenerService() {

    private val metricsBuffer = mutableListOf<Int>()
    
    // 프론트엔드 타임아웃 전에 완료하기 위해 버퍼 크기 조정 (30초 -> 28개 샘플)
    private val BUFFER_SIZE = 28 

    companion object {
        private const val TAG = "HeartRateListener"
    }

    /**
     * 메시지 수신 핸들러
     *
     * @param messageEvent 수신된 메시지 이벤트
     */
    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/heart-rate") {
            val heartRateStr = String(messageEvent.data)
            val hrValue = heartRateStr.toDoubleOrNull()?.toInt() ?: return

            // 1. UI 실시간 업데이트를 위해 MainActivity로 이벤트 전달
            MainActivity.emitNotification(
                notificationId = "HR_UPDATE",
                type = "HEALTH",
                title = "Heart Rate",
                message = heartRateStr,
                groupName = ""
            )

            // 2. 대기 중인 측정 요청 확인
            val prefs = getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
            val familyId = prefs.getString("pending_measurement_family_id", null)
            val relatedId = prefs.getString("pending_measurement_related_id", null)
            
            if (familyId != null) {
                // 데이터 버퍼링 (타이밍 동기화를 위해 0도 포함할 수 있음)
                metricsBuffer.add(hrValue)
                Log.d(TAG, "심박수 버퍼링: $hrValue. 현재 크기: ${metricsBuffer.size}/$BUFFER_SIZE")

                if (metricsBuffer.size >= BUFFER_SIZE) {
                    // 통계 계산
                    val validMetrics = metricsBuffer.filter { it > 0 }
                    var min = 0
                    var max = 0
                    var avg = 0

                    if (validMetrics.isNotEmpty()) {
                        min = validMetrics.minOrNull() ?: 0
                        max = validMetrics.maxOrNull() ?: 0
                        avg = validMetrics.average().toInt()
                    } else {
                         Log.w(TAG, "버퍼가 가득 찼으나 유효한 심박수(>0)가 없습니다. 0으로 설정합니다.")
                    }

                    Log.d(TAG, "버퍼 가득 참. 통계 업로드 -> 최소: $min, 최대: $max, 평균: $avg")
                    
                    // 백엔드 업로드
                    uploadHeartRateStats(min, max, avg, familyId, relatedId)

                    // 측정 상태 초기화
                    prefs.edit()
                        .remove("pending_measurement_family_id")
                        .remove("pending_measurement_related_id")
                        .apply()
                    metricsBuffer.clear()
                }
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    /**
     * 심박수 통계 업로드
     *
     * 계산된 심박수 통계 데이터를 백엔드 API로 전송합니다.
     *
     * @param min 최소 심박수
     * @param max 최대 심박수
     * @param avg 평균 심박수
     * @param familyId 측정 대상 가족 ID
     * @param relatedId 관련 이벤트 ID (선택 사항)
     */
    private fun uploadHeartRateStats(min: Int, max: Int, avg: Int, familyId: String, relatedId: String?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val now = LocalDateTime.now().format(formatter)
                
                // JSON 생성
                val jsonBuilder = StringBuilder()
                jsonBuilder.append("{")
                jsonBuilder.append("\"minRate\": $min,")
                jsonBuilder.append("\"maxRate\": $max,")
                jsonBuilder.append("\"avgRate\": $avg,")
                jsonBuilder.append("\"measuredAt\": \"$now\",")
                jsonBuilder.append("\"familyId\": $familyId")
                
                if (relatedId != null) {
                    jsonBuilder.append(", \"relatedId\": $relatedId")
                }
                
                jsonBuilder.append("}")
                
                val jsonBody = jsonBuilder.toString()

                // API 호출
                val urlStr = "${BuildConfig.API_BASE_URL}/api/health/heart-rate"
                val url = URL(urlStr)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                
                // 인증 토큰 추가
                val authPrefs = getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                val token = authPrefs.getString("access_token", "")
                if (!token.isNullOrEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                }

                conn.doOutput = true
                conn.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "심박수 통계 업로드 완료. 응답 코드: $responseCode")
                
            } catch (e: Exception) {
                Log.e(TAG, "심박수 통계 업로드 실패", e)
            }
        }
    }
}

