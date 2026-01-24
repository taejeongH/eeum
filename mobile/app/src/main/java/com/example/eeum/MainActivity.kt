package com.example.eeum

// 최상단 import 문에 추가되어야 함
import android.webkit.JavascriptInterface
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.eeum.ui.theme.EeumTheme


class MainActivity : ComponentActivity() {
    private lateinit var healthManager: SamsungHealthManager // 브릿지 주입
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("SHD_DEBUG", "앱이 시작되었습니다!")
        healthManager = SamsungHealthManager(this) // 매니저 초기화

        setContent {
            EeumTheme {
                WebViewScreen(this, healthManager) // 연결
            }
        }
    }
}

@Composable
fun WebViewScreen(activity: ComponentActivity, healthManager: SamsungHealthManager) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                // 🔥 WebView 설정 강화 (여기가 핵심!)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    // 👇 이 설정들이 있어야 'file://' 경로에서 모듈을 불러올 수 있음
                    allowFileAccess = true
                    allowContentAccess = true
                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true
                }
                // Android 이름으로 브릿지 등록
                addJavascriptInterface(WebAppInterface(activity, this, healthManager), "Android")

                webViewClient = WebViewClient()

                // 웹뷰 로드
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}
