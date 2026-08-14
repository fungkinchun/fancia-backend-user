package com.fancia.backend.user.config

import io.getstream.chat.java.services.framework.DefaultClient
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import java.util.*

@Configuration
@ConditionalOnProperty(prefix = "app.stream", name = ["enabled"], havingValue = "true")
class StreamChatConfiguration(
    private val streamChatProperties: StreamChatProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun initStreamClient() {
        require(streamChatProperties.apiKey.isNotBlank()) { "app.stream.api-key is required when Stream is enabled" }
        require(streamChatProperties.apiSecret.isNotBlank()) { "app.stream.api-secret is required when Stream is enabled" }
        val properties = Properties().apply {
            put(DefaultClient.API_KEY_PROP_NAME, streamChatProperties.apiKey)
            put(DefaultClient.API_SECRET_PROP_NAME, streamChatProperties.apiSecret)
        }
        DefaultClient.setInstance(DefaultClient(properties))
        log.info("Stream Chat client initialized")
    }
}
