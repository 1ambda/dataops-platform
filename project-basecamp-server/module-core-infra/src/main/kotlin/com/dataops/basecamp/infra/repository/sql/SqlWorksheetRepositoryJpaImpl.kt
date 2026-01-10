package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * SQL Worksheet JPA Repository 구현 인터페이스
 *
 * Domain SqlWorksheetRepositoryJpa 인터페이스와 JpaRepository를 모두 확장합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("sqlWorksheetRepositoryJpa")
interface SqlWorksheetRepositoryJpaImpl :
    SqlWorksheetRepositoryJpa,
    JpaRepository<SqlWorksheetEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository에서 자동 제공)

    override fun findByIdAndDeletedAtIsNull(id: Long): SqlWorksheetEntity?

    // Folder 기반 조회 (Spring Data JPA auto-implements)
    override fun findByFolderIdAndDeletedAtIsNull(folderId: Long): List<SqlWorksheetEntity>

    override fun findByIdAndFolderIdAndDeletedAtIsNull(
        id: Long,
        folderId: Long,
    ): SqlWorksheetEntity?

    // Folder 내 Worksheet 개수
    override fun countByFolderIdAndDeletedAtIsNull(folderId: Long): Long

    // 이름 기반 조회
    override fun findByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): SqlWorksheetEntity?

    override fun existsByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): Boolean
}
