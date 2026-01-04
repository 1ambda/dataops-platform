package com.github.lambda.controller

import com.github.lambda.common.constant.CommonConstants
import com.github.lambda.domain.model.transpile.SqlDialect
import com.github.lambda.domain.service.TranspileService
import com.github.lambda.dto.transpile.TranspileResultDto
import com.github.lambda.dto.transpile.TranspileRulesDto
import com.github.lambda.mapper.TranspileMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.NotBlank
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

/**
 * Transpile API REST Controller
 *
 * Provides endpoints for SQL transpilation between dialects.
 * Supports CLI commands: dli metric transpile, dli dataset transpile
 */
@RestController
@RequestMapping("${CommonConstants.Api.V1_PATH}/transpile")
@CrossOrigin
@Validated
@Tag(name = "Transpile", description = "SQL transpilation API")
class TranspileController(
    private val transpileService: TranspileService,
    private val transpileMapper: TranspileMapper,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Get transpile rules
     *
     * GET /api/v1/transpile/rules
     * CLI: dli transpile rules (internal)
     */
    @Operation(
        summary = "Get transpile rules",
        description = "Get SQL transpile rules for CLI caching with optional filtering",
    )
    @GetMapping("/rules")
    fun getTranspileRules(
        @Parameter(description = "Rule version (latest if not specified)")
        @RequestParam(required = false, defaultValue = "latest") version: String,
        @Parameter(description = "Filter by source dialect")
        @RequestParam(required = false) fromDialect: String?,
        @Parameter(description = "Filter by target dialect")
        @RequestParam(required = false) toDialect: String?,
    ): ResponseEntity<TranspileRulesDto> {
        logger.info {
            "GET /api/v1/transpile/rules - version: $version, fromDialect: $fromDialect, toDialect: $toDialect"
        }

        val fromDialectEnum = fromDialect?.let { parseDialect(it) }
        val toDialectEnum = toDialect?.let { parseDialect(it) }

        val result =
            transpileService.getTranspileRules(
                version = version,
                fromDialect = fromDialectEnum,
                toDialect = toDialectEnum,
            )

        val response = transpileMapper.toTranspileRulesDto(result)

        return ResponseEntity
            .ok()
            .header("ETag", "\"${result.version}\"")
            .header("Cache-Control", "max-age=${result.metadata.cacheTtlSeconds}")
            .body(response)
    }

    /**
     * Transpile metric SQL
     *
     * GET /api/v1/transpile/metrics/{metricName}
     * CLI: dli metric transpile <name>
     */
    @Operation(
        summary = "Transpile metric SQL",
        description = "Get transpiled SQL for a registered metric",
    )
    @GetMapping("/metrics/{metricName}")
    fun transpileMetric(
        @Parameter(description = "Fully qualified metric name (catalog.schema.name)")
        @PathVariable
        @NotBlank metricName: String,
        @Parameter(description = "Target SQL dialect (trino, bigquery)", required = true)
        @RequestParam
        @NotBlank targetDialect: String,
        @Parameter(description = "Source SQL dialect (auto-detected if omitted)")
        @RequestParam(required = false) sourceDialect: String?,
        @Parameter(description = "Parameter substitution values (JSON encoded, future feature)")
        @RequestParam(required = false) parameters: String?,
    ): ResponseEntity<TranspileResultDto> {
        logger.info {
            "GET /api/v1/transpile/metrics/$metricName - targetDialect: $targetDialect, sourceDialect: $sourceDialect"
        }

        val targetDialectEnum = parseDialect(targetDialect)
        val sourceDialectEnum = sourceDialect?.let { parseDialect(it) }

        val result =
            transpileService.transpileMetric(
                metricName = metricName,
                targetDialect = targetDialectEnum,
                sourceDialect = sourceDialectEnum,
                // TODO: Parse parameters from JSON string when implemented
                parameters = emptyMap(),
            )

        val response = transpileMapper.toTranspileResultDto(result)

        return ResponseEntity.ok(response)
    }

    /**
     * Transpile dataset SQL
     *
     * GET /api/v1/transpile/datasets/{datasetName}
     * CLI: dli dataset transpile <name>
     */
    @Operation(
        summary = "Transpile dataset SQL",
        description = "Get transpiled SQL for a registered dataset",
    )
    @GetMapping("/datasets/{datasetName}")
    fun transpileDataset(
        @Parameter(description = "Fully qualified dataset name (catalog.schema.name)")
        @PathVariable
        @NotBlank datasetName: String,
        @Parameter(description = "Target SQL dialect (trino, bigquery)", required = true)
        @RequestParam
        @NotBlank targetDialect: String,
        @Parameter(description = "Source SQL dialect (auto-detected if omitted)")
        @RequestParam(required = false) sourceDialect: String?,
        @Parameter(description = "Parameter substitution values (JSON encoded, future feature)")
        @RequestParam(required = false) parameters: String?,
    ): ResponseEntity<TranspileResultDto> {
        logger.info {
            "GET /api/v1/transpile/datasets/$datasetName - targetDialect: $targetDialect, sourceDialect: $sourceDialect"
        }

        val targetDialectEnum = parseDialect(targetDialect)
        val sourceDialectEnum = sourceDialect?.let { parseDialect(it) }

        val result =
            transpileService.transpileDataset(
                datasetName = datasetName,
                targetDialect = targetDialectEnum,
                sourceDialect = sourceDialectEnum,
                // TODO: Parse parameters from JSON string when implemented
                parameters = emptyMap(),
            )

        val response = transpileMapper.toTranspileResultDto(result)

        return ResponseEntity.ok(response)
    }

    /**
     * Helper method to parse dialect string to enum
     */
    private fun parseDialect(dialect: String): SqlDialect =
        try {
            SqlDialect.valueOf(dialect.uppercase())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Unsupported dialect: '$dialect'. Supported dialects: ${SqlDialect.values().joinToString {
                    it.name
                        .lowercase()
                }}",
            )
        }
}
