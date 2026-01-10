package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import com.dataops.basecamp.domain.service.ResourceShareService
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
 * ResourceShareController REST API Tests
 *
 * Spring Boot 4.x patterns with @SpringBootTest and @AutoConfigureMockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class ResourceShareControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var resourceShareService: ResourceShareService

    @MockkBean
    private lateinit var userGrantService: UserGrantService

    @MockkBean
    private lateinit var teamRepositoryJpa: TeamRepositoryJpa

    private lateinit var testShare: TeamResourceShareEntity
    private lateinit var ownerTeam: TeamEntity
    private lateinit var sharedWithTeam: TeamEntity
    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        ownerTeam = createTeamEntity(1L, "owner-team")
        sharedWithTeam = createTeamEntity(2L, "shared-team")

        testShare = createShareEntity(1L)
    }

    @Nested
    @DisplayName("GET /api/v1/resources/{resourceType}/shares")
    inner class ListShares {
        @Test
        @DisplayName("should return share list filtered by ownerTeamId")
        @WithMockUser(roles = ["USER"])
        fun `should return share list filtered by ownerTeamId`() {
            // Given
            val shares = listOf(testShare)
            every {
                resourceShareService.listSharesByOwnerTeamAndResourceType(1L, ShareableResourceType.METRIC)
            } returns shares
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every { userGrantService.countGrantsByShare(1L) } returns 3L

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/resources/METRIC/shares")
                        .param("ownerTeamId", "1"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].sharedWithTeamId").value(2))
                .andExpect(jsonPath("$[0].permission").value("VIEWER"))
        }

        @Test
        @DisplayName("should return share list filtered by sharedWithTeamId")
        @WithMockUser(roles = ["USER"])
        fun `should return share list filtered by sharedWithTeamId`() {
            // Given
            val shares = listOf(testShare)
            every {
                resourceShareService.listSharesBySharedWithTeamAndResourceType(2L, ShareableResourceType.METRIC)
            } returns shares
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every { userGrantService.countGrantsByShare(1L) } returns 3L

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/resources/METRIC/shares")
                        .param("sharedWithTeamId", "2"),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$[0].sharedWithTeamId").value(2))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/resources/{resourceType}/shares")
    inner class CreateShare {
        @Test
        @DisplayName("should create share successfully")
        @WithMockUser(roles = ["USER"])
        fun `should create share successfully`() {
            // Given
            every { resourceShareService.createShare(any()) } returns testShare
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every { userGrantService.countGrantsByShare(1L) } returns 0L

            val requestBody =
                """
                {
                    "sharedWithTeamId": 2,
                    "resourceId": 100,
                    "permission": "VIEWER",
                    "visibleToTeam": true
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/resources/METRIC/shares")
                        .param("ownerTeamId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ownerTeamId").value(1))
                .andExpect(jsonPath("$.sharedWithTeamId").value(2))
                .andExpect(jsonPath("$.resourceType").value("METRIC"))
                .andExpect(jsonPath("$.permission").value("VIEWER"))

            verify(exactly = 1) { resourceShareService.createShare(any()) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/resources/{resourceType}/shares/{shareId}")
    inner class GetShare {
        @Test
        @DisplayName("should return share details")
        @WithMockUser(roles = ["USER"])
        fun `should return share details`() {
            // Given
            every { resourceShareService.getShareOrThrow(1L) } returns testShare
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every { userGrantService.countGrantsByShare(1L) } returns 5L

            // When & Then
            mockMvc
                .perform(get("/api/v1/resources/METRIC/shares/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.ownerTeamId").value(1))
                .andExpect(jsonPath("$.ownerTeamName").value("owner-team"))
                .andExpect(jsonPath("$.sharedWithTeamId").value(2))
                .andExpect(jsonPath("$.sharedWithTeamName").value("shared-team"))
                .andExpect(jsonPath("$.grantCount").value(5))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/resources/{resourceType}/shares/{shareId}")
    inner class UpdateShare {
        @Test
        @DisplayName("should update share successfully")
        @WithMockUser(roles = ["USER"])
        fun `should update share successfully`() {
            // Given
            val updatedShare =
                TeamResourceShareEntity(
                    ownerTeamId = 1L,
                    sharedWithTeamId = 2L,
                    resourceType = ShareableResourceType.METRIC,
                    resourceId = 100L,
                    permission = ResourcePermission.EDITOR,
                    visibleToTeam = false,
                    grantedBy = 10L,
                    grantedAt = now,
                ).apply {
                    val idField = TeamResourceShareEntity::class.java.superclass.getDeclaredField("id")
                    idField.isAccessible = true
                    idField.set(this, 1L)
                }

            every { resourceShareService.updateShare(any()) } returns updatedShare
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(1L) } returns ownerTeam
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(2L) } returns sharedWithTeam
            every { userGrantService.countGrantsByShare(1L) } returns 0L

            val requestBody =
                """
                {
                    "permission": "EDITOR",
                    "visibleToTeam": false
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/resources/METRIC/shares/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.permission").value("EDITOR"))
                .andExpect(jsonPath("$.visibleToTeam").value(false))

            verify(exactly = 1) { resourceShareService.updateShare(any()) }
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/resources/{resourceType}/shares/{shareId}")
    inner class RevokeShare {
        @Test
        @DisplayName("should revoke share successfully")
        @WithMockUser(roles = ["USER"])
        fun `should revoke share successfully`() {
            // Given
            every { resourceShareService.revokeShare(any()) } returns Unit

            // When & Then
            mockMvc
                .perform(delete("/api/v1/resources/METRIC/shares/1"))
                .andExpect(status().isNoContent)

            verify(exactly = 1) { resourceShareService.revokeShare(any()) }
        }
    }

    // ==================== Helper Methods ====================

    private fun createTeamEntity(
        id: Long,
        name: String,
    ): TeamEntity =
        TeamEntity(
            name = name,
            displayName = name,
        ).apply {
            val idField = TeamEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }

    private fun createShareEntity(id: Long): TeamResourceShareEntity =
        TeamResourceShareEntity(
            ownerTeamId = 1L,
            sharedWithTeamId = 2L,
            resourceType = ShareableResourceType.METRIC,
            resourceId = 100L,
            permission = ResourcePermission.VIEWER,
            visibleToTeam = true,
            grantedBy = 10L,
            grantedAt = now,
        ).apply {
            val idField = TeamResourceShareEntity::class.java.superclass.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(this, id)
        }
}
