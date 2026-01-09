package com.dataops.basecamp.controller

import com.dataops.basecamp.common.enums.SqlDialect
import com.dataops.basecamp.common.exception.ProjectNotFoundException
import com.dataops.basecamp.common.exception.SqlFolderNotFoundException
import com.dataops.basecamp.common.exception.SqlSnippetNotFoundException
import com.dataops.basecamp.domain.service.ProjectService
import com.dataops.basecamp.domain.service.SqlFolderService
import com.dataops.basecamp.domain.service.SqlSnippetService
import com.dataops.basecamp.dto.project.CreateProjectRequest
import com.dataops.basecamp.dto.project.ProjectListResponse
import com.dataops.basecamp.dto.project.ProjectResponse
import com.dataops.basecamp.dto.project.UpdateProjectRequest
import com.dataops.basecamp.dto.sql.CreateSqlFolderRequest
import com.dataops.basecamp.dto.sql.CreateSqlSnippetRequest
import com.dataops.basecamp.dto.sql.SqlFolderListResponse
import com.dataops.basecamp.dto.sql.SqlFolderResponse
import com.dataops.basecamp.dto.sql.SqlSnippetDetailResponse
import com.dataops.basecamp.dto.sql.SqlSnippetListResponse
import com.dataops.basecamp.dto.sql.UpdateSqlSnippetRequest
import com.dataops.basecamp.mapper.ProjectMapper
import com.dataops.basecamp.mapper.SqlFolderMapper
import com.dataops.basecamp.mapper.SqlSnippetMapper
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * Project REST API Controller
 *
 * Project CRUD, SQL Folder 및 SQL Snippet 기능을 제공하는 REST API 컨트롤러입니다.
 *
 * Project API:
 * - GET /api/v1/projects (목록 조회 with 검색/페이징)
 * - POST /api/v1/projects (생성)
 * - GET /api/v1/projects/{projectId} (상세 조회)
 * - PUT /api/v1/projects/{projectId} (수정)
 * - DELETE /api/v1/projects/{projectId} (삭제 - soft delete)
 *
 * SQL Folder API:
 * - GET /api/v1/projects/{projectId}/sql/folders (폴더 목록 조회)
 * - POST /api/v1/projects/{projectId}/sql/folders (폴더 생성)
 * - GET /api/v1/projects/{projectId}/sql/folders/{folderId} (폴더 상세 조회)
 * - DELETE /api/v1/projects/{projectId}/sql/folders/{folderId} (폴더 삭제 - soft delete)
 *
 * SQL Snippet API:
 * - GET /api/v1/projects/{projectId}/sql/snippets (스니펫 목록 조회 with 검색/페이징)
 * - POST /api/v1/projects/{projectId}/sql/snippets (스니펫 생성)
 * - GET /api/v1/projects/{projectId}/sql/snippets/{snippetId} (스니펫 상세 조회)
 * - PUT /api/v1/projects/{projectId}/sql/snippets/{snippetId} (스니펫 수정)
 * - DELETE /api/v1/projects/{projectId}/sql/snippets/{snippetId} (스니펫 삭제 - soft delete)
 */
@RestController
@RequestMapping("/api/v1/projects")
@Validated
class ProjectController(
    private val projectService: ProjectService,
    private val sqlFolderService: SqlFolderService,
    private val sqlSnippetService: SqlSnippetService,
) {
    /**
     * Project 목록 조회 with 검색/페이징
     *
     * @param search 이름/displayName 검색 (부분 일치)
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1-100)
     * @return 필터 조건에 맞는 Project 목록
     */
    @GetMapping
    fun listProjects(
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<ProjectListResponse> {
        val pageRequest =
            PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "updatedAt"),
            )

        val projects = projectService.listProjects(search, pageRequest)
        val response = ProjectMapper.toListResponse(projects)

        return ResponseEntity.ok(response)
    }

    /**
     * Project 생성
     *
     * @param request Project 생성 요청
     * @return 생성된 Project 정보
     */
    @PostMapping
    fun createProject(
        @Valid @RequestBody request: CreateProjectRequest,
    ): ResponseEntity<ProjectResponse> {
        val entity = ProjectMapper.toEntity(request)
        val savedProject = projectService.createProject(entity)
        val response = ProjectMapper.toResponse(savedProject)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Project 상세 조회
     *
     * @param projectId Project ID
     * @return Project 상세 정보
     */
    @GetMapping("/{projectId}")
    fun getProject(
        @PathVariable projectId: Long,
    ): ResponseEntity<ProjectResponse> {
        val project =
            projectService.getProjectById(projectId)
                ?: throw ProjectNotFoundException(projectId)

        val response = ProjectMapper.toResponse(project)
        return ResponseEntity.ok(response)
    }

    /**
     * Project 수정
     *
     * @param projectId Project ID
     * @param request Project 수정 요청
     * @return 수정된 Project 정보
     */
    @PutMapping("/{projectId}")
    fun updateProject(
        @PathVariable projectId: Long,
        @Valid @RequestBody request: UpdateProjectRequest,
    ): ResponseEntity<ProjectResponse> {
        val updatedProject =
            projectService.updateProject(
                id = projectId,
                displayName = request.displayName,
                description = request.description,
            )

        val response = ProjectMapper.toResponse(updatedProject)
        return ResponseEntity.ok(response)
    }

    /**
     * Project 삭제 (Soft Delete)
     *
     * @param projectId Project ID
     * @return 204 No Content
     */
    @DeleteMapping("/{projectId}")
    fun deleteProject(
        @PathVariable projectId: Long,
    ): ResponseEntity<Void> {
        projectService.deleteProject(projectId)
        return ResponseEntity.noContent().build()
    }

    // ============= SQL Folder API =============

    /**
     * SQL Folder 목록 조회
     *
     * @param projectId Project ID
     * @return Project 내 모든 SQL Folder 목록 (displayOrder 순)
     */
    @GetMapping("/{projectId}/sql/folders")
    fun listSqlFolders(
        @PathVariable projectId: Long,
    ): ResponseEntity<SqlFolderListResponse> {
        val folders = sqlFolderService.listFolders(projectId)
        val response = SqlFolderMapper.toListResponse(folders, projectId)

        return ResponseEntity.ok(response)
    }

    /**
     * SQL Folder 생성
     *
     * @param projectId Project ID
     * @param request SQL Folder 생성 요청
     * @return 생성된 SQL Folder 정보
     */
    @PostMapping("/{projectId}/sql/folders")
    fun createSqlFolder(
        @PathVariable projectId: Long,
        @Valid @RequestBody request: CreateSqlFolderRequest,
    ): ResponseEntity<SqlFolderResponse> {
        val folder =
            sqlFolderService.createFolder(
                projectId = projectId,
                name = request.name,
                description = request.description,
                displayOrder = request.displayOrder ?: 0,
            )
        val response = SqlFolderMapper.toResponse(folder)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * SQL Folder 상세 조회
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @return SQL Folder 상세 정보
     */
    @GetMapping("/{projectId}/sql/folders/{folderId}")
    fun getSqlFolder(
        @PathVariable projectId: Long,
        @PathVariable folderId: Long,
    ): ResponseEntity<SqlFolderResponse> {
        val folder =
            sqlFolderService.getFolderById(projectId, folderId)
                ?: throw SqlFolderNotFoundException(folderId, projectId)

        val response = SqlFolderMapper.toResponse(folder)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Folder 삭제 (Soft Delete)
     *
     * @param projectId Project ID
     * @param folderId Folder ID
     * @return 204 No Content
     */
    @DeleteMapping("/{projectId}/sql/folders/{folderId}")
    fun deleteSqlFolder(
        @PathVariable projectId: Long,
        @PathVariable folderId: Long,
    ): ResponseEntity<Void> {
        sqlFolderService.deleteFolder(projectId, folderId)
        return ResponseEntity.noContent().build()
    }

    // ============= SQL Snippet API =============

    /**
     * SQL Snippet 목록 조회 (검색/페이징)
     *
     * @param projectId Project ID
     * @param folderId 특정 폴더로 필터링 (선택)
     * @param searchText 이름/설명/SQL 검색 (선택)
     * @param starred starred 필터 (선택)
     * @param dialect SQL dialect 필터 (선택)
     * @param page 페이지 번호 (0부터)
     * @param size 페이지 크기 (1-100)
     * @return 조건에 맞는 SQL Snippet 목록
     */
    @GetMapping("/{projectId}/sql/snippets")
    fun listSqlSnippets(
        @PathVariable projectId: Long,
        @RequestParam(required = false) folderId: Long?,
        @RequestParam(required = false) searchText: String?,
        @RequestParam(required = false) starred: Boolean?,
        @RequestParam(required = false) dialect: SqlDialect?,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
    ): ResponseEntity<SqlSnippetListResponse> {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))

        val snippetsPage =
            sqlSnippetService.listSnippets(
                projectId = projectId,
                folderId = folderId,
                searchText = searchText,
                starred = starred,
                dialect = dialect,
                pageable = pageable,
            )

        // Folder 이름 조회를 위한 매핑
        val folderIds = snippetsPage.content.map { it.folderId }.distinct()
        val folderNameMap =
            folderIds.associateWith { fid ->
                sqlFolderService.getFolderById(projectId, fid)?.name ?: "Unknown"
            }

        val response = SqlSnippetMapper.toListResponse(snippetsPage, folderNameMap)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Snippet 생성
     *
     * @param projectId Project ID
     * @param request SQL Snippet 생성 요청
     * @return 생성된 SQL Snippet 정보
     */
    @PostMapping("/{projectId}/sql/snippets")
    fun createSqlSnippet(
        @PathVariable projectId: Long,
        @Valid @RequestBody request: CreateSqlSnippetRequest,
    ): ResponseEntity<SqlSnippetDetailResponse> {
        val snippet =
            sqlSnippetService.createSnippet(
                projectId = projectId,
                folderId = request.folderId,
                name = request.name,
                description = request.description,
                sqlText = request.sqlText,
                dialect = request.dialect,
            )

        val folderName = sqlFolderService.getFolderById(projectId, request.folderId)?.name ?: "Unknown"
        val response = SqlSnippetMapper.toDetailResponse(snippet, folderName)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * SQL Snippet 상세 조회
     *
     * @param projectId Project ID
     * @param snippetId Snippet ID
     * @return SQL Snippet 상세 정보
     */
    @GetMapping("/{projectId}/sql/snippets/{snippetId}")
    fun getSqlSnippet(
        @PathVariable projectId: Long,
        @PathVariable snippetId: Long,
    ): ResponseEntity<SqlSnippetDetailResponse> {
        val result = findSnippetInProject(projectId, snippetId)
        val response = SqlSnippetMapper.toDetailResponse(result.snippet, result.folderName)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Snippet 수정
     *
     * @param projectId Project ID
     * @param snippetId Snippet ID
     * @param request SQL Snippet 수정 요청
     * @return 수정된 SQL Snippet 정보
     */
    @PutMapping("/{projectId}/sql/snippets/{snippetId}")
    fun updateSqlSnippet(
        @PathVariable projectId: Long,
        @PathVariable snippetId: Long,
        @Valid @RequestBody request: UpdateSqlSnippetRequest,
    ): ResponseEntity<SqlSnippetDetailResponse> {
        val result = findSnippetInProject(projectId, snippetId)

        val updatedSnippet =
            sqlSnippetService.updateSnippet(
                projectId = projectId,
                folderId = result.folderId,
                snippetId = snippetId,
                name = request.name,
                description = request.description,
                sqlText = request.sqlText,
                dialect = request.dialect,
            )

        val response = SqlSnippetMapper.toDetailResponse(updatedSnippet, result.folderName)
        return ResponseEntity.ok(response)
    }

    /**
     * SQL Snippet 삭제 (Soft Delete)
     *
     * @param projectId Project ID
     * @param snippetId Snippet ID
     * @return 204 No Content
     */
    @DeleteMapping("/{projectId}/sql/snippets/{snippetId}")
    fun deleteSqlSnippet(
        @PathVariable projectId: Long,
        @PathVariable snippetId: Long,
    ): ResponseEntity<Void> {
        val result = findSnippetInProject(projectId, snippetId)
        sqlSnippetService.deleteSnippet(projectId, result.folderId, snippetId)
        return ResponseEntity.noContent().build()
    }

    // ===========================================
    // Private Helper Methods
    // ===========================================

    /**
     * Project 내 모든 폴더를 검색하여 snippetId에 해당하는 Snippet 찾기
     */
    private fun findSnippetInProject(
        projectId: Long,
        snippetId: Long,
    ): SnippetLookupResult {
        val folders = sqlFolderService.listFolders(projectId)

        for (folder in folders) {
            val snippet = sqlSnippetService.getSnippetById(folder.id!!, snippetId)
            if (snippet != null) {
                return SnippetLookupResult(
                    snippet = snippet,
                    folderId = folder.id!!,
                    folderName = folder.name,
                )
            }
        }

        throw SqlSnippetNotFoundException(snippetId, 0L)
    }

    private data class SnippetLookupResult(
        val snippet: com.dataops.basecamp.domain.entity.sql.SqlSnippetEntity,
        val folderId: Long,
        val folderName: String,
    )
}
