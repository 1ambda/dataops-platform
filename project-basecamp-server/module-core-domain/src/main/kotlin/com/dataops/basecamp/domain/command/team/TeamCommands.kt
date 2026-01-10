package com.dataops.basecamp.domain.command.team

import com.dataops.basecamp.common.enums.TeamRole

/**
 * Command to create a new team.
 */
data class CreateTeamCommand(
    val name: String,
    val displayName: String,
    val description: String? = null,
) {
    init {
        require(name.isNotBlank()) { "Team name cannot be blank" }
        require(displayName.isNotBlank()) { "Display name cannot be blank" }
        require(name.matches(Regex("^[a-z0-9-]+$"))) {
            "Team name must be lowercase alphanumeric with hyphens"
        }
    }
}

/**
 * Command to update an existing team.
 */
data class UpdateTeamCommand(
    val teamId: Long,
    val displayName: String? = null,
    val description: String? = null,
)

/**
 * Command to add a member to a team.
 */
data class AddTeamMemberCommand(
    val teamId: Long,
    val userId: Long,
    val role: TeamRole = TeamRole.VIEWER,
)

/**
 * Command to remove a member from a team.
 */
data class RemoveTeamMemberCommand(
    val teamId: Long,
    val userId: Long,
)

/**
 * Command to update a team member's role.
 */
data class UpdateTeamMemberRoleCommand(
    val teamId: Long,
    val userId: Long,
    val newRole: TeamRole,
)
