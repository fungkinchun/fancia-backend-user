package com.fancia.backend.user.core.dto

import com.fancia.backend.shared.user.core.entity.Role
import com.fancia.backend.shared.user.core.entity.User
import org.springframework.security.core.GrantedAuthority
import java.time.LocalDateTime
import java.util.*

data class UserResponse(
    val id: UUID? = null,
    val role: Role? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
    val profileImageUrl: String? = null,
    val connectedAccounts: MutableList<ConnectedAccountResponse> = mutableListOf(),
    val authorities: MutableList<String> = mutableListOf()
) {
    constructor(user: User) : this(
        id = user.id,
        role = user.role,
        firstName = user.firstName,
        lastName = user.lastName,
        email = user.email,
        profileImageUrl = user.profileImageUrl
    ) {
        user.connectedAccounts.map { provider ->
            connectedAccounts.add(
                ConnectedAccountResponse(provider?.provider, provider?.connectedAt)
            )
        }
    }

    constructor(user: User, authorities: Collection<GrantedAuthority>) : this(user) {
        authorities.forEach { authority ->
            authority.authority?.let { this.authorities.add(it) }
        }
    }
}

data class ConnectedAccountResponse(val provider: String?, val connectedAt: LocalDateTime?)