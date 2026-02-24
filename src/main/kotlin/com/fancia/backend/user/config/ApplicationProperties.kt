package com.fancia.backend.user.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
class ApplicationProperties {
    lateinit var allowedOrigins: List<String>
    var applicationName: String? = null
    var baseUrl: String? = null
    var loginPageUrl: String? = null
    var loginSuccessUrl: String? = null
    var adminUserEmail: String? = null
    var adminUserPassword: String? = null
}
