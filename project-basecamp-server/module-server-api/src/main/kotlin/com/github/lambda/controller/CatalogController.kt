package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.command.query.CancelQueryCommand
import com.github.lambda.domain.model.query.QueryScope
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.ListQueriesQuery
import com.github.lambda.domain.service.CatalogService
import com.github.lambda.domain.service.QueryMetadataService
import com.github.lambda.dto.catalog.SampleQueryResponse
import com.github.lambda.dto.catalog.TableDetailResponse
import com.github.lambda.dto.catalog.TableInfoResponse
import com.github.lambda.dto.query.*
import com.github.lambda.mapper.CatalogMapper
import com.github.lambda.mapper.QueryMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse

/**
 * Catalog REST API Controller
 *
 * Provides data discovery capabilities for browsing and searching table metadata.
 * Supports CLI commands: dli catalog list/search/get
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/catalog")
@CrossOrigin
@Validated
@Tag(name = "Catalog", description = "Data catalog browsing and search API")
class CatalogController(
    private val catalogService: CatalogService,
    private val catalogMapper: CatalogMapper,
    private val queryMetadataService: QueryMetadataService,
    private val queryMapper: QueryMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * List tables with optional filters
     *
     * GET /api/v1/catalog/tables
     * CLI: dli catalog list
     */
    @Operation(
        summary = "List tables",
        description =
            "List tables from the data catalog with optional filtering " +
                "by project, dataset, owner, team, or tags",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Validation error")
    @SwaggerApiResponse(responseCode = "502", description = "Catalog service error")
    @GetMapping("/tables")
    fun listTables(
        @Parameter(description = "Filter by project/catalog name")
        @RequestParam(required = false) project: String?,
        @Parameter(description = "Filter by dataset/schema name")
        @RequestParam(required = false) dataset: String?,
        @Parameter(description = "Filter by owner email")
        @RequestParam(required = false) owner: String?,
        @Parameter(description = "Filter by team (e.g., @data-eng)")
        @RequestParam(required = false) team: String?,
        @Parameter(description = "Comma-separated tags (AND condition)")
        @RequestParam(required = false) tags: String?,
        @Parameter(description = "Maximum results (1-500)")
        @RequestParam(defaultValue = "50")
        @Min(1)
        @Max(500) limit: Int,
        @Parameter(description = "Pagination offset")
        @RequestParam(defaultValue = "0")
        @Min(0) offset: Int,
    ): ResponseEntity<List<TableInfoResponse>> {
        logger.info {
            "GET /api/v1/catalog/tables - project: $project, dataset: $dataset, owner: $owner, " +
                "team: $team, tags: $tags, limit: $limit, offset: $offset"
        }

        val tagSet =
            tags
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet() ?: emptySet()

        val tables =
            catalogService.listTables(
                project = project,
                dataset = dataset,
                owner = owner,
                team = team,
                tags = tagSet,
                limit = limit,
                offset = offset,
            )

        return ResponseEntity.ok(catalogMapper.toTableInfoResponseList(tables))
    }

    /**
     * Search tables by keyword
     *
     * GET /api/v1/catalog/search
     * CLI: dli catalog search <keyword>
     */
    @Operation(
        summary = "Search tables",
        description = "Search tables by keyword across table names, column names, and descriptions",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Validation error - keyword too short")
    @SwaggerApiResponse(responseCode = "502", description = "Catalog service error")
    @GetMapping("/search")
    fun searchTables(
        @Parameter(description = "Search keyword (minimum 2 characters)", required = true)
        @RequestParam
        @Size(min = 2, message = "Keyword must be at least 2 characters") keyword: String,
        @Parameter(description = "Limit search to project")
        @RequestParam(required = false) project: String?,
        @Parameter(description = "Maximum results (1-100)")
        @RequestParam(defaultValue = "20")
        @Min(1)
        @Max(100) limit: Int,
    ): ResponseEntity<List<TableInfoResponse>> {
        logger.info { "GET /api/v1/catalog/search - keyword: $keyword, project: $project, limit: $limit" }

        val tables =
            catalogService.searchTables(
                keyword = keyword,
                project = project,
                limit = limit,
            )

        return ResponseEntity.ok(catalogMapper.toTableInfoResponseList(tables))
    }

    /**
     * Get table details
     *
     * GET /api/v1/catalog/tables/{table_ref}
     * CLI: dli catalog get <table_ref>
     */
    @Operation(
        summary = "Get table details",
        description = "Get detailed table information including schema, ownership, freshness, and quality metrics",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid table reference format")
    @SwaggerApiResponse(responseCode = "404", description = "Table not found")
    @SwaggerApiResponse(responseCode = "502", description = "Catalog service error")
    @GetMapping("/tables/{table_ref}")
    fun getTableDetail(
        @Parameter(description = "Fully qualified table reference (project.dataset.table)", required = true)
        @PathVariable("table_ref") tableRef: String,
        @Parameter(description = "Include sample data (PII-masked)")
        @RequestParam(name = "include_sample", defaultValue = "false") includeSample: Boolean,
    ): ResponseEntity<TableDetailResponse> {
        logger.info { "GET /api/v1/catalog/tables/$tableRef - includeSample: $includeSample" }

        val tableDetail =
            catalogService.getTableDetail(
                tableRef = tableRef,
                includeSample = includeSample,
            )

        return ResponseEntity.ok(catalogMapper.toTableDetailResponse(tableDetail))
    }

    /**
     * Get sample queries for a table
     *
     * GET /api/v1/catalog/tables/{table_ref}/queries
     * CLI: dli catalog get <table_ref> --queries
     */
    @Operation(
        summary = "Get sample queries",
        description = "Get sample queries associated with a table, sorted by popularity",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid table reference format")
    @SwaggerApiResponse(responseCode = "404", description = "Table not found")
    @GetMapping("/tables/{table_ref}/queries")
    fun getSampleQueries(
        @Parameter(description = "Fully qualified table reference (project.dataset.table)", required = true)
        @PathVariable("table_ref") tableRef: String,
        @Parameter(description = "Maximum queries to return (1-20)")
        @RequestParam(defaultValue = "5")
        @Min(1)
        @Max(20) limit: Int,
    ): ResponseEntity<List<SampleQueryResponse>> {
        logger.info { "GET /api/v1/catalog/tables/$tableRef/queries - limit: $limit" }

        val queries =
            catalogService.getSampleQueries(
                tableRef = tableRef,
                limit = limit,
            )

        return ResponseEntity.ok(catalogMapper.toSampleQueryResponseList(queries))
    }

    // === Query Metadata API Endpoints ===

    /**
     * List query execution history
     *
     * GET /api/v1/catalog/queries
     * CLI: dli query list
     */
    @Operation(
        summary = "List query executions",
        description = "List query execution history with filtering by scope, status, and date range",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "400", description = "Invalid date range or parameters")
    @SwaggerApiResponse(responseCode = "403", description = "Insufficient permissions for scope")
    @GetMapping("/queries")
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
            "GET /api/v1/catalog/queries - scope: $scope, status: $status, startDate: $startDate, endDate: $endDate, limit: $limit, offset: $offset"
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
     * GET /api/v1/catalog/queries/{query_id}
     * CLI: dli query show <query_id>
     */
    @Operation(
        summary = "Get query details",
        description = "Get detailed query execution information including execution plan and error details",
    )
    @SwaggerApiResponse(responseCode = "200", description = "Success")
    @SwaggerApiResponse(responseCode = "403", description = "Not authorized to view this query")
    @SwaggerApiResponse(responseCode = "404", description = "Query not found")
    @GetMapping("/queries/{query_id}")
    fun getQueryDetails(
        @Parameter(description = "Unique query identifier", required = true)
        @PathVariable("query_id") queryId: String,
    ): ResponseEntity<*> {
        logger.info { "GET /api/v1/catalog/queries/$queryId" }

        return try {
            val currentUser = getCurrentUser()
            val query = queryMetadataService.getQueryDetails(queryId, currentUser)

            if (query != null) {
                ResponseEntity.ok(queryMapper.toDetailDto(query))
            } else {
                ResponseEntity.notFound().build<Any>()
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to get query details for $queryId" }
            when (e.message?.contains("access denied", ignoreCase = true)) {
                true ->
                    ResponseEntity.status(403).body(
                        queryMapper.toAccessDeniedError(e.message ?: "Access denied"),
                    )
                else -> ResponseEntity.notFound().build<Any>()
            }
        }
    }

    /**
     * Cancel a running query
     *
     * POST /api/v1/catalog/queries/{query_id}/cancel
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
    @PostMapping("/queries/{query_id}/cancel")
    fun cancelQuery(
        @Parameter(description = "Unique query identifier", required = true)
        @PathVariable("query_id") queryId: String,
        @RequestBody(required = false) request: CancelQueryRequestDto?,
    ): ResponseEntity<*> {
        logger.info { "POST /api/v1/catalog/queries/$queryId/cancel - reason: ${request?.reason}" }

        return try {
            val currentUser = getCurrentUser()
            val command =
                CancelQueryCommand(
                    queryId = queryId,
                    reason = request?.reason,
                )

            val cancelledQuery = queryMetadataService.cancelQuery(command, currentUser)

            ResponseEntity.ok(queryMapper.toCancelQueryResponseDto(cancelledQuery))
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel query $queryId" }
            when {
                e.message?.contains("not found", ignoreCase = true) == true ->
                    ResponseEntity.notFound().build<Any>()
                e.message?.contains("access denied", ignoreCase = true) == true ->
                    ResponseEntity.status(403).body(
                        queryMapper.toAccessDeniedError(e.message ?: "Access denied"),
                    )
                e.message?.contains("not cancellable", ignoreCase = true) == true ->
                    ResponseEntity.status(409).body(
                        queryMapper.toQueryNotCancellableError(queryId, "COMPLETED"),
                    )
                else ->
                    ResponseEntity.badRequest().body(
                        queryMapper.toAccessDeniedError(e.message ?: "Unknown error"),
                    )
            }
        }
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
