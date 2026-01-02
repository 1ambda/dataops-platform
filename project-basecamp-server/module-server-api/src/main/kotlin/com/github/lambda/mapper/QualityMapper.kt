package com.github.lambda.mapper

import com.github.lambda.domain.model.quality.QualityRunEntity
import com.github.lambda.domain.model.quality.QualitySpecEntity
import com.github.lambda.domain.model.quality.QualityTestEntity
import com.github.lambda.dto.quality.QualityRunResultDto
import com.github.lambda.dto.quality.QualityRunSummaryDto
import com.github.lambda.dto.quality.QualitySpecDetailDto
import com.github.lambda.dto.quality.QualitySpecSummaryDto
import com.github.lambda.dto.quality.QualityTestDto
import com.github.lambda.dto.quality.TestResultSummaryDto
import org.springframework.stereotype.Component

/**
 * Quality Mapper
 *
 * Handles conversions between API DTOs and Domain entities.
 * - Domain Entity -> Response DTO
 * - Request DTO -> Service parameters
 */
@Component
class QualityMapper {

    /**
     * Convert QualitySpecEntity to QualitySpecSummaryDto (list view)
     *
     * Used for GET /api/v1/quality
     */
    fun toSummaryDto(entity: QualitySpecEntity): QualitySpecSummaryDto =
        QualitySpecSummaryDto(
            name = entity.name,
            resourceName = entity.resourceName,
            resourceType = entity.resourceType.name,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
            scheduleCron = entity.scheduleCron,
            scheduleTimezone = entity.scheduleTimezone,
            enabled = entity.enabled,
            testCount = entity.tests.size,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert QualitySpecEntity to QualitySpecDetailDto (full details)
     *
     * Used for GET /api/v1/quality/{name}
     */
    fun toDetailDto(
        entity: QualitySpecEntity,
        recentRuns: List<QualityRunEntity> = emptyList(),
    ): QualitySpecDetailDto =
        QualitySpecDetailDto(
            name = entity.name,
            resourceName = entity.resourceName,
            resourceType = entity.resourceType.name,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
            scheduleCron = entity.scheduleCron,
            scheduleTimezone = entity.scheduleTimezone,
            enabled = entity.enabled,
            tests = entity.tests.map { toTestDto(it) },
            recentRuns = recentRuns.take(5).map { toRunSummaryDto(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert QualityTestEntity to QualityTestDto
     */
    fun toTestDto(entity: QualityTestEntity): QualityTestDto =
        QualityTestDto(
            name = entity.name,
            testType = entity.testType.name,
            description = entity.description,
            targetColumns = entity.getAllColumns(),
            config = entity.config?.let { convertJsonNodeToMap(it) },
            enabled = entity.enabled,
            createdAt = entity.createdAt,
        )

    /**
     * Convert QualityRunEntity to QualityRunSummaryDto
     */
    fun toRunSummaryDto(entity: QualityRunEntity): QualityRunSummaryDto =
        QualityRunSummaryDto(
            runId = entity.runId,
            resourceName = entity.resourceName,
            status = entity.status.name,
            overallStatus = entity.overallStatus?.name,
            passedTests = entity.passedTests,
            failedTests = entity.failedTests,
            durationSeconds = entity.durationSeconds,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            executedBy = entity.executedBy,
        )

    /**
     * Convert QualityRunEntity to QualityRunResultDto (for test execution)
     */
    fun toRunResultDto(entity: QualityRunEntity): QualityRunResultDto =
        QualityRunResultDto(
            runId = entity.runId,
            resourceName = entity.resourceName,
            qualitySpecName = entity.spec?.name ?: "",
            status = entity.status.name,
            overallStatus = entity.overallStatus?.name,
            passedTests = entity.passedTests,
            failedTests = entity.failedTests,
            totalTests = entity.passedTests + entity.failedTests,
            durationSeconds = entity.durationSeconds,
            startedAt = entity.startedAt,
            completedAt = entity.completedAt,
            executedBy = entity.executedBy,
            testResults = entity.results.map { toTestResultSummaryDto(it) },
        )

    /**
     * Convert TestResultEntity to TestResultSummaryDto
     *
     * Note: This is a mock implementation since TestResultEntity doesn't exist yet
     * In real implementation, would use proper TestResultEntity
     */
    private fun toTestResultSummaryDto(entity: Any): TestResultSummaryDto =
        TestResultSummaryDto(
            testName = "mock_test",
            testType = "NOT_NULL",
            status = "PASSED",
            failedRows = 0L,
            totalRows = 1000L,
            executionTimeSeconds = 2.5,
            errorMessage = null,
        )

    /**
     * Convert JsonNode to Map for config field
     */
    private fun convertJsonNodeToMap(jsonNode: com.fasterxml.jackson.databind.JsonNode): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        jsonNode.fields().forEach { (key, value) ->
            result[key] = when {
                value.isTextual -> value.asText()
                value.isNumber -> value.asLong()
                value.isBoolean -> value.asBoolean()
                value.isArray -> {
                    val list = mutableListOf<Any>()
                    value.forEach { item ->
                        list.add(when {
                            item.isTextual -> item.asText()
                            item.isNumber -> item.asLong()
                            item.isBoolean -> item.asBoolean()
                            else -> item.toString()
                        })
                    }
                    list
                }
                else -> value.toString()
            }
        }
        return result
    }
}
