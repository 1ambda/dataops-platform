# Query API Feature Specification

> **Version:** 1.0.0 | **Status:** ✅ Implemented | **Priority:** P3 Low
> **CLI Commands:** `dli query list/show/cancel` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Timeline:** Week 10 | **Endpoints:** 3/3 Complete
>
> **📦 Data Source:** Self-managed JPA (쿼리 메타데이터 저장)
> **Entities:** `QueryExecutionEntity`
>
> **📖 Implementation Details:** [`QUERY_RELEASE.md`](./QUERY_RELEASE.md)

---

## 1. Overview

### 1.1 Purpose

The Query Metadata API provides endpoints for querying execution history, viewing query details, and cancelling running queries. This enables the `dli query` CLI commands to interact with query execution metadata stored in Basecamp Server.

### 1.2 Key Features

| Feature | Description |
|---------|-------------|
| **Query History** | List and search past query executions with filtering |
| **Query Details** | View detailed execution information including query plans |
| **Query Cancellation** | Cancel running queries with reason tracking |
| **Access Control** | Scope-based visibility (my, system, user, all) |
| **Multi-Engine Support** | BigQuery, Trino query metadata aggregation |

### 1.3 CLI Integration

```bash
# List queries (default: my queries)
dli query list
dli query list --scope system --status running

# Show query details
dli query show query_20260101_100000_abc123

# Cancel running query
dli query cancel query_20260101_100000_abc123 --reason "Resource optimization"
```

---

## 2. CLI Command Mapping

### 2.1 Command to API Mapping

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli query list` | GET | `/api/v1/catalog/queries` | List query execution history |
| `dli query show <id>` | GET | `/api/v1/catalog/queries/{query_id}` | Get query execution details |
| `dli query cancel <id>` | POST | `/api/v1/catalog/queries/{query_id}/cancel` | Cancel a running query |

### 2.2 CLI Options to Query Parameters

| CLI Option | Query Parameter | Default | Description |
|------------|----------------|---------|-------------|
| `--scope` | `scope` | `my` | Query visibility scope |
| `--status` | `status` | - | Filter by execution status |
| `--start-date` | `start_date` | - | Filter from date |
| `--end-date` | `end_date` | - | Filter to date |
| `--limit` | `limit` | 50 | Max results per page |
| `--offset` | `offset` | 0 | Pagination offset |

---

## 3. API Specifications

### 3.1 List Queries ✅ Implemented

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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 400 | `INVALID_DATE_RANGE` | Invalid date format or range |
| 401 | `UNAUTHORIZED` | Missing or invalid token |
| 403 | `FORBIDDEN` | Insufficient permissions for scope |

---

### 3.2 Get Query Details ✅ Implemented

#### `GET /api/v1/catalog/queries/{query_id}`

**Purpose**: Get query execution details for `dli query show`

**Request:**
```http
GET /api/v1/catalog/queries/query_20260101_100000_abc123
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query_id` | string | Yes | Unique query identifier |

**Response (200 OK - Completed Query):**
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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 401 | `UNAUTHORIZED` | Missing or invalid token |
| 403 | `FORBIDDEN` | Not authorized to view this query |
| 404 | `QUERY_NOT_FOUND` | Query ID does not exist |

---

### 3.3 Cancel Query ✅ Implemented

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

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `query_id` | string | Yes | Unique query identifier |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `reason` | string | No | Cancellation reason for audit |

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

**Response (409 Conflict - Not Cancellable):**
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

**Error Responses:**

| Status | Code | Description |
|--------|------|-------------|
| 401 | `UNAUTHORIZED` | Missing or invalid token |
| 403 | `FORBIDDEN` | Not authorized to cancel this query |
| 404 | `QUERY_NOT_FOUND` | Query ID does not exist |
| 409 | `QUERY_NOT_CANCELLABLE` | Query already completed/failed/cancelled |

---

## 4. Domain Model

### 4.1 Entity Model

```kotlin
@Entity
@Table(name = "query_executions")
class QueryExecutionEntity(
    @Id
    @Column(name = "query_id")
    val queryId: String,

    @Column(name = "sql", columnDefinition = "TEXT")
    val sql: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    var status: QueryStatus,

    @Column(name = "submitted_by")
    val submittedBy: String,

    @Column(name = "submitted_at")
    val submittedAt: Instant,

    @Column(name = "started_at")
    var startedAt: Instant? = null,

    @Column(name = "completed_at")
    var completedAt: Instant? = null,

    @Column(name = "duration_seconds")
    var durationSeconds: Double? = null,

    @Column(name = "rows_returned")
    var rowsReturned: Long? = null,

    @Column(name = "bytes_scanned")
    var bytesScanned: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "engine")
    val engine: QueryEngine,

    @Column(name = "cost_usd")
    var costUsd: Double? = null,

    @Column(name = "execution_details", columnDefinition = "JSON")
    var executionDetails: String? = null,

    @Column(name = "error_details", columnDefinition = "JSON")
    var errorDetails: String? = null,

    @Column(name = "cancelled_by")
    var cancelledBy: String? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: Instant? = null,

    @Column(name = "cancellation_reason")
    var cancellationReason: String? = null,

    @Column(name = "is_system_query")
    val isSystemQuery: Boolean = false,
)

enum class QueryStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class QueryEngine {
    BIGQUERY,
    TRINO,
    SPARK
}
```

### 4.2 DTO Models

```kotlin
data class QueryListItemDto(
    val queryId: String,
    val sql: String,
    val status: QueryStatus,
    val submittedBy: String,
    val submittedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationSeconds: Double?,
    val rowsReturned: Long?,
    val bytesScanned: String?,
    val engine: QueryEngine,
    val costUsd: Double?,
)

data class QueryDetailDto(
    val queryId: String,
    val sql: String,
    val status: QueryStatus,
    val submittedBy: String,
    val submittedAt: Instant,
    val startedAt: Instant?,
    val completedAt: Instant?,
    val durationSeconds: Double?,
    val rowsReturned: Long?,
    val bytesScanned: String?,
    val engine: QueryEngine,
    val costUsd: Double?,
    val executionDetails: ExecutionDetailsDto?,
    val error: QueryErrorDto?,
)

data class ExecutionDetailsDto(
    val jobId: String?,
    val queryPlan: List<QueryPlanStageDto>?,
    val tablesAccessed: List<String>?,
)

data class QueryPlanStageDto(
    val stage: String,
    val operation: String,
    val inputRows: Long?,
    val outputRows: Long?,
)

data class QueryErrorDto(
    val code: String,
    val message: String,
    val details: Map<String, Any>?,
)

data class CancelQueryResponseDto(
    val queryId: String,
    val status: QueryStatus,
    val cancelledBy: String,
    val cancelledAt: Instant,
    val reason: String?,
)
```

### 4.3 Command/Query Objects

```kotlin
data class ListQueriesQuery(
    val scope: QueryScope = QueryScope.MY,
    val status: QueryStatus? = null,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

data class CancelQueryCommand(
    val queryId: String,
    val reason: String? = null,
)

enum class QueryScope {
    MY,      // Current user's queries only
    SYSTEM,  // System-generated queries
    USER,    // Specific user's queries (requires role)
    ALL      // All queries (requires role)
}
```

---

## 5. Access Control

### 5.1 Scope-Based Access Matrix

| Scope | Required Role | Description |
|-------|---------------|-------------|
| `my` | Any authenticated user | User's own queries only |
| `system` | Any authenticated user | System-generated queries (DAG runs, scheduled jobs) |
| `user` | `query:read:all` | Query by specific user ID |
| `all` | `query:read:all` | All queries across all users |

### 5.2 Cancellation Authorization

| Scenario | Authorization Rule |
|----------|-------------------|
| Own query | Always allowed |
| Other user's query | Requires `query:cancel:all` role |
| System query | Requires `query:cancel:system` role |

### 5.3 Implementation Pattern

```kotlin
@Service
class QueryAccessControlService(
    private val securityContext: SecurityContextHolder,
) {
    fun validateListAccess(scope: QueryScope, targetUserId: String?) {
        val currentUser = getCurrentUser()

        when (scope) {
            QueryScope.MY, QueryScope.SYSTEM -> {
                // All authenticated users allowed
            }
            QueryScope.USER, QueryScope.ALL -> {
                if (!currentUser.hasRole("query:read:all")) {
                    throw AccessDeniedException("Insufficient permissions for scope: $scope")
                }
            }
        }
    }

    fun validateCancelAccess(query: QueryExecutionEntity) {
        val currentUser = getCurrentUser()

        when {
            query.submittedBy == currentUser.email -> {
                // Owner can always cancel
            }
            query.isSystemQuery -> {
                if (!currentUser.hasRole("query:cancel:system")) {
                    throw AccessDeniedException("Cannot cancel system queries")
                }
            }
            else -> {
                if (!currentUser.hasRole("query:cancel:all")) {
                    throw AccessDeniedException("Cannot cancel other users' queries")
                }
            }
        }
    }

    fun buildScopeFilter(scope: QueryScope, targetUserId: String?): QueryScopeFilter {
        val currentUser = getCurrentUser()

        return when (scope) {
            QueryScope.MY -> QueryScopeFilter(
                submittedBy = currentUser.email,
                isSystemQuery = false
            )
            QueryScope.SYSTEM -> QueryScopeFilter(
                isSystemQuery = true
            )
            QueryScope.USER -> QueryScopeFilter(
                submittedBy = targetUserId ?: throw IllegalArgumentException("User ID required")
            )
            QueryScope.ALL -> QueryScopeFilter()  // No filter
        }
    }
}
```

---

## 6. Query Engine Integration

### 6.1 Multi-Engine Query Metadata Aggregation

```kotlin
@Service
class QueryMetadataService(
    private val queryRepository: QueryExecutionRepositoryJpa,
    private val bigQueryClient: BigQueryMetadataClient,
    private val trinoClient: TrinoMetadataClient,
) {
    fun listQueries(query: ListQueriesQuery, filter: QueryScopeFilter): List<QueryListItemDto> {
        return queryRepository.findByFilter(
            submittedBy = filter.submittedBy,
            isSystemQuery = filter.isSystemQuery,
            status = query.status,
            startDate = query.startDate,
            endDate = query.endDate,
            limit = query.limit,
            offset = query.offset
        ).map { it.toListItemDto() }
    }

    fun getQueryDetails(queryId: String): QueryDetailDto {
        val query = queryRepository.findById(queryId)
            ?: throw QueryNotFoundException(queryId)

        // Enrich with real-time execution details if running
        val executionDetails = if (query.status == QueryStatus.RUNNING) {
            fetchRealTimeExecutionDetails(query)
        } else {
            parseExecutionDetails(query.executionDetails)
        }

        return query.toDetailDto(executionDetails)
    }

    private fun fetchRealTimeExecutionDetails(query: QueryExecutionEntity): ExecutionDetailsDto? {
        return when (query.engine) {
            QueryEngine.BIGQUERY -> bigQueryClient.getJobDetails(query.queryId)
            QueryEngine.TRINO -> trinoClient.getQueryDetails(query.queryId)
            else -> null
        }
    }
}
```

### 6.2 Query Cancellation Service

```kotlin
@Service
class QueryCancellationService(
    private val queryRepository: QueryExecutionRepositoryJpa,
    private val bigQueryClient: BigQueryClient,
    private val trinoClient: TrinoClient,
    private val accessControl: QueryAccessControlService,
) {
    @Transactional
    fun cancelQuery(command: CancelQueryCommand): CancelQueryResponseDto {
        val query = queryRepository.findById(command.queryId)
            ?: throw QueryNotFoundException(command.queryId)

        // Validate authorization
        accessControl.validateCancelAccess(query)

        // Check if cancellable
        if (query.status !in listOf(QueryStatus.PENDING, QueryStatus.RUNNING)) {
            throw QueryNotCancellableException(
                queryId = command.queryId,
                currentStatus = query.status
            )
        }

        // Cancel in query engine
        cancelInEngine(query)

        // Update database
        val currentUser = getCurrentUser()
        val now = Instant.now()

        query.status = QueryStatus.CANCELLED
        query.cancelledBy = currentUser.email
        query.cancelledAt = now
        query.cancellationReason = command.reason
        query.completedAt = now

        queryRepository.save(query)

        return CancelQueryResponseDto(
            queryId = query.queryId,
            status = QueryStatus.CANCELLED,
            cancelledBy = currentUser.email,
            cancelledAt = now,
            reason = command.reason
        )
    }

    private fun cancelInEngine(query: QueryExecutionEntity) {
        try {
            when (query.engine) {
                QueryEngine.BIGQUERY -> bigQueryClient.cancelJob(query.queryId)
                QueryEngine.TRINO -> trinoClient.cancelQuery(query.queryId)
                else -> log.warn("Cancellation not supported for engine: ${query.engine}")
            }
        } catch (e: Exception) {
            log.error("Failed to cancel query in engine: ${query.queryId}", e)
            // Continue with database update even if engine cancellation fails
        }
    }
}
```

### 6.3 Query Engine Clients

```kotlin
interface QueryEngineMetadataClient {
    fun getQueryDetails(queryId: String): ExecutionDetailsDto?
    fun cancelQuery(queryId: String): Boolean
}

@Component
class BigQueryMetadataClient(
    private val bigQuery: BigQuery,
) : QueryEngineMetadataClient {
    override fun getQueryDetails(queryId: String): ExecutionDetailsDto? {
        val job = bigQuery.getJob(JobId.of(queryId)) ?: return null
        val stats = job.getStatistics<QueryStatistics>()

        return ExecutionDetailsDto(
            jobId = job.jobId.job,
            queryPlan = stats.queryPlan?.map { stage ->
                QueryPlanStageDto(
                    stage = stage.name,
                    operation = stage.steps.firstOrNull()?.kind ?: "Unknown",
                    inputRows = stage.recordsRead,
                    outputRows = stage.recordsWritten
                )
            },
            tablesAccessed = stats.referencedTables?.map { it.table }
        )
    }

    override fun cancelQuery(queryId: String): Boolean {
        return bigQuery.cancel(JobId.of(queryId))
    }
}

@Component
class TrinoMetadataClient(
    private val trinoClient: TrinoClient,
) : QueryEngineMetadataClient {
    override fun getQueryDetails(queryId: String): ExecutionDetailsDto? {
        val queryInfo = trinoClient.getQueryInfo(queryId) ?: return null

        return ExecutionDetailsDto(
            jobId = queryInfo.queryId,
            queryPlan = queryInfo.outputStage?.let { parseTrinoStages(it) },
            tablesAccessed = queryInfo.inputs?.map { it.table.tableName }
        )
    }

    override fun cancelQuery(queryId: String): Boolean {
        trinoClient.cancelQuery(queryId)
        return true
    }
}
```

---

## 7. Testing Requirements

### 7.1 Unit Tests

| Test Case | Description |
|-----------|-------------|
| `QueryMetadataService.listQueries` | Filter by scope, status, date range |
| `QueryMetadataService.getQueryDetails` | Return details with execution plan |
| `QueryCancellationService.cancelQuery` | Update status and notify engine |
| `QueryAccessControlService.validateListAccess` | Scope permission checks |
| `QueryAccessControlService.validateCancelAccess` | Owner and role-based cancellation |

### 7.2 Integration Tests

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class QueryApiIntegrationTest {
    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var queryRepository: QueryExecutionRepositoryJpa

    @Test
    fun `list queries returns user's own queries by default`() {
        // Given
        val userEmail = "test@example.com"
        createTestQueries(userEmail, 5)

        // When & Then
        mockMvc.perform(
            get("/api/v1/catalog/queries")
                .header("Authorization", "Bearer ${tokenFor(userEmail)}")
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[0].submitted_by").value(userEmail))
    }

    @Test
    fun `cancel query succeeds for own running query`() {
        // Given
        val userEmail = "test@example.com"
        val queryId = createRunningQuery(userEmail)

        // When & Then
        mockMvc.perform(
            post("/api/v1/catalog/queries/$queryId/cancel")
                .header("Authorization", "Bearer ${tokenFor(userEmail)}")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"reason": "No longer needed"}""")
        )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.cancelled_by").value(userEmail))
    }

    @Test
    fun `cancel query returns 409 for completed query`() {
        // Given
        val userEmail = "test@example.com"
        val queryId = createCompletedQuery(userEmail)

        // When & Then
        mockMvc.perform(
            post("/api/v1/catalog/queries/$queryId/cancel")
                .header("Authorization", "Bearer ${tokenFor(userEmail)}")
                .contentType(MediaType.APPLICATION_JSON)
        )
        .andExpect(status().isConflict)
        .andExpect(jsonPath("$.error.code").value("QUERY_NOT_CANCELLABLE"))
    }

    @Test
    fun `list all queries requires query_read_all role`() {
        // Given
        val userWithoutRole = "basic@example.com"

        // When & Then
        mockMvc.perform(
            get("/api/v1/catalog/queries?scope=all")
                .header("Authorization", "Bearer ${tokenFor(userWithoutRole)}")
        )
        .andExpect(status().isForbidden)
    }
}
```

### 7.3 E2E Test Scenarios

| Scenario | Steps | Expected Result |
|----------|-------|-----------------|
| List my queries | 1. Execute query 2. `dli query list` | Shows only user's queries |
| Show query details | 1. Get query ID 2. `dli query show <id>` | Displays execution plan |
| Cancel running query | 1. Start long query 2. `dli query cancel <id>` | Query cancelled, engine notified |
| Access control | 1. `dli query list --scope all` as non-admin | Returns 403 Forbidden |

---

## 8. Related Documents

### 8.1 Internal References

| Document | Description |
|----------|-------------|
| [`../IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Phase 4 implementation timeline |
| [`../BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md) | Architecture overview |
| [`../ERROR_CODES.md`](./ERROR_CODES.md) | Error code definitions |
| [`archive/P3_LOW_APIS.md`](./archive/P3_LOW_APIS.md) | Full P3 API specifications |

### 8.2 CLI Documentation

| Document | Description |
|----------|-------------|
| `project-interface-cli/README.md` | CLI usage guide |
| `project-interface-cli/docs/PATTERNS.md` | CLI implementation patterns |

### 8.3 Implementation Dependencies

| Dependency | Purpose |
|------------|---------|
| `QueryExecutionEntity` | Persist query metadata |
| `BigQueryClient` | BigQuery job management |
| `TrinoClient` | Trino query management |
| `OAuth2 Security` | Authentication and authorization |

---

## Appendix A: Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `QUERY_NOT_FOUND` | 404 | Query ID does not exist |
| `QUERY_NOT_CANCELLABLE` | 409 | Query already in terminal state |
| `INVALID_DATE_RANGE` | 400 | Invalid date format or range |
| `UNAUTHORIZED` | 401 | Missing or invalid token |
| `FORBIDDEN` | 403 | Insufficient permissions |

---

## Appendix B: Database Schema

```sql
CREATE TABLE query_executions (
    query_id VARCHAR(255) PRIMARY KEY,
    sql TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_by VARCHAR(255) NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    duration_seconds DOUBLE,
    rows_returned BIGINT,
    bytes_scanned VARCHAR(50),
    engine VARCHAR(20) NOT NULL,
    cost_usd DOUBLE,
    execution_details JSON,
    error_details JSON,
    cancelled_by VARCHAR(255),
    cancelled_at TIMESTAMP,
    cancellation_reason TEXT,
    is_system_query BOOLEAN DEFAULT FALSE,

    INDEX idx_submitted_by (submitted_by),
    INDEX idx_status (status),
    INDEX idx_submitted_at (submitted_at),
    INDEX idx_is_system_query (is_system_query)
);
```

---

*Document created: 2026-01-01 | Last updated: 2026-01-01*

---

## Appendix B: Review Feedback

> **Reviewed by:** expert-spring-kotlin Agent | **Date:** 2026-01-01 | **Rating:** 4.0/5

### Strengths
- Excellent access control design with scope-based filtering
- Good query engine abstraction with `QueryEngineMetadataClient` interface
- Correct `@Transactional` placement on write operations
- Comprehensive DTO models with clear separation

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | `SecurityContextHolder` injection issue - it's static, not injectable | Use `SecurityContextHolder.getContext().authentication.principal` |
| **Medium** | Entity has too many mutable fields (`var`) | Use immutable fields with copy methods |
| **Medium** | Access control uses strings for roles | Use sealed classes or enums for permissions |
| **Low** | `QueryMetadataService` missing `@Transactional(readOnly = true)` | Add annotation for read services |

### Required Changes Before Implementation
1. Fix `SecurityContextHolder` usage (it's static, not injectable)
2. Use sealed classes for permissions
3. Add immutable entity pattern with copy methods
4. Mark read services with `@Transactional(readOnly = true)`
