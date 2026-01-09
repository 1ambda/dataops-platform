package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.QSqlFolderEntity
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryDsl
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository

/**
 * SQL Folder DSL 리포지토리 구현체
 *
 * 복잡한 쿼리를 처리하는 QueryDSL 구현체입니다.
 */
@Repository
class SqlFolderRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlFolderRepositoryDsl {
    private val folder = QSqlFolderEntity.sqlFolderEntity

    override fun findAllByProjectIdOrderByDisplayOrder(projectId: Long): List<SqlFolderEntity> =
        queryFactory
            .selectFrom(folder)
            .where(
                folder.projectId.eq(projectId),
                folder.deletedAt.isNull,
            ).orderBy(folder.displayOrder.asc(), folder.name.asc())
            .fetch()
}
