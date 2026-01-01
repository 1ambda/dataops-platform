# Quality API Feature Specification

> **Version:** 0.1.0 | **Status:** Draft | **Priority:** P3 Low
> **CLI Commands:** `dli quality list/get/run` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** Week 10 | **Endpoints:** 3

---

## 1. Overview

### 1.1 Purpose

The Quality API provides server-side data quality testing capabilities, enabling:
- **Quality Spec Management**: Store and retrieve quality test specifications
- **Test Execution**: Run data quality tests against datasets and metrics
- **Result Tracking**: Track quality test history and failure patterns

### 1.2 CLI Command Mapping

| CLI Command | Server Endpoint | Description |
|-------------|-----------------|-------------|
| `dli quality list` | `GET /api/v1/quality` | List quality specifications |
| `dli quality get <name>` | `GET /api/v1/quality/{name}` | Get quality spec details |
| `dli quality run <resource>` | `POST /api/v1/quality/test/{resource_name}` | Execute quality tests |

### 1.3 Test Types Supported

| Test Type | Description | SQL Generation |
|-----------|-------------|----------------|
| `not_null` | Column contains no NULL values | `COUNT(*) FILTER (WHERE col IS NULL)` |
| `unique` | Column values are unique | `GROUP BY HAVING COUNT(*) > 1` |
| `accepted_values` | Column values in allowed list | `NOT IN (values)` |
| `relationships` | Foreign key integrity | `LEFT JOIN ... WHERE NULL` |
| `expression` | Custom SQL expression | User-provided expression |
| `row_count` | Row count within range | `COUNT(*) BETWEEN min AND max` |
| `singular` | Custom SQL test | Full SQL query execution |

---

## 2. CLI Command Mapping

### 2.1 `dli quality list`

```bash
# List all quality specs
dli quality list

# Filter by resource type
dli quality list --resource-type Dataset

# Filter by tag
dli quality list --tag critical

# With pagination
dli quality list --limit 50 --offset 0
```

**Server Request:**
```http
GET /api/v1/quality?resource_type=Dataset&tag=critical&limit=50&offset=0
```

### 2.2 `dli quality get`

```bash
# Get quality spec details
dli quality get user_table_quality

# Output format
dli quality get user_table_quality --format json
```

**Server Request:**
```http
GET /api/v1/quality/user_table_quality
```

### 2.3 `dli quality run`

```bash
# Run all tests for a resource
dli quality run iceberg.analytics.users

# Run specific quality spec
dli quality run iceberg.analytics.users --spec user_table_quality

# Run specific tests only
dli quality run iceberg.analytics.users --tests user_id_not_null,email_unique

# With timeout
dli quality run iceberg.analytics.users --timeout 300
```

**Server Request:**
```http
POST /api/v1/quality/test/iceberg.analytics.users
Content-Type: application/json

{
  "quality_spec": "user_table_quality",
  "tests": ["user_id_not_null", "email_unique"],
  "timeout": 300
}
```

---

## 3. API Specifications

### 3.1 List Quality Specs

#### `GET /api/v1/quality`

**Purpose**: List quality specifications for `dli quality list`

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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 400 | `INVALID_PARAMETER` | Invalid query parameter value |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |

---

### 3.2 Get Quality Spec

#### `GET /api/v1/quality/{name}`

**Purpose**: Get quality specification details for `dli quality get`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | Yes | Quality spec name |

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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 404 | `QUALITY_SPEC_NOT_FOUND` | Quality spec does not exist |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |

---

### 3.3 Execute Quality Test

#### `POST /api/v1/quality/test/{resource_name}`

**Purpose**: Execute quality tests for a resource for `dli quality run`

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `resource_name` | string | Yes | Fully qualified resource name (catalog.schema.table) |

**Request Body:**
```json
{
  "quality_spec": "user_table_quality",
  "tests": ["user_id_not_null", "email_unique"],
  "timeout": 300
}
```

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `quality_spec` | string | No | - | Specific quality spec to run (if omitted, runs all specs for resource) |
| `tests` | array | No | - | Specific test names to run (if omitted, runs all enabled tests) |
| `timeout` | int | No | 300 | Timeout in seconds (max 1800) |

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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 400 | `INVALID_REQUEST` | Invalid request body |
| 404 | `RESOURCE_NOT_FOUND` | Resource does not exist |
| 404 | `QUALITY_SPEC_NOT_FOUND` | Quality spec does not exist |
| 408 | `EXECUTION_TIMEOUT` | Test execution timed out |
| 503 | `QUERY_ENGINE_UNAVAILABLE` | Query engine is unavailable |

---

## 4. Domain Model

### 4.1 Entity Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        QualitySpecEntity                            │
├─────────────────────────────────────────────────────────────────────┤
│ id: Long (PK)                                                       │
│ name: String (unique)                                               │
│ resourceName: String                                                │
│ resourceType: ResourceType (DATASET, METRIC)                        │
│ owner: String                                                       │
│ team: String?                                                       │
│ description: String?                                                │
│ tags: List<String>                                                  │
│ scheduleCron: String?                                               │
│ scheduleTimezone: String                                            │
│ enabled: Boolean                                                    │
│ createdAt: Instant                                                  │
│ updatedAt: Instant                                                  │
├─────────────────────────────────────────────────────────────────────┤
│ tests: List<QualityTestEntity> (OneToMany)                          │
│ runs: List<QualityRunEntity> (OneToMany)                            │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        QualityTestEntity                            │
├─────────────────────────────────────────────────────────────────────┤
│ id: Long (PK)                                                       │
│ specId: Long (FK)                                                   │
│ name: String                                                        │
│ testType: TestType (NOT_NULL, UNIQUE, ACCEPTED_VALUES, etc.)        │
│ column: String?                                                     │
│ columns: List<String>?                                              │
│ config: JsonNode                                                    │
│ severity: Severity (ERROR, WARN)                                    │
│ enabled: Boolean                                                    │
│ description: String?                                                │
│ createdAt: Instant                                                  │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                        QualityRunEntity                             │
├─────────────────────────────────────────────────────────────────────┤
│ id: Long (PK)                                                       │
│ runId: String (unique)                                              │
│ specId: Long (FK)                                                   │
│ resourceName: String                                                │
│ status: RunStatus (RUNNING, COMPLETED, FAILED, TIMEOUT)             │
│ overallStatus: TestStatus (PASSED, FAILED)                          │
│ startedAt: Instant                                                  │
│ completedAt: Instant?                                               │
│ durationSeconds: Double?                                            │
│ passedTests: Int                                                    │
│ failedTests: Int                                                    │
│ executedBy: String                                                  │
├─────────────────────────────────────────────────────────────────────┤
│ results: List<TestResultEntity> (OneToMany)                         │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              │ 1:N
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        TestResultEntity                             │
├─────────────────────────────────────────────────────────────────────┤
│ id: Long (PK)                                                       │
│ runId: Long (FK)                                                    │
│ testId: Long (FK)                                                   │
│ testName: String                                                    │
│ testType: TestType                                                  │
│ status: TestStatus (PASSED, FAILED, ERROR, SKIPPED)                 │
│ failedRows: Long                                                    │
│ totalRows: Long                                                     │
│ executionTimeSeconds: Double                                        │
│ errorMessage: String?                                               │
│ sampleFailures: JsonNode?                                           │
│ generatedSql: String?                                               │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 Enums

```kotlin
enum class ResourceType {
    DATASET,
    METRIC
}

enum class TestType {
    NOT_NULL,
    UNIQUE,
    ACCEPTED_VALUES,
    RELATIONSHIPS,
    EXPRESSION,
    ROW_COUNT,
    SINGULAR
}

enum class Severity {
    ERROR,
    WARN
}

enum class RunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    TIMEOUT
}

enum class TestStatus {
    PASSED,
    FAILED,
    ERROR,
    SKIPPED
}
```

### 4.3 DTOs

```kotlin
// Request DTOs
data class ExecuteQualityTestRequest(
    val qualitySpec: String? = null,
    val tests: List<String>? = null,
    val timeout: Int = 300
)

// Response DTOs
data class QualitySpecSummaryDto(
    val name: String,
    val resourceName: String,
    val resourceType: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val testCount: Int,
    val lastRun: LastRunSummaryDto?,
    val createdAt: Instant
)

data class QualitySpecDetailDto(
    val name: String,
    val resourceName: String,
    val resourceType: String,
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val tests: List<QualityTestDto>,
    val schedule: ScheduleDto?,
    val lastRun: LastRunDetailDto?,
    val createdAt: Instant
)

data class QualityTestDto(
    val name: String,
    val testType: String,
    val column: String?,
    val columns: List<String>?,
    val config: Map<String, Any>?,
    val severity: String,
    val enabled: Boolean,
    val description: String?
)

data class QualityRunResultDto(
    val runId: String,
    val resourceName: String,
    val qualitySpec: String,
    val status: String,
    val startedAt: Instant,
    val completedAt: Instant?,
    val durationSeconds: Double?,
    val overallStatus: String,
    val passedTests: Int,
    val failedTests: Int,
    val testResults: List<TestResultDto>
)

data class TestResultDto(
    val testName: String,
    val testType: String,
    val status: String,
    val failedRows: Long,
    val totalRows: Long,
    val executionTimeSeconds: Double,
    val errorMessage: String?,
    val sampleFailures: List<Map<String, Any>>?
)
```

---

## 5. Quality Rule Engine

### 5.1 Overview

The `QualityRuleEngine` generates SQL queries for each test type. All generated SQL follows a consistent pattern returning `failed_rows` and `total_rows` columns.

### 5.2 Implementation

```kotlin
@Component
class QualityRuleEngine {

    fun generateSQL(resourceName: String, test: QualityTestEntity): String {
        return when (test.testType) {
            TestType.NOT_NULL -> generateNotNullSQL(resourceName, test)
            TestType.UNIQUE -> generateUniqueSQL(resourceName, test)
            TestType.ACCEPTED_VALUES -> generateAcceptedValuesSQL(resourceName, test)
            TestType.RELATIONSHIPS -> generateRelationshipsSQL(resourceName, test)
            TestType.EXPRESSION -> generateExpressionSQL(resourceName, test)
            TestType.ROW_COUNT -> generateRowCountSQL(resourceName, test)
            TestType.SINGULAR -> test.config["sql"]?.asText()
                ?: throw IllegalArgumentException("Singular test requires 'sql' in config")
        }
    }

    /**
     * NOT_NULL: Counts rows where column is NULL
     *
     * Example output:
     *   failed_rows: 5, total_rows: 1000000
     */
    private fun generateNotNullSQL(resourceName: String, test: QualityTestEntity): String {
        val column = test.column ?: test.columns?.firstOrNull()
            ?: throw IllegalArgumentException("not_null test requires column")

        return """
            SELECT
                COUNT(*) FILTER (WHERE $column IS NULL) as failed_rows,
                COUNT(*) as total_rows
            FROM $resourceName
        """.trimIndent()
    }

    /**
     * UNIQUE: Counts duplicate rows
     *
     * Uses CTE to find duplicates, then sums (count - 1) for each duplicate group.
     * Example: If email "a@b.com" appears 3 times, that contributes 2 to failed_rows.
     */
    private fun generateUniqueSQL(resourceName: String, test: QualityTestEntity): String {
        val column = test.column ?: test.columns?.firstOrNull()
            ?: throw IllegalArgumentException("unique test requires column")

        return """
            WITH duplicates AS (
                SELECT $column, COUNT(*) as cnt
                FROM $resourceName
                WHERE $column IS NOT NULL
                GROUP BY $column
                HAVING COUNT(*) > 1
            )
            SELECT
                COALESCE(SUM(cnt - 1), 0) as failed_rows,
                (SELECT COUNT(*) FROM $resourceName) as total_rows
            FROM duplicates
        """.trimIndent()
    }

    /**
     * ACCEPTED_VALUES: Counts rows with values not in allowed list
     *
     * Ignores NULL values (use not_null test separately if needed).
     */
    private fun generateAcceptedValuesSQL(resourceName: String, test: QualityTestEntity): String {
        val column = test.column ?: test.columns?.firstOrNull()
            ?: throw IllegalArgumentException("accepted_values test requires column")

        val config = test.config
        val values = config["values"]?.map { it.asText() }
            ?: throw IllegalArgumentException("accepted_values test requires 'values' in config")

        val valuesList = values.joinToString(", ") { "'$it'" }

        return """
            SELECT
                COUNT(*) FILTER (WHERE $column NOT IN ($valuesList)) as failed_rows,
                COUNT(*) as total_rows
            FROM $resourceName
            WHERE $column IS NOT NULL
        """.trimIndent()
    }

    /**
     * RELATIONSHIPS: Validates foreign key integrity
     *
     * Counts rows where the foreign key value doesn't exist in target table.
     */
    private fun generateRelationshipsSQL(resourceName: String, test: QualityTestEntity): String {
        val column = test.column ?: test.columns?.firstOrNull()
            ?: throw IllegalArgumentException("relationships test requires column")

        val config = test.config
        val toTable = config["to"]?.asText()
            ?: throw IllegalArgumentException("relationships test requires 'to' in config")
        val toColumn = config["to_column"]?.asText()
            ?: throw IllegalArgumentException("relationships test requires 'to_column' in config")

        return """
            WITH orphaned AS (
                SELECT a.$column
                FROM $resourceName a
                LEFT JOIN $toTable b ON a.$column = b.$toColumn
                WHERE a.$column IS NOT NULL AND b.$toColumn IS NULL
            )
            SELECT
                COUNT(*) as failed_rows,
                (SELECT COUNT(*) FROM $resourceName WHERE $column IS NOT NULL) as total_rows
            FROM orphaned
        """.trimIndent()
    }

    /**
     * EXPRESSION: Validates custom SQL expression evaluates to true
     *
     * Counts rows where expression is false.
     */
    private fun generateExpressionSQL(resourceName: String, test: QualityTestEntity): String {
        val expression = test.config["expression"]?.asText()
            ?: throw IllegalArgumentException("expression test requires 'expression' in config")

        return """
            SELECT
                COUNT(*) FILTER (WHERE NOT ($expression)) as failed_rows,
                COUNT(*) as total_rows
            FROM $resourceName
        """.trimIndent()
    }

    /**
     * ROW_COUNT: Validates total row count is within range
     *
     * Returns 1 as failed_rows if count is outside range, 0 otherwise.
     */
    private fun generateRowCountSQL(resourceName: String, test: QualityTestEntity): String {
        val config = test.config
        val min = config["min"]?.asLong() ?: 0L
        val max = config["max"]?.asLong() ?: Long.MAX_VALUE

        return """
            WITH row_count AS (
                SELECT COUNT(*) as cnt FROM $resourceName
            )
            SELECT
                CASE
                    WHEN cnt < $min OR cnt > $max THEN 1
                    ELSE 0
                END as failed_rows,
                cnt as total_rows
            FROM row_count
        """.trimIndent()
    }

    /**
     * Generate SQL to get sample failures for debugging
     */
    fun generateSampleFailuresSQL(
        resourceName: String,
        test: QualityTestEntity,
        limit: Int = 5
    ): String? {
        val column = test.column ?: test.columns?.firstOrNull() ?: return null

        return when (test.testType) {
            TestType.NOT_NULL -> """
                SELECT $column, 'NULL value' as reason
                FROM $resourceName
                WHERE $column IS NULL
                LIMIT $limit
            """.trimIndent()

            TestType.UNIQUE -> """
                SELECT $column, COUNT(*) as count
                FROM $resourceName
                WHERE $column IS NOT NULL
                GROUP BY $column
                HAVING COUNT(*) > 1
                ORDER BY COUNT(*) DESC
                LIMIT $limit
            """.trimIndent()

            TestType.ACCEPTED_VALUES -> {
                val values = test.config["values"]?.map { it.asText() } ?: return null
                val valuesList = values.joinToString(", ") { "'$it'" }
                """
                    SELECT $column, COUNT(*) as count
                    FROM $resourceName
                    WHERE $column IS NOT NULL AND $column NOT IN ($valuesList)
                    GROUP BY $column
                    ORDER BY COUNT(*) DESC
                    LIMIT $limit
                """.trimIndent()
            }

            else -> null
        }
    }
}
```

### 5.3 SQL Generation Examples

#### NOT_NULL Test

```yaml
# Quality Spec Test Definition
- name: user_id_not_null
  type: not_null
  column: user_id
  severity: error
```

```sql
-- Generated SQL
SELECT
    COUNT(*) FILTER (WHERE user_id IS NULL) as failed_rows,
    COUNT(*) as total_rows
FROM iceberg.analytics.users
```

#### UNIQUE Test

```yaml
- name: email_unique
  type: unique
  column: email
  severity: warn
```

```sql
-- Generated SQL
WITH duplicates AS (
    SELECT email, COUNT(*) as cnt
    FROM iceberg.analytics.users
    WHERE email IS NOT NULL
    GROUP BY email
    HAVING COUNT(*) > 1
)
SELECT
    COALESCE(SUM(cnt - 1), 0) as failed_rows,
    (SELECT COUNT(*) FROM iceberg.analytics.users) as total_rows
FROM duplicates
```

#### ACCEPTED_VALUES Test

```yaml
- name: status_valid
  type: accepted_values
  column: status
  config:
    values: ["active", "inactive", "pending"]
  severity: error
```

```sql
-- Generated SQL
SELECT
    COUNT(*) FILTER (WHERE status NOT IN ('active', 'inactive', 'pending')) as failed_rows,
    COUNT(*) as total_rows
FROM iceberg.analytics.users
WHERE status IS NOT NULL
```

---

## 6. Testing Requirements

### 6.1 Unit Tests

```kotlin
@ExtendWith(MockKExtension::class)
class QualityRuleEngineTest {

    private lateinit var engine: QualityRuleEngine

    @BeforeEach
    fun setup() {
        engine = QualityRuleEngine()
    }

    @Test
    fun `should generate NOT_NULL SQL correctly`() {
        // Given
        val test = QualityTestEntity(
            id = 1L,
            specId = 1L,
            name = "user_id_not_null",
            testType = TestType.NOT_NULL,
            column = "user_id",
            severity = Severity.ERROR,
            enabled = true
        )

        // When
        val sql = engine.generateSQL("iceberg.analytics.users", test)

        // Then
        assertThat(sql).contains("user_id IS NULL")
        assertThat(sql).contains("failed_rows")
        assertThat(sql).contains("total_rows")
    }

    @Test
    fun `should generate UNIQUE SQL with CTE`() {
        // Given
        val test = QualityTestEntity(
            id = 2L,
            specId = 1L,
            name = "email_unique",
            testType = TestType.UNIQUE,
            column = "email",
            severity = Severity.WARN,
            enabled = true
        )

        // When
        val sql = engine.generateSQL("iceberg.analytics.users", test)

        // Then
        assertThat(sql).contains("WITH duplicates AS")
        assertThat(sql).contains("GROUP BY email")
        assertThat(sql).contains("HAVING COUNT(*) > 1")
    }

    @Test
    fun `should generate ACCEPTED_VALUES SQL with IN clause`() {
        // Given
        val config = objectMapper.createObjectNode().apply {
            putArray("values").apply {
                add("active")
                add("inactive")
                add("pending")
            }
        }
        val test = QualityTestEntity(
            id = 3L,
            specId = 1L,
            name = "status_valid",
            testType = TestType.ACCEPTED_VALUES,
            column = "status",
            config = config,
            severity = Severity.ERROR,
            enabled = true
        )

        // When
        val sql = engine.generateSQL("iceberg.analytics.users", test)

        // Then
        assertThat(sql).contains("NOT IN ('active', 'inactive', 'pending')")
    }

    @Test
    fun `should throw exception when column is missing`() {
        // Given
        val test = QualityTestEntity(
            id = 4L,
            specId = 1L,
            name = "invalid_test",
            testType = TestType.NOT_NULL,
            column = null,
            severity = Severity.ERROR,
            enabled = true
        )

        // When/Then
        assertThrows<IllegalArgumentException> {
            engine.generateSQL("iceberg.analytics.users", test)
        }
    }
}
```

### 6.2 Integration Tests

```kotlin
@SpringBootTest
@Testcontainers
class QualityApiIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var qualitySpecRepository: QualitySpecRepositoryJpa

    @Test
    fun `GET quality should return list of specs`() {
        // Given
        createTestQualitySpec("test_spec_1")
        createTestQualitySpec("test_spec_2")

        // When/Then
        mockMvc.perform(get("/api/v1/quality")
            .header("Authorization", "Bearer $testToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    fun `GET quality by name should return spec details`() {
        // Given
        val spec = createTestQualitySpec("user_table_quality")

        // When/Then
        mockMvc.perform(get("/api/v1/quality/user_table_quality")
            .header("Authorization", "Bearer $testToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("user_table_quality"))
            .andExpect(jsonPath("$.tests").isArray)
    }

    @Test
    fun `GET quality by name should return 404 when not found`() {
        mockMvc.perform(get("/api/v1/quality/non_existent")
            .header("Authorization", "Bearer $testToken"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.error.code").value("QUALITY_SPEC_NOT_FOUND"))
    }

    @Test
    fun `POST quality test should execute tests and return results`() {
        // Given
        createTestQualitySpec("user_quality")

        val request = """
            {
                "quality_spec": "user_quality",
                "tests": ["user_id_not_null"],
                "timeout": 60
            }
        """

        // When/Then
        mockMvc.perform(post("/api/v1/quality/test/iceberg.analytics.users")
            .header("Authorization", "Bearer $testToken")
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.run_id").exists())
            .andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.test_results").isArray)
    }
}
```

### 6.3 Test Coverage Requirements

| Component | Min Coverage | Focus Areas |
|-----------|--------------|-------------|
| `QualityRuleEngine` | 90% | All test types, edge cases, error handling |
| `QualityController` | 85% | Request validation, error responses |
| `QualityService` | 85% | Business logic, test execution flow |
| `Repository` | 80% | Query methods, pagination |

---

## 7. Related Documents

### 7.1 Internal References

| Document | Description |
|----------|-------------|
| [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) | Phase 4 implementation timeline |
| [`../BASECAMP_OVERVIEW.md`](../BASECAMP_OVERVIEW.md) | Architecture overview |
| [`../ERROR_CODES.md`](../ERROR_CODES.md) | Error code definitions |
| [`archive/P3_LOW_APIS.md`](./archive/P3_LOW_APIS.md) | Source API specifications |

### 7.2 CLI References

| File | Description |
|------|-------------|
| `project-interface-cli/src/dli/api/quality.py` | QualityAPI implementation |
| `project-interface-cli/src/dli/models/quality.py` | Quality domain models |
| `project-interface-cli/src/dli/commands/quality.py` | CLI commands |

### 7.3 External References

- [dbt Data Tests](https://docs.getdbt.com/docs/build/data-tests) - Test type inspiration
- [Great Expectations](https://docs.greatexpectations.io/) - Quality testing patterns

---

## Appendix A: Quality Spec YAML Schema

```yaml
# quality.iceberg.analytics.users.yaml
version: 1

target:
  type: dataset  # or "metric"
  name: iceberg.analytics.users

metadata:
  owner: data@example.com
  team: "@data-quality"
  description: "Quality tests for user table"
  tags:
    - critical
    - pii

schedule:
  cron: "0 8 * * *"
  timezone: UTC
  enabled: true

notifications:
  slack:
    channel: "#data-quality-alerts"
    on_failure: true
    on_success: false
  email:
    recipients:
      - data@example.com
    on_failure: true

tests:
  - name: user_id_not_null
    type: not_null
    column: user_id
    severity: error
    description: "User ID must not be null"

  - name: email_unique
    type: unique
    column: email
    severity: warn

  - name: status_valid
    type: accepted_values
    column: status
    config:
      values:
        - active
        - inactive
        - pending
    severity: error

  - name: country_fk
    type: relationships
    column: country_code
    config:
      to: iceberg.reference.countries
      to_column: code
    severity: warn

  - name: age_range
    type: expression
    config:
      expression: "age >= 0 AND age <= 150"
    severity: warn

  - name: min_row_count
    type: row_count
    config:
      min: 1000
      max: 10000000
    severity: error

  - name: custom_business_rule
    type: singular
    config:
      sql: |
        SELECT COUNT(*) as failed_rows,
               (SELECT COUNT(*) FROM iceberg.analytics.users) as total_rows
        FROM iceberg.analytics.users
        WHERE created_at > updated_at
    severity: error
```

---

*Document Version: 0.1.0 | Last Updated: 2026-01-01 | Author: Platform Team*

---

## Appendix B: Review Feedback

> **Reviewed by:** expert-spring-kotlin Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Strengths
- Excellent domain model with proper entity relationships (OneToMany)
- Well-designed `QualityRuleEngine` as a stateless component
- Good use of `when` expression for pattern matching
- Comprehensive test examples with MockK and JUnit 5
- Proper nullable handling with safe calls

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | SQL injection risk in `generateAcceptedValuesSQL` | Parameterize values or use proper escaping |
| **Medium** | `QualityRuleEngine` is `@Component` but has no dependencies | Make it a regular class or Kotlin object |
| **Medium** | Using `JsonNode` directly for config | Define typed config models with sealed classes |
| **Low** | Test class missing `@TestInstance(PER_CLASS)` | Add for better test isolation |

### Required Changes Before Implementation
1. Add SQL injection protection for user-provided values (use parameterized queries)
2. Extract `QualityRuleEngine` as a pure Kotlin object (no Spring bean)
3. Create typed config models instead of raw JsonNode
4. Add extension functions for repetitive null checks
