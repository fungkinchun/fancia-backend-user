package com.fancia.backend.user.core.event

import java.util.*

data class PasswordResetTokenCreatedEvent(
    val id: UUID
)