package com.fancia.backend.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app.stream")
class StreamChatProperties {
    var enabled: Boolean = false
    var apiKey: String = ""
    var apiSecret: String = ""
}
