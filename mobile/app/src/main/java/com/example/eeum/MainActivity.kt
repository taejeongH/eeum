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

// ====== MainActivity ======
class MainActivity : ComponentActivity() {

    private lateinit var healthManager: SamsungHealthManager // 브릿지 주입

    // 파일 업로드 콜백
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    // 갤러리/파일 선택 런처 (GetMultipleContents)
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        filePathCallback?.onReceiveValue(uris.toTypedArray())
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d("SHD_DEBUG", "앱이 시작되었습니다!")
        healthManager = SamsungHealthManager(this) // 매니저 초기화

        setContent {
            EeumTheme {
                // ✅ 기존 1) WebViewScreen(this, healthManager) 유지
                // ✅ 기존 2) 파일 선택 onShowFileChooser 콜백 유지
                WebViewScreen(
                    activity = this,
                    healthManager = healthManager,
                    onShowFileChooser = { callback ->
                        filePathCallback = callback
                        fileChooserLauncher.launch("image/*")
                    }
                )
            }
        }
    }
}

// ====== JS Bridge (내용 보존 + 실제로 @JavascriptInterface를 쓸 수 있게 통합) ======
class HealthJsBridge(
    private val activity: ComponentActivity,
    private val healthManager: SamsungHealthManager,
    private val webView: WebView
) {
    @JavascriptInterface
    fun fetchHeartRate() {
        activity.lifecycleScope.launch {
            val data = healthManager.getLatestHeartRate()
            val arg = data ?: "null"
            webView.post {
                webView.evaluateJavascript("window.onReceiveHealthData('$arg')", null)
            }
        }
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

                // JS 브릿지 연결
                addJavascriptInterface(
                    HealthJsBridge(activity, healthManager, this),
                    "Android"
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

                // 로컬 assets/index.html 로드
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}
