package com.github.lambda.mapper

import com.github.lambda.domain.model.dataset.DatasetEntity
import com.github.lambda.dto.dataset.*
import org.springframework.stereotype.Component
import java.time.format.DateTimeFormatter

/**
 * Dataset 매퍼
 *
 * API DTO와 Domain Entity 간의 변환을 담당합니다.
 * - Request DTO → Domain Entity
 * - Domain Entity → Response DTO
 * - ISO 8601 날짜 형식 변환 처리
 */
@Component
object DatasetMapper {

    private val ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

    // === Request DTO → Domain Entity 변환 ===

    /**
     * Dataset 생성 요청을 도메인 Entity로 변환
     */
    fun toEntity(request: CreateDatasetRequest): DatasetEntity =
        DatasetEntity(
            name = request.name,
            owner = request.owner,
            team = request.team,
            description = request.description,
            sql = request.sql,
            tags = request.tags.toSet(),
            dependencies = request.dependencies.toSet(),
            scheduleCron = request.schedule?.cron,
            scheduleTimezone = request.schedule?.timezone ?: "UTC",
        )

    // === Domain Entity → Response DTO 변환 ===

    /**
     * Dataset 엔티티를 상세 응답 DTO로 변환 (GET /datasets/{name})
     */
    fun toDto(entity: DatasetEntity): DatasetDto = DatasetDto(
        name = entity.name,
        type = "Dataset",
        owner = entity.owner,
        team = entity.team,
        description = entity.description,
        tags = entity.tags.sorted(),  // 정렬된 태그 리스트
        sql = entity.sql,
        dependencies = entity.dependencies.sorted(), // 정렬된 의존성 리스트
        schedule = entity.scheduleCron?.let {
            ScheduleDto(cron = it, timezone = entity.scheduleTimezone ?: "UTC")
        },
        createdAt = entity.createdAt.format(ISO_FORMATTER),
        updatedAt = entity.updatedAt.format(ISO_FORMATTER),
    )

    /**
     * Dataset 엔티티를 목록 응답 DTO로 변환 (GET /datasets) - Simplified for list view
     */
    fun toListDto(entity: DatasetEntity): DatasetListDto = DatasetListDto(
        name = entity.name,
        type = "Dataset",
        owner = entity.owner,
        team = entity.team,
        description = entity.description,
        tags = entity.tags.sorted(),  // 정렬된 태그 리스트
        createdAt = entity.createdAt.format(ISO_FORMATTER),
        updatedAt = entity.updatedAt.format(ISO_FORMATTER),
    )

    /**
     * Dataset 등록 성공 응답 DTO 생성
     */
    fun toRegistrationResponse(datasetName: String): DatasetRegistrationResponse =
        DatasetRegistrationResponse(
            message = "Dataset '$datasetName' registered successfully",
            name = datasetName,
        )

    /**
     * Dataset 실행 결과 DTO 생성
     */
    fun toExecutionResult(
        rows: List<Map<String, Any>>,
        durationSeconds: Double,
        renderedSql: String,
    ): ExecutionResultDto = ExecutionResultDto(
        rows = rows,
        rowCount = rows.size,
        durationSeconds = durationSeconds,
        renderedSql = renderedSql,
    )

    // === Error Response 변환 ===

    /**
     * 에러 응답 DTO 생성
     */
    fun toErrorResponse(
        code: String,
        message: String,
        details: Map<String, Any> = emptyMap(),
    ): ErrorResponse = ErrorResponse(code, message, details)

    /**
     * Dataset Not Found 에러 응답
     */
    fun toDatasetNotFoundError(datasetName: String): ErrorResponse =
        toErrorResponse(
            code = "DATASET_NOT_FOUND",
            message = "Dataset '$datasetName' not found",
            details = mapOf("dataset_name" to datasetName)
        )

    /**
     * Dataset Already Exists 에러 응답
     */
    fun toDatasetAlreadyExistsError(datasetName: String): ErrorResponse =
        toErrorResponse(
            code = "DATASET_ALREADY_EXISTS",
            message = "Dataset '$datasetName' already exists",
            details = mapOf("dataset_name" to datasetName)
        )

    /**
     * Dataset Execution Timeout 에러 응답
     */
    fun toDatasetExecutionTimeoutError(datasetName: String, timeoutSeconds: Int): ErrorResponse =
        toErrorResponse(
            code = "DATASET_EXECUTION_TIMEOUT",
            message = "Dataset execution timed out after $timeoutSeconds seconds",
            details = mapOf(
                "dataset_name" to datasetName,
                "timeout_seconds" to timeoutSeconds,
            )
        )

    /**
     * Dataset Execution Failed 에러 응답
     */
    fun toDatasetExecutionFailedError(datasetName: String, sqlError: String): ErrorResponse =
        toErrorResponse(
            code = "DATASET_EXECUTION_FAILED",
            message = "Query execution failed",
            details = mapOf(
                "dataset_name" to datasetName,
                "sql_error" to sqlError,
            )
        )

    /**
     * Invalid Parameter 에러 응답
     */
    fun toInvalidParameterError(parameterName: String, value: Any, reason: String): ErrorResponse =
        toErrorResponse(
            code = "INVALID_PARAMETER",
            message = "Invalid $parameterName value: $reason",
            details = mapOf(
                "parameter" to parameterName,
                "value" to value,
            )
        )

    /**
     * Invalid Dataset Name 에러 응답
     */
    fun toInvalidDatasetNameError(datasetName: String): ErrorResponse =
        toErrorResponse(
            code = "INVALID_DATASET_NAME",
            message = "Dataset name must follow pattern: catalog.schema.name",
            details = mapOf(
                "dataset_name" to datasetName,
                "expected_pattern" to "[catalog].[schema].[name]",
            )
        )
}