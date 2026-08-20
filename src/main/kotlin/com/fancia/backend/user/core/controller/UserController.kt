package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.*
import com.fancia.backend.shared.user.core.exception.UserWithEmailNotFoundException
import com.fancia.backend.shared.user.core.message.UserDeletedEvent
import com.fancia.backend.user.config.ApplicationProperties
import com.fancia.backend.user.core.message.UserProducer
import com.fancia.backend.user.core.service.UserService
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView
import java.util.*

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "User endpoints")
@SecurityRequirement(name = "bearerAuth")
class UserController(
    private val userService: UserService,
    private val userProducer: UserProducer,
    private val applicationProperties: ApplicationProperties
) {
    @GetMapping("/email/{email}")
    fun getUserByEmail(@PathVariable email: String): ResponseEntity<ProfileResponse> {
        return ResponseEntity.ok(userService.findByEmail(email))
    }

    @GetMapping("/me")
    fun getCurrentUser(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getCurrentUser(jwt))
    }

    @GetMapping("/{id}")
    fun getUserById(@PathVariable id: UUID): ResponseEntity<ProfileResponse> {
        val user = userService.findById(id) ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(user)
    }

    @PostMapping
    fun createUser(@RequestBody @Valid request: CreateUserRequest): ResponseEntity<UserResponse> {
        val user = userService.create(request)
        return ResponseEntity.ok(user)
    }

    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): RedirectView? {
        userService.verifyEmail(token)
        return applicationProperties.loginPageUrl?.let { RedirectView(it) }
    }

    @PostMapping("/forgot-password")
    fun forgotPassword(@RequestBody @Valid req: ForgotPasswordRequest): ResponseEntity<Void> {
        val email = req.email ?: throw UserWithEmailNotFoundException(email = "")
        userService.findByEmail(email)
        userService.forgotPassword(email)
        return ResponseEntity.ok().build()
    }

    @PatchMapping("/reset-password")
    fun resetPassword(@RequestBody @Valid requestDTO: UpdateUserPasswordRequest): ResponseEntity<Void> {
        userService.resetPassword(requestDTO)
        return ResponseEntity.ok().build()
    }

    @PutMapping
    fun updateUser(
        @RequestBody @Valid request: UpdateUserRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<UserResponse> {
        val user = userService.update(request, jwt)
        return ResponseEntity.ok(user)
    }

    @PatchMapping("/settings")
    fun updateUserSettings(
        @RequestBody @Valid request: UpdateUserSettingsRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<UserResponse> {
        val user = userService.updateSettings(request, jwt)
        return ResponseEntity.ok(user)
    }

    @PatchMapping("/password")
    fun updatePassword(
        @RequestBody @Valid requestDTO: UpdateUserPasswordRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<UserResponse> {
        val user = userService.updatePassword(requestDTO, jwt)
        return ResponseEntity.ok(user)
    }

    @DeleteMapping("/{id}")
    fun deleteUser(
        @PathVariable id: UUID,
        @RequestParam("forceDelete") forceDelete: Boolean = false,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<Void> {
        val userId = userService.deleteUser(jwt, forceDelete)
        userProducer.publishUserDeleted(
            UserDeletedEvent(userId)
        )
        return ResponseEntity.ok().build()
    }
}