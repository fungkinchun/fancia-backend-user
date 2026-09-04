package com.fancia.backend.user.core.service

import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
import com.fancia.backend.shared.user.core.enums.ChatChannelKind
import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.NotificationInternalClient
import io.getstream.chat.java.exceptions.InvalidWebhookError
import io.getstream.chat.java.models.App
import io.getstream.chat.java.models.Event
import io.getstream.chat.java.models.Message
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.UUID

@Service
class StreamWebhookService(
    private val streamChatProperties: StreamChatProperties,
    private val chatChannelRepository: ChatChannelRepository,
    private val userRepository: UserRepository,
    private val notificationInternalClient: NotificationInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(rawBody: String, signature: String?) {
        if (!streamChatProperties.enabled) {
            log.debug("Ignoring Stream webhook: Stream is disabled")
            return
        }
        val secret = streamChatProperties.apiSecret
        if (secret.isBlank()) {
            log.warn("Ignoring Stream webhook: api secret is not configured")
            return
        }
        if (signature.isNullOrBlank()) {
            throw InvalidWebhookError(InvalidWebhookError.SIGNATURE_MISMATCH)
        }

        val event = try {
            App.verifyAndParseWebhook(rawBody.toByteArray(StandardCharsets.UTF_8), signature, secret)
        } catch (ex: InvalidWebhookError) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Failed to parse Stream webhook", ex)
            throw InvalidWebhookError(InvalidWebhookError.INVALID_JSON, ex)
        }

        when (event.type) {
            EVENT_MESSAGE_NEW -> handleMessageNew(event)
            else -> log.debug("Ignoring Stream webhook type={}", event.type)
        }
    }

    private fun handleMessageNew(event: Event) {
        val message = event.message ?: return
        if (message.silent == true) return
        val messageType = message.type
        if (messageType != null &&
            messageType != Message.MessageType.REGULAR &&
            messageType != Message.MessageType.REPLY
        ) {
            return
        }

        val senderId = resolveSenderId(event, message) ?: return
        if (senderId == ChatService.SUPPORT_STREAM_USER_ID) return

        val senderUuid = runCatching { UUID.fromString(senderId) }.getOrNull()
        val senderName = resolveSenderName(event, message, senderUuid)

        val channelId = event.channelId
            ?: event.cid?.substringAfter(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
            ?: return

        val channel = chatChannelRepository.findByChannelIdWithMembers(channelId)
        val recipients = resolveRecipientUserIds(channelId, event, senderId, channel?.members?.mapNotNull { it.userId })
        if (recipients.isEmpty()) {
            log.debug("No recipients for Stream message on channel {}", channelId)
            return
        }

        val body = previewText(message)
        val path = deepLinkPath(channel, senderUuid)

        recipients.forEach { recipientId ->
            try {
                notificationInternalClient.sendPush(
                    SendPushNotificationRequest(
                        userId = recipientId,
                        title = senderName,
                        body = body,
                        type = TYPE_CHAT_MESSAGE,
                        path = path,
                        data = buildMap {
                            put("channelId", channelId)
                            put("channelType", event.channelType ?: CHANNEL_TYPE)
                            put("senderUserId", senderId)
                            channel?.kind?.name?.let { put("channelKind", it) }
                            channel?.eventId?.let { put("eventId", it.toString()) }
                            channel?.interestGroupId?.let { put("interestGroupId", it.toString()) }
                        },
                        preference = "messages",
                    ),
                )
            } catch (ex: Exception) {
                log.warn("Failed to send chat push to {} for channel {}", recipientId, channelId, ex)
            }
        }
    }

    private fun resolveRecipientUserIds(
        channelId: String,
        event: Event,
        senderId: String,
        dbMemberIds: List<UUID>?,
    ): List<UUID> {
        val fromDb = dbMemberIds.orEmpty()
        val fromEvent = event.channel?.members
            ?.mapNotNull { member ->
                member.userId
                    ?.takeIf { it != ChatService.SUPPORT_STREAM_USER_ID }
                    ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            }
            .orEmpty()

        return (fromDb + fromEvent)
            .distinct()
            .filter { it.toString() != senderId }
    }

    private fun resolveSenderId(event: Event, message: Message): String? =
        event.user?.id
            ?: event.userId
            ?: message.user?.id

    private fun resolveSenderName(event: Event, message: Message, senderUuid: UUID?): String {
        val fromEvent = event.user?.name?.takeIf { it.isNotBlank() }
            ?: message.user?.name?.takeIf { it.isNotBlank() }
        if (fromEvent != null) return fromEvent
        if (senderUuid != null) {
            val user = userRepository.findById(senderUuid).orElse(null)
            val name = listOfNotNull(user?.firstName, user?.lastName).joinToString(" ").trim()
            if (name.isNotBlank()) return name
        }
        return "New message"
    }

    private fun previewText(message: Message): String {
        val text = message.text?.trim().orEmpty()
        if (text.isNotBlank()) {
            return if (text.length <= 140) text else text.take(137) + "…"
        }
        if (!message.attachments.isNullOrEmpty()) {
            return "Sent an attachment"
        }
        return "Sent a message"
    }

    private fun deepLinkPath(
        channel: com.fancia.backend.shared.user.core.entity.ChatChannel?,
        senderUuid: UUID?,
    ): String =
        when (channel?.kind) {
            ChatChannelKind.DM ->
                senderUuid?.let { "/messages?userId=$it" } ?: "/messages"
            ChatChannelKind.GROUP_INQUIRY ->
                channel.interestGroupId?.let { "/messages?groupId=$it" } ?: "/messages"
            ChatChannelKind.EVENT ->
                channel.eventId?.let { "/messages?eventId=$it" } ?: "/messages"
            ChatChannelKind.SUPPORT ->
                "/messages?support=1"
            null -> "/messages"
        }

    companion object {
        private const val EVENT_MESSAGE_NEW = "message.new"
        private const val CHANNEL_TYPE = "messaging"
        const val TYPE_CHAT_MESSAGE = "CHAT_MESSAGE"
    }
}
