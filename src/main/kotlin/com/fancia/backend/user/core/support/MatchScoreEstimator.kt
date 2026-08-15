package com.fancia.backend.user.core.support

import com.fancia.backend.shared.user.core.entity.User

/**
 * Lightweight absolute match score used when the cron ranker has not stored one.
 * Same shape as [com.fancia.backend.user.core.support] weights in user-cron:
 * base 1 + 10 per shared tag + 4 for overlapping location labels.
 */
object MatchScoreEstimator {
    const val BASE_SCORE = 1.0
    const val EXACT_TAG_WEIGHT = 10.0
    const val LOCATION_BONUS = 4.0

    fun estimate(currentUser: User, otherUser: User): Double {
        var score = BASE_SCORE
        score += currentUser.tags.intersect(otherUser.tags).size * EXACT_TAG_WEIGHT
        val a = currentUser.locationLabel?.trim()?.lowercase().orEmpty()
        val b = otherUser.locationLabel?.trim()?.lowercase().orEmpty()
        if (a.isNotBlank() && b.isNotBlank() && (a.contains(b) || b.contains(a))) {
            score += LOCATION_BONUS
        }
        return score
    }

    /** Prefer a positive stored ranker score; otherwise estimate. */
    fun coalesce(stored: Double?, currentUser: User, otherUser: User): Double {
        if (stored != null && stored.isFinite() && stored > 0.0) return stored
        return estimate(currentUser, otherUser)
    }
}
