package com.fancia.backend.user.core.repository

import  com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface ConnectedAccountRepository : JpaRepository<UserConnectedAccount, Long> {
    @Query("SELECT a FROM UserConnectedAccount a WHERE a.provider = :provider AND a.providerId = :providerId")
    fun findByProviderAndProviderId(
        @Param("provider") provider: String,
        @Param("providerId") providerId: String
    ): UserConnectedAccount?
}