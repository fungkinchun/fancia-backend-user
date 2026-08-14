package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.ChatDirectMessageChannel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ChatDirectMessageChannelRepository : JpaRepository<ChatDirectMessageChannel, UUID> {
    fun findByFirstUserIdAndSecondUserId(firstUserId: UUID, secondUserId: UUID): ChatDirectMessageChannel?
}
