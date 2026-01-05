package com.dataops.basecamp.dto.catalog

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * Table Info Response DTO
 *
 * Used for GET /api/v1/catalog/tables (list) and GET /api/v1/catalog/search (search)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableInfoResponse(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String?,
    val tags: List<String>,
    @JsonProperty("row_count")
    val rowCount: Long?,
    @JsonProperty("last_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val lastUpdated: Instant?,
    @JsonProperty("match_context")
    val matchContext: String?,
)

/**
 * Column Info Response DTO
 *
 * Used within TableDetailResponse
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ColumnInfoResponse(
    val name: String,
    @JsonProperty("data_type")
    val dataType: String,
    val description: String?,
    @JsonProperty("is_pii")
    val isPii: Boolean,
    @JsonProperty("fill_rate")
    val fillRate: Double?,
    @JsonProperty("distinct_count")
    val distinctCount: Long?,
)

/**
 * Table Ownership Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableOwnershipResponse(
    val owner: String,
    val team: String?,
    val stewards: List<String>,
    val consumers: List<String>,
)

/**
 * Table Freshness Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableFreshnessResponse(
    @JsonProperty("last_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val lastUpdated: Instant,
    @JsonProperty("avg_update_lag_hours")
    val avgUpdateLagHours: Double?,
    @JsonProperty("update_frequency")
    val updateFrequency: String?,
    @JsonProperty("is_stale")
    val isStale: Boolean,
    @JsonProperty("stale_threshold_hours")
    val staleThresholdHours: Int,
)

/**
 * Quality Test Result Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class QualityTestResultResponse(
    @JsonProperty("test_name")
    val testName: String,
    @JsonProperty("test_type")
    val testType: String,
    val status: String,
    @JsonProperty("failed_rows")
    val failedRows: Long,
)

/**
 * Table Quality Response DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableQualityResponse(
    val score: Int,
    @JsonProperty("total_tests")
    val totalTests: Int,
    @JsonProperty("passed_tests")
    val passedTests: Int,
    @JsonProperty("failed_tests")
    val failedTests: Int,
    val warnings: Int,
    @JsonProperty("recent_tests")
    val recentTests: List<QualityTestResultResponse>,
)

/**
 * Table Detail Response DTO
 *
 * Used for GET /api/v1/catalog/tables/{table_ref}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableDetailResponse(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    @JsonProperty("row_count")
    val rowCount: Long?,
    @JsonProperty("last_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val lastUpdated: Instant?,
    @JsonProperty("basecamp_url")
    val basecampUrl: String?,
    val columns: List<ColumnInfoResponse>,
    val ownership: TableOwnershipResponse?,
    val freshness: TableFreshnessResponse?,
    val quality: TableQualityResponse?,
    @JsonProperty("sample_data")
    val sampleData: List<Map<String, Any>>?,
)

/**
 * Sample Query Response DTO
 *
 * Used for GET /api/v1/catalog/tables/{table_ref}/queries
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SampleQueryResponse(
    val title: String,
    val sql: String,
    val author: String,
    @JsonProperty("run_count")
    val runCount: Int,
    @JsonProperty("last_run")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val lastRun: Instant?,
)

// === Request DTOs ===

/**
 * Request DTO for creating a catalog table
 *
 * Used for POST /api/v1/catalog/tables
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateCatalogTableRequest(
    val name: String,
    val engine: String,
    val owner: String,
    val team: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    @JsonProperty("row_count")
    val rowCount: Long? = null,
    @JsonProperty("last_updated")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    val lastUpdated: Instant? = null,
    @JsonProperty("basecamp_url")
    val basecampUrl: String? = null,
    val stewards: List<String>? = null,
    val consumers: List<String>? = null,
    @JsonProperty("avg_update_lag_hours")
    val avgUpdateLagHours: Double? = null,
    @JsonProperty("update_frequency")
    val updateFrequency: String? = null,
    @JsonProperty("stale_threshold_hours")
    val staleThresholdHours: Int? = null,
    @JsonProperty("quality_score")
    val qualityScore: Int? = null,
    val columns: List<CreateColumnRequest>? = null,
)

/**
 * Request DTO for creating a column
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateColumnRequest(
    val name: String,
    @JsonProperty("data_type")
    val dataType: String,
    val description: String? = null,
    @JsonProperty("is_pii")
    val isPii: Boolean? = null,
    @JsonProperty("fill_rate")
    val fillRate: Double? = null,
    @JsonProperty("distinct_count")
    val distinctCount: Long? = null,
    @JsonProperty("is_nullable")
    val isNullable: Boolean? = null,
    @JsonProperty("is_primary_key")
    val isPrimaryKey: Boolean? = null,
    @JsonProperty("is_partition_key")
    val isPartitionKey: Boolean? = null,
    @JsonProperty("is_clustering_key")
    val isClusteringKey: Boolean? = null,
    @JsonProperty("default_value")
    val defaultValue: String? = null,
)

/**
 * Request DTO for creating a sample query
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreateSampleQueryRequest(
    val title: String,
    val sql: String,
    val author: String,
    val description: String? = null,
)

/**
 * Response DTO for catalog table registration
 */
data class CatalogTableRegistrationResponse(
    val message: String,
    val name: String,
)
