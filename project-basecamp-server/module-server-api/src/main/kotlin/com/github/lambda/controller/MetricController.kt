package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.service.MetricExecutionService
import com.github.lambda.domain.service.MetricService
import com.github.lambda.dto.metric.CreateMetricRequest
import com.github.lambda.dto.metric.CreateMetricResponse
import com.github.lambda.dto.metric.MetricExecutionResultDto
import com.github.lambda.dto.metric.MetricResponse
import com.github.lambda.dto.metric.RunMetricRequest
import com.github.lambda.mapper.MetricMapper
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
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Metric Management REST API Controller
 *
 * Provides endpoints for metric CRUD operations and execution.
 * Supports CLI commands: dli metric list/get/register/run
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/metrics")
@CrossOrigin
@Validated
@Tag(name = "Metric", description = "Metric management API")
class MetricController(
    private val metricService: MetricService,
    private val metricExecutionService: MetricExecutionService,
    private val metricMapper: MetricMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List metrics with optional filters
     *
     * GET /api/v1/metrics
     * CLI: dli metric list
     */
    @Operation(
        summary = "List metrics",
        description = "List metrics with optional filtering by tag, owner, or search term",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listMetrics(
        @Parameter(description = "Filter by tag (exact match)")
        @RequestParam(required = false) tag: String?,
        @Parameter(description = "Filter by owner (partial match)")
        @RequestParam(required = false) owner: String?,
        @Parameter(description = "Search in name and description")
        @RequestParam(required = false) search: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<List<MetricResponse>> {
        logger.info {
            "GET /api/v1/metrics - tag: $tag, owner: $owner, search: $search, limit: $limit, offset: $offset"
        }

        val metrics = metricService.listMetrics(tag, owner, search, limit, offset)
        val response = metrics.map { metricMapper.toListResponse(it) }

        return ResponseEntity.ok(response)
    }

    /**
     * Get metric by name
     *
     * GET /api/v1/metrics/{name}
     * CLI: dli metric get <name>
     */
    @Operation(
        summary = "Get metric details",
        description = "Get metric details by fully qualified name (catalog.schema.name)",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Metric not found")
    @GetMapping("/{name}")
    fun getMetric(
        @Parameter(description = "Fully qualified metric name (catalog.schema.name)")
        @PathVariable
        @NotBlank name: String,
    ): ResponseEntity<MetricResponse> {
        logger.info { "GET /api/v1/metrics/$name" }

        val metric = metricService.getMetricOrThrow(name)

        return ResponseEntity.ok(metricMapper.toResponse(metric))
    }

    /**
     * Create/register a new metric
     *
     * POST /api/v1/metrics
     * CLI: dli metric register <file>
     */
    @Operation(
        summary = "Register metric",
        description = "Register a new metric from specification",
    )
    @SwaggerApiResponse(responseCode = "201", description = "Metric created")
    @SwaggerApiResponse(responseCode = "400", description = "Validation error")
    @SwaggerApiResponse(responseCode = "409", description = "Metric already exists")
    @PostMapping
    fun createMetric(
        @Valid @RequestBody request: CreateMetricRequest,
    ): ResponseEntity<CreateMetricResponse> {
        logger.info { "POST /api/v1/metrics - name: ${request.name}" }

        val command = metricMapper.extractCreateCommand(request)
        val metric =
            metricService.createMetric(
                name = command.name,
                owner = command.owner,
                team = command.team,
                description = command.description,
                sql = command.sql,
                sourceTable = command.sourceTable,
                tags = command.tags.toList(), // Convert Set back to List for service compatibility
            )

        val response =
            CreateMetricResponse(
                message = "Metric '${metric.name}' registered successfully",
                name = metric.name,
            )

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(response)
    }

    /**
     * Execute/run a metric
     *
     * POST /api/v1/metrics/{name}/run
     * CLI: dli metric run <name>
     */
    @Operation(
        summary = "Run metric",
        description = "Execute metric SQL with optional parameters",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Execution successful")
    @SwaggerApiResponse(responseCode = "404", description = "Metric not found")
    @SwaggerApiResponse(responseCode = "408", description = "Execution timeout")
    @PostMapping("/{name}/run")
    fun runMetric(
        @Parameter(description = "Fully qualified metric name")
        @PathVariable
        @NotBlank name: String,
        @Valid @RequestBody request: RunMetricRequest,
    ): ResponseEntity<MetricExecutionResultDto> {
        logger.info { "POST /api/v1/metrics/$name/run" }

        val result =
            metricExecutionService.executeMetric(
                metricName = name,
                parameters = request.parameters,
                limit = request.limit,
                timeout = request.timeout,
            )

        return ResponseEntity.ok(metricMapper.toExecutionResultDto(result))
    }
}
