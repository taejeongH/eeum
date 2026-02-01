package com.example.eeum.wear.presentation

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")

        if (messageEvent.path == "/emergency/start") {
            Log.d(TAG, "Request received: Start Heart Rate Monitoring")
            // TODO: Start Foreground Service to measure HR
            
            // For PoC: Start Activity as visual confirmation
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("action", "start")
            }
            startActivity(intent)
        }
    }

    companion object {
        private const val TAG = "WearableListener"
    }
}
