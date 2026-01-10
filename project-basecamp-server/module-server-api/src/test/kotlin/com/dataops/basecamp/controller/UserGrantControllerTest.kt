package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.entity.user.UserEntity
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import com.dataops.basecamp.domain.service.UserGrantService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDateTime

/**
 * UserGrantController REST API Tests
 *
 * Spring Boot 4.x patterns with @SpringBootTest and @AutoConfigureMockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class UserGrantControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var userGrantService: UserGrantService

    @MockkBean
    private lateinit var userRepositoryJpa: UserRepositoryJpa

    private lateinit var testGrant: UserResourceGrantEntity
    private lateinit var testUser: UserEntity
    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        testUser = createUserEntity(10L, "testuser", "test@example.com")
        testGrant = createGrantEntity(1L)
    }

    @Nested
    @DisplayName("GET /api/v1/resources/{resourceType}/shares/{shareId}/grants")
    inner class ListGrants {
        @Test
        @DisplayName("should return grant list for share")
        @WithMockUser(roles = ["USER"])
        fun `should return grant list for share`() {
            // Given
            val grants = listOf(testGrant)
            every { userGrantService.listGrantsByShare(1L) } returns grants
            every { userRepositoryJpa.findByIdAndDeletedAtIsNull(10L) } returns testUser

            // When & Then
            mockMvc
                .perform(get("/api/v1/resources/METRIC/shares/1/grants"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].shareId").value(1))
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].userEmail").value("test@example.com"))
                .andExpect(jsonPath("$[0].permission").value("VIEWER"))
        }

        @Test
        @DisplayName("should return empty list when no grants")
        @WithMockUser(roles = ["USER"])
        fun `should return empty list when no grants`() {
            // Given
            every { userGrantService.listGrantsByShare(1L) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/resources/METRIC/shares/1/grants"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$").isEmpty)
        }
    }

    @Nested
    @DisplayName("POST /api/v1/resources/{resourceType}/shares/{shareId}/grants")
    inner class CreateGrant {
        @Test
        @DisplayName("should create grant successfully")
        @WithMockUser(roles = ["USER"])
        fun `should create grant successfully`() {
            // Given
            every { userGrantService.createGrant(any()) } returns testGrant
            every { userRepositoryJpa.findByIdAndDeletedAtIsNull(10L) } returns testUser

            val requestBody =
                """
                {
                    "userId": 10,
                    "permission": "VIEWER"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/resources/METRIC/shares/1/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.shareId").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.permission").value("VIEWER"))

            verify(exactly = 1) { userGrantService.createGrant(any()) }
        }

        @Test
        @DisplayName("should create grant with EDITOR permission")
        @WithMockUser(roles = ["USER"])
        fun `should create grant with EDITOR permission`() {
            // Given
            val editorGrant =
                UserResourceGrantEntity(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.EDITOR,
                    grantedBy = 20L,
                    grantedAt = now,
                ).apply {
                    val idField = UserResourceGrantEntity::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1L)
                }

            every { userGrantService.createGrant(any()) } returns editorGrant
            every { userRepositoryJpa.findByIdAndDeletedAtIsNull(10L) } returns testUser

            val requestBody =
                """
                {
                    "userId": 10,
                    "permission": "EDITOR"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/resources/METRIC/shares/1/grants")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.permission").value("EDITOR"))
        }
    }

    @Nested
    @DisplayName("GET /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}")
    inner class GetGrant {
        @Test
        @DisplayName("should return grant details")
        @WithMockUser(roles = ["USER"])
        fun `should return grant details`() {
            // Given
            every { userGrantService.getGrantOrThrow(1L) } returns testGrant
            every { userRepositoryJpa.findByIdAndDeletedAtIsNull(10L) } returns testUser

            // When & Then
            mockMvc
                .perform(get("/api/v1/resources/METRIC/shares/1/grants/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.shareId").value(1))
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.userEmail").value("test@example.com"))
                .andExpect(jsonPath("$.userName").value("testuser"))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}")
    inner class UpdateGrant {
        @Test
        @DisplayName("should update grant successfully")
        @WithMockUser(roles = ["USER"])
        fun `should update grant successfully`() {
            // Given
            val updatedGrant =
                UserResourceGrantEntity(
                    shareId = 1L,
                    userId = 10L,
                    permission = ResourcePermission.EDITOR,
                    grantedBy = 20L,
                    grantedAt = now,
                ).apply {
                    val idField = UserResourceGrantEntity::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1L)
                }

            every { userGrantService.updateGrant(any()) } returns updatedGrant
            every { userRepositoryJpa.findByIdAndDeletedAtIsNull(10L) } returns testUser

            val requestBody =
                """
                {
                    "permission": "EDITOR"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/resources/METRIC/shares/1/grants/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.permission").value("EDITOR"))

            verify(exactly = 1) { userGrantService.updateGrant(any()) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/resources/{resourceType}/shares/{shareId}/grants/{grantId}")
    inner class RevokeGrant {
        @Test
        @DisplayName("should revoke grant successfully")
        @WithMockUser(roles = ["USER"])
        fun `should revoke grant successfully`() {
            // Given
            every { userGrantService.revokeGrant(any()) } returns Unit

            // When & Then
            mockMvc
                .perform(delete("/api/v1/resources/METRIC/shares/1/grants/1"))
                .andExpect(status().isNoContent)

            verify(exactly = 1) { userGrantService.revokeGrant(any()) }
        }
    }

    // ==================== Helper Methods ====================

    private fun createUserEntity(
        id: Long,
        username: String,
        email: String,
    ): UserEntity =
        UserEntity(
            email = email,
            username = username,
        ).apply {
            val idField = UserEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createGrantEntity(id: Long): UserResourceGrantEntity =
        UserResourceGrantEntity(
            shareId = 1L,
            userId = 10L,
            permission = ResourcePermission.VIEWER,
            grantedBy = 20L,
            grantedAt = now,
        ).apply {
            val idField = UserResourceGrantEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
}
