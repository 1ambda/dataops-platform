package com.github.lambda.mapper

import com.github.lambda.domain.model.adhoc.AdHocExecutionEntity
import com.github.lambda.domain.service.AdHocExecutionResult
import com.github.lambda.domain.service.CurrentUsageDto
import com.github.lambda.domain.service.ExecutionPolicyDto
import com.github.lambda.domain.service.RateLimitsDto
import com.github.lambda.dto.run.CurrentUsageResponseDto
import com.github.lambda.dto.run.ExecutionDetailDto
import com.github.lambda.dto.run.ExecutionHistoryItemDto
import com.github.lambda.dto.run.ExecutionPolicyResponseDto
import com.github.lambda.dto.run.ExecutionResultResponseDto
import com.github.lambda.dto.run.RateLimitsResponseDto
import java.time.format.DateTimeFormatter

/**
 * Run API Mapper
 *
 * Domain 모델과 API DTO 간의 변환을 담당합니다.
 */
object RunMapper {
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    /**
     * ExecutionPolicyDto -> ExecutionPolicyResponseDto 변환
     */
    fun toResponseDto(policy: ExecutionPolicyDto): ExecutionPolicyResponseDto =
        ExecutionPolicyResponseDto(
            maxQueryDurationSeconds = policy.maxQueryDurationSeconds,
            maxResultRows = policy.maxResultRows,
            maxResultSizeMb = policy.maxResultSizeMb,
            allowedEngines = policy.allowedEngines,
            allowedFileTypes = policy.allowedFileTypes,
            maxFileSizeMb = policy.maxFileSizeMb,
            rateLimits = toResponseDto(policy.rateLimits),
            currentUsage = toResponseDto(policy.currentUsage),
        )

    /**
     * RateLimitsDto -> RateLimitsResponseDto 변환
     */
    fun toResponseDto(rateLimits: RateLimitsDto): RateLimitsResponseDto =
        RateLimitsResponseDto(
            queriesPerHour = rateLimits.queriesPerHour,
            queriesPerDay = rateLimits.queriesPerDay,
        )

    /**
     * CurrentUsageDto -> CurrentUsageResponseDto 변환
     */
    fun toResponseDto(currentUsage: CurrentUsageDto): CurrentUsageResponseDto =
        CurrentUsageResponseDto(
            queriesToday = currentUsage.queriesToday,
            queriesThisHour = currentUsage.queriesThisHour,
        )

    /**
     * AdHocExecutionResult -> ExecutionResultResponseDto 변환
     */
    fun toResponseDto(
        result: AdHocExecutionResult,
        downloadUrls: Map<String, String>,
    ): ExecutionResultResponseDto =
        ExecutionResultResponseDto(
            queryId = result.queryId,
            status = result.status.name,
            executionTimeSeconds = result.executionTimeSeconds,
            rowsReturned = result.rowsReturned,
            bytesScanned = result.bytesScanned?.let { formatBytes(it) },
            costUsd = result.costUsd,
            downloadUrls = downloadUrls,
            expiresAt = result.expiresAt?.format(isoFormatter),
            renderedSql = result.renderedSql,
        )

    /**
     * AdHocExecutionEntity -> ExecutionHistoryItemDto 변환
     */
    fun toHistoryItemDto(entity: AdHocExecutionEntity): ExecutionHistoryItemDto =
        ExecutionHistoryItemDto(
            queryId = entity.queryId,
            status = entity.status.name,
            engine = entity.engine,
            executionTimeSeconds = entity.executionTimeSeconds,
            rowsReturned = entity.rowsReturned,
            bytesScanned = entity.bytesScanned?.let { formatBytes(it) },
            costUsd = entity.costUsd,
            createdAt = entity.createdAt?.format(isoFormatter) ?: "",
            expiresAt = entity.expiresAt?.format(isoFormatter),
            canDownload = entity.canDownload(),
        )

    /**
     * AdHocExecutionEntity -> ExecutionDetailDto 변환
     */
    fun toDetailDto(
        entity: AdHocExecutionEntity,
        downloadUrls: Map<String, String>,
    ): ExecutionDetailDto =
        ExecutionDetailDto(
            queryId = entity.queryId,
            status = entity.status.name,
            engine = entity.engine,
            sqlQuery = entity.sqlQuery,
            renderedSql = entity.renderedSql,
            executionTimeSeconds = entity.executionTimeSeconds,
            rowsReturned = entity.rowsReturned,
            bytesScanned = entity.bytesScanned?.let { formatBytes(it) },
            costUsd = entity.costUsd,
            errorMessage = entity.errorMessage,
            downloadUrls = downloadUrls,
            createdAt = entity.createdAt?.format(isoFormatter) ?: "",
            expiresAt = entity.expiresAt?.format(isoFormatter),
            canDownload = entity.canDownload(),
        )

    /**
     * 바이트 수를 읽기 쉬운 형식으로 변환
     */
    private fun formatBytes(bytes: Long): String =
        when {
            bytes >= 1_073_741_824 -> String.format("%.2f GB", bytes / 1_073_741_824.0)
            bytes >= 1_048_576 -> String.format("%.2f MB", bytes / 1_048_576.0)
            bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
}
