package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.CreateSmartMatchRequest
import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.dto.UpdateSmartMatchRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.user.core.service.SmartMatchService
import com.fancia.backend.user.core.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/smart-match")
@Tag(name = "Smart Match", description = "Interest-based people matching")
@SecurityRequirement(name = "bearerAuth")
class SmartMatchController(
    private val userService: UserService,
    private val smartMatchService: SmartMatchService,
) {
    @GetMapping
    @Operation(
        summary = "Match people by shared interests",
        description = "Returns public active users ranked by interest overlap, similar tags, location, and blacklist preferences.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Matched people returned"),
        ],
    )
    fun smartMatch(
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 20)
        pageable: Pageable,
    ): ResponseEntity<Page<UserResponse>> {
        return ResponseEntity.ok(userService.smartMatch(jwt, pageable))
    }

    @PostMapping
    @Operation(summary = "Create a smart match with another user")
    fun createSmartMatch(
        @RequestBody @Valid request: CreateSmartMatchRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SmartMatchResponse> {
        return ResponseEntity.ok(smartMatchService.create(request, jwt))
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update smart match flags")
    fun updateSmartMatch(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateSmartMatchRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SmartMatchResponse> {
        return ResponseEntity.ok(smartMatchService.update(id, request, jwt))
    }
}
