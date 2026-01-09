package com.dataops.basecamp.infra.repository.flag

import com.dataops.basecamp.common.enums.FlagStatus
import com.dataops.basecamp.domain.entity.flag.FlagEntity
import com.dataops.basecamp.domain.entity.flag.QFlagEntity
import com.dataops.basecamp.domain.repository.flag.FlagRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * Flag DSL Repository 구현체
 *
 * QueryDSL을 사용한 복합 쿼리를 구현합니다.
 */
@Repository("flagRepositoryDsl")
class FlagRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : FlagRepositoryDsl {
    private val flag = QFlagEntity.flagEntity

    override fun findByStatus(status: FlagStatus): List<FlagEntity> =
        queryFactory
            .selectFrom(flag)
            .where(
                flag.status.eq(status),
                flag.deletedAt.isNull,
            ).orderBy(flag.flagKey.asc())
            .fetch()

    override fun findBySearch(search: String): List<FlagEntity> =
        queryFactory
            .selectFrom(flag)
            .where(
                flag.flagKey
                    .containsIgnoreCase(search)
                    .or(flag.name.containsIgnoreCase(search))
                    .or(flag.description.containsIgnoreCase(search)),
                flag.deletedAt.isNull,
            ).orderBy(flag.flagKey.asc())
            .fetch()
}
