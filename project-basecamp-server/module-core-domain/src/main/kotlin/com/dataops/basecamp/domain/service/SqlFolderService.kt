package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.SqlFolderAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlFolderEntity
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlFolderRepositoryJpa
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * SQL Folder 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 소프트 삭제(deletedAt)를 지원합니다
 */
@Service
@Transactional(readOnly = true)
class SqlFolderService(
    private val sqlFolderRepositoryJpa: SqlFolderRepositoryJpa,
    private val sqlFolderRepositoryDsl: SqlFolderRepositoryDsl,
    private val projectService: ProjectService,
) {
    // === 명령(Command) 처리 ===

    /**
     * SQL Folder 생성 명령 처리
     *
     * @param projectId Project ID
     * @param name 폴더 이름
     * @param description 폴더 설명
     * @param displayOrder 표시 순서
     * @return 생성된 SQL Folder Entity
     * @throws ProjectNotFoundException Project가 존재하지 않는 경우
     * @throws SqlFolderAlreadyExistsException 폴더 이름이 이미 존재하는 경우
     */
    @Transactional
    fun createFolder(
        projectId: Long,
        name: String,
        description: String? = null,
        displayOrder: Int = 0,
    ): SqlFolderEntity {
        // Project 존재 확인
        projectService.getProjectByIdOrThrow(projectId)

        // 이름 중복 체크 (Project 내에서 unique)
        if (sqlFolderRepositoryJpa.existsByNameAndProjectIdAndDeletedAtIsNull(name, projectId)) {
            throw SqlFolderAlreadyExistsException(name, projectId)
        }

        val folder =
            SqlFolderEntity(
                projectId = projectId,
                name = name,
                description = description,
                displayOrder = displayOrder,
            )

        return sqlFolderRepositoryJpa.save(folder)
    }

    /**
     * SQL Folder 삭제 명령 처리 (Soft Delete)
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @throws ProjectNotFoundException Project가 존재하지 않는 경우
     * @throws SqlFolderNotFoundException Folder를 찾을 수 없는 경우
     */
    @Transactional
    fun deleteFolder(
        projectId: Long,
        folderId: Long,
    ) {
        // Project 존재 확인
        projectService.getProjectByIdOrThrow(projectId)

        val folder =
            sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
                ?: throw SqlFolderNotFoundException(folderId, projectId)

        folder.deletedAt = LocalDateTime.now()
        sqlFolderRepositoryJpa.save(folder)
    }

    // === 조회(Query) 처리 ===

    /**
     * SQL Folder 단건 조회 (by ID and Project ID)
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @return SQL Folder Entity (없으면 null)
     */
    fun getFolderById(
        projectId: Long,
        folderId: Long,
    ): SqlFolderEntity? {
        // Project 존재 확인
        projectService.getProjectByIdOrThrow(projectId)

        return sqlFolderRepositoryJpa.findByIdAndProjectIdAndDeletedAtIsNull(folderId, projectId)
    }

    /**
     * SQL Folder 단건 조회 (by ID and Project ID, Not Null)
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @return SQL Folder Entity
     * @throws ProjectNotFoundException Project가 존재하지 않는 경우
     * @throws SqlFolderNotFoundException Folder를 찾을 수 없는 경우
     */
    fun getFolderByIdOrThrow(
        projectId: Long,
        folderId: Long,
    ): SqlFolderEntity =
        getFolderById(projectId, folderId)
            ?: throw SqlFolderNotFoundException(folderId, projectId)

    /**
     * Project 내 모든 SQL Folder 목록 조회 (displayOrder 순)
     *
     * @param projectId Project ID
     * @return SQL Folder 목록 (soft delete 제외)
     * @throws ProjectNotFoundException Project가 존재하지 않는 경우
     */
    fun listFolders(projectId: Long): List<SqlFolderEntity> {
        // Project 존재 확인
        projectService.getProjectByIdOrThrow(projectId)

        return sqlFolderRepositoryDsl.findAllByProjectIdOrderByDisplayOrder(projectId)
    }

    /**
     * Project 내 SQL Folder 개수 조회
     *
     * @param projectId Project ID
     * @return Folder 개수 (soft delete 제외)
     */
    fun countFolders(projectId: Long): Long = sqlFolderRepositoryJpa.countByProjectIdAndDeletedAtIsNull(projectId)
}
