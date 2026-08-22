package com.fancia.backend.user.core.repository

import com.fancia.backend.user.core.entity.UserSlugHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserSlugHistoryRepository : JpaRepository<UserSlugHistory, String> {
    fun existsBySlug(slug: String): Boolean
}
