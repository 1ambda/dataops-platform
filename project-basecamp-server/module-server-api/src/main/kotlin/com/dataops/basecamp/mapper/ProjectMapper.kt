package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.entity.project.ProjectEntity
import com.dataops.basecamp.dto.project.CreateProjectRequest
import com.dataops.basecamp.dto.project.ProjectListResponse
import com.dataops.basecamp.dto.project.ProjectResponse
import org.springframework.data.domain.Page
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * Project 매퍼
 *
 * API DTO와 Domain Entity 간의 변환을 담당합니다.
 * - Request DTO -> Domain Entity
 * - Domain Entity -> Response DTO
 * - ISO 8601 날짜 형식 변환 처리
 */
@Component
object ProjectMapper {
    private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // === Request DTO -> Domain Entity 변환 ===

    /**
     * Project 생성 요청을 도메인 Entity로 변환
     */
    fun toEntity(request: CreateProjectRequest): ProjectEntity =
        ProjectEntity(
            name = request.name,
            displayName = request.displayName,
            description = request.description,
        )

    // === Domain Entity -> Response DTO 변환 ===

    /**
     * Project 엔티티를 상세 응답 DTO로 변환
     */
    fun toResponse(entity: ProjectEntity): ProjectResponse =
        ProjectResponse(
            id = entity.id!!,
            name = entity.name,
            displayName = entity.displayName,
            description = entity.description,
            createdAt = entity.createdAt?.format(ISO_FORMATTER) ?: "",
            updatedAt = entity.updatedAt?.format(ISO_FORMATTER) ?: "",
        )

    /**
     * Project 페이지를 목록 응답 DTO로 변환
     */
    fun toListResponse(page: Page<ProjectEntity>): ProjectListResponse =
        ProjectListResponse(
            content = page.content.map { toResponse(it) },
            page = page.number,
            size = page.size,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
        )
}
