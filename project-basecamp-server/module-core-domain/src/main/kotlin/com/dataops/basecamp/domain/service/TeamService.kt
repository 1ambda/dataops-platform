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
import com.dataops.basecamp.domain.projection.team.TeamMemberWithUserProjection
import com.dataops.basecamp.domain.projection.team.TeamResourceSummaryProjection
import com.dataops.basecamp.domain.projection.team.TeamStatisticsProjection
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryDsl
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryJpa
import com.dataops.basecamp.domain.repository.team.TeamRepositoryDsl
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import com.dataops.basecamp.domain.repository.user.UserRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Team Service
 *
 * Handles team management operations including team CRUD and member management.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class TeamService(
    private val teamRepositoryJpa: TeamRepositoryJpa,
    private val teamRepositoryDsl: TeamRepositoryDsl,
    private val teamMemberRepositoryJpa: TeamMemberRepositoryJpa,
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl,
    private val userRepositoryJpa: UserRepositoryJpa,
) {
    private val log = LoggerFactory.getLogger(TeamService::class.java)

    // ==================== Team CRUD Operations ====================

    /**
     * Creates a new team.
     *
     * @param command The create team command
     * @return The created team entity
     * @throws TeamAlreadyExistsException if a team with the same name already exists
     */
    @Transactional
    fun createTeam(command: CreateTeamCommand): TeamEntity {
        log.info("Creating team: ${command.name}")

        if (teamRepositoryJpa.existsByNameAndDeletedAtIsNull(command.name)) {
            throw TeamAlreadyExistsException(command.name)
        }

        val team =
            TeamEntity(
                name = command.name,
                displayName = command.displayName,
                description = command.description,
            )

        return teamRepositoryJpa.save(team)
    }

    /**
     * Updates an existing team.
     *
     * @param command The update team command
     * @return The updated team entity
     * @throws TeamNotFoundException if the team is not found
     */
    @Transactional
    fun updateTeam(command: UpdateTeamCommand): TeamEntity {
        log.info("Updating team: ${command.teamId}")

        val team =
            teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId)
                ?: throw TeamNotFoundException(command.teamId)

        team.update(
            displayName = command.displayName,
            description = command.description,
        )

        return teamRepositoryJpa.save(team)
    }

    /**
     * Soft deletes a team if it has no resources.
     *
     * @param teamId The team ID
     * @param deletedBy The user ID performing the deletion
     * @throws TeamNotFoundException if the team is not found
     * @throws TeamHasResourcesException if the team has resources
     */
    @Transactional
    fun deleteTeam(
        teamId: Long,
        deletedBy: Long,
    ) {
        log.info("Deleting team: $teamId")

        val team =
            teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId)
                ?: throw TeamNotFoundException(teamId)

        val checkResult = teamRepositoryDsl.hasResources(teamId)
        if (checkResult.hasResources) {
            throw TeamHasResourcesException(teamId, checkResult.toErrorMessage())
        }

        team.delete(deletedBy)
        teamRepositoryJpa.save(team)
    }

    /**
     * Gets a team by ID.
     *
     * @param teamId The team ID
     * @return The team entity or null if not found
     */
    fun getTeam(teamId: Long): TeamEntity? = teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId)

    /**
     * Gets a team by ID, throwing an exception if not found.
     *
     * @param teamId The team ID
     * @return The team entity
     * @throws TeamNotFoundException if the team is not found
     */
    fun getTeamOrThrow(teamId: Long): TeamEntity =
        teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId)
            ?: throw TeamNotFoundException(teamId)

    /**
     * Gets a team by name.
     *
     * @param name The team name
     * @return The team entity or null if not found
     */
    fun getTeamByName(name: String): TeamEntity? = teamRepositoryJpa.findByNameAndDeletedAtIsNull(name)

    /**
     * Lists all teams.
     *
     * @return List of all teams
     */
    fun listTeams(): List<TeamEntity> = teamRepositoryJpa.findAllByDeletedAtIsNull()

    /**
     * Lists teams with pagination and filters.
     *
     * @param name Optional name filter
     * @param page Page number (0-indexed)
     * @param size Page size
     * @return Page of teams
     */
    fun listTeams(
        name: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): Page<TeamEntity> =
        teamRepositoryDsl.findByConditions(
            name = name,
            page = page,
            size = size,
        )

    /**
     * Gets team statistics including member count and resource counts.
     *
     * @param teamId The team ID
     * @return Team statistics or null if team not found
     */
    fun getTeamStatistics(teamId: Long): TeamStatisticsProjection? = teamRepositoryDsl.getTeamStatistics(teamId)

    // ==================== Team Member Operations ====================

    /**
     * Adds a member to a team.
     *
     * @param command The add team member command
     * @return The created team member entity
     * @throws TeamNotFoundException if the team is not found
     * @throws TeamMemberAlreadyExistsException if the user is already a member
     */
    @Transactional
    fun addMember(command: AddTeamMemberCommand): TeamMemberEntity {
        log.info("Adding member ${command.userId} to team ${command.teamId} with role ${command.role}")

        // Verify team exists
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) == null) {
            throw TeamNotFoundException(command.teamId)
        }

        // Check if already a member
        if (teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(command.teamId, command.userId)) {
            throw TeamMemberAlreadyExistsException(command.teamId, command.userId)
        }

        val member =
            TeamMemberEntity(
                teamId = command.teamId,
                userId = command.userId,
                role = command.role,
            )

        return teamMemberRepositoryJpa.save(member)
    }

    /**
     * Removes a member from a team.
     *
     * @param command The remove team member command
     * @throws TeamNotFoundException if the team is not found
     * @throws TeamMemberNotFoundException if the member is not found
     */
    @Transactional
    fun removeMember(command: RemoveTeamMemberCommand) {
        log.info("Removing member ${command.userId} from team ${command.teamId}")
        // TODO: Phase 2 - Remove user's resource permissions when removed from team

        // Verify team exists
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) == null) {
            throw TeamNotFoundException(command.teamId)
        }

        // Verify member exists
        val member =
            teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(command.teamId, command.userId)
                ?: throw TeamMemberNotFoundException(command.teamId, command.userId)

        member.delete(command.userId)
        teamMemberRepositoryJpa.save(member)
    }

    /**
     * Updates a team member's role.
     *
     * @param command The update team member role command
     * @return The updated team member entity
     * @throws TeamNotFoundException if the team is not found
     * @throws TeamMemberNotFoundException if the member is not found
     */
    @Transactional
    fun updateMemberRole(command: UpdateTeamMemberRoleCommand): TeamMemberEntity {
        log.info("Updating role for member ${command.userId} in team ${command.teamId} to ${command.newRole}")

        // Verify team exists
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.teamId) == null) {
            throw TeamNotFoundException(command.teamId)
        }

        val member =
            teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(command.teamId, command.userId)
                ?: throw TeamMemberNotFoundException(command.teamId, command.userId)

        member.updateRole(command.newRole)

        return teamMemberRepositoryJpa.save(member)
    }

    /**
     * Lists members of a team with user details.
     *
     * @param teamId The team ID
     * @return List of team members with user details
     * @throws TeamNotFoundException if the team is not found
     */
    fun listMembers(teamId: Long): List<TeamMemberWithUserProjection> {
        // Verify team exists
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) == null) {
            throw TeamNotFoundException(teamId)
        }

        return teamMemberRepositoryDsl.findMembersWithUserByTeamId(teamId)
    }

    /**
     * Gets a team member by team ID and user ID.
     *
     * @param teamId The team ID
     * @param userId The user ID
     * @return The team member entity or null if not found
     */
    fun getMember(
        teamId: Long,
        userId: Long,
    ): TeamMemberEntity? = teamMemberRepositoryJpa.findByTeamIdAndUserIdAndDeletedAtIsNull(teamId, userId)

    /**
     * Checks if a user is a member of a team.
     *
     * @param teamId The team ID
     * @param userId The user ID
     * @return true if the user is a member
     */
    fun isMember(
        teamId: Long,
        userId: Long,
    ): Boolean = teamMemberRepositoryJpa.existsByTeamIdAndUserIdAndDeletedAtIsNull(teamId, userId)

    /**
     * Checks if a user has a specific role in a team.
     *
     * @param teamId The team ID
     * @param userId The user ID
     * @param role The role to check
     * @return true if the user has the specified role
     */
    fun hasRole(
        teamId: Long,
        userId: Long,
        role: TeamRole,
    ): Boolean = teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, role)

    /**
     * Checks if a user has any of the specified roles in a team.
     *
     * @param teamId The team ID
     * @param userId The user ID
     * @param roles The roles to check
     * @return true if the user has any of the specified roles
     */
    fun hasAnyRole(
        teamId: Long,
        userId: Long,
        roles: List<TeamRole>,
    ): Boolean = teamMemberRepositoryDsl.hasAnyRoleInTeam(teamId, userId, roles)

    /**
     * Gets teams that a user is a member of.
     *
     * @param userId The user ID
     * @return List of team IDs
     */
    fun getTeamIdsByUserId(userId: Long): List<Long> = teamMemberRepositoryDsl.findTeamIdsByUserIdAndRoles(userId)

    /**
     * Gets teams that a user is a member of with their membership details.
     *
     * @param userId The user ID
     * @return List of team entities with user's role
     */
    fun getTeamsByUserId(userId: Long): List<Pair<TeamEntity, TeamRole>> {
        val memberships = teamMemberRepositoryJpa.findByUserIdAndDeletedAtIsNull(userId)
        return memberships.mapNotNull { membership ->
            teamRepositoryJpa.findByIdAndDeletedAtIsNull(membership.teamId)?.let { team ->
                Pair(team, membership.role)
            }
        }
    }

    // ==================== Team Resources ====================

    /**
     * Lists team resources grouped by type.
     * For Phase 1, this returns empty as no resources have teamId FK yet.
     *
     * @param teamId The team ID
     * @return List of resource summaries grouped by type
     */
    fun listTeamResources(teamId: Long): List<TeamResourceSummaryProjection> {
        // Verify team exists
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(teamId) == null) {
            throw TeamNotFoundException(teamId)
        }

        // Phase 1: Return empty list as resources don't have teamId FK yet
        // In future phases, aggregate counts from:
        // - MetricEntity, DatasetEntity, WorkflowEntity, QualitySpecEntity
        // - GitHubRepoEntity, SqlFolderEntity, SqlWorksheetEntity, QueryHistoryEntity
        return emptyList()
    }
}
