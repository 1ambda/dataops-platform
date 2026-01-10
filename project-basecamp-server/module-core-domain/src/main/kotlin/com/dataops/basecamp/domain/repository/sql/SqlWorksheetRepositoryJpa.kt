package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity

/**
 * SQL Worksheet Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * SQL Worksheet에 대한 기본 CRUD 작업을 정의합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
interface SqlWorksheetRepositoryJpa {
    // 기본 CRUD 작업
    fun save(worksheet: SqlWorksheetEntity): SqlWorksheetEntity

    fun findById(id: Long): SqlWorksheetEntity?

    fun findByIdAndDeletedAtIsNull(id: Long): SqlWorksheetEntity?

    // Folder 기반 조회
    fun findByFolderIdAndDeletedAtIsNull(folderId: Long): List<SqlWorksheetEntity>

    // Worksheet ID + Folder ID 기반 조회
    fun findByIdAndFolderIdAndDeletedAtIsNull(
        id: Long,
        folderId: Long,
    ): SqlWorksheetEntity?

    // Folder 내 Worksheet 개수
    fun countByFolderIdAndDeletedAtIsNull(folderId: Long): Long

    // 이름 기반 조회 (Folder 내에서)
    fun findByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): SqlWorksheetEntity?

    fun existsByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): Boolean
}
