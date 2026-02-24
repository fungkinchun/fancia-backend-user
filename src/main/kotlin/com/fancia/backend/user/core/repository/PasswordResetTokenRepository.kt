package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.PasswordResetToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.*

interface PasswordResetTokenRepository : JpaRepository<PasswordResetToken, UUID> {
    @Query("SELECT prt FROM PasswordResetToken prt WHERE prt.token = :passwordResetToken")
    fun findByToken(passwordResetToken: String): PasswordResetToken?
}