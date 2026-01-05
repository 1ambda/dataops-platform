package com.dataops.basecamp.domain.external.parser

/**
 * Lineage parsing result response
 *
 * Contains the parsed dependencies from a SQL query.
 *
 * @param success Whether parsing was successful
 * @param sourceTables Tables read by the SQL (inputs)
 * @param targetTables Tables written by the SQL (outputs) - usually from INSERT/CREATE statements
 * @param columnLineage Optional column-level lineage mapping
 * @param errorMessage Error message if parsing failed
 */
data class LineageParseResponse(
    val success: Boolean,
    val sourceTables: List<String> = emptyList(),
    val targetTables: List<String> = emptyList(),
    val columnLineage: Map<String, List<String>> = emptyMap(), // target_column -> [source_columns]
    val errorMessage: String? = null,
) {
    companion object {
        fun success(
            sourceTables: List<String>,
            targetTables: List<String> = emptyList(),
            columnLineage: Map<String, List<String>> = emptyMap(),
        ) = LineageParseResponse(
            success = true,
            sourceTables = sourceTables,
            targetTables = targetTables,
            columnLineage = columnLineage,
        )

        fun error(message: String) =
            LineageParseResponse(
                success = false,
                errorMessage = message,
            )
    }
}

/**
 * Transpile rule request for SQL transformation
 */
data class TranspileRuleRequest(
    val name: String,
    val pattern: String,
    val replacement: String,
)

/**
 * SQL transpilation result response
 */
data class TranspileResponse(
    val success: Boolean,
    val transpiledSql: String,
    val appliedTransformations: List<AppliedTransformationResponse> = emptyList(),
    val warnings: List<TranspileWarningResponse> = emptyList(),
    val parseTimeMs: Long = 0,
    val transpileTimeMs: Long = 0,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(
            transpiledSql: String,
            appliedTransformations: List<AppliedTransformationResponse> = emptyList(),
            warnings: List<TranspileWarningResponse> = emptyList(),
            parseTimeMs: Long = 0,
            transpileTimeMs: Long = 0,
        ) = TranspileResponse(
            success = true,
            transpiledSql = transpiledSql,
            appliedTransformations = appliedTransformations,
            warnings = warnings,
            parseTimeMs = parseTimeMs,
            transpileTimeMs = transpileTimeMs,
        )

        fun error(message: String) =
            TranspileResponse(
                success = false,
                transpiledSql = "",
                errorMessage = message,
            )
    }
}

/**
 * Applied transformation information response
 */
data class AppliedTransformationResponse(
    val type: String,
    val name: String?,
    val from: String?,
    val to: String?,
)

/**
 * Transpile warning information response
 */
data class TranspileWarningResponse(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)
