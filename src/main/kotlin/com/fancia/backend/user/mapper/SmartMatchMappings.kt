package com.fancia.backend.user.mapper

import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.entity.SmartMatch

fun SmartMatch.toDto(): SmartMatchResponse =
    SmartMatchResponse(
        id = this@toDto.id!!,
        userId = this@toDto.userId!!,
        targetId = this@toDto.targetId!!,
        userIdFlag = this@toDto.userIdFlag,
        targetIdFlag = this@toDto.targetIdFlag,
        userIdFlagAt = this@toDto.userIdFlagAt,
        targetIdFlagAt = this@toDto.targetIdFlagAt,
        rank = this@toDto.rank,
        score = this@toDto.score,
        createdAt = this@toDto.createdAt,
    )
