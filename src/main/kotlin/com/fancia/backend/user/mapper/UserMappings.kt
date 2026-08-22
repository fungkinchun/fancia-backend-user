package com.fancia.backend.user.mapper

import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.user.core.dto.*
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import com.fancia.backend.shared.user.core.entity.UserSettings
import com.fancia.backend.shared.user.core.enums.ConnectedAccountProvider
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.user.core.support.UserSlugService

fun User.toDto(): UserResponse =
    UserResponse(
        id = id,
        slug = slug,
        slugChangeAllowedAt = slugChangeAllowedAt(),
        role = role,
        firstName = firstName,
        lastName = lastName,
        email = email,
        status = status,
        profileImageUrl = profileImageUrl,
        bio = bio,
        locationLabel = locationLabel,
        birthDate = birthDate,
        gender = gender,
        visibility = visibility,
        tags = tags,
        blacklistedIds = blacklistedIds,
        privacy = settings?.privacy ?: UserPrivacySettings(),
        notifications = settings?.notifications ?: UserNotificationSettings(),
        connectedAccounts = connectedAccounts
            .filterNot { it?.provider.equals(ConnectedAccountProvider.STRIPE.value, ignoreCase = true) }
            .mapNotNull { it?.toDto() }
            .toMutableList(),
        authorities = authorities.mapNotNull { it.authority }.toMutableList(),
        links = links.map { it.toDto() }.toSet(),
        premiumActive = premiumActive,
        premiumExpiresAt = premiumExpiresAt,
    )

fun User.toSmartMatchPerson(
    mutualMatch: Boolean? = null,
    score: Double? = null,
    icebreakerEvents: List<com.fancia.backend.shared.user.core.dto.PairEventIcebreakerResponse> = emptyList(),
): SmartMatchPersonResponse {
    val privacy = settings?.privacy ?: UserPrivacySettings()
    if (visibility == ProfileVisibility.PRIVATE) {
        return SmartMatchPersonResponse(
            id = id,
            firstName = firstName,
            lastName = lastName,
            profileImageUrl = profileImageUrl,
            bio = bio,
            visibility = ProfileVisibility.PRIVATE,
            interestsCount = tags.size,
            score = score,
            mutualMatch = mutualMatch,
            icebreakerEvents = icebreakerEvents,
        )
    }
    return SmartMatchPersonResponse(
        id = id,
        firstName = firstName,
        lastName = lastName,
        profileImageUrl = profileImageUrl,
        bio = bio,
        locationLabel = locationLabel,
        birthDate = if (privacy.showBirthday) birthDate else null,
        gender = if (privacy.showGender) gender else null,
        visibility = visibility,
        tags = if (privacy.showInterests) tags else emptySet(),
        links = links.map { it.toDto() }.toSet(),
        interestsCount = tags.size,
        score = score,
        mutualMatch = mutualMatch,
        icebreakerEvents = icebreakerEvents,
    )
}

fun User.toProfileResponse(): ProfileResponse {
    val privacy = settings?.privacy ?: UserPrivacySettings()
    val slugMeta = slug to slugChangeAllowedAt()
    if (visibility == ProfileVisibility.PRIVATE) {
        return ProfileResponse(
            id = id,
            slug = slugMeta.first,
            slugChangeAllowedAt = slugMeta.second,
            firstName = firstName,
            lastName = lastName,
            profileImageUrl = profileImageUrl,
            bio = bio,
            visibility = ProfileVisibility.PRIVATE,
            interestsCount = null,
            postsCount = null,
            eventsCount = null,
            groupsCount = null,
        )
    }
    return ProfileResponse(
        id = id,
        slug = slugMeta.first,
        slugChangeAllowedAt = slugMeta.second,
        firstName = firstName,
        lastName = lastName,
        profileImageUrl = profileImageUrl,
        bio = bio,
        locationLabel = locationLabel,
        birthDate = if (privacy.showBirthday) birthDate else null,
        gender = if (privacy.showGender) gender else null,
        visibility = visibility,
        tags = if (privacy.showInterests) tags else emptySet(),
        links = links.map { it.toDto() }.toSet(),
        interestsCount = if (privacy.showInterests) tags.size else null,
        eventsCount = if (privacy.showEvents) 0 else null,
        groupsCount = if (privacy.showGroups) 0 else null,
    )
}

fun CreateUserRequest.toEntity(): User =
    User().apply {
        email = this@toEntity.email
        firstName = this@toEntity.firstName
        lastName = this@toEntity.lastName
        setPassword(this@toEntity.password.orEmpty())
    }

fun UpdateUserRequest.toEntity(user: User): User {
    firstName?.takeIf { it.isNotBlank() }?.let { user.firstName = it }
    lastName?.takeIf { it.isNotBlank() }?.let { user.lastName = it }
    bio?.let { user.bio = it.trim().ifBlank { null } }
    locationLabel?.let { user.locationLabel = it.trim().ifBlank { null } }
    birthDate?.let { user.birthDate = it }
    gender?.let { user.gender = it }
    links?.let { linkItems ->
        user.links.clear()
        user.links.addAll(linkItems.map { Link(type = it.type, url = it.url) })
    }
    return user
}

fun UpdateUserSettingsRequest.toEntity(user: User, settings: UserSettings): User {
    visibility?.let { user.visibility = it }
    privacy?.let { incoming ->
        val current = settings.privacy
        settings.privacy = UserPrivacySettings(
            allowFriendRequests = incoming.allowFriendRequests,
            showGroups = incoming.showGroups,
            showInterests = incoming.showInterests,
            showEvents = incoming.showEvents,
            showGender = incoming.showGender,
            showBirthday = incoming.showBirthday,
            smartMatchEnabled = incoming.smartMatchEnabled,
        )
    }
    notifications?.let { incoming ->
        val current = settings.notifications
        settings.notifications = UserNotificationSettings(
            match = incoming.match ?: current.match,
            messages = incoming.messages ?: current.messages,
            postEngagement = incoming.postEngagement ?: current.postEngagement,
            eventRecommendations = incoming.eventRecommendations ?: current.eventRecommendations,
            eventReminders = incoming.eventReminders ?: current.eventReminders,
            fcmToken = incoming.fcmToken ?: current.fcmToken,
            deviceType = incoming.deviceType ?: current.deviceType,
            deviceId = incoming.deviceId ?: current.deviceId,
        )
    }
    return user
}

fun UserResponse.toEntity(): User =
    User().apply {
        id = this@toEntity.id
        email = this@toEntity.email
        firstName = this@toEntity.firstName
        lastName = this@toEntity.lastName
        profileImageUrl = this@toEntity.profileImageUrl
        status = this@toEntity.status
        role = this@toEntity.role
        bio = this@toEntity.bio
        locationLabel = this@toEntity.locationLabel
        birthDate = this@toEntity.birthDate
        gender = this@toEntity.gender
        visibility = this@toEntity.visibility
        tags = this@toEntity.tags.toMutableSet()
        links = this@toEntity.links.map { Link(type = it.type, url = it.url) }.toMutableSet()
    }

private fun UserConnectedAccount.toDto(): ConnectedAccountResponse =
    ConnectedAccountResponse(provider = provider, connectedAt = connectedAt)

private fun Link.toDto(): LinkResponse =
    LinkResponse(type = type, url = url)

private fun User.slugChangeAllowedAt(): java.time.LocalDateTime? {
    val changedAt = slugChangedAt ?: return null
    val nextAllowed = changedAt.plusDays(UserSlugService.COOLDOWN_DAYS)
  return if (java.time.LocalDateTime.now().isBefore(nextAllowed)) nextAllowed else null
}
