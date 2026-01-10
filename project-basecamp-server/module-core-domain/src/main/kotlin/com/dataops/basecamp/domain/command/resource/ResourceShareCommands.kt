package com.dataops.basecamp.domain.command.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType

/**
 * Command to create a new resource share.
 */
data class CreateResourceShareCommand(
    val ownerTeamId: Long,
    val sharedWithTeamId: Long,
    val resourceType: ShareableResourceType,
    val resourceId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
    val visibleToTeam: Boolean = true,
    val grantedBy: Long,
) {
    init {
        require(ownerTeamId != sharedWithTeamId) {
            "Cannot share a resource with the owning team"
        }
    }
}

/**
 * Command to update an existing resource share.
 */
data class UpdateResourceShareCommand(
    val shareId: Long,
    val permission: ResourcePermission? = null,
    val visibleToTeam: Boolean? = null,
    val updatedBy: Long,
)

/**
 * Command to revoke a resource share.
 * This will also cascade delete all associated UserResourceGrants.
 */
data class RevokeResourceShareCommand(
    val shareId: Long,
    val revokedBy: Long,
)
