package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.AccountStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, UUID> {
    fun findByEmail(@Param("email") email: String): User?

    @Query("SELECT u FROM User u WHERE :tagId MEMBER OF u.tags")
    fun findByTagId(@Param("tagId") tagId: UUID): List<User>

    @Query(
        """
        SELECT u
        FROM User u
        WHERE u.id IN (
            SELECT u2.id
            FROM User u2
            JOIN u2.tags tag
            WHERE tag IN :tagIds
              AND u2.id <> :excludeUserId
              AND u2.visibility = :visibility
              AND u2.status = :status
        )
        """,
    )
    fun findPublicActiveUsersWithSharedTags(
        @Param("tagIds") tagIds: Collection<UUID>,
        @Param("excludeUserId") excludeUserId: UUID,
        @Param("visibility") visibility: ProfileVisibility,
        @Param("status") status: AccountStatus,
    ): List<User>

    @Query(
        """
        SELECT u
        FROM User u
        WHERE u.id <> :excludeUserId
          AND u.visibility = :visibility
          AND u.status = :status
        """,
    )
    fun findPublicActiveUsersExcluding(
        @Param("excludeUserId") excludeUserId: UUID,
        @Param("visibility") visibility: ProfileVisibility,
        @Param("status") status: AccountStatus,
    ): List<User>
}