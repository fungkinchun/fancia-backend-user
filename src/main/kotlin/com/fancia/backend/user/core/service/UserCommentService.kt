package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.CommonInternalClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserCommentService(
    private val userRepository: UserRepository,
    private val commonInternalClient: CommonInternalClient,
) {
    fun create(userId: UUID, request: CreateCommentRequest, jwt: Jwt): CommentResponse {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        return commonInternalClient.createComment(request)
    }

    fun list(
        userId: UUID,
        targetId: UUID,
        pageable: Pageable,
    ): Page<CommentResponse> {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        return commonInternalClient.listComments(targetId, userId, pageable)
    }

    fun get(userId: UUID, commentId: UUID): CommentResponse {
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != userId) {
            throw CommentNotFoundException(commentId)
        }
        return comment
    }

    fun like(userId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(userId, commentId)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(userId: UUID, commentId: UUID, jwt: Jwt) {
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        get(userId, commentId)
        commonInternalClient.unlikeComment(commentId)
    }
}
