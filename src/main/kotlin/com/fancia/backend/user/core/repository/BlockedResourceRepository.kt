package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.common.moderation.core.entity.BlockedResource
import com.fancia.backend.shared.common.moderation.core.entity.BlockedResourceId
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface BlockedResourceRepository : JpaRepository<BlockedResource, BlockedResourceId> {
    fun findByIdUserId(userId: UUID, pageable: Pageable): Page<BlockedResource>

    fun findByIdUserIdAndIdResourceType(
        userId: UUID,
        resourceType: BlockedResourceType,
        pageable: Pageable,
    ): Page<BlockedResource>

    fun findAllByIdUserId(userId: UUID): List<BlockedResource>

    fun findAllByIdUserIdAndIdResourceTypeIn(
        userId: UUID,
        resourceTypes: Collection<BlockedResourceType>,
    ): List<BlockedResource>

    fun deleteByIdUserIdAndIdResourceTypeAndIdResourceId(
        userId: UUID,
        resourceType: BlockedResourceType,
        resourceId: UUID,
    )
}
