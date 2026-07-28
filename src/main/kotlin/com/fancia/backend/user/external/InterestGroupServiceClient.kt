package com.fancia.backend.user.external

import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@FeignClient(name = "interestgroup-service", path = "/api", configuration = [FeignConfig::class])
interface InterestGroupServiceClient {
    @GetMapping("/interest-groups/users/{userId}/memberships")
    fun getInterestGroupMembership(
        @RequestParam("userId") userId: UUID,
        @RequestParam("role") role: InterestGroupRole = InterestGroupRole.ADMIN,
        @RequestParam(value = "page", required = false) page: Int = 0,
        @RequestParam(value = "size", required = false) size: Int = 20
    ): Page<InterestGroupMembershipResponse>
}