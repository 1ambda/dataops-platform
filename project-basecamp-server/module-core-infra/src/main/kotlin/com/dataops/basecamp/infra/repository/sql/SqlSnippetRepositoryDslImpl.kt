package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.sql.QSqlFolderEntity
import com.dataops.basecamp.domain.entity.sql.QSqlSnippetEntity
import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * SQL Snippet DSL 리포지토리 구현체
 *
 * 복잡한 쿼리를 처리하는 QueryDSL 구현체입니다.
 */
@Repository
class SqlSnippetRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlSnippetRepositoryDsl {
    private val snippet = QSqlSnippetEntity.sqlSnippetEntity
    private val folder = QSqlFolderEntity.sqlFolderEntity

    override fun findByConditions(
        projectId: Long,
        folderId: Long?,
        searchText: String?,
        starred: Boolean?,
        dialect: SqlDialect?,
        pageable: Pageable,
    ): Page<SqlSnippetEntity> {
        val builder = BooleanBuilder()

        // Project에 속한 폴더들의 snippet만 조회
        builder.and(
            snippet.folderId.`in`(
                queryFactory
                    .select(folder.id)
                    .from(folder)
                    .where(
                        folder.projectId.eq(projectId),
                        folder.deletedAt.isNull,
                    ),
            ),
        )

        // Soft delete 제외
        builder.and(snippet.deletedAt.isNull)

        // 특정 폴더 필터링
        folderId?.let {
            builder.and(snippet.folderId.eq(it))
        }

        // 검색어 필터링 (name, description, sqlText)
        searchText?.takeIf { it.isNotBlank() }?.let { search ->
            builder.and(
                snippet.name
                    .containsIgnoreCase(search)
                    .or(snippet.description.containsIgnoreCase(search))
                    .or(snippet.sqlText.containsIgnoreCase(search)),
            )
        }

        // starred 필터링
        starred?.let {
            builder.and(snippet.isStarred.eq(it))
        }

        // dialect 필터링
        dialect?.let {
            builder.and(snippet.dialect.eq(it))
        }

        // 전체 개수 조회
        val totalCount =
            queryFactory
                .select(snippet.count())
                .from(snippet)
                .where(builder)
                .fetchOne() ?: 0L

        // 페이징된 결과 조회
        val content =
            queryFactory
                .selectFrom(snippet)
                .where(builder)
                .orderBy(snippet.updatedAt.desc())
                .offset(pageable.offset)
                .limit(pageable.pageSize.toLong())
                .fetch()

        return PageImpl(content, pageable, totalCount)
    }

    override fun countByFolderIds(folderIds: List<Long>): Map<Long, Long> {
        if (folderIds.isEmpty()) {
            return emptyMap()
        }

        val results =
            queryFactory
                .select(snippet.folderId, snippet.count())
                .from(snippet)
                .where(
                    snippet.folderId.`in`(folderIds),
                    snippet.deletedAt.isNull,
                ).groupBy(snippet.folderId)
                .fetch()

        return results.associate { tuple ->
            val folderId = tuple.get(snippet.folderId)!!
            val count = tuple.get(Expressions.numberPath(Long::class.java, "count")) ?: 0L
            folderId to count
        }
    }
}
