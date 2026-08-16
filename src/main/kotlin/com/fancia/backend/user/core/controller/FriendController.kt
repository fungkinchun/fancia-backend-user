package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.CreateFriendRequest
import com.fancia.backend.shared.user.core.dto.FriendshipResponse
import com.fancia.backend.shared.user.core.dto.FriendshipStatusResponse
import com.fancia.backend.user.core.service.FriendService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/friends")
@Tag(name = "Friends", description = "Friend requests and friendships")
@SecurityRequirement(name = "bearerAuth")
class FriendController(
    private val friendService: FriendService,
) {
    @PostMapping("/requests")
    @Operation(summary = "Send a friend request")
    fun request(
        @RequestBody @Valid request: CreateFriendRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<FriendshipResponse> =
        ResponseEntity.ok(friendService.request(request, jwt))

    @PostMapping("/requests/{id}/accept")
    @Operation(summary = "Accept an incoming friend request")
    fun accept(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<FriendshipResponse> =
        ResponseEntity.ok(friendService.accept(id, jwt))

    @PostMapping("/requests/{id}/reject")
    @Operation(summary = "Reject an incoming friend request")
    fun reject(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<FriendshipResponse> =
        ResponseEntity.ok(friendService.reject(id, jwt))

    @DeleteMapping("/requests/{id}")
    @Operation(summary = "Cancel an outgoing pending friend request")
    fun cancel(
        @PathVariable id: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        friendService.cancel(id, jwt)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Unfriend an accepted friend")
    fun unfriend(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        friendService.unfriend(userId, jwt)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @GetMapping
    @Operation(summary = "List accepted friends")
    fun listFriends(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<FriendshipResponse>> =
        ResponseEntity.ok(friendService.listFriends(jwt, pageable))

    @GetMapping("/requests/incoming")
    @Operation(summary = "List incoming pending friend requests")
    fun listIncoming(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<FriendshipResponse>> =
        ResponseEntity.ok(friendService.listIncoming(jwt, pageable))

    @GetMapping("/requests/outgoing")
    @Operation(summary = "List outgoing pending friend requests")
    fun listOutgoing(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20) pageable: Pageable,
    ): ResponseEntity<Page<FriendshipResponse>> =
        ResponseEntity.ok(friendService.listOutgoing(jwt, pageable))

    @GetMapping("/status/{userId}")
    @Operation(summary = "Get friendship relation status with another user")
    fun status(
        @PathVariable userId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<FriendshipStatusResponse> =
        ResponseEntity.ok(friendService.status(userId, jwt))
}
