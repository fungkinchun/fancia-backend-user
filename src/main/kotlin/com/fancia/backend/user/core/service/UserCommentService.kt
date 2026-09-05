package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.comment.core.dto.CommentResponse
import com.fancia.backend.shared.common.comment.core.dto.CreateCommentRequest
import com.fancia.backend.shared.common.comment.core.exception.CommentNotFoundException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.support.CommentVisibility
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
    private val blockedResourceService: BlockedResourceService,
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
        jwt: Jwt? = null,
    ): Page<CommentResponse> {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val page = commonInternalClient.listComments(targetId, userId, pageable)
        return filterBlocked(page, pageable, jwt)
    }

    fun get(userId: UUID, commentId: UUID, jwt: Jwt? = null): CommentResponse {
        val comment = commonInternalClient.getComment(commentId)
        if (comment.resourceId != userId) {
            throw CommentNotFoundException(commentId)
        }
        assertVisible(comment, jwt)
        return comment
    }

    fun like(userId: UUID, commentId: UUID, jwt: Jwt) {
        get(userId, commentId, jwt)
        commonInternalClient.likeComment(commentId)
    }

    fun unlike(userId: UUID, commentId: UUID, jwt: Jwt) {
        get(userId, commentId, jwt)
        commonInternalClient.unlikeComment(commentId)
    }

    private fun filterBlocked(
        page: Page<CommentResponse>,
        pageable: Pageable,
        jwt: Jwt?,
    ): Page<CommentResponse> {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return page
        val blockedComments = blockedResourceService.blockedIds(viewerId, BlockedResourceType.COMMENT)
        val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
        return CommentVisibility.filterPage(page, pageable, blockedComments, blockedUsers)
    }

    private fun assertVisible(comment: CommentResponse, jwt: Jwt?) {
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) } ?: return
        val blockedComments = blockedResourceService.blockedIds(viewerId, BlockedResourceType.COMMENT)
        val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
        if (!CommentVisibility.isVisibleToViewer(comment, blockedComments, blockedUsers)) {
            throw CommentNotFoundException(comment.id)
        }
    }
}
