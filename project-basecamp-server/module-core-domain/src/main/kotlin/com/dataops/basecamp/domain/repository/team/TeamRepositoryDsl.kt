package com.dataops.basecamp.domain.repository.team

import com.dataops.basecamp.domain.entity.team.TeamEntity
import com.dataops.basecamp.domain.projection.team.TeamResourceCheckResult
import com.dataops.basecamp.domain.projection.team.TeamStatisticsProjection
import org.springframework.data.domain.Page

/**
 * Team Repository DSL Interface
 *
 * Defines complex query operations for TeamEntity using QueryDSL.
 */
interface TeamRepositoryDsl {
    /**
     * Find teams with pagination and optional filters.
     */
    fun findByConditions(
        name: String? = null,
        page: Int = 0,
        size: Int = 20,
    ): Page<TeamEntity>

    /**
     * Check if team has any resources (blocks deletion).
     */
    fun hasResources(teamId: Long): TeamResourceCheckResult

    /**
     * Get team statistics (member count, resource counts).
     */
    fun getTeamStatistics(teamId: Long): TeamStatisticsProjection?
}
