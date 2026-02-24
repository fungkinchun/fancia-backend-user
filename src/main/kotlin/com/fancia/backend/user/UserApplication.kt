package com.fancia.backend.user

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication

@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@EntityScan(
    basePackages = [
        "com.fancia.backend.user",
        "com.fancia.backend.shared.user.core.entity"
    ]
)
@SpringBootApplication
class UserApplication

fun main(args: Array<String>) {
    runApplication<UserApplication>(*args)
}
