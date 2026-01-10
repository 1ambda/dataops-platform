package com.dataops.basecamp.dto.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import java.time.LocalDateTime

// ==================== Request DTOs ====================

/**
 * Request DTO for creating a resource share.
 */
data class CreateResourceShareRequest(
    val sharedWithTeamId: Long,
    val resourceId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
    val visibleToTeam: Boolean = true,
)

/**
 * Request DTO for updating a resource share.
 */
data class UpdateResourceShareRequest(
    val permission: ResourcePermission? = null,
    val visibleToTeam: Boolean? = null,
)

/**
 * Request DTO for creating a user grant.
 */
data class CreateUserGrantRequest(
    val userId: Long,
    val permission: ResourcePermission = ResourcePermission.VIEWER,
)

/**
 * Request DTO for updating a user grant.
 */
data class UpdateUserGrantRequest(
    val permission: ResourcePermission,
)

// ==================== Response DTOs ====================

/**
 * DTO for resource share information.
 */
data class ResourceShareDto(
    val id: Long,
    val ownerTeamId: Long,
    val ownerTeamName: String?,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String?,
    val resourceType: ShareableResourceType,
    val resourceId: Long,
    val permission: ResourcePermission,
    val visibleToTeam: Boolean,
    val grantedBy: Long,
    val grantedAt: LocalDateTime,
    val grantCount: Int?,
)

/**
 * Summary DTO for share listing.
 */
data class ResourceShareSummaryDto(
    val id: Long,
    val sharedWithTeamId: Long,
    val sharedWithTeamName: String?,
    val resourceId: Long,
    val permission: ResourcePermission,
    val visibleToTeam: Boolean,
    val grantCount: Int,
    val grantedAt: LocalDateTime,
)

/**
 * DTO for user grant information.
 */
data class UserGrantDto(
    val id: Long,
    val shareId: Long,
    val userId: Long,
    val userEmail: String?,
    val userName: String?,
    val permission: ResourcePermission,
    val grantedBy: Long,
    val grantedAt: LocalDateTime,
)
