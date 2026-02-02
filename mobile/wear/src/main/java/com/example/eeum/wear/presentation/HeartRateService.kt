package com.example.eeum.wear.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.*
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.eeum.wear.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class HeartRateService : LifecycleService() {

    private lateinit var measureClient: MeasureClient
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        
        measureClient = HealthServices.getClient(this).measureClient
        createNotificationChannel()

        // Partial WakeLock to keep CPU running (optional for some sensors, but good for stability)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EeumWatch:HeartRateWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        Log.d(TAG, "Service Started")

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = createNotification()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                androidx.core.app.ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                    } else {
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST
                    }
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            vibrateStart()
            
            // Send initial ping to confirm connection
            sendHeartRateToPhone(0.0) 
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground", e)
            vibrateError()
            stopSelf()
            return START_NOT_STICKY
        }
        
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes*/)

        registerHeartRateSensor()

        return START_STICKY
    }

    private fun vibrateStart() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        // 3 short pulses
        val timing = longArrayOf(0, 100, 100, 100, 100, 100)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             vibrator.vibrate(android.os.VibrationEffect.createWaveform(timing, -1))
        } else {
             vibrator.vibrate(timing, -1)
        }
    }

    private fun vibrateError() {
         val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
         // Long error buzz
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
         } else {
             vibrator.vibrate(1000)
         }
    }

    private fun registerHeartRateSensor() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permission not granted")
            stopSelf()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "Registering HR callback...")
                val capabilities = measureClient.getCapabilitiesAsync().await()
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                    measureClient.registerMeasureCallback(
                        DataType.HEART_RATE_BPM,
                        hrCallback
                    )
                    Log.d(TAG, "HR Callback registered in Service")
                } else {
                    Log.e(TAG, "HR not supported")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
            }
        }
    }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "Availability: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            heartRateData.lastOrNull()?.let {
                val hr = it.value
                Log.d(TAG, "HR Service Received: $hr")
                sendHeartRateToPhone(hr)
            }
        }
    }

    private fun sendHeartRateToPhone(hr: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Use await() from kotlinx-coroutines-play-services
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@HeartRateService)
                val nodes = nodeClient.connectedNodes.await()
                
                if (nodes.isEmpty()) {
                    Log.w(TAG, "No connected nodes found to send data!")
                    return@launch
                }

                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@HeartRateService)
                val payload = hr.toString().toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/heart-rate", payload).await()
                    Log.d(TAG, "Sent HR $hr to ${node.displayName} (${node.id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed send HR", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service Destroyed")
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering", e)
        }
        wakeLock?.release()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Heart Rate Monitor",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, HeartRateService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Heart Rate Monitoring")
            .setContentText("Measuring your heart rate...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now) // Use a system icon or app icon
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val TAG = "HRService"
        const val CHANNEL_ID = "hr_service_channel"
        const val NOTIFICATION_ID = 101
        const val ACTION_STOP = "STOP_SERVICE"
    }
}
