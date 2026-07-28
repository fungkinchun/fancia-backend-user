package com.fancia.backend.user

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.security.SecurityScheme
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.boot.runApplication
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT"
)
@EntityScan(
    basePackages = [
        "com.fancia.backend.user",
        "com.fancia.backend.shared.user.core.entity",
        "com.fancia.backend.shared.auth.core.client.entity",
        "com.fancia.backend.shared.common.tag.core.entity",
    ]
)
@EnableJpaRepositories
@EnableFeignClients
@SpringBootApplication(
    scanBasePackages = [
        "com.fancia.backend.user",
    ]
)
class UserApplication

fun main(args: Array<String>) {
    runApplication<UserApplication>(*args)
}
