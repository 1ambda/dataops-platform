package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity
import com.dataops.basecamp.domain.repository.sql.SqlSnippetRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * SQL Snippet JPA Repository 구현 인터페이스
 *
 * Domain SqlSnippetRepositoryJpa 인터페이스와 JpaRepository를 모두 확장합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("sqlSnippetRepositoryJpa")
interface SqlSnippetRepositoryJpaImpl :
    SqlSnippetRepositoryJpa,
    JpaRepository<SqlSnippetEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository에서 자동 제공)

    override fun findByIdAndDeletedAtIsNull(id: Long): SqlSnippetEntity?

    // Folder 기반 조회 (Spring Data JPA auto-implements)
    override fun findByFolderIdAndDeletedAtIsNull(folderId: Long): List<SqlSnippetEntity>

    override fun findByIdAndFolderIdAndDeletedAtIsNull(
        id: Long,
        folderId: Long,
    ): SqlSnippetEntity?

    // Folder 내 Snippet 개수
    override fun countByFolderIdAndDeletedAtIsNull(folderId: Long): Long

    // 이름 기반 조회
    override fun findByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): SqlSnippetEntity?

    override fun existsByNameAndFolderIdAndDeletedAtIsNull(
        name: String,
        folderId: Long,
    ): Boolean
}
