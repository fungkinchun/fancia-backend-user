package com.fancia.backend.user

import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.user.core.repository.ReferralRepository
import com.fancia.backend.user.core.repository.UserRepository
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.patch
import org.springframework.test.web.servlet.post
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [UserApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class ReferralControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val referralRepository: ReferralRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    beforeEach {
        reset()
        referralRepository.deleteAll()
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    fun registerUser(
        email: String = "user-${UUID.randomUUID()}@example.com",
        firstName: String = "Jon",
        lastName: String = "Snow",
    ): UserResponse {
        val password = "Password1a"
        return mockMvc
            .post("/api/users") {
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "email" to email,
                        "password" to password,
                        "confirmPassword" to password,
                        "firstName" to firstName,
                        "lastName" to lastName,
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }
            .toUserResponse(jsonMapper)
    }

    fun setSlug(userId: UUID, handle: String) {
        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(userId))
                content = jsonMapper.writeValueAsString(mapOf("slug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }
    }

    fun stubReferralPremiumGrant(userId: UUID, expiresAt: LocalDateTime = LocalDateTime.now().plusDays(30)) {
        stubFor(
            post(urlPathEqualTo("/internal/subscriptions/referral"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "userId" to userId.toString(),
                                    "premiumActive" to true,
                                    "premiumExpiresAt" to expiresAt.toString(),
                                ),
                            ),
                        ),
                ),
        )
    }

    test("should list successful referrals for signed-in referrer") {
        val referrer = registerUser(firstName = "Carol", lastName = "Referrer")
        val handle = "carol-${UUID.randomUUID().toString().take(8)}"
        setSlug(referrer.id!!, handle)

        mockMvc
            .get("/api/referrals") {
                with(jwtFor(referrer.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(0))
                jsonPath("$.content", `is`(emptyList<Any>()))
            }

        val referee = registerUser()
        stubReferralPremiumGrant(referee.id!!)

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/referrals") {
                with(jwtFor(referrer.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].referrerSlug", `is`(handle))
            }
    }

    test("should claim referral premium for a new signup") {
        val referrer = registerUser(firstName = "Alice", lastName = "Referrer")
        val handle = "alice-${UUID.randomUUID().toString().take(8)}"
        setSlug(referrer.id!!, handle)

        val referee = registerUser(firstName = "Bob", lastName = "Invitee")
        val expiresAt = LocalDateTime.now().plusDays(30)
        stubReferralPremiumGrant(referee.id!!, expiresAt)

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.referrerSlug", `is`(handle))
                jsonPath("$.premiumActive", `is`(true))
                jsonPath("$.premiumExpiresAt", `is`(notNullValue()))
            }

        referralRepository.existsByRefereeUserId(referee.id!!) shouldBe true
        verify(postRequestedFor(urlPathEqualTo("/internal/subscriptions/referral")))
    }

    test("should reject unknown referrer slug") {
        val referee = registerUser()

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to "missing-handle"))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode", `is`("REFERRAL_NOT_FOUND"))
            }
    }

    test("should reject self-referral") {
        val user = registerUser()
        val handle = "self-${UUID.randomUUID().toString().take(8)}"
        setSlug(user.id!!, handle)

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode", `is`("REFERRAL_SELF"))
            }
    }

    test("should reject when referee already claimed") {
        val referrer = registerUser()
        val handle = "once-${UUID.randomUUID().toString().take(8)}"
        setSlug(referrer.id!!, handle)

        val referee = registerUser()
        stubReferralPremiumGrant(referee.id!!)

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode", `is`("REFERRAL_ALREADY_CLAIMED"))
            }
    }

    test("should reject accounts older than signup window") {
        val referrer = registerUser()
        val handle = "old-${UUID.randomUUID().toString().take(8)}"
        setSlug(referrer.id!!, handle)

        val referee = registerUser()
        val entity = userRepository.findByIdOrNull(referee.id!!)!!
        entity.createdAt = LocalDateTime.now().minusHours(49)
        userRepository.save(entity)

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode", `is`("REFERRAL_NOT_ELIGIBLE"))
            }
    }

    test("should reject when payment grant fails") {
        val referrer = registerUser()
        val handle = "pay-${UUID.randomUUID().toString().take(8)}"
        setSlug(referrer.id!!, handle)

        val referee = registerUser()
        stubFor(
            post(urlPathEqualTo("/internal/subscriptions/referral"))
                .willReturn(aResponse().withStatus(500)),
        )

        mockMvc
            .post("/api/referrals/claim") {
                with(jwtFor(referee.id!!))
                content = jsonMapper.writeValueAsString(mapOf("referrerSlug" to handle))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isBadRequest() }
                jsonPath("$.errorCode", `is`("REFERRAL_NOT_ELIGIBLE"))
            }
    }
})

private fun ResultActionsDsl.toUserResponse(jsonMapper: JsonMapper): UserResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<UserResponse>() {}) }
