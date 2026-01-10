package com.dataops.basecamp.domain.entity.resource

import com.dataops.basecamp.common.enums.ResourcePermission
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
 * User Resource Grant Entity
 *
 * Represents an individual user's permission to access a shared resource.
 * The grant permission cannot exceed the share-level permission.
 *
 * Uses FK ID references instead of JPA relationships.
 */
@Entity
@Table(
    name = "user_resource_grant",
    indexes = [
        Index(name = "idx_user_grant_share_id", columnList = "share_id"),
        Index(name = "idx_user_grant_user_id", columnList = "user_id"),
        Index(name = "idx_user_grant_deleted_at", columnList = "deleted_at"),
    ],
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_user_grant",
            columnNames = ["share_id", "user_id"],
        ),
    ],
)
class UserResourceGrantEntity(
    /**
     * Reference to the TeamResourceShareEntity.
     * Note: No MySQL FK - cascade delete handled at API level.
     * When TeamResourceShareEntity is deleted, all UserResourceGrantEntity with this shareId
     * must be deleted by the service layer.
     */
    @Column(name = "share_id", nullable = false)
    val shareId: Long,
    /**
     * User ID receiving the grant within Consumer Team.
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    /**
     * Permission for this specific user.
     * Cannot exceed the share-level permission.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 20)
    var permission: ResourcePermission = ResourcePermission.VIEWER,
    /**
     * User ID of who created this grant (typically Team Manager).
     * Note: No MySQL FK - use ID-based join.
     */
    @Column(name = "granted_by", nullable = false)
    val grantedBy: Long,
    @Column(name = "granted_at", nullable = false)
    var grantedAt: LocalDateTime = LocalDateTime.now(),
) : BaseEntity() {
    /**
     * Updates the grant permission.
     */
    fun updatePermission(newPermission: ResourcePermission) {
        this.permission = newPermission
    }

    /**
     * Soft deletes the grant.
     */
    fun delete(deletedBy: Long) {
        this.deletedAt = LocalDateTime.now()
        this.deletedBy = deletedBy
    }
}
