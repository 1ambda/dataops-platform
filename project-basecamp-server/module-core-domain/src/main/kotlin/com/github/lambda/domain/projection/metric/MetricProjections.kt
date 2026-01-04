package com.github.lambda.domain.projection.metric

/**
 * Metric Execution Result Projection
 * Service에서 Controller로 전달되는 메트릭 실행 결과
 */
data class MetricExecutionProjection(
    val rows: List<Map<String, Any>>,
    val rowCount: Int,
    val durationSeconds: Double,
    val renderedSql: String,
)