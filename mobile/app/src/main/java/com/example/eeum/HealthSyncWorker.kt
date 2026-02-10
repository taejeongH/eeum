package com.example.eeum

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

/**
 * 건강 데이터 동기화 워커
 *
 * 백그라운드에서 주기적으로 삼성 헬스 데이터를 수집하여 백엔드 서버로 전송합니다.
 * FCM 알림이나 주기적 작업 예약에 의해 실행될 수 있습니다.
 *
 * @param context 애플리케이션 컨텍스트
 * @param workerParams 워커 파라미터
 */
class HealthSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    /**
     * 워커 실행 로직
     *
     * 1. 인증 토큰 및 가족 그룹 ID 확인
     * 2. 삼성 헬스 데이터 수집
     * 3. 백엔드 API 형식에 맞춰 데이터 변환
     * 4. 서버로 데이터 전송
     *
     * @return 작업 결과 (Success, Failure, Retry)
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tag = "HealthSyncWorker"
        Log.i(tag, "🔄 건강 데이터 동기화 작업 시작")

        try {
            // 1. 인증 및 그룹 ID 확인
            val authPrefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val token = authPrefs.getString("access_token", null)
            
            // FCM 요청(inputData)에서 먼저 확인하고, 없으면 저장된 환경설정에서 가져옴
            val groupIdStr = inputData.getString("family_id") ?: authPrefs.getString("family_id", null)

            // FCM에서 받았지만 환경설정에 없다면, 향후 주기적 작업을 위해 저장
            if (!groupIdStr.isNullOrEmpty() && authPrefs.getString("family_id", null) == null) {
                authPrefs.edit().putString("family_id", groupIdStr).apply()
                Log.d(tag, "💾 family_id를 SharedPreferences에 저장: $groupIdStr")
            }

            if (token.isNullOrEmpty() || groupIdStr.isNullOrEmpty()) {
                Log.e(tag, "❌ 인증 토큰 또는 그룹 ID 누락 (Token: ${if (token == null) "null" else "존재"}, Group: $groupIdStr)")
                if (token.isNullOrEmpty()) {
                    Log.w(tag, "⚠️ 토큰이 없어 동기화를 건너뜁니다. 다음 주기를 기다립니다.")
                }
                return@withContext Result.failure()
            }

            val groupId = groupIdStr.toIntOrNull() ?: run {
                Log.e(tag, "❌ 잘못된 그룹 ID: $groupIdStr")
                return@withContext Result.failure()
            }

            // 2. 삼성 헬스 데이터 수집
            val healthManager = SamsungHealthManager(applicationContext)
            if (!healthManager.hasAllPermissions()) {
                Log.w(tag, "⚠️ 삼성 헬스 권한이 없습니다. 동기화 중단.")
                return@withContext Result.failure()
            }

            val dataJson = healthManager.getAllHealthMetrics()
            if (dataJson == null) {
                Log.w(tag, "⚠️ 동기화할 건강 데이터가 없습니다.")
                return@withContext Result.success()
            }

            Log.d(tag, "📊 수집된 데이터 (Raw): $dataJson")

            // 3. 백엔드 규격에 맞게 변환 (snake_case -> camelCase)
            val rawData = JSONObject(dataJson)
            val mappedData = JSONObject()
            
            val keyMap = mapOf(
                "record_date" to "recordDate",
                "resting_heart_rate" to "restingHeartRate",
                "average_heart_rate" to "averageHeartRate",
                "max_heart_rate" to "maxHeartRate",
                "steps" to "steps",
                "sleep_total_minutes" to "sleepTotalMinutes",
                "sleep_deep_minutes" to "sleepDeepMinutes",
                "sleep_light_minutes" to "sleepLightMinutes",
                "sleep_rem_minutes" to "sleepRemMinutes",
                "blood_oxygen" to "bloodOxygen",
                "blood_glucose" to "bloodGlucose",
                "systolic_pressure" to "systolicPressure",
                "diastolic_pressure" to "diastolicPressure",
                "active_calories" to "activeCalories",
                "active_minutes" to "activeMinutes"
            )

            for ((snake, camel) in keyMap) {
                if (rawData.has(snake)) {
                    mappedData.put(camel, rawData.get(snake))
                }
            }
            
            // 배열 형태로 래핑 (백엔드가 리스트 수신)
            val finalBody = "[$mappedData]"

            Log.i(tag, "📤 백엔드로 전송 중... (Group: $groupId, URL: ${BuildConfig.API_BASE_URL})")

            // 4. API 전송
            val urlStr = "${BuildConfig.API_BASE_URL}/api/health/data?groupId=$groupId"
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 15000
            
            conn.outputStream.use { os ->
                os.write(finalBody.toByteArray(Charsets.UTF_8))
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                Log.i(tag, "✅ 건강 데이터 동기화 성공 (Code: $responseCode)")
                return@withContext Result.success()
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error message"
                Log.e(tag, "❌ 건강 데이터 동기화 실패 (Code: $responseCode): $errorMsg")
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e(tag, "❌ 건강 데이터 동기화 중 치명적 오류 발생", e)
            return@withContext Result.retry()
        }
    }
}

