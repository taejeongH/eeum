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
import kotlinx.coroutines.guava.await // Guava ListenbleFuture await
import androidx.lifecycle.lifecycleScope // lifecycleScope import
import kotlinx.coroutines.launch // launch import


class MainActivity : ComponentActivity() {

    private lateinit var measureClient: MeasureClient

    // HR 상태 관리를 위한 State
    private var heartRate by mutableStateOf(0.0)
    private var statusText by mutableStateOf("Initializing...")

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            registerHeartRateSensor()
        } else {
            statusText = "Permission Denied"
            Log.e(TAG, "Permissions denied: ${permissions.filter { !it.value }.keys}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        handleIntent(intent) // Check intent on Create

        // Health Services Client 초기화
        measureClient = HealthServices.getClient(this).measureClient

        setContent {
            WearApp(heartRate, statusText)
        }

        checkPermissionAndStart()
    }

    private fun checkPermissionAndStart() {
        val permissions = mutableListOf(Manifest.permission.BODY_SENSORS)
        
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

    private fun registerHeartRateSensor() {
        Log.d(TAG, "Registering Heart Rate Sensor...")
        statusText = "Measuring..."
        
        lifecycleScope.launchWhenStarted {
            try {
                // 센서 지원 여부 확인 (Capabilities)
                val capabilities = measureClient.getCapabilitiesAsync().await()
                if (DataType.HEART_RATE_BPM in capabilities.supportedDataTypesMeasure) {
                    measureClient.registerMeasureCallback(
                        DataType.HEART_RATE_BPM,
                        hrCallback
                    )
                    Log.d(TAG, "HR Callback registered")
                } else {
                    statusText = "HR Not Supported"
                    Log.d(TAG, "Heart Rate not supported on this device")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error registering sensor", e)
                statusText = "Error: ${e.message}"
            }
        }
    }

    private val hrCallback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            Log.d(TAG, "Availability changed: $availability")
        }

        override fun onDataReceived(data: DataPointContainer) {
            val heartRateData = data.getData(DataType.HEART_RATE_BPM)
            heartRateData.lastOrNull()?.let {
                heartRate = it.value
                Log.d(TAG, "HR Received: $heartRate")
                sendHeartRateToPhone(heartRate)
            }
        }
    }

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
                    Log.d(TAG, "Sent HR $hr to ${node.displayName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send HR to phone", e)
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
            Log.d(TAG, "HR Sensor unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering sensor", e)
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

    private fun handleIntent(intent: Intent) {
        val action = intent.getStringExtra("action")
        
        if (action == "start_service") {
            Log.d(TAG, "Starting HeartRateService via Activity")
            statusText = "Measuring..."
            
            // 1. Force Screen ON and Show over Lock Screen
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
                
                // Request Keyguard Dismissal (Important for pattern/PIN locks)
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
            
            // 2. Start Service
            val serviceIntent = Intent(this, HeartRateService::class.java)
            startForegroundService(serviceIntent)
            
            // 3. Set 1 Hour Timeout
            lifecycleScope.launch {
                kotlinx.coroutines.delay(60 * 60 * 1000L) // 1 Hour
                Log.d(TAG, "1 Hour passed, releasing screen lock")
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                statusText = "Timeout: Screen releasing"
            }
        } else if (action == "stop_service") {
            Log.d(TAG, "Stopping HeartRateService via Activity")
            statusText = "Stopped"
            
            // 1. Release Screen Lock (Returns to normal timeout)
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            
            // 2. Stop Service
            val serviceIntent = Intent(this, HeartRateService::class.java)
            stopService(serviceIntent)
        }
    }

    // Coroutine Scope Helper (lifecycleScope는 Activity 멤버이므로 직접 호출 가능하지만,
    // import가 안 되어 있을 수 있어 명시적으로 추가하지 않음 - Activity 멤버 사용)
}

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
                text = "Heart Rate:\n${hr.toInt()} BPM\n\n$status"
            )
        }
    }
}
