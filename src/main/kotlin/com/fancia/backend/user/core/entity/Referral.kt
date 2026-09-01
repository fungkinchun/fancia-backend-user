package com.fancia.backend.user.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "referrals")
class Referral : AbstractEntity() {
    @Column(name = "referrer_user_id", nullable = false)
    var referrerUserId: UUID? = null

    @Column(name = "referee_user_id", nullable = false)
    var refereeUserId: UUID? = null

    @Column(name = "referrer_slug", nullable = false, length = 255)
    var referrerSlug: String = ""

    @Column(name = "rewarded_at", nullable = false)
    var rewardedAt: LocalDateTime = LocalDateTime.now()
}
