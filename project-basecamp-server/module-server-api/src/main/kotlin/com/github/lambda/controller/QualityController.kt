package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.service.QualityService
import com.github.lambda.dto.quality.ExecuteQualityTestRequest
import com.github.lambda.dto.quality.QualityRunResultDto
import com.github.lambda.dto.quality.QualitySpecDetailDto
import com.github.lambda.dto.quality.QualitySpecSummaryDto
import com.github.lambda.mapper.QualityMapper
import com.github.lambda.util.SecurityContext
import io.github.oshai.kotlinlogging.KotlinLogging
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Quality Management REST API Controller
 *
 * Provides endpoints for quality spec management and quality test execution.
 * Supports CLI commands: dli quality list/get/run
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/quality")
@CrossOrigin
@Validated
@Tag(name = "Quality", description = "Quality management API")
@PreAuthorize("hasRole('ROLE_USER')")
class QualityController(
    private val qualityService: QualityService,
    private val qualityMapper: QualityMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List quality specifications with optional filters
     *
     * GET /api/v1/quality
     * CLI: dli quality list
     */
    @Operation(
        summary = "List quality specifications",
        description = "List quality specifications with optional filtering by resourceType or tag",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @GetMapping
    fun listQualitySpecs(
        @Parameter(description = "Filter by resource type (DATASET, METRIC)")
        @RequestParam(required = false) resourceType: String?,
        @Parameter(description = "Filter by tag (exact match)")
        @RequestParam(required = false) tag: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<List<QualitySpecSummaryDto>> {
        logger.info {
            "GET /api/v1/quality - resourceType: $resourceType, tag: $tag, limit: $limit, offset: $offset"
        }

        val specs = qualityService.getQualitySpecs(resourceType, tag, limit, offset)
        val response = specs.map { qualityMapper.toSummaryDto(it) }

        return ResponseEntity.ok(response)
    }

    /**
     * Get quality specification details by name
     *
     * GET /api/v1/quality/{name}
     * CLI: dli quality get <name>
     */
    @Operation(
        summary = "Get quality specification details",
        description = "Get quality specification details by name including tests and recent runs",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @GetMapping("/{name}")
    fun getQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable
        @NotBlank name: String,
    ): ResponseEntity<QualitySpecDetailDto> {
        logger.info { "GET /api/v1/quality/$name" }

        val spec = qualityService.getQualitySpecOrThrow(name)
        val recentRuns = qualityService.getQualityRuns(name, limit = 5, offset = 0)

        val response = qualityMapper.toDetailDto(spec, recentRuns)

        return ResponseEntity.ok(response)
    }

    /**
     * Execute quality tests for a resource
     *
     * POST /api/v1/quality/test/{resource_name}
     * CLI: dli quality run <resource_name>
     */
    @Operation(
        summary = "Execute quality tests",
        description = "Execute quality tests for a specific resource with optional filtering",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Test execution started successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification or resource not found")
    @SwaggerApiResponse(responseCode = "408", description = "Test execution timeout")
    @PostMapping("/test/{resource_name}")
    fun executeQualityTests(
        @Parameter(description = "Fully qualified resource name (catalog.schema.table)")
        @PathVariable("resource_name")
        @NotBlank resourceName: String,
        @Valid @RequestBody request: ExecuteQualityTestRequest,
    ): ResponseEntity<QualityRunResultDto> {
        logger.info { "POST /api/v1/quality/test/$resourceName - spec: ${request.qualitySpecName}, tests: ${request.testNames}" }

        val executedBy = request.executedBy ?: SecurityContext.getCurrentUsername()

        val run = qualityService.executeQualityTests(
            resourceName = resourceName,
            qualitySpecName = request.qualitySpecName,
            testNames = request.testNames.takeIf { it.isNotEmpty() },
            timeout = request.timeout,
            executedBy = executedBy,
        )

        val response = qualityMapper.toRunResultDto(run)

        return ResponseEntity.ok(response)
    }
}
