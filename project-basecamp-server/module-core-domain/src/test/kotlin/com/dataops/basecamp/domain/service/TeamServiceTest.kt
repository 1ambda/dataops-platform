package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.common.exception.TeamAlreadyExistsException
import com.dataops.basecamp.common.exception.TeamHasResourcesException
import com.dataops.basecamp.common.exception.TeamMemberAlreadyExistsException
import com.dataops.basecamp.common.exception.TeamMemberNotFoundException
import com.dataops.basecamp.common.exception.TeamNotFoundException
import com.dataops.basecamp.domain.command.team.AddTeamMemberCommand
import com.dataops.basecamp.domain.command.team.CreateTeamCommand
import com.dataops.basecamp.domain.command.team.RemoveTeamMemberCommand
import com.dataops.basecamp.domain.command.team.UpdateTeamCommand
import com.dataops.basecamp.domain.command.team.UpdateTeamMemberRoleCommand
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.entity.team.TeamMemberEntity
import com.dataops.basecamp.domain.projection.team.TeamResourceCheckResult
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryDsl
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryJpa
import com.dataops.basecamp.domain.repository.team.TeamRepositoryDsl
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * TeamService 비즈니스 로직 단위 테스트
 *
 * MockK를 사용하여 의존성을 격리하고 순수한 비즈니스 로직을 테스트합니다.
 */
@DisplayName("TeamService 비즈니스 로직 테스트")
class TeamServiceTest {
    private val teamRepositoryJpa: TeamRepositoryJpa = mockk()
    private val teamRepositoryDsl: TeamRepositoryDsl = mockk()
    private val teamMemberRepositoryJpa: TeamMemberRepositoryJpa = mockk()
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl = mockk()
    private val userRepositoryJpa: UserRepositoryJpa = mockk()

    private val teamService =
        TeamService(
            teamRepositoryJpa,
            teamRepositoryDsl,
            teamMemberRepositoryJpa,
            teamMemberRepositoryDsl,
            userRepositoryJpa,
        )

    @Nested
    @DisplayName("팀 생성 테스트")
    inner class CreateTeamTest {
        @Test
        @DisplayName("새 팀 생성 성공")
        fun `should create team successfully`() {
            // Given
            val command =
                CreateTeamCommand(
                    name = "platform-team",
                    displayName = "Platform Team",
                    description = "Platform engineering team",
                )
            val savedTeam =
                TeamEntity(
                    name = command.name,
                    displayName = command.displayName,
                    description = command.description,
                )

            every { teamRepositoryJpa.existsByNameAndDeletedAtIsNull(command.name) } returns false
            every { teamRepositoryJpa.save(any()) } returns savedTeam

            // When
            val result = teamService.createTeam(command)

            // Then
            assertThat(result.name).isEqualTo(command.name)
            assertThat(result.displayName).isEqualTo(command.displayName)
            verify(exactly = 1) { teamRepositoryJpa.existsByNameAndDeletedAtIsNull(command.name) }
            verify(exactly = 1) { teamRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("중복된 팀 이름으로 생성 시 TeamAlreadyExistsException 발생")
        fun `should throw TeamAlreadyExistsException when team name exists`() {
            // Given
            val command =
                CreateTeamCommand(
                    name = "existing-team",
                    displayName = "Existing Team",
                )
            every { teamRepositoryJpa.existsByNameAndDeletedAtIsNull(command.name) } returns true

            // When & Then
            val exception =
                assertThrows<TeamAlreadyExistsException> {
                    teamService.createTeam(command)
                }

            assertThat(exception.message).contains("existing-team")
            verify(exactly = 1) { teamRepositoryJpa.existsByNameAndDeletedAtIsNull(command.name) }
            verify(exactly = 0) { teamRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("팀 조회 테스트")
    inner class GetTeamTest {
        @Test
        @DisplayName("ID로 팀 조회 성공")
        fun `should return team when found by id`() {
            // Given
            val teamId = 1L
            val team =
                TeamEntity(
                    name = "test-team",
                    displayName = "Test Team",
                )
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns team

            // When
            val result = teamService.getTeam(teamId)

            // Then
            assertThat(result).isNotNull
            assertThat(result?.name).isEqualTo("test-team")
        }

        @Test
        @DisplayName("존재하지 않는 ID로 조회 시 null 반환")
        fun `should return null when team not found`() {
            // Given
            val teamId = 999L
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns null

            // When
            val result = teamService.getTeam(teamId)

            // Then
            assertThat(result).isNull()
        }

        @Test
        @DisplayName("getTeamOrThrow - 팀 없을 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found`() {
            // Given
            val teamId = 999L
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns null

            // When & Then
            val exception =
                assertThrows<TeamNotFoundException> {
                    teamService.getTeamOrThrow(teamId)
                }

            assertThat(exception.message).contains("999")
        }
    }

    @Nested
    @DisplayName("팀 수정 테스트")
    inner class UpdateTeamTest {
        @Test
        @DisplayName("팀 정보 수정 성공")
        fun `should update team successfully`() {
            // Given
            val command =
                UpdateTeamCommand(
                    teamId = 1L,
                    displayName = "Updated Team",
                    description = "Updated description",
                )
            val team =
                TeamEntity(
                    name = "test-team",
                    displayName = "Original Team",
                    description = "Original description",
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every { teamRepositoryJpa.save(any()) } returns team

            // When
            val result = teamService.updateTeam(command)

            // Then
            assertThat(result.displayName).isEqualTo("Updated Team")
            verify(exactly = 1) { teamRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 팀 수정 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found`() {
            // Given
            val command =
                UpdateTeamCommand(
                    teamId = 999L,
                    displayName = "Updated Team",
                )
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns null

            // When & Then
            assertThrows<TeamNotFoundException> {
                teamService.updateTeam(command)
            }
            verify(exactly = 0) { teamRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("팀 삭제 테스트")
    inner class DeleteTeamTest {
        @Test
        @DisplayName("리소스가 없는 팀 삭제 성공")
        fun `should delete team successfully when no resources`() {
            // Given
            val teamId = 1L
            val team = TeamEntity(name = "test-team", displayName = "Test Team")
            val resourceCheckResult = TeamResourceCheckResult(hasResources = false)

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns team
            every { teamRepositoryDsl.hasResources(teamId) } returns resourceCheckResult
            every { teamRepositoryJpa.save(any()) } returns team

            // When
            teamService.deleteTeam(teamId, deletedBy = 100L)

            // Then
            verify(exactly = 1) { teamRepositoryDsl.hasResources(teamId) }
            verify(exactly = 1) { teamRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("리소스가 있는 팀 삭제 시 TeamHasResourcesException 발생")
        fun `should throw TeamHasResourcesException when team has resources`() {
            // Given
            val teamId = 1L
            val team = TeamEntity(name = "test-team", displayName = "Test Team")
            val resourceCheckResult =
                TeamResourceCheckResult(
                    hasResources = true,
                    metricCount = 5,
                    datasetCount = 3,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns team
            every { teamRepositoryDsl.hasResources(teamId) } returns resourceCheckResult

            // When & Then
            val exception =
                assertThrows<TeamHasResourcesException> {
                    teamService.deleteTeam(teamId, deletedBy = 100L)
                }

            assertThat(exception.message).contains("Metric(5)")
            assertThat(exception.message).contains("Dataset(3)")
            verify(exactly = 0) { teamRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 팀 삭제 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found`() {
            // Given
            val teamId = 999L
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) } returns null

            // When & Then
            assertThrows<TeamNotFoundException> {
                teamService.deleteTeam(teamId, deletedBy = 100L)
            }
            verify(exactly = 0) { teamRepositoryDsl.hasResources(any()) }
            verify(exactly = 0) { teamRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("팀 멤버 추가 테스트")
    inner class AddMemberTest {
        @Test
        @DisplayName("팀에 멤버 추가 성공")
        fun `should add member successfully`() {
            // Given
            val command =
                AddTeamMemberCommand(
                    teamId = 1L,
                    userId = 10L,
                    role = TeamRole.EDITOR,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")
            val savedMember =
                TeamMemberEntity(
                    teamId = command.teamId,
                    userId = command.userId,
                    role = command.role,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                false
            every { teamMemberRepositoryJpa.save(any()) } returns savedMember

            // When
            val result = teamService.addMember(command)

            // Then
            assertThat(result.teamId).isEqualTo(command.teamId)
            assertThat(result.userId).isEqualTo(command.userId)
            assertThat(result.role).isEqualTo(TeamRole.EDITOR)
        }

        @Test
        @DisplayName("존재하지 않는 팀에 멤버 추가 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found`() {
            // Given
            val command =
                AddTeamMemberCommand(
                    teamId = 999L,
                    userId = 10L,
                    role = TeamRole.VIEWER,
                )
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns null

            // When & Then
            assertThrows<TeamNotFoundException> {
                teamService.addMember(command)
            }
        }

        @Test
        @DisplayName("이미 멤버인 사용자 추가 시 TeamMemberAlreadyExistsException 발생")
        fun `should throw TeamMemberAlreadyExistsException when member already exists`() {
            // Given
            val command =
                AddTeamMemberCommand(
                    teamId = 1L,
                    userId = 10L,
                    role = TeamRole.VIEWER,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                true

            // When & Then
            assertThrows<TeamMemberAlreadyExistsException> {
                teamService.addMember(command)
            }
        }
    }

    @Nested
    @DisplayName("팀 멤버 제거 테스트")
    inner class RemoveMemberTest {
        @Test
        @DisplayName("팀에서 멤버 제거 성공")
        fun `should remove member successfully`() {
            // Given
            val command =
                RemoveTeamMemberCommand(
                    teamId = 1L,
                    userId = 10L,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")
            val member =
                TeamMemberEntity(
                    teamId = command.teamId,
                    userId = command.userId,
                    role = TeamRole.VIEWER,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                member
            every { teamMemberRepositoryJpa.save(any()) } returns member

            // When
            teamService.removeMember(command)

            // Then
            verify(exactly = 1) { teamMemberRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 멤버 제거 시 TeamMemberNotFoundException 발생")
        fun `should throw TeamMemberNotFoundException when member not found`() {
            // Given
            val command =
                RemoveTeamMemberCommand(
                    teamId = 1L,
                    userId = 999L,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                null

            // When & Then
            assertThrows<TeamMemberNotFoundException> {
                teamService.removeMember(command)
            }
        }

        @Test
        @DisplayName("존재하지 않는 팀에서 멤버 제거 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found for remove`() {
            // Given
            val command =
                RemoveTeamMemberCommand(
                    teamId = 999L,
                    userId = 10L,
                )
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns null

            // When & Then
            assertThrows<TeamNotFoundException> {
                teamService.removeMember(command)
            }
            verify(exactly = 0) { teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(any(), any()) }
        }
    }

    @Nested
    @DisplayName("팀 멤버 역할 변경 테스트")
    inner class UpdateMemberRoleTest {
        @Test
        @DisplayName("멤버 역할 변경 성공")
        fun `should update member role successfully`() {
            // Given
            val command =
                UpdateTeamMemberRoleCommand(
                    teamId = 1L,
                    userId = 10L,
                    newRole = TeamRole.MANAGER,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")
            val member =
                TeamMemberEntity(
                    teamId = command.teamId,
                    userId = command.userId,
                    role = TeamRole.VIEWER,
                )

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                member
            every { teamMemberRepositoryJpa.save(any()) } returns member

            // When
            val result = teamService.updateMemberRole(command)

            // Then
            assertThat(result.role).isEqualTo(TeamRole.MANAGER)
            verify(exactly = 1) { teamMemberRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("존재하지 않는 팀에서 역할 변경 시 TeamNotFoundException 발생")
        fun `should throw TeamNotFoundException when team not found for role update`() {
            // Given
            val command =
                UpdateTeamMemberRoleCommand(
                    teamId = 999L,
                    userId = 10L,
                    newRole = TeamRole.MANAGER,
                )
            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns null

            // When & Then
            assertThrows<TeamNotFoundException> {
                teamService.updateMemberRole(command)
            }
            verify(exactly = 0) { teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(any(), any()) }
        }

        @Test
        @DisplayName("존재하지 않는 멤버 역할 변경 시 TeamMemberNotFoundException 발생")
        fun `should throw TeamMemberNotFoundException when member not found for role update`() {
            // Given
            val command =
                UpdateTeamMemberRoleCommand(
                    teamId = 1L,
                    userId = 999L,
                    newRole = TeamRole.MANAGER,
                )
            val team = TeamEntity(name = "test-team", displayName = "Test Team")

            every { teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) } returns team
            every {
                teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(
                    command.teamId,
                    command.userId,
                )
            } returns
                null

            // When & Then
            assertThrows<TeamMemberNotFoundException> {
                teamService.updateMemberRole(command)
            }
            verify(exactly = 0) { teamMemberRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("멤버십 확인 테스트")
    inner class MembershipTest {
        @Test
        @DisplayName("멤버십 존재 확인 - true")
        fun `should return true when user is member`() {
            // Given
            val teamId = 1L
            val userId = 10L
            every { teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(teamId, userId) } returns true

            // When
            val result = teamService.isMember(teamId, userId)

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("멤버십 존재 확인 - false")
        fun `should return false when user is not member`() {
            // Given
            val teamId = 1L
            val userId = 999L
            every { teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(teamId, userId) } returns false

            // When
            val result = teamService.isMember(teamId, userId)

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("특정 역할 보유 확인")
        fun `should check if user has specific role`() {
            // Given
            val teamId = 1L
            val userId = 10L
            val role = TeamRole.MANAGER
            every { teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, role) } returns true

            // When
            val result = teamService.hasRole(teamId, userId, role)

            // Then
            assertThat(result).isTrue()
        }
    }
}
