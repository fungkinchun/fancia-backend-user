package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.ChatChannelResponse
import com.fancia.backend.shared.user.core.dto.ChatTokenResponse
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.exception.ChatAccessDeniedException
import com.fancia.backend.shared.user.core.exception.ChatNotConfiguredException
import com.fancia.backend.shared.user.core.exception.SmartMatchSelfMatchException
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import io.getstream.chat.java.models.Channel
import io.getstream.chat.java.models.Channel.ChannelMemberRequestObject
import io.getstream.chat.java.models.Channel.ChannelRequestObject
import io.getstream.chat.java.models.User as StreamUser
import io.getstream.chat.java.models.User.UserRequestObject
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ChatService(
    private val streamChatProperties: StreamChatProperties,
    private val userRepository: UserRepository,
    private val smartMatchRepository: SmartMatchRepository,
) {
    fun createToken(jwt: Jwt): ChatTokenResponse {
        requireEnabled()
        val currentUserId = currentUserId(jwt)
        val user = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        upsertStreamUser(user)
        val token = StreamUser.createToken(currentUserId.toString(), null, null)
        return ChatTokenResponse(
            apiKey = streamChatProperties.apiKey,
            token = token,
            userId = currentUserId.toString(),
        )
    }

    fun getOrCreateChannel(jwt: Jwt, otherUserId: UUID): ChatChannelResponse {
        requireEnabled()
        val currentUserId = currentUserId(jwt)
        if (currentUserId == otherUserId) {
            throw SmartMatchSelfMatchException()
        }
        if (!canMessage(currentUserId, otherUserId)) {
            throw ChatAccessDeniedException()
        }
        val currentUser = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        val otherUser = userRepository.findById(otherUserId).orElseThrow { UserNotFoundException() }
        upsertStreamUser(currentUser)
        upsertStreamUser(otherUser)

        val channelId = dmChannelId(currentUserId, otherUserId)
        Channel.getOrCreate(CHANNEL_TYPE, channelId)
            .data(
                ChannelRequestObject.builder()
                    .createdBy(UserRequestObject.builder().id(currentUserId.toString()).build())
                    .member(ChannelMemberRequestObject.builder().userId(currentUserId.toString()).build())
                    .member(ChannelMemberRequestObject.builder().userId(otherUserId.toString()).build())
                    .build(),
            )
            .request()

        return ChatChannelResponse(type = CHANNEL_TYPE, channelId = channelId)
    }

    fun canMessage(currentUserId: UUID, otherUserId: UUID): Boolean {
        if (currentUserId == otherUserId) return false
        return smartMatchRepository.findEitherLikedRowsForUser(currentUserId)
            .any { row -> matchesOtherUser(row, currentUserId, otherUserId) && currentUserHasNotPassed(row, currentUserId) }
    }

    private fun matchesOtherUser(row: SmartMatch, currentUserId: UUID, otherUserId: UUID): Boolean {
        val other = when (currentUserId) {
            row.userId -> row.targetId
            row.targetId -> row.userId
            else -> null
        }
        if (other != otherUserId) return false
        return row.userIdFlag == true || row.targetIdFlag == true
    }

    private fun currentUserHasNotPassed(row: SmartMatch, currentUserId: UUID): Boolean =
        when (currentUserId) {
            row.userId -> row.userIdFlag != false
            row.targetId -> row.targetIdFlag != false
            else -> false
        }

    private fun upsertStreamUser(user: User) {
        val id = user.id?.toString() ?: return
        val name = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .ifBlank { user.email ?: "Fancia member" }
        val builder = UserRequestObject.builder()
            .id(id)
            .name(name)
        user.profileImageUrl?.takeIf { it.isNotBlank() }?.let { builder.image(it) }
        StreamUser.upsert().user(builder.build()).request()
    }

    private fun requireEnabled() {
        if (!streamChatProperties.enabled) {
            throw ChatNotConfiguredException()
        }
    }

    private fun currentUserId(jwt: Jwt): UUID =
        jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

    companion object {
        private const val CHANNEL_TYPE = "messaging"

        fun dmChannelId(first: UUID, second: UUID): String {
            val (a, b) = listOf(first, second).sortedBy { it.toString() }
            return "dm-$a-$b"
        }
    }
}
