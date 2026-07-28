package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.CommonInternalClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserPostService(
    private val userRepository: UserRepository,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(userId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        return commonInternalClient.createPost(
            CreatePostRequest(
                targetId = userId,
                authorUserId = currentUserId,
                body = request.body,
                media = request.media,
                featured = request.featured,
                pinned = request.pinned,
            )
        )
    }

    fun update(
        userId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val post = commonInternalClient.updatePost(postId, request)
        if (post.targetId != userId) {
            throw UserNotFoundException()
        }
        return post
    }

    fun like(userId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(userId, postId)
        commonInternalClient.likePost(postId)
    }

    fun unlike(userId: UUID, postId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(userId, postId)
        commonInternalClient.unlikePost(postId)
    }

    fun list(userId: UUID, pageable: Pageable): Page<PostResponse> {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        return commonInternalClient.listPosts(userId, pageable)
    }

    fun get(userId: UUID, postId: UUID): PostResponse {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != userId) {
            throw UserNotFoundException()
        }
        return post
    }
}
