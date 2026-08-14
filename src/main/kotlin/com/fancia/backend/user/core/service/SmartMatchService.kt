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
    private val chatService: ChatService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun create(request: @Valid CreateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val otherUserId = request.userId
        val liked = request.liked
        if (currentUserId == otherUserId) {
            throw SmartMatchSelfMatchException()
        }
        userRepository.findById(otherUserId).orElseThrow { UserNotFoundException() }
        val asFirst = smartMatchRepository.findByFirstUserIdAndSecondUserId(currentUserId, otherUserId)
        val asSecond = smartMatchRepository.findByFirstUserIdAndSecondUserId(otherUserId, currentUserId)
        if (asFirst != null || asSecond != null) {
            val previousMine = asFirst?.likedBy(currentUserId) ?: asSecond?.likedBy(currentUserId)
            val previousTheirs = asFirst?.likedBy(otherUserId) ?: asSecond?.likedBy(otherUserId)
            val now = LocalDateTime.now()
            if (asFirst != null && asFirst.likedBy(currentUserId) != liked) {
                asFirst.setLikedBy(currentUserId, liked, now)
                smartMatchRepository.save(asFirst)
            }
            if (asSecond != null && asSecond.likedBy(currentUserId) != liked) {
                asSecond.setLikedBy(currentUserId, liked, now)
                smartMatchRepository.save(asSecond)
            }
            val saved = asFirst ?: asSecond!!
            val theirLiked = saved.likedBy(otherUserId)
            notifyAfterFlagsChanged(
                actorUserId = currentUserId,
                otherUserId = otherUserId,
                previousActorLiked = previousMine,
                previousOtherLiked = previousTheirs,
                actorLiked = liked,
                otherLiked = theirLiked,
            )
            return saved.toDto()
        }
        val smartMatch = SmartMatch().apply {
            createdBy = currentUserId
            firstUserId = currentUserId
            secondUserId = otherUserId
            setLikedBy(currentUserId, liked, LocalDateTime.now())
        }
        val saved = smartMatchRepository.save(smartMatch)
        notifyAfterFlagsChanged(
            actorUserId = currentUserId,
            otherUserId = otherUserId,
            previousActorLiked = null,
            previousOtherLiked = null,
            actorLiked = liked,
            otherLiked = null,
        )
        return saved.toDto()
    }

    @Transactional
    fun update(id: UUID, request: @Valid UpdateSmartMatchRequest, jwt: Jwt): SmartMatchResponse {
        val currentUserId = currentUserId(jwt)
        val smartMatch = smartMatchRepository.findById(id).orElseThrow { SmartMatchNotFoundException(id) }
        val firstUserId = smartMatch.firstUserId ?: throw SmartMatchNotFoundException(id)
        val secondUserId = smartMatch.secondUserId ?: throw SmartMatchNotFoundException(id)
        val previousFirstUserLiked = smartMatch.firstUserLiked
        val previousSecondUserLiked = smartMatch.secondUserLiked
        val now = LocalDateTime.now()

        request.resolvedFirstUserLiked()?.let { value ->
            if (currentUserId != firstUserId) {
                throw SmartMatchAccessDeniedException()
            }
            if (smartMatch.firstUserLiked != value) {
                smartMatch.firstUserLiked = value
                smartMatch.firstUserLikedAt = now
            }
        }
        request.resolvedSecondUserLiked()?.let { value ->
            if (currentUserId != secondUserId) {
                throw SmartMatchAccessDeniedException()
            }
            if (smartMatch.secondUserLiked != value) {
                smartMatch.secondUserLiked = value
                smartMatch.secondUserLikedAt = now
            }
        }
        val saved = smartMatchRepository.save(smartMatch)
        notifyAfterFlagsChanged(
            firstUserId = firstUserId,
            secondUserId = secondUserId,
            previousFirstUserLiked = previousFirstUserLiked,
            previousSecondUserLiked = previousSecondUserLiked,
            firstUserLiked = saved.firstUserLiked,
            secondUserLiked = saved.secondUserLiked,
        )
        return saved.toDto()
    }

    private fun notifyAfterFlagsChanged(
        actorUserId: UUID,
        otherUserId: UUID,
        previousActorLiked: Boolean?,
        previousOtherLiked: Boolean?,
        actorLiked: Boolean?,
        otherLiked: Boolean?,
    ) {
        val actorLikedNow = previousActorLiked != true && actorLiked == true
        val otherLikedNow = previousOtherLiked != true && otherLiked == true
        if (!actorLikedNow && !otherLikedNow) {
            return
        }

        try {
            val mutualNow = actorLiked == true && otherLiked == true
            val mutualBefore = previousActorLiked == true && previousOtherLiked == true
            val messageableNow = actorLiked == true || otherLiked == true
            val messageableBefore = previousActorLiked == true || previousOtherLiked == true

            if (messageableNow && !messageableBefore) {
                chatService.provisionDirectMessageChannelIfAllowed(actorUserId, otherUserId)
            }

            if (mutualNow && !mutualBefore) {
                notifyMutualMatch(actorUserId, otherUserId)
                return
            }

            if (actorLikedNow) {
                notifyLike(recipientId = otherUserId, actorId = actorUserId)
            }
            if (otherLikedNow) {
                notifyLike(recipientId = actorUserId, actorId = otherUserId)
            }
        } catch (ex: Exception) {
            log.warn(
                "Smart match notification failed (actor={}, other={}): {}",
                actorUserId,
                otherUserId,
                ex.message,
                ex,
            )
        }
    }

    private fun notifyAfterFlagsChanged(
        firstUserId: UUID,
        secondUserId: UUID,
        previousFirstUserLiked: Boolean?,
        previousSecondUserLiked: Boolean?,
        firstUserLiked: Boolean?,
        secondUserLiked: Boolean?,
    ) {
        val firstLikedNow = previousFirstUserLiked != true && firstUserLiked == true
        val secondLikedNow = previousSecondUserLiked != true && secondUserLiked == true
        if (!firstLikedNow && !secondLikedNow) {
            return
        }

        try {
            val mutualNow = firstUserLiked == true && secondUserLiked == true
            val mutualBefore = previousFirstUserLiked == true && previousSecondUserLiked == true
            val messageableNow = firstUserLiked == true || secondUserLiked == true
            val messageableBefore = previousFirstUserLiked == true || previousSecondUserLiked == true

            if (messageableNow && !messageableBefore) {
                chatService.provisionDirectMessageChannelIfAllowed(firstUserId, secondUserId)
            }

            if (mutualNow && !mutualBefore) {
                notifyMutualMatch(firstUserId, secondUserId)
                return
            }

            if (firstLikedNow) {
                notifyLike(recipientId = secondUserId, actorId = firstUserId)
            }
            if (secondLikedNow) {
                notifyLike(recipientId = firstUserId, actorId = secondUserId)
            }
        } catch (ex: Exception) {
            log.warn(
                "Smart match notification failed (first={}, second={}): {}",
                firstUserId,
                secondUserId,
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

    private fun notifyMutualMatch(firstUserId: UUID, secondUserId: UUID) {
        val firstUser = userRepository.findById(firstUserId).orElse(null)
        val secondUser = userRepository.findById(secondUserId).orElse(null)
        val firstName = displayName(firstUser)
        val secondName = displayName(secondUser)

        if (firstUser != null) {
            firebaseCloudMessagingService.sendSmartMatchMutualNotification(
                recipient = firstUser,
                actorName = secondName,
                actorUserId = secondUserId,
            )
        }
        if (secondUser != null) {
            firebaseCloudMessagingService.sendSmartMatchMutualNotification(
                recipient = secondUser,
                actorName = firstName,
                actorUserId = firstUserId,
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
