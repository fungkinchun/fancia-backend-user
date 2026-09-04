package com.fancia.backend.user.core.service

import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.NotificationInternalClient
import io.getstream.chat.java.exceptions.InvalidWebhookError
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import org.mockito.Mockito

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

        service.handle("""{"type":"message.new"}""", "sig")
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
            service.handle("""{"type":"message.new"}""", null)
        }
    }
})
