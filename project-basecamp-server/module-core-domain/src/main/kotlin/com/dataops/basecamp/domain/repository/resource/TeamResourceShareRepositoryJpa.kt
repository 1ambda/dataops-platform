package com.dataops.basecamp.domain.repository.resource

import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity

/**
 * Team Resource Share Repository JPA Interface
 *
 * Defines basic CRUD operations for TeamResourceShareEntity.
 */
interface TeamResourceShareRepositoryJpa {
    fun save(share: TeamResourceShareEntity): TeamResourceShareEntity

    fun findByIdAndDeletedAtIsNull(id: Long): TeamResourceShareEntity?

    fun findByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId: Long): List<TeamResourceShareEntity>

    fun findBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId: Long): List<TeamResourceShareEntity>

    fun findByResourceTypeAndResourceIdAndDeletedAtIsNull(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): List<TeamResourceShareEntity>

    fun findByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
        ownerTeamId: Long,
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): TeamResourceShareEntity?

    fun existsByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
        ownerTeamId: Long,
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean

    fun countByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId: Long): Long

    fun countBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId: Long): Long
}
