package com.dataops.basecamp.domain.repository.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity

/**
 * User Resource Grant Repository DSL Interface
 *
 * Defines complex query operations for UserResourceGrantEntity using QueryDSL.
 */
interface UserResourceGrantRepositoryDsl {
    /**
     * Finds all grants for a user across all shares.
     */
    fun findGrantsByUserId(userId: Long): List<UserResourceGrantEntity>

    /**
     * Finds a user's grant for a specific resource type and ID.
     */
    fun findGrantForUserAndResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): UserResourceGrantEntity?

    /**
     * Checks if a user has a grant for a specific resource.
     */
    fun hasGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean

    /**
     * Checks if a user has EDITOR grant for a specific resource.
     */
    fun hasEditorGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean

    /**
     * Gets the effective permission for a user on a resource.
     * Returns null if no grant exists.
     */
    fun getEffectivePermission(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): ResourcePermission?
}
