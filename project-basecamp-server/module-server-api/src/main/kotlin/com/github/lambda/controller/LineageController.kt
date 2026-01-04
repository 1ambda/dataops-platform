package com.github.lambda.controller

import com.github.lambda.common.enums.LineageDirection
import com.github.lambda.common.exception.InvalidParameterException
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.domain.service.LineageService
import com.github.lambda.dto.ApiResponse
import com.github.lambda.dto.ErrorDetails
import com.github.lambda.dto.lineage.LineageGraphDto
import com.github.lambda.mapper.LineageMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Lineage REST API Controller
 *
 * Provides lineage graph retrieval and analysis capabilities.
 * Supports traversing upstream dependencies, downstream dependents, or both directions.
 */
@RestController
@RequestMapping("/api/v1/lineage")
@CrossOrigin
@Validated
@Tag(name = "Lineage", description = "Data lineage analysis API")
class LineageController(
    private val lineageService: LineageService,
    private val lineageMapper: LineageMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get lineage graph for a resource
     *
     * GET /api/v1/lineage/{resource_name}
     *
     * Retrieves the lineage graph for a given resource, showing its dependencies
     * and dependents based on the specified direction and depth.
     */
    @Operation(
        summary = "Get resource lineage",
        description = """
            Get the lineage graph for a resource showing its dependencies and dependents.
            Supports upstream (dependencies), downstream (dependents), or both directions.
            Depth can be limited to control traversal scope.
        """,
    )
    @SwaggerApiResponse(
        responseCode = "200",
        description = "Lineage graph retrieved successfully",
    )
    @SwaggerApiResponse(
        responseCode = "400",
        description = "Invalid direction or depth parameter",
    )
    @SwaggerApiResponse(
        responseCode = "404",
        description = "Resource not found in lineage graph",
    )
    @GetMapping("/{resource_name}")
    fun getLineageGraph(
        @Parameter(
            description = "Fully qualified resource name (e.g., 'iceberg.analytics.users')",
            required = true,
            example = "iceberg.analytics.users",
        )
        @PathVariable("resource_name") resourceName: String,
        @Parameter(
            description = "Lineage direction: 'upstream' (dependencies), 'downstream' (dependents), or 'both'",
            example = "both",
        )
        @RequestParam(defaultValue = "both") direction: String,
        @Parameter(
            description = "Maximum depth for traversal (-1 for unlimited, 0 for just the resource itself)",
            example = "3",
        )
        @RequestParam(defaultValue = "-1")
        @Min(-1, message = "Depth must be -1 (unlimited) or a positive integer")
        @Max(10, message = "Depth cannot exceed 10 levels")
        depth: Int,
    ): ResponseEntity<ApiResponse<LineageGraphDto>> {
        logger.info {
            "GET /api/v1/lineage/$resourceName - direction: $direction, depth: $depth"
        }

        return try {
            // Validate and parse direction parameter
            val lineageDirection = parseDirection(direction)

            // Validate depth parameter
            validateDepth(depth)

            // Get lineage graph from service
            val lineageResult =
                lineageService.getLineageGraph(
                    resourceName = resourceName,
                    direction = lineageDirection,
                    depth = depth,
                )

            // Convert to DTO and return
            val responseDto = lineageMapper.toGraphDto(lineageResult)

            logger.info {
                "Lineage graph retrieved successfully for $resourceName: " +
                    "${responseDto.totalNodes} nodes, ${responseDto.totalEdges} edges"
            }

            ResponseEntity.ok(
                ApiResponse.success(
                    data = responseDto,
                    message = "Lineage graph retrieved successfully",
                ),
            )
        } catch (e: InvalidParameterException) {
            logger.warn { "Invalid parameter for lineage request: ${e.message}" }
            ResponseEntity.badRequest().body(
                ApiResponse.error<LineageGraphDto>(
                    message = e.message ?: "Invalid parameter",
                    error =
                        ErrorDetails(
                            code = "LINEAGE_INVALID_PARAMETER",
                            details =
                                mapOf(
                                    "resource_name" to resourceName,
                                    "direction" to direction,
                                    "depth" to depth,
                                ),
                        ),
                ),
            )
        } catch (e: ResourceNotFoundException) {
            logger.warn { "Resource not found in lineage: ${e.message}" }
            ResponseEntity.notFound().build<ApiResponse<LineageGraphDto>>()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get lineage graph for $resourceName" }
            ResponseEntity.internalServerError().body(
                ApiResponse.error<LineageGraphDto>(
                    message = "Failed to retrieve lineage graph",
                    error =
                        ErrorDetails(
                            code = "LINEAGE_INTERNAL_ERROR",
                            details = mapOf("resource_name" to resourceName),
                        ),
                ),
            )
        }
    }

    /**
     * Parse and validate direction parameter
     */
    private fun parseDirection(direction: String): LineageDirection =
        try {
            when (direction.lowercase()) {
                "upstream" -> LineageDirection.UPSTREAM
                "downstream" -> LineageDirection.DOWNSTREAM
                "both" -> LineageDirection.BOTH
                else -> throw InvalidParameterException(
                    parameterName = "direction",
                    value = direction,
                    reason = "Must be 'upstream', 'downstream', or 'both'",
                )
            }
        } catch (e: IllegalArgumentException) {
            throw InvalidParameterException(
                parameterName = "direction",
                value = direction,
                reason = "Must be 'upstream', 'downstream', or 'both'",
            )
        }

    /**
     * Validate depth parameter
     */
    private fun validateDepth(depth: Int) {
        if (depth < -1) {
            throw InvalidParameterException(
                parameterName = "depth",
                value = depth,
                reason = "Must be -1 (unlimited) or a non-negative integer",
            )
        }
        if (depth > 10) {
            throw InvalidParameterException(
                parameterName = "depth",
                value = depth,
                reason = "Maximum allowed depth is 10 to prevent excessive traversal",
            )
        }
    }
}
