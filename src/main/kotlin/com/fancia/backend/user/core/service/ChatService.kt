package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.DomainException
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.interestgroup.core.exception.InterestGroupNotFoundException
import com.fancia.backend.shared.user.core.dto.ChatChannelResponse
import com.fancia.backend.shared.user.core.dto.ChatTokenResponse
import com.fancia.backend.shared.user.core.entity.ChatChannel
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.ChatChannelKind
import com.fancia.backend.shared.user.core.enums.FriendshipStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.shared.user.core.exception.ChatAccessDeniedException
import com.fancia.backend.shared.user.core.exception.ChatChannelException
import com.fancia.backend.shared.user.core.exception.ChatNotConfiguredException
import com.fancia.backend.shared.user.core.exception.SmartMatchSelfMatchException
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.FriendshipRepository
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.InterestGroupServiceClient
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import feign.FeignException
import io.getstream.chat.java.models.Channel
import io.getstream.chat.java.models.Channel.ChannelMemberRequestObject
import io.getstream.chat.java.models.Channel.ChannelRequestObject
import io.getstream.chat.java.models.User.UserRequestObject
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID
import io.getstream.chat.java.models.User as StreamUser

@Service
class ChatService(
    private val streamChatProperties: StreamChatProperties,
    private val userRepository: UserRepository,
    private val smartMatchRepository: SmartMatchRepository,
    private val friendshipRepository: FriendshipRepository,
    private val chatChannelRepository: ChatChannelRepository,
    private val interestGroupServiceClient: InterestGroupServiceClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun createToken(jwt: Jwt): ChatTokenResponse {
        requireEnabled()
        val currentUserId = currentUserId(jwt)
        val user = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        upsertStreamUser(user)
        syncMessageableChannels(currentUserId)
        val token = StreamUser.createToken(currentUserId.toString(), null, null)
        return ChatTokenResponse(
            apiKey = streamChatProperties.apiKey,
            token = token,
            userId = currentUserId.toString(),
        )
    }

    @Transactional
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

        return try {
            upsertStreamUser(currentUser)
            upsertStreamUser(otherUser)
            val channelId = createDirectMessageChannel(currentUserId, otherUserId)
            ChatChannelResponse(type = CHANNEL_TYPE, channelId = channelId)
        } catch (ex: DomainException) {
            throw ex
        } catch (ex: Exception) {
            log.error(
                "Failed to create Stream channel between {} and {}",
                currentUserId,
                otherUserId,
                ex,
            )
            throw ChatChannelException(message = ex.message ?: "Stream Chat error")
        }
    }

    @Transactional
    fun getOrCreateGroupInquiryChannel(jwt: Jwt, interestGroupId: UUID): ChatChannelResponse {
        requireEnabled()
        val currentUserId = currentUserId(jwt)
        userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }

        val memberships = try {
            interestGroupServiceClient.listMembershipsInGroup(interestGroupId, InterestGroupRole.ADMIN)
        } catch (ex: FeignException.NotFound) {
            throw InterestGroupNotFoundException(interestGroupId)
        } catch (ex: FeignException) {
            if (ex.status() == 400 || ex.status() == 404) {
                throw InterestGroupNotFoundException(interestGroupId)
            }
            log.error("Failed to load memberships for interest group {}", interestGroupId, ex)
            throw ChatChannelException(message = "Could not load interest group memberships")
        }

        val memberIds = (memberships.mapNotNull { it.userId } + currentUserId).distinct()
        val interestGroupName = runCatching {
            interestGroupServiceClient.getInterestGroup(interestGroupId).name
        }.getOrDefault("")

        return try {
            memberIds.forEach { userId ->
                userRepository.findById(userId).orElse(null)?.let { upsertStreamUser(it) }
            }
            val channelId = createGroupInquiryChannel(
                interestGroupId = interestGroupId,
                initiatorUserId = currentUserId,
                memberIds = memberIds,
                interestGroupName = interestGroupName,
            )
            ChatChannelResponse(type = CHANNEL_TYPE, channelId = channelId)
        } catch (ex: DomainException) {
            throw ex
        } catch (ex: Exception) {
            log.error(
                "Failed to create group inquiry channel for group {} by {}",
                interestGroupId,
                currentUserId,
                ex,
            )
            throw ChatChannelException(message = ex.message ?: "Stream Chat error")
        }
    }

    /** Best-effort Stream channel provisioning when Smart Match or friendship connects two users. */
    @Transactional
    fun provisionDirectMessageChannelIfAllowed(firstUserId: UUID, secondUserId: UUID) {
        if (!streamChatProperties.enabled) return
        if (firstUserId == secondUserId) return

        try {
            if (!canMessage(firstUserId, secondUserId)) return
            val firstUser = userRepository.findById(firstUserId).orElse(null) ?: return
            val secondUser = userRepository.findById(secondUserId).orElse(null) ?: return
            upsertStreamUser(firstUser)
            upsertStreamUser(secondUser)
            createDirectMessageChannel(firstUserId, secondUserId)
        } catch (ex: Exception) {
            log.warn(
                "Failed to provision Stream channel between {} and {}: {}",
                firstUserId,
                secondUserId,
                ex.message,
                ex,
            )
        }
    }

    /** Ensures Stream channels exist for Smart Match connections and accepted friends. */
    fun syncMessageableChannels(currentUserId: UUID) {
        if (!streamChatProperties.enabled) return

        messageableOtherUserIds(currentUserId).forEach { otherUserId ->
            provisionDirectMessageChannelIfAllowed(currentUserId, otherUserId)
        }
    }

    /**
     * DM allowed when the pair are distinct and any of:
     * Smart Match either-liked (caller has not passed), ACCEPTED friends, or target profile is PUBLIC.
     */
    fun canMessage(currentUserId: UUID, otherUserId: UUID): Boolean {
        if (currentUserId == otherUserId) return false
        if (smartMatchAllows(currentUserId, otherUserId)) return true
        if (friendshipRepository.existsBetweenUsersWithStatus(
                currentUserId,
                otherUserId,
                FriendshipStatus.ACCEPTED,
            )
        ) {
            return true
        }
        val otherUser = userRepository.findById(otherUserId).orElse(null) ?: return false
        return otherUser.visibility == ProfileVisibility.PUBLIC
    }

    private fun smartMatchAllows(currentUserId: UUID, otherUserId: UUID): Boolean =
        smartMatchRepository.findEitherLikedRowsForUser(currentUserId)
            .any { row ->
                matchesOtherUser(row, currentUserId, otherUserId) && currentUserHasNotPassed(
                    row,
                    currentUserId,
                )
            }

    private fun matchesOtherUser(row: SmartMatch, currentUserId: UUID, otherUserId: UUID): Boolean {
        if (row.otherUserId(currentUserId) != otherUserId) return false
        return row.eitherLiked()
    }

    private fun currentUserHasNotPassed(row: SmartMatch, currentUserId: UUID): Boolean =
        row.hasNotPassed(currentUserId)

    private fun upsertStreamUser(user: User) {
        val id = user.id?.toString() ?: return
        val name = listOfNotNull(user.firstName, user.lastName)
            .joinToString(" ")
            .ifBlank { user.email ?: "Fancia member" }
        val builder = UserRequestObject.builder()
            .id(id)
            .name(name)
        user.profileImageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            builder.additionalField("image", url)
        }
        StreamUser.upsert().user(builder.build()).request()
    }

    private fun createDirectMessageChannel(currentUserId: UUID, otherUserId: UUID): String {
        val channel = resolveDirectMessageChannel(currentUserId, otherUserId)
        Channel.getOrCreate(CHANNEL_TYPE, channel.channelId)
            .data(
                ChannelRequestObject.builder()
                    .createdBy(UserRequestObject.builder().id(currentUserId.toString()).build())
                    .member(ChannelMemberRequestObject.builder().userId(currentUserId.toString()).build())
                    .member(ChannelMemberRequestObject.builder().userId(otherUserId.toString()).build())
                    .additionalField("kind", "dm")
                    .build(),
            )
            .request()
        return channel.channelId
    }

    private fun createGroupInquiryChannel(
        interestGroupId: UUID,
        initiatorUserId: UUID,
        memberIds: List<UUID>,
        interestGroupName: String,
    ): String {
        val channel = resolveGroupInquiryChannel(interestGroupId, initiatorUserId, memberIds)
        val dataBuilder = ChannelRequestObject.builder()
            .createdBy(UserRequestObject.builder().id(initiatorUserId.toString()).build())
            .additionalField("kind", "group_inquiry")
            .additionalField("interestGroupId", interestGroupId.toString())
            .additionalField("interestGroupName", interestGroupName)
        memberIds.forEach { userId ->
            dataBuilder.member(ChannelMemberRequestObject.builder().userId(userId.toString()).build())
        }
        Channel.getOrCreate(CHANNEL_TYPE, channel.channelId)
            .data(dataBuilder.build())
            .request()
        return channel.channelId
    }

    private fun resolveDirectMessageChannel(firstUserId: UUID, secondUserId: UUID): ChatChannel {
        val (first, second) = ChatChannel.canonicalUserPair(firstUserId, secondUserId)
        chatChannelRepository.findByKindAndFirstUserIdAndSecondUserId(ChatChannelKind.DM, first, second)
            ?.let { return it }

        val now = LocalDateTime.now()
        val channel = ChatChannel().apply {
            kind = ChatChannelKind.DM
            this.firstUserId = first
            this.secondUserId = second
            channelId = generateChannelId()
            createdBy = firstUserId
            addMember(first, now)
            addMember(second, now)
        }

        return try {
            chatChannelRepository.save(channel)
        } catch (_: DataIntegrityViolationException) {
            chatChannelRepository.findByKindAndFirstUserIdAndSecondUserId(ChatChannelKind.DM, first, second)
                ?: throw ChatChannelException(message = "Could not allocate a chat channel id")
        }
    }

    private fun resolveGroupInquiryChannel(
        interestGroupId: UUID,
        initiatorUserId: UUID,
        memberIds: List<UUID>,
    ): ChatChannel {
        val existing = chatChannelRepository.findByKindAndInterestGroupIdAndInitiatorUserId(
            ChatChannelKind.GROUP_INQUIRY,
            interestGroupId,
            initiatorUserId,
        )
        val now = LocalDateTime.now()
        if (existing != null) {
            memberIds.forEach { userId -> existing.addMember(userId, now) }
            return chatChannelRepository.save(existing)
        }

        val channel = ChatChannel().apply {
            kind = ChatChannelKind.GROUP_INQUIRY
            this.interestGroupId = interestGroupId
            this.initiatorUserId = initiatorUserId
            channelId = generateChannelId()
            createdBy = initiatorUserId
            memberIds.forEach { userId -> addMember(userId, now) }
        }

        return try {
            chatChannelRepository.save(channel)
        } catch (_: DataIntegrityViolationException) {
            val raced = chatChannelRepository.findByKindAndInterestGroupIdAndInitiatorUserId(
                ChatChannelKind.GROUP_INQUIRY,
                interestGroupId,
                initiatorUserId,
            ) ?: throw ChatChannelException(message = "Could not allocate a group inquiry channel id")
            memberIds.forEach { userId -> raced.addMember(userId, now) }
            chatChannelRepository.save(raced)
        }
    }

    private fun generateChannelId(): String = UUID.randomUUID().toString().replace("-", "")

    private fun messageableOtherUserIds(currentUserId: UUID): Set<UUID> {
        val fromSmartMatch = smartMatchRepository.findEitherLikedRowsForUser(currentUserId)
            .mapNotNull { row -> row.otherUserId(currentUserId)?.takeIf { canMessage(currentUserId, it) } }
        val fromFriends = friendshipRepository.findFriendIdsByStatus(
            currentUserId,
            FriendshipStatus.ACCEPTED,
        )
        return (fromSmartMatch + fromFriends).toSet()
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
    }
}
