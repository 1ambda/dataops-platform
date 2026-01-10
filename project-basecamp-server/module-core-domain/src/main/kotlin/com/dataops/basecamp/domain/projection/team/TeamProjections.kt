package com.dataops.basecamp.domain.projection.team

import com.dataops.basecamp.common.enums.TeamResourceType
import com.dataops.basecamp.common.enums.TeamRole
import java.time.LocalDateTime

/**
 * Projection for team statistics including member count and resource counts.
 */
data class TeamStatisticsProjection(
    val teamId: Long,
    val memberCount: Int,
    val resourceCounts: Map<TeamResourceType, Int>,
)

/**
 * Projection for team member with user details.
 */
data class TeamMemberWithUserProjection(
    val memberId: Long,
    val userId: Long,
    val username: String,
    val email: String,
    val role: TeamRole,
    val joinedAt: LocalDateTime,
)

/**
 * Result of checking if a team has resources (for deletion validation).
 */
data class TeamResourceCheckResult(
    val hasResources: Boolean,
    val sqlFolderCount: Int = 0,
    val sqlWorksheetCount: Int = 0,
    val metricCount: Int = 0,
    val datasetCount: Int = 0,
    val workflowCount: Int = 0,
    val qualityCount: Int = 0,
    val githubRepoCount: Int = 0,
    val memberCount: Int = 0,
) {
    /**
     * Generates an error message listing all non-zero resource counts.
     */
    fun toErrorMessage(): String {
        val resources = mutableListOf<String>()
        if (sqlFolderCount > 0) resources.add("SqlFolder($sqlFolderCount)")
        if (sqlWorksheetCount > 0) resources.add("SqlWorksheet($sqlWorksheetCount)")
        if (metricCount > 0) resources.add("Metric($metricCount)")
        if (datasetCount > 0) resources.add("Dataset($datasetCount)")
        if (workflowCount > 0) resources.add("Workflow($workflowCount)")
        if (qualityCount > 0) resources.add("Quality($qualityCount)")
        if (githubRepoCount > 0) resources.add("GitHubRepo($githubRepoCount)")
        if (memberCount > 0) resources.add("Member($memberCount)")
        return "Cannot delete team. Has resources: ${resources.joinToString(", ")}"
    }

    companion object {
        /**
         * Creates an empty result indicating no resources.
         */
        fun empty(): TeamResourceCheckResult = TeamResourceCheckResult(hasResources = false)
    }
}

/**
 * Summary of team resources grouped by type.
 */
data class TeamResourceSummaryProjection(
    val resourceType: TeamResourceType,
    val count: Int,
)
