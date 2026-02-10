package com.example.eeum.wear.presentation

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.*
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import kotlinx.coroutines.guava.await
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Wear OS 메인 액티비티
 *
 * 심박수 측정 UI를 표시하고 권한을 관리하며, 핸드폰 앱으로부터의 요청을 처리합니다.
 * - 심박수 실시간 표시 및 상태 업데이트
 * - 포그라운드 서비스 시작/중지 제어
 * - 화면 켜짐 유지 및 잠금 해제 처리 (긴급 상황 대응)
 */
class MainActivity : ComponentActivity() {

    private lateinit var measureClient: MeasureClient

    // 심박수 상태 관리를 위한 State
    private var heartRate by mutableStateOf(0.0)
    private var statusText by mutableStateOf("초기화 중...")

    // 권한 요청 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            registerHeartRateSensor()
        } else {
            statusText = "권한 거부됨"
            Log.e(TAG, "권한 거부됨: ${permissions.filter { !it.value }.keys}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent) 

        measureClient = HealthServices.getClient(this).measureClient

        setContent {
            // Recomposition을 명확히 트리거하기 위해 UI 구조 최적화
            WearApp(heartRate, statusText)
        }

        checkPermissionAndStart()
    }

    /**
     * 권한 확인 및 센서 등록 시작
     */
    private fun checkPermissionAndStart() {
        val permissions = mutableListOf(Manifest.permission.BODY_SENSORS)
        
        // Android 13 이상에서 알림 및 백그라운드 센서 권한 추가
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.BODY_SENSORS_BACKGROUND)
        }
        
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            registerHeartRateSensor()
        } else {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    /**
     * 심박수 센서 등록
     */
    private fun registerHeartRateSensor() {
        Log.w(TAG, "심박수 센서 등록 중...")
        statusText = "측정 중..."
        
        lifecycleScope.launch {
            try {
                val capabilities = measureClient.getCapabilitiesAsync().await()
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                    measureClient.registerMeasureCallback(
                        DataType.HEART_RATE_BPM,
                        hrCallback
                    )
                    Log.w(TAG, "✅ 심박수 콜백 등록 성공")
                } else {
                    statusText = "심박수 미지원"
                }
            } catch (e: Exception) {
                Log.e(TAG, "센서 등록 에러", e)
                statusText = "에러: ${e.message}"
            }
        }
    }

    // 심박수 데이터 수신 콜백
    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "가용성 변경: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            val latestHr = heartRateData.lastOrNull()?.value ?: 0.0
            
            if (latestHr > 0) {
                // UI 스레드에서 상태 업데이트 보장
                runOnUiThread {
                    heartRate = latestHr
                    statusText = "측정 중..."
                }
                Log.d(TAG, "심박수 업데이트: $latestHr")
                sendHeartRateToPhone(latestHr)
            }
        }
    }

    /**
     * 핸드폰으로 심박수 데이터 전송
     *
     * @param hr 측정된 심박수
     */
    private fun sendHeartRateToPhone(hr: Double) {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 편의상 전체 노드에 브로드캐스트
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@MainActivity)
                val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                
                nodes.forEach { node ->
                    val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@MainActivity)
                    val payload = hr.toString().toByteArray()
                    com.google.android.gms.tasks.Tasks.await(
                        messageClient.sendMessage(node.id, "/heart-rate", payload)
                    )
                    Log.d(TAG, "데이터 전송 성공 ($hr) -> ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "데이터 전송 실패", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterHeartRateSensor()
    }

    private fun unregisterHeartRateSensor() {
        try {
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, hrCallback)
            Log.d(TAG, "심박수 센서 해제됨")
        } catch (e: Exception) {
            Log.e(TAG, "센서 해제 에러", e)
        }
    }

    companion object {
        private const val TAG = "WearActivity"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /**
     * 인텐트 처리
     *
     * 서비스 시작/중지 요청을 처리합니다.
     */
    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        
        if (action == "start_service") {
            Log.d(TAG, "액티비티를 통해 HeartRateService 시작")
            statusText = "측정 중..."
            
            // 1. 화면 켜짐 유지 및 잠금 해제 (긴급 상황)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                
                // Keyguard 해제 요청 (패턴/핀 잠금 시 중요)
                val keyguardManager = getSystemService(android.app.KeyguardManager::class.java)
                keyguardManager?.requestDismissKeyguard(this, null)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                )
            }
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 2. 서비스 시작
            val serviceIntent = Intent(this, HeartRateService::class.java)
            startForegroundService(serviceIntent)
            
            // 3. 30초 후 자동 중지 (배터리 절약)
            lifecycleScope.launch {
                kotlinx.coroutines.delay(30000L) // 30초
                Log.d(TAG, "30초 경과, 자동 측정 중지")
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                statusText = "시간 초과: 중지됨"
                
                // 서비스 중지
                val serviceIntent = Intent(this@MainActivity, HeartRateService::class.java)
                stopService(serviceIntent)
                // 액티비티 리스너도 해제
                unregisterHeartRateSensor()
            }
        } else if (action == "stop_service") {
            Log.d(TAG, "액티비티를 통해 HeartRateService 중지")
            statusText = "중지됨"
            
            // 1. 화면 켜짐 유지 해제
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 2. 서비스 중지
            val serviceIntent = Intent(this, HeartRateService::class.java)
            stopService(serviceIntent)
        }
    }
}

/**
 * Wear OS 앱 UI
 */
@Composable
fun WearApp(hr: Double, status: String) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            Text(
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colors.primary,
                text = "심박수:\n${hr.toInt()} BPM\n\n$status"
            )
        }
    }
}

