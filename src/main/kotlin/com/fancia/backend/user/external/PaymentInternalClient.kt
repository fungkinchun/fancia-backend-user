package com.fancia.backend.user.external

import com.fancia.backend.shared.user.core.dto.GrantReferralPremiumRequest
import com.fancia.backend.shared.user.core.dto.GrantReferralPremiumResponse
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@FeignClient(
    name = "payment-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface PaymentInternalClient {
    @PostMapping("/subscriptions/referral")
    fun grantReferralPremium(
        @RequestBody request: GrantReferralPremiumRequest,
    ): GrantReferralPremiumResponse
}
