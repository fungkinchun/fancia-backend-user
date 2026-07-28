package com.fancia.backend.user.config

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.io.ByteArrayInputStream

@Configuration
@EnableConfigurationProperties(FirebaseProperties::class)
class FirebaseConfig(
    private val firebaseProperties: FirebaseProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    @ConditionalOnProperty(prefix = "app.firebase", name = ["enabled"], havingValue = "true")
    fun firebaseMessaging(): FirebaseMessaging {
        if (FirebaseApp.getApps().isEmpty()) {
            val credentialsJson = firebaseProperties.credentialsJson
                ?: error("app.firebase.credentials-json is required when Firebase is enabled")
            val options = FirebaseOptions.builder()
                .setCredentials(
                    GoogleCredentials.fromStream(ByteArrayInputStream(credentialsJson.toByteArray())),
                )
                .build()
            FirebaseApp.initializeApp(options)
            log.info("Firebase app initialized")
        }
        return FirebaseMessaging.getInstance()
    }
}
