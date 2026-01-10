package com.dataops.basecamp.infra.repository.resource

import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

/**
 * User Resource Grant Repository JPA Implementation
 *
 * Implements UserResourceGrantRepositoryJpa interface using Spring Data JPA.
 */
@Repository("userResourceGrantRepositoryJpa")
interface UserResourceGrantRepositoryJpaImpl :
    UserResourceGrantRepositoryJpa,
    JpaRepository<UserResourceGrantEntity, Long> {
    // Spring Data JPA auto-implements methods with naming convention

    override fun findByIdAndDeletedAtIsNull(id: Long): UserResourceGrantEntity?

    override fun findByShareIdAndDeletedAtIsNull(shareId: Long): List<UserResourceGrantEntity>

    override fun findByUserIdAndDeletedAtIsNull(userId: Long): List<UserResourceGrantEntity>

    override fun findByShareIdAndUserIdAndDeletedAtIsNull(
        shareId: Long,
        userId: Long,
    ): UserResourceGrantEntity?

    override fun existsByShareIdAndUserIdAndDeletedAtIsNull(
        shareId: Long,
        userId: Long,
    ): Boolean

    override fun countByShareIdAndDeletedAtIsNull(shareId: Long): Long

    @Modifying
    @Query("DELETE FROM UserResourceGrantEntity g WHERE g.shareId = :shareId")
    override fun deleteByShareId(
        @Param("shareId") shareId: Long,
    )
}
