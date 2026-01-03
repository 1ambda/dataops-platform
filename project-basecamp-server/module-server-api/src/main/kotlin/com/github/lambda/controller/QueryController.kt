package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.command.query.CancelQueryCommand
import com.github.lambda.domain.model.query.QueryScope
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.ListQueriesQuery
import com.github.lambda.domain.service.QueryMetadataService
import com.github.lambda.dto.query.CancelQueryRequestDto
import com.github.lambda.mapper.QueryMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Query REST API Controller
 *
 * Provides query execution metadata management capabilities for tracking,
 * viewing, and cancelling SQL query executions across multiple engines.
 * Supports CLI commands: dli query list/show/cancel
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/queries")
@CrossOrigin
@Validated
@Tag(name = "Query", description = "Query execution metadata API")
class QueryController(
    private val queryMetadataService: QueryMetadataService,
    private val queryMapper: QueryMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List query execution history
     *
     * GET /api/v1/queries
     * CLI: dli query list
     */
    @Operation(
        summary = "List query executions",
        description = "List query execution history with filtering by scope, status, and date range",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid date range or parameters")
    @SwaggerApiResponse(responseCode = "403", description = "Insufficient permissions for scope")
    @GetMapping
    fun listQueries(
        @Parameter(description = "Query scope (my, system, user, all)")
        @RequestParam(defaultValue = "my") scope: String,
        @Parameter(description = "Filter by execution status")
        @RequestParam(required = false) status: String?,
        @Parameter(description = "Filter from date (YYYY-MM-DD)")
        @RequestParam(name = "start_date", required = false) startDate: String?,
        @Parameter(description = "Filter to date (YYYY-MM-DD)")
        @RequestParam(name = "end_date", required = false) endDate: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<*> {
        logger.info {
            "GET /api/v1/queries - scope: $scope, status: $status, startDate: $startDate, endDate: $endDate, limit: $limit, offset: $offset"
        }

        return try {
            val queryScope = QueryScope.valueOf(scope.uppercase())
            val queryStatus = status?.let { QueryStatus.valueOf(it.uppercase()) }

            val parsedStartDate = startDate?.let { LocalDate.parse(it) }
            val parsedEndDate = endDate?.let { LocalDate.parse(it) }

            val query =
                ListQueriesQuery(
                    scope = queryScope,
                    status = queryStatus,
                    startDate = parsedStartDate,
                    endDate = parsedEndDate,
                    limit = limit,
                    offset = offset,
                )

            val currentUser = getCurrentUser()
            val queries = queryMetadataService.listQueries(query, currentUser)

            ResponseEntity.ok(queryMapper.toListItemDtoList(queries))
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                queryMapper.toInvalidDateRangeError("Invalid scope or status value"),
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to list queries" }
            ResponseEntity.badRequest().body(
                queryMapper.toInvalidDateRangeError(e.message ?: "Unknown error"),
            )
        }
    }

    /**
     * Get query execution details
     *
     * GET /api/v1/queries/{query_id}
     * CLI: dli query show <query_id>
     */
    @Operation(
        summary = "Get query details",
        description = "Get detailed query execution information including execution plan and error details",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "403", description = "Not authorized to view this query")
    @SwaggerApiResponse(responseCode = "404", description = "Query not found")
    @GetMapping("/{query_id}")
    fun getQueryDetails(
        @Parameter(description = "Unique query identifier", required = true)
        @PathVariable("query_id") queryId: String,
    ): ResponseEntity<*> {
        logger.info { "GET /api/v1/queries/$queryId" }

        val currentUser = getCurrentUser()
        val query = queryMetadataService.getQueryDetails(queryId, currentUser)

        return if (query != null) {
            ResponseEntity.ok(queryMapper.toDetailDto(query))
        } else {
            ResponseEntity.notFound().build<Any>()
        }
    }

    /**
     * Cancel a running query
     *
     * POST /api/v1/queries/{query_id}/cancel
     * CLI: dli query cancel <query_id>
     */
    @Operation(
        summary = "Cancel query",
        description = "Cancel a running query execution",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Query cancelled successfully")
    @SwaggerApiResponse(responseCode = "403", description = "Not authorized to cancel this query")
    @SwaggerApiResponse(responseCode = "404", description = "Query not found")
    @SwaggerApiResponse(responseCode = "409", description = "Query cannot be cancelled (already completed)")
    @PostMapping("/{query_id}/cancel")
    fun cancelQuery(
        @Parameter(description = "Unique query identifier", required = true)
        @PathVariable("query_id") queryId: String,
        @RequestBody(required = false) request: CancelQueryRequestDto?,
    ): ResponseEntity<*> {
        logger.info { "POST /api/v1/queries/$queryId/cancel - reason: ${request?.reason}" }

        val currentUser = getCurrentUser()
        val command =
            CancelQueryCommand(
                queryId = queryId,
                reason = request?.reason,
            )

        val cancelledQuery = queryMetadataService.cancelQuery(command, currentUser)

        return ResponseEntity.ok(queryMapper.toCancelQueryResponseDto(cancelledQuery))
    }

    /**
     * Get current user from security context
     * TODO: Implement proper security context integration
     */
    private fun getCurrentUser(): String {
        // For now, return a mock user
        // In production, this should extract the user from Spring Security context
        return "analyst@example.com"
    }
}
