package com.dataops.basecamp.domain.repository.resource

import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import org.springframework.data.domain.Page

/**
 * Team Resource Share Repository DSL Interface
 *
 * Defines complex query operations for TeamResourceShareEntity using QueryDSL.
 */
interface TeamResourceShareRepositoryDsl {
    /**
     * Find shares by resource type with pagination.
     */
    fun findByResourceType(
        resourceType: ShareableResourceType,
        page: Int,
        size: Int,
    ): Page<TeamResourceShareEntity>

    /**
     * Find shares for a specific owner team and resource type.
     */
    fun findByOwnerTeamIdAndResourceType(
        ownerTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity>

    /**
     * Find shares that a team has received for a specific resource type.
     */
    fun findBySharedWithTeamIdAndResourceType(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity>

    /**
     * Find visible shares for a consumer team (where visibleToTeam = true).
     */
    fun findVisibleSharesForTeam(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType?,
    ): List<TeamResourceShareEntity>

    /**
     * Check if a user has access to a resource through any share.
     * User must be a member of a team that has been shared the resource.
     */
    fun hasShareAccess(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean

    /**
     * Get the share for a specific resource if the user's team has access.
     */
    fun findShareForUserTeam(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): TeamResourceShareEntity?
}
