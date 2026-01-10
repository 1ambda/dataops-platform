package com.dataops.basecamp.domain.command.resource

import com.dataops.basecamp.common.enums.ResourcePermission

/**
 * Command to create a new user grant within a share.
 */
data class CreateUserGrantCommand(
    val shareId: Long,
    val userId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
    val grantedBy: Long,
)

/**
 * Command to update an existing user grant.
 */
data class UpdateUserGrantCommand(
    val grantId: Long,
    val permission: ResourcePermission,
    val updatedBy: Long,
)

/**
 * Command to revoke a user grant.
 */
data class RevokeUserGrantCommand(
    val grantId: Long,
    val revokedBy: Long,
)
