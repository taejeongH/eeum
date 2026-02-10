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

/**
 * 심박수 측정 백그라운드 서비스
 *
 * Wear OS에서 백그라운드로 심박수를 측정하고, 측정된 데이터를 핸드폰 앱으로 전송합니다.
 * - Foreground Service로 실행되어 지속적인 측정을 보장합니다.
 * - WakeLock을 사용하여 CPU가 잠들지 않도록 합니다.
 */
class HeartRateService : LifecycleService() {

    private lateinit var measureClient: MeasureClient
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "서비스 생성됨")
        
        measureClient = HealthServices.getClient(this).measureClient
        createNotificationChannel()

        // 부분 WakeLock을 사용하여 CPU 상태 유지 (센서 안정성 확보)
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EeumWatch:HeartRateWakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        Log.d(TAG, "서비스 시작됨")

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = createNotification()
            // Android 14 (UPSIDE_DOWN_CAKE) 이상 대응
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
            
            // 연결 확인을 위한 초기 데이터 전송
            sendHeartRateToPhone(0.0) 
            
        } catch (e: Exception) {
            Log.e(TAG, "포그라운드 서비스 시작 실패", e)
            vibrateError()
            stopSelf()
            return START_NOT_STICKY
        }
        
        // 10분간 WakeLock 유지
        wakeLock?.acquire(10 * 60 * 1000L)

        registerHeartRateSensor()

        return START_STICKY
    }

    /**
     * 시작 진동 피드백 (짧은 펄스 3회)
     */
    private fun vibrateStart() {
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
        val timing = longArrayOf(0, 100, 100, 100, 100, 100)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             vibrator.vibrate(android.os.VibrationEffect.createWaveform(timing, -1))
        } else {
             vibrator.vibrate(timing, -1)
        }
    }

    /**
     * 에러 진동 피드백 (긴 진동 1회)
     */
    private fun vibrateError() {
         val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
         if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             vibrator.vibrate(android.os.VibrationEffect.createOneShot(1000, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
         } else {
             vibrator.vibrate(1000)
         }
    }

    /**
     * 심박수 센서 등록
     */
    private fun registerHeartRateSensor() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "권한 없음")
            stopSelf()
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "심박수 콜백 등록 중...")
                val capabilities = measureClient.getCapabilitiesAsync().await()
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                    measureClient.registerMeasureCallback(
                        DataType.HEART_RATE_BPM,
                        hrCallback
                    )
                    Log.d(TAG, "심박수 콜백 등록 완료")
                } else {
                    Log.e(TAG, "심박수 측정 미지원 기기")
                }
            } catch (e: Exception) {
                Log.e(TAG, "센서 등록 에러: ${e.message}", e)
            }
        }
    }

    // 심박수 측정 콜백
    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "센서 가용성 변경: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            heartRateData.lastOrNull()?.let {
                val hr = it.value
                Log.d(TAG, "심박수 수신: $hr")
                sendHeartRateToPhone(hr)
            }
        }
    }

    /**
     * 핸드폰으로 심박수 데이터 전송
     *
     * @param hr 측정된 심박수
     */
    private fun sendHeartRateToPhone(hr: Double) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@HeartRateService)
                val nodes = nodeClient.connectedNodes.await()
                
                if (nodes.isEmpty()) {
                    Log.w(TAG, "연결된 기기가 없어 데이터를 전송할 수 없습니다.")
                    return@launch
                }

                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@HeartRateService)
                val payload = hr.toString().toByteArray()

                for (node in nodes) {
                    messageClient.sendMessage(node.id, "/heart-rate", payload).await()
                    Log.d(TAG, "데이터 전송 성공 ($hr) -> ${node.displayName} (${node.id})")
                }
            } catch (e: Exception) {
                Log.e(TAG, "데이터 전송 실패", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "서비스 종료됨")
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
        } catch (e: Exception) {
            Log.e(TAG, "콜백 해제 중 에러", e)
        }
        wakeLock?.release()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "심박수 모니터링",
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
            .setContentTitle("심박수 측정 중")
            .setContentText("백그라운드에서 심박수를 측정하고 있습니다.")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "중지", pendingStopIntent)
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

