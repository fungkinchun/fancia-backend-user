package com.fancia.backend.user.core.service

import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.AccountStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.user.config.ApplicationProperties
import com.fancia.backend.user.core.repository.BlockedResourceRepository
import com.fancia.backend.user.core.repository.ChatChannelRepository
import com.fancia.backend.user.core.repository.ConnectedAccountRepository
import com.fancia.backend.user.core.repository.FriendshipRepository
import com.fancia.backend.user.core.repository.PasswordResetTokenRepository
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.repository.VerificationCodeRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserErasureService(
    private val userRepository: UserRepository,
    private val friendshipRepository: FriendshipRepository,
    private val smartMatchRepository: SmartMatchRepository,
    private val chatChannelRepository: ChatChannelRepository,
    private val blockedResourceRepository: BlockedResourceRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val connectedAccountRepository: ConnectedAccountRepository,
    private val fileStorageService: FileStorageService,
    private val applicationProperties: ApplicationProperties,
    private val chatService: ChatService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun erase(user: User) {
        val userId = user.id ?: return

        chatService.deleteStreamUser(userId)
        deleteProfileImage(user)

        friendshipRepository.deleteAll(friendshipRepository.findAllForUser(userId))
        smartMatchRepository.deleteAll(smartMatchRepository.findAllForUser(userId))
        chatChannelRepository.deleteAll(chatChannelRepository.findAllForUser(userId))
        blockedResourceRepository.deleteAll(blockedResourceRepository.findAllByIdUserId(userId))
        passwordResetTokenRepository.deleteAll(passwordResetTokenRepository.findAllByUserId(userId))
        connectedAccountRepository.deleteAll(connectedAccountRepository.findAllByUserId(userId))
        verificationCodeRepository.findByUserId(userId)?.let { verificationCodeRepository.delete(it) }

        anonymise(user, userId)
    }

    private fun anonymise(user: User, userId: UUID) {
        user.email = "$ERASED_EMAIL_PREFIX$userId@$ERASED_EMAIL_DOMAIN"
        user.setPassword("")
        user.firstName = ERASED_NAME
        user.lastName = ""
        user.profileImageUrl = null
        user.bio = null
        user.locationLabel = null
        user.birthDate = null
        user.gender = null
        user.status = AccountStatus.INACTIVE
        user.visibility = ProfileVisibility.PRIVATE
        user.premiumActive = false
        user.premiumExpiresAt = null
        user.tags.clear()
        user.links.clear()
        user.settings = null

        userRepository.saveAndFlush(user)
    }

    private fun deleteProfileImage(user: User) {
        val objectKey = user.profileImageUrl?.let(::toObjectKey) ?: return
        runCatching { fileStorageService.deleteFile(objectKey) }
            .onFailure { log.error("Failed to delete profile image {}", objectKey, it) }
    }

    private fun toObjectKey(profileImageUrl: String): String? {
        val prefix = applicationProperties.cdnUrl.orEmpty().trimEnd('/')
        if (prefix.isEmpty() || !profileImageUrl.startsWith("$prefix/")) return null
        return profileImageUrl.removePrefix("$prefix/").ifBlank { null }
    }

    companion object {
        private const val ERASED_EMAIL_PREFIX = "erased-"
        private const val ERASED_EMAIL_DOMAIN = "users.fancia.invalid"
        private const val ERASED_NAME = "Deleted user"
    }
}
