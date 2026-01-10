package com.dataops.basecamp.domain.repository.resource

import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity

/**
 * User Resource Grant Repository JPA Interface
 *
 * Defines basic CRUD operations for UserResourceGrantEntity.
 */
interface UserResourceGrantRepositoryJpa {
    fun save(grant: UserResourceGrantEntity): UserResourceGrantEntity

    fun findByIdAndDeletedAtIsNull(id: Long): UserResourceGrantEntity?

    fun findByShareIdAndDeletedAtIsNull(shareId: Long): List<UserResourceGrantEntity>

    fun findByUserIdAndDeletedAtIsNull(userId: Long): List<UserResourceGrantEntity>

    fun findByShareIdAndUserIdAndDeletedAtIsNull(
        shareId: Long,
        userId: Long,
    ): UserResourceGrantEntity?

    fun existsByShareIdAndUserIdAndDeletedAtIsNull(
        shareId: Long,
        userId: Long,
    ): Boolean

    fun countByShareIdAndDeletedAtIsNull(shareId: Long): Long

    /**
     * Deletes all grants for a share (used for cascade delete).
     * This performs a hard delete since these are child records.
     */
    fun deleteByShareId(shareId: Long)
}
