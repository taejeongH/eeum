package com.example.eeum

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch

class HeartRateListenerService : WearableListenerService() {

    private val metricsBuffer = mutableListOf<Int>()
    private val BUFFER_SIZE = 28 // Reduce slightly to ensure finish before frontend timeout

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/heart-rate") {
            val heartRateStr = String(messageEvent.data)
            val hrValue = heartRateStr.toDoubleOrNull()?.toInt() ?: return

            // 1. Emit to MainActivity for UI (Real-time update if needed)
            MainActivity.emitNotification(
                notificationId = "HR_UPDATE",
                type = "HEALTH",
                title = "Heart Rate",
                message = "$heartRateStr",
                groupName = ""
            )

            // 2. Check for pending measurement request
            val prefs = getSharedPreferences("health_prefs", android.content.Context.MODE_PRIVATE)
            val familyId = prefs.getString("pending_measurement_family_id", null)
            val relatedId = prefs.getString("pending_measurement_related_id", null)
            
            if (familyId != null) {
                // Buffer the data (include 0s to keep timing sync with frontend)
                metricsBuffer.add(hrValue)
                Log.d(TAG, "Buffered HR: $hrValue. Current Size: ${metricsBuffer.size}/$BUFFER_SIZE")

                if (metricsBuffer.size >= BUFFER_SIZE) {
                    // Calculate stats
                    val validMetrics = metricsBuffer.filter { it > 0 }
                    var min = 0
                    var max = 0
                    var avg = 0

                    if (validMetrics.isNotEmpty()) {
                        min = validMetrics.minOrNull() ?: 0
                        max = validMetrics.maxOrNull() ?: 0
                        avg = validMetrics.average().toInt()
                    } else {
                         Log.w(TAG, "Buffer Full but NO valid heart rates (>0). Defaulting to 0.")
                    }

                    Log.d(TAG, "Buffer Full. Uploading Stats -> Min: $min, Max: $max, Avg: $avg")
                    
                    // Always upload
                    uploadHeartRateStats(min, max, avg, familyId, relatedId)

                    // Clear state
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

    private fun uploadHeartRateStats(min: Int, max: Int, avg: Int, familyId: String, relatedId: String?) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                // Construct JSON for HeartRateRequestDTO
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
                val now = java.time.LocalDateTime.now().format(formatter)
                
                // Construct JSON with optional relatedId
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

                // Use the new heart rate endpoint
                val urlStr = BuildConfig.API_BASE_URL + "/api/health/heart-rate" 
                val url = java.net.URL(urlStr)
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                
                // Get Auth Token
                val authPrefs = getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)
                val token = authPrefs.getString("access_token", "")
                if (!token.isNullOrEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer $token")
                }

                conn.doOutput = true
                conn.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = conn.responseCode
                Log.d(TAG, "Uploaded Heart Rate Stats. Response: $responseCode")
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload Heart Rate Stats", e)
            }
        }
    }

    companion object {
        private const val TAG = "WearListener"
    }
}
