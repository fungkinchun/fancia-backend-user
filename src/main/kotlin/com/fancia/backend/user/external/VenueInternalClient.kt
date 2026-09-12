package com.fancia.backend.user.external

import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import java.util.UUID

@FeignClient(
    name = "venue-internal-service",
    path = "/internal/v1",
    configuration = [FeignConfig::class],
)
interface VenueInternalClient {
    @DeleteMapping("/users/{userId}/resources")
    fun deleteUserResources(
        @PathVariable userId: UUID,
    )
}
