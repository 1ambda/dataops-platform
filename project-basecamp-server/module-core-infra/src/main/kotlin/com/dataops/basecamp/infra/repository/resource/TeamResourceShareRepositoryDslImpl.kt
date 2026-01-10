package com.dataops.basecamp.infra.repository.resource

import com.dataops.basecamp.common.enums.ShareableResourceType
import com.dataops.basecamp.domain.entity.resource.QTeamResourceShareEntity
import com.dataops.basecamp.domain.entity.resource.TeamResourceShareEntity
import com.dataops.basecamp.domain.entity.team.QTeamMemberEntity
import com.dataops.basecamp.domain.repository.resource.TeamResourceShareRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

/**
 * Team Resource Share Repository DSL Implementation
 *
 * Implements complex queries using QueryDSL.
 */
@Repository("teamResourceShareRepositoryDsl")
class TeamResourceShareRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : TeamResourceShareRepositoryDsl {
    private val share = QTeamResourceShareEntity.teamResourceShareEntity
    private val teamMember = QTeamMemberEntity.teamMemberEntity

    override fun findByResourceType(
        resourceType: ShareableResourceType,
        page: Int,
        size: Int,
    ): Page<TeamResourceShareEntity> {
        val content =
            queryFactory
                .selectFrom(share)
                .where(
                    share.resourceType.eq(resourceType),
                    share.deletedAt.isNull,
                ).offset((page * size).toLong())
                .limit(size.toLong())
                .orderBy(share.grantedAt.desc())
                .fetch()

        val total =
            queryFactory
                .select(share.count())
                .from(share)
                .where(
                    share.resourceType.eq(resourceType),
                    share.deletedAt.isNull,
                ).fetchOne() ?: 0L

        return PageImpl(content, PageRequest.of(page, size), total)
    }

    override fun findByOwnerTeamIdAndResourceType(
        ownerTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity> =
        queryFactory
            .selectFrom(share)
            .where(
                share.ownerTeamId.eq(ownerTeamId),
                share.resourceType.eq(resourceType),
                share.deletedAt.isNull,
            ).orderBy(share.grantedAt.desc())
            .fetch()

    override fun findBySharedWithTeamIdAndResourceType(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType,
    ): List<TeamResourceShareEntity> =
        queryFactory
            .selectFrom(share)
            .where(
                share.sharedWithTeamId.eq(sharedWithTeamId),
                share.resourceType.eq(resourceType),
                share.deletedAt.isNull,
            ).orderBy(share.grantedAt.desc())
            .fetch()

    override fun findVisibleSharesForTeam(
        sharedWithTeamId: Long,
        resourceType: ShareableResourceType?,
    ): List<TeamResourceShareEntity> {
        val query =
            queryFactory
                .selectFrom(share)
                .where(
                    share.sharedWithTeamId.eq(sharedWithTeamId),
                    share.visibleToTeam.isTrue,
                    share.deletedAt.isNull,
                )

        if (resourceType != null) {
            query.where(share.resourceType.eq(resourceType))
        }

        return query.orderBy(share.grantedAt.desc()).fetch()
    }

    override fun hasShareAccess(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): Boolean {
        // Check if user is member of any team that has a share for this resource
        return queryFactory
            .selectOne()
            .from(share)
            .join(teamMember)
            .on(teamMember.teamId.eq(share.sharedWithTeamId))
            .where(
                teamMember.userId.eq(userId),
                teamMember.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst() != null
    }

    override fun findShareForUserTeam(
        userId: Long,
        resourceType: ShareableResourceType,
        resourceId: Long,
    ): TeamResourceShareEntity? =
        queryFactory
            .selectFrom(share)
            .join(teamMember)
            .on(teamMember.teamId.eq(share.sharedWithTeamId))
            .where(
                teamMember.userId.eq(userId),
                teamMember.deletedAt.isNull,
                share.resourceType.eq(resourceType),
                share.resourceId.eq(resourceId),
                share.deletedAt.isNull,
            ).fetchFirst()
}
