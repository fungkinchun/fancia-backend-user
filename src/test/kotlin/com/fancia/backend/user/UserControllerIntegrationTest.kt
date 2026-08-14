package com.fancia.backend.user

import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.shared.user.core.enums.Gender
import com.fancia.backend.user.core.repository.UserRepository
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.*

@SpringBootTest(classes = [UserApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class UserControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private var jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080)
        )
    }

    beforeEach {
        reset()
    }
    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }
    fun registerUser(
        email: String = "user-${UUID.randomUUID()}@example.com",
        firstName: String = "Jon",
        lastName: String = "Snow",
        birthDate: LocalDate? = null,
        gender: Gender? = null,
        bio: String? = null,
    ): UserResponse {
        val password = "Password1a"
        val user = mockMvc
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

        if (birthDate != null || gender != null || bio != null) {
            mockMvc
                .put("/api/users") {
                    with(jwtFor(user.id!!))
                    content = jsonMapper.writeValueAsString(
                        buildMap {
                            birthDate?.let { put("birthDate", it.toString()) }
                            gender?.let { put("gender", it.name) }
                            bio?.let { put("bio", it) }
                        },
                    )
                    contentType = APPLICATION_JSON
                    accept = APPLICATION_JSON
                }
                .andExpect { status { isOk() } }
        }
        return user
    }

    test("should create a new user") {
        val response = mockMvc
            .post("/api/users") {
                val requestBody = mapOf(
                    "email" to "user@example.com",
                    "password" to "user@example.comA1",
                    "confirmPassword" to "user@example.comA1",
                    "firstName" to "Jon",
                    "lastName" to "Snow"
                )
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.email", `is`("user@example.com"))
                jsonPath("$.id", `is`(notNullValue()))
                jsonPath("$.status", `is`("REGISTERED"))
            }
        val createdUser = response.toUserResponse(jsonMapper)
        val found = userRepository.findByIdOrNull(createdUser.id!!)
        found?.id shouldBe createdUser.id
        val mockResponse = mapOf(
            "content" to listOf(
                mapOf(
                    "interestGroupId" to UUID.randomUUID().toString(),
                    "userId" to createdUser.id.toString(),
                    "role" to "ADMIN"
                )
            ),
            "totalElements" to 1,
            "totalPages" to 1,
            "size" to 20,
            "number" to 0
        )
        stubFor(
            get(urlPathTemplate("/api/interest-groups/users/{userId}/memberships"))
                .withQueryParam("userId", equalTo(createdUser.id.toString()))
                .withQueryParam("role", equalTo("ADMIN"))
                .withQueryParam("page", equalTo("0"))
                .withQueryParam("size", equalTo("20"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mockResponse
                            )
                        )
                )
        )

        mockMvc.delete("/api/users/${createdUser.id}") {
            with(jwt().jwt {
                it.claim("userId", createdUser.id)
            })
            accept = APPLICATION_JSON
        }
            .andDo { print() }
            .andExpect {
                status { isBadRequest() }
            }
    }

    test("should update user settings for visibility, privacy and notifications") {
        val user = registerUser()
        val requestBody = mapOf(
            "visibility" to "PRIVATE",
            "privacy" to mapOf(
                "allowFriendRequests" to false,
                "showGroups" to true,
                "showInterests" to false,
                "showGender" to true,
                "showBirthday" to true,
            ),
            "notifications" to mapOf(
                "match" to "PUSH_ONLY",
                "messages" to "EMAIL_ONLY",
                "postEngagement" to "NONE",
                "eventRecommendations" to "BOTH",
                "eventReminders" to "PUSH_ONLY",
            ),
        )

        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(requestBody)
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.visibility", `is`("PRIVATE"))
                jsonPath("$.privacy.allowFriendRequests", `is`(false))
                jsonPath("$.privacy.showGroups", `is`(true))
                jsonPath("$.privacy.showInterests", `is`(false))
                jsonPath("$.privacy.showGender", `is`(true))
                jsonPath("$.privacy.showBirthday", `is`(true))
                jsonPath("$.notifications.match", `is`("PUSH_ONLY"))
                jsonPath("$.notifications.messages", `is`("EMAIL_ONLY"))
                jsonPath("$.notifications.postEngagement", `is`("NONE"))
                jsonPath("$.notifications.eventRecommendations", `is`("BOTH"))
                jsonPath("$.notifications.eventReminders", `is`("PUSH_ONLY"))
            }
        val updated = userRepository.findByIdOrNull(user.id!!)!!
        updated.visibility.name shouldBe "PRIVATE"
        updated.settings?.privacy?.allowFriendRequests shouldBe false
        updated.settings?.privacy?.showInterests shouldBe false
        updated.settings?.notifications?.match?.name shouldBe "PUSH_ONLY"
        updated.settings?.notifications?.postEngagement?.name shouldBe "NONE"
    }

    test("should return full profile and settings for authenticated user via GET /me") {
        val birthDate = LocalDate.of(1995, 3, 15)
        val user = registerUser(birthDate = birthDate, gender = Gender.M)

        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "privacy" to mapOf("showGender" to true, "showBirthday" to true),
                        "notifications" to mapOf("match" to "NONE"),
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/users/me") {
                with(jwtFor(user.id!!))
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.id", `is`(user.id.toString()))
                jsonPath("$.email", `is`(user.email!!))
                jsonPath("$.birthDate", `is`(birthDate.toString()))
                jsonPath("$.gender", `is`("M"))
                jsonPath("$.privacy.showGender", `is`(true))
                jsonPath("$.privacy.showBirthday", `is`(true))
                jsonPath("$.notifications.match", `is`("NONE"))
            }
    }

    test("should update user tags via common-service") {
        val user = registerUser()
        val tagName = "hiking"
        val tagId = UUID.randomUUID()
        val tagsResponse = mapOf(
            "content" to listOf(
                mapOf(
                    "id" to tagId,
                    "name" to tagName,
                    "type" to "INTEREST",
                ),
            ),
            "totalElements" to 1,
            "totalPages" to 1,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(tagsResponse))
                )
        )

        mockMvc
            .put("/api/users") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "tags" to listOf(
                            mapOf(
                                "name" to tagName,
                                "type" to "INTEREST",
                            ),
                        ),
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.tags[0]", `is`(tagId.toString()))
            }
        val updated = userRepository.findByIdOrNull(user.id!!)!!
        updated.tags shouldBe setOf(tagId)
    }

    test("should update fcm token via PUT /api/users") {
        val user = registerUser()

        mockMvc
            .put("/api/users") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "fcmToken" to "test-fcm-token",
                        "deviceType" to "ANDROID",
                        "deviceId" to "device-123",
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.notifications.fcmToken", `is`("test-fcm-token"))
                jsonPath("$.notifications.deviceType", `is`("ANDROID"))
                jsonPath("$.notifications.deviceId", `is`("device-123"))
            }

        val updated = userRepository.findByIdOrNull(user.id!!)!!
        updated.settings?.notifications?.fcmToken shouldBe "test-fcm-token"
        updated.settings?.notifications?.deviceType?.name shouldBe "ANDROID"
        updated.settings?.notifications?.deviceId shouldBe "device-123"
    }

    test("should add onboarded system tag via PUT /api/users") {
        val user = registerUser()
        val tagName = "onboarded"
        val tagId = UUID.randomUUID()
        val tagsResponse = mapOf(
            "content" to listOf(
                mapOf(
                    "id" to tagId,
                    "name" to tagName,
                    "type" to "SYSTEM",
                ),
            ),
            "totalElements" to 1,
            "totalPages" to 1,
            "size" to 20,
            "number" to 0,
        )
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(tagsResponse))
                )
        )

        mockMvc
            .put("/api/users") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "tags" to listOf(
                            mapOf(
                                "name" to tagName,
                                "type" to "SYSTEM",
                            ),
                        ),
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.tags[0]", `is`(tagId.toString()))
            }
    }

    test("should redact gender and birth date on public lookup when privacy toggles are off") {
        val email = "public-${UUID.randomUUID()}@example.com"
        val birthDate = LocalDate.of(1992, 7, 4)
        val user = registerUser(
            email = email,
            firstName = "Public",
            lastName = "User",
            birthDate = birthDate,
            gender = Gender.M,
            bio = "Visible bio",
        )

        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "visibility" to "PUBLIC",
                        "privacy" to mapOf(
                            "showGender" to false,
                            "showBirthday" to false,
                        ),
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/users/email/$email") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.bio", `is`("Visible bio"))
                jsonPath("$.gender", nullValue())
                jsonPath("$.birthDate", nullValue())
                jsonPath("$.email").doesNotExist()
                jsonPath("$.notifications").doesNotExist()
                jsonPath("$.authorities").doesNotExist()
                jsonPath("$.privacy").doesNotExist()
                jsonPath("$.premiumActive").doesNotExist()
            }
    }

    test("should redact profile details on public lookup when visibility is private") {
        val email = "private-${UUID.randomUUID()}@example.com"
        val user = registerUser(
            email = email,
            firstName = "Hidden",
            lastName = "User",
            bio = "Secret bio",
        )

        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(user.id!!))
                content = jsonMapper.writeValueAsString(mapOf("visibility" to "PRIVATE"))
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/users/email/$email") {
                accept = APPLICATION_JSON
            }
            .andDo { print() }
            .andExpect {
                status { isOk() }
                jsonPath("$.firstName", `is`("Hidden"))
                jsonPath("$.lastName", `is`("User"))
                jsonPath("$.bio", `is`("Secret bio"))
                jsonPath("$.visibility", `is`("PRIVATE"))
                jsonPath("$.email").doesNotExist()
                jsonPath("$.gender", nullValue())
                jsonPath("$.birthDate", nullValue())
                jsonPath("$.tags.length()", `is`(0))
                jsonPath("$.notifications").doesNotExist()
                jsonPath("$.authorities").doesNotExist()
                jsonPath("$.privacy").doesNotExist()
                jsonPath("$.premiumActive").doesNotExist()
                jsonPath("$.eventsCount", nullValue())
                jsonPath("$.groupsCount", nullValue())
            }
    }
})

private fun ResultActionsDsl.toUserResponse(jsonMapper: JsonMapper): UserResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<UserResponse>() {}) }
