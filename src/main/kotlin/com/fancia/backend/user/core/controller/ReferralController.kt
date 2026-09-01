package com.fancia.backend.user.core.controller

import com.fancia.backend.shared.user.core.dto.ClaimReferralRequest
import com.fancia.backend.shared.user.core.dto.ClaimReferralResponse
import com.fancia.backend.user.core.service.ReferralService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/referrals")
@Tag(name = "Referrals", description = "Refer-a-friend claim")
@SecurityRequirement(name = "bearerAuth")
class ReferralController(
    private val referralService: ReferralService,
) {
    @PostMapping("/claim")
    @Operation(summary = "Claim referral Premium as a new signup (invitee reward)")
    fun claim(
        @RequestBody @Valid request: ClaimReferralRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ClaimReferralResponse> =
        ResponseEntity.ok(referralService.claim(jwt, request))
}
