package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.VerificationCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface VerificationCodeRepository : JpaRepository<VerificationCode, UUID> {
    fun findByCode(code: String): VerificationCode?

    @Query("SELECT v FROM VerificationCode v WHERE v.user.id = :userId")
    fun findByUserId(@Param("userId") userId: UUID): VerificationCode?
}