package com.dataops.basecamp.domain.entity.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.LocalDateTime

/**
 * Team Resource Share Entity
 *
 * Represents a share of a resource from one team (owner) to another team (consumer).
 * The owning team can share resources with other teams with specific permissions.
 *
 * Uses FK ID references instead of JPA relationships.
 */
@Entity
@Table(
    name = "team_resource_share",
    indexes = [
        Index(name = "idx_resource_share_owner_team", columnList = "owner_team_id"),
        Index(name = "idx_resource_share_shared_team", columnList = "shared_with_team_id"),
        Index(name = "idx_resource_share_resource", columnList = "resource_type, resource_id"),
        Index(name = "idx_resource_share_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_resource_share",
            columnNames = ["owner_team_id", "shared_with_team_id", "resource_type", "resource_id"],
        ),
    ],
)
class TeamResourceShareEntity(
    /**
     * Team that owns the resource (Producer Team).
     * Note: No MySQL FK - use ID-based join via QueryDSL.
     */
    @Column(name = "owner_team_id", nullable = false)
    val ownerTeamId: Long,
    /**
     * Team receiving access to the resource (Consumer Team).
     * Note: No MySQL FK - use ID-based join via QueryDSL.
     */
    @Column(name = "shared_with_team_id", nullable = false)
    val sharedWithTeamId: Long,
    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    val resourceType: ShareableResourceType,
    /**
     * ID of the shared resource.
     * Note: No MySQL FK - validated at API level, cascade delete via API.
     */
    @Column(name = "resource_id", nullable = false)
    val resourceId: Long,
    /**
     * Default permission level for the share.
     * Individual user grants can have same or lower permission.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    var permission: ResourcePermission = ResourcePermission.VIEWER,
    /**
     * Whether Consumer Team members can see this resource in their SHARED list
     * even without a UserResourceGrant. true = visible, false = hidden until granted.
     */
    @Column(name = "visible_to_team", nullable = false)
    var visibleToTeam: Boolean = true,
    /**
     * User ID of who created this share.
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "granted_by", nullable = false)
    val grantedBy: Long,
    @Column(name = "granted_at", nullable = false)
    var grantedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity() {
    /**
     * Updates share settings.
     */
    fun update(
        permission: ResourcePermission? = null,
        visibleToTeam: Boolean? = null,
    ) {
        permission?.let { this.permission = it }
        visibleToTeam?.let { this.visibleToTeam = it }
    }

    /**
     * Soft deletes the share.
     */
    fun delete(deletedBy: Long) {
        this.deletedAt = LocalDateTime.now()
        this.deletedBy = deletedBy
    }
}
