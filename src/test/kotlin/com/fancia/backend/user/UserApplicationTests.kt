package com.fancia.backend.user

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
@SpringBootTest
@Import(TestConfig::class)
class UserApplicationTests {
    @Test
    fun contextLoads() {
    }
}
