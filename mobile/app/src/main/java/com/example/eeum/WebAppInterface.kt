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
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                webView.evaluateJavascript("javascript:onReceiveHealthData($data)", null)
            }
        }
    }

    @JavascriptInterface
    fun fetchSteps() {
        activity.lifecycleScope.launch {
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                webView.evaluateJavascript("javascript:onReceiveSteps($data)", null)
            }
        }
    }

    @JavascriptInterface
    fun fetchSleep() {
        activity.lifecycleScope.launch {
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                webView.evaluateJavascript("javascript:onReceiveSleep($data)", null)
            }
        }
    }

    @JavascriptInterface
    fun fetchAllHealthMetrics() {
        activity.lifecycleScope.launch {
            val data = healthManager.getAllHealthMetrics()
            webView.post {
                webView.evaluateJavascript("javascript:onReceiveAllHealthData($data)", null)
            }
        }
    }
}