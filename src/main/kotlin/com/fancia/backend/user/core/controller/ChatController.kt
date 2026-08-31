package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.ChatChannelResponse
import com.fancia.backend.shared.user.core.dto.ChatTokenResponse
import com.fancia.backend.shared.user.core.dto.CreateChatChannelRequest
import com.fancia.backend.shared.user.core.dto.CreateGroupInquiryRequest
import com.fancia.backend.shared.user.core.exception.ChatChannelException
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
        summary = "Get or create a Stream messaging channel",
        description = "Pass otherUserId for a 1:1 DM (friends / Smart Match / public profile), " +
            "or eventId for the shared channel of an event. Exactly one of those fields is required.",
    )
    fun createChannel(
        @RequestBody @Valid request: CreateChatChannelRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ChatChannelResponse> {
        val otherUserId = request.otherUserId
        val eventId = request.eventId
        return when {
            otherUserId != null && eventId == null ->
                ResponseEntity.ok(chatService.getOrCreateChannel(jwt, otherUserId))
            eventId != null && otherUserId == null ->
                ResponseEntity.ok(chatService.getOrCreateEventChannel(jwt, eventId))
            else -> throw ChatChannelException(
                message = "Provide either otherUserId or eventId (exactly one)",
            )
        }
    }

    @PostMapping("/group-inquiries")
    @Operation(
        summary = "Get or create a group inquiry channel",
        description = "Creates a messaging channel with the initiator and all ACCEPTED ADMIN members of the interest group.",
    )
    fun createGroupInquiry(
        @RequestBody @Valid request: CreateGroupInquiryRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ChatChannelResponse> =
        ResponseEntity.ok(chatService.getOrCreateGroupInquiryChannel(jwt, request.interestGroupId))

    @PostMapping("/support")
    @Operation(
        summary = "Get or create the member's Fancia Support channel",
        description = "Creates a 1:1 messaging channel with the Stream system user fancia-support. " +
            "Staff reply from the Stream Dashboard as that user.",
    )
    fun createSupport(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ChatChannelResponse> =
        ResponseEntity.ok(chatService.getOrCreateSupportChannel(jwt))
}
