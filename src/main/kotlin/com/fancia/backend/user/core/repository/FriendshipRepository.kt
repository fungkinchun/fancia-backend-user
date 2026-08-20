package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.Friendship
import com.fancia.backend.shared.user.core.enums.FriendshipStatus
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface FriendshipRepository : JpaRepository<Friendship, UUID> {
    @Query(
        """
        SELECT f FROM Friendship f
        WHERE f.status IN :statuses
          AND (
            (f.requesterId = :userA AND f.addresseeId = :userB)
            OR (f.requesterId = :userB AND f.addresseeId = :userA)
          )
        """,
    )
    fun findBetweenUsersWithStatuses(
        @Param("userA") userA: UUID,
        @Param("userB") userB: UUID,
        @Param("statuses") statuses: Collection<FriendshipStatus>,
    ): Friendship?

    @Query("SELECT f FROM Friendship f WHERE f.requesterId = :userId OR f.addresseeId = :userId")
    fun findAllForUser(@Param("userId") userId: UUID): List<Friendship>

    fun findByRequesterIdAndStatus(
        requesterId: UUID,
        status: FriendshipStatus,
        pageable: Pageable,
    ): Page<Friendship>

    fun findByAddresseeIdAndStatus(
        addresseeId: UUID,
        status: FriendshipStatus,
        pageable: Pageable,
    ): Page<Friendship>

    @Query(
        """
        SELECT f FROM Friendship f
        WHERE f.status = :status
          AND (f.requesterId = :userId OR f.addresseeId = :userId)
        """,
    )
    fun findByUserIdAndStatus(
        @Param("userId") userId: UUID,
        @Param("status") status: FriendshipStatus,
        pageable: Pageable,
    ): Page<Friendship>

    @Query(
        """
        SELECT CASE
            WHEN f.requesterId = :userId THEN f.addresseeId
            ELSE f.requesterId
        END
        FROM Friendship f
        WHERE f.status = :status
          AND (f.requesterId = :userId OR f.addresseeId = :userId)
        """,
    )
    fun findFriendIdsByStatus(
        @Param("userId") userId: UUID,
        @Param("status") status: FriendshipStatus,
    ): List<UUID>

    @Query(
        """
        SELECT COUNT(f) > 0 FROM Friendship f
        WHERE f.status = :status
          AND (
            (f.requesterId = :userA AND f.addresseeId = :userB)
            OR (f.requesterId = :userB AND f.addresseeId = :userA)
          )
        """,
    )
    fun existsBetweenUsersWithStatus(
        @Param("userA") userA: UUID,
        @Param("userB") userB: UUID,
        @Param("status") status: FriendshipStatus,
    ): Boolean
}
