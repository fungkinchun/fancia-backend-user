package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.*

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    fun findByToken(token: String): PasswordResetToken?

    @Query("SELECT t FROM PasswordResetToken t WHERE t.user.id = :userId")
    fun findAllByUserId(@Param("userId") userId: UUID): List<PasswordResetToken>
}