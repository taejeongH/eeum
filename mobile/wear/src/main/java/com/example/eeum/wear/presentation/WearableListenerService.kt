package com.example.eeum.wear.presentation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")

        if (messageEvent.path == "/emergency/start") {
            Log.d(TAG, "Request received: Start Activity with Screen ON")
            
            // 1. Acquire WakeLock immediately to keep CPU running
            val powerManager = getSystemService(POWER_SERVICE) as android.os.PowerManager
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "EeumWatch:EmergencyWakeLock"
            )
            wakeLock.acquire(5000) // Hold for 5 seconds
            
            vibrateWatch()

            // 2. Prepare Intent
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                putExtra("action", "start_service")
            }
            
            val pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent, 
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            // 3. Create High Priority Channel (New ID to force update)
            val channelId = "emergency_channel_critical_v2"
            val notificationManager = getSystemService(android.app.NotificationManager::class.java)
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    channelId, "Emergency Alert", 
                    android.app.NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Wakes up screen for emergency measurement"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            // 4. Build Notification with Full Screen Intent
            val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Heart Rate Measurement")
                .setContentText("Starting...")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX) // MAX Priority
                .setCategory(androidx.core.app.NotificationCompat.CATEGORY_ALARM) // ALARM Category
                .setFullScreenIntent(pendingIntent, true) // Force Screen On
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(1001, notification)
            
            // 5. Try Direct Start (As a fallback, works if 'Display over other apps' is granted)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Direct start failed (expected if restricted): ${e.message}")
            }
            
            // Release WakeLock after a short delay is handled by timeout, strictly release here logic-wise is risky if notify acts async, but 5s timeout covers it.

        } else if (messageEvent.path == "/emergency/stop") {
             Log.d(TAG, "Request received: Stop Heart Rate Monitoring")
             vibrateWatch()
             
             // Send Stop Command to Activity to release Screen Lock
             val intent = Intent(this, MainActivity::class.java).apply {
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                 putExtra("action", "stop_service")
             }
             startActivity(intent)
             
             // Also stop service directly just in case
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
