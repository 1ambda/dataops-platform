package com.dataops.basecamp.domain.projection.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType

/**
 * Projection for a resource with ownership information.
 */
data class ResourceWithOwnershipProjection(
    val resourceId: Long,
    val resourceType: ShareableResourceType,
    val resourceName: String,
    val ownerTeamId: Long,
    val ownerTeamName: String,
    val isOwned: Boolean,
    val sharePermission: ResourcePermission?,
    val grantPermission: ResourcePermission?,
    val visibleToTeam: Boolean?,
)

/**
 * Projection for a share with its grant count.
 */
data class ShareWithGrantsProjection(
    val shareId: Long,
    val resourceId: Long,
    val resourceName: String,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String,
    val permission: ResourcePermission,
    val grantCount: Int,
)
