package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.domain.service.QualityService
import com.dataops.basecamp.dto.quality.ExecuteQualityTestRequest
import com.dataops.basecamp.dto.quality.QualityRunResultDto
import com.dataops.basecamp.dto.quality.QualitySpecDetailDto
import com.dataops.basecamp.dto.quality.QualitySpecSummaryDto
import com.dataops.basecamp.mapper.QualityMapper
import com.dataops.basecamp.util.SecurityContext
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import mu.KotlinLogging
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
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Quality Management REST API Controller (v1.0 - Partial Migration)
 *
 * Provides endpoints for quality spec management and quality test execution.
 * Supports CLI commands: dli quality list/get/run
 *
 * **API Migration Status (v1.0 → v2.0):**
 * - ✅ **MAINTAINED**: list/get operations continue using `/api/v1/quality`
 * - ⚠️ **DEPRECATED**: execution operations moved to Quality Workflow API
 *
 * **For new implementations, use Quality Workflow API:**
 * - Execution: POST `/api/v1/workflows/quality/{spec_name}/run`
 * - Control: POST `/api/v1/workflows/quality/{spec_name}/{pause|unpause}`
 * - Management: POST/DELETE `/api/v1/workflows/quality/register` or `/{spec_name}`
 * - Monitoring: GET `/api/v1/workflows/quality/runs/{run_id}` or `/history`
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
        description = """
        List quality specifications with optional filtering by resourceType or tag.

        **💡 For workflow operations on these specs, use Quality Workflow API:**
        - Execute: POST /api/v1/workflows/quality/{spec_name}/run
        - Monitor: GET /api/v1/workflows/quality/runs/{run_id} or /history
        """,
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
        description = """
        Get quality specification details by name including tests and recent runs.

        **💡 For workflow operations on this spec, use Quality Workflow API:**
        - Execute: POST /api/v1/workflows/quality/{spec_name}/run
        - Control: POST /api/v1/workflows/quality/{spec_name}/{pause|unpause}
        - Monitor: GET /api/v1/workflows/quality/runs/{run_id}
        """,
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
        val tests = qualityService.getQualityTests(name)
        val recentRuns = qualityService.getQualityRuns(name, limit = 5, offset = 0)

        val response = qualityMapper.toDetailDto(spec, tests, recentRuns)

        return ResponseEntity.ok(response)
    }

    /**
     * Execute quality tests for a registered quality spec
     *
     * POST /api/v1/quality/{name}/run
     * CLI: dli quality run <name>
     *
     * Consistent with Dataset/Metric pattern:
     * - Dataset: POST /api/v1/datasets/{name}/run
     * - Metric: POST /api/v1/metrics/{name}/run
     * - Quality: POST /api/v1/quality/{name}/run
     */
    @Operation(
        summary = "Execute quality tests",
        description = """
        Execute quality tests for a registered quality specification.

        The quality spec name in the path identifies which spec to run.
        The target resource is determined from the spec's resourceName field.

        Optionally filter which tests to run using test_names in the request body.
        """,
    )
    @SwaggerApiResponse(responseCode = "200", description = "Test execution completed successfully")
    @SwaggerApiResponse(responseCode = "404", description = "Quality specification not found")
    @SwaggerApiResponse(responseCode = "408", description = "Test execution timeout")
    @PostMapping("/{name}/run")
    fun runQualitySpec(
        @Parameter(description = "Quality specification name")
        @PathVariable
        @NotBlank name: String,
        @Valid @RequestBody request: ExecuteQualityTestRequest,
    ): ResponseEntity<QualityRunResultDto> {
        logger.info {
            "POST /api/v1/quality/$name/run - tests: ${request.testNames}"
        }

        // Get the spec to retrieve the target resource name
        val spec = qualityService.getQualitySpecOrThrow(name)
        val executedBy = request.executedBy ?: SecurityContext.getCurrentUsername()

        val run =
            qualityService.executeQualityTests(
                resourceName = spec.resourceName,
                qualitySpecName = name,
                testNames = request.testNames.takeIf { it.isNotEmpty() },
                timeout = request.timeout,
                executedBy = executedBy,
            )

        val response = qualityMapper.toRunResultDto(run)

        return ResponseEntity.ok(response)
    }
}
