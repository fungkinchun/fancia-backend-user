package com.fancia.backend.user.core.dto

import jakarta.validation.constraints.NotBlank

data class UpdateUserRequest(
    @field:NotBlank
    val firstName: String = "",
    @field:NotBlank
    val lastName: String = ""
)