package com.example.eeum.wear.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

/**
 * 핸드폰 메시지 수신 서비스
 *
 * 핸드폰(Mobile) 앱에서 발송한 명령을 수신하여 처리합니다.
 * - 긴급 측정 시작 (/emergency/start)
 * - 측정 중지 (/emergency/stop)
 */
class WearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "메시지 수신: ${messageEvent.path}")

        if (messageEvent.path == "/emergency/start") {
            Log.d(TAG, "요청 수신: 긴급 측정 시작 (화면 켜짐)")
            
            // 1. 빠른 반응을 위해 즉시 WakeLock 획득
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "EeumWatch:EmergencyWakeLock"
            )
            wakeLock.acquire(5000) // 5초간 유지
            
            vibrateWatch()

            // 2. 인텐트 준비
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("action", "start_service")
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // 3. 높은 우선순위 알림 채널 생성 (화면 깨우기용)
            val channelId = "emergency_channel_critical_v2"
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId, "긴급 알림", 
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "긴급 측정을 위해 화면을 깨웁니다."
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 4. 전체 화면 인텐트(Full Screen Intent)를 포함한 알림 실행
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("심박수 측정")
                .setContentText("측정을 시작합니다...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true) // 화면 강제 켜짐
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
            
            // 5. 직접 실행 시도 (fallback: '다른 앱 위에 표시' 권한이 있는 경우 작동)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "직접 실행 실패 (권한 제한 등): ${e.message}")
            }
            
        } else if (messageEvent.path == "/emergency/stop") {
             Log.d(TAG, "요청 수신: 심박수 측정 중지")
             vibrateWatch()
             
             // 화면 잠금 해제를 위해 액티비티에 중지 명령 전달
             val intent = Intent(this, MainActivity::class.java).apply {
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                 putExtra("action", "stop_service")
             }
             startActivity(intent)
             
             // 서비스도 직접 중지
             val serviceIntent = Intent(this, HeartRateService::class.java)
             stopService(serviceIntent)
        }
    }

    private fun vibrateWatch() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(100, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
    }

    companion object {
        private const val TAG = "WearableListener"
    }
}

