package com.fancia.backend.user.core.support

import com.fancia.backend.shared.common.core.utils.Slugify
import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.exception.UserSlugChangeCooldownException
import com.fancia.backend.shared.user.core.exception.UserSlugInvalidException
import com.fancia.backend.shared.user.core.exception.UserSlugTakenException
import com.fancia.backend.user.core.entity.UserSlugHistory
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.repository.UserSlugHistoryRepository
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.UUID

@Service
class UserSlugService(
    private val userRepository: UserRepository,
    private val userSlugHistoryRepository: UserSlugHistoryRepository,
) {
    fun normalizeHandle(raw: String): String = Slugify.slugify(raw.trim(), fallback = "")

    fun validateHandle(handle: String) {
        if (handle.isBlank()) {
            throw UserSlugInvalidException("Profile URL handle is required")
        }
        if (!HANDLE_PATTERN.matches(handle)) {
            throw UserSlugInvalidException(
                "Use 3–30 lowercase letters, numbers, and hyphens. Must start with a letter or number.",
            )
        }
        if (handle in RESERVED_HANDLES) {
            throw UserSlugInvalidException("This profile URL is reserved")
        }
    }

    fun isHandleAvailable(handle: String, excludeUserId: UUID? = null): Boolean {
        val normalized = normalizeHandle(handle)
        if (normalized.isBlank()) return false
        return try {
            validateHandle(normalized)
            !isTaken(normalized, excludeUserId)
        } catch (_: UserSlugInvalidException) {
            false
        }
    }

    fun resolveUser(ref: String): User? {
        val trimmed = ref.trim()
        if (trimmed.isEmpty()) return null
        runCatching { UUID.fromString(trimmed) }.getOrNull()?.let { id ->
            return userRepository.findById(id).orElse(null)
        }
        val normalized = normalizeHandle(trimmed)
        userRepository.findBySlug(normalized)?.let { return it }
        val history = userSlugHistoryRepository.findById(normalized).orElse(null) ?: return null
        val userId = history.userId ?: return null
        return userRepository.findById(userId).orElse(null)
    }

    fun applySlugChange(user: User, requestedHandle: String) {
        val newSlug = normalizeHandle(requestedHandle)
        validateHandle(newSlug)
        val currentSlug = user.slug?.trim()?.lowercase()
        if (currentSlug == newSlug) return

        if (currentSlug != null) {
            val changedAt = user.slugChangedAt
                ?: throw UserSlugChangeCooldownException(LocalDateTime.now().plusDays(COOLDOWN_DAYS))
            val nextAllowed = changedAt.plusDays(COOLDOWN_DAYS)
            if (LocalDateTime.now().isBefore(nextAllowed)) {
                throw UserSlugChangeCooldownException(nextAllowed)
            }
        }

        if (isTaken(newSlug, user.id)) {
            throw UserSlugTakenException(newSlug)
        }

        if (currentSlug != null) {
            userSlugHistoryRepository.save(
                UserSlugHistory().apply {
                    slug = currentSlug
                    userId = user.id
                    createdAt = LocalDateTime.now()
                },
            )
        }

        user.slug = newSlug
        user.slugChangedAt = LocalDateTime.now()
    }

    fun slugChangeAllowedAt(user: User): LocalDateTime? {
        val changedAt = user.slugChangedAt ?: return null
        val nextAllowed = changedAt.plusDays(COOLDOWN_DAYS)
        return if (LocalDateTime.now().isBefore(nextAllowed)) nextAllowed else null
    }

    private fun isTaken(handle: String, excludeUserId: UUID?): Boolean {
        val activeOwner = userRepository.findBySlug(handle)?.id
        if (activeOwner != null && activeOwner != excludeUserId) return true
        if (userSlugHistoryRepository.existsBySlug(handle)) return true
        return false
    }

    companion object {
        const val COOLDOWN_DAYS = 180L
        private val HANDLE_PATTERN = Regex("^[a-z0-9][a-z0-9-]{2,29}$")
        private val RESERVED_HANDLES = setOf(
            "profile",
            "profiles",
            "settings",
            "api",
            "admin",
            "events",
            "event",
            "venues",
            "venue",
            "groups",
            "group",
            "login",
            "signup",
            "sign-up",
            "register",
            "help",
            "terms",
            "privacy",
            "discover",
            "messages",
            "friends",
            "calendar",
            "smart-match",
            "create-event",
            "create-venue",
            "create-group",
            "me",
            "email",
            "handles",
            "handle",
            "users",
            "user",
            "www",
            "r",
        )
    }
}
