package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourcesGroupedResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.common.moderation.core.entity.BlockedResource
import com.fancia.backend.shared.common.moderation.core.entity.BlockedResourceId
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.exception.SelfBlockException
import com.fancia.backend.shared.common.moderation.core.exception.UnsupportedBlockedResourceTypeException
import com.fancia.backend.user.core.repository.BlockedResourceRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class BlockedResourceService(
    private val blockedResourceRepository: BlockedResourceRepository,
    private val chatService: ChatService,
) {
    @Transactional
    fun block(request: CreateBlockedResourceRequest, jwt: Jwt): BlockedResourceResponse {
        val userId = currentUserId(jwt)
        validateOwnedType(request.resourceType)
        if (request.resourceType == BlockedResourceType.USER && request.resourceId == userId) {
            throw SelfBlockException()
        }
        val id = BlockedResourceId(
            userId = userId,
            resourceType = request.resourceType,
            resourceId = request.resourceId,
        )
        val existing = blockedResourceRepository.findById(id).orElse(null)
        val saved = existing ?: blockedResourceRepository.save(BlockedResource(id))
        if (request.resourceType == BlockedResourceType.USER && existing == null) {
            chatService.muteUser(userId, request.resourceId)
        }
        return saved.toResponse()
    }

    @Transactional
    fun unblock(resourceType: BlockedResourceType, resourceId: UUID, jwt: Jwt) {
        val userId = currentUserId(jwt)
        validateOwnedType(resourceType)
        blockedResourceRepository.deleteByIdUserIdAndIdResourceTypeAndIdResourceId(
            userId,
            resourceType,
            resourceId,
        )
        if (resourceType == BlockedResourceType.USER) {
            chatService.unmuteUser(userId, resourceId)
        }
    }

    @Transactional(readOnly = true)
    fun list(
        resourceType: BlockedResourceType?,
        jwt: Jwt,
        pageable: Pageable,
    ): Page<BlockedResourceResponse> {
        val userId = currentUserId(jwt)
        val page = if (resourceType == null) {
            blockedResourceRepository.findByIdUserId(userId, pageable)
        } else {
            validateOwnedType(resourceType)
            blockedResourceRepository.findByIdUserIdAndIdResourceType(userId, resourceType, pageable)
        }
        return page.map { it.toResponse() }
    }

    @Transactional(readOnly = true)
    fun groupedForUser(
        userId: UUID,
        types: Collection<BlockedResourceType> = OWNED_TYPES,
    ): BlockedResourcesGroupedResponse {
        val rows = blockedResourceRepository.findAllByIdUserIdAndIdResourceTypeIn(userId, types)
        val grouped = rows
            .groupBy { it.id.resourceType }
            .mapValues { (_, value) -> value.map { it.id.resourceId } }
        return BlockedResourcesGroupedResponse(blocked = grouped)
    }

    @Transactional(readOnly = true)
    fun blockedIds(userId: UUID, type: BlockedResourceType): Set<UUID> =
        blockedResourceRepository.findAllByIdUserIdAndIdResourceTypeIn(userId, listOf(type))
            .map { it.id.resourceId }
            .toSet()

    fun validateOwnedType(resourceType: BlockedResourceType) {
        if (resourceType !in OWNED_TYPES) {
            throw UnsupportedBlockedResourceTypeException()
        }
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    private fun BlockedResource.toResponse() = BlockedResourceResponse(
        resourceType = id.resourceType,
        resourceId = id.resourceId,
        createdAt = createdAt,
    )

    companion object {
        val OWNED_TYPES = setOf(
            BlockedResourceType.USER,
            BlockedResourceType.POST,
            BlockedResourceType.COMMENT,
            BlockedResourceType.TAG,
        )
    }
}
