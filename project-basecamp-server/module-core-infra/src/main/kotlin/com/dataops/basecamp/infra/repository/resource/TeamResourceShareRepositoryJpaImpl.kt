package com.dataops.basecamp.infra.repository.resource

import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Team Resource Share Repository JPA Implementation
 *
 * Implements TeamResourceShareRepositoryJpa interface using Spring Data JPA.
 */
@Repository("teamResourceShareRepositoryJpa")
interface TeamResourceShareRepositoryJpaImpl :
    TeamResourceShareRepositoryJpa,
    JpaRepository<TeamResourceShareEntity, Long> {
    // Spring Data JPA auto-implements methods with naming convention

    override fun findByIdAndDeletedAtIsNull(id: Long): TeamResourceShareEntity?

    override fun findByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId: Long): List<TeamResourceShareEntity>

    override fun findBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId: Long): List<TeamResourceShareEntity>

    override fun findByResourceTypeAndResourceIdAndDeletedAtIsNull(
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): List<TeamResourceShareEntity>

    override fun findByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
        ownerTeamId: Long,
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): TeamResourceShareEntity?

    override fun existsByOwnerTeamIdAndSharedWithTeamIdAndResourceTypeAndResourceIdAndDeletedAtIsNull(
        ownerTeamId: Long,
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean

    override fun countByOwnerTeamIdAndDeletedAtIsNull(ownerTeamId: Long): Long

    override fun countBySharedWithTeamIdAndDeletedAtIsNull(sharedWithTeamId: Long): Long
}
