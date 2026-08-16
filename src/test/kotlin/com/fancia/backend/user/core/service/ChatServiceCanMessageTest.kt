package com.fancia.backend.user.core.service

import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.FriendshipStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.user.config.StreamChatProperties
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.FriendshipRepository
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.external.InterestGroupServiceClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.mockito.Mockito
import java.util.Optional
import java.util.UUID

class ChatServiceCanMessageTest : FunSpec({
    val streamChatProperties = StreamChatProperties().apply { enabled = false }
    val userRepository = Mockito.mock(UserRepository::class.java)
    val smartMatchRepository = Mockito.mock(SmartMatchRepository::class.java)
    val friendshipRepository = Mockito.mock(FriendshipRepository::class.java)
    val chatChannelRepository = Mockito.mock(ChatChannelRepository::class.java)
    val interestGroupServiceClient = Mockito.mock(InterestGroupServiceClient::class.java)

    val chatService = ChatService(
        streamChatProperties = streamChatProperties,
        userRepository = userRepository,
        smartMatchRepository = smartMatchRepository,
        friendshipRepository = friendshipRepository,
        chatChannelRepository = chatChannelRepository,
        interestGroupServiceClient = interestGroupServiceClient,
    )

    val currentUserId = UUID.randomUUID()
    val otherUserId = UUID.randomUUID()

    beforeEach {
        Mockito.reset(
            userRepository,
            smartMatchRepository,
            friendshipRepository,
            chatChannelRepository,
            interestGroupServiceClient,
        )
        Mockito.`when`(smartMatchRepository.findEitherLikedRowsForUser(currentUserId))
            .thenReturn(emptyList())
        Mockito.`when`(
            friendshipRepository.existsBetweenUsersWithStatus(
                currentUserId,
                otherUserId,
                FriendshipStatus.ACCEPTED,
            ),
        ).thenReturn(false)
    }

    test("denies messaging yourself") {
        chatService.canMessage(currentUserId, currentUserId) shouldBe false
    }

    test("allows messaging when Smart Match either-liked and caller has not passed") {
        val row = SmartMatch().apply {
            firstUserId = currentUserId
            secondUserId = otherUserId
            firstUserLiked = true
            secondUserLiked = null
        }
        Mockito.`when`(smartMatchRepository.findEitherLikedRowsForUser(currentUserId))
            .thenReturn(listOf(row))

        chatService.canMessage(currentUserId, otherUserId) shouldBe true
    }

    test("allows messaging accepted friends") {
        Mockito.`when`(
            friendshipRepository.existsBetweenUsersWithStatus(
                currentUserId,
                otherUserId,
                FriendshipStatus.ACCEPTED,
            ),
        ).thenReturn(true)

        chatService.canMessage(currentUserId, otherUserId) shouldBe true
    }

    test("allows messaging public profiles") {
        val other = User().apply {
            id = otherUserId
            visibility = ProfileVisibility.PUBLIC
        }
        Mockito.`when`(userRepository.findById(otherUserId)).thenReturn(Optional.of(other))

        chatService.canMessage(currentUserId, otherUserId) shouldBe true
    }

    test("denies messaging private strangers without match or friendship") {
        val other = User().apply {
            id = otherUserId
            visibility = ProfileVisibility.PRIVATE
        }
        Mockito.`when`(userRepository.findById(otherUserId)).thenReturn(Optional.of(other))

        chatService.canMessage(currentUserId, otherUserId) shouldBe false
    }
})
