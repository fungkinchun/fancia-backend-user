package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.common.moderation.core.dto.BlockedResourceResponse
import com.fancia.backend.shared.common.moderation.core.dto.CreateBlockedResourceRequest
import com.fancia.backend.shared.common.moderation.core.enums.BlockedResourceType
import com.fancia.backend.user.core.service.BlockedResourceService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/blocked")
@Tag(name = "Blocked resources", description = "Block/hide users, posts, comments, and tags")
@SecurityRequirement(name = "bearerAuth")
class BlockedController(
    private val blockedResourceService: BlockedResourceService,
) {
    @PostMapping
    @Operation(summary = "Block or hide a resource")
    fun block(
        @RequestBody @Valid request: CreateBlockedResourceRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<BlockedResourceResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(blockedResourceService.block(request, jwt))

    @GetMapping
    @Operation(summary = "List blocked/hidden resources for the current user")
    fun list(
        @RequestParam(required = false) resourceType: BlockedResourceType?,
        @AuthenticationPrincipal jwt: Jwt,
        @PageableDefault(size = 50) pageable: Pageable,
    ): ResponseEntity<Page<BlockedResourceResponse>> =
        ResponseEntity.ok(blockedResourceService.list(resourceType, jwt, pageable))

    @DeleteMapping("/{resourceType}/{resourceId}")
    @Operation(summary = "Unblock or unhide a resource")
    fun unblock(
        @PathVariable resourceType: BlockedResourceType,
        @PathVariable resourceId: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        blockedResourceService.unblock(resourceType, resourceId, jwt)
        return ResponseEntity.noContent().build()
    }
}
