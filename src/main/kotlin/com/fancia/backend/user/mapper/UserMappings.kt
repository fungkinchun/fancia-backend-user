package com.fancia.backend.user.mapper

import com.fancia.backend.shared.common.social.core.dto.LinkResponse
import com.fancia.backend.shared.common.social.core.entity.Link
import com.fancia.backend.shared.user.core.dto.*
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import com.fancia.backend.shared.user.core.entity.UserSettings

fun User.toDto(): UserResponse =
    UserResponse(
        id = id,
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
        connectedAccounts = connectedAccounts.mapNotNull { it?.toDto() }.toMutableList(),
        authorities = authorities.mapNotNull { it.authority }.toMutableList(),
        links = links.map { it.toDto() }.toSet(),
        premiumActive = premiumActive,
        premiumExpiresAt = premiumExpiresAt,
    )

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
