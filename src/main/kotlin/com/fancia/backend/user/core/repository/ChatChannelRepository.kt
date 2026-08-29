package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.ChatChannel
import com.fancia.backend.shared.user.core.enums.ChatChannelKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChatChannelRepository : JpaRepository<ChatChannel, UUID> {
    fun findByKindAndFirstUserIdAndSecondUserId(
        kind: ChatChannelKind,
        firstUserId: UUID,
        secondUserId: UUID,
    ): ChatChannel?

    fun findByKindAndInterestGroupIdAndInitiatorUserId(
        kind: ChatChannelKind,
        interestGroupId: UUID,
        initiatorUserId: UUID,
    ): ChatChannel?

    fun findByKindAndInitiatorUserId(
        kind: ChatChannelKind,
        initiatorUserId: UUID,
    ): ChatChannel?

    @Query(
        """
        SELECT c FROM ChatChannel c
        WHERE c.firstUserId = :userId
           OR c.secondUserId = :userId
           OR c.initiatorUserId = :userId
        """,
    )
    fun findAllForUser(@Param("userId") userId: UUID): List<ChatChannel>
}
