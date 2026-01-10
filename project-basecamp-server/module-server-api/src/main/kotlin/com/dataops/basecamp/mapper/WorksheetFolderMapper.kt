package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.entity.sql.WorksheetFolderEntity
import com.dataops.basecamp.dto.sql.WorksheetFolderListResponse
import com.dataops.basecamp.dto.sql.WorksheetFolderResponse
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * Worksheet Folder 매퍼
 *
 * API DTO와 Domain Entity 간의 변환을 담당합니다.
 * - Domain Entity -> Response DTO
 * - ISO 8601 날짜 형식 변환 처리
 */
@Component
object WorksheetFolderMapper {
    private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // === Domain Entity -> Response DTO 변환 ===

    /**
     * Worksheet Folder 엔티티를 상세 응답 DTO로 변환
     *
     * @param entity Worksheet Folder Entity
     * @param snippetCount 폴더 내 snippet 개수 (향후 추가, 기본값 0)
     */
    fun toResponse(
        entity: WorksheetFolderEntity,
        snippetCount: Int = 0,
    ): WorksheetFolderResponse =
        WorksheetFolderResponse(
            id = entity.id!!,
            teamId = entity.teamId,
            name = entity.name,
            description = entity.description,
            displayOrder = entity.displayOrder,
            snippetCount = snippetCount,
            createdAt = entity.createdAt?.format(ISO_FORMATTER) ?: "",
            updatedAt = entity.updatedAt?.format(ISO_FORMATTER) ?: "",
        )

    /**
     * Worksheet Folder 목록을 목록 응답 DTO로 변환
     */
    fun toListResponse(
        folders: List<WorksheetFolderEntity>,
        teamId: Long,
    ): WorksheetFolderListResponse =
        WorksheetFolderListResponse(
            content = folders.map { toResponse(it) },
            teamId = teamId,
        )
}
