package com.example.eeum

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // FCM 메시지 수신 시 호출
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.w(TAG, "🚨 From: ${remoteMessage.from}")

        // 메시지에 데이터 페이로드가 있는지 확인
        if (remoteMessage.data.isNotEmpty()) {
            Log.w(TAG, "🚨 Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        } else {
            Log.w(TAG, "🚨 Message data payload is EMPTY")
        }

        // 메시지에 알림 페이로드가 있는지 확인
        remoteMessage.notification?.let {
            Log.w(TAG, "🚨 Message Notification Body: ${it.body}")
            sendNotification(it.title ?: "알림", it.body ?: "", "NORMAL")
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        sendRegistrationToServer(token)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val title = data["title"] ?: "알림"
        val body = data["body"] ?: "새로운 소식이 있습니다."
        val type = data["type"] ?: "NORMAL"
        val route = data["route"]

        Log.d(TAG, "Handling data message - Title: $title, Body: $body, Type: $type, Route: $route")
        
        // 1. 측정 명령 수신 시 처리
        if (type == "CMD_MEASURE_HR") {
            Log.d(TAG, "⚡ 심박수 측정 명령 수신: 워치로 신호 전송")
            
            // Save state for ListenerService
            val familyId = data["familyId"]
            val eventId = data["eventId"] // Fall Event ID (relatedId)
            
            if (familyId != null) {
                val prefs = getSharedPreferences("health_prefs", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.putString("pending_measurement_family_id", familyId)
                if (eventId != null) {
                    editor.putString("pending_measurement_related_id", eventId)
                }
                editor.apply()
                Log.d(TAG, "Saved pending familyId: $familyId, relatedId: $eventId")
            }
            
            triggerMeasurement(data)
            return
        }

        // 2. 건강 데이터 동기화 명령 수신 시 처리
        if (type == "CMD_SYNC_HEALTH") {
            Log.d(TAG, "⚡ 건강 데이터 동기화 명령 수수: 즉시 워커 실행")
            val familyId = data["familyId"]
            MainActivity.triggerImmediateHealthSync(applicationContext, familyId)
            return
        }

        val notificationId = data["notificationId"]
        val familyId = data["familyId"]
        val groupName = data["groupName"] ?: ""
        
        // 포그라운드 상태에서 수신 시 즉시 WebView에 전달
        if (notificationId != null) {
            MainActivity.emitNotification(notificationId, type, familyId, title, body, groupName)
        }
        
        // 앱이 포그라운드 상태가 아닐 때만 시스템 알림 표시
        if (!MainActivity.isAppInForeground && !type.startsWith("CMD_")) {
            sendNotification(title, body, type, notificationId, route, familyId, groupName)
        } else {
            Log.d(TAG, "Notification skipped - Foreground: ${MainActivity.isAppInForeground}, Type: $type")
        }
    }

    // 워치로 측정 시작 신호 전송
    private fun triggerMeasurement(data: Map<String, String>) {
        Log.d(TAG, "Attempting to trigger Watch Measurement via Wearable API...")
        
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val context = applicationContext
                val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(context)
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(context)
                
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isNotEmpty()) {
                    nodes.forEach { node ->
                        // 워치 앱이 듣고 있는 정확한 Path
                        val path = "/emergency/start"
                        Log.d(TAG, "Sending trigger to $path on node: ${node.displayName}")
                        messageClient.sendMessage(node.id, path, "START".toByteArray()).await()
                        
                        Log.d(TAG, "Trigger message sent to ${node.displayName}")
                    }
                } else {
                    Log.e(TAG, "No connected watch found via Wearable API")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering measurement on Watch", e)
            }
        }
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: 토큰을 앱 서버로 전송하는 로직 구현 필요
        Log.d(TAG, "Send token to server: $token")
    }

    private fun sendNotification(title: String, body: String, type: String, notificationId: String? = null, route: String? = null, familyId: String? = null, groupName: String? = null) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        
        if (notificationId != null) {
            intent.putExtra("notificationId", notificationId)
        }
        intent.putExtra("type", type)
        if (route != null) {
            intent.putExtra("route", route)
        }
        if (familyId != null) {
            intent.putExtra("familyId", familyId)
        }
        if (groupName != null) {
            intent.putExtra("groupName", groupName)
        }
        intent.putExtra("title", title)
        intent.putExtra("body", body)
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isEmergency = type == "EMERGENCY"
        
        // 채널 및 우선순위 설정
        val channelId = if (isEmergency) {
            getString(R.string.emergency_notification_channel_id)
        } else {
            getString(R.string.default_notification_channel_id)
        }

        val defaultSoundUri = if (isEmergency) {
             // 긴급 시 알람 소리로 설정 (사용자 설정에 따라 다를 수 있음)
             RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
             RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (isEmergency) "🚨 $title" else title) // 긴급 시 이모지 추가
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            
        if (isEmergency) {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_MAX) // 최대 우선순위
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVibrate(longArrayOf(0, 1000, 500, 1000)) // 긴급 진동 패턴
                .setColor(0xFFFF0000.toInt()) // 빨간색 틴트
        } else {
            notificationBuilder
                .setPriority(NotificationCompat.PRIORITY_HIGH)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 안드로이드 오레오(API 26) 이상에서는 채널이 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 채널 생성 (이미 존재하면 update됨)
            if (isEmergency) {
                val channel = NotificationChannel(
                    channelId,
                    "긴급 알림", // 사용자에게 보이는 채널 이름
                    NotificationManager.IMPORTANCE_MAX // 긴급은 HIGH/MAX
                ).apply {
                    description = "낙상 감지 등 긴급 상황 알림입니다."
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 1000, 500, 1000)
                    // 사운드 설정 명시
                    val audioAttributes = android.media.AudioAttributes.Builder()
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                    setSound(defaultSoundUri, audioAttributes)
                }
                notificationManager.createNotificationChannel(channel)
                Log.w(TAG, "🚨 Emergency Channel Created/Updated")
            } else {
                val channel = NotificationChannel(
                    channelId,
                    "기본 알림",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        Log.w(TAG, "🚨 Calling notificationManager.notify with ID: ${if(isEmergency) 999 else 0}")
        notificationManager.notify(if(isEmergency) 999 else 0, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
