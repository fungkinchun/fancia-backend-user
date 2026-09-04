package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourcesGroupedResponse
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.shared.user.core.dto.UpdatePremiumStatusRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.user.core.service.BlockedResourceService
import com.fancia.backend.user.core.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/internal/users")
@Tag(name = "Users Internal", description = "Internal user endpoints for service-to-service calls")
class UserInternalController(
    private val userService: UserService,
    private val blockedResourceService: BlockedResourceService,
) {
    @Operation(summary = "Get user by id (service-to-service)")
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: UUID): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.getUserResponseById(id))
    }

    @Operation(summary = "List blocked USER and TAG ids for filtering (service-to-service)")
    @GetMapping("/{id}/blocked")
    fun getBlocked(
        @PathVariable id: UUID,
        @RequestParam(required = false) types: List<BlockedResourceType>?,
    ): ResponseEntity<BlockedResourcesGroupedResponse> {
        val resolvedTypes = types?.takeIf { it.isNotEmpty() }
            ?: listOf(BlockedResourceType.USER, BlockedResourceType.TAG)
        return ResponseEntity.ok(blockedResourceService.groupedForUser(id, resolvedTypes))
    }

    @Operation(summary = "Update user premium status")
    @PutMapping("/{id}/premium")
    fun updatePremiumStatus(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdatePremiumStatusRequest,
    ): ResponseEntity<UserResponse> {
        return ResponseEntity.ok(userService.updatePremiumStatus(id, request))
    }
}
