package com.fancia.backend.user.core.controller

import com.fancia.backend.user.core.service.StreamWebhookService
import io.getstream.chat.java.exceptions.InvalidWebhookError
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Provider webhooks")
class StreamWebhookController(
    private val streamWebhookService: StreamWebhookService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Operation(
        summary = "Stream Chat webhook",
        description = "Receives Stream Chat events (e.g. message.new). Verifies X-Signature with the Stream API secret.",
    )
    @PostMapping("/stream")
    fun stream(
        request: HttpServletRequest,
        @RequestHeader(name = "X-Signature", required = false) signature: String?,
    ): ResponseEntity<Void> {
        val rawBody = request.inputStream.readAllBytes()
        streamWebhookService.handle(rawBody, signature)
        return ResponseEntity.ok().build()
    }

    @ExceptionHandler(InvalidWebhookError::class)
    fun handleInvalidWebhook(ex: InvalidWebhookError): ResponseEntity<Void> {
        log.warn("Rejecting Stream webhook: {}", ex.message)
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
    }
}
