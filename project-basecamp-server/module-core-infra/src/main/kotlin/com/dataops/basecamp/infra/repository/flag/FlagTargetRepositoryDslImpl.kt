package com.dataops.basecamp.infra.repository.flag

import com.dataops.basecamp.common.enums.SubjectType
import com.dataops.basecamp.domain.entity.flag.FlagTargetEntity
import com.dataops.basecamp.domain.entity.flag.QFlagEntity
import com.dataops.basecamp.domain.entity.flag.QFlagTargetEntity
import com.dataops.basecamp.domain.projection.flag.FlagTargetWithKeyProjection
import com.dataops.basecamp.domain.repository.flag.FlagTargetRepositoryDsl
import com.querydsl.core.types.Projections
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * FlagTarget DSL Repository 구현체
 *
 * QueryDSL을 사용한 복합 쿼리를 구현합니다.
 */
@Repository("flagTargetRepositoryDsl")
class FlagTargetRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : FlagTargetRepositoryDsl {
    private val flag = QFlagEntity.flagEntity
    private val target = QFlagTargetEntity.flagTargetEntity

    override fun findByFlagIdAndSubject(
        flagId: Long,
        subjectType: SubjectType,
        subjectId: Long,
    ): FlagTargetEntity? =
        queryFactory
            .selectFrom(target)
            .where(
                target.flagId.eq(flagId),
                target.subjectType.eq(subjectType),
                target.subjectId.eq(subjectId),
                target.deletedAt.isNull,
            ).fetchOne()

    override fun findBySubjectWithFlagKey(
        subjectType: SubjectType,
        subjectId: Long,
    ): List<FlagTargetWithKeyProjection> =
        queryFactory
            .select(
                Projections.constructor(
                    FlagTargetWithKeyProjection::class.java,
                    flag.flagKey,
                    target.flagId,
                    target.subjectType,
                    target.subjectId,
                    target.enabled,
                    target.permissions,
                ),
            ).from(target)
            .join(flag)
            .on(target.flagId.eq(flag.id))
            .where(
                target.subjectType.eq(subjectType),
                target.subjectId.eq(subjectId),
                target.deletedAt.isNull,
                flag.deletedAt.isNull,
            ).fetch()
}
