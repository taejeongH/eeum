package com.example.eeum

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.example.eeum.ui.theme.EeumTheme
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await

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

        fun triggerImmediateHealthSync(context: Context, familyId: String? = null) {
            val inputData = androidx.work.Data.Builder()
            familyId?.let { inputData.putString("family_id", it) }

            val immediateRequest = androidx.work.OneTimeWorkRequestBuilder<HealthSyncWorker>()
                .setInitialDelay(2, java.util.concurrent.TimeUnit.SECONDS)
                .setInputData(inputData.build())
                .build()

            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "ImmediateHealthSync",
                androidx.work.ExistingWorkPolicy.REPLACE,
                immediateRequest
            )
            Log.i("MainActivity", "🚀 Triggered Immediate Health Sync (Family: $familyId)")
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

        // 인텐트 데이터 추출 (알림 클릭으로 실행된 경우)
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

        // Wearable PING Test
        lifecycleScope.launch {
            delay(3000) 
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val nodeClient = Wearable.getNodeClient(this@MainActivity)
                    val nodes = nodeClient.connectedNodes.await()
                    if (nodes.isNotEmpty()) {
                        val messageClient = Wearable.getMessageClient(this@MainActivity)
                        nodes.forEach { node ->
                            messageClient.sendMessage(node.id, "/emergency/start", null)
                                .addOnSuccessListener { Log.d("Wearable", "Message sent successfully") }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("Wearable", "Error sending message", e)
            }
        }

        setContent {
            EeumTheme(darkTheme = false) {
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

        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.CALL_PHONE, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions(permissions.toTypedArray(), 1001)
        
        schedulePeriodicHealthSync()
    }

    private fun schedulePeriodicHealthSync() {
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<HealthSyncWorker>(
            1, java.util.concurrent.TimeUnit.HOURS
        ).build()

        androidx.work.WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "HealthSyncWork",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
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
    fun saveSelectedFamilyId(familyId: String) {
        prefs.edit().putString("family_id", familyId).apply()
    }

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
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sendMessageToWatch("/emergency/start")
        }
    }

    @JavascriptInterface
    fun stopHeartRateMonitoring() {
        activity.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            sendMessageToWatch("/emergency/stop")
        }
    }

    private suspend fun sendMessageToWatch(path: String) {
        try {
            val nodeClient = Wearable.getNodeClient(activity)
            val nodes = nodeClient.connectedNodes.await()
            val messageClient = Wearable.getMessageClient(activity)
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, null).await()
            }
        } catch (e: Exception) {
            Log.e("BRIDGE", "Failed to send message", e)
        }
    }

    @JavascriptInterface
    fun finishApp() {
        activity.finish()
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
        webViewRef?.evaluateJavascript(
            "javascript:if(window.onNativeBackPressed){window.onNativeBackPressed();}else{history.back();}",
            null
        )
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
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewRef = this
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                    
                    // [IMPORTANT] 실시간 영상(HTTP) 로드를 위한 Mixed Content 허용
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    // [IMPORTANT] 자동 재생 허용
                    mediaPlaybackRequiresUserGesture = false
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

                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        android.app.AlertDialog.Builder(context).setTitle("알림").setMessage(message).setPositiveButton("확인") { _, _ -> result?.confirm() }.setCancelable(false).show()
                        return true
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        request?.grant(request.resources)
                    }

                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("WebViewConsole", "${consoleMessage?.message()} -- From line ${consoleMessage?.lineNumber()} of ${consoleMessage?.sourceId()}")
                        return true
                    }
                }

                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val url = request?.url?.toString() ?: return false
                        if (url.startsWith("tel:")) {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse(url))
                            context.startActivity(intent)
                            return true
                        }
                        return false
                    }

                    override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                        handler?.proceed() // 로컬 테스트용 SSL 허용
                    }

                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                        super.onReceivedError(view, request, error)
                        Log.e("WebView", "❌ Load Error: ${error?.errorCode} - ${error?.description}")
                        if (request?.isForMainFrame == true) {
                            Log.e("WebView", "❌ Main Frame failure: ${request.url}")
                        }
                    }
                }

                setBackgroundColor(android.graphics.Color.WHITE)
                loadUrl(BuildConfig.WEBVIEW_URL)
            }
        }
    )
}