package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity

/**
 * SQL Folder Repository DSL 인터페이스 (순수 도메인 추상화)
 *
 * SQL Folder에 대한 복잡한 쿼리 작업을 정의합니다.
 * QueryDSL이나 기타 복잡한 쿼리 기술에 대한 추상화를 제공합니다.
 */
interface SqlFolderRepositoryDsl {
    /**
     * Project 내의 모든 폴더를 displayOrder 순으로 조회
     *
     * @param projectId Project ID
     * @return 정렬된 폴더 목록 (soft delete 제외)
     */
    fun findAllByProjectIdOrderByDisplayOrder(projectId: Long): List<SqlFolderEntity>
}
