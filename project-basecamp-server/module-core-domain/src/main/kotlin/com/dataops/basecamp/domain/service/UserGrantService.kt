package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.common.exception.GrantPermissionExceedsShareException
import com.dataops.basecamp.common.exception.ResourceShareNotFoundException
import com.dataops.basecamp.common.exception.UserGrantAlreadyExistsException
import com.dataops.basecamp.common.exception.UserGrantNotFoundException
import com.dataops.basecamp.domain.command.resource.CreateUserGrantCommand
import com.dataops.basecamp.domain.command.resource.RevokeUserGrantCommand
import com.dataops.basecamp.domain.command.resource.UpdateUserGrantCommand
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryJpa
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * User Grant Service
 *
 * Handles user-level grant operations for shared resources.
 * Enforces the permission hierarchy where grant permission cannot exceed share permission.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class UserGrantService(
    private val userResourceGrantRepositoryJpa: UserResourceGrantRepositoryJpa,
    private val userResourceGrantRepositoryDsl: UserResourceGrantRepositoryDsl,
    private val teamResourceShareRepositoryJpa: TeamResourceShareRepositoryJpa,
) {
    private val log = LoggerFactory.getLogger(UserGrantService::class.java)

    // ==================== Grant CRUD Operations ====================

    /**
     * Creates a new user grant.
     *
     * @param command The create user grant command
     * @return The created grant entity
     * @throws ResourceShareNotFoundException if the share is not found
     * @throws UserGrantAlreadyExistsException if a grant already exists for this user
     * @throws GrantPermissionExceedsShareException if grant permission exceeds share permission
     */
    @Transactional
    fun createGrant(command: CreateUserGrantCommand): UserResourceGrantEntity {
        log.info("Creating grant for user ${command.userId} on share ${command.shareId}")

        // Verify share exists
        val share =
            teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(command.shareId)
                ?: throw ResourceShareNotFoundException(command.shareId)

        // Check if grant already exists
        if (userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(
                command.shareId,
                command.userId,
            )
        ) {
            throw UserGrantAlreadyExistsException(command.shareId, command.userId)
        }

        // Validate permission hierarchy: grant permission cannot exceed share permission
        validatePermissionHierarchy(command.permission, share.permission)

        val grant =
            UserResourceGrantEntity(
                shareId = command.shareId,
                userId = command.userId,
                permission = command.permission,
                grantedBy = command.grantedBy,
                grantedAt = LocalDateTime.now(),
            )

        return userResourceGrantRepositoryJpa.save(grant)
    }

    /**
     * Updates an existing user grant.
     *
     * @param command The update user grant command
     * @return The updated grant entity
     * @throws UserGrantNotFoundException if the grant is not found
     * @throws GrantPermissionExceedsShareException if new permission exceeds share permission
     */
    @Transactional
    fun updateGrant(command: UpdateUserGrantCommand): UserResourceGrantEntity {
        log.info("Updating grant: ${command.grantId} to permission ${command.permission}")

        val grant =
            userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(command.grantId)
                ?: throw UserGrantNotFoundException(command.grantId)

        // Get the share to validate permission hierarchy
        val share =
            teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(grant.shareId)
                ?: throw ResourceShareNotFoundException(grant.shareId)

        // Validate permission hierarchy
        validatePermissionHierarchy(command.permission, share.permission)

        grant.updatePermission(command.permission)

        return userResourceGrantRepositoryJpa.save(grant)
    }

    /**
     * Revokes a user grant.
     *
     * @param command The revoke user grant command
     * @throws UserGrantNotFoundException if the grant is not found
     */
    @Transactional
    fun revokeGrant(command: RevokeUserGrantCommand) {
        log.info("Revoking grant: ${command.grantId}")

        val grant =
            userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(command.grantId)
                ?: throw UserGrantNotFoundException(command.grantId)

        grant.delete(command.revokedBy)
        userResourceGrantRepositoryJpa.save(grant)
    }

    // ==================== Grant Query Operations ====================

    /**
     * Gets a grant by ID.
     *
     * @param grantId The grant ID
     * @return The grant entity or null if not found
     */
    fun getGrant(grantId: Long): UserResourceGrantEntity? =
        userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(grantId)

    /**
     * Gets a grant by ID, throwing an exception if not found.
     *
     * @param grantId The grant ID
     * @return The grant entity
     * @throws UserGrantNotFoundException if the grant is not found
     */
    fun getGrantOrThrow(grantId: Long): UserResourceGrantEntity =
        userResourceGrantRepositoryJpa.findByIdAndDeletedAtIsNull(grantId)
            ?: throw UserGrantNotFoundException(grantId)

    /**
     * Lists all grants for a share.
     *
     * @param shareId The share ID
     * @return List of grant entities
     */
    fun listGrantsByShare(shareId: Long): List<UserResourceGrantEntity> =
        userResourceGrantRepositoryJpa.findByShareIdAndDeletedAtIsNull(shareId)

    /**
     * Lists all grants for a user.
     *
     * @param userId The user ID
     * @return List of grant entities
     */
    fun listGrantsByUser(userId: Long): List<UserResourceGrantEntity> =
        userResourceGrantRepositoryJpa.findByUserIdAndDeletedAtIsNull(userId)

    /**
     * Gets a user's grant for a specific share.
     *
     * @param shareId The share ID
     * @param userId The user ID
     * @return The grant entity or null if not found
     */
    fun getGrantByShareAndUser(
        shareId: Long,
        userId: Long,
    ): UserResourceGrantEntity? =
        userResourceGrantRepositoryJpa.findByShareIdAndUserIdAndDeletedAtIsNull(shareId, userId)

    /**
     * Checks if a user has a grant for a share.
     *
     * @param shareId The share ID
     * @param userId The user ID
     * @return true if the user has a grant
     */
    fun hasGrant(
        shareId: Long,
        userId: Long,
    ): Boolean = userResourceGrantRepositoryJpa.existsByShareIdAndUserIdAndDeletedAtIsNull(shareId, userId)

    /**
     * Counts grants for a share.
     *
     * @param shareId The share ID
     * @return Count of grants
     */
    fun countGrantsByShare(shareId: Long): Long =
        userResourceGrantRepositoryJpa.countByShareIdAndDeletedAtIsNull(shareId)

    /**
     * Finds a user's grant for a specific resource.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return The grant entity or null if not found
     */
    fun findGrantForUserAndResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): UserResourceGrantEntity? =
        userResourceGrantRepositoryDsl.findGrantForUserAndResource(userId, resourceType, resourceId)

    /**
     * Checks if a user has a grant for a specific resource.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user has a grant
     */
    fun hasGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean = userResourceGrantRepositoryDsl.hasGrantForResource(userId, resourceType, resourceId)

    /**
     * Checks if a user has EDITOR grant for a specific resource.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user has EDITOR grant
     */
    fun hasEditorGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean = userResourceGrantRepositoryDsl.hasEditorGrantForResource(userId, resourceType, resourceId)

    /**
     * Gets the effective permission for a user on a resource.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return The effective permission or null if no grant exists
     */
    fun getEffectivePermission(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): ResourcePermission? = userResourceGrantRepositoryDsl.getEffectivePermission(userId, resourceType, resourceId)

    // ==================== Helper Methods ====================

    /**
     * Validates that the grant permission does not exceed the share permission.
     * Permission hierarchy: VIEWER < EDITOR
     *
     * @throws GrantPermissionExceedsShareException if grant permission exceeds share permission
     */
    private fun validatePermissionHierarchy(
        grantPermission: ResourcePermission,
        sharePermission: ResourcePermission,
    ) {
        // EDITOR grant is only valid if share is EDITOR
        // VIEWER grant is valid for both VIEWER and EDITOR shares
        if (grantPermission == ResourcePermission.EDITOR && sharePermission == ResourcePermission.VIEWER) {
            throw GrantPermissionExceedsShareException(
                grantPermission.name,
                sharePermission.name,
            )
        }
    }
}
