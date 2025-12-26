package com.github.lambda.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.data.domain.Page
import java.time.LocalDateTime

/**
 * 공통 API 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: ErrorDetails? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val timestamp: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        fun <T> success(
            data: T? = null,
            message: String? = null,
        ) = ApiResponse(success = true, data = data, message = message)

        fun <T> error(
            message: String,
            error: ErrorDetails? = null,
        ) = ApiResponse<T>(success = false, message = message, error = error)
    }
}

/**
 * 에러 상세 정보
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorDetails(
    val code: String,
    val field: String? = null,
    val rejectedValue: Any? = null,
    val details: Map<String, Any>? = null,
)

/**
 * 페이징된 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean,
) {
    companion object {
        fun <T : Any> from(page: Page<T>): PagedResponse<T> =
            PagedResponse(
                content = page.content,
                page = page.number,
                size = page.size,
                totalElements = page.totalElements,
                totalPages = page.totalPages,
                first = page.isFirst,
                last = page.isLast,
            )
    }
}

/**
 * 생성/수정 요청 기본 DTO
 */
interface BaseRequestDto {
    val name: String
    val description: String?
}

/**
 * 식별자를 포함한 응답 DTO
 */
interface BaseResponseDto {
    val id: Long?
    val name: String
    val description: String?
    val createdAt: LocalDateTime?
    val updatedAt: LocalDateTime?
}
