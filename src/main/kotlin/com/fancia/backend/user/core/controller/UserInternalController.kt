package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.UpdatePremiumStatusRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.user.core.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/users")
@Tag(name = "Users Internal", description = "Internal user endpoints for service-to-service calls")
class UserInternalController(
    private val userService: UserService,
) {
    @Operation(summary = "Update user premium status")
    @PutMapping("/{id}/premium")
    fun updatePremiumStatus(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdatePremiumStatusRequest,
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.updatePremiumStatus(id, request))
    }
}
