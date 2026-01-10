package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.common.exception.ResourceShareAlreadyExistsException
import com.dataops.basecamp.common.exception.ResourceShareNotFoundException
import com.dataops.basecamp.common.exception.TeamNotFoundException
import com.dataops.basecamp.domain.command.resource.CreateResourceShareCommand
import com.dataops.basecamp.domain.command.resource.RevokeResourceShareCommand
import com.dataops.basecamp.domain.command.resource.UpdateResourceShareCommand
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryDsl
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryJpa
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryJpa
import com.dataops.basecamp.domain.repository.team.TeamRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Resource Share Service
 *
 * Handles resource sharing operations between teams.
 * Services are concrete classes (no interfaces) following Pure Hexagonal Architecture.
 */
@Service
@Transactional(readOnly = true)
class ResourceShareService(
    private val teamResourceShareRepositoryJpa: TeamResourceShareRepositoryJpa,
    private val teamResourceShareRepositoryDsl: TeamResourceShareRepositoryDsl,
    private val userResourceGrantRepositoryJpa: UserResourceGrantRepositoryJpa,
    private val teamRepositoryJpa: TeamRepositoryJpa,
) {
    private val log = LoggerFactory.getLogger(ResourceShareService::class.java)

    // ==================== Share CRUD Operations ====================

    /**
     * Creates a new resource share.
     *
     * @param command The create resource share command
     * @return The created share entity
     * @throws TeamNotFoundException if owner or shared-with team is not found
     * @throws ResourceShareAlreadyExistsException if a share already exists
     */
    @Transactional
    fun createShare(command: CreateResourceShareCommand): TeamResourceShareEntity {
        log.info(
            "Creating share: ${command.resourceType}:${command.resourceId} " +
                "from team ${command.ownerTeamId} to team ${command.sharedWithTeamId}",
        )

        // Verify teams exist
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.ownerTeamId) == null) {
            throw TeamNotFoundException(command.ownerTeamId)
        }
        if (teamRepositoryJpa.findByIdAndDeletedAtIsNull(command.sharedWithTeamId) == null) {
            throw TeamNotFoundException(command.sharedWithTeamId)
        }

        // Check if share already exists
        if (teamResourceShareRepositoryJpa
                .existsByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
                    command.ownerTeamId,
                    command.sharedWithTeamId,
                    command.resourceType,
                    command.resourceId,
                )
        ) {
            throw ResourceShareAlreadyExistsException(
                command.resourceType.name,
                command.resourceId,
                command.ownerTeamId,
                command.sharedWithTeamId,
            )
        }

        val share =
            TeamResourceShareEntity(
                ownerTeamId = command.ownerTeamId,
                sharedWithTeamId = command.sharedWithTeamId,
                resourceType = command.resourceType,
                resourceId = command.resourceId,
                permission = command.permission,
                visibleToTeam = command.visibleToTeam,
                grantedBy = command.grantedBy,
                grantedAt = LocalDateTime.now(),
            )

        return teamResourceShareRepositoryJpa.save(share)
    }

    /**
     * Updates an existing resource share.
     *
     * @param command The update resource share command
     * @return The updated share entity
     * @throws ResourceShareNotFoundException if the share is not found
     */
    @Transactional
    fun updateShare(command: UpdateResourceShareCommand): TeamResourceShareEntity {
        log.info("Updating share: ${command.shareId}")

        val share =
            teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(command.shareId)
                ?: throw ResourceShareNotFoundException(command.shareId)

        share.update(
            permission = command.permission,
            visibleToTeam = command.visibleToTeam,
        )

        return teamResourceShareRepositoryJpa.save(share)
    }

    /**
     * Revokes a resource share and cascade deletes all associated user grants.
     *
     * @param command The revoke resource share command
     * @throws ResourceShareNotFoundException if the share is not found
     */
    @Transactional
    fun revokeShare(command: RevokeResourceShareCommand) {
        log.info("Revoking share: ${command.shareId}")

        val share =
            teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(command.shareId)
                ?: throw ResourceShareNotFoundException(command.shareId)

        // Cascade delete all user grants for this share
        userResourceGrantRepositoryJpa.deleteByShareId(command.shareId)
        log.info("Deleted all user grants for share: ${command.shareId}")

        // Soft delete the share
        share.delete(command.revokedBy)
        teamResourceShareRepositoryJpa.save(share)
    }

    // ==================== Share Query Operations ====================

    /**
     * Gets a share by ID.
     *
     * @param shareId The share ID
     * @return The share entity or null if not found
     */
    fun getShare(shareId: Long): TeamResourceShareEntity? =
        teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(shareId)

    /**
     * Gets a share by ID, throwing an exception if not found.
     *
     * @param shareId The share ID
     * @return The share entity
     * @throws ResourceShareNotFoundException if the share is not found
     */
    fun getShareOrThrow(shareId: Long): TeamResourceShareEntity =
        teamResourceShareRepositoryJpa.findByIdAndDeletedAtIsNull(shareId)
            ?: throw ResourceShareNotFoundException(shareId)

    /**
     * Lists all shares owned by a team (outgoing shares).
     *
     * @param ownerTeamId The owner team ID
     * @return List of share entities
     */
    fun listSharesByOwnerTeam(ownerTeamId: Long): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryJpa.findByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId)

    /**
     * Lists all shares received by a team (incoming shares).
     *
     * @param sharedWithTeamId The shared-with team ID
     * @return List of share entities
     */
    fun listSharesBySharedWithTeam(sharedWithTeamId: Long): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryJpa.findBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId)

    /**
     * Lists shares for a specific resource type owned by a team.
     *
     * @param ownerTeamId The owner team ID
     * @param resourceType The resource type
     * @return List of share entities
     */
    fun listSharesByOwnerTeamAndResourceType(
        ownerTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryDsl.findByOwnerTeamIdAndResourceType(ownerTeamId, resourceType)

    /**
     * Lists shares for a specific resource type received by a team.
     *
     * @param sharedWithTeamId The shared-with team ID
     * @param resourceType The resource type
     * @return List of share entities
     */
    fun listSharesBySharedWithTeamAndResourceType(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryDsl.findBySharedWithTeamIdAndResourceType(sharedWithTeamId, resourceType)

    /**
     * Lists visible shares for a consumer team.
     *
     * @param sharedWithTeamId The consumer team ID
     * @param resourceType Optional resource type filter
     * @return List of visible share entities
     */
    fun listVisibleSharesForTeam(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType? = null,
    ): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryDsl.findVisibleSharesForTeam(sharedWithTeamId, resourceType)

    /**
     * Lists all shares for a specific resource.
     *
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return List of share entities
     */
    fun listSharesForResource(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): List<TeamResourceShareEntity> =
        teamResourceShareRepositoryJpa.findByResourceTypeAndResourceIdAndDeletedAtIsNull(resourceType, resourceId)

    /**
     * Checks if a user has access to a resource through any share.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return true if the user has share access
     */
    fun hasShareAccess(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean = teamResourceShareRepositoryDsl.hasShareAccess(userId, resourceType, resourceId)

    /**
     * Gets the share for a specific resource if the user's team has access.
     *
     * @param userId The user ID
     * @param resourceType The resource type
     * @param resourceId The resource ID
     * @return The share entity or null if not found
     */
    fun findShareForUserTeam(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): TeamResourceShareEntity? = teamResourceShareRepositoryDsl.findShareForUserTeam(userId, resourceType, resourceId)

    /**
     * Counts outgoing shares for a team.
     *
     * @param ownerTeamId The owner team ID
     * @return Count of outgoing shares
     */
    fun countOutgoingShares(ownerTeamId: Long): Long =
        teamResourceShareRepositoryJpa.countByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId)

    /**
     * Counts incoming shares for a team.
     *
     * @param sharedWithTeamId The shared-with team ID
     * @return Count of incoming shares
     */
    fun countIncomingShares(sharedWithTeamId: Long): Long =
        teamResourceShareRepositoryJpa.countBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId)
}
