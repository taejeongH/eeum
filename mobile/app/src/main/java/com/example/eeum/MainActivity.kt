package com.example.eeum

// 최상단 import 문에 추가되어야 함
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.app.Dialog
import android.net.Uri
import android.os.Bundle
import android.os.Message
import android.view.ViewGroup

// (통합을 위해 필요한 import들)
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.viewinterop.AndroidView
import com.example.eeum.ui.theme.EeumTheme

//Firebase 사용을 위해 필요한 import들
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging

// ====== MainActivity ======
class MainActivity : ComponentActivity() {

    private lateinit var healthManager: SamsungHealthManager // 브릿지 주입
    // FCM 토큰 저장 변수
    private var fcmToken: String = ""
    // 알림 ID 저장 변수
    private var pendingNotificationId: String? = null

    // 파일 업로드 콜백
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 갤러리/파일 선택 런처 (GetMultipleContents)
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        val notiId = intent?.getStringExtra("notificationId")
        if (notiId != null) {
            pendingNotificationId = notiId
            Log.d("FCM", "onNewIntent: Received Notification ID: $notiId")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        pendingNotificationId = intent.getStringExtra("notificationId")
        Log.d("FCM", "onCreate: Pending Notification ID: $pendingNotificationId")

        Log.d("SHD_DEBUG", "앱이 시작되었습니다!")
        healthManager = SamsungHealthManager(this) // 매니저 초기화

        //webvie 디버깅 활성화
        WebView.setWebContentsDebuggingEnabled(true)

        //fcm 로그 및 저장
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener {
                fcmToken = it
                Log.d("FCM", "TOKEN=$it")
            }

        setContent {
            EeumTheme {
                // ✅ 기존 1) WebViewScreen(this, healthManager) 유지
                // ✅ 기존 2) 파일 선택 onShowFileChooser 콜백 유지
                WebViewScreen(
                    activity = this,
                    healthManager = healthManager,
                    tokenProvider = { fcmToken }, // 토큰 제공 람다 전달
                    notificationIdProvider = { 
                        val id = pendingNotificationId
                        pendingNotificationId = null // Consume it
                        id
                    },
                    onShowFileChooser = { callback ->
                        filePathCallback = callback
                        fileChooserLauncher.launch("image/*")
                    }
                )
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001
            )
        }
    }
}

// ====== JS Bridge (내용 보존 + 실제로 @JavascriptInterface를 쓸 수 있게 통합) ======
class HealthJsBridge(
    private val activity: ComponentActivity,
    private val healthManager: SamsungHealthManager,
    private val tokenProvider: () -> String, // 토큰 제공자 추가
    private val notificationIdProvider: () -> String? // 알림 ID 제공자 추가
) {
    // SharedPreferences 초기화
    private val prefs = activity.getSharedPreferences("auth_prefs", android.content.Context.MODE_PRIVATE)

    @JavascriptInterface
    fun fetchUser() {
        // (요청에 있던 lifecycleScope + launch 패턴을 살려둠)
        activity.lifecycleScope.launch {
            // 예: healthManager 쪽 로직 호출 자리 (프로젝트 구현에 맞게 연결)
            // healthManager.fetchUser()
        }
    }

    @JavascriptInterface
    fun getFcmToken(): String {
        return tokenProvider()
    }

    @JavascriptInterface
    fun consumeNotificationId(): String? {
        val id = notificationIdProvider()
        Log.d("BRIDGE", "Consumed Notification ID: $id")
        return id
    }

    // 네이티브에 토큰 저장
    @JavascriptInterface
    fun saveAccessToken(token: String) {
        prefs.edit().putString("access_token", token).apply()
        Log.d("BRIDGE", "Access Token Saved Native: $token")
    }

    // 네이티브에서 토큰 가져오기
    @JavascriptInterface
    fun getAccessToken(): String? {
        val token = prefs.getString("access_token", null)
        Log.d("BRIDGE", "Access Token Retrieved Native: $token")
        return token
    }
}

// ====== WebViewScreen ======
// 충돌났던 두 시그니처를 하나로 합침:
// 1) WebViewScreen(activity: ComponentActivity, healthManager: SamsungHealthManager)
// 2) WebViewScreen(onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit)
@Composable
fun WebViewScreen(
    activity: ComponentActivity,
    healthManager: SamsungHealthManager,
    tokenProvider: () -> String,
    notificationIdProvider: () -> String?,
    onShowFileChooser: (ValueCallback<Array<Uri>>) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {

                // WebView 설정 블록(주어진 내용 모두 보존)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true

                    allowFileAccess = true
                    // 👇 이 설정들이 있어야 'file://' 경로에서 모듈을 불러올 수 있음
                    loadWithOverviewMode = true
                    useWideViewPort = true

                    // (원문에 중복으로 등장했으니 의미는 같지만 "내용 보존" 차원에서 그대로 둠)
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    // 참고로 보통은 아래도 함께 필요할 때가 많음(프로젝트 상황 따라):
                    // allowUniversalAccessFromFileURLs = true
                }

                // JS 브릿지 연결 (JavascriptInterface import/의도 보존)
                addJavascriptInterface(
                    HealthJsBridge(activity, healthManager, tokenProvider, notificationIdProvider),
                    "AndroidBridge"
                )

                // 파일 선택 처리(WebChromeClient)
                webChromeClient = object : WebChromeClient() {
                    override fun onShowFileChooser(
                        webView: WebView?,
                        filePathCallback: ValueCallback<Array<Uri>>?,
                        fileChooserParams: FileChooserParams?
                    ): Boolean {
                        if (filePathCallback == null) return false
                        onShowFileChooser(filePathCallback)
                        return true
                    }
                }

                // 링크 이동이 외부 브라우저(크롬)로 튀지 않도록 WebViewClient 설정
                webViewClient = android.webkit.WebViewClient()

                // 서버 인증(로그인)을 위해 쿠키 허용 (서드파티 쿠키 포함)
                android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                   android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                }


                // 로컬 개발 환경용
                loadUrl("http://10.0.2.2:5173")

                // 배포 서버용
                // loadUrl("https://i14a105.p.ssafy.io")
            }
        }
    )
}
