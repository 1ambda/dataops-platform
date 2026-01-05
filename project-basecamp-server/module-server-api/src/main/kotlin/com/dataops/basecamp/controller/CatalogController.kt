package com.dataops.basecamp.controller

import com.dataops.basecamp.common.constant.CommonConstants
import com.dataops.basecamp.domain.service.CatalogService
import com.dataops.basecamp.dto.catalog.SampleQueryResponse
import com.dataops.basecamp.dto.catalog.TableDetailResponse
import com.dataops.basecamp.dto.catalog.TableInfoResponse
import com.dataops.basecamp.mapper.CatalogMapper
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
}
