# P3 Low Priority APIs - Quality, Query, Run & Transpile

> **Priority:** P3 Low | **Implementation Time:** 3 weeks | **CLI Impact:** Advanced features
> **Target Audience:** Backend developers implementing advanced functionality
> **Cross-Reference:** [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 4 details

---

## 📋 Table of Contents

1. [Implementation Overview](#1-implementation-overview)
2. [Quality APIs](#2-quality-apis)
3. [Query Metadata APIs](#3-query-metadata-apis)
4. [Transpile APIs](#4-transpile-apis)
5. [Run APIs](#5-run-apis)
6. [Implementation Patterns](#6-implementation-patterns)

---

## 1. Implementation Overview

### 1.1 P3 APIs Summary

| API Group | Endpoints | CLI Commands Enabled | Implementation Timeline |
|-----------|-----------|---------------------|----------------------|
| **Quality** | 3 endpoints | `dli quality list/get/run` | Week 10 |
| **Query** | 3 endpoints | `dli query list/show/cancel` | Week 10 |
| **Transpile** | 2 endpoints | `dli *transpile` commands | Week 11-12 |
| **Run** | 2 endpoints | `dli run` ad-hoc execution | Week 11-12 |

**Total: 10 endpoints enabling advanced CLI functionality**

---

## 2. Quality APIs

### 2.1 List Quality Specs

#### `GET /api/v1/quality`

**Purpose**: List quality specifications for `dli quality list`

**Request:**
```http
GET /api/v1/quality?resource_type=Dataset&tag=critical&limit=50&offset=0
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `resource_type` | string | No | - | Filter by resource type (`Dataset`, `Metric`) |
| `tag` | string | No | - | Filter by tag |
| `limit` | int | No | 50 | Max results (1-500) |
| `offset` | int | No | 0 | Pagination offset |

**Response (200 OK):**
```json
[
  {
    "name": "user_table_quality",
    "resource_name": "iceberg.analytics.users",
    "resource_type": "Dataset",
    "owner": "data@example.com",
    "team": "@data-quality",
    "description": "Quality tests for user table",
    "tags": ["critical", "pii"],
    "test_count": 5,
    "last_run": {
      "run_at": "2026-01-01T08:00:00Z",
      "status": "PASSED",
      "passed_tests": 4,
      "failed_tests": 1
    },
    "created_at": "2025-12-01T10:00:00Z"
  }
]
```

---

### 2.2 Get Quality Spec

#### `GET /api/v1/quality/{name}`

**Purpose**: Get quality specification details for `dli quality get`

**Request:**
```http
GET /api/v1/quality/user_table_quality
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "name": "user_table_quality",
  "resource_name": "iceberg.analytics.users",
  "resource_type": "Dataset",
  "owner": "data@example.com",
  "team": "@data-quality",
  "description": "Quality tests for user table",
  "tags": ["critical", "pii"],
  "tests": [
    {
      "name": "user_id_not_null",
      "test_type": "not_null",
      "column": "user_id",
      "severity": "error",
      "enabled": true
    },
    {
      "name": "email_unique",
      "test_type": "unique",
      "column": "email",
      "severity": "warn",
      "enabled": true
    },
    {
      "name": "status_accepted_values",
      "test_type": "accepted_values",
      "column": "status",
      "config": {
        "values": ["active", "inactive", "pending"]
      },
      "severity": "error",
      "enabled": true
    }
  ],
  "schedule": {
    "cron": "0 8 * * *",
    "timezone": "UTC"
  },
  "last_run": {
    "run_id": "user_table_quality_20260101_080000",
    "run_at": "2026-01-01T08:00:00Z",
    "status": "PASSED",
    "duration_seconds": 15.3,
    "passed_tests": 4,
    "failed_tests": 1,
    "test_results": [
      {
        "test_name": "user_id_not_null",
        "status": "PASSED",
        "failed_rows": 0,
        "total_rows": 1500000
      },
      {
        "test_name": "email_unique",
        "status": "FAILED",
        "failed_rows": 50,
        "total_rows": 1500000,
        "error_message": "50 duplicate email addresses found"
      }
    ]
  },
  "created_at": "2025-12-01T10:00:00Z"
}
```

---

### 2.3 Execute Quality Test

#### `POST /api/v1/quality/test/{resource_name}`

**Purpose**: Execute quality tests for a resource for `dli quality run`

**Request:**
```http
POST /api/v1/quality/test/iceberg.analytics.users
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "quality_spec": "user_table_quality",
  "tests": ["user_id_not_null", "email_unique"],
  "timeout": 300
}
```

**Response (200 OK):**
```json
{
  "run_id": "user_table_quality_20260101_100000",
  "resource_name": "iceberg.analytics.users",
  "quality_spec": "user_table_quality",
  "status": "COMPLETED",
  "started_at": "2026-01-01T10:00:00Z",
  "completed_at": "2026-01-01T10:00:15Z",
  "duration_seconds": 15.3,
  "overall_status": "FAILED",
  "passed_tests": 1,
  "failed_tests": 1,
  "test_results": [
    {
      "test_name": "user_id_not_null",
      "test_type": "not_null",
      "status": "PASSED",
      "failed_rows": 0,
      "total_rows": 1500000,
      "execution_time_seconds": 8.2
    },
    {
      "test_name": "email_unique",
      "test_type": "unique",
      "status": "FAILED",
      "failed_rows": 50,
      "total_rows": 1500000,
      "execution_time_seconds": 7.1,
      "error_message": "50 duplicate email addresses found",
      "sample_failures": [
        {"email": "duplicate@example.com", "count": 2},
        {"email": "another@example.com", "count": 3}
      ]
    }
  ]
}
```

---

## 3. Query Metadata APIs

### 3.1 List Queries

#### `GET /api/v1/catalog/queries`

**Purpose**: List query execution history for `dli query list`

**Request:**
```http
GET /api/v1/catalog/queries?scope=my&status=running&start_date=2026-01-01&limit=50
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `scope` | string | No | `my` | Query scope (`my`, `system`, `user`, `all`) |
| `status` | string | No | - | Filter by status (`running`, `completed`, `failed`, `cancelled`) |
| `start_date` | string | No | - | Filter from date (YYYY-MM-DD) |
| `end_date` | string | No | - | Filter to date (YYYY-MM-DD) |
| `limit` | int | No | 50 | Max results (1-500) |
| `offset` | int | No | 0 | Pagination offset |

**Scope Access Control:**
- `my`: All authenticated users
- `system`: All authenticated users
- `user`: Requires `query:read:all` role
- `all`: Requires `query:read:all` role

**Response (200 OK):**
```json
[
  {
    "query_id": "query_20260101_100000_abc123",
    "sql": "SELECT user_id, COUNT(*) FROM iceberg.analytics.users GROUP BY 1",
    "status": "COMPLETED",
    "submitted_by": "analyst@example.com",
    "submitted_at": "2026-01-01T10:00:00Z",
    "started_at": "2026-01-01T10:00:05Z",
    "completed_at": "2026-01-01T10:00:15Z",
    "duration_seconds": 10.5,
    "rows_returned": 1500000,
    "bytes_scanned": "1.2 GB",
    "engine": "bigquery",
    "cost_usd": 0.006
  }
]
```

---

### 3.2 Get Query Details

#### `GET /api/v1/catalog/queries/{query_id}`

**Purpose**: Get query execution details for `dli query show`

**Request:**
```http
GET /api/v1/catalog/queries/query_20260101_100000_abc123
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "query_id": "query_20260101_100000_abc123",
  "sql": "SELECT user_id, COUNT(*) FROM iceberg.analytics.users WHERE created_at >= '2026-01-01' GROUP BY 1 ORDER BY 2 DESC LIMIT 100",
  "status": "COMPLETED",
  "submitted_by": "analyst@example.com",
  "submitted_at": "2026-01-01T10:00:00Z",
  "started_at": "2026-01-01T10:00:05Z",
  "completed_at": "2026-01-01T10:00:15Z",
  "duration_seconds": 10.5,
  "rows_returned": 100,
  "bytes_scanned": "1.2 GB",
  "engine": "bigquery",
  "cost_usd": 0.006,
  "execution_details": {
    "job_id": "bqjob_r1234567890_000001_project",
    "query_plan": [
      {
        "stage": "Stage 1",
        "operation": "Scan",
        "input_rows": 1500000,
        "output_rows": 450000
      },
      {
        "stage": "Stage 2",
        "operation": "Aggregate",
        "input_rows": 450000,
        "output_rows": 100
      }
    ],
    "tables_accessed": [
      "iceberg.analytics.users"
    ]
  },
  "error": null
}
```

**Response (200 OK - Failed Query):**
```json
{
  "query_id": "query_20260101_100001_def456",
  "sql": "SELECT * FROM non_existent_table",
  "status": "FAILED",
  "submitted_by": "analyst@example.com",
  "submitted_at": "2026-01-01T10:01:00Z",
  "started_at": "2026-01-01T10:01:02Z",
  "completed_at": "2026-01-01T10:01:03Z",
  "duration_seconds": 1.0,
  "error": {
    "code": "TABLE_NOT_FOUND",
    "message": "Table 'non_existent_table' was not found",
    "details": {
      "location": "line 1, column 15"
    }
  }
}
```

---

### 3.3 Cancel Query

#### `POST /api/v1/catalog/queries/{query_id}/cancel`

**Purpose**: Cancel a running query for `dli query cancel`

**Request:**
```http
POST /api/v1/catalog/queries/query_20260101_100000_abc123/cancel
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "reason": "User requested cancellation"
}
```

**Response (200 OK):**
```json
{
  "query_id": "query_20260101_100000_abc123",
  "status": "CANCELLED",
  "cancelled_by": "analyst@example.com",
  "cancelled_at": "2026-01-01T10:00:10Z",
  "reason": "User requested cancellation"
}
```

**Response (409 Conflict):**
```json
{
  "error": {
    "code": "QUERY_NOT_CANCELLABLE",
    "message": "Query query_20260101_100000_abc123 is already completed",
    "details": {
      "query_id": "query_20260101_100000_abc123",
      "current_status": "COMPLETED"
    }
  }
}
```

---

## 4. Transpile APIs

### 4.1 Get Transpile Rules

#### `GET /api/v1/transpile/rules`

**Purpose**: Get SQL transpile rules for CLI caching

**Request:**
```http
GET /api/v1/transpile/rules?version=latest
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "version": "2026-01-01-001",
  "rules": [
    {
      "name": "bigquery_to_trino_date_functions",
      "from_dialect": "bigquery",
      "to_dialect": "trino",
      "pattern": "DATE_SUB\\((.+?), INTERVAL (.+?) DAY\\)",
      "replacement": "date_add('day', -$2, $1)",
      "priority": 100
    },
    {
      "name": "standard_table_references",
      "from_dialect": "any",
      "to_dialect": "any",
      "pattern": "`([^`]+)`",
      "replacement": "\"$1\"",
      "priority": 50
    }
  ],
  "metadata": {
    "created_at": "2026-01-01T10:00:00Z",
    "created_by": "system",
    "total_rules": 2
  }
}
```

---

### 4.2 Get Metric SQL

#### `GET /api/v1/transpile/metrics/{metric_name}`

**Purpose**: Get transpiled SQL for a metric

**Request:**
```http
GET /api/v1/transpile/metrics/iceberg.reporting.user_summary?target_dialect=trino
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "metric_name": "iceberg.reporting.user_summary",
  "source_dialect": "bigquery",
  "target_dialect": "trino",
  "original_sql": "SELECT user_id, COUNT(*) FROM `iceberg.analytics.users` WHERE DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1",
  "transpiled_sql": "SELECT user_id, COUNT(*) FROM \"iceberg.analytics.users\" WHERE date_add('day', -30, CURRENT_DATE) GROUP BY 1",
  "applied_rules": [
    "bigquery_to_trino_date_functions",
    "standard_table_references"
  ],
  "transpiled_at": "2026-01-01T10:00:00Z"
}
```

---

## 5. Run APIs

### 5.1 Get Execution Policy

#### `GET /api/v1/run/policy`

**Purpose**: Get ad-hoc execution policy for `dli run` validation

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

---

### 5.2 Execute SQL

#### `POST /api/v1/run/execute`

**Purpose**: Execute ad-hoc SQL file for `dli run`

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

**Response (200 OK):**
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

---

## 6. Implementation Patterns

### 6.1 Quality Testing Engine

```kotlin
@Service
class QualityTestService(
    private val queryEngine: QueryEngineClient,
    private val qualityRuleEngine: QualityRuleEngine,
) {
    fun executeQualityTests(
        resourceName: String,
        qualitySpec: QualitySpec,
        testNames: List<String>?,
    ): QualityTestResult {
        val testsToRun = if (testNames != null) {
            qualitySpec.tests.filter { it.name in testNames }
        } else {
            qualitySpec.tests.filter { it.enabled }
        }

        val results = testsToRun.map { test ->
            executeQualityTest(resourceName, test)
        }

        return QualityTestResult(
            runId = generateRunId(),
            resourceName = resourceName,
            qualitySpec = qualitySpec.name,
            testResults = results,
            overallStatus = if (results.any { it.status == TestStatus.FAILED }) {
                QualityStatus.FAILED
            } else {
                QualityStatus.PASSED
            }
        )
    }

    private fun executeQualityTest(resourceName: String, test: QualityTest): TestResult {
        val sql = qualityRuleEngine.generateSQL(resourceName, test)

        return try {
            val result = queryEngine.execute(sql)
            val failedRows = result.rows.firstOrNull()?.get("failed_rows") as? Long ?: 0L
            val totalRows = result.rows.firstOrNull()?.get("total_rows") as? Long ?: 0L

            TestResult(
                testName = test.name,
                testType = test.testType,
                status = if (failedRows == 0L) TestStatus.PASSED else TestStatus.FAILED,
                failedRows = failedRows,
                totalRows = totalRows,
                executionTimeSeconds = result.durationSeconds,
                errorMessage = if (failedRows > 0) "Test failed with $failedRows failures" else null
            )
        } catch (e: Exception) {
            TestResult(
                testName = test.name,
                testType = test.testType,
                status = TestStatus.FAILED,
                errorMessage = e.message
            )
        }
    }
}

@Component
class QualityRuleEngine {
    fun generateSQL(resourceName: String, test: QualityTest): String {
        return when (test.testType) {
            "not_null" -> generateNotNullSQL(resourceName, test)
            "unique" -> generateUniqueSQL(resourceName, test)
            "accepted_values" -> generateAcceptedValuesSQL(resourceName, test)
            else -> throw UnsupportedTestTypeException(test.testType)
        }
    }

    private fun generateNotNullSQL(resourceName: String, test: QualityTest): String {
        return """
            SELECT
                COUNT(*) FILTER (WHERE ${test.column} IS NULL) as failed_rows,
                COUNT(*) as total_rows
            FROM $resourceName
        """.trimIndent()
    }

    private fun generateUniqueSQL(resourceName: String, test: QualityTest): String {
        return """
            WITH duplicates AS (
                SELECT ${test.column}, COUNT(*) as cnt
                FROM $resourceName
                WHERE ${test.column} IS NOT NULL
                GROUP BY ${test.column}
                HAVING COUNT(*) > 1
            )
            SELECT
                COALESCE(SUM(cnt - 1), 0) as failed_rows,
                (SELECT COUNT(*) FROM $resourceName) as total_rows
            FROM duplicates
        """.trimIndent()
    }

    private fun generateAcceptedValuesSQL(resourceName: String, test: QualityTest): String {
        val acceptedValues = test.config["values"] as List<String>
        val valuesList = acceptedValues.joinToString(", ") { "'$it'" }

        return """
            SELECT
                COUNT(*) FILTER (WHERE ${test.column} NOT IN ($valuesList)) as failed_rows,
                COUNT(*) as total_rows
            FROM $resourceName
            WHERE ${test.column} IS NOT NULL
        """.trimIndent()
    }
}
```

### 6.2 Ad-hoc Execution Service

```kotlin
@Service
class AdHocExecutionService(
    private val queryEngine: QueryEngineClient,
    private val resultStorageService: ResultStorageService,
    private val executionPolicyService: ExecutionPolicyService,
) {
    fun executeSQL(request: ExecuteSQLRequest): ExecutionResult {
        // 1. Validate execution policy
        executionPolicyService.validateExecution(request)

        // 2. Render SQL with parameters
        val renderedSQL = renderSQL(request.sql, request.parameters)

        // 3. Execute query
        val queryResult = if (request.dryRun) {
            queryEngine.validateSQL(renderedSQL)
            QueryResult.dryRun()
        } else {
            queryEngine.execute(renderedSQL, request.engine)
        }

        // 4. Store results if not dry run
        val downloadUrls = if (!request.dryRun && queryResult.rows.isNotEmpty()) {
            resultStorageService.storeResults(queryResult, request.downloadFormat)
        } else {
            emptyMap()
        }

        return ExecutionResult(
            queryId = queryResult.queryId,
            status = "COMPLETED",
            executionTimeSeconds = queryResult.durationSeconds,
            rowsReturned = queryResult.rowCount,
            downloadUrls = downloadUrls,
            renderedSQL = renderedSQL
        )
    }

    private fun renderSQL(sql: String, parameters: Map<String, Any>): String {
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
}

@Service
class ExecutionPolicyService(
    private val rateLimitService: RateLimitService,
) {
    fun validateExecution(request: ExecuteSQLRequest) {
        val policy = getExecutionPolicy()

        // Check rate limits
        if (!rateLimitService.checkLimit(getCurrentUser(), policy.rateLimits)) {
            throw RateLimitExceededException("Rate limit exceeded")
        }

        // Validate SQL size
        if (request.sql.length > policy.maxQuerySizeBytes) {
            throw QueryTooLargeException("SQL exceeds maximum size")
        }

        // Validate engine
        if (request.engine !in policy.allowedEngines) {
            throw UnsupportedEngineException("Engine ${request.engine} not allowed")
        }
    }
}
```

---

## 🔗 Related Documentation

- **Implementation Timeline**: [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 4
- **Architecture Overview**: [`../BASECAMP_OVERVIEW.md`](../BASECAMP_OVERVIEW.md)
- **Error Handling**: [`../ERROR_CODES.md`](../ERROR_CODES.md)
- **Previous APIs**: [`P2_MEDIUM_APIS.md`](./P2_MEDIUM_APIS.md)

### Implementation Complete
After P3 implementation, all CLI commands will have full server support:
- ✅ `dli metric/dataset` (P0)
- ✅ `dli catalog/lineage` (P1)
- ✅ `dli workflow` (P2)
- ✅ `dli quality/query/run` (P3)

---

*This document provides implementation-ready specifications for P3 Low Priority APIs, completing the full Basecamp Server API within 12.5 weeks.*