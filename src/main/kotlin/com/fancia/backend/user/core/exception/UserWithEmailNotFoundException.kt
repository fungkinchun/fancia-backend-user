package com.fancia.backend.user.core.exception

class UserWithEmailNotFoundException(
    val email: String,
    message: String = "User not found with email: $email"
) : Throwable()