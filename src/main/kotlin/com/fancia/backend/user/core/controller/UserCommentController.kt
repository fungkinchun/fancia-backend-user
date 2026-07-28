package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.user.core.service.UserCommentService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
import java.util.*

@RestController
@RequestMapping("/api/users/{userId}/comments")
@Tag(name = "User Comments", description = "Comments on user profiles")
@SecurityRequirement(name = "bearerAuth")
class UserCommentController(
    private val userCommentService: UserCommentService,
) {
    @Operation(
        summary = "Create comment on user profile",
        description = "Creates a top-level comment or reply.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Comment created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "User or parent comment not found"),
        ]
    )
    @PostMapping
    fun createComment(
        @PathVariable
        @Parameter(description = "User id")
        userId: UUID,
        @RequestBody @Valid request: CreateCommentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CommentResponse> {
        val comment = userCommentService.create(userId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(comment)
    }

    @Operation(
        summary = "List comments",
        description = "Paginated comments scoped by resourceId (user id or post id). Omit targetId to list top-level comments for that resource, or pass a comment id for replies.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comments returned"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ]
    )
    @GetMapping
    fun listComments(
        @PathVariable
        @Parameter(description = "User id")
        userId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Target id to list under (defaults to userId)")
        targetId: UUID?,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<CommentResponse>> {
        return ResponseEntity.ok(userCommentService.list(userId, targetId ?: userId, pageable))
    }

    @Operation(summary = "Like comment")
    @PostMapping("/{commentId}/likes")
    fun likeComment(
        @PathVariable userId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        userCommentService.like(userId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Unlike comment")
    @DeleteMapping("/{commentId}/likes")
    fun unlikeComment(
        @PathVariable userId: UUID,
        @PathVariable commentId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        userCommentService.unlike(userId, commentId, jwt)
        return ResponseEntity.noContent().build()
    }
}
