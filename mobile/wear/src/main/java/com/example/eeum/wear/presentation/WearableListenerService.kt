package com.example.eeum.wear.presentation

import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

class WearableListenerService : WearableListenerService() {

    override fun onMessageReceived(messageEvent: MessageEvent) {
        Log.d(TAG, "onMessageReceived: ${messageEvent.path}")

        if (messageEvent.path == "/emergency/start") {
            Log.d(TAG, "Request received: Start Heart Rate Monitoring (Service)")
            
            val serviceIntent = Intent(this, HeartRateService::class.java)
            startForegroundService(serviceIntent)
        } else if (messageEvent.path == "/emergency/stop") {
             Log.d(TAG, "Request received: Stop Heart Rate Monitoring")
             val serviceIntent = Intent(this, HeartRateService::class.java)
             stopService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "WearableListener"
    }
}
