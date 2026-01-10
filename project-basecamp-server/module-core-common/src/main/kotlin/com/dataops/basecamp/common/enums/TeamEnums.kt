package com.dataops.basecamp.common.enums

/**
 * Team member role defining access level within a team.
 */
enum class TeamRole {
    /** Full access, manage members, approve shares, manage settings */
    MANAGER,

    /** Create, update, delete team resources */
    EDITOR,

    /** Read-only access, execute queries */
    VIEWER,
}

/**
 * Type of internal resource that can be owned by teams.
 */
enum class TeamResourceType {
    METRIC,
    DATASET,
    WORKFLOW,
    QUALITY,
    GITHUB_REPO,
    SQL_FOLDER,
    SQL_WORKSHEET,
    QUERY_HISTORY,
}
