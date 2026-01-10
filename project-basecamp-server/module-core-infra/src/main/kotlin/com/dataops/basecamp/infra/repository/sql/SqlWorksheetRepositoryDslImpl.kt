package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.sql.QSqlWorksheetEntity
import com.dataops.basecamp.domain.entity.sql.QWorksheetFolderEntity
import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryDsl
import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.dsl.Expressions
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

/**
 * SQL Worksheet DSL 리포지토리 구현체
 *
 * 복잡한 쿼리를 처리하는 QueryDSL 구현체입니다.
 */
@Repository
class SqlWorksheetRepositoryDslImpl(
    private val queryFactory: JPAQueryFactory,
) : SqlWorksheetRepositoryDsl {
    private val worksheet = QSqlWorksheetEntity.sqlWorksheetEntity
    private val folder = QWorksheetFolderEntity.worksheetFolderEntity

    override fun findByConditions(
        projectId: Long, // TODO: v3.0.0 - Rename to teamId after full migration
        folderId: Long?,
        searchText: String?,
        starred: Boolean?,
        dialect: SqlDialect?,
        pageable: Pageable,
    ): Page<SqlWorksheetEntity> {
        val builder = BooleanBuilder()

        // Team에 속한 폴더들의 worksheet만 조회
        // v3.0.0: projectId 파라미터는 임시로 teamId로 사용됨 (Worksheet 마이그레이션 후 수정 예정)
        builder.and(
            worksheet.folderId.`in`(
                queryFactory
                    .select(folder.id)
                    .from(folder)
                    .where(
                        folder.teamId.eq(projectId), // TODO: Rename parameter to teamId
                        folder.deletedAt.isNull,
                    ),
            ),
        )

        // Soft delete 제외
        builder.and(worksheet.deletedAt.isNull)

        // 특정 폴더 필터링
        folderId?.let {
            builder.and(worksheet.folderId.eq(it))
        }

        // 검색어 필터링 (name, description, sqlText)
        searchText?.takeIf { it.isNotBlank() }?.let { search ->
            builder.and(
                worksheet.name
                    .containsIgnoreCase(search)
                    .or(worksheet.description.containsIgnoreCase(search))
                    .or(worksheet.sqlText.containsIgnoreCase(search)),
            )
        }

        // starred 필터링
        starred?.let {
            builder.and(worksheet.isStarred.eq(it))
        }

        // dialect 필터링
        dialect?.let {
            builder.and(worksheet.dialect.eq(it))
        }

        // 전체 개수 조회
        val totalCount =
            queryFactory
                .select(worksheet.count())
                .from(worksheet)
                .where(builder)
                .fetchOne() ?: 0L

        // 페이징된 결과 조회
        val content =
            queryFactory
                .selectFrom(worksheet)
                .where(builder)
                .orderBy(worksheet.updatedAt.desc())
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
                .select(worksheet.folderId, worksheet.count())
                .from(worksheet)
                .where(
                    worksheet.folderId.`in`(folderIds),
                    worksheet.deletedAt.isNull,
                ).groupBy(worksheet.folderId)
                .fetch()

        return results.associate { tuple ->
            val fId = tuple.get(worksheet.folderId)!!
            val count = tuple.get(Expressions.numberPath(Long::class.java, "count")) ?: 0L
            fId to count
        }
    }
}
