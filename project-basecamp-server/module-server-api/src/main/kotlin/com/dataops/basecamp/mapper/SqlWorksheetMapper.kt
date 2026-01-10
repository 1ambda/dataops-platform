package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.entity.sql.SqlWorksheetEntity
import com.dataops.basecamp.dto.sql.SqlWorksheetDetailResponse
import com.dataops.basecamp.dto.sql.SqlWorksheetListResponse
import com.dataops.basecamp.dto.sql.SqlWorksheetSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * SQL Worksheet 매퍼
 *
 * API DTO와 Domain Entity 간의 변환을 담당합니다.
 * - Domain Entity -> Response DTO
 * - ISO 8601 날짜 형식 변환 처리
 */
@Component
object SqlWorksheetMapper {
    private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // === Domain Entity -> Response DTO 변환 ===

    /**
     * SQL Worksheet 엔티티를 요약 응답 DTO로 변환 (목록용)
     *
     * @param entity SQL Worksheet Entity
     * @param folderName 폴더 이름
     */
    fun toSummaryResponse(
        entity: SqlWorksheetEntity,
        folderName: String,
    ): SqlWorksheetSummaryResponse =
        SqlWorksheetSummaryResponse(
            id = entity.id!!,
            folderId = entity.folderId,
            folderName = folderName,
            name = entity.name,
            description = entity.description,
            dialect = entity.dialect,
            isStarred = entity.isStarred,
            runCount = entity.runCount,
            lastRunAt = entity.lastRunAt?.format(ISO_FORMATTER),
            createdAt = entity.createdAt?.format(ISO_FORMATTER) ?: "",
            updatedAt = entity.updatedAt?.format(ISO_FORMATTER) ?: "",
        )

    /**
     * SQL Worksheet 엔티티를 상세 응답 DTO로 변환 (단건 조회용)
     *
     * @param entity SQL Worksheet Entity
     * @param folderName 폴더 이름
     */
    fun toDetailResponse(
        entity: SqlWorksheetEntity,
        folderName: String,
    ): SqlWorksheetDetailResponse =
        SqlWorksheetDetailResponse(
            id = entity.id!!,
            folderId = entity.folderId,
            folderName = folderName,
            name = entity.name,
            description = entity.description,
            sqlText = entity.sqlText,
            dialect = entity.dialect,
            isStarred = entity.isStarred,
            runCount = entity.runCount,
            lastRunAt = entity.lastRunAt?.format(ISO_FORMATTER),
            createdAt = entity.createdAt?.format(ISO_FORMATTER) ?: "",
            updatedAt = entity.updatedAt?.format(ISO_FORMATTER) ?: "",
        )

    /**
     * SQL Worksheet 페이지를 목록 응답 DTO로 변환
     *
     * @param page 페이징된 SQL Worksheet 엔티티
     * @param folderNameMap FolderId -> FolderName 매핑
     */
    fun toListResponse(
        page: Page<SqlWorksheetEntity>,
        folderNameMap: Map<Long, String>,
    ): SqlWorksheetListResponse =
        SqlWorksheetListResponse(
            content =
                page.content.map { entity ->
                    toSummaryResponse(entity, folderNameMap[entity.folderId] ?: "Unknown")
                },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
}
