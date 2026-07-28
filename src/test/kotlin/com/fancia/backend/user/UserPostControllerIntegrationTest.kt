package com.fancia.backend.user

import com.fancia.backend.shared.common.post.core.dto.PostResponse
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.shared.user.core.enums.Gender
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.`is`
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
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
class UserPostControllerIntegrationTest(
    private val mockMvc: MockMvc,
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


    test("should forward post creation to common-internal with user target id") {
        val user = registerUser()
        val userId = user.id!!
        val postId = UUID.randomUUID()
        val commonResponse = PostResponse(
            id = postId,
            targetId = userId,
            authorUserId = userId,
            body = "hello",
            media = emptyList(),
            featured = false,
            pinned = false,
            createdAt = null,
        )
        stubFor(
            post(urlPathEqualTo("/internal/posts"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(jsonMapper.writeValueAsString(commonResponse))
                )
        )
        val responseBody = mockMvc
            .post("/api/users/$userId/posts") {
                with(jwt().jwt { it.claim("userId", userId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "body" to "hello",
                        "media" to emptyList<Any>(),
                        "featured" to false,
                        "pinned" to false,
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isCreated() }
                jsonPath("$.id", `is`(postId.toString()))
                jsonPath("$.targetId", `is`(userId.toString()))
            }
            .andReturn()
            .response
            .contentAsString
        val response = jsonMapper.readValue(responseBody, object : TypeReference<PostResponse>() {})
        response.targetId shouldBe userId

        verify(
            postRequestedFor(urlPathEqualTo("/internal/posts"))
                .withRequestBody(matchingJsonPath("$.targetId", equalTo(userId.toString())))
                .withRequestBody(matchingJsonPath("$.authorUserId", equalTo(userId.toString()))),
        )
    }

    test("should return bad request when user does not exist") {
        val missingUserId = UUID.randomUUID()
        val currentUserId = UUID.randomUUID()

        mockMvc
            .post("/api/users/$missingUserId/posts") {
                with(jwt().jwt { it.claim("userId", currentUserId) })
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "body" to "hello",
                        "media" to emptyList<Any>(),
                        "featured" to false,
                        "pinned" to false,
                    )
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isBadRequest() } }

        verify(0, postRequestedFor(urlPathEqualTo("/internal/posts")))
    }
})

private fun ResultActionsDsl.toUserResponse(jsonMapper: JsonMapper): UserResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<UserResponse>() {}) }
