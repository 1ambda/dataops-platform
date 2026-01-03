package com.github.lambda.domain.external

/**
 * Lineage parsing result
 *
 * Contains the parsed dependencies from a SQL query.
 *
 * @param success Whether parsing was successful
 * @param sourceTables Tables read by the SQL (inputs)
 * @param targetTables Tables written by the SQL (outputs) - usually from INSERT/CREATE statements
 * @param columnLineage Optional column-level lineage mapping
 * @param errorMessage Error message if parsing failed
 */
data class LineageResult(
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
        ) = LineageResult(
            success = true,
            sourceTables = sourceTables,
            targetTables = targetTables,
            columnLineage = columnLineage,
        )

        fun error(message: String) =
            LineageResult(
                success = false,
                errorMessage = message,
            )
    }
}

/**
 * Transpile rule for SQL transformation
 */
data class TranspileRule(
    val name: String,
    val pattern: String,
    val replacement: String,
)

/**
 * SQL transpilation result
 */
data class TranspileResult(
    val success: Boolean,
    val transpiledSql: String,
    val appliedTransformations: List<AppliedTransformation> = emptyList(),
    val warnings: List<TranspileWarning> = emptyList(),
    val parseTimeMs: Long = 0,
    val transpileTimeMs: Long = 0,
    val errorMessage: String? = null,
) {
    companion object {
        fun success(
            transpiledSql: String,
            appliedTransformations: List<AppliedTransformation> = emptyList(),
            warnings: List<TranspileWarning> = emptyList(),
            parseTimeMs: Long = 0,
            transpileTimeMs: Long = 0,
        ) = TranspileResult(
            success = true,
            transpiledSql = transpiledSql,
            appliedTransformations = appliedTransformations,
            warnings = warnings,
            parseTimeMs = parseTimeMs,
            transpileTimeMs = transpileTimeMs,
        )

        fun error(message: String) =
            TranspileResult(
                success = false,
                transpiledSql = "",
                errorMessage = message,
            )
    }
}

/**
 * Applied transformation information
 */
data class AppliedTransformation(
    val type: String,
    val name: String?,
    val from: String?,
    val to: String?,
)

/**
 * Transpile warning information
 */
data class TranspileWarning(
    val type: String,
    val message: String,
    val line: Int?,
    val column: Int?,
)
