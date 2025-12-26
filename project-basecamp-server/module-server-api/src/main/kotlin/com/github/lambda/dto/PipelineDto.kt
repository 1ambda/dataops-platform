package com.github.lambda.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.security.SecureField
import com.github.lambda.security.SecurityLevel
import com.github.lambda.security.SensitiveData
import com.github.lambda.security.SensitiveDataCategory
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

/**
 * 파이프라인 생성 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatePipelineRequest(
    @field:NotBlank(message = "Pipeline name is required")
    @field:Size(max = 100, message = "Pipeline name must not exceed 100 characters")
    override val name: String,
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    override val description: String? = null,
    @field:NotBlank(message = "Owner is required")
    val owner: String,
    val scheduleExpression: String? = null,
    val isActive: Boolean = true,
) : BaseRequestDto

/**
 * 파이프라인 수정 요청 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class UpdatePipelineRequest(
    @field:Size(max = 100, message = "Pipeline name must not exceed 100 characters")
    override val name: String,
    @field:Size(max = 500, message = "Description must not exceed 500 characters")
    override val description: String? = null,
    val scheduleExpression: String? = null,
) : BaseRequestDto

/**
 * 파이프라인 응답 DTO
 *
 * API 레이어에서 클라이언트에게 반환하는 데이터 구조입니다.
 * 도메인 엔터티와 직접적인 의존성을 제거하여 API 스펙 변경에 유연하게 대응합니다.
 *
 * 보안 어노테이션을 통해 필드별 접근 제어를 설정합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PipelineResponse(
    override val id: Long?,
    override val name: String,
    override val description: String?,
    val status: PipelineStatus,
    @SecureField(
        minSecurityLevel = SecurityLevel.INTERNAL,
        masked = true,
        ownerOnly = true,
        auditLog = true,
        description = "파이프라인 소유자 정보",
    )
    @SensitiveData(
        category = SensitiveDataCategory.PERSONAL_INFO,
        reason = "개인 식별 가능 정보",
    )
    val owner: String?,
    @SecureField(
        minSecurityLevel = SecurityLevel.INTERNAL,
        auditLog = true,
        description = "파이프라인 스케줄 설정",
    )
    @SensitiveData(
        category = SensitiveDataCategory.SYSTEM_CONFIG,
        reason = "시스템 운영 정보",
    )
    val scheduleExpression: String?,
    val isActive: Boolean,
    val jobCount: Int = 0,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    override val createdAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    override val updatedAt: LocalDateTime?,
) : BaseResponseDto

/**
 * 파이프라인 상태 변경 요청 DTO
 */
data class UpdatePipelineStatusRequest(
    val status: PipelineStatus,
)

/**
 * 파이프라인 실행 응답 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PipelineExecutionResponse(
    val executionId: String,
    val pipelineId: Long,
    val pipelineName: String,
    val status: String,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val startedAt: LocalDateTime,
    val message: String? = null,
)
