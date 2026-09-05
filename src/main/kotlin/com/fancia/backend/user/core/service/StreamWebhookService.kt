package com.fancia.backend.user.core.service

import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
import com.fancia.backend.shared.user.core.entity.ChatChannel
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
import java.util.UUID

@Service
class StreamWebhookService(
    private val streamChatProperties: StreamChatProperties,
    private val chatChannelRepository: ChatChannelRepository,
    private val userRepository: UserRepository,
    private val notificationInternalClient: NotificationInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun handle(rawBody: ByteArray, signature: String?) {
        if (!streamChatProperties.enabled) {
            log.warn("Ignoring Stream webhook: Stream is disabled (STREAM_ENABLED)")
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
            App.verifyAndParseWebhook(rawBody, signature, secret)
        } catch (ex: InvalidWebhookError) {
            log.warn(
                "Stream webhook verification failed ({} bytes, gzipMagic={}): {}",
                rawBody.size,
                rawBody.size >= 2 && rawBody[0] == 0x1f.toByte() && rawBody[1] == 0x8b.toByte(),
                ex.message,
            )
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
        val message = event.message ?: run {
            log.warn("Ignoring Stream message.new: missing message payload")
            return
        }
        if (message.silent == true) return
        val messageType = message.type
        if (messageType != null &&
            messageType != Message.MessageType.REGULAR &&
            messageType != Message.MessageType.REPLY
        ) {
            return
        }

        val senderId = resolveSenderId(event, message) ?: run {
            log.warn("Ignoring Stream message.new: could not resolve sender")
            return
        }
        if (senderId == ChatService.SUPPORT_STREAM_USER_ID) return

        val senderUuid = runCatching { UUID.fromString(senderId) }.getOrNull()
        val senderName = resolveSenderName(event, message, senderUuid)

        val channelId = event.channelId
            ?: event.cid?.substringAfter(':', missingDelimiterValue = "")?.takeIf { it.isNotBlank() }
            ?: run {
                log.warn("Ignoring Stream message.new: missing channel id")
                return
            }

        val channel = chatChannelRepository.findByChannelIdWithMembers(channelId)
        val recipients = resolveRecipientUserIds(event, senderId, channel)
        if (recipients.isEmpty()) {
            log.warn(
                "No recipients for Stream message on channel {} (dbChannel={}, eventMembers={})",
                channelId,
                channel != null,
                memberIdsFromEvent(event).size,
            )
            return
        }

        val body = previewText(message)
        val path = deepLinkPath(channel, senderUuid)

        log.info(
            "Sending CHAT_MESSAGE push to {} recipient(s) for channel {} from {}",
            recipients.size,
            channelId,
            senderId,
        )

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

    internal fun resolveRecipientUserIds(
        event: Event,
        senderId: String,
        channel: ChatChannel?,
    ): List<UUID> {
        val muted = mutedMemberIdsFromEvent(event)
        val candidates = linkedSetOf<UUID>()

        channel?.members?.mapNotNull { it.userId }?.forEach { candidates.add(it) }
        channel?.firstUserId?.let { candidates.add(it) }
        channel?.secondUserId?.let { candidates.add(it) }
        channel?.initiatorUserId?.let { candidates.add(it) }

        memberIdsFromEvent(event).forEach { candidates.add(it) }

        return candidates
            .filter { it.toString() != senderId }
            .filter { it.toString() !in muted }
            .toList()
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
        channel: ChatChannel?,
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

        internal fun memberIdsFromEvent(event: Event): List<UUID> {
            val ids = linkedSetOf<UUID>()
            event.channel?.members.orEmpty().forEach { member ->
                parseMemberUserId(member.userId ?: member.user?.id)?.let { ids.add(it) }
            }
            for (raw in rawMembers(event)) {
                parseMemberUserId(memberUserId(raw))?.let { ids.add(it) }
            }
            return ids.toList()
        }

        internal fun mutedMemberIdsFromEvent(event: Event): Set<String> =
            rawMembers(event)
                .filter { isMuted(it) }
                .mapNotNull { memberUserId(it) }
                .filter { it != ChatService.SUPPORT_STREAM_USER_ID }
                .toSet()

        private fun rawMembers(event: Event): List<Any> {
            val value = event.additionalFields?.get("members") ?: return emptyList()
            return when (value) {
                is List<*> -> value.filterNotNull()
                else -> emptyList()
            }
        }

        private fun memberUserId(raw: Any): String? =
            when (raw) {
                is Map<*, *> -> {
                    val userId = raw["user_id"] ?: raw["userId"]
                    when (userId) {
                        is String -> userId
                        else -> {
                            val user = raw["user"]
                            if (user is Map<*, *>) {
                                (user["id"] as? String)
                            } else {
                                null
                            }
                        }
                    }
                }
                else -> null
            }?.takeIf { it.isNotBlank() && it != ChatService.SUPPORT_STREAM_USER_ID }

        private fun isMuted(raw: Any): Boolean {
            if (raw !is Map<*, *>) return false
            val muted = raw["notifications_muted"] ?: raw["notificationsMuted"]
            return muted == true || muted == "true"
        }

        private fun parseMemberUserId(raw: String?): UUID? {
            val id = raw?.takeIf { it.isNotBlank() && it != ChatService.SUPPORT_STREAM_USER_ID } ?: return null
            return runCatching { UUID.fromString(id) }.getOrNull()
        }
    }
}
