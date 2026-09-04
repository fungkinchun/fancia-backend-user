package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.CreateSmartMatchRequest
import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.dto.UpdateSmartMatchRequest
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.exception.*
import com.fancia.backend.shared.common.redis.RedisQueryCache
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.NotificationInternalClient
import com.fancia.backend.user.mapper.toDto
import com.fancia.backend.shared.notification.core.dto.SendPushNotificationRequest
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
class SmartMatchService(
    private val smartMatchRepository: SmartMatchRepository,
    private val userRepository: UserRepository,
    private val notificationInternalClient: NotificationInternalClient,
    private val chatService: ChatService,
    private val redisQueryCache: ObjectProvider<RedisQueryCache>,
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
        userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
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
            notifyAfterFlagsChanged(
                firstUserId = saved.firstUserId!!,
                secondUserId = saved.secondUserId!!,
                previousFirstUserLiked = saved.firstUserId.let { id ->
                    if (id == currentUserId) previousMine else previousTheirs
                },
                previousSecondUserLiked = saved.secondUserId.let { id ->
                    if (id == currentUserId) previousMine else previousTheirs
                },
                firstUserLiked = saved.firstUserLiked,
                secondUserLiked = saved.secondUserLiked,
            )
            invalidateDecks(currentUserId, otherUserId)
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
            firstUserId = currentUserId,
            secondUserId = otherUserId,
            previousFirstUserLiked = null,
            previousSecondUserLiked = null,
            firstUserLiked = liked,
            secondUserLiked = null,
        )
        invalidateDecks(currentUserId, otherUserId)
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
        invalidateDecks(firstUserId, secondUserId)
        return saved.toDto()
    }

    private fun invalidateDecks(vararg userIds: UUID) {
        val cache = redisQueryCache.ifAvailable ?: return
        userIds.forEach { cache.evictByPrefix("user:smartmatch:deck:$it:") }
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
        if (!userRepository.existsById(recipientId)) return
        val actorName = displayName(userRepository.findById(actorId).orElse(null))
        try {
            notificationInternalClient.sendPush(
                SendPushNotificationRequest(
                    userId = recipientId,
                    title = "New like",
                    body = "$actorName liked your profile",
                    type = "SMART_MATCH_LIKE",
                    path = "/smart-match?focus=matched&userId=$actorId",
                    data = mapOf(
                        "actorUserId" to actorId.toString(),
                        "focus" to "matched",
                    ),
                    preference = "match",
                ),
            )
        } catch (ex: Exception) {
            log.warn("Failed to send smart match like push to {}", recipientId, ex)
        }
    }

    private fun notifyMutualMatch(firstUserId: UUID, secondUserId: UUID) {
        val firstUser = userRepository.findById(firstUserId).orElse(null)
        val secondUser = userRepository.findById(secondUserId).orElse(null)
        val firstName = displayName(firstUser)
        val secondName = displayName(secondUser)

        if (firstUser != null) {
            try {
                notificationInternalClient.sendPush(
                    SendPushNotificationRequest(
                        userId = firstUserId,
                        title = "It's a match!",
                        body = "You and $secondName liked each other. Say hello!",
                        type = "SMART_MATCH_MUTUAL",
                        path = "/smart-match?focus=matched&userId=$secondUserId",
                        data = mapOf(
                            "actorUserId" to secondUserId.toString(),
                            "focus" to "matched",
                        ),
                        preference = "match",
                    ),
                )
            } catch (ex: Exception) {
                log.warn("Failed to send smart match mutual push to {}", firstUserId, ex)
            }
        }
        if (secondUser != null) {
            try {
                notificationInternalClient.sendPush(
                    SendPushNotificationRequest(
                        userId = secondUserId,
                        title = "It's a match!",
                        body = "You and $firstName liked each other. Say hello!",
                        type = "SMART_MATCH_MUTUAL",
                        path = "/smart-match?focus=matched&userId=$firstUserId",
                        data = mapOf(
                            "actorUserId" to firstUserId.toString(),
                            "focus" to "matched",
                        ),
                        preference = "match",
                    ),
                )
            } catch (ex: Exception) {
                log.warn("Failed to send smart match mutual push to {}", secondUserId, ex)
            }
        }
    }

    private fun displayName(user: User?): String =
        user?.firstName?.takeIf { it.isNotBlank() } ?: "Someone"

    private fun currentUserId(jwt: Jwt): UUID {
        return jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
    }
}
