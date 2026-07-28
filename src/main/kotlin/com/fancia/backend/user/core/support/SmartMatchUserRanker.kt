package com.fancia.backend.user.core.support

import com.fancia.backend.shared.common.tag.core.dto.TagResponse
import com.fancia.backend.shared.common.tag.core.enums.TagType
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.user.external.CommonServiceClient
import org.springframework.stereotype.Component
import java.util.UUID

data class SmartMatchUserPreferences(
    val tagIds: Set<UUID> = emptySet(),
    val blacklistedIds: Set<UUID> = emptySet(),
    val locationLabel: String? = null,
)

data class RankedUser(
    val user: User,
    val score: Double,
)

@Component
class SmartMatchUserRanker(
    private val commonServiceClient: CommonServiceClient,
) {
    fun expandTagWeights(preferences: SmartMatchUserPreferences): Map<UUID, Double> {
        val weights = mutableMapOf<UUID, Double>()
        if (preferences.tagIds.isEmpty()) return weights

        val seedTags = commonServiceClient.getTagsByIds(preferences.tagIds)
            .filter { it.id != null && it.id in preferences.tagIds }
        for (tag in seedTags) {
            val tagId = tag.id!!
            weights.merge(tagId, EXACT_TAG_WEIGHT) { existing, added -> maxOf(existing, added) }
            expandSimilarTags(tag, weights)
        }
        return weights
    }

    fun isBlacklisted(candidate: User, blacklistedIds: Set<UUID>): Boolean {
        if (blacklistedIds.isEmpty()) return false
        if (candidate.id != null && candidate.id in blacklistedIds) return true
        if (candidate.tags.any { it in blacklistedIds }) return true
        return false
    }

    fun score(
        candidate: User,
        tagWeights: Map<UUID, Double>,
        preferences: SmartMatchUserPreferences,
    ): Double {
        var score = BASE_SCORE
        for (candidateTag in candidate.tags) {
            score += tagWeights[candidateTag] ?: 0.0
        }
        val userLocation = preferences.locationLabel?.trim()?.lowercase()
        val candidateLocation = candidate.locationLabel?.trim()?.lowercase()
        if (!userLocation.isNullOrBlank() && !candidateLocation.isNullOrBlank()) {
            if (candidateLocation.contains(userLocation) || userLocation.contains(candidateLocation)) {
                score += LOCATION_BONUS
            }
        }
        return score
    }

    fun rank(
        candidates: List<User>,
        preferences: SmartMatchUserPreferences,
        currentUserId: UUID,
    ): List<RankedUser> {
        val tagWeights = expandTagWeights(preferences)
        return candidates
            .asSequence()
            .filter { user -> user.id != null && user.id != currentUserId }
            .filter { user -> !isBlacklisted(user, preferences.blacklistedIds) }
            .map { user -> RankedUser(user, score(user, tagWeights, preferences)) }
            .sortedWith(compareByDescending<RankedUser> { it.score }.thenBy { it.user.id })
            .toList()
    }

    private fun expandSimilarTags(seedTag: TagResponse, weights: MutableMap<UUID, Double>) {
        val name = seedTag.name.trim()
        if (name.isEmpty()) return
        val types = linkedSetOf(seedTag.type, TagType.INTEREST, TagType.TOPIC, TagType.EVENT)
        for (type in types) {
            val similarPage = commonServiceClient.searchTags(setOf(name), type, page = 0, size = 8)
            for (similar in similarPage.content) {
                val similarId = similar.id ?: continue
                if (similarId == seedTag.id) continue
                weights.merge(similarId, SIMILAR_TAG_WEIGHT) { existing, added -> maxOf(existing, added) }
            }
        }
    }

    companion object {
        const val BASE_SCORE = 1.0
        const val EXACT_TAG_WEIGHT = 10.0
        const val SIMILAR_TAG_WEIGHT = 3.5
        const val LOCATION_BONUS = 4.0
    }
}
