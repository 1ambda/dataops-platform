package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.common.enums.WorkflowSourceType
import com.dataops.basecamp.domain.internal.workflow.ScheduleInfo
import com.dataops.basecamp.domain.service.QualityService
import com.dataops.basecamp.domain.service.WorkflowService
import com.dataops.basecamp.dto.quality.QualityRunResultDto
import com.dataops.basecamp.dto.quality.QualityRunSummaryDto
import com.dataops.basecamp.dto.workflow.BackfillRequest
import com.dataops.basecamp.dto.workflow.BackfillResponseDto
import com.dataops.basecamp.dto.workflow.PauseWorkflowRequest
import com.dataops.basecamp.dto.workflow.RegisterWorkflowRequest
import com.dataops.basecamp.dto.workflow.StopRunRequest
import com.dataops.basecamp.dto.workflow.TriggerRunRequest
import com.dataops.basecamp.dto.workflow.WorkflowDetailDto
import com.dataops.basecamp.dto.workflow.WorkflowRunDetailDto
import com.dataops.basecamp.dto.workflow.WorkflowRunSummaryDto
import com.dataops.basecamp.dto.workflow.WorkflowSummaryDto
import com.dataops.basecamp.mapper.QualityMapper
import com.dataops.basecamp.mapper.WorkflowMapper
import com.dataops.basecamp.util.SecurityContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import mu.KotlinLogging
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Workflow Management REST API Controller
 *
 * Provides endpoints for workflow management and execution.
 * Supports CLI commands:
 * - Dataset workflows: dli workflow list/run/backfill/stop/status/history/pause/unpause/register/unregister
 * - Quality workflows: dli quality run/status/stop/history/pause/unpause/register/unregister
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/workflows")
@CrossOrigin
@Validated
@Tag(name = "Workflow", description = "Dataset and Quality workflow management API")
@PreAuthorize("hasRole('ROLE_USER')")
class WorkflowController(
    private val workflowService: WorkflowService,
    private val workflowMapper: WorkflowMapper,
    private val qualityService: QualityService,
    private val qualityMapper: QualityMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List workflows with optional filters
     *
     * GET /api/v1/workflows
     * CLI: dli workflow list
     */
    @Operation(
        summary = "List workflows",
        description = "List workflows with optional filtering by status, sourceType, or owner",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listWorkflows(
        @Parameter(description = "Filter by workflow status (ACTIVE, PAUSED, DISABLED)")
        @RequestParam(required = false) status: String?,
        @Parameter(description = "Filter by source type (MANUAL, CODE)")
        @RequestParam(required = false) sourceType: String?,
        @Parameter(description = "Filter by owner email")
        @RequestParam(required = false) owner: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<List<WorkflowSummaryDto>> {
        logger.info {
            "GET /api/v1/workflows - status: $status, sourceType: $sourceType, owner: $owner, limit: $limit, offset: $offset"
        }

        val workflows = workflowService.getWorkflows(status, sourceType, owner, limit, offset)
        val response = workflows.map { workflowMapper.toSummaryDto(it) }

        return ResponseEntity.ok(response)
    }

    /**
     * Get workflow run status by run ID
     *
     * GET /api/v1/workflows/runs/{run_id}
     * CLI: dli workflow status <run_id>
     */
    @Operation(
        summary = "Get workflow run status",
        description = "Get detailed status information for a specific workflow run",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow run not found")
    @GetMapping("/runs/{run_id}")
    fun getWorkflowRunStatus(
        @Parameter(description = "Workflow run ID")
        @PathVariable("run_id")
        @NotBlank runId: String,
    ): ResponseEntity<WorkflowRunDetailDto> {
        logger.info { "GET /api/v1/workflows/runs/$runId" }

        val run = workflowService.getWorkflowRunWithSync(runId)
        val response = workflowMapper.toRunDetailDto(run)

        return ResponseEntity.ok(response)
    }

    /**
     * Get workflow execution history
     *
     * GET /api/v1/workflows/history
     * CLI: dli workflow history
     */
    @Operation(
        summary = "Get workflow execution history",
        description = "Get execution history with optional filtering by dataset name and date range",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/history")
    fun getWorkflowHistory(
        @Parameter(description = "Filter by dataset name")
        @RequestParam(required = false) datasetName: String?,
        @Parameter(description = "Filter by start date (YYYY-MM-DD)")
        @RequestParam(required = false) startDate: String?,
        @Parameter(description = "Filter by end date (YYYY-MM-DD)")
        @RequestParam(required = false) endDate: String?,
        @Parameter(description = "Maximum results (1-100)")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100) limit: Int,
    ): ResponseEntity<List<WorkflowRunSummaryDto>> {
        logger.info {
            "GET /api/v1/workflows/history - datasetName: $datasetName, startDate: $startDate, endDate: $endDate, limit: $limit"
        }

        val runs = workflowService.getWorkflowHistory(datasetName, startDate, endDate, limit)
        val response = runs.map { workflowMapper.toRunSummaryDto(it) }

        return ResponseEntity.ok(response)
    }

    /**
     * Register a new workflow
     *
     * POST /api/v1/workflows/register
     * CLI: dli workflow register
     */
    @Operation(
        summary = "Register workflow",
        description = "Register a new workflow with dataset specification",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Workflow registered successfully")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow already exists")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid workflow specification")
    @PostMapping("/register")
    fun registerWorkflow(
        @Valid @RequestBody request: RegisterWorkflowRequest,
    ): ResponseEntity<WorkflowDetailDto> {
        logger.info {
            "POST /api/v1/workflows/register - datasetName: ${request.datasetName}, sourceType: ${request.sourceType}"
        }

        val workflow =
            workflowService.registerWorkflow(
                datasetName = request.datasetName,
                sourceType = WorkflowSourceType.valueOf(request.sourceType),
                schedule =
                    ScheduleInfo(
                        cron = request.scheduleCron,
                        timezone = request.scheduleTimezone,
                    ),
                owner = request.owner,
                team = request.team,
                description = request.description,
                yamlContent = request.yamlContent,
            )

        val response = workflowMapper.toDetailDto(workflow)

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    /**
     * Trigger workflow run
     *
     * POST /api/v1/workflows/{dataset_name}/run
     * CLI: dli workflow run <dataset_name>
     */
    @Operation(
        summary = "Trigger workflow run",
        description = "Trigger a new execution of the specified workflow",
    )
    @SwaggerApiResponse(responseCode = "202", description = "Workflow run triggered successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow not found")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow cannot be run (paused or disabled)")
    @PostMapping("/{dataset_name}/run")
    fun triggerWorkflowRun(
        @Parameter(description = "Dataset name (workflow identifier)")
        @PathVariable("dataset_name")
        @NotBlank datasetName: String,
        @Valid @RequestBody request: TriggerRunRequest,
    ): ResponseEntity<WorkflowRunDetailDto> {
        logger.info {
            "POST /api/v1/workflows/$datasetName/run - parameters: ${request.parameters}, dryRun: ${request.dryRun}"
        }

        val run =
            workflowService.triggerWorkflowRun(
                datasetName = datasetName,
                params = request.parameters,
                dryRun = request.dryRun,
            )

        val response = workflowMapper.toRunDetailDto(run)

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    /**
     * Trigger workflow backfill
     *
     * POST /api/v1/workflows/{dataset_name}/backfill
     * CLI: dli workflow backfill <dataset_name>
     */
    @Operation(
        summary = "Trigger workflow backfill",
        description = "Trigger backfill runs for the specified date range",
    )
    @SwaggerApiResponse(responseCode = "202", description = "Backfill triggered successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow not found")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid date range")
    @PostMapping("/{dataset_name}/backfill")
    fun triggerBackfill(
        @Parameter(description = "Dataset name (workflow identifier)")
        @PathVariable("dataset_name")
        @NotBlank datasetName: String,
        @Valid @RequestBody request: BackfillRequest,
    ): ResponseEntity<BackfillResponseDto> {
        logger.info {
            "POST /api/v1/workflows/$datasetName/backfill - startDate: ${request.startDate}, endDate: ${request.endDate}"
        }

        val backfillResult =
            workflowService.triggerBackfill(
                datasetName = datasetName,
                startDate = request.startDate,
                endDate = request.endDate,
                params = request.parameters,
            )

        val response =
            workflowMapper.toBackfillResponseDto(
                backfillId = "backfill_${System.currentTimeMillis()}",
                datasetName = datasetName,
                startDate = request.startDate,
                endDate = request.endDate,
                runIds = backfillResult.map { it.runId },
            )

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    /**
     * Stop workflow run
     *
     * POST /api/v1/workflows/runs/{run_id}/stop
     * CLI: dli workflow stop <run_id>
     */
    @Operation(
        summary = "Stop workflow run",
        description = "Stop a running workflow execution",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Workflow run stop requested")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow run not found")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow run cannot be stopped")
    @PostMapping("/runs/{run_id}/stop")
    fun stopWorkflowRun(
        @Parameter(description = "Workflow run ID")
        @PathVariable("run_id")
        @NotBlank runId: String,
        @Valid @RequestBody request: StopRunRequest,
    ): ResponseEntity<WorkflowRunDetailDto> {
        logger.info { "POST /api/v1/workflows/runs/$runId/stop - reason: ${request.reason}" }

        val run = workflowService.stopWorkflowRun(runId, request.reason)
        val response = workflowMapper.toRunDetailDto(run)

        return ResponseEntity.ok(response)
    }

    /**
     * Pause workflow
     *
     * POST /api/v1/workflows/{dataset_name}/pause
     * CLI: dli workflow pause <dataset_name>
     */
    @Operation(
        summary = "Pause workflow",
        description = "Pause workflow scheduling (no new runs will be triggered)",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Workflow paused successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow not found")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow is already paused or disabled")
    @PostMapping("/{dataset_name}/pause")
    fun pauseWorkflow(
        @Parameter(description = "Dataset name (workflow identifier)")
        @PathVariable("dataset_name")
        @NotBlank datasetName: String,
        @Valid @RequestBody request: PauseWorkflowRequest,
    ): ResponseEntity<WorkflowDetailDto> {
        logger.info { "POST /api/v1/workflows/$datasetName/pause - reason: ${request.reason}" }

        val workflow = workflowService.pauseWorkflow(datasetName, request.reason)
        val response = workflowMapper.toDetailDto(workflow)

        return ResponseEntity.ok(response)
    }

    /**
     * Unpause workflow
     *
     * POST /api/v1/workflows/{dataset_name}/unpause
     * CLI: dli workflow unpause <dataset_name>
     */
    @Operation(
        summary = "Unpause workflow",
        description = "Resume workflow scheduling",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Workflow unpaused successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow not found")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow is not paused")
    @PostMapping("/{dataset_name}/unpause")
    fun unpauseWorkflow(
        @Parameter(description = "Dataset name (workflow identifier)")
        @PathVariable("dataset_name")
        @NotBlank datasetName: String,
    ): ResponseEntity<WorkflowDetailDto> {
        logger.info { "POST /api/v1/workflows/$datasetName/unpause" }

        val workflow = workflowService.unpauseWorkflow(datasetName)
        val response = workflowMapper.toDetailDto(workflow)

        return ResponseEntity.ok(response)
    }

    /**
     * Unregister workflow
     *
     * DELETE /api/v1/workflows/{dataset_name}
     * CLI: dli workflow unregister <dataset_name>
     */
    @Operation(
        summary = "Unregister workflow",
        description = "Remove workflow registration (optionally force deletion)",
    )
    @SwaggerApiResponse(responseCode = "204", description = "Workflow unregistered successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Workflow not found")
    @SwaggerApiResponse(responseCode = "409", description = "Workflow has running tasks (use force=true)")
    @DeleteMapping("/{dataset_name}")
    fun unregisterWorkflow(
        @Parameter(description = "Dataset name (workflow identifier)")
        @PathVariable("dataset_name")
        @NotBlank datasetName: String,
        @Parameter(description = "Force deletion even if workflow has running tasks")
        @RequestParam(defaultValue = "false")
        force: Boolean,
    ): ResponseEntity<Void> {
        logger.info { "DELETE /api/v1/workflows/$datasetName - force: $force" }

        workflowService.unregisterWorkflow(datasetName, force)

        return ResponseEntity.noContent().build()
    }

    // ========================================
    // Quality Workflow Endpoints
    // ========================================

    /**
     * Execute quality tests for a specification
     *
     * POST /api/v1/workflows/quality/{spec_name}/run
     * CLI: dli quality run <spec_name>
     */
    @Operation(
        summary = "Execute quality tests",
        description = "Execute quality tests for a specific quality specification via workflow",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Quality run started successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @PostMapping("/quality/{spec_name}/run")
    fun runQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable("spec_name")
        @NotBlank specName: String,
        @Valid @RequestBody request: TriggerRunRequest,
    ): ResponseEntity<QualityRunResultDto> {
        logger.info {
            "POST /api/v1/workflows/quality/$specName/run - params: ${request.parameters}"
        }

        val executedBy = SecurityContext.getCurrentUsername()

        val run =
            qualityService.executeQualityWorkflow(
                specName = specName,
                parameters = request.parameters,
                executedBy = executedBy,
            )

        val response = qualityMapper.toRunResultDto(run)

        return ResponseEntity.ok(response)
    }

    /**
     * Get quality run status by run ID
     *
     * GET /api/v1/workflows/quality/runs/{run_id}
     * CLI: dli quality status <run_id>
     */
    @Operation(
        summary = "Get quality run status",
        description = "Get quality run status and details by run ID",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Quality run not found")
    @GetMapping("/quality/runs/{run_id}")
    fun getQualityRunStatus(
        @Parameter(description = "Quality run ID")
        @PathVariable("run_id")
        @NotBlank runId: String,
    ): ResponseEntity<QualityRunResultDto> {
        logger.info { "GET /api/v1/workflows/quality/runs/$runId" }

        val run = qualityService.getQualityRunByIdOrThrow(runId)
        // TODO: Convert QualityTestEntity list to TestResultSummaryDto list when needed
        val response = qualityMapper.toRunResultDto(run, emptyList())

        return ResponseEntity.ok(response)
    }

    /**
     * Stop quality run by run ID
     *
     * POST /api/v1/workflows/quality/runs/{run_id}/stop
     * CLI: dli quality stop <run_id>
     */
    @Operation(
        summary = "Stop quality run",
        description = "Stop a running quality test execution by run ID",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Quality run stopped successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality run not found")
    @SwaggerApiResponse(responseCode = "409", description = "Quality run cannot be stopped (already finished)")
    @PostMapping("/quality/runs/{run_id}/stop")
    fun stopQualityRun(
        @Parameter(description = "Quality run ID")
        @PathVariable("run_id")
        @NotBlank runId: String,
        @Valid @RequestBody request: StopRunRequest,
    ): ResponseEntity<QualityRunResultDto> {
        logger.info { "POST /api/v1/workflows/quality/runs/$runId/stop - reason: ${request.reason}" }

        val stoppedBy = SecurityContext.getCurrentUsername()

        val run =
            qualityService.stopQualityRun(
                runId = runId,
                stoppedBy = stoppedBy,
                reason = request.reason,
            )

        val response = qualityMapper.toRunResultDto(run)

        return ResponseEntity.ok(response)
    }

    /**
     * Get quality run history
     *
     * GET /api/v1/workflows/quality/history
     * CLI: dli quality history
     */
    @Operation(
        summary = "Get quality run history",
        description = "Get paginated quality run history with optional filtering",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping("/quality/history")
    fun getQualityRunHistory(
        @Parameter(description = "Filter by quality specification name")
        @RequestParam(required = false) specName: String?,
        @Parameter(description = "Filter by run status (PENDING, RUNNING, SUCCESS, FAILED)")
        @RequestParam(required = false) status: String?,
        @Parameter(description = "Filter by executor (user email)")
        @RequestParam(required = false) executedBy: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<List<QualityRunSummaryDto>> {
        logger.info {
            "GET /api/v1/workflows/quality/history - specName: $specName, status: $status, " +
                "executedBy: $executedBy, limit: $limit, offset: $offset"
        }

        val runs =
            qualityService.getQualityRunHistory(
                specName = specName,
                status = status,
                executedBy = executedBy,
                limit = limit,
                offset = offset,
            )

        val response = runs.map { qualityMapper.toRunSummaryDto(it) }

        return ResponseEntity.ok(response)
    }

    /**
     * Pause quality specification
     *
     * POST /api/v1/workflows/quality/{spec_name}/pause
     * CLI: dli quality pause <spec_name>
     */
    @Operation(
        summary = "Pause quality specification",
        description = "Pause scheduled execution of a quality specification",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Quality specification paused successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @SwaggerApiResponse(responseCode = "409", description = "Quality specification is already paused or disabled")
    @PostMapping("/quality/{spec_name}/pause")
    fun pauseQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable("spec_name")
        @NotBlank specName: String,
        @Valid @RequestBody request: PauseWorkflowRequest,
    ): ResponseEntity<Void> {
        logger.info { "POST /api/v1/workflows/quality/$specName/pause - reason: ${request.reason}" }

        val pausedBy = SecurityContext.getCurrentUsername()

        qualityService.pauseQualitySpec(
            specName = specName,
            pausedBy = pausedBy,
            reason = request.reason,
        )

        return ResponseEntity.ok().build()
    }

    /**
     * Unpause quality specification
     *
     * POST /api/v1/workflows/quality/{spec_name}/unpause
     * CLI: dli quality unpause <spec_name>
     */
    @Operation(
        summary = "Unpause quality specification",
        description = "Resume scheduled execution of a quality specification",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Quality specification unpaused successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @SwaggerApiResponse(responseCode = "409", description = "Quality specification is not paused")
    @PostMapping("/quality/{spec_name}/unpause")
    fun unpauseQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable("spec_name")
        @NotBlank specName: String,
    ): ResponseEntity<Void> {
        logger.info { "POST /api/v1/workflows/quality/$specName/unpause" }

        val unpausedBy = SecurityContext.getCurrentUsername()

        qualityService.unpauseQualitySpec(
            specName = specName,
            unpausedBy = unpausedBy,
        )

        return ResponseEntity.ok().build()
    }

    /**
     * Register quality specification
     *
     * POST /api/v1/workflows/quality/register
     * CLI: dli quality register <file>
     */
    @Operation(
        summary = "Register quality specification",
        description = "Register a new quality specification from YAML definition",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Quality specification registered successfully")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid YAML content or specification")
    @SwaggerApiResponse(responseCode = "409", description = "Quality specification with same name already exists")
    @PostMapping("/quality/register")
    fun registerQualitySpec(
        @Valid @RequestBody request: RegisterWorkflowRequest,
    ): ResponseEntity<Void> {
        logger.info { "POST /api/v1/workflows/quality/register - datasetName: ${request.datasetName}" }

        val registeredBy = SecurityContext.getCurrentUsername()

        qualityService.registerQualitySpec(
            yamlContent = request.yamlContent,
            registeredBy = registeredBy,
            sourceType = WorkflowSourceType.valueOf(request.sourceType),
            s3Path = null, // s3Path not available in RegisterWorkflowRequest
        )

        return ResponseEntity.status(HttpStatus.CREATED).build()
    }

    /**
     * Unregister quality specification
     *
     * DELETE /api/v1/workflows/quality/{spec_name}
     * CLI: dli quality unregister <spec_name>
     */
    @Operation(
        summary = "Unregister quality specification",
        description = "Remove quality specification and stop all related executions",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Quality specification unregistered successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @SwaggerApiResponse(
        responseCode = "409",
        description = "Quality specification cannot be unregistered (has running executions)",
    )
    @DeleteMapping("/quality/{spec_name}")
    fun unregisterQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable("spec_name")
        @NotBlank specName: String,
    ): ResponseEntity<Void> {
        logger.info { "DELETE /api/v1/workflows/quality/$specName" }

        val unregisteredBy = SecurityContext.getCurrentUsername()

        qualityService.unregisterQualitySpec(
            specName = specName,
            unregisteredBy = unregisteredBy,
        )

        return ResponseEntity.ok().build()
    }
}
