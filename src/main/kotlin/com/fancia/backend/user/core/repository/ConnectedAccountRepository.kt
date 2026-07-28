package com.fancia.backend.user.core.repository

import com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface ConnectedAccountRepository : JpaRepository<UserConnectedAccount, Long> {
    fun findByProviderAndProviderId(
        provider: String,
        providerId: String
    ): UserConnectedAccount?
}