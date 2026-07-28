package com.fancia.backend.user.core.support

import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.enums.AccountStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.user.external.CommonServiceClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.springframework.data.domain.PageImpl
import java.util.UUID

class SmartMatchUserRankerTest : FunSpec({
    val hikingTagId = UUID.randomUUID()
    val yogaTagId = UUID.randomUUID()
    val similarYogaTagId = UUID.randomUUID()
    val blacklistTagId = UUID.randomUUID()
    val blacklistedUserId = UUID.randomUUID()
    val currentUserId = UUID.randomUUID()

    val commonServiceClient =
        object : CommonServiceClient {
            override fun searchTags(
                search: Set<String>,
                type: TagType,
                page: Int,
                size: Int,
            ) = PageImpl(
                if (search.contains("hiking")) {
                    listOf(TagResponse(id = hikingTagId, name = "hiking", type = TagType.INTEREST))
                } else if (search.contains("yoga")) {
                    listOf(
                        TagResponse(id = yogaTagId, name = "yoga", type = TagType.INTEREST),
                        TagResponse(id = similarYogaTagId, name = "yogalates", type = TagType.INTEREST),
                    )
                } else {
                    emptyList()
                },
            )

            override fun createTags(
                request: com.fancia.backend.shared.common.tag.core.dto.CreateTagsRequest,
                page: Int,
                size: Int,
            ) = PageImpl(emptyList<TagResponse>())

            override fun getTagsByIds(ids: Set<UUID>) =
                ids.mapNotNull { id ->
                    when (id) {
                        hikingTagId -> TagResponse(id = hikingTagId, name = "hiking", type = TagType.INTEREST)
                        yogaTagId -> TagResponse(id = yogaTagId, name = "yoga", type = TagType.INTEREST)
                        else -> null
                    }
                }
        }

    val ranker = SmartMatchUserRanker(commonServiceClient)

    fun user(
        id: UUID,
        tags: Set<UUID> = emptySet(),
        locationLabel: String? = null,
    ) = User().apply {
        this.id = id
        this.tags = tags.toMutableSet()
        this.locationLabel = locationLabel
        visibility = ProfileVisibility.PUBLIC
        status = AccountStatus.ACTIVE
    }

    test("exact tag match ranks above partial similar tag match") {
        val exact = user(UUID.randomUUID(), setOf(hikingTagId, yogaTagId))
        val partial = user(UUID.randomUUID(), setOf(similarYogaTagId))
        val preferences = SmartMatchUserPreferences(tagIds = setOf(hikingTagId, yogaTagId))

        val ranked = ranker.rank(listOf(exact, partial), preferences, currentUserId)

        ranked shouldHaveSize 2
        ranked[0].user shouldBe exact
        ranked[0].score shouldBeGreaterThan ranked[1].score
    }

    test("blacklisted tag excludes candidate") {
        val blocked = user(UUID.randomUUID(), setOf(blacklistTagId, hikingTagId))
        val allowed = user(UUID.randomUUID(), setOf(hikingTagId))
        val preferences = SmartMatchUserPreferences(
            tagIds = setOf(hikingTagId),
            blacklistedIds = setOf(blacklistTagId),
        )

        val ranked = ranker.rank(listOf(blocked, allowed), preferences, currentUserId)

        ranked shouldHaveSize 1
        ranked[0].user shouldBe allowed
    }

    test("blacklisted user id excludes candidate") {
        val blocked = user(blacklistedUserId, setOf(hikingTagId))
        val allowed = user(UUID.randomUUID(), setOf(hikingTagId))
        val preferences = SmartMatchUserPreferences(
            tagIds = setOf(hikingTagId),
            blacklistedIds = setOf(blacklistedUserId),
        )

        val ranked = ranker.rank(listOf(blocked, allowed), preferences, currentUserId)

        ranked shouldHaveSize 1
        ranked[0].user shouldBe allowed
    }

    test("location overlap adds bonus score") {
        val nearby = user(UUID.randomUUID(), setOf(hikingTagId), "Berlin, Germany")
        val farAway = user(UUID.randomUUID(), setOf(hikingTagId), "Paris, France")
        val preferences = SmartMatchUserPreferences(
            tagIds = setOf(hikingTagId),
            locationLabel = "Berlin",
        )

        val ranked = ranker.rank(listOf(farAway, nearby), preferences, currentUserId)

        ranked[0].user shouldBe nearby
        ranked[0].score shouldBeGreaterThan ranked[1].score
    }
})
