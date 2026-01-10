package com.dataops.basecamp.infra.repository.resource

import com.dataops.basecamp.common.enums.ResourcePermission
import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.QTeamResourceShareEntity
import com.dataops.basecamp.domain.entity.resource.QUserResourceGrantEntity
import com.dataops.basecamp.domain.entity.resource.UserResourceGrantEntity
import com.dataops.basecamp.domain.repository.resource.UserResourceGrantRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * User Resource Grant Repository DSL Implementation
 *
 * Implements complex queries using QueryDSL.
 */
@Repository("userResourceGrantRepositoryDsl")
class UserResourceGrantRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : UserResourceGrantRepositoryDsl {
    private val grant = QUserResourceGrantEntity.userResourceGrantEntity
    private val share = QTeamResourceShareEntity.teamResourceShareEntity

    override fun findGrantsByUserId(userId: Long): List<UserResourceGrantEntity> =
        queryFactory
            .selectFrom(grant)
            .where(
                grant.userId.eq(userId),
                grant.deletedAt.isNull,
            ).orderBy(grant.grantedAt.desc())
            .fetch()

    override fun findGrantForUserAndResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): UserResourceGrantEntity? =
        queryFactory
            .selectFrom(grant)
            .join(share)
            .on(share.id.eq(grant.shareId))
            .where(
                grant.userId.eq(userId),
                grant.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst()

    override fun hasGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean =
        queryFactory
            .selectOne()
            .from(grant)
            .join(share)
            .on(share.id.eq(grant.shareId))
            .where(
                grant.userId.eq(userId),
                grant.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst() != null

    override fun hasEditorGrantForResource(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean =
        queryFactory
            .selectOne()
            .from(grant)
            .join(share)
            .on(share.id.eq(grant.shareId))
            .where(
                grant.userId.eq(userId),
                grant.permission.eq(ResourcePermission.EDITOR),
                grant.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst() != null

    override fun getEffectivePermission(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): ResourcePermission? =
        queryFactory
            .select(grant.permission)
            .from(grant)
            .join(share)
            .on(share.id.eq(grant.shareId))
            .where(
                grant.userId.eq(userId),
                grant.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst()
}
