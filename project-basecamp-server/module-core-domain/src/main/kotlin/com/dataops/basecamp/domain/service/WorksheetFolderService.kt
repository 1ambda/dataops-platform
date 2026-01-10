package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.WorksheetFolderAlreadyExistsException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * Worksheet Folder 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 소프트 삭제(deletedAt)를 지원합니다
 *
 * v3.0.0: Project 기반에서 Team 기반으로 마이그레이션됨
 */
@Service
@Transactional(readOnly = true)
class WorksheetFolderService(
    private val worksheetFolderRepositoryJpa: WorksheetFolderRepositoryJpa,
    private val worksheetFolderRepositoryDsl: WorksheetFolderRepositoryDsl,
) {
    // === 명령(Command) 처리 ===

    /**
     * Worksheet Folder 생성 명령 처리
     *
     * @param teamId Team ID
     * @param name 폴더 이름
     * @param description 폴더 설명
     * @param displayOrder 표시 순서
     * @return 생성된 Worksheet Folder Entity
     * @throws WorksheetFolderAlreadyExistsException 폴더 이름이 이미 존재하는 경우
     */
    @Transactional
    fun createFolder(
        teamId: Long,
        name: String,
        description: String? = null,
        displayOrder: Int = 0,
    ): WorksheetFolderEntity {
        // 이름 중복 체크 (Team 내에서 unique)
        if (worksheetFolderRepositoryJpa.existsByNameAndTeamIdAndDeletedAtIsNull(name, teamId)) {
            throw WorksheetFolderAlreadyExistsException(name, teamId)
        }

        val folder =
            WorksheetFolderEntity(
                teamId = teamId,
                name = name,
                description = description,
                displayOrder = displayOrder,
            )

        return worksheetFolderRepositoryJpa.save(folder)
    }

    /**
     * Worksheet Folder 삭제 명령 처리 (Soft Delete)
     *
     * @param teamId Team ID
     * @param folderId Folder ID
     * @throws WorksheetFolderNotFoundException Folder를 찾을 수 없는 경우
     */
    @Transactional
    fun deleteFolder(
        teamId: Long,
        folderId: Long,
    ) {
        val folder =
            worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)
                ?: throw WorksheetFolderNotFoundException(folderId, teamId)

        folder.deletedAt = LocalDateTime.now()
        worksheetFolderRepositoryJpa.save(folder)
    }

    // === 조회(Query) 처리 ===

    /**
     * Worksheet Folder 단건 조회 (by ID and Team ID)
     *
     * @param teamId Team ID
     * @param folderId Folder ID
     * @return Worksheet Folder Entity (없으면 null)
     */
    fun getFolderById(
        teamId: Long,
        folderId: Long,
    ): WorksheetFolderEntity? = worksheetFolderRepositoryJpa.findByIdAndTeamIdAndDeletedAtIsNull(folderId, teamId)

    /**
     * Worksheet Folder 단건 조회 (by ID and Team ID, Not Null)
     *
     * @param teamId Team ID
     * @param folderId Folder ID
     * @return Worksheet Folder Entity
     * @throws WorksheetFolderNotFoundException Folder를 찾을 수 없는 경우
     */
    fun getFolderByIdOrThrow(
        teamId: Long,
        folderId: Long,
    ): WorksheetFolderEntity =
        getFolderById(teamId, folderId)
            ?: throw WorksheetFolderNotFoundException(folderId, teamId)

    /**
     * Team 내 모든 Worksheet Folder 목록 조회 (displayOrder 순)
     *
     * @param teamId Team ID
     * @return Worksheet Folder 목록 (soft delete 제외)
     */
    fun listFolders(teamId: Long): List<WorksheetFolderEntity> =
        worksheetFolderRepositoryDsl.findAllByTeamIdOrderByDisplayOrder(teamId)

    /**
     * Team 내 Worksheet Folder 개수 조회
     *
     * @param teamId Team ID
     * @return Folder 개수 (soft delete 제외)
     */
    fun countFolders(teamId: Long): Long = worksheetFolderRepositoryJpa.countByTeamIdAndDeletedAtIsNull(teamId)
}
