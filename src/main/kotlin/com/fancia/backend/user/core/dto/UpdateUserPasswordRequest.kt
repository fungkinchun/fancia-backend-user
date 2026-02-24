package com.fancia.backend.user.core.dto

import com.fancia.backend.shared.common.core.validator.PasswordMatch
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import org.hibernate.validator.constraints.Length

@PasswordMatch(passwordField = "password", passwordConfirmationField = "confirmPassword")
data class UpdateUserPasswordRequest(
    val oldPassword: String = "",
    @NotNull
    @Length(min = 8)
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).*$",
        message = "must contain at least one uppercase letter, one lowercase letter, and one digit."
    )
    val password: String = "",
    val confirmPassword: String = "",
    val passwordResetToken: String = ""
)
