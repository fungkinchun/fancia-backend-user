package com.fancia.backend.user.core.service

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.NotificationChannel
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class FirebaseCloudMessagingService(
    @Autowired(required = false)
    private val firebaseMessaging: FirebaseMessaging?,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun sendMatchNotification(user: User, title: String, body: String) {
        val notifications = user.settings?.notifications ?: return
        val channel = notifications.match ?: return
        if (channel != NotificationChannel.PUSH_ONLY && channel != NotificationChannel.BOTH) return
        val token = notifications.fcmToken
        if (token.isNullOrBlank()) {
            log.debug("Skipping FCM for user {}: no fcm token", user.id)
            return
        }
        val messaging = firebaseMessaging
        if (messaging == null) {
            log.debug("Skipping FCM for user {}: Firebase is disabled", user.id)
            return
        }

        try {
            val message = Message.builder()
                .setToken(token)
                .setNotification(
                    Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build(),
                )
                .putData("type", "SMART_MATCH")
                .putData("userId", user.id.toString())
                .build()
            messaging.send(message)
            log.info("Sent smart match FCM notification to user {}", user.id)
        } catch (ex: Exception) {
            log.warn("Failed to send FCM notification to user {}", user.id, ex)
        }
    }
}
