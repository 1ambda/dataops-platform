package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.SqlWorksheetNotFoundException
import com.dataops.basecamp.common.exception.WorksheetFolderNotFoundException
import com.dataops.basecamp.domain.service.SqlWorksheetService
import com.dataops.basecamp.domain.service.WorksheetFolderService
import com.dataops.basecamp.dto.sql.CreateSqlWorksheetRequest
import com.dataops.basecamp.dto.sql.CreateWorksheetFolderRequest
import com.dataops.basecamp.dto.sql.SqlWorksheetDetailResponse
import com.dataops.basecamp.dto.sql.SqlWorksheetListResponse
import com.dataops.basecamp.dto.sql.UpdateSqlWorksheetRequest
import com.dataops.basecamp.dto.sql.WorksheetFolderListResponse
import com.dataops.basecamp.dto.sql.WorksheetFolderResponse
import com.dataops.basecamp.mapper.SqlWorksheetMapper
import com.dataops.basecamp.mapper.WorksheetFolderMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Team REST API Controller
 *
 * Team 기반 SQL Folder 및 SQL Worksheet 기능을 제공하는 REST API 컨트롤러입니다.
 *
 * SQL Folder API:
 * - GET /api/v1/teams/{teamId}/sql/folders (폴더 목록 조회)
 * - POST /api/v1/teams/{teamId}/sql/folders (폴더 생성)
 * - GET /api/v1/teams/{teamId}/sql/folders/{folderId} (폴더 상세 조회)
 * - DELETE /api/v1/teams/{teamId}/sql/folders/{folderId} (폴더 삭제 - soft delete)
 *
 * SQL Worksheet API:
 * - GET /api/v1/teams/{teamId}/sql/worksheets (워크시트 목록 조회 with 검색/페이징)
 * - POST /api/v1/teams/{teamId}/sql/worksheets (워크시트 생성)
 * - GET /api/v1/teams/{teamId}/sql/worksheets/{worksheetId} (워크시트 상세 조회)
 * - PUT /api/v1/teams/{teamId}/sql/worksheets/{worksheetId} (워크시트 수정)
 * - DELETE /api/v1/teams/{teamId}/sql/worksheets/{worksheetId} (워크시트 삭제 - soft delete)
 *
 * v3.0.0: Project 기반에서 Team 기반으로 마이그레이션됨
 */
@RestController
@RequestMapping("/api/v1/teams")
@Validated
class TeamController(
    private val worksheetFolderService: WorksheetFolderService,
    private val sqlWorksheetService: SqlWorksheetService,
) {
    // ============= SQL Folder API =============

    /**
     * SQL Folder 목록 조회
     *
     * @param teamId Team ID
     * @return Team 내 모든 SQL Folder 목록 (displayOrder 순)
     */
    @GetMapping("/{teamId}/sql/folders")
    fun listSqlFolders(
        @PathVariable teamId: Long,
    ): ResponseEntity<WorksheetFolderListResponse> {
        val folders = worksheetFolderService.listFolders(teamId)
        val response = WorksheetFolderMapper.toListResponse(folders, teamId)

        return ResponseEntity.ok(response)
    }

    /**
     * SQL Folder 생성
     *
     * @param teamId Team ID
     * @param request SQL Folder 생성 요청
     * @return 생성된 SQL Folder 정보
     */
    @PostMapping("/{teamId}/sql/folders")
    fun createSqlFolder(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: CreateWorksheetFolderRequest,
    ): ResponseEntity<WorksheetFolderResponse> {
        val folder =
            worksheetFolderService.createFolder(
                teamId = teamId,
                name = request.name,
                description = request.description,
                displayOrder = request.displayOrder ?: 0,
            )
        val response = WorksheetFolderMapper.toResponse(folder)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * SQL Folder 상세 조회
     *
     * @param teamId Team ID
     * @param folderId Folder ID
     * @return SQL Folder 상세 정보
     */
    @GetMapping("/{teamId}/sql/folders/{folderId}")
    fun getSqlFolder(
        @PathVariable teamId: Long,
        @PathVariable folderId: Long,
    ): ResponseEntity<WorksheetFolderResponse> {
        val folder =
            worksheetFolderService.getFolderById(teamId, folderId)
                ?: throw WorksheetFolderNotFoundException(folderId, teamId)

        val response = WorksheetFolderMapper.toResponse(folder)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Folder 삭제 (Soft Delete)
     *
     * @param teamId Team ID
     * @param folderId Folder ID
     * @return 204 No Content
     */
    @DeleteMapping("/{teamId}/sql/folders/{folderId}")
    fun deleteSqlFolder(
        @PathVariable teamId: Long,
        @PathVariable folderId: Long,
    ): ResponseEntity<Void> {
        worksheetFolderService.deleteFolder(teamId, folderId)
        return ResponseEntity.noContent().build()
    }

    // ============= SQL Worksheet API =============

    /**
     * SQL Worksheet 목록 조회 (검색/페이징)
     *
     * @param teamId Team ID
     * @param folderId 특정 폴더로 필터링 (선택)
     * @param searchText 이름/설명/SQL 검색 (선택)
     * @param starred starred 필터 (선택)
     * @param dialect SQL dialect 필터 (선택)
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1-100)
     * @return 조건에 맞는 SQL Worksheet 목록
     */
    @GetMapping("/{teamId}/sql/worksheets")
    fun listSqlWorksheets(
        @PathVariable teamId: Long,
        @RequestParam(required = false) folderId: Long?,
        @RequestParam(required = false) searchText: String?,
        @RequestParam(required = false) starred: Boolean?,
        @RequestParam(required = false) dialect: SqlDialect?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<SqlWorksheetListResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))

        // Note: SqlWorksheetService.listWorksheets uses projectId parameter,
        // but internally it operates on teamId since Worksheet belongs to Folder which belongs to Team
        val worksheetsPage =
            sqlWorksheetService.listWorksheets(
                projectId = teamId,
                folderId = folderId,
                searchText = searchText,
                starred = starred,
                dialect = dialect,
                pageable = pageable,
            )

        // Folder 이름 조회를 위한 매핑 생성
        val folderIds = worksheetsPage.content.map { it.folderId }.distinct()
        val folderNameMap = buildFolderNameMap(teamId, folderIds)

        val response = SqlWorksheetMapper.toListResponse(worksheetsPage, folderNameMap)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Worksheet 생성
     *
     * @param teamId Team ID
     * @param request SQL Worksheet 생성 요청
     * @return 생성된 SQL Worksheet 정보
     */
    @PostMapping("/{teamId}/sql/worksheets")
    fun createSqlWorksheet(
        @PathVariable teamId: Long,
        @Valid @RequestBody request: CreateSqlWorksheetRequest,
    ): ResponseEntity<SqlWorksheetDetailResponse> {
        // Folder 존재 확인 및 이름 조회
        val folder =
            worksheetFolderService.getFolderById(teamId, request.folderId)
                ?: throw WorksheetFolderNotFoundException(request.folderId, teamId)

        val worksheet =
            sqlWorksheetService.createWorksheet(
                projectId = teamId,
                folderId = request.folderId,
                name = request.name,
                description = request.description,
                sqlText = request.sqlText,
                dialect = request.dialect,
            )

        val response = SqlWorksheetMapper.toDetailResponse(worksheet, folder.name)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * SQL Worksheet 상세 조회
     *
     * @param teamId Team ID
     * @param worksheetId Worksheet ID
     * @return SQL Worksheet 상세 정보
     */
    @GetMapping("/{teamId}/sql/worksheets/{worksheetId}")
    fun getSqlWorksheet(
        @PathVariable teamId: Long,
        @PathVariable worksheetId: Long,
    ): ResponseEntity<SqlWorksheetDetailResponse> {
        val result = findWorksheetInTeam(teamId, worksheetId)
        val response = SqlWorksheetMapper.toDetailResponse(result.worksheet, result.folderName)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Worksheet 수정
     *
     * @param teamId Team ID
     * @param worksheetId Worksheet ID
     * @param request SQL Worksheet 수정 요청
     * @return 수정된 SQL Worksheet 정보
     */
    @PutMapping("/{teamId}/sql/worksheets/{worksheetId}")
    fun updateSqlWorksheet(
        @PathVariable teamId: Long,
        @PathVariable worksheetId: Long,
        @Valid @RequestBody request: UpdateSqlWorksheetRequest,
    ): ResponseEntity<SqlWorksheetDetailResponse> {
        val result = findWorksheetInTeam(teamId, worksheetId)

        val updatedWorksheet =
            sqlWorksheetService.updateWorksheet(
                projectId = teamId,
                folderId = result.folderId,
                worksheetId = worksheetId,
                name = request.name,
                description = request.description,
                sqlText = request.sqlText,
                dialect = request.dialect,
            )

        val response = SqlWorksheetMapper.toDetailResponse(updatedWorksheet, result.folderName)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Worksheet 삭제 (Soft Delete)
     *
     * @param teamId Team ID
     * @param worksheetId Worksheet ID
     * @return 204 No Content
     */
    @DeleteMapping("/{teamId}/sql/worksheets/{worksheetId}")
    fun deleteSqlWorksheet(
        @PathVariable teamId: Long,
        @PathVariable worksheetId: Long,
    ): ResponseEntity<Void> {
        val result = findWorksheetInTeam(teamId, worksheetId)
        sqlWorksheetService.deleteWorksheet(teamId, result.folderId, worksheetId)
        return ResponseEntity.noContent().build()
    }

    // ===========================================
    // Private Helper Methods
    // ===========================================

    /**
     * Team 내 worksheetId에 해당하는 Worksheet 찾기
     */
    private fun findWorksheetInTeam(
        teamId: Long,
        worksheetId: Long,
    ): WorksheetLookupResult {
        // Worksheet를 직접 조회하여 folderId 확인
        val worksheet =
            sqlWorksheetService.findWorksheetById(worksheetId)
                ?: throw SqlWorksheetNotFoundException(worksheetId, 0L)

        // Folder가 해당 Team에 속하는지 확인하고 이름 조회
        val folder =
            worksheetFolderService.getFolderById(teamId, worksheet.folderId)
                ?: throw SqlWorksheetNotFoundException(worksheetId, worksheet.folderId)

        return WorksheetLookupResult(
            worksheet = worksheet,
            folderId = worksheet.folderId,
            folderName = folder.name,
        )
    }

    /**
     * FolderId 목록에 대한 FolderName 매핑 생성
     */
    private fun buildFolderNameMap(
        teamId: Long,
        folderIds: List<Long>,
    ): Map<Long, String> =
        folderIds
            .mapNotNull { folderId ->
                worksheetFolderService.getFolderById(teamId, folderId)?.let { folder ->
                    folderId to folder.name
                }
            }.toMap()

    private data class WorksheetLookupResult(
        val worksheet: com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity,
        val folderId: Long,
        val folderName: String,
    )
}
