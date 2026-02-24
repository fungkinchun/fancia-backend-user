package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.VerificationCode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface VerificationCodeRepository : JpaRepository<VerificationCode, Long> {
    @Query("SELECT vc FROM VerificationCode vc WHERE vc.code = :code")
    fun findByCode(code: String): VerificationCode?
}