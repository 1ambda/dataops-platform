package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * SQL Folder JPA Repository 구현 인터페이스
 *
 * Domain SqlFolderRepositoryJpa 인터페이스와 JpaRepository를 모두 확장합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("sqlFolderRepositoryJpa")
interface SqlFolderRepositoryJpaImpl :
    SqlFolderRepositoryJpa,
    JpaRepository<SqlFolderEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository에서 자동 제공)

    override fun findByIdAndDeletedAtIsNull(id: Long): SqlFolderEntity?

    // Project 기반 조회 (Spring Data JPA auto-implements)
    override fun findByProjectIdAndDeletedAtIsNull(projectId: Long): List<SqlFolderEntity>

    override fun findByIdAndProjectIdAndDeletedAtIsNull(
        id: Long,
        projectId: Long,
    ): SqlFolderEntity?

    // 이름 기반 조회
    override fun findByNameAndProjectIdAndDeletedAtIsNull(
        name: String,
        projectId: Long,
    ): SqlFolderEntity?

    override fun existsByNameAndProjectIdAndDeletedAtIsNull(
        name: String,
        projectId: Long,
    ): Boolean

    // 삭제되지 않은 폴더 개수 (Project 내)
    override fun countByProjectIdAndDeletedAtIsNull(projectId: Long): Long
}
