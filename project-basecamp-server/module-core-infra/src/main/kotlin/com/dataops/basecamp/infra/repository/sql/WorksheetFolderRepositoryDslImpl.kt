package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.QWorksheetFolderEntity
import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * Worksheet Folder DSL 리포지토리 구현체
 *
 * 복잡한 쿼리를 처리하는 QueryDSL 구현체입니다.
 */
@Repository
class WorksheetFolderRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : WorksheetFolderRepositoryDsl {
    private val folder = QWorksheetFolderEntity.worksheetFolderEntity

    override fun findAllByTeamIdOrderByDisplayOrder(teamId: Long): List<WorksheetFolderEntity> =
        queryFactory
            .selectFrom(folder)
            .where(
                folder.teamId.eq(teamId),
                folder.deletedAt.isNull,
            ).orderBy(folder.displayOrder.asc(), folder.name.asc())
            .fetch()
}
