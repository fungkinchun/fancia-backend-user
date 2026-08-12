package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.CreateSmartMatchRequest
import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.dto.UpdateSmartMatchRequest
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.exception.*
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.mapper.toDto
import jakarta.validation.Valid
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
    @Transactional
    fun create(request: @Valid CreateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val targetId = request.userId
        if (currentUserId == targetId) {
            throw SmartMatchSelfMatchException()
        }
        userRepository.findById(targetId).orElseThrow { UserNotFoundException() }

        val asOwner = smartMatchRepository.findByUserIdAndTargetId(currentUserId, targetId)
        if (asOwner != null) {
            val previous = asOwner.userIdFlag
            if (asOwner.userIdFlag != true) {
                asOwner.userIdFlag = true
                asOwner.userIdFlagAt = LocalDateTime.now()
            }
            val saved = smartMatchRepository.save(asOwner)
            if (previous != true) {
                notifyMatchedUser(targetId, currentUserId)
            }
            return saved.toDto()
        }

        val asTarget = smartMatchRepository.findByUserIdAndTargetId(targetId, currentUserId)
        if (asTarget != null) {
            val previous = asTarget.targetIdFlag
            if (asTarget.targetIdFlag != true) {
                asTarget.targetIdFlag = true
                asTarget.targetIdFlagAt = LocalDateTime.now()
            }
            val saved = smartMatchRepository.save(asTarget)
            if (previous != true) {
                notifyMatchedUser(targetId, currentUserId)
            }
            return saved.toDto()
        }

        val smartMatch = SmartMatch().apply {
            createdBy = currentUserId
            userId = currentUserId
            this.targetId = targetId
            userIdFlag = true
            userIdFlagAt = LocalDateTime.now()
            targetIdFlag = null
            targetIdFlagAt = null
        }
        val saved = smartMatchRepository.save(smartMatch)
        notifyMatchedUser(targetId, currentUserId)
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

        if (previousUserIdFlag != true && saved.userIdFlag == true) {
            notifyMatchedUser(matchedTargetId, ownerId)
        }
        if (previousTargetIdFlag != true && saved.targetIdFlag == true) {
            notifyCreator(ownerId, matchedTargetId)
        }

        return saved.toDto()
    }

    private fun notifyMatchedUser(matchedUserId: UUID, actorUserId: UUID) {
        val matchedUser = userRepository.findById(matchedUserId).orElse(null) ?: return
        val actor = userRepository.findById(actorUserId).orElse(null)
        val actorName = actor?.firstName?.takeIf { it.isNotBlank() } ?: "Someone"
        firebaseCloudMessagingService.sendMatchNotification(
            matchedUser,
            "New smart match",
            "$actorName wants to connect with you",
        )
    }

    private fun notifyCreator(creatorUserId: UUID, actorUserId: UUID) {
        val creator = userRepository.findById(creatorUserId).orElse(null) ?: return
        val actor = userRepository.findById(actorUserId).orElse(null)
        val actorName = actor?.firstName?.takeIf { it.isNotBlank() } ?: "Someone"
        firebaseCloudMessagingService.sendMatchNotification(
            creator,
            "New smart match",
            "$actorName matched with you",
        )
    }

    private fun currentUserId(jwt: Jwt): UUID {
        return jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
    }
}
