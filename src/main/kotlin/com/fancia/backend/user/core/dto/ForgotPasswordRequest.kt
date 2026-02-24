package com.fancia.backend.user.core.dto

import jakarta.validation.constraints.Email

data class ForgotPasswordRequest(
    val email: @Email String = ""
)