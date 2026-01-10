package com.dataops.basecamp.infra.repository.sql

import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryJpa
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

/**
 * Worksheet Folder JPA Repository 구현 인터페이스
 *
 * Domain WorksheetFolderRepositoryJpa 인터페이스와 JpaRepository를 모두 확장합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
@Repository("worksheetFolderRepositoryJpa")
interface WorksheetFolderRepositoryJpaImpl :
    WorksheetFolderRepositoryJpa,
    JpaRepository<WorksheetFolderEntity, Long> {
    // 기본 CRUD 작업 (save는 JpaRepository에서 자동 제공)

    override fun findByIdAndDeletedAtIsNull(id: Long): WorksheetFolderEntity?

    // Team 기반 조회 (Spring Data JPA auto-implements)
    override fun findByTeamIdAndDeletedAtIsNull(teamId: Long): List<WorksheetFolderEntity>

    override fun findByIdAndTeamIdAndDeletedAtIsNull(
        id: Long,
        teamId: Long,
    ): WorksheetFolderEntity?

    // 이름 기반 조회
    override fun findByNameAndTeamIdAndDeletedAtIsNull(
        name: String,
        teamId: Long,
    ): WorksheetFolderEntity?

    override fun existsByNameAndTeamIdAndDeletedAtIsNull(
        name: String,
        teamId: Long,
    ): Boolean

    // 삭제되지 않은 폴더 개수 (Team 내)
    override fun countByTeamIdAndDeletedAtIsNull(teamId: Long): Long
}
