package com.example.eeum

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

class HealthSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tag = "HealthSyncWorker"
        Log.i(tag, "🔄 Starting Health Sync Background Work")

        try {
            // 1. Check Auth & Group ID
            val authPrefs = applicationContext.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
            val token = authPrefs.getString("access_token", null)
            
            // Try getting from inputData (FCM request) first, then fallback to prefs
            val groupIdStr = inputData.getString("family_id") ?: authPrefs.getString("family_id", null)

            // If we got it from FCM but it's not in prefs, save it for future periodic syncs
            if (!groupIdStr.isNullOrEmpty() && authPrefs.getString("family_id", null) == null) {
                authPrefs.edit().putString("family_id", groupIdStr).apply()
                Log.d(tag, "💾 Saved family_id to SharedPreferences from inputData: $groupIdStr")
            }

            if (token.isNullOrEmpty() || groupIdStr.isNullOrEmpty()) {
                Log.e(tag, "❌ Missing Auth Token or Group ID (Token: ${if (token == null) "null" else "present"}, Group: $groupIdStr)")
                if (token.isNullOrEmpty()) {
                    Log.w(tag, "⚠️ Sync skipped because of empty token. Waiting for next window.")
                }
                return@withContext Result.failure()
            }

            val groupId = groupIdStr.toIntOrNull() ?: run {
                Log.e(tag, "❌ Invalid Group ID: $groupIdStr")
                return@withContext Result.failure()
            }

            // 2. Fetch Data from Samsung Health
            val healthManager = SamsungHealthManager(applicationContext)
            if (!healthManager.hasAllPermissions()) {
                Log.w(tag, "⚠️ Missing Samsung Health Permissions. Sync aborted.")
                return@withContext Result.failure()
            }

            val dataJson = healthManager.getAllHealthMetrics()
            if (dataJson == null) {
                Log.w(tag, "⚠️ No health data found to sync")
                return@withContext Result.success()
            }

            Log.d(tag, "📊 Data Collected (Raw): $dataJson")

            // 3. Map snake_case to camelCase for Backend
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
            
            val finalBody = "[$mappedData]"

            Log.i(tag, "📤 Uploading to Backend... (Group: $groupId, URL: ${BuildConfig.API_BASE_URL})")

            // 4. Send to API
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
                Log.i(tag, "✅ Health Sync Successful (Code: $responseCode)")
                return@withContext Result.success()
            } else {
                val errorMsg = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error message"
                Log.e(tag, "❌ Health Sync Failed (Code: $responseCode): $errorMsg")
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            Log.e(tag, "❌ Critical Error during Health Sync", e)
            return@withContext Result.retry()
        }
    }
}
