package com.fancia.backend.user.core.repository

import com.fancia.backend.user.core.entity.Referral
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ReferralRepository : JpaRepository<Referral, UUID> {
    fun existsByRefereeUserId(refereeUserId: UUID): Boolean

    fun findByReferrerUserIdOrderByRewardedAtDesc(
        referrerUserId: UUID,
        pageable: Pageable,
    ): Page<Referral>
}
