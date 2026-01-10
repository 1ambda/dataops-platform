package com.dataops.basecamp.dto.sql

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * Worksheet Folder 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateWorksheetFolderRequest(
    @field:NotBlank(message = "Folder name is required")
    @field:Size(max = 100, message = "Folder name must not exceed 100 characters")
    val name: String,
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    val description: String? = null,
    val displayOrder: Int? = 0,
)

/**
 * Worksheet Folder 상세 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorksheetFolderResponse(
    val id: Long,
    val teamId: Long,
    val name: String,
    val description: String?,
    val displayOrder: Int,
    val snippetCount: Int, // 폴더 내 snippet 개수 (향후 추가, 일단 0)
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * Worksheet Folder 목록 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorksheetFolderListResponse(
    val content: List<WorksheetFolderResponse>,
    val teamId: Long,
)
