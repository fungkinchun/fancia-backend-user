package com.fancia.backend.user.core.service

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.NotificationChannel
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.Message
import com.google.firebase.messaging.Notification
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

@Service
class FirebaseCloudMessagingService(
    @Autowired(required = false)
    private val firebaseMessaging: FirebaseMessaging?,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun sendSmartMatchLikeNotification(recipient: User, actorName: String, actorUserId: UUID) {
        send(
            recipient = recipient,
            title = "New like",
            body = "$actorName liked your profile",
            type = TYPE_LIKE,
            actorUserId = actorUserId,
        )
    }

    fun sendSmartMatchMutualNotification(recipient: User, actorName: String, actorUserId: UUID) {
        send(
            recipient = recipient,
            title = "It's a match!",
            body = "You and $actorName liked each other. Say hello!",
            type = TYPE_MUTUAL,
            actorUserId = actorUserId,
        )
    }

    private fun send(
        recipient: User,
        title: String,
        body: String,
        type: String,
        actorUserId: UUID,
    ) {
        val notifications = recipient.settings?.notifications ?: return
        val channel = notifications.match ?: return
        if (channel != NotificationChannel.PUSH_ONLY && channel != NotificationChannel.BOTH) return
        val token = notifications.fcmToken
        if (token.isNullOrBlank()) {
            log.debug("Skipping FCM for user {}: no fcm token", recipient.id)
            return
        }
        val messaging = firebaseMessaging
        if (messaging == null) {
            log.debug("Skipping FCM for user {}: Firebase is disabled", recipient.id)
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
                .putData("type", type)
                .putData("userId", recipient.id.toString())
                .putData("actorUserId", actorUserId.toString())
                .putData("focus", "matched")
                .putData("path", "/smart-match?focus=matched&userId=$actorUserId")
                .build()
            messaging.send(message)
            log.info("Sent {} FCM notification to user {}", type, recipient.id)
        } catch (ex: Exception) {
            log.warn("Failed to send {} FCM notification to user {}", type, recipient.id, ex)
        }
    }

    companion object {
        const val TYPE_LIKE = "SMART_MATCH_LIKE"
        const val TYPE_MUTUAL = "SMART_MATCH_MUTUAL"
    }
}
