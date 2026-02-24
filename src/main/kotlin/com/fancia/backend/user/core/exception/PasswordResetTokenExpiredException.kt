package com.fancia.backend.user.core.exception

class PasswordResetTokenExpiredException(
    message: String = "Password reset token not found"
) : Throwable()