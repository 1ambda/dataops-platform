package com.dataops.basecamp.mapper

import com.dataops.basecamp.domain.command.execution.RenderedDatasetExecutionParams
import com.dataops.basecamp.domain.command.execution.RenderedMetricExecutionParams
import com.dataops.basecamp.domain.command.execution.RenderedQualityExecutionParams
import com.dataops.basecamp.domain.command.execution.RenderedQualityTestItem
import com.dataops.basecamp.domain.command.execution.RenderedSqlExecutionParams
import com.dataops.basecamp.domain.projection.execution.QualityTestResultProjection
import com.dataops.basecamp.domain.projection.execution.RenderedExecutionResultProjection
import com.dataops.basecamp.domain.projection.execution.RenderedQualityExecutionResultProjection
import com.dataops.basecamp.dto.execution.DatasetExecutionRequest
import com.dataops.basecamp.dto.execution.ExecutionResultDto
import com.dataops.basecamp.dto.execution.MetricExecutionRequest
import com.dataops.basecamp.dto.execution.QualityExecutionRequest
import com.dataops.basecamp.dto.execution.QualityExecutionResultDto
import com.dataops.basecamp.dto.execution.QualityTestResultDto
import com.dataops.basecamp.dto.execution.SqlExecutionRequest

/**
 * Execution API Mapper
 *
 * Execution DTOs와 Domain 모델 간의 변환을 담당합니다.
 */
object ExecutionMapper {
    /**
     * DatasetExecutionRequest -> RenderedDatasetExecutionParams 변환
     */
    fun toParams(
        request: DatasetExecutionRequest,
        userId: Long,
    ): RenderedDatasetExecutionParams =
        RenderedDatasetExecutionParams(
            renderedSql = request.renderedSql,
            parameters = request.parameters,
            executionTimeout = request.executionTimeout,
            executionLimit = request.executionLimit,
            transpileSourceDialect = request.transpileSourceDialect,
            transpileTargetDialect = request.transpileTargetDialect,
            transpileUsedServerPolicy = request.transpileUsedServerPolicy,
            resourceName = request.resourceName,
            originalSpec = request.originalSpec,
            userId = userId,
        )

    /**
     * MetricExecutionRequest -> RenderedMetricExecutionParams 변환
     */
    fun toParams(
        request: MetricExecutionRequest,
        userId: Long,
    ): RenderedMetricExecutionParams =
        RenderedMetricExecutionParams(
            renderedSql = request.renderedSql,
            parameters = request.parameters,
            executionTimeout = request.executionTimeout,
            executionLimit = request.executionLimit,
            transpileSourceDialect = request.transpileSourceDialect,
            transpileTargetDialect = request.transpileTargetDialect,
            transpileUsedServerPolicy = request.transpileUsedServerPolicy,
            resourceName = request.resourceName,
            originalSpec = request.originalSpec,
            userId = userId,
        )

    /**
     * QualityExecutionRequest -> RenderedQualityExecutionParams 변환
     */
    fun toParams(
        request: QualityExecutionRequest,
        userId: Long,
    ): RenderedQualityExecutionParams =
        RenderedQualityExecutionParams(
            resourceName = request.resourceName,
            tests =
                request.tests.map { test ->
                    RenderedQualityTestItem(
                        name = test.name,
                        type = test.type,
                        renderedSql = test.renderedSql,
                    )
                },
            executionTimeout = request.executionTimeout,
            transpileSourceDialect = request.transpileSourceDialect,
            transpileTargetDialect = request.transpileTargetDialect,
            userId = userId,
        )

    /**
     * SqlExecutionRequest -> RenderedSqlExecutionParams 변환
     */
    fun toParams(
        request: SqlExecutionRequest,
        userId: Long,
    ): RenderedSqlExecutionParams =
        RenderedSqlExecutionParams(
            sql = request.sql,
            parameters = request.parameters,
            executionTimeout = request.executionTimeout,
            executionLimit = request.executionLimit,
            targetDialect = request.targetDialect,
            userId = userId,
        )

    /**
     * RenderedExecutionResultProjection -> ExecutionResultDto 변환
     */
    fun toDto(projection: RenderedExecutionResultProjection): ExecutionResultDto =
        ExecutionResultDto(
            executionId = projection.executionId,
            status = projection.status.name,
            rows = projection.rows,
            rowCount = projection.rowCount,
            durationSeconds = projection.durationSeconds,
            renderedSql = projection.renderedSql,
            error = projection.error,
        )

    /**
     * RenderedQualityExecutionResultProjection -> QualityExecutionResultDto 변환
     */
    fun toDto(projection: RenderedQualityExecutionResultProjection): QualityExecutionResultDto =
        QualityExecutionResultDto(
            executionId = projection.executionId,
            status = projection.status.name,
            results = projection.results.map { toDto(it) },
            totalTests = projection.totalTests,
            passedTests = projection.passedTests,
            failedTests = projection.failedTests,
            totalDurationMs = projection.totalDurationMs,
        )

    /**
     * QualityTestResultProjection -> QualityTestResultDto 변환
     */
    private fun toDto(projection: QualityTestResultProjection): QualityTestResultDto =
        QualityTestResultDto(
            testName = projection.testName,
            passed = projection.passed,
            failedCount = projection.failedCount,
            failedRows = projection.failedRows,
            durationMs = projection.durationMs,
        )
}
