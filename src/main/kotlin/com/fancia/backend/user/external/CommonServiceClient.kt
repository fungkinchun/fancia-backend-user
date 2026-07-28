package com.fancia.backend.user.external

import com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest
import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.user.config.FeignConfig
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import java.util.*

@FeignClient(name = "common-service", path = "/api", configuration = [FeignConfig::class])
interface CommonServiceClient {
    @GetMapping("/tags")
    fun searchTags(
        @RequestParam("search") search: Set<String>,
        @RequestParam("type") type: TagType,
        @RequestParam("page") page: Int = 0,
        @RequestParam("size") size: Int = 20,
    ): Page<TagResponse>

    @PostMapping("/tags")
    fun createTags(
        @RequestBody request: CreateTagsRequest,
        @RequestParam("page") page: Int = 0,
        @RequestParam("size") size: Int = 100,
    ): Page<TagResponse>

    @GetMapping("/tags/ids")
    fun getTagsByIds(@RequestParam("id") ids: Set<UUID>): List<TagResponse>
}
