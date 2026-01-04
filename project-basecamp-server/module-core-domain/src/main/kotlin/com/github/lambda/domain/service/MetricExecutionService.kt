package com.github.lambda.domain.service

import com.github.lambda.domain.projection.metric.MetricExecutionProjection
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.system.measureTimeMillis

/**
 * Metric Execution Service
 *
 * Handles metric SQL execution.
 * Currently mocked - will be integrated with query engine in future.
 */
@Service
@Transactional(readOnly = true)
class MetricExecutionService(
    private val metricService: MetricService,
) {
    /**
     * Execute a metric with parameters
     *
     * @param metricName Fully qualified metric name
     * @param parameters SQL template parameters
     * @param limit Maximum rows to return (optional)
     * @param timeout Execution timeout in seconds (default: 300)
     * @return Execution result with rows and metadata
     * @throws MetricNotFoundException if metric not found
     * @throws MetricExecutionException if execution fails
     */
    fun executeMetric(
        metricName: String,
        parameters: Map<String, Any> = emptyMap(),
        limit: Int? = null,
        timeout: Int = 300,
    ): MetricExecutionProjection {
        val metric = metricService.getMetricOrThrow(metricName)
        val renderedSql = renderSqlWithParameters(metric.sql, parameters)

        // Mock execution - returns sample data
        val (rows, durationMs) = mockExecute(renderedSql, limit)

        return MetricExecutionProjection(
            rows = rows,
            rowCount = rows.size,
            durationSeconds = durationMs / 1000.0,
            renderedSql = renderedSql,
        )
    }

    /**
     * Render SQL with parameters
     *
     * Replaces {{param}} placeholders with actual values.
     * Strings are quoted, other types are converted to string.
     */
    private fun renderSqlWithParameters(
        sql: String,
        parameters: Map<String, Any>,
    ): String {
        var result = sql
        parameters.forEach { (key, value) ->
            val placeholder = "{{$key}}"
            val replacement =
                when (value) {
                    is String -> "'${value.replace("'", "''")}'"
                    is Number -> value.toString()
                    is Boolean -> value.toString()
                    else -> "'${value.toString().replace("'", "''")}'"
                }
            result = result.replace(placeholder, replacement)
        }
        return result
    }

    /**
     * Mock execution - returns sample data
     *
     * TODO: Replace with actual query engine integration
     */
    private fun mockExecute(
        sql: String,
        limit: Int?,
    ): Pair<List<Map<String, Any>>, Long> {
        val rows = mutableListOf<Map<String, Any>>()
        val durationMs =
            measureTimeMillis {
                // Generate mock data based on the SQL
                val mockRowCount = limit?.coerceIn(1, 100) ?: 10

                for (i in 1..mockRowCount) {
                    rows.add(
                        mapOf(
                            "id" to i,
                            "value" to (i * 100.0),
                            "name" to "sample_$i",
                            "timestamp" to
                                java.time.LocalDateTime
                                    .now()
                                    .toString(),
                        ),
                    )
                }

                // Simulate some processing time
                Thread.sleep(50)
            }

        return Pair(rows, durationMs)
    }

    /**
     * Dry run - render SQL without executing
     *
     * @param metricName Fully qualified metric name
     * @param parameters SQL template parameters
     * @return Rendered SQL
     */
    fun dryRun(
        metricName: String,
        parameters: Map<String, Any> = emptyMap(),
    ): String {
        val metric = metricService.getMetricOrThrow(metricName)
        return renderSqlWithParameters(metric.sql, parameters)
    }
}
