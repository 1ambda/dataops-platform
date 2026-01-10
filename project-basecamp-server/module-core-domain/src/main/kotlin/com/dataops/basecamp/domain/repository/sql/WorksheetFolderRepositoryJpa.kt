package com.dataops.basecamp.domain.repository.sql

import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity

/**
 * Worksheet Folder Repository JPA 인터페이스 (순수 도메인 추상화)
 *
 * Worksheet Folder에 대한 기본 CRUD 작업을 정의합니다.
 * Pure Hexagonal Architecture 패턴에 따라 구현되었습니다.
 */
interface WorksheetFolderRepositoryJpa {
    // 기본 CRUD 작업
    fun save(folder: WorksheetFolderEntity): WorksheetFolderEntity

    fun findById(id: Long): WorksheetFolderEntity?

    fun findByIdAndDeletedAtIsNull(id: Long): WorksheetFolderEntity?

    // Team 기반 조회
    fun findByTeamIdAndDeletedAtIsNull(teamId: Long): List<WorksheetFolderEntity>

    fun findByIdAndTeamIdAndDeletedAtIsNull(
        id: Long,
        teamId: Long,
    ): WorksheetFolderEntity?

    // 이름 기반 조회 (Team 내에서 unique)
    fun findByNameAndTeamIdAndDeletedAtIsNull(
        name: String,
        teamId: Long,
    ): WorksheetFolderEntity?

    fun existsByNameAndTeamIdAndDeletedAtIsNull(
        name: String,
        teamId: Long,
    ): Boolean

    // 삭제되지 않은 폴더 개수 (Team 내)
    fun countByTeamIdAndDeletedAtIsNull(teamId: Long): Long
}
