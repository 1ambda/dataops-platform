package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity

/**
 * SQL Snippet Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * SQL Snippet에 대한 기본 CRUD 작업을 정의합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
interface SqlSnippetRepositoryJpa {
    // 기본 CRUD 작업
    fun save(snippet: SqlSnippetEntity): SqlSnippetEntity

    fun findById(id: Long): SqlSnippetEntity?

    fun findByIdAndDeletedAtIsNull(id: Long): SqlSnippetEntity?

    // Folder 기반 조회
    fun findByFolderIdAndDeletedAtIsNull(folderId: Long): List<SqlSnippetEntity>

    // Snippet ID + Folder ID 기반 조회
    fun findByIdAndFolderIdAndDeletedAtIsNull(
        id: Long,
        folderId: Long,
    ): SqlSnippetEntity?

    // Folder 내 Snippet 개수
    fun countByFolderIdAndDeletedAtIsNull(folderId: Long): Long

    // 이름 기반 조회 (Folder 내에서)
    fun findByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): SqlSnippetEntity?

    fun existsByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): Boolean
}
