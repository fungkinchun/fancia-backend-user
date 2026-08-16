package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.CreateFriendRequest
import com.fancia.backend.shared.user.core.dto.FriendshipResponse
import com.fancia.backend.shared.user.core.dto.FriendshipStatusResponse
import com.fancia.backend.shared.user.core.entity.Friendship
import com.fancia.backend.shared.user.core.enums.FriendshipRelationStatus
import com.fancia.backend.shared.user.core.enums.FriendshipStatus
import com.fancia.backend.shared.user.core.exception.FriendRequestNotAllowedException
import com.fancia.backend.shared.user.core.exception.FriendshipAccessDeniedException
import com.fancia.backend.shared.user.core.exception.FriendshipAlreadyExistsException
import com.fancia.backend.shared.user.core.exception.FriendshipNotFoundException
import com.fancia.backend.shared.user.core.exception.FriendshipSelfRequestException
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.core.repository.FriendshipRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.mapper.toDto
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class FriendService(
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
    private val chatService: ChatService,
) {
    @Transactional
    fun request(@Valid request: CreateFriendRequest, jwt: Jwt): FriendshipResponse {
        val currentUserId = currentUserId(jwt)
        val targetUserId = request.userId
        if (currentUserId == targetUserId) {
            throw FriendshipSelfRequestException()
        }
        userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        val target = userRepository.findById(targetUserId).orElseThrow { UserNotFoundException() }
        val allowFriendRequests = target.settings?.privacy?.allowFriendRequests ?: true
        if (!allowFriendRequests) {
            throw FriendRequestNotAllowedException()
        }
        val existingActive = friendshipRepository.findBetweenUsersWithStatuses(
            currentUserId,
            targetUserId,
            listOf(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED),
        )
        if (existingActive != null) {
            throw FriendshipAlreadyExistsException(targetUserId)
        }

        val friendship = Friendship().apply {
            requesterId = currentUserId
            addresseeId = targetUserId
            status = FriendshipStatus.PENDING
            createdBy = currentUserId
        }
        return friendshipRepository.save(friendship).toDto()
    }

    @Transactional
    fun accept(friendshipId: UUID, jwt: Jwt): FriendshipResponse {
        val currentUserId = currentUserId(jwt)
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { FriendshipNotFoundException(friendshipId) }
        if (friendship.addresseeId != currentUserId) {
            throw FriendshipAccessDeniedException()
        }
        if (friendship.status != FriendshipStatus.PENDING) {
            throw FriendshipAccessDeniedException(
                message = "Only pending friend requests can be accepted",
            )
        }
        friendship.status = FriendshipStatus.ACCEPTED
        friendship.respondedAt = LocalDateTime.now()
        val saved = friendshipRepository.save(friendship)
        chatService.provisionDirectMessageChannelIfAllowed(
            saved.requesterId!!,
            saved.addresseeId!!,
        )
        return saved.toDto()
    }

    @Transactional
    fun reject(friendshipId: UUID, jwt: Jwt): FriendshipResponse {
        val currentUserId = currentUserId(jwt)
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { FriendshipNotFoundException(friendshipId) }
        if (friendship.addresseeId != currentUserId) {
            throw FriendshipAccessDeniedException()
        }
        if (friendship.status != FriendshipStatus.PENDING) {
            throw FriendshipAccessDeniedException(
                message = "Only pending friend requests can be rejected",
            )
        }
        friendship.status = FriendshipStatus.REJECTED
        friendship.respondedAt = LocalDateTime.now()
        return friendshipRepository.save(friendship).toDto()
    }

    @Transactional
    fun cancel(friendshipId: UUID, jwt: Jwt) {
        val currentUserId = currentUserId(jwt)
        val friendship = friendshipRepository.findById(friendshipId)
            .orElseThrow { FriendshipNotFoundException(friendshipId) }
        if (friendship.requesterId != currentUserId) {
            throw FriendshipAccessDeniedException()
        }
        if (friendship.status != FriendshipStatus.PENDING) {
            throw FriendshipAccessDeniedException(
                message = "Only pending outgoing friend requests can be cancelled",
            )
        }
        friendship.status = FriendshipStatus.CANCELLED
        friendship.respondedAt = LocalDateTime.now()
        friendshipRepository.save(friendship)
    }

    @Transactional
    fun unfriend(otherUserId: UUID, jwt: Jwt) {
        val currentUserId = currentUserId(jwt)
        if (currentUserId == otherUserId) {
            throw FriendshipSelfRequestException()
        }
        val friendship = friendshipRepository.findBetweenUsersWithStatuses(
            currentUserId,
            otherUserId,
            listOf(FriendshipStatus.ACCEPTED),
        ) ?: throw FriendshipNotFoundException()
        friendship.status = FriendshipStatus.CANCELLED
        friendship.respondedAt = LocalDateTime.now()
        friendshipRepository.save(friendship)
    }

    fun listFriends(jwt: Jwt, pageable: Pageable): Page<FriendshipResponse> {
        val currentUserId = currentUserId(jwt)
        return friendshipRepository
            .findByUserIdAndStatus(currentUserId, FriendshipStatus.ACCEPTED, pageable)
            .map { it.toDto() }
    }

    fun listIncoming(jwt: Jwt, pageable: Pageable): Page<FriendshipResponse> {
        val currentUserId = currentUserId(jwt)
        return friendshipRepository
            .findByAddresseeIdAndStatus(currentUserId, FriendshipStatus.PENDING, pageable)
            .map { it.toDto() }
    }

    fun listOutgoing(jwt: Jwt, pageable: Pageable): Page<FriendshipResponse> {
        val currentUserId = currentUserId(jwt)
        return friendshipRepository
            .findByRequesterIdAndStatus(currentUserId, FriendshipStatus.PENDING, pageable)
            .map { it.toDto() }
    }

    fun status(otherUserId: UUID, jwt: Jwt): FriendshipStatusResponse {
        val currentUserId = currentUserId(jwt)
        if (currentUserId == otherUserId) {
            return FriendshipStatusResponse(userId = otherUserId, status = FriendshipRelationStatus.NONE)
        }
        userRepository.findById(otherUserId).orElseThrow { UserNotFoundException() }
        val friendship = friendshipRepository.findBetweenUsersWithStatuses(
            currentUserId,
            otherUserId,
            listOf(FriendshipStatus.PENDING, FriendshipStatus.ACCEPTED),
        )
        return when {
            friendship == null -> FriendshipStatusResponse(
                userId = otherUserId,
                status = FriendshipRelationStatus.NONE,
            )
            friendship.status == FriendshipStatus.ACCEPTED -> FriendshipStatusResponse(
                userId = otherUserId,
                status = FriendshipRelationStatus.FRIENDS,
                friendshipId = friendship.id,
            )
            friendship.requesterId == currentUserId -> FriendshipStatusResponse(
                userId = otherUserId,
                status = FriendshipRelationStatus.PENDING_OUT,
                friendshipId = friendship.id,
            )
            else -> FriendshipStatusResponse(
                userId = otherUserId,
                status = FriendshipRelationStatus.PENDING_IN,
                friendshipId = friendship.id,
            )
        }
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
