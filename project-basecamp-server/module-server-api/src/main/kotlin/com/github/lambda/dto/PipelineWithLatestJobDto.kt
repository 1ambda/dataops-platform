package com.github.lambda.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.github.lambda.domain.model.pipeline.JobStatus
import com.github.lambda.domain.model.pipeline.PipelineStatus
import com.github.lambda.security.SecureField
import com.github.lambda.security.SecurityLevel
import com.github.lambda.security.SensitiveData
import com.github.lambda.security.SensitiveDataCategory
import java.time.LocalDateTime

/**
 * 파이프라인과 최신 Job 정보 응답 DTO
 *
 * API 레이어에서 클라이언트에게 반환하는 파이프라인과 최신 Job 정보 구조입니다.
 * QueryDSL Projection 결과를 API 응답 형태로 변환하여 제공합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PipelineWithLatestJobDto(
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
    val pipelineStatus: PipelineStatus,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val pipelineUpdatedAt: LocalDateTime?,
    val latestJobId: Long?,
    val latestJobName: String?,
    val latestJobStatus: JobStatus?,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val latestJobStartedAt: LocalDateTime?,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    val latestJobFinishedAt: LocalDateTime?,
) {
    /**
     * 최신 Job이 존재하는지 확인
     */
    val hasLatestJob: Boolean
        get() = latestJobId != null

    /**
     * 최신 Job이 실행 중인지 확인
     */
    val isLatestJobRunning: Boolean
        get() = latestJobStatus == JobStatus.RUNNING

    /**
     * 최신 Job 실행 시간 (초 단위)
     */
    val latestJobDurationSeconds: Long?
        get() =
            if (latestJobStartedAt != null && latestJobFinishedAt != null) {
                java.time.Duration
                    .between(latestJobStartedAt, latestJobFinishedAt)
                    .seconds
            } else {
                null
            }

    /**
     * 최신 Job의 상태 표시용 문자열
     */
    val latestJobStatusDisplay: String
        get() =
            when {
                !hasLatestJob -> "No Jobs"
                isLatestJobRunning -> "Running"
                latestJobStatus == JobStatus.SUCCESS -> "Success"
                latestJobStatus == JobStatus.FAILED -> "Failed"
                else -> latestJobStatus?.name ?: "Unknown"
            }
}
