package com.example.eeum

import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class HeartRateListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == "/heart-rate") {
            val heartRate = String(messageEvent.data)
            Log.d(TAG, "Received HR from Watch: $heartRate")

            // 1. Emit to MainActivity Flow if active
            MainActivity.emitNotification(
                notificationId = "HR_UPDATE",
                type = "HEALTH",
                title = "Heart Rate",
                message = "$heartRate BPM",
                groupName = ""
            )

            // 2. Broadcast for other components (optional but good for debugging)
            val intent = Intent("com.example.eeum.HEART_RATE_UPDATE").apply {
                putExtra("heart_rate", heartRate)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    companion object {
        private const val TAG = "WearListener"
    }
}
