package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

/**
 * SQL Snippet Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * SQL Snippet에 대한 복잡한 쿼리 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface SqlSnippetRepositoryDsl {
    /**
     * Project 내의 Snippet 목록을 조건에 따라 페이징 조회
     *
     * @param projectId Project ID
     * @param folderId 특정 폴더로 필터링 (null인 경우 전체)
     * @param searchText 이름/설명/SQL 검색 (null인 경우 전체)
     * @param starred starred 필터 (null인 경우 전체)
     * @param dialect SQL dialect 필터 (null인 경우 전체)
     * @param pageable 페이징 정보
     * @return 페이징된 Snippet 목록 (soft delete 제외)
     */
    fun findByConditions(
        projectId: Long,
        folderId: Long?,
        searchText: String?,
        starred: Boolean?,
        dialect: SqlDialect?,
        pageable: Pageable,
    ): Page<SqlSnippetEntity>

    /**
     * Folder ID 목록에 해당하는 Snippet 개수를 Folder별로 조회
     *
     * @param folderIds Folder ID 목록
     * @return Map<FolderId, Count>
     */
    fun countByFolderIds(folderIds: List<Long>): Map<Long, Long>
}
