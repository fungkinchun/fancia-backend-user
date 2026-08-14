package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.ChatChannelResponse
import com.fancia.backend.shared.user.core.dto.ChatTokenResponse
import com.fancia.backend.shared.user.core.dto.CreateChatChannelRequest
import com.fancia.backend.user.core.service.ChatService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/chat")
@Tag(name = "Chat", description = "Stream Chat token and channel provisioning")
@SecurityRequirement(name = "bearerAuth")
class ChatController(
    private val chatService: ChatService,
) {
    @GetMapping("/token")
    @Operation(summary = "Get Stream Chat user token for the current user")
    fun token(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ChatTokenResponse> =
        ResponseEntity.ok(chatService.createToken(jwt))

    @PostMapping("/channels")
    @Operation(
        summary = "Get or create a 1:1 DM channel",
        description = "Allowed when Smart Match connects the two users (either or both liked, and the caller has not passed).",
    )
    fun createChannel(
        @RequestBody @Valid request: CreateChatChannelRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ChatChannelResponse> =
        ResponseEntity.ok(chatService.getOrCreateChannel(jwt, request.otherUserId))
}
