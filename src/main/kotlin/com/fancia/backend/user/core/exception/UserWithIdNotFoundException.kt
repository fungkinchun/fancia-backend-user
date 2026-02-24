package com.fancia.backend.user.core.exception

class UserWithIdNotFoundException(
    val id: String,
    message: String = "User not found with id: $id"
) : Throwable()