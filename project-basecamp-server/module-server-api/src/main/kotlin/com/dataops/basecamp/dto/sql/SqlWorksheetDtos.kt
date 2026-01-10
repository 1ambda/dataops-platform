package com.dataops.basecamp.dto.sql

import com.dataops.basecamp.common.enums.SqlDialect
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/**
 * SQL Worksheet 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSqlWorksheetRequest(
    val folderId: Long,
    @field:NotBlank(message = "Worksheet name is required")
    @field:Size(max = 200, message = "Worksheet name must not exceed 200 characters")
    val name: String,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    @field:NotBlank(message = "SQL text is required")
    val sqlText: String,
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
)

/**
 * SQL Worksheet 수정 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdateSqlWorksheetRequest(
    @field:Size(max = 200, message = "Worksheet name must not exceed 200 characters")
    val name: String? = null,
    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,
    val sqlText: String? = null,
    val dialect: SqlDialect? = null,
)

/**
 * SQL Worksheet 요약 응답 DTO (목록용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlWorksheetSummaryResponse(
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
 * SQL Worksheet 상세 응답 DTO (단건 조회용)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlWorksheetDetailResponse(
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
 * SQL Worksheet 목록 응답 DTO (페이징 포함)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SqlWorksheetListResponse(
    val content: List<SqlWorksheetSummaryResponse>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)
