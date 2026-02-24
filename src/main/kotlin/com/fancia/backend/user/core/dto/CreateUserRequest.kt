package com.fancia.backend.user.core.dto

import com.fancia.backend.shared.common.core.validator.PasswordMatch
import com.fancia.backend.shared.common.core.validator.Unique
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@PasswordMatch(passwordField = "password", passwordConfirmationField = "confirmPassword")
data class CreateUserRequest(
    @field:Email
    @Unique(columnName = "email", tableName = "users", message = "User with this email already exists")
    val email: String = "",
    @field:NotNull
    @field:Length(min = 8)
    @field:Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
        message = "must contain at least one uppercase letter, one lowercase letter, and one digit."
    )
    val password: String = "",
    val confirmPassword: String = "",
    @field:NotBlank
    val firstName: String = "",
    @field:NotBlank
    val lastName: String = ""
)