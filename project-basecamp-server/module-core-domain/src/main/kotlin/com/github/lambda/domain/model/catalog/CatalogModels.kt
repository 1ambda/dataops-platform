package com.github.lambda.domain.model.catalog

import java.time.Instant

/**
 * Table summary information for list/search results
 */
data class TableInfo(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String? = null,
    val tags: Set<String> = emptySet(),
    val rowCount: Long? = null,
    val lastUpdated: Instant? = null,
    val matchContext: String? = null,
)

/**
 * Column metadata information
 */
data class ColumnInfo(
    val name: String,
    val dataType: String,
    val description: String? = null,
    val isPii: Boolean = false,
    val fillRate: Double? = null,
    val distinctCount: Long? = null,
)

/**
 * Table ownership information
 */
data class TableOwnership(
    val owner: String,
    val team: String? = null,
    val stewards: List<String> = emptyList(),
    val consumers: List<String> = emptyList(),
)

/**
 * Table freshness information
 */
data class TableFreshness(
    val lastUpdated: Instant,
    val avgUpdateLagHours: Double? = null,
    val updateFrequency: String? = null,
    val isStale: Boolean = false,
    val staleThresholdHours: Int = 24,
)

/**
 * Table quality information
 */
data class TableQuality(
    val score: Int,
    val totalTests: Int,
    val passedTests: Int,
    val failedTests: Int,
    val warnings: Int = 0,
    val recentTests: List<QualityTestResult> = emptyList(),
)

/**
 * Quality test result
 */
data class QualityTestResult(
    val testName: String,
    val testType: String,
    val status: TestStatus,
    val failedRows: Long = 0,
)

/**
 * Test status enumeration
 */
enum class TestStatus {
    PASS,
    FAIL,
    WARNING,
    SKIPPED,
}

/**
 * Detailed table information including columns, ownership, freshness, and quality
 */
data class TableDetail(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String? = null,
    val description: String? = null,
    val tags: Set<String> = emptySet(),
    val rowCount: Long? = null,
    val lastUpdated: Instant? = null,
    val basecampUrl: String? = null,
    val columns: List<ColumnInfo> = emptyList(),
    val ownership: TableOwnership? = null,
    val freshness: TableFreshness? = null,
    val quality: TableQuality? = null,
    val sampleData: List<Map<String, Any>> = emptyList(),
)

/**
 * Sample query associated with a table
 */
data class SampleQuery(
    val title: String,
    val sql: String,
    val author: String,
    val runCount: Int,
    val lastRun: Instant? = null,
)

/**
 * Catalog filter parameters
 */
data class CatalogFilters(
    val project: String? = null,
    val dataset: String? = null,
    val owner: String? = null,
    val team: String? = null,
    val tags: Set<String> = emptySet(),
    val limit: Int = 50,
    val offset: Int = 0,
)
