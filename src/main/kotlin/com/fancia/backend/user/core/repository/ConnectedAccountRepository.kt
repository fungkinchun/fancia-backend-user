package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ConnectedAccountRepository : JpaRepository<UserConnectedAccount, Long> {
    fun findByProviderAndProviderId(
        provider: String,
        providerId: String
    ): UserConnectedAccount?

    @Query("SELECT a FROM UserConnectedAccount a WHERE a.user.id = :userId")
    fun findAllByUserId(@Param("userId") userId: UUID): List<UserConnectedAccount>
}