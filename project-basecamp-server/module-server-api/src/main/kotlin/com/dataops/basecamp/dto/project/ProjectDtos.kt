package com.dataops.basecamp.dto.project

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/**
 * Project 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateProjectRequest(
    @field:NotBlank(message = "Project name is required")
    @field:Size(max = 100, message = "Project name must not exceed 100 characters")
    @field:Pattern(
        regexp = "^[a-z][a-z0-9-]*$",
        message = "Project name must be lowercase with hyphens only (e.g., marketing-analytics)",
    )
    val name: String,
    @field:NotBlank(message = "Display name is required")
    @field:Size(max = 200, message = "Display name must not exceed 200 characters")
    val displayName: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
)

/**
 * Project 수정 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateProjectRequest(
    @field:Size(max = 200, message = "Display name must not exceed 200 characters")
    val displayName: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
)

/**
 * Project 상세 응답 DTO (GET /api/v1/projects/{projectId})
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProjectResponse(
    val id: Long,
    val name: String,
    val displayName: String,
    val description: String?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * Project 목록 응답 DTO (GET /api/v1/projects)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProjectListResponse(
    val content: List<ProjectResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
