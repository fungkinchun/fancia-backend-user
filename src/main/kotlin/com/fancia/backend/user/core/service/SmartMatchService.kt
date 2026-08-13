package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.CreateSmartMatchRequest
import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.dto.UpdateSmartMatchRequest
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.exception.*
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.mapper.toDto
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class SmartMatchService(
    private val smartMatchRepository: SmartMatchRepository,
    private val userRepository: UserRepository,
    private val firebaseCloudMessagingService: FirebaseCloudMessagingService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(request: @Valid CreateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val targetId = request.userId
        val liked = request.liked
        if (currentUserId == targetId) {
            throw SmartMatchSelfMatchException()
        }
        userRepository.findById(targetId).orElseThrow { UserNotFoundException() }

        val asOwner = smartMatchRepository.findByUserIdAndTargetId(currentUserId, targetId)
        if (asOwner != null) {
            val previousUserIdFlag = asOwner.userIdFlag
            val previousTargetIdFlag = asOwner.targetIdFlag
            if (asOwner.userIdFlag != liked) {
                asOwner.userIdFlag = liked
                asOwner.userIdFlagAt = LocalDateTime.now()
            }
            val saved = smartMatchRepository.save(asOwner)
            notifyAfterFlagsChanged(
                ownerId = currentUserId,
                targetId = targetId,
                previousUserIdFlag = previousUserIdFlag,
                previousTargetIdFlag = previousTargetIdFlag,
                userIdFlag = saved.userIdFlag,
                targetIdFlag = saved.targetIdFlag,
            )
            return saved.toDto()
        }

        val asTarget = smartMatchRepository.findByUserIdAndTargetId(targetId, currentUserId)
        if (asTarget != null) {
            val previousUserIdFlag = asTarget.userIdFlag
            val previousTargetIdFlag = asTarget.targetIdFlag
            if (asTarget.targetIdFlag != liked) {
                asTarget.targetIdFlag = liked
                asTarget.targetIdFlagAt = LocalDateTime.now()
            }
            val saved = smartMatchRepository.save(asTarget)
            notifyAfterFlagsChanged(
                ownerId = targetId,
                targetId = currentUserId,
                previousUserIdFlag = previousUserIdFlag,
                previousTargetIdFlag = previousTargetIdFlag,
                userIdFlag = saved.userIdFlag,
                targetIdFlag = saved.targetIdFlag,
            )
            return saved.toDto()
        }

        val smartMatch = SmartMatch().apply {
            createdBy = currentUserId
            userId = currentUserId
            this.targetId = targetId
            userIdFlag = liked
            userIdFlagAt = LocalDateTime.now()
            targetIdFlag = null
            targetIdFlagAt = null
        }
        val saved = smartMatchRepository.save(smartMatch)
        notifyAfterFlagsChanged(
            ownerId = currentUserId,
            targetId = targetId,
            previousUserIdFlag = null,
            previousTargetIdFlag = null,
            userIdFlag = liked,
            targetIdFlag = null,
        )
        return saved.toDto()
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val smartMatch = smartMatchRepository.findById(id).orElseThrow { SmartMatchNotFoundException(id) }
        val ownerId = smartMatch.userId ?: throw SmartMatchNotFoundException(id)
        val matchedTargetId = smartMatch.targetId ?: throw SmartMatchNotFoundException(id)

        val previousUserIdFlag = smartMatch.userIdFlag
        val previousTargetIdFlag = smartMatch.targetIdFlag
        val now = LocalDateTime.now()

        request.resolvedUserIdFlag()?.let { value ->
            if (currentUserId != ownerId) {
                throw SmartMatchAccessDeniedException()
            }
            if (smartMatch.userIdFlag != value) {
                smartMatch.userIdFlag = value
                smartMatch.userIdFlagAt = now
            }
        }
        request.resolvedTargetIdFlag()?.let { value ->
            if (currentUserId != matchedTargetId) {
                throw SmartMatchAccessDeniedException()
            }
            if (smartMatch.targetIdFlag != value) {
                smartMatch.targetIdFlag = value
                smartMatch.targetIdFlagAt = now
            }
        }

        val saved = smartMatchRepository.save(smartMatch)
        notifyAfterFlagsChanged(
            ownerId = ownerId,
            targetId = matchedTargetId,
            previousUserIdFlag = previousUserIdFlag,
            previousTargetIdFlag = previousTargetIdFlag,
            userIdFlag = saved.userIdFlag,
            targetIdFlag = saved.targetIdFlag,
        )
        return saved.toDto()
    }

    private fun notifyAfterFlagsChanged(
        ownerId: UUID,
        targetId: UUID,
        previousUserIdFlag: Boolean?,
        previousTargetIdFlag: Boolean?,
        userIdFlag: Boolean?,
        targetIdFlag: Boolean?,
    ) {
        val ownerLikedNow = previousUserIdFlag != true && userIdFlag == true
        val targetLikedNow = previousTargetIdFlag != true && targetIdFlag == true
        if (!ownerLikedNow && !targetLikedNow) {
            return
        }

        try {
            val mutualNow = userIdFlag == true && targetIdFlag == true
            val mutualBefore = previousUserIdFlag == true && previousTargetIdFlag == true
            if (mutualNow && !mutualBefore) {
                notifyMutualMatch(ownerId, targetId)
                return
            }

            if (ownerLikedNow) {
                notifyLike(recipientId = targetId, actorId = ownerId)
            }
            if (targetLikedNow) {
                notifyLike(recipientId = ownerId, actorId = targetId)
            }
        } catch (ex: Exception) {
            // Never fail like/pass because push/user JSON deserialization broke.
            log.warn(
                "Smart match notification failed (owner={}, target={}): {}",
                ownerId,
                targetId,
                ex.message,
                ex,
            )
        }
    }

    private fun notifyLike(recipientId: UUID, actorId: UUID) {
        val recipient = userRepository.findById(recipientId).orElse(null) ?: return
        val actorName = displayName(userRepository.findById(actorId).orElse(null))
        firebaseCloudMessagingService.sendSmartMatchLikeNotification(
            recipient = recipient,
            actorName = actorName,
            actorUserId = actorId,
        )
    }

    private fun notifyMutualMatch(ownerId: UUID, targetId: UUID) {
        val owner = userRepository.findById(ownerId).orElse(null)
        val target = userRepository.findById(targetId).orElse(null)
        val ownerName = displayName(owner)
        val targetName = displayName(target)

        if (owner != null) {
            firebaseCloudMessagingService.sendSmartMatchMutualNotification(
                recipient = owner,
                actorName = targetName,
                actorUserId = targetId,
            )
        }
        if (target != null) {
            firebaseCloudMessagingService.sendSmartMatchMutualNotification(
                recipient = target,
                actorName = ownerName,
                actorUserId = ownerId,
            )
        }
    }

    private fun displayName(user: User?): String =
        user?.firstName?.takeIf { it.isNotBlank() } ?: "Someone"

    private fun currentUserId(jwt: Jwt): UUID {
        return jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
    }
}
