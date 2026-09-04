package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.DomainException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.common.moderation.core.dto.CreateReportRequest
import com.fancia.backend.shared.common.moderation.core.dto.ReportResponse
import com.fancia.backend.shared.common.moderation.core.entity.Report
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.common.moderation.core.enums.ReportStatus
import com.fancia.backend.shared.common.moderation.core.exception.UnsupportedBlockedResourceTypeException
import com.fancia.backend.user.core.repository.ReportRepository
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class ReportService(
    private val reportRepository: ReportRepository,
    private val blockedResourceService: BlockedResourceService,
) {
    @Transactional
    fun create(request: CreateReportRequest, jwt: Jwt): ReportResponse {
        val userId = currentUserId(jwt)
        if (request.targetType !in REPORTABLE_TYPES) {
            throw UnsupportedBlockedResourceTypeException()
        }

        val report = Report().apply {
            reporterUserId = userId
            createdBy = userId
            targetType = request.targetType
            targetId = request.targetId
            reason = request.reason
            details = request.details?.trim()?.ifBlank { null }
            status = ReportStatus.OPEN
        }
        val saved = reportRepository.save(report)

        if (request.alsoHideResource && request.targetType in BlockedResourceService.OWNED_TYPES) {
            blockedResourceService.block(
                CreateBlockedResourceRequest(
                    resourceType = request.targetType,
                    resourceId = request.targetId,
                ),
                jwt,
            )
        }

        if (request.alsoBlockUser) {
            val ownerId = when (request.targetType) {
                BlockedResourceType.USER -> request.targetId
                else -> request.targetOwnerUserId
                    ?: throw DomainException(
                        title = "Owner Required",
                        message = "targetOwnerUserId is required when alsoBlockUser is true",
                        errorCode = "TARGET_OWNER_REQUIRED",
                    )
            }
            blockedResourceService.block(
                CreateBlockedResourceRequest(
                    resourceType = BlockedResourceType.USER,
                    resourceId = ownerId,
                ),
                jwt,
            )
        }

        return saved.toResponse()
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    private fun Report.toResponse() = ReportResponse(
        id = id,
        targetType = targetType,
        targetId = targetId!!,
        reason = reason,
        details = details,
        status = status,
        createdAt = createdAt,
    )

    companion object {
        val REPORTABLE_TYPES = setOf(
            BlockedResourceType.USER,
            BlockedResourceType.POST,
            BlockedResourceType.COMMENT,
        )
    }
}
