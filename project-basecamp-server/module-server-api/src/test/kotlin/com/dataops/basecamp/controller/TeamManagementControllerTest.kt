package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.domain.command.team.AddTeamMemberCommand
import com.dataops.basecamp.domain.command.team.CreateTeamCommand
import com.dataops.basecamp.domain.command.team.RemoveTeamMemberCommand
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.entity.team.TeamMemberEntity
import com.dataops.basecamp.domain.projection.team.TeamMemberWithUserProjection
import com.dataops.basecamp.domain.projection.team.TeamStatisticsProjection
import com.dataops.basecamp.domain.service.TeamService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
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
import org.springframework.data.domain.PageImpl
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
 * TeamManagementController REST API Tests
 *
 * Spring Boot 4.x patterns with @SpringBootTest and @AutoConfigureMockMvc
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class TeamManagementControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var teamService: TeamService

    private lateinit var testTeam: TeamEntity
    private lateinit var testStatistics: TeamStatisticsProjection
    private val now = LocalDateTime.now()

    @BeforeEach
    fun setUp() {
        testTeam =
            TeamEntity(
                name = "test-team",
                displayName = "Test Team",
                description = "Test team description",
            ).apply {
                // Simulate saved entity with ID and timestamps
                val idField = TeamEntity::class.java.superclass.getDeclaredField("id")
                idField.isAccessible = true
                idField.set(this, 1L)

                val createdAtField = TeamEntity::class.java.superclass.getDeclaredField("createdAt")
                createdAtField.isAccessible = true
                createdAtField.set(this, now)

                val updatedAtField = TeamEntity::class.java.superclass.getDeclaredField("updatedAt")
                updatedAtField.isAccessible = true
                updatedAtField.set(this, now)
            }

        testStatistics =
            TeamStatisticsProjection(
                teamId = 1L,
                memberCount = 5,
                resourceCounts = emptyMap(),
            )
    }

    @Nested
    @DisplayName("GET /api/v1/team-management")
    inner class ListTeams {
        @Test
        @DisplayName("should return team list")
        @WithMockUser(roles = ["USER"])
        fun `should return team list`() {
            // Given
            val teams = PageImpl(listOf(testTeam))
            every { teamService.listTeams(any(), any(), any()) } returns teams
            every { teamService.getTeamStatistics(any()) } returns testStatistics

            // When & Then
            mockMvc
                .perform(get("/api/v1/team-management"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("test-team"))
                .andExpect(jsonPath("$[0].displayName").value("Test Team"))
        }

        @Test
        @DisplayName("should filter by name parameter")
        @WithMockUser(roles = ["USER"])
        fun `should filter by name parameter`() {
            // Given
            val teams = PageImpl(listOf(testTeam))
            every { teamService.listTeams(eq("test"), any(), any()) } returns teams
            every { teamService.getTeamStatistics(any()) } returns testStatistics

            // When & Then
            mockMvc
                .perform(get("/api/v1/team-management").param("name", "test"))
                .andExpect(status().isOk)

            verify { teamService.listTeams("test", any(), any()) }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/team-management")
    inner class CreateTeam {
        @Test
        @DisplayName("should create team when admin")
        @WithMockUser(roles = ["ADMIN"])
        fun `should create team when admin`() {
            // Given
            val commandSlot = slot<CreateTeamCommand>()
            every { teamService.createTeam(capture(commandSlot)) } returns testTeam
            every { teamService.getTeamStatistics(any()) } returns testStatistics

            val requestBody =
                """
                {
                    "name": "new-team",
                    "displayName": "New Team",
                    "description": "New team description"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/team-management")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.name").value("test-team"))

            assert(commandSlot.captured.name == "new-team")
        }
    }

    @Nested
    @DisplayName("GET /api/v1/team-management/{teamId}")
    inner class GetTeam {
        @Test
        @DisplayName("should return team details")
        @WithMockUser(roles = ["USER"])
        fun `should return team details`() {
            // Given
            every { teamService.getTeamOrThrow(1L) } returns testTeam
            every { teamService.getTeamStatistics(1L) } returns testStatistics

            // When & Then
            mockMvc
                .perform(get("/api/v1/team-management/1"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("test-team"))
                .andExpect(jsonPath("$.memberCount").value(5))
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/team-management/{teamId}")
    inner class UpdateTeam {
        @Test
        @DisplayName("should update team")
        @WithMockUser(roles = ["USER"])
        fun `should update team`() {
            // Given
            every { teamService.updateTeam(any()) } returns testTeam
            every { teamService.getTeamStatistics(1L) } returns testStatistics

            val requestBody =
                """
                {
                    "displayName": "Updated Team",
                    "description": "Updated description"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    put("/api/v1/team-management/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(1))
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/team-management/{teamId}")
    inner class DeleteTeam {
        @Test
        @DisplayName("should delete team when admin")
        @WithMockUser(username = "admin@test.com", roles = ["ADMIN"])
        fun `should delete team when admin`() {
            // Given
            every { teamService.deleteTeam(any(), any()) } returns Unit

            // When & Then
            mockMvc
                .perform(delete("/api/v1/team-management/1"))
                .andExpect(status().isNoContent)

            verify(exactly = 1) { teamService.deleteTeam(1L, any()) }
        }
    }

    @Nested
    @DisplayName("GET /api/v1/team-management/{teamId}/members")
    inner class ListMembers {
        @Test
        @DisplayName("should return team members")
        @WithMockUser(roles = ["USER"])
        fun `should return team members`() {
            // Given
            val member =
                TeamMemberWithUserProjection(
                    memberId = 1L,
                    userId = 10L,
                    username = "Test User",
                    email = "user@test.com",
                    role = TeamRole.VIEWER,
                    joinedAt = now,
                )
            every { teamService.listMembers(1L) } returns listOf(member)

            // When & Then
            mockMvc
                .perform(get("/api/v1/team-management/1/members"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].userId").value(10))
                .andExpect(jsonPath("$[0].email").value("user@test.com"))
                .andExpect(jsonPath("$[0].role").value("VIEWER"))
        }
    }

    @Nested
    @DisplayName("POST /api/v1/team-management/{teamId}/members")
    inner class AddMember {
        @Test
        @DisplayName("should add member when admin")
        @WithMockUser(roles = ["ADMIN"])
        fun `should add member when admin`() {
            // Given
            val commandSlot = slot<AddTeamMemberCommand>()
            val member =
                TeamMemberWithUserProjection(
                    memberId = 1L,
                    userId = 10L,
                    username = "New User",
                    email = "newuser@test.com",
                    role = TeamRole.EDITOR,
                    joinedAt = now,
                )
            val memberEntity =
                TeamMemberEntity(
                    teamId = 1L,
                    userId = 10L,
                    role = TeamRole.EDITOR,
                )
            every { teamService.addMember(capture(commandSlot)) } returns memberEntity
            every { teamService.listMembers(1L) } returns listOf(member)

            val requestBody =
                """
                {
                    "userId": 10,
                    "role": "EDITOR"
                }
                """.trimIndent()

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/team-management/1/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody),
                ).andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(10))
                .andExpect(jsonPath("$.role").value("EDITOR"))

            // Verify command has correct values from path + body
            assert(commandSlot.captured.teamId == 1L)
            assert(commandSlot.captured.userId == 10L)
            assert(commandSlot.captured.role == TeamRole.EDITOR)
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/team-management/{teamId}/members/{userId}")
    inner class RemoveMember {
        @Test
        @DisplayName("should remove member when admin")
        @WithMockUser(roles = ["ADMIN"])
        fun `should remove member when admin`() {
            // Given
            val commandSlot = slot<RemoveTeamMemberCommand>()
            every { teamService.removeMember(capture(commandSlot)) } returns Unit

            // When & Then
            mockMvc
                .perform(delete("/api/v1/team-management/1/members/10"))
                .andExpect(status().isNoContent)

            // Verify command has correct values from path variables
            assert(commandSlot.captured.teamId == 1L)
            assert(commandSlot.captured.userId == 10L)
        }
    }

    @Nested
    @DisplayName("GET /api/v1/team-management/{teamId}/resources")
    inner class ListResources {
        @Test
        @DisplayName("should return team resources")
        @WithMockUser(roles = ["USER"])
        fun `should return team resources`() {
            // Given
            every { teamService.listTeamResources(1L) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/team-management/1/resources"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
        }
    }
}
