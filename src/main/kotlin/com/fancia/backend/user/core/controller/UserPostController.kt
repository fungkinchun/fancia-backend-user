package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
import com.fancia.backend.user.core.service.UserPostService
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
@RequestMapping("/api/users/{userId}/posts")
@Tag(name = "User Posts", description = "Posts on user profiles")
@SecurityRequirement(name = "bearerAuth")
class UserPostController(
    private val userPostService: UserPostService,
) {
    @Operation(
        summary = "Create post on user profile",
        description = "Creates a post with optional body and media from presigned upload.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Post created"),
            ApiResponse(responseCode = "400", description = "Validation error"),
            ApiResponse(responseCode = "401", description = "Unauthorized"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ]
    )
    @PostMapping
    fun createPost(
        @PathVariable @Parameter(description = "User id") userId: UUID,
        @RequestBody @Valid request: CreatePostBody,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        val post = userPostService.create(userId, request, jwt)
        return ResponseEntity.status(HttpStatus.CREATED).body(post)
    }

    @Operation(summary = "List posts on user profile", description = "Paginated posts for the user, newest first.")
    @GetMapping
    fun listPosts(
        @PathVariable userId: UUID,
        @RequestParam(required = false)
        @Parameter(description = "Filter by post kind (TEXT or POLL)")
        kind: PostKind?,
        @RequestParam(required = false)
        @Parameter(description = "Filter by post status (repeatable)")
        status: List<PostStatus>?,
        @PageableDefault(size = 20) pageable: Pageable,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<Page<PostResponse>> {
        return ResponseEntity.ok(userPostService.list(userId, kind, status, pageable, jwt))
    }

    @Operation(summary = "Get post on user profile")
    @GetMapping("/{postId}")
    fun getPost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt?,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(userPostService.get(userId, postId, jwt))
    }

    @Operation(summary = "Update post")
    @PutMapping("/{postId}")
    fun updatePost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: UpdatePostRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(userPostService.update(userId, postId, request, jwt))
    }

    @Operation(summary = "Delete post")
    @DeleteMapping("/{postId}")
    fun deletePost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        userPostService.delete(userId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Like post")
    @PostMapping("/{postId}/likes")
    fun likePost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        userPostService.like(userId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{postId}/likes")
    fun unlikePost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        userPostService.unlike(userId, postId, jwt)
        return ResponseEntity.noContent().build()
    }

    @Operation(summary = "Vote on poll post")
    @PostMapping("/{postId}/votes")
    fun voteOnPost(
        @PathVariable userId: UUID,
        @PathVariable postId: UUID,
        @RequestBody @Valid request: CastPollVoteRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<PostResponse> {
        return ResponseEntity.ok(userPostService.vote(userId, postId, request, jwt))
    }
}
