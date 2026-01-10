package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.domain.command.team.AddTeamMemberCommand
import com.dataops.basecamp.domain.command.team.CreateTeamCommand
import com.dataops.basecamp.domain.command.team.RemoveTeamMemberCommand
import com.dataops.basecamp.domain.command.team.UpdateTeamCommand
import com.dataops.basecamp.domain.command.team.UpdateTeamMemberRoleCommand
import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.projection.team.TeamMemberWithUserProjection
import com.dataops.basecamp.domain.projection.team.TeamStatisticsProjection
import com.dataops.basecamp.domain.service.TeamService
import com.dataops.basecamp.dto.team.AddTeamMemberRequest
import com.dataops.basecamp.dto.team.CreateTeamRequest
import com.dataops.basecamp.dto.team.TeamDetailDto
import com.dataops.basecamp.dto.team.TeamMemberDto
import com.dataops.basecamp.dto.team.TeamResourceSummaryDto
import com.dataops.basecamp.dto.team.TeamSummaryDto
import com.dataops.basecamp.dto.team.UpdateTeamMemberRequest
import com.dataops.basecamp.dto.team.UpdateTeamRequest
import com.dataops.basecamp.util.SecurityContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Team Management REST API Controller
 *
 * Provides endpoints for team CRUD and member management.
 * Base path: /api/v1/team-management
 *
 * Note: SQL Folder/Worksheet operations are handled by TeamController at /api/v1/teams
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/team-management")
@CrossOrigin
@Validated
@Tag(name = "Team Management", description = "Team and member management API")
@PreAuthorize("hasRole('ROLE_USER')")
class TeamManagementController(
    private val teamService: TeamService,
) {
    private val logger = KotlinLogging.logger {}

    // ==================== Team CRUD ====================

    /**
     * List all teams
     *
     * GET /api/v1/team-management
     */
    @Operation(
        summary = "List teams",
        description = "List all teams with optional name filter",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listTeams(
        @Parameter(description = "Filter by name (partial match)")
        @RequestParam(required = false) name: String?,
        @Parameter(description = "Page number (0-indexed)")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size")
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<TeamSummaryDto>> {
        logger.info { "Listing teams: name=$name, page=$page, size=$size" }

        val teams = teamService.listTeams(name = name, page = page, size = size)
        val dtos = teams.content.map { it.toSummaryDto() }

        return ResponseEntity.ok(dtos)
    }

    /**
     * Create a new team (Admin only)
     *
     * POST /api/v1/team-management
     */
    @Operation(
        summary = "Create team",
        description = "Create a new team (Admin only)",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Team created")
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun createTeam(
        @Valid @RequestBody request: CreateTeamRequest,
    ): ResponseEntity<TeamSummaryDto> {
        logger.info { "Creating team: ${request.name}" }

        val command =
            CreateTeamCommand(
                name = request.name,
                displayName = request.displayName,
                description = request.description,
            )

        val team = teamService.createTeam(command)

        return ResponseEntity.status(HttpStatus.CREATED).body(team.toSummaryDto())
    }

    /**
     * Get team details
     *
     * GET /api/v1/team-management/{teamId}
     */
    @Operation(
        summary = "Get team",
        description = "Get team details by ID",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{teamId}")
    fun getTeam(
        @PathVariable teamId: Long,
    ): ResponseEntity<TeamDetailDto> {
        logger.info { "Getting team: $teamId" }

        val team = teamService.getTeamOrThrow(teamId)
        val statistics = teamService.getTeamStatistics(teamId)

        return ResponseEntity.ok(team.toDetailDto(statistics))
    }

    /**
     * Update team (Manager+)
     *
     * PUT /api/v1/team-management/{teamId}
     */
    @Operation(
        summary = "Update team",
        description = "Update team information (Manager+ role required)",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Team updated")
    @PutMapping("/{teamId}")
    fun updateTeam(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: UpdateTeamRequest,
    ): ResponseEntity<TeamDetailDto> {
        logger.info { "Updating team: $teamId" }

        val command =
            UpdateTeamCommand(
                teamId = teamId,
                displayName = request.displayName,
                description = request.description,
            )

        val team = teamService.updateTeam(command)
        val statistics = teamService.getTeamStatistics(teamId)

        return ResponseEntity.ok(team.toDetailDto(statistics))
    }

    /**
     * Delete team (Admin only)
     *
     * DELETE /api/v1/team-management/{teamId}
     */
    @Operation(
        summary = "Delete team",
        description = "Delete a team (Admin only). Blocked if team has resources.",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Team deleted")
    @DeleteMapping("/{teamId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun deleteTeam(
        @PathVariable teamId: Long,
    ): ResponseEntity<Void> {
        logger.info { "Deleting team: $teamId" }

        val currentUserId = SecurityContext.getCurrentUserIdOrThrow()
        teamService.deleteTeam(teamId, currentUserId)

        return ResponseEntity.noContent().build()
    }

    // ==================== Team Members ====================

    /**
     * List team members
     *
     * GET /api/v1/team-management/{teamId}/members
     */
    @Operation(
        summary = "List team members",
        description = "List all members of a team",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{teamId}/members")
    fun listMembers(
        @PathVariable teamId: Long,
    ): ResponseEntity<List<TeamMemberDto>> {
        logger.info { "Listing members for team: $teamId" }

        val members = teamService.listMembers(teamId)
        val dtos = members.map { it.toDto() }

        return ResponseEntity.ok(dtos)
    }

    /**
     * Add member to team (Admin only)
     *
     * POST /api/v1/team-management/{teamId}/members
     */
    @Operation(
        summary = "Add team member",
        description = "Add a user to a team (Admin only)",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Member added")
    @PostMapping("/{teamId}/members")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun addMember(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: AddTeamMemberRequest,
    ): ResponseEntity<TeamMemberDto> {
        logger.info { "Adding member ${request.userId} to team: $teamId" }

        val command =
            AddTeamMemberCommand(
                teamId = teamId,
                userId = request.userId,
                role = request.role,
            )

        teamService.addMember(command)

        // Get member with user details for response
        val memberWithUser =
            teamService
                .listMembers(teamId)
                .find { it.userId == request.userId }
                ?: throw IllegalStateException("Failed to get member details")

        return ResponseEntity.status(HttpStatus.CREATED).body(memberWithUser.toDto())
    }

    /**
     * Update member role (Admin only)
     *
     * PUT /api/v1/team-management/{teamId}/members/{userId}
     */
    @Operation(
        summary = "Update member role",
        description = "Update a team member's role (Admin only)",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Role updated")
    @PutMapping("/{teamId}/members/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun updateMemberRole(
        @PathVariable teamId: Long,
        @PathVariable userId: Long,
        @Valid @RequestBody request: UpdateTeamMemberRequest,
    ): ResponseEntity<TeamMemberDto> {
        logger.info { "Updating role for member $userId in team: $teamId to ${request.role}" }

        val command =
            UpdateTeamMemberRoleCommand(
                teamId = teamId,
                userId = userId,
                newRole = request.role,
            )

        teamService.updateMemberRole(command)

        // Get updated member with user details for response
        val memberWithUser =
            teamService
                .listMembers(teamId)
                .find { it.userId == userId }
                ?: throw IllegalStateException("Failed to get member details")

        return ResponseEntity.ok(memberWithUser.toDto())
    }

    /**
     * Remove member from team (Admin only)
     *
     * DELETE /api/v1/team-management/{teamId}/members/{userId}
     */
    @Operation(
        summary = "Remove team member",
        description = "Remove a user from a team (Admin only)",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Member removed")
    @DeleteMapping("/{teamId}/members/{userId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    fun removeMember(
        @PathVariable teamId: Long,
        @PathVariable userId: Long,
    ): ResponseEntity<Void> {
        logger.info { "Removing member $userId from team: $teamId" }

        val command =
            RemoveTeamMemberCommand(
                teamId = teamId,
                userId = userId,
            )

        teamService.removeMember(command)

        return ResponseEntity.noContent().build()
    }

    // ==================== Team Resources ====================

    /**
     * List team resources
     *
     * GET /api/v1/team-management/{teamId}/resources
     */
    @Operation(
        summary = "List team resources",
        description = "List resources owned by a team grouped by type",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/{teamId}/resources")
    fun listResources(
        @PathVariable teamId: Long,
    ): ResponseEntity<List<TeamResourceSummaryDto>> {
        logger.info { "Listing resources for team: $teamId" }

        val resources = teamService.listTeamResources(teamId)
        val dtos = resources.map { TeamResourceSummaryDto(it.resourceType, it.count) }

        return ResponseEntity.ok(dtos)
    }

    // ==================== Mappers ====================

    private fun TeamEntity.toSummaryDto(): TeamSummaryDto {
        val memberCount = teamService.getTeamStatistics(this.id!!)?.memberCount ?: 0
        return TeamSummaryDto(
            id = this.id!!,
            name = this.name,
            displayName = this.displayName,
            description = this.description,
            memberCount = memberCount,
            createdAt = this.createdAt!!,
        )
    }

    private fun TeamEntity.toDetailDto(statistics: TeamStatisticsProjection?): TeamDetailDto =
        TeamDetailDto(
            id = this.id!!,
            name = this.name,
            displayName = this.displayName,
            description = this.description,
            memberCount = statistics?.memberCount ?: 0,
            resourceCounts = statistics?.resourceCounts ?: emptyMap(),
            createdAt = this.createdAt!!,
            updatedAt = this.updatedAt!!,
        )

    private fun TeamMemberWithUserProjection.toDto(): TeamMemberDto =
        TeamMemberDto(
            userId = this.userId,
            email = this.email,
            displayName = this.username,
            role = this.role,
            joinedAt = this.joinedAt,
        )
}
