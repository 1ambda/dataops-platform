package com.dataops.basecamp.dto.team

import com.dataops.basecamp.common.enums.TeamResourceType
import com.dataops.basecamp.common.enums.TeamRole
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

// ==================== Request DTOs ====================

/**
 * Request DTO for creating a team.
 */
data class CreateTeamRequest(
    @field:NotBlank(message = "Team name is required")
    @field:Size(max = 50, message = "Team name must be at most 50 characters")
    @field:Pattern(
        regexp = "^[a-z0-9-]+$",
        message = "Team name must be lowercase alphanumeric with hyphens",
    )
    val name: String,
    @field:NotBlank(message = "Display name is required")
    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String,
    @field:Size(max = 500, message = "Description must be at most 500 characters")
    val description: String? = null,
)

/**
 * Request DTO for updating a team.
 */
data class UpdateTeamRequest(
    @field:Size(max = 100, message = "Display name must be at most 100 characters")
    val displayName: String? = null,
    @field:Size(max = 500, message = "Description must be at most 500 characters")
    val description: String? = null,
)

/**
 * Request DTO for adding a team member.
 */
data class AddTeamMemberRequest(
    val userId: Long,
    val role: TeamRole = TeamRole.VIEWER,
)

/**
 * Request DTO for updating a team member's role.
 */
data class UpdateTeamMemberRequest(
    val role: TeamRole,
)

/**
 * Request DTO for setting user's default team.
 */
data class SetDefaultTeamRequest(
    val teamId: Long?,
)

// ==================== Response DTOs ====================

/**
 * Summary DTO for team listing.
 */
data class TeamSummaryDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val createdAt: LocalDateTime,
)

/**
 * Detailed DTO for single team view.
 */
data class TeamDetailDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    val memberCount: Int,
    val resourceCounts: Map<TeamResourceType, Int>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

/**
 * DTO for team member information.
 */
data class TeamMemberDto(
    val userId: Long,
    val email: String,
    val displayName: String,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
)

/**
 * DTO for team resource summary.
 */
data class TeamResourceSummaryDto(
    val resourceType: TeamResourceType,
    val count: Int,
)

/**
 * DTO for user's team membership information.
 */
data class UserTeamDto(
    val id: Long,
    val name: String,
    val displayName: String,
    val role: TeamRole,
)
