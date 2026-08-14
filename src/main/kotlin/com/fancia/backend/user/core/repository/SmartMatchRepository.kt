package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.SmartMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SmartMatchRepository : JpaRepository<SmartMatch, UUID> {
    fun findByFirstUserIdAndSecondUserId(firstUserId: UUID, secondUserId: UUID): SmartMatch?
    fun existsByFirstUserIdAndSecondUserId(firstUserId: UUID, secondUserId: UUID): Boolean
    fun findByFirstUserIdAndFirstUserLikedIsNullOrderByRankAsc(firstUserId: UUID): List<SmartMatch>
    fun findByFirstUserId(firstUserId: UUID): List<SmartMatch>

    @Query(
        """
        SELECT s
        FROM SmartMatch s
        WHERE (s.firstUserId = :userId OR s.secondUserId = :userId)
          AND (s.firstUserLiked = TRUE OR s.secondUserLiked = TRUE)
          AND (
            (s.firstUserId = :userId AND (s.firstUserLiked IS NULL OR s.firstUserLiked = TRUE))
            OR
            (s.secondUserId = :userId AND (s.secondUserLiked IS NULL OR s.secondUserLiked = TRUE))
          )
        ORDER BY COALESCE(s.secondUserLikedAt, s.firstUserLikedAt) DESC
        """,
    )
    fun findLikedConnectionsForUser(@Param("userId") userId: UUID): List<SmartMatch>

    @Query(
        """
        SELECT s
        FROM SmartMatch s
        WHERE (s.firstUserId = :userId OR s.secondUserId = :userId)
          AND (s.firstUserLiked = TRUE OR s.secondUserLiked = TRUE)
        """,
    )
    fun findEitherLikedRowsForUser(@Param("userId") userId: UUID): List<SmartMatch>

    @Query(
        """
        SELECT s.secondUserId
        FROM SmartMatch s
        WHERE s.firstUserId = :userId
          AND s.firstUserLiked IS NOT NULL
        """,
    )
    fun findFlaggedSecondUserIdsForFirstUser(@Param("userId") userId: UUID): List<UUID>

    @Query(
        """
        SELECT s.firstUserId
        FROM SmartMatch s
        WHERE s.secondUserId = :userId
          AND s.secondUserLiked IS NOT NULL
        """,
    )
    fun findFlaggedFirstUserIdsForSecondUser(@Param("userId") userId: UUID): List<UUID>
}
