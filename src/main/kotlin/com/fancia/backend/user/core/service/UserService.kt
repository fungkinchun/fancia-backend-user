package com.fancia.backend.user.core.service

import tools.jackson.core.type.TypeReference
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagItemRequest
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.shared.upload.storage.core.enums.UploadScope
import com.fancia.backend.shared.upload.storage.core.service.FileStorageService
import com.fancia.backend.shared.upload.storage.core.service.moveTmpToDedicatedPath
import com.fancia.backend.shared.user.core.dto.CreateUserRequest
import com.fancia.backend.shared.user.core.dto.ProfileResponse
import com.fancia.backend.shared.user.core.dto.SmartMatchPersonResponse
import com.fancia.backend.shared.user.core.dto.UpdatePremiumStatusRequest
import com.fancia.backend.shared.user.core.dto.UpdateUserPasswordRequest
import com.fancia.backend.shared.user.core.dto.UpdateUserRequest
import com.fancia.backend.shared.user.core.dto.UpdateUserSettingsRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.shared.user.core.entity.PasswordResetToken
import com.fancia.backend.shared.user.core.entity.SmartMatch
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.entity.UserSettings
import com.fancia.backend.shared.user.core.entity.VerificationCode
import com.fancia.backend.shared.user.core.enums.AccountStatus
import com.fancia.backend.shared.user.core.exception.*
import com.fancia.backend.shared.user.core.support.hasPremiumAccess
import com.fancia.backend.shared.user.core.support.PremiumLimits
import com.fancia.backend.shared.user.core.support.redactForPublicView
import com.fancia.backend.shared.user.core.support.smartMatchEligible
import com.fancia.backend.user.config.ApplicationProperties
import com.fancia.backend.shared.common.redis.CachedPage
import com.fancia.backend.shared.common.redis.RedisQueryCache
import com.fancia.backend.user.core.event.PasswordResetTokenCreatedEvent
import com.fancia.backend.user.core.event.UserCreatedEvent
import com.fancia.backend.user.core.repository.PasswordResetTokenRepository
import com.fancia.backend.user.mapper.toProfileResponse
import com.fancia.backend.user.mapper.toSmartMatchPerson
import com.fancia.backend.user.core.repository.SmartMatchRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.repository.VerificationCodeRepository
import com.fancia.backend.user.core.support.UserSlugService
import com.fancia.backend.user.external.CommonServiceClient
import com.fancia.backend.user.external.InterestGroupServiceClient
import com.fancia.backend.user.external.NotificationInternalClient
import com.fancia.backend.shared.notification.core.dto.SendPasswordResetEmailRequest
import com.fancia.backend.shared.notification.core.dto.SendWelcomeEmailRequest
import com.fancia.backend.user.mapper.toDto
import com.fancia.backend.user.mapper.toEntity
import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Duration
import java.util.*

@Service
class UserService(
    private val userRepository: UserRepository,
    private val verificationCodeRepository: VerificationCodeRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val fileUploadService: FileStorageService,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val interestGroupServiceClient: InterestGroupServiceClient,
    private val commonServiceClient: CommonServiceClient,
    private val notificationInternalClient: NotificationInternalClient,
    private val applicationProperties: ApplicationProperties,
    private val smartMatchRepository: SmartMatchRepository,
    private val userErasureService: UserErasureService,
    private val userSlugService: UserSlugService,
    private val redisQueryCache: ObjectProvider<RedisQueryCache>,
    @Value("\${DOMAIN_NAME}") private val domainName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    fun findByEmail(email: String): ProfileResponse {
        val user = userRepository.findByEmail(email)
            ?: throw UserWithEmailNotFoundException(email)

        return user.toProfileResponse()
    }

    fun getCurrentUser(jwt: Jwt): UserResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val user = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        return user.toDto()
    }

    fun findById(id: UUID): ProfileResponse? {
        val cache = redisQueryCache.ifAvailable
            ?: return userRepository.findById(id).orElse(null)?.toProfileResponse()
        val boxed = cache.getOrLoad(
            profileKey(id),
            PROFILE_TTL,
            object : TypeReference<CachedProfile>() {},
        ) {
            CachedProfile(userRepository.findById(id).orElse(null)?.toProfileResponse())
        }
        return boxed.value
    }

    fun getUserResponseById(id: UUID): UserResponse {
        val cache = redisQueryCache.ifAvailable
            ?: return userRepository.findById(id).orElseThrow { UserWithIdNotFoundException(id.toString()) }.toDto()
        return cache.getOrLoad(
            userResponseKey(id),
            PROFILE_TTL,
            object : TypeReference<UserResponse>() {},
        ) {
            userRepository.findById(id).orElseThrow { UserWithIdNotFoundException(id.toString()) }.toDto()
        }
    }

    fun findByIdOrSlug(ref: String): ProfileResponse? {
        val user = userSlugService.resolveUser(ref) ?: return null
        return user.toProfileResponse()
    }

    fun isHandleAvailable(handle: String, jwt: Jwt?): Boolean {
        val excludeUserId = jwt?.getClaimAsString("userId")?.let { UUID.fromString(it) }
        return userSlugService.isHandleAvailable(handle, excludeUserId)
    }

    @Transactional
    fun updatePremiumStatus(id: UUID, request: UpdatePremiumStatusRequest): UserResponse {
        val user = userRepository.findById(id).orElseThrow { UserWithIdNotFoundException(id.toString()) }
        user.premiumActive = request.premiumActive
        user.premiumExpiresAt = request.premiumExpiresAt
        val saved = userRepository.save(user).toDto()
        invalidateUserCaches(id)
        return saved
    }

    @Transactional
    fun create(request: @Valid CreateUserRequest): UserResponse {
        val user = request.toEntity()
        user.setPassword(
            passwordEncoder.encode(user.password) ?: throw InvalidPasswordException()
        )
        userSlugService.assignDefaultSlugIfMissing(user)
        userRepository.save(user)
        verificationCodeRepository.save(VerificationCode(user))
        user.id?.let {
            applicationEventPublisher.publishEvent(UserCreatedEvent(it))
        }
        return user.toDto()
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendVerificationEmail(event: UserCreatedEvent) {
        val user =
            userRepository.findById(event.id).orElseThrow { UserWithIdNotFoundException(event.id.toString()) }
        val verificationCode = verificationCodeRepository.findByUserId(event.id) ?: return
        if (verificationCode.emailSent) return
        val email = user.email ?: return
        try {
            notificationInternalClient.sendWelcomeEmail(
                SendWelcomeEmailRequest(
                    to = email,
                    firstName = user.firstName?.takeIf { it.isNotBlank() } ?: "there",
                    verificationLink = "https://api.${domainName.trimEnd('/')}/user/api/users/verify-email?token=${verificationCode.code}",
                ),
            )
            verificationCode.emailSent = true
            verificationCodeRepository.save(verificationCode)
        } catch (ex: Exception) {
            log.warn("Failed to send welcome email for user {}", event.id, ex)
        }
    }

    @Transactional
    fun verifyEmail(code: String) {
        val verificationCode = verificationCodeRepository.findByCode(code)
            ?: throw InvalidVerificationCodeException()
        verificationCode.user?.apply {
            status = AccountStatus.ACTIVE
            userRepository.save(this)
        }
        verificationCodeRepository.delete(verificationCode)
    }

    @Transactional
    fun forgotPassword(email: String) {
        val user = userRepository.findByEmail(email)
            ?: throw UserWithEmailNotFoundException(email)
        val passwordResetToken = PasswordResetToken(user)
        passwordResetTokenRepository.save(passwordResetToken)
        passwordResetToken.id?.let {
            applicationEventPublisher.publishEvent(PasswordResetTokenCreatedEvent(it))
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun sendResetPasswordEmail(event: PasswordResetTokenCreatedEvent) {
        val passwordResetToken =
            passwordResetTokenRepository.findById(event.id)
                .orElseThrow { PasswordResetTokenNotFoundException(event.id.toString()) }
        if (passwordResetToken.emailSent) return
        val user = passwordResetToken.user ?: return
        val email = user.email ?: return
        try {
            notificationInternalClient.sendPasswordResetEmail(
                SendPasswordResetEmailRequest(
                    to = email,
                    firstName = user.firstName,
                    resetLink = "${siteUrl()}/reset-password?token=${passwordResetToken.token}",
                ),
            )
            passwordResetToken.onEmailSent()
            passwordResetTokenRepository.save(passwordResetToken)
        } catch (ex: Exception) {
            log.warn("Failed to send password reset email for token {}", event.id, ex)
        }
    }

    @Transactional
    fun resetPassword(request: UpdateUserPasswordRequest) {
        request.passwordResetToken?.let {
            val passwordResetToken = passwordResetTokenRepository.findByToken(it)
                ?: throw PasswordResetTokenNotFoundException()

            if (passwordResetToken.isExpired) {
                throw PasswordResetTokenExpiredException()
            }

            passwordResetToken.user?.let { user ->
                passwordEncoder.encode(request.password)?.let {
                    user.setPassword(it)
                    userRepository.save(user)
                    passwordResetTokenRepository.delete(passwordResetToken)
                } ?: throw InvalidPasswordResetTokenException()
            } ?: throw UserNotFoundException()
        } ?: throw InvalidPasswordResetTokenException()
    }

    @Transactional
    fun update(request: UpdateUserRequest, jwt: Jwt): UserResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val user = userRepository.findById(currentUserId).orElseThrow()
        return request.toEntity(user).let {
            request.tags?.let { tags -> applyTags(it.tags, tags) }
            applyDeviceSettings(it, request)
            when (val profileImageKey = request.profileImageKey) {
                null -> Unit
                "" -> it.profileImageUrl = null
                else -> {
                    val destinationPath = fileUploadService.moveTmpToDedicatedPath(
                        profileImageKey,
                        UploadScope.USER,
                        currentUserId,
                    )
                    it.profileImageUrl = "${applicationProperties.cdnUrl.orEmpty().trimEnd('/')}/$destinationPath"
                }
            }
            userRepository.save(it).toDto()
        }.also { invalidateUserCaches(currentUserId) }
    }

    @Transactional
    fun updateSettings(request: UpdateUserSettingsRequest, jwt: Jwt): UserResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val user = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        val settings = ensureUserSettings(user)
        request.toEntity(user, settings)
        request.slug?.let { userSlugService.applySlugChange(user, it) }
        return userRepository.save(user).toDto().also { invalidateUserCaches(currentUserId) }
    }

    @Transactional
    fun removeTagFromAllUsers(tagId: UUID) {
        val usersWithTag = userRepository.findByTagId(tagId)
        for (user in usersWithTag) {
            user.tags.remove(tagId)
        }
        if (usersWithTag.isNotEmpty()) {
            userRepository.saveAll(usersWithTag)
            usersWithTag.mapNotNull { it.id }.forEach { invalidateUserCaches(it) }
        }
    }

    private fun ensureUserSettings(user: User): UserSettings {
        val existing = user.settings
        if (existing != null) {
            return existing
        }
        val settings = UserSettings().apply {
            userId = user.id
            this.user = user
        }
        user.settings = settings
        return settings
    }

    @Transactional
    fun updatePassword(request: UpdateUserPasswordRequest, jwt: Jwt): UserResponse {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val user = userRepository.findById(currentUserId)
            .orElseThrow { UserNotFoundException() }

        if (user.password != null && !passwordEncoder.matches(request.oldPassword, user.password)) {
            throw InvalidAuthenticationException()
        }
        passwordEncoder.encode(request.password)?.let {
            user.setPassword(it)
        }
        return userRepository.save(user).toDto()
    }

    @Transactional
    fun deleteUser(jwt: Jwt, forceDeleted: Boolean = false): UUID {
        val requestId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val user = userRepository.findById(requestId)
            .orElseThrow { UserNotFoundException() }
        val memberships = interestGroupServiceClient.getInterestGroupMembership(requestId, InterestGroupRole.ADMIN)
        if (!forceDeleted) {
            if (!memberships.isEmpty) throw UserIsStillGroupAdminException(
                requestId.toString(),
                groupIds = memberships.content.map { it.interestGroupId.toString() }
            )
        }
        userErasureService.erase(user)
        userRepository.delete(user)
        invalidateUserCaches(requestId)
        invalidateSmartMatchDeck(requestId)
        return requestId
    }

    fun smartMatch(jwt: Jwt, pageable: Pageable): Page<SmartMatchPersonResponse> {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        val currentUser = userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        if (!currentUser.smartMatchEligible()) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val isPremium = hasPremiumAccess(jwt, currentUser)
        val cache = redisQueryCache.ifAvailable
        if (cache != null) {
            val key = "$DECK_PREFIX$currentUserId:$isPremium:${pageable.pageNumber}:${pageable.pageSize}"
            val cached = cache.getOrLoad(
                key,
                DECK_TTL,
                object : TypeReference<CachedPage<SmartMatchPersonResponse>>() {},
            ) {
                CachedPage.from(loadSmartMatchDeck(currentUserId, isPremium, pageable))
            }
            return cached.toPage(pageable)
        }
        return loadSmartMatchDeck(currentUserId, isPremium, pageable)
    }

    private fun loadSmartMatchDeck(
        currentUserId: UUID,
        isPremium: Boolean,
        pageable: Pageable,
    ): Page<SmartMatchPersonResponse> {
        val batchRows = smartMatchRepository.findByFirstUserIdAndFirstUserLikedIsNullOrderByRankAsc(currentUserId)
        if (batchRows.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val alreadyMatchedIds = otherUserIds(
            smartMatchRepository.findEitherLikedRowsForUser(currentUserId),
            currentUserId,
        )
        val scoreByUserId = batchRows
            .mapNotNull { row ->
                val id = row.secondUserId ?: return@mapNotNull null
                val score = row.score?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
                id to score
            }
            .toMap()
        val targetIds = batchRows.mapNotNull { it.secondUserId }
            .filterNot { it in alreadyMatchedIds }
        val usersById = userRepository.findAllById(targetIds)
            .filter { it.id != null && it.smartMatchEligible() }
            .associateBy { it.id!! }
        val deckLimit = PremiumLimits.smartMatchDeckSize(isPremium)
        val ordered = targetIds.mapNotNull { usersById[it] }.take(deckLimit)
        val pageContent = ordered
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
            .map { user ->
                user.toSmartMatchPerson(score = user.id?.let { scoreByUserId[it] })
            }
        return PageImpl(pageContent, pageable, ordered.size.toLong())
    }

    fun listMutualSmartMatches(jwt: Jwt, pageable: Pageable): Page<SmartMatchPersonResponse> {
        val currentUserId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
        userRepository.findById(currentUserId).orElseThrow { UserNotFoundException() }
        val rows = smartMatchRepository.findLikedConnectionsForUser(currentUserId)
            .sortedWith(
                compareByDescending<SmartMatch> { row ->
                    row.mutualLike()
                }.thenByDescending { row ->
                    listOfNotNull(row.firstUserLikedAt, row.secondUserLikedAt).maxOrNull()
                },
            )
        val otherIds = otherUserIds(rows, currentUserId)
        if (otherIds.isEmpty()) {
            return PageImpl(emptyList(), pageable, 0)
        }
        val mutualByOtherId = rows.mapNotNull { row ->
            row.otherUserId(currentUserId)?.let { otherId -> otherId to row.mutualLike() }
        }.toMap()
        val scoreByOtherId = rows.mapNotNull { row ->
            val otherId = row.otherUserId(currentUserId) ?: return@mapNotNull null
            val score = row.score?.takeIf { it.isFinite() && it > 0.0 } ?: return@mapNotNull null
            otherId to score
        }.toMap()
        val icebreakersByOtherId = rows.mapNotNull { row ->
            val otherId = row.otherUserId(currentUserId) ?: return@mapNotNull null
            if (!row.mutualLike()) return@mapNotNull null
            otherId to row.icebreakerEventResponses()
        }.toMap()
        val usersById = userRepository.findAllById(otherIds)
            .mapNotNull { user -> user.id?.let { id -> id to user } }
            .toMap()
        val ordered = otherIds.mapNotNull { usersById[it] }
        val pageContent = ordered
            .drop(pageable.offset.toInt())
            .take(pageable.pageSize)
            .map { user ->
                val mutual = user.id?.let { mutualByOtherId[it] } == true
                val score = user.id?.let { scoreByOtherId[it] }
                val icebreakers = if (mutual) {
                    user.id?.let { icebreakersByOtherId[it] }.orEmpty()
                } else {
                    emptyList()
                }
                user.toSmartMatchPerson(
                    mutualMatch = mutual,
                    score = score,
                    icebreakerEvents = icebreakers,
                )
            }
        return PageImpl(pageContent, pageable, ordered.size.toLong())
    }

    private fun applyDeviceSettings(user: User, request: UpdateUserRequest) {
        if (request.fcmToken == null && request.deviceType == null && request.deviceId == null) {
            return
        }
        val settings = ensureUserSettings(user)
        val current = settings.notifications
        settings.notifications = current.copy(
            fcmToken = request.fcmToken ?: current.fcmToken,
            deviceType = request.deviceType ?: current.deviceType,
            deviceId = request.deviceId ?: current.deviceId,
        )
    }

    private fun applyTags(tags: MutableSet<UUID>, requestTags: Set<TagItemRequest>) {
        tags.clear()
        if (requestTags.isEmpty()) return
        val resolved = commonServiceClient.createTags(
            CreateTagsRequest(tags = requestTags.toList()),
            size = requestTags.size,
        ).content.mapNotNull { it.id }
        tags.addAll(resolved)
    }

    private fun otherUserIds(rows: List<SmartMatch>, currentUserId: UUID): List<UUID> =
        rows.mapNotNull { row -> row.otherUserId(currentUserId) }.distinct()

    private fun profileKey(id: UUID) = "$PROFILE_PREFIX$id"
    private fun userResponseKey(id: UUID) = "$USER_RESPONSE_PREFIX$id"

    fun invalidateUserCaches(userId: UUID) {
        val cache = redisQueryCache.ifAvailable ?: return
        cache.evict(profileKey(userId))
        cache.evict(userResponseKey(userId))
        invalidateSmartMatchDeck(userId)
    }

    fun invalidateSmartMatchDeck(userId: UUID) {
        redisQueryCache.ifAvailable?.evictByPrefix("$DECK_PREFIX$userId:")
    }

    private fun siteUrl(): String = "https://${domainName.trimEnd('/')}"

    private data class CachedProfile(val value: ProfileResponse? = null)

    companion object {
        private const val PROFILE_PREFIX = "user:profile:"
        private const val USER_RESPONSE_PREFIX = "user:response:"
        private const val DECK_PREFIX = "user:smartmatch:deck:"
        private val PROFILE_TTL = Duration.ofMinutes(3)
        private val DECK_TTL = Duration.ofMinutes(5)
    }
}