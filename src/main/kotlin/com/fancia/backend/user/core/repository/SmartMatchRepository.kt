package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.SmartMatch
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface SmartMatchRepository : JpaRepository<SmartMatch, UUID> {
    fun findByCreatedByAndUserId(createdBy: UUID, userId: UUID): SmartMatch?
    fun existsByCreatedByAndUserId(createdBy: UUID, userId: UUID): Boolean
}
