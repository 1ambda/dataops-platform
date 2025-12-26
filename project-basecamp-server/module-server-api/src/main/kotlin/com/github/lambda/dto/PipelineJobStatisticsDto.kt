package com.github.lambda.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.security.SecureField
import com.github.lambda.security.SecurityLevel
import com.github.lambda.security.SensitiveData
import com.github.lambda.security.SensitiveDataCategory

/**
 * 파이프라인 Job 통계 응답 DTO
 *
 * API 레이어에서 클라이언트에게 반환하는 파이프라인 통계 데이터 구조입니다.
 * QueryDSL Projection 결과를 API 응답 형태로 변환하여 제공합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PipelineJobStatisticsDto(
    val pipelineId: Long?,
    val pipelineName: String,
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
    val owner: String,
    val status: PipelineStatus,
    val totalJobs: Long,
    val successJobs: Long,
    val failedJobs: Long,
    val runningJobs: Long,
) {
    /**
     * Job 성공률 계산 (백분율)
     */
    val successRate: Double
        get() =
            if (totalJobs > 0) {
                (successJobs.toDouble() / totalJobs.toDouble()) * 100.0
            } else {
                0.0
            }

    /**
     * Job 실패율 계산 (백분율)
     */
    val failureRate: Double
        get() =
            if (totalJobs > 0) {
                (failedJobs.toDouble() / totalJobs.toDouble()) * 100.0
            } else {
                0.0
            }

    /**
     * 완료된 Job 수 (성공 + 실패)
     */
    val completedJobs: Long
        get() = successJobs + failedJobs

    /**
     * 미완료 Job 수
     */
    val pendingJobs: Long
        get() = totalJobs - completedJobs - runningJobs
}
