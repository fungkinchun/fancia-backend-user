package com.fancia.backend.user.core.service

import com.fancia.backend.shared.user.core.entity.ChatChannel
import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.NotificationInternalClient
import io.getstream.chat.java.exceptions.InvalidWebhookError
import io.getstream.chat.java.models.Event
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeEmpty
import org.mockito.Mockito
import java.util.UUID

class StreamWebhookServiceTest : FunSpec({
    test("ignores webhooks when Stream is disabled") {
        val props = StreamChatProperties().apply {
            enabled = false
            apiSecret = "secret"
        }
        val notification = Mockito.mock(NotificationInternalClient::class.java)
        val service = StreamWebhookService(
            streamChatProperties = props,
            chatChannelRepository = Mockito.mock(ChatChannelRepository::class.java),
            userRepository = Mockito.mock(UserRepository::class.java),
            notificationInternalClient = notification,
        )

        service.handle("""{"type":"message.new"}""".toByteArray(), "sig")
        Mockito.verifyNoInteractions(notification)
    }

    test("rejects missing signature when Stream is enabled") {
        val props = StreamChatProperties().apply {
            enabled = true
            apiSecret = "secret"
        }
        val service = StreamWebhookService(
            streamChatProperties = props,
            chatChannelRepository = Mockito.mock(ChatChannelRepository::class.java),
            userRepository = Mockito.mock(UserRepository::class.java),
            notificationInternalClient = Mockito.mock(NotificationInternalClient::class.java),
        )

        shouldThrow<InvalidWebhookError> {
            service.handle("""{"type":"message.new"}""".toByteArray(), null)
        }
    }

    test("resolves recipients from webhook root members additionalFields") {
        val sender = UUID.randomUUID()
        val recipient = UUID.randomUUID()
        val event = Event().apply {
            setAdditionalField(
                "members",
                listOf(
                    mapOf("user_id" to sender.toString()),
                    mapOf("user_id" to recipient.toString()),
                ),
            )
        }
        val service = newService()

        service.resolveRecipientUserIds(event, sender.toString(), channel = null)
            .shouldContainExactlyInAnyOrder(recipient)
    }

    test("falls back to chat channel first/second user ids") {
        val sender = UUID.randomUUID()
        val recipient = UUID.randomUUID()
        val channel = ChatChannel().apply {
            firstUserId = sender
            secondUserId = recipient
        }
        val service = newService()

        service.resolveRecipientUserIds(Event(), sender.toString(), channel)
            .shouldContainExactlyInAnyOrder(recipient)
    }

    test("skips members with notifications_muted") {
        val sender = UUID.randomUUID()
        val muted = UUID.randomUUID()
        val event = Event().apply {
            setAdditionalField(
                "members",
                listOf(
                    mapOf("user_id" to sender.toString()),
                    mapOf(
                        "user_id" to muted.toString(),
                        "notifications_muted" to true,
                    ),
                ),
            )
        }
        val service = newService()

        service.resolveRecipientUserIds(event, sender.toString(), channel = null).shouldBeEmpty()
    }
})

private fun newService(): StreamWebhookService =
    StreamWebhookService(
        streamChatProperties = StreamChatProperties().apply {
            enabled = true
            apiSecret = "secret"
        },
        chatChannelRepository = Mockito.mock(ChatChannelRepository::class.java),
        userRepository = Mockito.mock(UserRepository::class.java),
        notificationInternalClient = Mockito.mock(NotificationInternalClient::class.java),
    )
