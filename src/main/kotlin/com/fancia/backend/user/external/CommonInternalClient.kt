package com.fancia.backend.user.external

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.web.bind.annotation.*
import java.util.*

@FeignClient(
    name = "common-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface CommonInternalClient {
    @PostMapping("/comments")
    fun createComment(@RequestBody request: CreateCommentRequest): CommentResponse

    @GetMapping("/comments")
    fun listComments(
        @RequestParam targetId: UUID,
        @RequestParam resourceId: UUID,
        pageable: Pageable,
    ): Page<CommentResponse>

    @GetMapping("/comments/{commentId}")
    fun getComment(@PathVariable commentId: UUID): CommentResponse

    @PostMapping("/posts")
    fun createPost(@RequestBody request: CreatePostRequest): PostResponse

    @GetMapping("/posts")
    fun listPosts(
        @RequestParam targetId: UUID,
        @RequestParam(required = false) kind: PostKind? = null,
        @RequestParam(required = false) status: List<PostStatus>? = null,
        pageable: Pageable,
    ): Page<PostResponse>

    @GetMapping("/posts/{postId}")
    fun getPost(@PathVariable postId: UUID): PostResponse

    @PutMapping("/posts/{postId}")
    fun updatePost(
        @PathVariable postId: UUID,
        @RequestBody request: UpdatePostRequest,
    ): PostResponse

    @PostMapping("/posts/{postId}/likes")
    fun likePost(@PathVariable postId: UUID)

    @DeleteMapping("/posts/{postId}/likes")
    fun unlikePost(@PathVariable postId: UUID)

    @PostMapping("/posts/{postId}/votes")
    fun voteOnPost(
        @PathVariable postId: UUID,
        @RequestBody request: CastPollVoteRequest,
    ): PostResponse

    @PostMapping("/comments/{commentId}/likes")
    fun likeComment(@PathVariable commentId: UUID)

    @DeleteMapping("/comments/{commentId}/likes")
    fun unlikeComment(@PathVariable commentId: UUID)
}
