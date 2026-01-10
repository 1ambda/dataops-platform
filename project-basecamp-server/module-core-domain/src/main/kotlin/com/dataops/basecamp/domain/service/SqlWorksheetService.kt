package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlWorksheetAlreadyExistsException
import com.dataops.basecamp.common.exception.SqlWorksheetNotFoundException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryDsl
import com.dataops.basecamp.domain.repository.sql.SqlWorksheetRepositoryJpa
import com.dataops.basecamp.domain.repository.sql.WorksheetFolderRepositoryJpa
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * SQL Worksheet 서비스
 *
 * Pure Hexagonal Architecture 패턴을 적용한 도메인 서비스입니다.
 * - Services는 concrete classes (no interfaces)
 * - 명령과 조회가 명확히 분리됨
 * - Domain Entity를 직접 반환 (DTO 변환은 API layer에서 처리)
 * - 소프트 삭제(deletedAt)를 지원합니다
 *
 * v3.0.0: SqlFolder가 Team 기반으로 마이그레이션됨
 * - Folder 검증은 folderId 존재 여부로만 확인 (teamId 컨텍스트 없이)
 * - TODO: Worksheet도 Team 기반으로 마이그레이션 예정
 */
@Service
@Transactional(readOnly = true)
class SqlWorksheetService(
    private val sqlWorksheetRepositoryJpa: SqlWorksheetRepositoryJpa,
    private val sqlWorksheetRepositoryDsl: SqlWorksheetRepositoryDsl,
    private val worksheetFolderRepositoryJpa: WorksheetFolderRepositoryJpa,
) {
    // === 명령(Command) 처리 ===

    /**
     * SQL Worksheet 생성 명령 처리
     *
     * @param projectId Project ID (deprecated - Team 마이그레이션 후 제거 예정)
     * @param folderId Folder ID
     * @param name 워크시트 이름
     * @param description 워크시트 설명
     * @param sqlText SQL 쿼리 텍스트
     * @param dialect SQL dialect
     * @return 생성된 SQL Worksheet Entity
     * @throws WorksheetFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlWorksheetAlreadyExistsException 워크시트 이름이 이미 존재하는 경우
     */
    @Transactional
    fun createWorksheet(
        @Suppress("UNUSED_PARAMETER") projectId: Long, // TODO: Remove after Team migration
        folderId: Long,
        name: String,
        description: String? = null,
        sqlText: String,
        dialect: SqlDialect = SqlDialect.BIGQUERY,
    ): SqlWorksheetEntity {
        // Folder 존재 확인 (folderId만으로 검증)
        validateFolderExists(folderId)

        // 이름 중복 체크 (Folder 내에서 unique)
        if (sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(name, folderId)) {
            throw SqlWorksheetAlreadyExistsException(name, folderId)
        }

        val worksheet =
            SqlWorksheetEntity(
                folderId = folderId,
                name = name,
                description = description,
                sqlText = sqlText,
                dialect = dialect,
            )

        return sqlWorksheetRepositoryJpa.save(worksheet)
    }

    /**
     * SQL Worksheet 수정 명령 처리
     *
     * @param projectId Project ID (deprecated - Team 마이그레이션 후 제거 예정)
     * @param folderId Folder ID
     * @param worksheetId Worksheet ID
     * @param name 워크시트 이름 (null이면 변경 없음)
     * @param description 워크시트 설명 (null이면 변경 없음)
     * @param sqlText SQL 쿼리 텍스트 (null이면 변경 없음)
     * @param dialect SQL dialect (null이면 변경 없음)
     * @return 수정된 SQL Worksheet Entity
     * @throws WorksheetFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlWorksheetNotFoundException Worksheet을 찾을 수 없는 경우
     * @throws SqlWorksheetAlreadyExistsException 새 이름이 이미 존재하는 경우
     */
    @Transactional
    fun updateWorksheet(
        @Suppress("UNUSED_PARAMETER") projectId: Long, // TODO: Remove after Team migration
        folderId: Long,
        worksheetId: Long,
        name: String? = null,
        description: String? = null,
        sqlText: String? = null,
        dialect: SqlDialect? = null,
    ): SqlWorksheetEntity {
        // Folder 존재 확인
        validateFolderExists(folderId)

        val worksheet = getWorksheetByIdOrThrow(folderId, worksheetId)

        // 이름 변경 시 중복 체크
        name?.let { newName ->
            if (newName != worksheet.name &&
                sqlWorksheetRepositoryJpa.existsByNameAndFolderIdAndDeletedAtIsNull(newName, folderId)
            ) {
                throw SqlWorksheetAlreadyExistsException(newName, folderId)
            }
        }

        worksheet.update(name, description, sqlText, dialect)
        return sqlWorksheetRepositoryJpa.save(worksheet)
    }

    /**
     * SQL Worksheet 삭제 명령 처리 (Soft Delete)
     *
     * @param projectId Project ID (deprecated - Team 마이그레이션 후 제거 예정)
     * @param folderId Folder ID
     * @param worksheetId Worksheet ID
     * @throws WorksheetFolderNotFoundException Folder가 존재하지 않는 경우
     * @throws SqlWorksheetNotFoundException Worksheet을 찾을 수 없는 경우
     */
    @Transactional
    fun deleteWorksheet(
        @Suppress("UNUSED_PARAMETER") projectId: Long, // TODO: Remove after Team migration
        folderId: Long,
        worksheetId: Long,
    ) {
        // Folder 존재 확인
        validateFolderExists(folderId)

        val worksheet = getWorksheetByIdOrThrow(folderId, worksheetId)
        worksheet.deletedAt = LocalDateTime.now()
        sqlWorksheetRepositoryJpa.save(worksheet)
    }

    // === 조회(Query) 처리 ===

    /**
     * SQL Worksheet 단건 조회 (by ID and Folder ID)
     *
     * @param folderId Folder ID
     * @param worksheetId Worksheet ID
     * @return SQL Worksheet Entity (없으면 null)
     */
    fun getWorksheetById(
        folderId: Long,
        worksheetId: Long,
    ): SqlWorksheetEntity? = sqlWorksheetRepositoryJpa.findByIdAndFolderIdAndDeletedAtIsNull(worksheetId, folderId)

    /**
     * SQL Worksheet 단건 조회 (by ID and Folder ID, Not Null)
     *
     * @param folderId Folder ID
     * @param worksheetId Worksheet ID
     * @return SQL Worksheet Entity
     * @throws SqlWorksheetNotFoundException Worksheet을 찾을 수 없는 경우
     */
    fun getWorksheetByIdOrThrow(
        folderId: Long,
        worksheetId: Long,
    ): SqlWorksheetEntity =
        getWorksheetById(folderId, worksheetId)
            ?: throw SqlWorksheetNotFoundException(worksheetId, folderId)

    /**
     * Worksheet 단건 조회 (by ID only)
     * TODO: v3.0.0 - Team 마이그레이션 후 projectId 검증 추가 예정
     *
     * @param worksheetId Worksheet ID
     * @return SQL Worksheet Entity (없으면 null)
     */
    fun findWorksheetById(worksheetId: Long): SqlWorksheetEntity? =
        sqlWorksheetRepositoryJpa.findByIdAndDeletedAtIsNull(worksheetId)

    /**
     * Project 내 SQL Worksheet 목록 조회 (조건 검색, 페이징)
     *
     * @param projectId Project ID (deprecated - Team 마이그레이션 후 제거 예정)
     * @param folderId 특정 폴더로 필터링 (null인 경우 전체)
     * @param searchText 이름/설명/SQL 검색 (null인 경우 전체)
     * @param starred starred 필터 (null인 경우 전체)
     * @param dialect SQL dialect 필터 (null인 경우 전체)
     * @param pageable 페이징 정보
     * @return 페이징된 SQL Worksheet 목록 (soft delete 제외)
     */
    fun listWorksheets(
        projectId: Long, // TODO: Remove after Team migration
        folderId: Long?,
        searchText: String?,
        starred: Boolean?,
        dialect: SqlDialect?,
        pageable: Pageable,
    ): Page<SqlWorksheetEntity> =
        sqlWorksheetRepositoryDsl.findByConditions(
            projectId = projectId,
            folderId = folderId,
            searchText = searchText,
            starred = starred,
            dialect = dialect,
            pageable = pageable,
        )

    /**
     * Folder 내 SQL Worksheet 개수 조회
     *
     * @param folderId Folder ID
     * @return Worksheet 개수 (soft delete 제외)
     */
    fun countWorksheets(folderId: Long): Long = sqlWorksheetRepositoryJpa.countByFolderIdAndDeletedAtIsNull(folderId)

    /**
     * 여러 Folder의 Worksheet 개수 조회
     *
     * @param folderIds Folder ID 목록
     * @return Map<FolderId, Count>
     */
    fun countWorksheetsByFolderIds(folderIds: List<Long>): Map<Long, Long> =
        sqlWorksheetRepositoryDsl.countByFolderIds(folderIds)

    // === Private Helper Methods ===

    /**
     * Folder 존재 여부 검증 (folderId만으로 검증)
     * v3.0.0: SqlFolder가 Team 기반으로 변경되어 projectId 없이 검증
     */
    private fun validateFolderExists(folderId: Long) {
        worksheetFolderRepositoryJpa.findByIdAndDeletedAtIsNull(folderId)
            ?: throw WorksheetFolderNotFoundException(folderId, 0L) // teamId는 컨텍스트 없이 0으로 설정
    }
}
