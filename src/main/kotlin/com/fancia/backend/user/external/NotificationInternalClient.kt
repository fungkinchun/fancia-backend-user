package com.fancia.backend.user.external

import com.fancia.backend.shared.notification.core.dto.SendPasswordResetEmailRequest
import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
import com.fancia.backend.shared.notification.core.dto.SendWelcomeEmailRequest
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "notification-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface NotificationInternalClient {
    @PostMapping("/push")
    fun sendPush(@RequestBody request: SendPushNotificationRequest)

    @PostMapping("/emails/welcome")
    fun sendWelcomeEmail(@RequestBody request: SendWelcomeEmailRequest)

    @PostMapping("/emails/password-reset")
    fun sendPasswordResetEmail(@RequestBody request: SendPasswordResetEmailRequest)
}
