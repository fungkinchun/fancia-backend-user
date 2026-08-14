package com.fancia.backend.user.mapper

import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.entity.SmartMatch

fun SmartMatch.toDto(): SmartMatchResponse =
    SmartMatchResponse(
        id = this.id!!,
        firstUserId = this.firstUserId!!,
        secondUserId = this.secondUserId!!,
        firstUserLiked = this.firstUserLiked,
        secondUserLiked = this.secondUserLiked,
        firstUserLikedAt = this.firstUserLikedAt,
        secondUserLikedAt = this.secondUserLikedAt,
        rank = this.rank,
        score = this.score,
        createdAt = this.createdAt,
    )
