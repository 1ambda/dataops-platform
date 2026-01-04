package com.github.lambda.domain.projection.execution

/**
 * 실행 정책 Projection
 * Service에서 Controller로 전달되는 실행 정책 정보
 */
data class ExecutionPolicyProjection(
    val maxQueryDurationSeconds: Int,
    val maxResultRows: Int,
    val maxResultSizeMb: Int,
    val allowedEngines: List<String>,
    val allowedFileTypes: List<String>,
    val maxFileSizeMb: Int,
    val rateLimits: RateLimitsProjection,
    val currentUsage: CurrentUsageProjection,
)

/**
 * Rate Limits Projection
 */
data class RateLimitsProjection(
    val queriesPerHour: Int,
    val queriesPerDay: Int,
)

/**
 * 현재 사용량 Projection
 */
data class CurrentUsageProjection(
    val queriesToday: Int,
    val queriesThisHour: Int,
)
