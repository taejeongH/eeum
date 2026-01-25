package com.example.eeum

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class WebAppInterface(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val healthManager: SamsungHealthManager
) {

    @JavascriptInterface
    fun fetchHeartRate() {
        activity.lifecycleScope.launch {
            // 데이터가 없으면 null, 있으면 JSON 문자열
            val data = healthManager.getLatestHeartRate()

            webView.post {
                // data가 null이면 JS로 null이라는 값 자체를 전달합니다.
                webView.evaluateJavascript("javascript:onReceiveHealthData($data)", null)
            }
        }
    }

//    @JavascriptInterface
//    fun fetchSteps() {
//        // 💡 Casting 없이 바로 activity.lifecycleScope를 사용합니다.
//        activity.lifecycleScope.launch {
//            try {
//                // 데이터를 가져오다가 여기서 에러가 나면 앱이 죽을 수 있습니다.
//                val data = healthManager.getTodaySteps() ?: "{\"count\": 0}"
//
//                webView.post {
//                    webView.evaluateJavascript("javascript:onReceiveStepsData($data)", null)
//                }
//            } catch (e: Exception) {
//                // 에러 발생 시 앱이 죽지 않도록 방어합니다.
//                android.util.Log.e("SHD_DEBUG", "에러 발생: ${e.message}")
//            }
//        }
//    }
}

//    // Vue에서 window.Android.fetchHeartRate()로 호출 가능
//    @JavascriptInterface
//    fun fetchHeartRate() {
//        activity.lifecycleScope.launch {
//            // 권한 확인 후 데이터 조회
//            if (healthManager.checkAndRequestPermissions(activity)) {
//                val data = healthManager.getLatestHeartRate()
//
//                // 조회된 데이터를 다시 웹뷰(JS)로 전달
//                webView.post {
//                    webView.evaluateJavascript("javascript:onReceiveHeartData($data)", null)
//                }
//            }
//        }
//    }
//    // WebAppInterface.kt 내부에 추가
//    @JavascriptInterface
//    fun fetchSteps() {
//        android.util.Log.d("SHD_DEBUG", "네이티브 함수 진입 성공!")
//        activity.lifecycleScope.launch {
//            try {
//                val data = healthManager.getTodaySteps() ?: "null"
//                android.util.Log.d("SHD_DEBUG", "삼성헬스 데이터 가져옴: $data")
//
//                webView.post {
//                    // Vue의 onReceiveStepsData 함수로 전달
//                    webView.evaluateJavascript("javascript:onReceiveStepsData($data)", null)
//                    android.util.Log.d("SHD_DEBUG", "Vue로 데이터 전송 완료")
//                }
//            } catch (e: Exception) {
//                android.util.Log.e("SHD_DEBUG", "에러 발생: ${e.message}")
//            }
//        }
//    }
//}