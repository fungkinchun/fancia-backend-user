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
        if (currentUserId == request.userId) {
            throw SmartMatchSelfMatchException()
        }
        userRepository.findById(request.userId).orElseThrow { UserNotFoundException() }
        if (smartMatchRepository.existsByCreatedByAndUserId(currentUserId, request.userId)) {
            throw SmartMatchAlreadyExistsException(request.userId)
        }
        val smartMatch = SmartMatch().apply {
            createdBy = currentUserId
            userId = request.userId
            matchedByCreatedBy = true
            matchedByUser = false
        }
        val saved = smartMatchRepository.save(smartMatch)
        notifyMatchedUser(request.userId, currentUserId)
        return saved.toDto()
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val smartMatch = smartMatchRepository.findById(id).orElseThrow { SmartMatchNotFoundException(id) }
        val createdBy = smartMatch.createdBy ?: throw SmartMatchAccessDeniedException()
        val matchedUserId = smartMatch.userId ?: throw SmartMatchNotFoundException(id)
        val previousMatchedByUser = smartMatch.matchedByUser
        val previousMatchedByCreatedBy = smartMatch.matchedByCreatedBy

        request.matchedByUser?.let { value ->
            if (currentUserId != matchedUserId) {
                throw SmartMatchAccessDeniedException()
            }
            smartMatch.matchedByUser = value
        }
        request.matchedByCreatedBy?.let { value ->
            if (currentUserId != createdBy) {
                throw SmartMatchAccessDeniedException()
            }
            smartMatch.matchedByCreatedBy = value
        }
        val saved = smartMatchRepository.save(smartMatch)

        if (!previousMatchedByUser && saved.matchedByUser) {
            notifyCreator(createdBy, matchedUserId)
        }
        if (!previousMatchedByCreatedBy && saved.matchedByCreatedBy) {
            notifyMatchedUser(matchedUserId, createdBy)
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
