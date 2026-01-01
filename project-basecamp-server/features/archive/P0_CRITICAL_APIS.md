# P0 Critical APIs - Health, Metrics & Datasets

> **Priority:** P0 Critical | **Implementation Time:** 2.5 weeks | **CLI Impact:** Core functionality
> **Target Audience:** Backend developers implementing foundation APIs
> **Cross-Reference:** [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) for timeline details

---

## 📋 Table of Contents

1. [Implementation Overview](#1-implementation-overview)
2. [Health & System APIs](#2-health--system-apis)
3. [Metrics APIs](#3-metrics-apis)
4. [Datasets APIs](#4-datasets-apis)
5. [Implementation Patterns](#5-implementation-patterns)
6. [Testing Requirements](#6-testing-requirements)

---

## 1. Implementation Overview

### 1.1 P0 APIs Summary

| API Group | Endpoints | CLI Commands Enabled | Implementation Priority |
|-----------|-----------|---------------------|----------------------|
| **Health** | 2 endpoints | `dli debug` | Week 2.5 |
| **Metrics** | 4 endpoints | `dli metric list/get/register/run` | Week 1 |
| **Datasets** | 4 endpoints | `dli dataset list/get/register/run` | Week 2 |

**Total: 10 endpoints enabling core CLI functionality**

### 1.2 Implementation Strategy

```kotlin
// Week 1: Metrics Foundation
MetricEntity + MetricService + MetricController + Tests

// Week 2: Datasets + Execution
DatasetService (extend existing) + Execution services + Tests

// Week 2.5: Health Extension
ExtendedHealthService + System diagnostics
```

### 1.3 Code Reuse Opportunities

| Pattern Source | Target Implementation | Reuse % |
|----------------|----------------------|---------|
| `PipelineController` → `MetricController` | REST patterns | 95% |
| `PipelineService` → `MetricService` | Business logic patterns | 85% |
| `DatasetEntity` → `MetricEntity` | JPA entity structure | 80% |
| `PipelineMapper` → `MetricMapper` | DTO conversion | 90% |

---

## 2. Health & System APIs

### 2.1 Basic Health Check

#### `GET /api/v1/health`

**Purpose**: Basic server health status for load balancers and monitoring

**Request:**
```http
GET /api/v1/health
Accept: application/json
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "components": {
    "database": "healthy",
    "redis": "healthy",
    "airflow": "healthy"
  }
}
```

**Response (503 Service Unavailable):**
```json
{
  "status": "unhealthy",
  "version": "1.0.0",
  "components": {
    "database": "healthy",
    "redis": "unhealthy",
    "airflow": "healthy"
  },
  "error": "Redis connection failed"
}
```

**Implementation Notes:**
- ✅ Already implemented in existing health check
- No changes required for basic health endpoint

---

### 2.2 Extended Health Check

#### `GET /api/v1/health/extended`

**Purpose**: Detailed system diagnostics for `dli debug` command

**Request:**
```http
GET /api/v1/health/extended
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "uptime_seconds": 3600,
  "components": {
    "database": {
      "status": "healthy",
      "response_time_ms": 5,
      "pool_active": 10,
      "pool_max": 20
    },
    "redis": {
      "status": "healthy",
      "response_time_ms": 2,
      "memory_used_mb": 128
    },
    "airflow": {
      "status": "healthy",
      "api_version": "2.8.0",
      "active_dags": 45,
      "running_tasks": 12
    }
  },
  "system": {
    "jvm_memory_used_mb": 512,
    "jvm_memory_max_mb": 1024,
    "cpu_usage_percent": 15.5,
    "disk_usage_percent": 67.2
  },
  "environment": {
    "profile": "production",
    "region": "us-west-2"
  }
}
```

**Response (503 Service Unavailable):**
```json
{
  "status": "degraded",
  "components": {
    "database": "healthy",
    "redis": "unhealthy",
    "airflow": "degraded"
  },
  "errors": [
    {
      "component": "redis",
      "error": "Connection timeout after 5s"
    },
    {
      "component": "airflow",
      "error": "High response time (>10s)"
    }
  ]
}
```

**Implementation Requirements:**
```kotlin
@Service
class ExtendedHealthService(
    private val dataSource: DataSource,
    private val redisTemplate: RedisTemplate<String, String>,
    private val airflowClient: AirflowClient,
) {
    fun getExtendedHealth(): ExtendedHealthDto {
        // Implement detailed health checks
    }
}
```

---

## 3. Metrics APIs

> **Implementation Notes for feature-basecamp-server:**
> - Create `MetricEntity` following `DatasetEntity` pattern
> - Use `MetricService` (concrete class) following `PipelineService` pattern
> - Repository: `MetricRepositoryJpa` + `MetricRepositoryDsl` interfaces

### 3.1 List Metrics

#### `GET /api/v1/metrics`

**Purpose**: List metrics with optional filtering for `dli metric list`

**Request:**
```http
GET /api/v1/metrics?tag=revenue&owner=data@example.com&search=daily&limit=50&offset=0
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `tag` | string | No | - | Filter by tag (exact match) |
| `owner` | string | No | - | Filter by owner (partial match) |
| `search` | string | No | - | Search in name and description |
| `limit` | int | No | 50 | Max results (1-500) |
| `offset` | int | No | 0 | Pagination offset |

**Response (200 OK):**
```json
[
  {
    "name": "iceberg.reporting.user_summary",
    "type": "Metric",
    "owner": "analyst@example.com",
    "team": "@analytics",
    "description": "User summary metrics",
    "tags": ["reporting", "daily"],
    "created_at": "2025-12-01T10:00:00Z",
    "updated_at": "2025-12-15T14:30:00Z"
  }
]
```

**Implementation Pattern:**
```kotlin
@RestController
@RequestMapping("/api/v1/metrics")
class MetricController(
    private val metricService: MetricService,
) {
    @GetMapping
    fun listMetrics(
        @RequestParam tag: String?,
        @RequestParam owner: String?,
        @RequestParam search: String?,
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int,
    ): ResponseEntity<List<MetricDto>> {
        return ResponseEntity.ok(metricService.listMetrics(tag, owner, search, limit, offset))
    }
}
```

---

### 3.2 Get Metric Details

#### `GET /api/v1/metrics/{name}`

**Purpose**: Get metric details by fully qualified name for `dli metric get`

**Request:**
```http
GET /api/v1/metrics/iceberg.reporting.user_summary
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "name": "iceberg.reporting.user_summary",
  "type": "Metric",
  "owner": "analyst@example.com",
  "team": "@analytics",
  "description": "User summary metrics",
  "tags": ["reporting", "daily"],
  "sql": "SELECT user_id, COUNT(*) FROM events GROUP BY 1",
  "source_table": "iceberg.raw.events",
  "dependencies": ["iceberg.raw.events", "iceberg.dim.users"],
  "created_at": "2025-12-01T10:00:00Z",
  "updated_at": "2025-12-15T14:30:00Z"
}
```

> **⚠️ Field Name Note:** Use `sql` (not `sql_expression`) to match CLI client expectations

**Response (404 Not Found):**
```json
{
  "error": {
    "code": "METRIC_NOT_FOUND",
    "message": "Metric 'iceberg.reporting.user_summary' not found",
    "details": {
      "metric_name": "iceberg.reporting.user_summary"
    }
  }
}
```

---

### 3.3 Register Metric

#### `POST /api/v1/metrics`

**Purpose**: Register a new metric for `dli metric register`

**Request:**
```http
POST /api/v1/metrics
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "name": "iceberg.reporting.new_metric",
  "type": "Metric",
  "owner": "analyst@example.com",
  "team": "@analytics",
  "description": "New metric description",
  "tags": ["reporting"],
  "sql": "SELECT COUNT(*) FROM events",
  "source_table": "iceberg.raw.events"
}
```

**Validation Rules:**
- `name`: Required, pattern: `[catalog].[schema].[name]`
- `sql`: Required, valid SQL expression
- `owner`: Required, valid email format
- `tags`: Optional array, max 10 tags

**Response (201 Created):**
```json
{
  "message": "Metric 'iceberg.reporting.new_metric' registered successfully",
  "name": "iceberg.reporting.new_metric"
}
```

**Response (409 Conflict):**
```json
{
  "error": {
    "code": "METRIC_ALREADY_EXISTS",
    "message": "Metric 'iceberg.reporting.new_metric' already exists",
    "details": {
      "metric_name": "iceberg.reporting.new_metric"
    }
  }
}
```

---

### 3.4 Execute Metric

#### `POST /api/v1/metrics/{name}/run`

**Purpose**: Execute a metric and return results for `dli metric run`

**Request:**
```http
POST /api/v1/metrics/iceberg.reporting.user_summary/run
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "parameters": {
    "date": "2026-01-01",
    "region": "US"
  },
  "limit": 100,
  "timeout": 300
}
```

**Response (200 OK):**
```json
{
  "rows": [
    {"user_id": "user_001", "count": 150},
    {"user_id": "user_002", "count": 120}
  ],
  "row_count": 2,
  "duration_seconds": 1.2,
  "rendered_sql": "SELECT user_id, COUNT(*) FROM events WHERE date = '2026-01-01' GROUP BY 1"
}
```

**Response (408 Request Timeout):**
```json
{
  "error": {
    "code": "METRIC_EXECUTION_TIMEOUT",
    "message": "Metric execution timed out after 300 seconds",
    "details": {
      "metric_name": "iceberg.reporting.user_summary",
      "timeout_seconds": 300
    }
  }
}
```

---

## 4. Datasets APIs

> **Implementation Notes for feature-basecamp-server:**
> - Leverage existing `DatasetEntity` (already implemented)
> - Create `DatasetService` following `PipelineService` pattern
> - Repository pattern: reuse or extend existing `DatasetRepositoryJpa`

### 4.1 List Datasets

#### `GET /api/v1/datasets`

**Purpose**: List datasets with optional filtering for `dli dataset list`

**Request:**
```http
GET /api/v1/datasets?tag=feed&owner=engineer@example.com&search=daily
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:** Same as metrics API

**Response (200 OK):**
```json
[
  {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "engineer@example.com",
    "team": "@data-eng",
    "description": "Daily click aggregations",
    "tags": ["feed", "daily"],
    "created_at": "2025-12-01T10:00:00Z",
    "updated_at": "2025-12-15T14:30:00Z"
  }
]
```

---

### 4.2 Get Dataset Details

#### `GET /api/v1/datasets/{name}`

**Purpose**: Get dataset details by fully qualified name for `dli dataset get`

**Request:**
```http
GET /api/v1/datasets/iceberg.analytics.daily_clicks
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "name": "iceberg.analytics.daily_clicks",
  "type": "Dataset",
  "owner": "engineer@example.com",
  "team": "@data-eng",
  "description": "Daily click aggregations",
  "tags": ["feed", "daily"],
  "sql": "SELECT date, COUNT(*) FROM events GROUP BY 1",
  "dependencies": ["iceberg.raw.events"],
  "schedule": {
    "cron": "0 6 * * *",
    "timezone": "UTC"
  },
  "created_at": "2025-12-01T10:00:00Z",
  "updated_at": "2025-12-15T14:30:00Z"
}
```

---

### 4.3 Register Dataset

#### `POST /api/v1/datasets`

**Purpose**: Register a new dataset for `dli dataset register`

**Request:** Same pattern as metrics registration
**Response:** Same pattern as metrics registration

---

### 4.4 Execute Dataset

#### `POST /api/v1/datasets/{name}/run`

**Purpose**: Execute a dataset and return results for `dli dataset run`

**Request:**
```http
POST /api/v1/datasets/iceberg.analytics.daily_clicks/run
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "parameters": {
    "date": "2026-01-01"
  },
  "limit": 100,
  "timeout": 600
}
```

**Response (200 OK):**
```json
{
  "rows": [
    {"date": "2026-01-01", "clicks": 15000, "conversions": 450},
    {"date": "2026-01-01", "clicks": 12000, "conversions": 380}
  ],
  "row_count": 2,
  "duration_seconds": 5.8,
  "rendered_sql": "SELECT date, COUNT(*) as clicks, SUM(conversions) FROM events WHERE date = '2026-01-01' GROUP BY 1"
}
```

**Response (408 Request Timeout):**
```json
{
  "error": {
    "code": "DATASET_EXECUTION_TIMEOUT",
    "message": "Dataset execution timed out after 600 seconds",
    "details": {
      "dataset_name": "iceberg.analytics.daily_clicks",
      "timeout_seconds": 600
    }
  }
}
```

---

## 5. Implementation Patterns

### 5.1 Domain Layer Implementation

```kotlin
// MetricEntity (Week 1)
@Entity
@Table(name = "metrics")
class MetricEntity(
    @Id val id: String,
    @Column(nullable = false) val name: String,
    @Column(nullable = false) val owner: String,
    @Column val team: String?,
    @Column(length = 1000) val description: String?,
    @Column(name = "sql_expression", nullable = false, length = 10000) val sql: String,
    @ElementCollection val tags: Set<String> = emptySet(),
    @CreationTimestamp val createdAt: LocalDateTime,
    @UpdateTimestamp val updatedAt: LocalDateTime,
) {
    // Following DatasetEntity pattern
}

// Repository Interfaces
interface MetricRepositoryJpa {
    fun save(metric: MetricEntity): MetricEntity
    fun findById(id: String): MetricEntity?
    fun findByNameContainingIgnoreCase(name: String): List<MetricEntity>
}

interface MetricRepositoryDsl {
    fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity>
}
```

### 5.2 Service Layer Implementation

```kotlin
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
) {
    @Transactional
    fun registerMetric(request: CreateMetricRequest): MetricDto {
        // Validation + save logic
        val entity = MetricEntity(
            id = generateId(),
            name = request.name,
            owner = request.owner,
            // ... other fields
        )
        val saved = metricRepositoryJpa.save(entity)
        return MetricMapper.toDto(saved)
    }

    fun listMetrics(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricDto> {
        val entities = metricRepositoryDsl.findByFilters(tag, owner, search, limit, offset)
        return entities.map { MetricMapper.toDto(it) }
    }
}
```

### 5.3 Execution Service Pattern

```kotlin
@Service
class MetricExecutionService(
    private val queryEngine: QueryEngineClient,
    private val metricService: MetricService,
) {
    fun executeMetric(
        metricName: String,
        parameters: Map<String, Any>,
        limit: Int?,
        timeout: Int,
    ): ExecutionResultDto {
        val metric = metricService.getMetric(metricName)
            ?: throw MetricNotFoundException(metricName)

        val renderedSql = renderSqlWithParameters(metric.sql, parameters)
        val result = queryEngine.execute(renderedSql, limit, timeout)

        return ExecutionResultDto(
            rows = result.rows,
            rowCount = result.rowCount,
            durationSeconds = result.durationSeconds,
            renderedSql = renderedSql,
        )
    }
}
```

---

## 6. Testing Requirements

### 6.1 Unit Tests

**Required Coverage: 80%+**

```kotlin
@ExtendWith(MockitoExtension::class)
class MetricServiceTest {
    @Mock private lateinit var metricRepositoryJpa: MetricRepositoryJpa
    @Mock private lateinit var metricRepositoryDsl: MetricRepositoryDsl
    @InjectMocks private lateinit var metricService: MetricService

    @Test
    fun `should register metric successfully`() {
        // Given
        val request = CreateMetricRequest(...)
        val expected = MetricEntity(...)
        given(metricRepositoryJpa.save(any())).willReturn(expected)

        // When
        val result = metricService.registerMetric(request)

        // Then
        assertThat(result.name).isEqualTo(expected.name)
    }
}
```

### 6.2 Integration Tests

**Required: Full API endpoint testing**

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestDatabase(replace = NONE)
class MetricControllerTest {
    @Autowired private lateinit var testRestTemplate: TestRestTemplate
    @Autowired private lateinit var metricRepository: MetricRepositoryJpa

    @Test
    fun `should list metrics with filters`() {
        // Given
        val metric = createTestMetric()
        metricRepository.save(metric)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics?tag=test",
            Array<MetricDto>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
    }
}
```

### 6.3 CLI Integration Tests

**Verify CLI compatibility:**

```bash
# Test metric operations
dli metric list --server-url http://localhost:8081
dli metric get iceberg.test.metric --server-url http://localhost:8081
dli metric register test_metric.yaml --server-url http://localhost:8081

# Test dataset operations
dli dataset list --server-url http://localhost:8081
dli dataset get iceberg.test.dataset --server-url http://localhost:8081

# Test health check
dli debug --server-url http://localhost:8081
```

---

## 🔗 Related Documentation

- **Implementation Timeline**: [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md)
- **Spring Boot Patterns**: [`../INTEGRATION_PATTERNS.md`](../INTEGRATION_PATTERNS.md)
- **Error Handling**: [`../ERROR_CODES.md`](../ERROR_CODES.md)
- **CLI Command Mapping**: [`../CLI_API_MAPPING.md`](../CLI_API_MAPPING.md)

### Next Priority
- **P1 High APIs**: [`P1_HIGH_APIS.md`](./P1_HIGH_APIS.md) - Catalog & Lineage APIs

---

*This document provides implementation-ready specifications for P0 Critical APIs, enabling core CLI functionality within 2.5 weeks.*