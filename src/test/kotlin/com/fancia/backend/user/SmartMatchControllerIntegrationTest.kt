package com.fancia.backend.user

import com.fancia.backend.shared.user.core.dto.SmartMatchResponse
import com.fancia.backend.shared.user.core.dto.UserResponse
import com.fancia.backend.shared.user.core.enums.AccountStatus
import com.fancia.backend.shared.user.core.enums.ProfileVisibility
import com.fancia.backend.user.core.repository.UserRepository
import com.github.tomakehurst.wiremock.client.WireMock.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.hamcrest.CoreMatchers.*
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.data.domain.Page
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.*
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.util.*

@SpringBootTest(classes = [UserApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class SmartMatchControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val userRepository: UserRepository,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
) : FunSpec({
    beforeSpec {
        configureFor(
            wiremock.host,
            wiremock.getMappedPort(8080),
        )
    }

    val tagRegistry = mutableListOf<Pair<UUID, String>>()

    beforeEach {
        reset()
        userRepository.deleteAll()
        tagRegistry.clear()
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

    fun activateUser(userId: UUID) {
        val user = userRepository.findByIdOrNull(userId)!!
        user.status = AccountStatus.ACTIVE
        userRepository.save(user)
    }

    fun stubCreateTags(tags: List<Pair<UUID, String>>, type: String = "INTEREST") {
        stubFor(
            post(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to tags.map { (id, name) ->
                                        mapOf(
                                            "id" to id.toString(),
                                            "name" to name,
                                            "type" to type,
                                        )
                                    },
                                    "totalElements" to tags.size,
                                    "totalPages" to 1,
                                    "size" to tags.size,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun stubGetTagsByIds(tags: List<Pair<UUID, String>>, type: String = "INTEREST") {
        stubFor(
            get(urlPathEqualTo("/api/tags/ids"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                tags.map { (id, name) ->
                                    mapOf(
                                        "id" to id.toString(),
                                        "name" to name,
                                        "type" to type,
                                    )
                                },
                            ),
                        ),
                ),
        )
    }

    fun stubSearchTags(tags: List<Pair<UUID, String>>, type: String = "INTEREST") {
        stubFor(
            get(urlPathEqualTo("/api/tags"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "content" to tags.map { (id, name) ->
                                        mapOf(
                                            "id" to id.toString(),
                                            "name" to name,
                                            "type" to type,
                                        )
                                    },
                                    "totalElements" to tags.size,
                                    "totalPages" to 1,
                                    "size" to tags.size,
                                    "number" to 0,
                                ),
                            ),
                        ),
                ),
        )
    }

    fun assignTags(userId: UUID, tags: List<Pair<String, UUID>>) {
        tagRegistry.addAll(tags.map { (name, id) -> id to name })
        stubCreateTags(tags.map { (name, id) -> id to name })
        stubGetTagsByIds(tagRegistry.distinct())
        stubSearchTags(emptyList())
        mockMvc
            .put("/api/users") {
                with(jwtFor(userId))
                content = jsonMapper.writeValueAsString(
                    mapOf(
                        "tags" to tags.map { (name, _) ->
                            mapOf("name" to name, "type" to "INTEREST")
                        },
                    ),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }
    }

    test("should return browse pool when user has no interest tags") {
        val user = registerUser()
        activateUser(user.id!!)

        val otherUser = registerUser(firstName = "Browse", lastName = "Pool")
        activateUser(otherUser.id!!)

        mockMvc
            .get("/api/smart-match") {
                with(jwtFor(user.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.totalElements", `is`(1))
                jsonPath("$.content[0].id", `is`(otherUser.id.toString()))
            }
    }

    test("should match public active users by shared interests") {
        val hikingTagId = UUID.randomUUID()
        val musicTagId = UUID.randomUUID()

        val currentUser = registerUser(firstName = "Current", lastName = "User")
        activateUser(currentUser.id!!)
        assignTags(currentUser.id!!, listOf("hiking" to hikingTagId, "music" to musicTagId))

        val bestMatch = registerUser(firstName = "Best", lastName = "Match")
        activateUser(bestMatch.id!!)
        assignTags(bestMatch.id!!, listOf("hiking" to hikingTagId, "music" to musicTagId))

        val partialMatch = registerUser(firstName = "Partial", lastName = "Match")
        activateUser(partialMatch.id!!)
        assignTags(partialMatch.id!!, listOf("hiking" to hikingTagId))

        val noMatch = registerUser(firstName = "No", lastName = "Match")
        activateUser(noMatch.id!!)
        val cookingTagId = UUID.randomUUID()
        assignTags(noMatch.id!!, listOf("cooking" to cookingTagId))

        val response = mockMvc
            .get("/api/smart-match") {
                with(jwtFor(currentUser.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(2))
                jsonPath("$.totalElements", `is`(2))
                jsonPath("$.content[0].id", `is`(bestMatch.id.toString()))
                jsonPath("$.content[0].firstName", `is`("Best"))
                jsonPath("$.content[0].lastName", `is`("Match"))
                jsonPath("$.content[0].tags", hasItems(hikingTagId.toString(), musicTagId.toString()))
                jsonPath("$.content[1].id", `is`(partialMatch.id.toString()))
            }
            .toUserPage(jsonMapper)

        response.content.map { it.id } shouldContainExactlyInAnyOrder listOf(bestMatch.id!!, partialMatch.id!!)
    }

    test("should not include current user, private users, or inactive users") {
        val hikingTagId = UUID.randomUUID()
        val currentUser = registerUser(firstName = "Current", lastName = "User")
        activateUser(currentUser.id!!)
        assignTags(currentUser.id!!, listOf("hiking" to hikingTagId))

        val privateUser = registerUser(firstName = "Private", lastName = "User")
        activateUser(privateUser.id!!)
        assignTags(privateUser.id!!, listOf("hiking" to hikingTagId))
        val savedPrivateUser = userRepository.findByIdOrNull(privateUser.id!!)!!
        savedPrivateUser.visibility = ProfileVisibility.PRIVATE
        userRepository.save(savedPrivateUser)

        val inactiveUser = registerUser(firstName = "Inactive", lastName = "User")
        assignTags(inactiveUser.id!!, listOf("hiking" to hikingTagId))

        val visibleMatch = registerUser(firstName = "Visible", lastName = "Match")
        activateUser(visibleMatch.id!!)
        assignTags(visibleMatch.id!!, listOf("hiking" to hikingTagId))

        mockMvc
            .get("/api/smart-match") {
                with(jwtFor(currentUser.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.content[0].id", `is`(visibleMatch.id.toString()))
            }
    }

    test("should hide interests when showInterests privacy is disabled") {
        val hikingTagId = UUID.randomUUID()
        val currentUser = registerUser(firstName = "Current", lastName = "User")
        activateUser(currentUser.id!!)
        assignTags(currentUser.id!!, listOf("hiking" to hikingTagId))

        val matchedUser = registerUser(firstName = "Hidden", lastName = "Interests")
        activateUser(matchedUser.id!!)
        assignTags(matchedUser.id!!, listOf("hiking" to hikingTagId))

        mockMvc
            .patch("/api/users/settings") {
                with(jwtFor(matchedUser.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf("privacy" to mapOf("showInterests" to false)),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect { status { isOk() } }

        mockMvc
            .get("/api/smart-match") {
                with(jwtFor(currentUser.id!!))
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.content.length()", `is`(1))
                jsonPath("$.content[0].id", `is`(matchedUser.id.toString()))
                jsonPath("$.content[0].tags.length()", `is`(0))
            }
    }

    test("should require authentication") {
        mockMvc
            .get("/api/smart-match") {
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isUnauthorized() }
            }
    }

    test("should create and update smart match") {
        val hikingTagId = UUID.randomUUID()
        val currentUser = registerUser(firstName = "Matcher", lastName = "One")
        activateUser(currentUser.id!!)
        assignTags(currentUser.id!!, listOf("hiking" to hikingTagId))

        val matchedUser = registerUser(firstName = "Matcher", lastName = "Two")
        activateUser(matchedUser.id!!)
        assignTags(matchedUser.id!!, listOf("hiking" to hikingTagId))

        val createResponse = mockMvc
            .post("/api/smart-match") {
                with(jwtFor(currentUser.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf("userId" to matchedUser.id.toString()),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.userId", `is`(matchedUser.id.toString()))
                jsonPath("$.createdBy", `is`(currentUser.id.toString()))
                jsonPath("$.matchedByCreatedBy", `is`(true))
                jsonPath("$.matchedByUser", `is`(false))
            }
            .toSmartMatchResponse(jsonMapper)

        mockMvc
            .patch("/api/smart-match/${createResponse.id}") {
                with(jwtFor(matchedUser.id!!))
                content = jsonMapper.writeValueAsString(
                    mapOf("matchedByUser" to true),
                )
                contentType = APPLICATION_JSON
                accept = APPLICATION_JSON
            }
            .andExpect {
                status { isOk() }
                jsonPath("$.matchedByUser", `is`(true))
                jsonPath("$.matchedByCreatedBy", `is`(true))
            }
    }

    afterSpec {
        userRepository.deleteAll()
    }
})

private fun ResultActionsDsl.toSmartMatchResponse(jsonMapper: JsonMapper): SmartMatchResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<SmartMatchResponse>() {}) }

private fun ResultActionsDsl.toUserResponse(jsonMapper: JsonMapper): UserResponse =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<UserResponse>() {}) }

private fun ResultActionsDsl.toUserPage(jsonMapper: JsonMapper): Page<UserResponse> =
    andReturn()
        .response
        .contentAsString
        .let { jsonMapper.readValue(it, object : TypeReference<Page<UserResponse>>() {}) }
