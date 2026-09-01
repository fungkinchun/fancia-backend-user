package com.fancia.backend.user.core.service

import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.user.core.dto.ClaimReferralRequest
import com.fancia.backend.shared.user.core.dto.ClaimReferralResponse
import com.fancia.backend.shared.user.core.dto.GrantReferralPremiumRequest
import com.fancia.backend.shared.user.core.exception.ReferralAlreadyClaimedException
import com.fancia.backend.shared.user.core.exception.ReferralNotEligibleException
import com.fancia.backend.shared.user.core.exception.ReferralNotFoundException
import com.fancia.backend.shared.user.core.exception.ReferralSelfClaimException
import com.fancia.backend.shared.user.core.exception.UserNotFoundException
import com.fancia.backend.user.core.entity.Referral
import com.fancia.backend.user.core.repository.ReferralRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.fancia.backend.user.core.support.UserSlugService
import com.fancia.backend.user.external.PaymentInternalClient
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class ReferralService(
    private val referralRepository: ReferralRepository,
    private val userRepository: UserRepository,
    private val userSlugService: UserSlugService,
    private val paymentInternalClient: PaymentInternalClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun claim(jwt: Jwt, request: ClaimReferralRequest): ClaimReferralResponse {
        val refereeId = jwt.getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()

        val referee = userRepository.findById(refereeId).orElseThrow { UserNotFoundException() }
        val slug = userSlugService.normalizeHandle(request.referrerSlug)
        if (slug.isBlank()) {
            throw ReferralNotFoundException(request.referrerSlug)
        }

        val referrer = userSlugService.resolveUser(slug)
            ?: throw ReferralNotFoundException(slug)

        val referrerId = referrer.id ?: throw ReferralNotFoundException(slug)
        if (referrerId == refereeId) {
            throw ReferralSelfClaimException()
        }

        if (referralRepository.existsByRefereeUserId(refereeId)) {
            throw ReferralAlreadyClaimedException()
        }

        val createdAt = referee.createdAt ?: LocalDateTime.now()
        if (createdAt.isBefore(LocalDateTime.now().minusHours(NEW_SIGNUP_WINDOW_HOURS))) {
            throw ReferralNotEligibleException()
        }

        val grant = try {
            paymentInternalClient.grantReferralPremium(
                GrantReferralPremiumRequest(userId = refereeId, days = REWARD_DAYS),
            )
        } catch (ex: Exception) {
            log.error("Failed to grant referral premium for referee={}", refereeId, ex)
            throw ReferralNotEligibleException(
                "Could not apply referral Premium right now. Please try again shortly.",
            )
        }

        try {
            referralRepository.save(
                Referral().apply {
                    referrerUserId = referrerId
                    refereeUserId = refereeId
                    referrerSlug = referrer.slug ?: slug
                    rewardedAt = LocalDateTime.now()
                    createdBy = refereeId
                },
            )
        } catch (_: DataIntegrityViolationException) {
            throw ReferralAlreadyClaimedException()
        }

        val updatedReferee = userRepository.findById(refereeId).orElse(referee)

        log.info(
            "Referral claimed referee={} referrer={} slug={} premiumExpiresAt={}",
            refereeId,
            referrerId,
            slug,
            grant.premiumExpiresAt,
        )

        return ClaimReferralResponse(
            referrerSlug = referrer.slug ?: slug,
            premiumExpiresAt = updatedReferee.premiumExpiresAt ?: grant.premiumExpiresAt,
            premiumActive = updatedReferee.premiumActive || grant.premiumActive,
        )
    }

    companion object {
        const val NEW_SIGNUP_WINDOW_HOURS: Long = 48
        const val REWARD_DAYS: Long = 30
    }
}
