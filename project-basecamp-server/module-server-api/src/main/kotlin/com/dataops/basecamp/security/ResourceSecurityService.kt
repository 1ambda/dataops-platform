package com.dataops.basecamp.security

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.common.enums.TeamRole
import com.dataops.basecamp.common.enums.UserRole
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryDsl
import com.dataops.basecamp.domain.repository.team.TeamMemberRepositoryDsl
import com.dataops.basecamp.util.SecurityContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Resource Security Service
 *
 * Provides security expression methods for resource access control.
 * Used with @PreAuthorize annotations in controllers.
 *
 * Usage:
 * @PreAuthorize("@resourceSecurity.canView(#resourceType, #resourceId)")
 * @PreAuthorize("@resourceSecurity.canEdit(#resourceType, #resourceId)")
 * @PreAuthorize("@resourceSecurity.canShare(#resourceType, #resourceId)")
 */
@Component("resourceSecurity")
class ResourceSecurityService(
    private val teamMemberRepositoryDsl: TeamMemberRepositoryDsl,
    private val teamResourceShareRepositoryDsl: TeamResourceShareRepositoryDsl,
    private val userResourceGrantRepositoryDsl: UserResourceGrantRepositoryDsl,
) {
    private val log = LoggerFactory.getLogger(ResourceSecurityService::class.java)

    /**
     * Check if current user can view a resource.
     * True if: Admin, Owner Team member, or has Grant for shared resource.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user can view the resource
     */
    fun canView(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean {
        val userId = SecurityContext.getCurrentUserId() ?: return false

        // Admin can access everything
        if (SecurityContext.hasRole(UserRole.ADMIN)) {
            log.debug("User $userId has ADMIN role - granting view access")
            return true
        }

        // Check if user is member of owner team
        val ownerTeamId = getResourceOwnerTeamId(resourceType, resourceId)
        if (ownerTeamId != null && isTeamMember(ownerTeamId, userId)) {
            log.debug("User $userId is member of owner team $ownerTeamId - granting view access")
            return true
        }

        // Check if user has grant for shared resource
        if (userResourceGrantRepositoryDsl.hasGrantForResource(userId, resourceType, resourceId)) {
            log.debug("User $userId has grant for resource - granting view access")
            return true
        }

        log.debug("User $userId does not have view access to $resourceType:$resourceId")
        return false
    }

    /**
     * Check if current user can edit a resource.
     * True if: Admin, Owner Team editor+, or has EDITOR grant.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user can edit the resource
     */
    fun canEdit(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean {
        val userId = SecurityContext.getCurrentUserId() ?: return false

        // Admin can edit everything
        if (SecurityContext.hasRole(UserRole.ADMIN)) {
            log.debug("User $userId has ADMIN role - granting edit access")
            return true
        }

        // Check if user is editor+ in owner team
        val ownerTeamId = getResourceOwnerTeamId(resourceType, resourceId)
        if (ownerTeamId != null && isTeamEditor(ownerTeamId, userId)) {
            log.debug("User $userId is editor+ in owner team $ownerTeamId - granting edit access")
            return true
        }

        // Check if user has EDITOR grant
        if (userResourceGrantRepositoryDsl.hasEditorGrantForResource(userId, resourceType, resourceId)) {
            log.debug("User $userId has EDITOR grant for resource - granting edit access")
            return true
        }

        log.debug("User $userId does not have edit access to $resourceType:$resourceId")
        return false
    }

    /**
     * Check if current user can manage shares for a resource.
     * True if: Admin or Owner Team manager.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user can manage shares for the resource
     */
    fun canShare(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean {
        val userId = SecurityContext.getCurrentUserId() ?: return false

        // Admin can share everything
        if (SecurityContext.hasRole(UserRole.ADMIN)) {
            log.debug("User $userId has ADMIN role - granting share access")
            return true
        }

        // Check if user is manager in owner team
        val ownerTeamId = getResourceOwnerTeamId(resourceType, resourceId)
        if (ownerTeamId != null && isTeamManager(ownerTeamId, userId)) {
            log.debug("User $userId is manager in owner team $ownerTeamId - granting share access")
            return true
        }

        log.debug("User $userId does not have share access to $resourceType:$resourceId")
        return false
    }

    /**
     * Check if current user can view grants for a share.
     * True if: Admin, or manager of the owner team.
     *
     * @param ownerTeamId The owner team ID
     * @return true if the user can view grants
     */
    fun canViewGrants(ownerTeamId: Long): Boolean {
        val userId = SecurityContext.getCurrentUserId() ?: return false

        if (SecurityContext.hasRole(UserRole.ADMIN)) {
            return true
        }

        return isTeamManager(ownerTeamId, userId)
    }

    /**
     * Check if current user can manage grants for a share.
     * True if: Admin or manager of the owner team.
     *
     * @param ownerTeamId The owner team ID
     * @return true if the user can manage grants
     */
    fun canManageGrants(ownerTeamId: Long): Boolean = canViewGrants(ownerTeamId)

    /**
     * Get the effective permission for the current user on a resource.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return The effective permission or null if no access
     */
    fun getEffectivePermission(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): ResourcePermission? {
        val userId = SecurityContext.getCurrentUserId() ?: return null

        // Admin has full access
        if (SecurityContext.hasRole(UserRole.ADMIN)) {
            return ResourcePermission.EDITOR
        }

        // Check if user is owner team member
        val ownerTeamId = getResourceOwnerTeamId(resourceType, resourceId)
        if (ownerTeamId != null) {
            val teamRole = getTeamRole(ownerTeamId, userId)
            if (teamRole != null) {
                return when (teamRole) {
                    TeamRole.MANAGER, TeamRole.EDITOR -> ResourcePermission.EDITOR
                    TeamRole.VIEWER -> ResourcePermission.VIEWER
                }
            }
        }

        // Check grant permission
        return userResourceGrantRepositoryDsl.getEffectivePermission(userId, resourceType, resourceId)
    }

    // ==================== Helper Methods ====================

    private fun isTeamMember(
        teamId: Long,
        userId: Long,
    ): Boolean {
        val teamIds = teamMemberRepositoryDsl.findTeamIdsByUserIdAndRoles(userId, null)
        return teamId in teamIds
    }

    private fun isTeamEditor(
        teamId: Long,
        userId: Long,
    ): Boolean = teamMemberRepositoryDsl.hasAnyRoleInTeam(teamId, userId, listOf(TeamRole.MANAGER, TeamRole.EDITOR))

    private fun isTeamManager(
        teamId: Long,
        userId: Long,
    ): Boolean = teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, TeamRole.MANAGER)

    private fun getTeamRole(
        teamId: Long,
        userId: Long,
    ): TeamRole? {
        // Use hasRoleInTeam to check each role
        return when {
            teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, TeamRole.MANAGER) -> TeamRole.MANAGER
            teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, TeamRole.EDITOR) -> TeamRole.EDITOR
            teamMemberRepositoryDsl.hasRoleInTeam(teamId, userId, TeamRole.VIEWER) -> TeamRole.VIEWER
            else -> null
        }
    }

    /**
     * Gets the owner team ID for a resource.
     * NOTE: This is a placeholder implementation. In a real implementation,
     * this would query the actual resource table to get its teamId.
     *
     * For Phase 1, we don't have the actual resource tables integrated.
     * This should be updated when resources (Worksheet, Dataset, etc.) are implemented.
     */
    private fun getResourceOwnerTeamId(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Long? {
        // TODO: Implement actual resource lookup when resource tables are ready
        // For now, we can only check shares - if a share exists, we can get the owner team
        val shares =
            teamResourceShareRepositoryDsl.findByResourceType(
                resourceType,
                page = 0,
                size = 1,
            )

        return shares.content.firstOrNull { it.resourceId == resourceId }?.ownerTeamId
    }
}
