package com.fancia.backend.user.core.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "user_slug_history")
class UserSlugHistory {
    @Id
    @Column(nullable = false, length = 255)
    var slug: String = ""

    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
}
