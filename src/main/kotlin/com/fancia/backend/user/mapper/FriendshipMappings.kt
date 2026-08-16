package com.fancia.backend.user.mapper

import com.fancia.backend.shared.user.core.dto.FriendshipResponse
import com.fancia.backend.shared.user.core.entity.Friendship

fun Friendship.toDto(): FriendshipResponse =
    FriendshipResponse(
        id = id!!,
        requesterId = requesterId!!,
        addresseeId = addresseeId!!,
        status = status,
        respondedAt = respondedAt,
        createdAt = createdAt,
    )
