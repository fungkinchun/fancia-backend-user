package com.fancia.backend.user.external

import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupMembershipResponse
import com.fancia.backend.shared.interestgroup.core.dto.InterestGroupResponse
import com.fancia.backend.shared.interestgroup.core.enums.InterestGroupRole
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@FeignClient(name = "interestgroup-service", path = "/api", configuration = [FeignConfig::class])
interface InterestGroupServiceClient {
    @GetMapping("/interest-groups/{id}")
    fun getInterestGroup(@PathVariable id: UUID): InterestGroupResponse

    @GetMapping("/interest-groups/{id}/memberships")
    fun listMembershipsInGroup(
        @PathVariable id: UUID,
        @RequestParam(required = false) role: InterestGroupRole? = null,
    ): List<InterestGroupMembershipResponse>

    @GetMapping("/interest-groups/users/{userId}/memberships")
    fun getInterestGroupMembership(
        @RequestParam("userId") userId: UUID,
        @RequestParam("role") role: InterestGroupRole = InterestGroupRole.ADMIN,
        @RequestParam(value = "page", required = false) page: Int = 0,
        @RequestParam(value = "size", required = false) size: Int = 20
    ): Page<InterestGroupMembershipResponse>
}