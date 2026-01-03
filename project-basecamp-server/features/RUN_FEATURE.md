# Run API Feature Specification

> **Version:** 1.0.0 | **Status:** ✅ Implemented | **Priority:** P3 Low
> **CLI Commands:** `dli run` (ad-hoc SQL execution) | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Date:** 2026-01-03 | **Actual Effort:** 3 days
>
> **📦 Data Source:** External API (Query Engine 실행)
> **External:** BigQuery/Trino Query Engine | **Entities:** AdHocExecutionEntity, UserExecutionQuotaEntity
>
> **📖 Implementation Details:** See [`RUN_RELEASE.md`](./RUN_RELEASE.md)

---

## 1. Overview

### 1.1 Purpose

The Run API provides ad-hoc SQL execution capabilities with execution policies, rate limiting, and result download functionality. This enables the CLI tool (`dli run`) to execute arbitrary SQL files against query engines with proper governance and resource management.

### 1.2 Scope

| Feature | CLI Command | API Endpoint | Status |
|---------|-------------|--------------|--------|
| Get Execution Policy | `dli run` (pre-validation) | `GET /api/v1/run/policy` | ✅ Complete |
| Execute SQL | `dli run <file.sql>` | `POST /api/v1/run/execute` | ✅ Complete |
| Download Results | `dli run --download` | `GET /api/v1/run/results/{queryId}/download` | ✅ Complete |

### 1.3 Implementation Notes

> **Implementation Completed:** 2026-01-03
>
> **Key Implementation Decisions:**
> - `AdHocExecutionService` - Concrete class with Clock injection for testability
> - `ExecutionPolicyService` - Rate limiting with sliding window (hourly + daily)
> - `ResultStorageService` - In-memory storage with CSV support (Parquet deferred to MVP+1)
> - `MockQueryEngineClient` - Simulates BigQuery/Trino responses for development
> - `QueryIdGenerator` - Injectable for deterministic test IDs
>
> **Deferred to MVP+1:**
> - Parquet download format
> - Real BigQuery/Trino client integration
> - S3/GCS result storage
> - Async execution with polling

### 1.4 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Rate Limiting | Per-user, sliding window | Prevent resource abuse |
| Result Storage | Temporary with expiration | Balance storage costs vs user convenience |
| Download Formats | CSV, Parquet | Cover common use cases |
| Execution Timeout | Configurable, max 30 min | Prevent runaway queries |

---

## 2. CLI Command Mapping

### 2.1 Command to API Mapping

| CLI Command | HTTP Method | Endpoint | Description |
|-------------|-------------|----------|-------------|
| `dli run --dry-run` | POST | `/api/v1/run/execute` | Validate SQL without execution |
| `dli run <file.sql>` | POST | `/api/v1/run/execute` | Execute SQL and return results |
| `dli run --download csv` | POST | `/api/v1/run/execute` | Execute and get download URL |

### 2.2 CLI Options to Request Parameters

```bash
# Basic execution
dli run query.sql --engine bigquery

# Maps to:
POST /api/v1/run/execute
{
  "sql": "<contents of query.sql>",
  "engine": "bigquery",
  "parameters": {},
  "download_format": null,
  "dry_run": false
}

# Dry run with parameters
dli run query.sql --dry-run --param date=2026-01-01 --param region=US

# Maps to:
POST /api/v1/run/execute
{
  "sql": "<contents of query.sql>",
  "engine": "bigquery",
  "parameters": {"date": "2026-01-01", "region": "US"},
  "download_format": null,
  "dry_run": true
}

# Execute with download
dli run query.sql --download csv --engine trino

# Maps to:
POST /api/v1/run/execute
{
  "sql": "<contents of query.sql>",
  "engine": "trino",
  "parameters": {},
  "download_format": "csv",
  "dry_run": false
}
```

### 2.3 Pre-execution Policy Check

The CLI should call the policy endpoint before execution to validate:
- User has not exceeded rate limits
- Requested engine is allowed
- File size is within limits

```bash
# CLI internally calls:
GET /api/v1/run/policy

# Response includes current usage:
{
  "rate_limits": {"queries_per_hour": 50, "queries_per_day": 200},
  "current_usage": {"queries_today": 12, "queries_this_hour": 3}
}
```

---

## 3. API Specifications

### 3.1 Get Execution Policy

#### `GET /api/v1/run/policy`

**Purpose:** Get ad-hoc execution policy for `dli run` pre-validation

**Request:**
```http
GET /api/v1/run/policy
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "max_query_duration_seconds": 1800,
  "max_result_rows": 10000,
  "max_result_size_mb": 100,
  "allowed_engines": ["bigquery", "trino"],
  "allowed_file_types": [".sql", ".yaml"],
  "max_file_size_mb": 10,
  "rate_limits": {
    "queries_per_hour": 50,
    "queries_per_day": 200
  },
  "current_usage": {
    "queries_today": 12,
    "queries_this_hour": 3
  }
}
```

**Response Fields:**

| Field | Type | Description |
|-------|------|-------------|
| `max_query_duration_seconds` | int | Maximum query execution time (default: 1800 = 30 min) |
| `max_result_rows` | int | Maximum rows returned |
| `max_result_size_mb` | int | Maximum result size for download |
| `allowed_engines` | array | List of allowed query engines |
| `allowed_file_types` | array | Allowed input file extensions |
| `max_file_size_mb` | int | Maximum SQL file size |
| `rate_limits` | object | Rate limit configuration |
| `current_usage` | object | User's current usage statistics |

---

### 3.2 Execute SQL

#### `POST /api/v1/run/execute`

**Purpose:** Execute ad-hoc SQL file for `dli run`

**Request:**
```http
POST /api/v1/run/execute
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "sql": "SELECT user_id, COUNT(*) FROM iceberg.analytics.users GROUP BY 1 LIMIT 100",
  "engine": "bigquery",
  "parameters": {
    "date": "2026-01-01",
    "region": "US"
  },
  "download_format": "csv",
  "dry_run": false
}
```

**Request Body:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `sql` | string | Yes | - | SQL query to execute |
| `engine` | string | No | `bigquery` | Query engine (`bigquery`, `trino`) |
| `parameters` | object | No | `{}` | Template parameters for SQL |
| `download_format` | string | No | `null` | Result format (`csv`, `parquet`, `null`) |
| `dry_run` | bool | No | `false` | Validate only without execution |

**Response (200 OK - Execution Complete):**
```json
{
  "query_id": "adhoc_20260101_100000_xyz789",
  "status": "COMPLETED",
  "execution_time_seconds": 5.2,
  "rows_returned": 100,
  "bytes_scanned": "500 MB",
  "cost_usd": 0.0025,
  "download_urls": {
    "csv": "https://basecamp.example.com/api/v1/run/results/adhoc_20260101_100000_xyz789/download?format=csv&token=temp_token_123",
    "parquet": "https://basecamp.example.com/api/v1/run/results/adhoc_20260101_100000_xyz789/download?format=parquet&token=temp_token_123"
  },
  "expires_at": "2026-01-01T18:00:00Z",
  "rendered_sql": "SELECT user_id, COUNT(*) FROM iceberg.analytics.users WHERE region = 'US' GROUP BY 1 LIMIT 100"
}
```

**Response (200 OK - Dry Run):**
```json
{
  "query_id": null,
  "status": "VALIDATED",
  "execution_time_seconds": 0.1,
  "rows_returned": 0,
  "bytes_scanned": null,
  "cost_usd": null,
  "download_urls": {},
  "expires_at": null,
  "rendered_sql": "SELECT user_id, COUNT(*) FROM iceberg.analytics.users WHERE region = 'US' GROUP BY 1 LIMIT 100"
}
```

**Response (400 Bad Request - Invalid SQL):**
```json
{
  "error": {
    "code": "INVALID_SQL",
    "message": "SQL syntax error at line 1, column 15",
    "details": {
      "sql_error": "Unrecognized keyword 'SELET'",
      "suggestion": "Did you mean 'SELECT'?"
    }
  }
}
```

**Response (408 Request Timeout):**
```json
{
  "error": {
    "code": "QUERY_EXECUTION_TIMEOUT",
    "message": "Query execution timed out after 1800 seconds",
    "details": {
      "query_id": "adhoc_20260101_100000_xyz789",
      "timeout_seconds": 1800
    }
  }
}
```

**Response (413 Payload Too Large):**
```json
{
  "error": {
    "code": "RESULT_SIZE_LIMIT_EXCEEDED",
    "message": "Query result size exceeds limit of 100 MB",
    "details": {
      "result_size_mb": 150,
      "limit_mb": 100,
      "suggestion": "Add LIMIT clause or filter data"
    }
  }
}
```

**Response (429 Too Many Requests):**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded: 50 queries per hour",
    "details": {
      "limit_type": "queries_per_hour",
      "limit": 50,
      "current_usage": 50,
      "reset_at": "2026-01-01T11:00:00Z"
    }
  }
}
```

---

## 4. Domain Model

### 4.1 Entities

#### AdHocExecutionEntity

```kotlin
@Entity
@Table(name = "adhoc_executions")
class AdHocExecutionEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "query_id", nullable = false, unique = true)
    val queryId: String,

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "sql_query", nullable = false, length = 100000)
    val sqlQuery: String,

    @Column(name = "rendered_sql", nullable = false, length = 100000)
    val renderedSql: String,

    @Column(nullable = false)
    val engine: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ExecutionStatus,

    @Column(name = "rows_returned")
    val rowsReturned: Int? = null,

    @Column(name = "bytes_scanned")
    val bytesScanned: Long? = null,

    @Column(name = "cost_usd")
    val costUsd: BigDecimal? = null,

    @Column(name = "execution_time_seconds")
    val executionTimeSeconds: Double? = null,

    @Column(name = "result_path")
    val resultPath: String? = null,

    @Column(name = "error_message", length = 5000)
    val errorMessage: String? = null,

    @Column(name = "expires_at")
    val expiresAt: LocalDateTime? = null,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
)

enum class ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT,
    CANCELLED,
    VALIDATED,
}
```

#### UserExecutionQuotaEntity

```kotlin
@Entity
@Table(name = "user_execution_quotas")
class UserExecutionQuotaEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(name = "user_id", nullable = false)
    val userId: String,

    @Column(name = "queries_today", nullable = false)
    var queriesToday: Int = 0,

    @Column(name = "queries_this_hour", nullable = false)
    var queriesThisHour: Int = 0,

    @Column(name = "last_query_date", nullable = false)
    var lastQueryDate: LocalDate = LocalDate.now(),

    @Column(name = "last_query_hour", nullable = false)
    var lastQueryHour: Int = LocalDateTime.now().hour,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
```

### 4.2 DTOs

#### ExecutionPolicyDto

```kotlin
data class ExecutionPolicyDto(
    val maxQueryDurationSeconds: Int,
    val maxResultRows: Int,
    val maxResultSizeMb: Int,
    val allowedEngines: List<String>,
    val allowedFileTypes: List<String>,
    val maxFileSizeMb: Int,
    val rateLimits: RateLimitsDto,
    val currentUsage: CurrentUsageDto,
)

data class RateLimitsDto(
    val queriesPerHour: Int,
    val queriesPerDay: Int,
)

data class CurrentUsageDto(
    val queriesToday: Int,
    val queriesThisHour: Int,
)
```

#### ExecuteSqlRequest

```kotlin
data class ExecuteSqlRequest(
    @field:NotBlank
    @field:Size(max = 100000)
    val sql: String,

    @field:Pattern(regexp = "^(bigquery|trino)$")
    val engine: String = "bigquery",

    val parameters: Map<String, Any> = emptyMap(),

    @field:Pattern(regexp = "^(csv|parquet)?$")
    val downloadFormat: String? = null,

    val dryRun: Boolean = false,
)
```

#### ExecutionResultDto

```kotlin
data class ExecutionResultDto(
    val queryId: String?,
    val status: String,
    val executionTimeSeconds: Double,
    val rowsReturned: Int,
    val bytesScanned: String?,
    val costUsd: BigDecimal?,
    val downloadUrls: Map<String, String>,
    val expiresAt: String?,
    val renderedSql: String,
)
```

### 4.3 Mapper

```kotlin
object AdHocExecutionMapper {
    fun toResultDto(entity: AdHocExecutionEntity, downloadUrls: Map<String, String>): ExecutionResultDto =
        ExecutionResultDto(
            queryId = entity.queryId,
            status = entity.status.name,
            executionTimeSeconds = entity.executionTimeSeconds ?: 0.0,
            rowsReturned = entity.rowsReturned ?: 0,
            bytesScanned = entity.bytesScanned?.let { formatBytes(it) },
            costUsd = entity.costUsd,
            downloadUrls = downloadUrls,
            expiresAt = entity.expiresAt?.toString(),
            renderedSql = entity.renderedSql,
        )

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1_073_741_824 -> "${bytes / 1_073_741_824.0} GB"
            bytes >= 1_048_576 -> "${bytes / 1_048_576.0} MB"
            bytes >= 1024 -> "${bytes / 1024.0} KB"
            else -> "$bytes B"
        }
    }
}
```

---

## 5. Execution Policy Service

### 5.1 Configuration

```kotlin
@ConfigurationProperties(prefix = "basecamp.run")
data class RunExecutionProperties(
    val maxQueryDurationSeconds: Int = 1800,
    val maxResultRows: Int = 10000,
    val maxResultSizeMb: Int = 100,
    val allowedEngines: List<String> = listOf("bigquery", "trino"),
    val allowedFileTypes: List<String> = listOf(".sql", ".yaml"),
    val maxFileSizeMb: Int = 10,
    val rateLimits: RateLimitsProperties = RateLimitsProperties(),
    val resultExpirationHours: Int = 8,
)

data class RateLimitsProperties(
    val queriesPerHour: Int = 50,
    val queriesPerDay: Int = 200,
)
```

### 5.2 Execution Policy Service

```kotlin
@Service
class ExecutionPolicyService(
    private val properties: RunExecutionProperties,
    private val quotaRepository: UserExecutionQuotaRepository,
) {
    fun getPolicy(userId: String): ExecutionPolicyDto {
        val quota = getOrCreateQuota(userId)
        refreshQuotaIfNeeded(quota)

        return ExecutionPolicyDto(
            maxQueryDurationSeconds = properties.maxQueryDurationSeconds,
            maxResultRows = properties.maxResultRows,
            maxResultSizeMb = properties.maxResultSizeMb,
            allowedEngines = properties.allowedEngines,
            allowedFileTypes = properties.allowedFileTypes,
            maxFileSizeMb = properties.maxFileSizeMb,
            rateLimits = RateLimitsDto(
                queriesPerHour = properties.rateLimits.queriesPerHour,
                queriesPerDay = properties.rateLimits.queriesPerDay,
            ),
            currentUsage = CurrentUsageDto(
                queriesToday = quota.queriesToday,
                queriesThisHour = quota.queriesThisHour,
            ),
        )
    }

    fun validateExecution(userId: String, request: ExecuteSqlRequest) {
        val quota = getOrCreateQuota(userId)
        refreshQuotaIfNeeded(quota)

        // Check rate limits
        if (quota.queriesThisHour >= properties.rateLimits.queriesPerHour) {
            throw RateLimitExceededException(
                limitType = "queries_per_hour",
                limit = properties.rateLimits.queriesPerHour,
                currentUsage = quota.queriesThisHour,
            )
        }

        if (quota.queriesToday >= properties.rateLimits.queriesPerDay) {
            throw RateLimitExceededException(
                limitType = "queries_per_day",
                limit = properties.rateLimits.queriesPerDay,
                currentUsage = quota.queriesToday,
            )
        }

        // Validate engine
        if (request.engine !in properties.allowedEngines) {
            throw UnsupportedEngineException(request.engine, properties.allowedEngines)
        }

        // Validate SQL size
        val sqlSizeBytes = request.sql.toByteArray().size
        val maxSizeBytes = properties.maxFileSizeMb * 1024 * 1024
        if (sqlSizeBytes > maxSizeBytes) {
            throw QueryTooLargeException(sqlSizeBytes, maxSizeBytes)
        }
    }

    fun incrementUsage(userId: String) {
        val quota = getOrCreateQuota(userId)
        refreshQuotaIfNeeded(quota)
        quota.queriesThisHour++
        quota.queriesToday++
        quotaRepository.save(quota)
    }

    private fun getOrCreateQuota(userId: String): UserExecutionQuotaEntity {
        return quotaRepository.findByUserId(userId)
            ?: quotaRepository.save(UserExecutionQuotaEntity(userId = userId))
    }

    private fun refreshQuotaIfNeeded(quota: UserExecutionQuotaEntity) {
        val now = LocalDateTime.now()

        // Reset daily counter
        if (quota.lastQueryDate != now.toLocalDate()) {
            quota.queriesToday = 0
            quota.lastQueryDate = now.toLocalDate()
        }

        // Reset hourly counter
        if (quota.lastQueryHour != now.hour) {
            quota.queriesThisHour = 0
            quota.lastQueryHour = now.hour
        }
    }
}
```

### 5.3 Custom Exceptions

```kotlin
class RateLimitExceededException(
    val limitType: String,
    val limit: Int,
    val currentUsage: Int,
) : RuntimeException("Rate limit exceeded: $limit $limitType")

class UnsupportedEngineException(
    val engine: String,
    val allowedEngines: List<String>,
) : RuntimeException("Engine '$engine' not allowed. Allowed: ${allowedEngines.joinToString()}")

class QueryTooLargeException(
    val actualSizeBytes: Int,
    val maxSizeBytes: Int,
) : RuntimeException("SQL query size ${actualSizeBytes}B exceeds limit ${maxSizeBytes}B")

class QueryExecutionTimeoutException(
    val queryId: String,
    val timeoutSeconds: Int,
) : RuntimeException("Query execution timed out after $timeoutSeconds seconds")

class ResultSizeLimitExceededException(
    val resultSizeMb: Int,
    val limitMb: Int,
) : RuntimeException("Result size ${resultSizeMb}MB exceeds limit ${limitMb}MB")
```

---

## 6. Result Storage & Download

### 6.1 Result Storage Service

```kotlin
@Service
class ResultStorageService(
    private val properties: RunExecutionProperties,
    private val storageClient: StorageClient,  // S3/GCS abstraction
) {
    fun storeResults(
        queryId: String,
        rows: List<Map<String, Any>>,
        format: String?,
    ): Map<String, String> {
        if (format == null || rows.isEmpty()) {
            return emptyMap()
        }

        val downloadUrls = mutableMapOf<String, String>()
        val expiresAt = LocalDateTime.now().plusHours(properties.resultExpirationHours.toLong())

        // Always generate both formats if download is requested
        listOf("csv", "parquet").forEach { fmt ->
            val content = when (fmt) {
                "csv" -> convertToCsv(rows)
                "parquet" -> convertToParquet(rows)
                else -> throw IllegalArgumentException("Unsupported format: $fmt")
            }

            val path = "adhoc-results/$queryId/result.$fmt"
            storageClient.upload(path, content, expiresAt)

            val token = generateDownloadToken(queryId, fmt)
            downloadUrls[fmt] = buildDownloadUrl(queryId, fmt, token)
        }

        return downloadUrls
    }

    fun getResultForDownload(queryId: String, format: String, token: String): ByteArray {
        validateDownloadToken(queryId, format, token)

        val path = "adhoc-results/$queryId/result.$format"
        return storageClient.download(path)
            ?: throw ResultNotFoundException(queryId)
    }

    private fun convertToCsv(rows: List<Map<String, Any>>): ByteArray {
        if (rows.isEmpty()) return ByteArray(0)

        val headers = rows.first().keys.toList()
        val sb = StringBuilder()

        // Header row
        sb.appendLine(headers.joinToString(",") { escapeCsv(it) })

        // Data rows
        rows.forEach { row ->
            sb.appendLine(headers.map { row[it] }.joinToString(",") { escapeCsv(it.toString()) })
        }

        return sb.toString().toByteArray()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun convertToParquet(rows: List<Map<String, Any>>): ByteArray {
        // Use Apache Parquet library for conversion
        // Implementation details omitted for brevity
        TODO("Implement Parquet conversion")
    }

    private fun generateDownloadToken(queryId: String, format: String): String {
        // Generate time-limited signed token
        val payload = "$queryId:$format:${System.currentTimeMillis()}"
        return Base64.getEncoder().encodeToString(payload.toByteArray())
    }

    private fun validateDownloadToken(queryId: String, format: String, token: String) {
        // Validate token signature and expiration
        // Implementation details omitted for brevity
    }

    private fun buildDownloadUrl(queryId: String, format: String, token: String): String {
        return "/api/v1/run/results/$queryId/download?format=$format&token=$token"
    }
}

class ResultNotFoundException(val queryId: String) :
    RuntimeException("Result not found for query: $queryId")
```

### 6.2 Download Endpoint

```kotlin
@RestController
@RequestMapping("/api/v1/run")
class RunController(
    private val adHocExecutionService: AdHocExecutionService,
    private val resultStorageService: ResultStorageService,
    private val executionPolicyService: ExecutionPolicyService,
) {
    @GetMapping("/policy")
    fun getPolicy(authentication: Authentication): ResponseEntity<ExecutionPolicyDto> {
        val userId = authentication.name
        val policy = executionPolicyService.getPolicy(userId)
        return ResponseEntity.ok(policy)
    }

    @PostMapping("/execute")
    fun executeSQL(
        @Valid @RequestBody request: ExecuteSqlRequest,
        authentication: Authentication,
    ): ResponseEntity<ExecutionResultDto> {
        val userId = authentication.name
        val result = adHocExecutionService.executeSQL(userId, request)
        return ResponseEntity.ok(result)
    }

    @GetMapping("/results/{queryId}/download")
    fun downloadResult(
        @PathVariable queryId: String,
        @RequestParam format: String,
        @RequestParam token: String,
    ): ResponseEntity<ByteArray> {
        val content = resultStorageService.getResultForDownload(queryId, format, token)

        val contentType = when (format) {
            "csv" -> "text/csv"
            "parquet" -> "application/octet-stream"
            else -> "application/octet-stream"
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"result.$format\"")
            .body(content)
    }
}
```

---

## 7. Ad-Hoc Execution Service

### 7.1 Service Implementation

```kotlin
@Service
class AdHocExecutionService(
    private val executionPolicyService: ExecutionPolicyService,
    private val resultStorageService: ResultStorageService,
    private val queryEngineClient: QueryEngineClient,
    private val executionRepository: AdHocExecutionRepository,
    private val properties: RunExecutionProperties,
) {
    fun executeSQL(userId: String, request: ExecuteSqlRequest): ExecutionResultDto {
        // 1. Validate execution policy
        executionPolicyService.validateExecution(userId, request)

        // 2. Render SQL with parameters
        val renderedSql = renderSqlWithParameters(request.sql, request.parameters)

        // 3. Handle dry run
        if (request.dryRun) {
            queryEngineClient.validateSQL(renderedSql, request.engine)
            return ExecutionResultDto(
                queryId = null,
                status = "VALIDATED",
                executionTimeSeconds = 0.1,
                rowsReturned = 0,
                bytesScanned = null,
                costUsd = null,
                downloadUrls = emptyMap(),
                expiresAt = null,
                renderedSql = renderedSql,
            )
        }

        // 4. Generate query ID
        val queryId = generateQueryId()
        val startTime = System.currentTimeMillis()

        // 5. Create execution record
        val execution = AdHocExecutionEntity(
            queryId = queryId,
            userId = userId,
            sqlQuery = request.sql,
            renderedSql = renderedSql,
            engine = request.engine,
            status = ExecutionStatus.RUNNING,
        )
        executionRepository.save(execution)

        try {
            // 6. Execute query
            val result = queryEngineClient.execute(
                sql = renderedSql,
                engine = request.engine,
                timeoutSeconds = properties.maxQueryDurationSeconds,
                maxRows = properties.maxResultRows,
            )

            // 7. Check result size
            val resultSizeMb = estimateResultSizeMb(result.rows)
            if (resultSizeMb > properties.maxResultSizeMb) {
                throw ResultSizeLimitExceededException(resultSizeMb, properties.maxResultSizeMb)
            }

            // 8. Store results for download
            val downloadUrls = resultStorageService.storeResults(
                queryId = queryId,
                rows = result.rows,
                format = request.downloadFormat,
            )

            val executionTime = (System.currentTimeMillis() - startTime) / 1000.0
            val expiresAt = LocalDateTime.now().plusHours(properties.resultExpirationHours.toLong())

            // 9. Update execution record
            val completedExecution = execution.copy(
                status = ExecutionStatus.COMPLETED,
                rowsReturned = result.rows.size,
                bytesScanned = result.bytesScanned,
                costUsd = result.costUsd,
                executionTimeSeconds = executionTime,
                expiresAt = expiresAt,
            )
            executionRepository.save(completedExecution)

            // 10. Increment usage
            executionPolicyService.incrementUsage(userId)

            return AdHocExecutionMapper.toResultDto(completedExecution, downloadUrls)

        } catch (e: QueryTimeoutException) {
            val failedExecution = execution.copy(
                status = ExecutionStatus.TIMEOUT,
                errorMessage = e.message,
            )
            executionRepository.save(failedExecution)
            throw QueryExecutionTimeoutException(queryId, properties.maxQueryDurationSeconds)

        } catch (e: Exception) {
            val failedExecution = execution.copy(
                status = ExecutionStatus.FAILED,
                errorMessage = e.message,
            )
            executionRepository.save(failedExecution)
            throw e
        }
    }

    private fun renderSqlWithParameters(sql: String, parameters: Map<String, Any>): String {
        var rendered = sql
        parameters.forEach { (key, value) ->
            val placeholder = "{$key}"
            val replacement = when (value) {
                is String -> "'$value'"
                is Number -> value.toString()
                is Boolean -> value.toString()
                else -> "'$value'"
            }
            rendered = rendered.replace(placeholder, replacement)
        }
        return rendered
    }

    private fun generateQueryId(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val suffix = UUID.randomUUID().toString().take(8)
        return "adhoc_${timestamp}_$suffix"
    }

    private fun estimateResultSizeMb(rows: List<Map<String, Any>>): Int {
        val estimatedBytes = rows.sumOf { row ->
            row.values.sumOf { it.toString().toByteArray().size }
        }
        return (estimatedBytes / (1024 * 1024)).toInt()
    }
}
```

---

## 8. Testing Requirements

### 8.1 Unit Tests

**Required Coverage: 80%+**

```kotlin
@ExtendWith(MockitoExtension::class)
class ExecutionPolicyServiceTest {
    @Mock private lateinit var properties: RunExecutionProperties
    @Mock private lateinit var quotaRepository: UserExecutionQuotaRepository
    @InjectMocks private lateinit var policyService: ExecutionPolicyService

    @Test
    fun `should return policy with current usage`() {
        // Given
        val userId = "user@example.com"
        val quota = UserExecutionQuotaEntity(
            userId = userId,
            queriesToday = 10,
            queriesThisHour = 3,
        )
        given(quotaRepository.findByUserId(userId)).willReturn(quota)
        given(properties.rateLimits).willReturn(RateLimitsProperties())

        // When
        val policy = policyService.getPolicy(userId)

        // Then
        assertThat(policy.currentUsage.queriesToday).isEqualTo(10)
        assertThat(policy.currentUsage.queriesThisHour).isEqualTo(3)
    }

    @Test
    fun `should throw RateLimitExceededException when hourly limit reached`() {
        // Given
        val userId = "user@example.com"
        val quota = UserExecutionQuotaEntity(
            userId = userId,
            queriesThisHour = 50,
        )
        given(quotaRepository.findByUserId(userId)).willReturn(quota)
        given(properties.rateLimits).willReturn(RateLimitsProperties(queriesPerHour = 50))

        val request = ExecuteSqlRequest(sql = "SELECT 1")

        // When/Then
        assertThatThrownBy { policyService.validateExecution(userId, request) }
            .isInstanceOf(RateLimitExceededException::class.java)
            .hasFieldOrPropertyWithValue("limitType", "queries_per_hour")
    }

    @Test
    fun `should throw UnsupportedEngineException for invalid engine`() {
        // Given
        val userId = "user@example.com"
        given(quotaRepository.findByUserId(userId)).willReturn(null)
        given(quotaRepository.save(any())).willAnswer { it.arguments[0] }
        given(properties.rateLimits).willReturn(RateLimitsProperties())
        given(properties.allowedEngines).willReturn(listOf("bigquery", "trino"))

        val request = ExecuteSqlRequest(sql = "SELECT 1", engine = "snowflake")

        // When/Then
        assertThatThrownBy { policyService.validateExecution(userId, request) }
            .isInstanceOf(UnsupportedEngineException::class.java)
    }
}
```

### 8.2 Integration Tests

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class RunControllerTest {
    @Autowired private lateinit var testRestTemplate: TestRestTemplate

    @Container
    @ServiceConnection
    val mysql = MySQLContainer("mysql:8.0")

    @Test
    fun `should return execution policy`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/run/policy",
            ExecutionPolicyDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.allowedEngines).contains("bigquery")
    }

    @Test
    fun `should execute SQL successfully`() {
        // Given
        val request = ExecuteSqlRequest(
            sql = "SELECT 1 as id",
            engine = "bigquery",
            dryRun = true,
        )

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/run/execute",
            request,
            ExecutionResultDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.status).isEqualTo("VALIDATED")
    }

    @Test
    fun `should return 429 when rate limit exceeded`() {
        // Given: Exhaust rate limit
        repeat(51) {
            testRestTemplate.postForEntity(
                "/api/v1/run/execute",
                ExecuteSqlRequest(sql = "SELECT 1", dryRun = false),
                ExecutionResultDto::class.java
            )
        }

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/run/execute",
            ExecuteSqlRequest(sql = "SELECT 1"),
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        assertThat(response.body?.error?.code).isEqualTo("RATE_LIMIT_EXCEEDED")
    }
}
```

### 8.3 CLI Integration Tests

```bash
# Test execution policy endpoint
dli run --check-policy --server-url http://localhost:8081

# Test dry run
dli run query.sql --dry-run --server-url http://localhost:8081

# Test execution with parameters
dli run query.sql --param date=2026-01-01 --server-url http://localhost:8081

# Test download
dli run query.sql --download csv --server-url http://localhost:8081
```

---

## 9. Related Documents

### Internal References

| Document | Description |
|----------|-------------|
| [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) | Implementation timeline (Phase 4) |
| [`../INTEGRATION_PATTERNS.md`](../INTEGRATION_PATTERNS.md) | Spring Boot patterns |
| [`../ERROR_CODES.md`](../ERROR_CODES.md) | Error code definitions |
| [`archive/P3_LOW_APIS.md`](./archive/P3_LOW_APIS.md) | P3 APIs overview |
| [`./QUERY_FEATURE.md`](./QUERY_FEATURE.md) | Query metadata APIs (related) |

### CLI Reference

| CLI Feature | Documentation |
|-------------|---------------|
| Run Command | `project-interface-cli/features/EXECUTION_RELEASE.md` |
| Library API | `project-interface-cli/features/LIBRARY_RELEASE.md` |

### Existing Code References

| Component | File | Pattern |
|-----------|------|---------|
| Service Example | `module-core-domain/service/DatasetExecutionService.kt` | Execution service pattern |
| Controller Example | `module-server-api/controller/DatasetController.kt` | REST controller pattern |
| Exception Handler | `module-server-api/exception/GlobalExceptionHandler.kt` | Error handling pattern |

---

## Appendix: Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `INVALID_SQL` | 400 | SQL syntax error |
| `RATE_LIMIT_EXCEEDED` | 429 | User exceeded query rate limits |
| `UNSUPPORTED_ENGINE` | 400 | Query engine not allowed |
| `QUERY_TOO_LARGE` | 400 | SQL query exceeds size limit |
| `QUERY_EXECUTION_TIMEOUT` | 408 | Query execution timed out |
| `RESULT_SIZE_LIMIT_EXCEEDED` | 413 | Query result exceeds size limit |
| `RESULT_NOT_FOUND` | 404 | Download result not found or expired |

---

*Last Updated: 2026-01-01*

---

## Appendix B: Review Feedback

> **Reviewed by:** expert-spring-kotlin Agent | **Date:** 2026-01-01 | **Rating:** 3.5/5

### Strengths
- Well-designed execution policy with rate limiting
- Clear separation of concerns (Policy, Storage, Execution services)
- Good use of configuration properties pattern
- Comprehensive error handling with custom exceptions

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | Entity uses `UUID.randomUUID()` at object creation, not persist time | Use `@GeneratedValue(generator = "uuid2")` pattern |
| **High** | `@CreationTimestamp` requires `var`, not `val` | Change timestamp fields to `var` |
| **High** | Race condition in quota management between check and increment | Use `@Version` or database locks |
| **Medium** | Mixed testing frameworks (Mockito vs MockK) | Use MockK throughout for consistency |
| **Medium** | Entity classes shouldn't be data classes with copy() | Use factory methods instead |
| **Low** | `TODO("Implement Parquet conversion")` should throw | Throw `UnsupportedOperationException` |

### Required Changes Before Implementation
1. Fix entity ID generation pattern
2. Change `@CreationTimestamp` fields to `var`
3. Handle race conditions in quota management (use `@Version` or database locks)
4. Replace Mockito with MockK for consistency
5. Fix entity immutability issues
