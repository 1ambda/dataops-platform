package com.dataops.basecamp.dto

import com.dataops.basecamp.common.enums.TeamRole

/**
 * 세션 응답 DTO
 */
data class SessionResponse(
    val authenticated: Boolean,
    val userId: String,
    val email: String,
    val roles: List<String>,
    // Team information added in Phase 1
    val teams: List<UserTeamInfo> = emptyList(),
    val defaultTeamId: Long? = null,
    val defaultTeamName: String? = null,
)

/**
 * User's team membership information for session response.
 */
data class UserTeamInfo(
    val id: Long,
    val name: String,
    val displayName: String,
    val role: TeamRole,
)
