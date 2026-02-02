package com.example.eeum

import android.Manifest
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.eeum.ui.theme.EeumTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var healthManager: SamsungHealthManager
    private var fcmToken: String = ""
    
    @Volatile private var pendingNotificationId: String? = null
    @Volatile private var pendingNotificationType: String? = null
    @Volatile private var pendingFamilyId: String? = null
    
    val notificationEvent = MutableSharedFlow<String>()

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    companion object {
        private var instance: MainActivity? = null
        var isAppInForeground: Boolean = false
        
        fun emitNotification(notificationId: String, type: String? = "NORMAL", familyId: String? = "", title: String? = "", message: String? = "", groupName: String? = "") {
            instance?.let { activity ->
                activity.lifecycleScope.launch {
                    activity.notificationEvent.emit("$notificationId|$type|$familyId|$title|$message|$groupName")
                }
            }
        }

        fun clearNotifications(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.cancelAll()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val notiId = intent.getStringExtra("notificationId")
        val type = intent.getStringExtra("type") ?: "NORMAL"
        val familyId = intent.getStringExtra("familyId") ?: ""
        val title = intent.getStringExtra("title") ?: ""
        val message = intent.getStringExtra("body") ?: intent.getStringExtra("message") ?: ""
        val groupName = intent.getStringExtra("groupName") ?: ""

        if (notiId != null) {
            pendingNotificationId = notiId
            pendingNotificationType = type
            pendingFamilyId = familyId
            emitNotification(notiId, type, familyId, title, message, groupName)
        }
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        clearNotifications(this)
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this

        pendingNotificationId = intent.getStringExtra("notificationId")
        pendingNotificationType = intent.getStringExtra("type") ?: "NORMAL"
        pendingFamilyId = intent.getStringExtra("familyId") ?: ""
        val pendingTitle = intent.getStringExtra("title") ?: ""
        val pendingMessage = intent.getStringExtra("body") ?: intent.getStringExtra("message") ?: ""
        val pendingGroupName = intent.getStringExtra("groupName") ?: ""

        if (pendingNotificationId != null) {
             lifecycleScope.launch {
                 delay(1000)
                 if (pendingNotificationId != null) {
                     notificationEvent.emit("${pendingNotificationId!!}|${pendingNotificationType ?: "NORMAL"}|${pendingFamilyId ?: ""}|$pendingTitle|$pendingMessage|$pendingGroupName")
                 }
            }
        }

        healthManager = SamsungHealthManager(this)
        WebView.setWebContentsDebuggingEnabled(true)

        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            fcmToken = it
            Log.d("FCM", "✅ FCM TOKEN SUCCESS: $it")
        }

        // ==========================================
        // [PoC] Wearable PING Test (임시)
        // ==========================================
        // 앱 실행 시 자동으로 3초 뒤에 PING 전송 시도 (테스트용)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(3000) 
            Log.d("Wearable", "Trying to find connected nodes...")
            try {
                // IO 스레드에서 실행
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@MainActivity)
                    val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
                    if (nodes.isNotEmpty()) {
                        val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@MainActivity)
                        nodes.forEach { node ->
                            Log.d("Wearable", "Sending /emergency/start to node: ${node.displayName} (${node.id})")
                            // 비동기 전송은 await 없이 리스너만 달아도 되지만, 여기선 Tasks.await를 쓰진 않았으므로 그대로 둠.
                            // 다만 sendMessage 자체는 비동기 Task를 반환하므로 바로 리스너 부착 가능.
                            messageClient.sendMessage(node.id, "/emergency/start", null)
                                .addOnSuccessListener { Log.d("Wearable", "Message sent successfully") }
                                .addOnFailureListener { Log.e("Wearable", "Message failed", it) }
                        }
                    } else {
                        Log.d("Wearable", "No connected nodes found.")
                    }
                }
            } catch (e: Exception) {
                Log.e("Wearable", "Error sending message", e)
            }
        }
        // ==========================================

        setContent {
            EeumTheme {
                WebViewScreen(
                    activity = this,
                    healthManager = healthManager,
                    tokenProvider = { fcmToken },
                    notificationIdProvider = { 
                        val id = pendingNotificationId
                        val type = pendingNotificationType ?: "NORMAL"
                        val familyId = pendingFamilyId ?: ""
                        val title = instance?.intent?.getStringExtra("title") ?: ""
                        val message = instance?.intent?.getStringExtra("body") ?: instance?.intent?.getStringExtra("message") ?: ""
                        val groupName = instance?.intent?.getStringExtra("groupName") ?: ""
                        
                        pendingNotificationId = null
                        if (id != null) "$id|$type|$familyId|$title|$message|$groupName" else null
                    },
                    notificationEvent = notificationEvent,
                    onShowFileChooser = { callback ->
                        filePathCallback = callback
                        fileChooserLauncher.launch("image/*")
                    }
                )
            }
        }

        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 1001)
    }
}

class HealthJsBridge(
    private val activity: ComponentActivity,
    private val healthManager: SamsungHealthManager,
    private val tokenProvider: () -> String,
    private val notificationIdProvider: () -> String?,
    private val webView: WebView
) {
    private val prefs = activity.getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)

    @JavascriptInterface
    fun fetchHeartRate() {
        activity.lifecycleScope.launch {
            if (!healthManager.hasAllPermissions()) {
                healthManager.requestPermissions(activity)
                webView.post { webView.evaluateJavascript("javascript:onReceiveHealthData(null)", null) }
                return@launch
            }
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                val escapedData = data?.replace("\\", "\\\\")?.replace("'", "\\'")
                webView.evaluateJavascript("javascript:onReceiveHealthData('$escapedData')", null)
            }
        }
    }

    @JavascriptInterface
    fun fetchSteps() = fetchHeartRate() // Simplified for now as it uses the same manager

    @JavascriptInterface
    fun fetchSleep() = fetchHeartRate()

    @JavascriptInterface
    fun fetchAllHealthMetrics() {
        activity.lifecycleScope.launch {
            if (!healthManager.hasAllPermissions()) {
                healthManager.requestPermissions(activity)
                webView.post { webView.evaluateJavascript("javascript:onReceiveAllHealthData(null)", null) }
                return@launch
            }
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                val escapedData = data?.replace("\\", "\\\\")?.replace("'", "\\'")
                webView.evaluateJavascript("javascript:onReceiveAllHealthData('$escapedData')", null)
            }
        }
    }

    @JavascriptInterface
    fun getFcmToken(): String = tokenProvider()

    @JavascriptInterface
    fun consumeNotificationId(): String? = notificationIdProvider()

    @JavascriptInterface
    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
    }

    @JavascriptInterface
    fun getAccessToken(): String? = prefs.getString("access_token", null)

    @JavascriptInterface
    fun setOrientation(orientation: String) {
        activity.runOnUiThread {
            activity.requestedOrientation = if (orientation == "landscape") {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    @JavascriptInterface
    fun clearNotifications() {
        MainActivity.clearNotifications(activity)
    }

    @JavascriptInterface
    fun startHeartRateMonitoring() {
        Log.d("BRIDGE", "startHeartRateMonitoring called")
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sendMessageToWatch("/emergency/start")
        }
    }

    @JavascriptInterface
    fun stopHeartRateMonitoring() {
        Log.d("BRIDGE", "stopHeartRateMonitoring called")
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sendMessageToWatch("/emergency/stop")
        }
    }

    private suspend fun sendMessageToWatch(path: String) {
        try {
            val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(activity)
            val nodes = com.google.android.gms.tasks.Tasks.await(nodeClient.connectedNodes)
            val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(activity)
            
            nodes.forEach { node ->
                com.google.android.gms.tasks.Tasks.await(messageClient.sendMessage(node.id, path, null))
                Log.d("BRIDGE", "Sent $path to ${node.displayName}")
            }
        } catch (e: Exception) {
            Log.e("BRIDGE", "Failed to send message", e)
        }
    }
}

@Composable
fun WebViewScreen(
    activity: ComponentActivity,
    healthManager: SamsungHealthManager,
    tokenProvider: () -> String,
    notificationIdProvider: () -> String?,
    notificationEvent: kotlinx.coroutines.flow.SharedFlow<String>,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    
    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            activity.finish()
        }
    }
    
    LaunchedEffect(Unit) {
        notificationEvent.collect { eventData ->
            val parts = eventData.split("|").map { it.replace("'", "\\'").replace("\n", " ") }
            val id = parts.getOrNull(0) ?: ""
            val type = parts.getOrNull(1) ?: "NORMAL"
            val familyId = parts.getOrNull(2) ?: ""
            val title = parts.getOrNull(3) ?: ""
            val message = parts.getOrNull(4) ?: ""
            val groupName = parts.getOrNull(5) ?: ""
            
            Log.d("FCM", "Pushing to JS: $id, $type, $familyId, $title, $message, $groupName")
            webViewRef?.post {
                webViewRef?.evaluateJavascript(
                    "javascript:if(window.onNativeNotification){window.onNativeNotification('$id', '$type', '$familyId', '$title', '$message', '$groupName')}",
                    null
                )
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewRef = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                }

                addJavascriptInterface(
                    HealthJsBridge(activity, healthManager, tokenProvider, notificationIdProvider, webView = this),
                    "AndroidBridge"
                )

                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(view: WebView?, callback: ValueCallback<Array<Uri>>?, params: FileChooserParams?): Boolean {
                        if (callback == null) return false
                        onShowFileChooser(callback)
                        return true
                    }

                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        android.app.AlertDialog.Builder(context).setTitle("알림").setMessage(message).setPositiveButton("확인") { _, _ -> result?.confirm() }.setCancelable(false).show()
                        return true
                    }

                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                        android.app.AlertDialog.Builder(context).setTitle("확인").setMessage(message).setPositiveButton("확인") { _, _ -> result?.confirm() }.setNegativeButton("취소") { _, _ -> result?.cancel() }.setCancelable(false).show()
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("tel:")) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                            context.startActivity(intent)
                            return true
                        }
                        return false
                    }
                }

                // 로컬 개발 환경용
                // loadUrl("http://192.168.35.76:5173")

                // 배포 서버용
//                loadUrl("http://10.0.2.2:5173")
                loadUrl("https://i14a105.p.ssafy.io")
            }
        }
    )
}
