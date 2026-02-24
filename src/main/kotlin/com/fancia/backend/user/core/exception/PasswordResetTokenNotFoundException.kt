package com.fancia.backend.user.core.exception

class PasswordResetTokenNotFoundException(
    message: String = "Password reset token not found"
) : Throwable()