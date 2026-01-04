package com.github.lambda.mapper

import com.github.lambda.domain.entity.workflow.WorkflowEntity
import com.github.lambda.domain.entity.workflow.WorkflowRunEntity
import com.github.lambda.dto.workflow.BackfillResponseDto
import com.github.lambda.dto.workflow.RegisterWorkflowRequest
import com.github.lambda.dto.workflow.WorkflowDetailDto
import com.github.lambda.dto.workflow.WorkflowRunDetailDto
import com.github.lambda.dto.workflow.WorkflowRunSummaryDto
import com.github.lambda.dto.workflow.WorkflowSummaryDto
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * Workflow Mapper
 *
 * Handles conversions between API DTOs and Domain entities.
 * - Domain Entity -> Response DTO
 * - Request DTO -> Service parameters
 */
@Component
class WorkflowMapper {
    /**
     * Convert WorkflowEntity to WorkflowSummaryDto (list view)
     *
     * Used for GET /api/v1/workflows
     */
    fun toSummaryDto(entity: WorkflowEntity): WorkflowSummaryDto =
        WorkflowSummaryDto(
            datasetName = entity.datasetName,
            sourceType = entity.sourceType.name,
            status = entity.status.name,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            airflowDagId = entity.airflowDagId,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert WorkflowEntity to WorkflowDetailDto (full details)
     *
     * Used for register/pause/unpause workflow responses
     */
    fun toDetailDto(
        entity: WorkflowEntity,
        recentRuns: List<WorkflowRunEntity> = emptyList(),
    ): WorkflowDetailDto =
        WorkflowDetailDto(
            datasetName = entity.datasetName,
            sourceType = entity.sourceType.name,
            status = entity.status.name,
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            s3Path = entity.s3Path,
            airflowDagId = entity.airflowDagId,
            schedule = entity.schedule.cron,
            recentRuns = recentRuns.take(5).map { toRunSummaryDto(it) },
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )

    /**
     * Convert WorkflowRunEntity to WorkflowRunSummaryDto
     *
     * Used for workflow history responses
     */
    fun toRunSummaryDto(entity: WorkflowRunEntity): WorkflowRunSummaryDto =
        WorkflowRunSummaryDto(
            runId = entity.runId,
            datasetName = entity.datasetName,
            status = entity.status.name,
            triggerMode = entity.runType.name,
            executionDate = entity.createdAt, // Using createdAt as execution date
            startedAt = entity.startedAt,
            completedAt = entity.endedAt,
            durationSeconds = entity.getDurationSeconds()?.toLong(),
            triggeredBy = entity.triggeredBy,
        )

    /**
     * Convert WorkflowRunEntity to WorkflowRunDetailDto
     *
     * Used for GET /api/v1/workflows/runs/{run_id} and run trigger responses
     */
    fun toRunDetailDto(entity: WorkflowRunEntity): WorkflowRunDetailDto =
        WorkflowRunDetailDto(
            runId = entity.runId,
            datasetName = entity.datasetName,
            status = entity.status.name,
            triggerMode = entity.runType.name,
            executionDate = entity.createdAt, // Using createdAt as execution date
            parameters = parseParams(entity.params),
            startedAt = entity.startedAt,
            completedAt = entity.endedAt,
            durationSeconds = entity.getDurationSeconds()?.toLong(),
            triggeredBy = entity.triggeredBy,
            airflowDagRunId = entity.runId, // Using runId as DAG run ID for now
            airflowLogUrl = entity.logsUrl,
            errorMessage = entity.stopReason?.takeIf { entity.isFailed() },
        )

    /**
     * Convert RegisterWorkflowRequest to WorkflowEntity
     *
     * Note: This creates a new entity without ID, timestamps, etc.
     * These should be set by the service layer
     */
    fun toEntity(request: RegisterWorkflowRequest): WorkflowEntity {
        // This is a simplified conversion - the service layer should handle
        // proper entity creation with all required fields
        throw NotImplementedError("Entity creation should be handled by service layer")
    }

    /**
     * Create BackfillResponseDto
     *
     * Used for POST /api/v1/workflows/{dataset_name}/backfill response
     */
    fun toBackfillResponseDto(
        backfillId: String,
        datasetName: String,
        startDate: String,
        endDate: String,
        runIds: List<String>,
        status: String = "PENDING",
        createdAt: LocalDateTime = LocalDateTime.now(),
    ): BackfillResponseDto =
        BackfillResponseDto(
            backfillId = backfillId,
            datasetName = datasetName,
            startDate = startDate,
            endDate = endDate,
            totalRuns = runIds.size,
            runIds = runIds,
            status = status,
            createdAt = createdAt,
        )

    /**
     * Parse JSON parameters string to Map
     */
    private fun parseParams(params: String?): Map<String, Any>? {
        if (params.isNullOrBlank()) {
            return null
        }

        return try {
            // Simple parsing - in real implementation, use JSON library
            // For now, return empty map to avoid parsing errors
            emptyMap()
        } catch (e: Exception) {
            // Log error and return empty map
            emptyMap()
        }
    }
}
