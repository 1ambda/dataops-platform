package com.dataops.basecamp.dto.workflow

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

// ========================
// Request DTOs
// ========================

/**
 * Request to register a new workflow
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class RegisterWorkflowRequest(
    @field:NotBlank
    @JsonProperty("dataset_name")
    val datasetName: String,
    @field:NotBlank
    @JsonProperty("source_type")
    val sourceType: String,
    @field:Email
    val owner: String,
    val team: String?,
    val description: String?,
    @JsonProperty("schedule_cron")
    val scheduleCron: String?,
    @JsonProperty("schedule_timezone")
    val scheduleTimezone: String = "UTC",
    @field:NotBlank
    @JsonProperty("yaml_content")
    val yamlContent: String,
)

/**
 * Request to trigger workflow run
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TriggerRunRequest(
    val parameters: Map<String, Any> = emptyMap(),
    @JsonProperty("dry_run")
    val dryRun: Boolean = false,
)

/**
 * Request to backfill workflow runs
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BackfillRequest(
    @field:NotBlank
    @JsonProperty("start_date")
    val startDate: String, // YYYY-MM-DD format
    @field:NotBlank
    @JsonProperty("end_date")
    val endDate: String, // YYYY-MM-DD format
    val parameters: Map<String, Any> = emptyMap(),
    @JsonProperty("dry_run")
    val dryRun: Boolean = false,
)

/**
 * Request to stop a workflow run
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class StopRunRequest(
    val reason: String?,
)

/**
 * Request to pause a workflow
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PauseWorkflowRequest(
    val reason: String?,
)

// ========================
// Response DTOs
// ========================

/**
 * Workflow Summary DTO
 *
 * Used for GET /api/v1/workflows list response (minimal info)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorkflowSummaryDto(
    @JsonProperty("dataset_name")
    val datasetName: String,
    @JsonProperty("source_type")
    val sourceType: String,
    val status: String,
    val owner: String,
    val team: String?,
    val description: String?,
    @JsonProperty("airflow_dag_id")
    val airflowDagId: String?,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

/**
 * Workflow Detail DTO
 *
 * Used for register/pause/unpause workflow responses (full details)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorkflowDetailDto(
    @JsonProperty("dataset_name")
    val datasetName: String,
    @JsonProperty("source_type")
    val sourceType: String,
    val status: String,
    val owner: String,
    val team: String?,
    val description: String?,
    @JsonProperty("s3_path")
    val s3Path: String?,
    @JsonProperty("airflow_dag_id")
    val airflowDagId: String?,
    val schedule: String?,
    @JsonProperty("recent_runs")
    val recentRuns: List<WorkflowRunSummaryDto> = emptyList(),
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val updatedAt: LocalDateTime?,
)

/**
 * Workflow Run Summary DTO
 *
 * Used for GET /api/v1/workflows/history response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorkflowRunSummaryDto(
    @JsonProperty("run_id")
    val runId: String,
    @JsonProperty("dataset_name")
    val datasetName: String,
    val status: String,
    @JsonProperty("trigger_mode")
    val triggerMode: String,
    @JsonProperty("execution_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val executionDate: LocalDateTime?,
    @JsonProperty("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val startedAt: LocalDateTime?,
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val completedAt: LocalDateTime?,
    @JsonProperty("duration_seconds")
    val durationSeconds: Long?,
    @JsonProperty("triggered_by")
    val triggeredBy: String?,
)

/**
 * Workflow Run Detail DTO
 *
 * Used for GET /api/v1/workflows/runs/{run_id} and run trigger responses
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class WorkflowRunDetailDto(
    @JsonProperty("run_id")
    val runId: String,
    @JsonProperty("dataset_name")
    val datasetName: String,
    val status: String,
    @JsonProperty("trigger_mode")
    val triggerMode: String,
    @JsonProperty("execution_date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val executionDate: LocalDateTime?,
    val parameters: Map<String, Any>?,
    @JsonProperty("started_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val startedAt: LocalDateTime?,
    @JsonProperty("completed_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val completedAt: LocalDateTime?,
    @JsonProperty("duration_seconds")
    val durationSeconds: Long?,
    @JsonProperty("triggered_by")
    val triggeredBy: String?,
    @JsonProperty("airflow_dag_run_id")
    val airflowDagRunId: String?,
    @JsonProperty("airflow_log_url")
    val airflowLogUrl: String?,
    @JsonProperty("error_message")
    val errorMessage: String?,
)

/**
 * Backfill Response DTO
 *
 * Used for POST /api/v1/workflows/{dataset_name}/backfill response
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class BackfillResponseDto(
    @JsonProperty("backfill_id")
    val backfillId: String,
    @JsonProperty("dataset_name")
    val datasetName: String,
    @JsonProperty("start_date")
    val startDate: String,
    @JsonProperty("end_date")
    val endDate: String,
    @JsonProperty("total_runs")
    val totalRuns: Int,
    @JsonProperty("run_ids")
    val runIds: List<String>,
    val status: String,
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    val createdAt: LocalDateTime?,
)
