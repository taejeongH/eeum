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

class MyFirebaseMessagingService : FirebaseMessagingService() {

    // FCM 메시지 수신 시 호출
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // Check if message contains notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
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

        Log.d(TAG, "Handling data message - Title: $title, Body: $body, Type: $type")
        
        sendNotification(title, body, type)
    }

    private fun sendRegistrationToServer(token: String) {
        // TODO: Implement this method to send token to your app server.
        Log.d(TAG, "Send token to server: $token")
    }

    private fun sendNotification(title: String, body: String, type: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0 /* Request code */, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
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
            } else {
                val channel = NotificationChannel(
                    channelId,
                    "기본 알림",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
        }

        notificationManager.notify(if(isEmergency) 999 else 0, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "MyFirebaseMsgService"
    }
}
