package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity

/**
 * SQL Folder Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * SQL Folder에 대한 기본 CRUD 작업을 정의합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
interface SqlFolderRepositoryJpa {
    // 기본 CRUD 작업
    fun save(folder: SqlFolderEntity): SqlFolderEntity

    fun findById(id: Long): SqlFolderEntity?

    fun findByIdAndDeletedAtIsNull(id: Long): SqlFolderEntity?

    // Project 기반 조회
    fun findByProjectIdAndDeletedAtIsNull(projectId: Long): List<SqlFolderEntity>

    fun findByIdAndProjectIdAndDeletedAtIsNull(
        id: Long,
        projectId: Long,
    ): SqlFolderEntity?

    // 이름 기반 조회 (Project 내에서 unique)
    fun findByNameAndProjectIdAndDeletedAtIsNull(
        name: String,
        projectId: Long,
    ): SqlFolderEntity?

    fun existsByNameAndProjectIdAndDeletedAtIsNull(
        name: String,
        projectId: Long,
    ): Boolean

    // 삭제되지 않은 폴더 개수 (Project 내)
    fun countByProjectIdAndDeletedAtIsNull(projectId: Long): Long
}
