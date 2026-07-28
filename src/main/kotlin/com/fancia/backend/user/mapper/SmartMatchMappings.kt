package com.fancia.backend.user.mapper

import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.entity.SmartMatch

fun SmartMatch.toDto(): SmartMatchResponse =
    SmartMatchResponse(
        id = id!!,
        userId = userId!!,
        createdBy = createdBy,
        matchedByUser = matchedByUser,
        matchedByCreatedBy = matchedByCreatedBy,
        createdAt = createdAt,
    )
