package com.github.lambda.mapper

import com.github.lambda.common.enums.WorkflowRunStatus
import com.github.lambda.domain.entity.quality.QualityRunEntity
import com.github.lambda.domain.entity.quality.QualitySpecEntity
import com.github.lambda.domain.entity.quality.QualityTestEntity
import com.github.lambda.dto.quality.QualityRunResultDto
import com.github.lambda.dto.quality.QualityRunSummaryDto
import com.github.lambda.dto.quality.QualitySpecDetailDto
import com.github.lambda.dto.quality.QualitySpecSummaryDto
import com.github.lambda.dto.quality.QualityTestDto
import com.github.lambda.dto.quality.TestResultSummaryDto
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.ZoneOffset

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
     * Note: testCount must be provided separately since tests relationship was removed
     */
    fun toSummaryDto(
        entity: QualitySpecEntity,
        testCount: Int = 0,
    ): QualitySpecSummaryDto =
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
            testCount = testCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert QualitySpecEntity to QualitySpecDetailDto (full details)
     *
     * Used for GET /api/v1/quality/{name}
     * Note: tests must be provided separately since tests relationship was removed
     */
    fun toDetailDto(
        entity: QualitySpecEntity,
        tests: List<QualityTestEntity> = emptyList(),
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
            tests = tests.map { toTestDto(it) },
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
     *
     * v2.0: Maps new entity fields to existing DTO structure:
     * - targetResource -> resourceName
     * - endedAt -> completedAt
     * - triggeredBy -> executedBy
     * - getDurationSeconds() -> durationSeconds
     * - overallStatus derived from passedTests/failedTests
     */
    fun toRunSummaryDto(entity: QualityRunEntity): QualityRunSummaryDto =
        QualityRunSummaryDto(
            runId = entity.runId,
            resourceName = entity.targetResource,
            status = entity.status.name,
            overallStatus = deriveOverallStatus(entity),
            passedTests = entity.passedTests,
            failedTests = entity.failedTests,
            durationSeconds = entity.getDurationSeconds(),
            startedAt = entity.startedAt?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
            completedAt = entity.endedAt?.toInstant(ZoneOffset.UTC),
            executedBy = entity.triggeredBy,
        )

    /**
     * Derive overall status from entity state
     * Returns PASSED if all tests passed, FAILED if any failed, null if still running
     */
    private fun deriveOverallStatus(entity: QualityRunEntity): String? =
        when {
            entity.status == WorkflowRunStatus.RUNNING ||
                entity.status == WorkflowRunStatus.PENDING -> null
            entity.failedTests > 0 -> "FAILED"
            entity.passedTests > 0 -> "PASSED"
            else -> null
        }

    /**
     * Convert QualityRunEntity to QualityRunResultDto (for test execution)
     *
     * v2.0: Entity now has specName field, testResults provided separately
     */
    fun toRunResultDto(
        entity: QualityRunEntity,
        testResults: List<TestResultSummaryDto> = emptyList(),
    ): QualityRunResultDto =
        QualityRunResultDto(
            runId = entity.runId,
            resourceName = entity.targetResource,
            qualitySpecName = entity.specName,
            status = entity.status.name,
            overallStatus = deriveOverallStatus(entity),
            passedTests = entity.passedTests,
            failedTests = entity.failedTests,
            totalTests = entity.totalTests,
            durationSeconds = entity.getDurationSeconds(),
            startedAt = entity.startedAt?.toInstant(ZoneOffset.UTC) ?: Instant.now(),
            completedAt = entity.endedAt?.toInstant(ZoneOffset.UTC),
            executedBy = entity.triggeredBy,
            testResults = testResults,
        )

    /**
     * Convert JsonNode to Map for config field
     */
    private fun convertJsonNodeToMap(jsonNode: com.fasterxml.jackson.databind.JsonNode): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        jsonNode.fields().forEach { (key, value) ->
            result[key] =
                when {
                    value.isTextual -> value.asText()
                    value.isNumber -> value.asLong()
                    value.isBoolean -> value.asBoolean()
                    value.isArray -> {
                        val list = mutableListOf<Any>()
                        value.forEach { item ->
                            list.add(
                                when {
                                    item.isTextual -> item.asText()
                                    item.isNumber -> item.asLong()
                                    item.isBoolean -> item.asBoolean()
                                    else -> item.toString()
                                },
                            )
                        }
                        list
                    }
                    else -> value.toString()
                }
        }
        return result
    }
}
