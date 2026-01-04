package com.github.lambda.domain.projection.quality

import com.github.lambda.common.enums.TestStatus

/**
 * Mock quality test execution result projection
 * Used for service-to-controller responses in development/testing
 * In production, this would be replaced with actual query engine response
 */
data class MockTestResultProjection(
    val status: TestStatus,
    val failedRows: Long,
    val totalRows: Long,
    val executionTimeSeconds: Double,
    val errorMessage: String? = null,
)
