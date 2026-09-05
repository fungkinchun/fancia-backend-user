package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.support.PostVisibility
import com.fancia.backend.shared.common.post.core.dto.CastPollVoteRequest
import com.fancia.backend.shared.common.post.core.enums.PostKind
import com.fancia.backend.shared.common.post.core.enums.PostStatus
import com.fancia.backend.shared.common.post.core.dto.CreatePostBody
import com.fancia.backend.shared.common.post.core.dto.CreatePostRequest
import com.fancia.backend.shared.common.post.core.dto.PostMediaItem
import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.common.post.core.dto.UpdatePostRequest
import com.fancia.backend.shared.common.post.core.exception.PostNotFoundException
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.CommonInternalClient
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.*

@Service
class UserPostService(
    private val userRepository: UserRepository,
    private val commonInternalClient: CommonInternalClient,
    private val fileUploadService: FileStorageService,
    private val blockedResourceService: BlockedResourceService,
) {
    fun create(userId: UUID, request: CreatePostBody, jwt: Jwt): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (currentUserId != userId) {
            throw InvalidAuthenticationException()
        }
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        return commonInternalClient.createPost(
            CreatePostRequest(
                targetId = userId,
                authorUserId = currentUserId,
                body = request.body,
                media = dedicateMedia(request.mediaOrEmpty(), userId),
                status = request.statusOrDefault(),
                expiredAt = request.expiredAt,
                kind = request.kindOrDefault(),
                poll = request.poll,
            )
        )
    }

    fun update(
        userId: UUID,
        postId: UUID,
        request: UpdatePostRequest,
        jwt: Jwt,
    ): PostResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        if (currentUserId != userId) {
            throw InvalidAuthenticationException()
        }
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val scopedRequest = request.copy(media = dedicateMedia(request.media, userId))
        val post = commonInternalClient.updatePost(postId, scopedRequest)
        if (post.targetId != userId) {
            throw UserNotFoundException()
        }
        return post
    }

    fun like(userId: UUID, postId: UUID, jwt: Jwt) {
        get(userId, postId, jwt)
        commonInternalClient.likePost(postId)
    }

    fun unlike(userId: UUID, postId: UUID, jwt: Jwt) {
        get(userId, postId, jwt)
        commonInternalClient.unlikePost(postId)
    }

    fun vote(userId: UUID, postId: UUID, request: CastPollVoteRequest, jwt: Jwt): PostResponse {
        get(userId, postId, jwt)
        val post = commonInternalClient.voteOnPost(postId, request)
        if (post.targetId != userId) {
            throw UserNotFoundException()
        }
        return post
    }

    fun list(
        userId: UUID,
        kind: PostKind? = null,
        status: List<PostStatus>? = null,
        pageable: Pageable,
        jwt: Jwt? = null,
    ): Page<PostResponse> {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
        if (viewerId != null) {
            val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
            if (userId in blockedUsers) {
                return Page.empty(pageable)
            }
        }
        val page = commonInternalClient.listPosts(userId, kind, status, pageable)
        return filterBlocked(page, pageable, viewerId)
    }

    fun get(userId: UUID, postId: UUID, jwt: Jwt? = null): PostResponse {
        if (!userRepository.existsById(userId)) {
            throw UserNotFoundException()
        }
        val viewerId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
        if (viewerId != null) {
            val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
            if (userId in blockedUsers) {
                throw PostNotFoundException(postId)
            }
        }
        val post = commonInternalClient.getPost(postId)
        if (post.targetId != userId) {
            throw UserNotFoundException()
        }
        assertVisible(post, viewerId)
        return post
    }

    private fun filterBlocked(
        page: Page<PostResponse>,
        pageable: Pageable,
        viewerId: UUID?,
    ): Page<PostResponse> {
        if (viewerId == null || page.isEmpty) return page
        val blockedPosts = blockedResourceService.blockedIds(viewerId, BlockedResourceType.POST)
        val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
        if (blockedPosts.isEmpty() && blockedUsers.isEmpty()) return page
        val kept = page.content.filter {
            PostVisibility.isVisibleToViewer(it, blockedPosts, blockedUsers)
        }
        if (kept.size == page.content.size) return page
        return PageImpl(kept, pageable, page.totalElements)
    }

    private fun assertVisible(post: PostResponse, viewerId: UUID?) {
        if (viewerId == null) return
        val blockedPosts = blockedResourceService.blockedIds(viewerId, BlockedResourceType.POST)
        val blockedUsers = blockedResourceService.blockedIds(viewerId, BlockedResourceType.USER)
        if (!PostVisibility.isVisibleToViewer(post, blockedPosts, blockedUsers)) {
            throw PostNotFoundException(post.id)
        }
    }

    private fun dedicateMedia(media: List<PostMediaItem>, userId: UUID): List<PostMediaItem> =
        media.map { item ->
            item.copy(
                objectKey = fileUploadService.moveTmpToDedicatedPath(
                    item.objectKey,
                    UploadScope.USER,
                    userId,
                ),
            )
        }
}
