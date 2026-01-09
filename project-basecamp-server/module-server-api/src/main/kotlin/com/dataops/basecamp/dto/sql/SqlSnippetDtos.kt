package com.dataops.basecamp.dto.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * SQL Snippet 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSqlSnippetRequest(
    val folderId: Long,
    @field:NotBlank(message = "Snippet name is required")
    @field:Size(max = 200, message = "Snippet name must not exceed 200 characters")
    val name: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    @field:NotBlank(message = "SQL text is required")
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

/**
 * SQL Snippet 수정 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateSqlSnippetRequest(
    @field:Size(max = 200, message = "Snippet name must not exceed 200 characters")
    val name: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

/**
 * SQL Snippet 요약 응답 DTO (목록용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlSnippetSummaryResponse(
    val id: Long,
    val folderId: Long,
    val folderName: String,
    val name: String,
    val description: String?,
    val dialect: SqlDialect,
    val isStarred: Boolean,
    val runCount: Int,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val lastRunAt: String?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * SQL Snippet 상세 응답 DTO (단건 조회용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlSnippetDetailResponse(
    val id: Long,
    val folderId: Long,
    val folderName: String,
    val name: String,
    val description: String?,
    val sqlText: String,
    val dialect: SqlDialect,
    val isStarred: Boolean,
    val runCount: Int,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val lastRunAt: String?,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: String,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: String,
)

/**
 * SQL Snippet 목록 응답 DTO (페이징 포함)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlSnippetListResponse(
    val content: List<SqlSnippetSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
