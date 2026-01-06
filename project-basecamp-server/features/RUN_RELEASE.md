# RELEASE: Run API Implementation

> **Version:** 1.0.0
> **Status:** ✅ Implemented (100% - 3/3 endpoints)
> **Release Date:** 2026-01-03

---

## 1. Implementation Summary

### 1.1 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **Get Execution Policy** | ✅ Complete | Query rate limits, allowed engines, and current usage |
| **Execute SQL** | ✅ Complete | Ad-hoc SQL execution with parameter substitution and dry-run |
| **Download Results** | ✅ Complete | CSV download with token-based authentication |

### 1.2 Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `module-core-domain/.../model/adhoc/AdHocEnums.kt` | ~25 | ExecutionStatus enum (PENDING, RUNNING, COMPLETED, etc.) |
| `module-core-domain/.../model/adhoc/AdHocExecutionEntity.kt` | ~120 | Ad-hoc execution tracking with lifecycle methods |
| `module-core-domain/.../model/adhoc/UserExecutionQuotaEntity.kt` | ~100 | User rate limit quota with sliding window logic |
| `module-core-domain/.../model/adhoc/RunExecutionConfig.kt` | ~35 | Configuration data class for execution policies |
| `module-core-domain/.../service/ExecutionService.kt` | ~450 | Integrated execution service with parameter substitution, rate limiting, and policy validation |
| `module-core-domain/.../service/ResultStorageService.kt` | ~150 | In-memory result storage with CSV conversion |
| `module-core-domain/.../service/QueryIdGenerator.kt` | ~25 | Deterministic query ID generation for testability |
| `module-core-domain/.../external/QueryEngineClient.kt` | ~35 | Domain interface for BigQuery/Trino integration |
| `module-core-domain/.../external/MockQueryEngineClient.kt` | ~80 | Mock implementation for development and testing |
| `module-core-domain/.../repository/AdHocExecutionRepositoryJpa.kt` | ~20 | Domain interface for execution CRUD |
| `module-core-domain/.../repository/AdHocExecutionRepositoryDsl.kt` | ~25 | Domain interface for execution queries |
| `module-core-domain/.../repository/UserExecutionQuotaRepositoryJpa.kt` | ~15 | Domain interface for quota management |
| `module-core-infra/.../repository/AdHocExecutionRepositoryJpaImpl.kt` | ~35 | JPA repository implementation |
| `module-core-infra/.../repository/AdHocExecutionRepositoryDslImpl.kt` | ~50 | QueryDSL implementation for complex queries |
| `module-core-infra/.../repository/UserExecutionQuotaRepositoryJpaImpl.kt` | ~25 | Quota repository implementation |
| `module-core-infra/.../config/RunConfiguration.kt` | ~30 | Spring configuration for Run API beans |
| `module-core-infra/.../config/RunExecutionProperties.kt` | ~35 | Spring Boot configuration properties |
| `module-server-api/.../controller/RunController.kt` | ~137 | REST endpoints for Run API |
| `module-server-api/.../dto/run/RunDtos.kt` | ~140 | Request/Response DTOs |
| `module-server-api/.../mapper/RunMapper.kt` | ~50 | Entity to DTO mapping |
| **Test Files** | | |
| `module-core-domain/test/.../service/ExecutionServiceTest.kt` | ~957 | Integrated service unit tests (32 scenarios: execution, rate limiting, policy validation) |
| `module-server-api/test/.../controller/RunControllerTest.kt` | ~622 | Controller tests (12 scenarios) |

**Total Lines Added:** ~3,181 lines (1,605 implementation + 1,579 tests)

### 1.3 Files Modified

| File | Changes |
|------|---------|
| `module-server-api/.../exception/GlobalExceptionHandler.kt` | +40 lines - Added Run API exception handlers |
| `module-core-common/.../exception/*.kt` | +150 lines - Added 7 new exception classes |

---

## 2. API Endpoints

### 2.1 Endpoint Summary

| Endpoint | Method | Status | Controller Method | CLI Command |
|----------|--------|--------|-------------------|-------------|
| `/api/v1/run/policy` | GET | ✅ Complete | `getPolicy()` | `dli run --check-policy` |
| `/api/v1/run/execute` | POST | ✅ Complete | `executeSQL()` | `dli run <file.sql>` |
| `/api/v1/run/results/{queryId}/download` | GET | ✅ Complete | `downloadResult()` | `dli run --download csv` |

### 2.2 Get Execution Policy

**Endpoint:** `GET /api/v1/run/policy`

**Response (200 OK):**
```json
{
  "maxQueryDurationSeconds": 1800,
  "maxResultRows": 10000,
  "maxResultSizeMb": 100,
  "allowedEngines": ["bigquery", "trino"],
  "allowedFileTypes": ["csv"],
  "maxFileSizeMb": 10,
  "rateLimits": {
    "queriesPerHour": 50,
    "queriesPerDay": 200
  },
  "currentUsage": {
    "queriesToday": 12,
    "queriesThisHour": 3
  }
}
```

### 2.3 Execute SQL

**Endpoint:** `POST /api/v1/run/execute`

**Request Body:**
```json
{
  "sql": "SELECT * FROM users WHERE date = {date}",
  "engine": "bigquery",
  "parameters": {"date": "2026-01-01"},
  "downloadFormat": "csv",
  "dryRun": false
}
```

**Response (200 OK - Execution Complete):**
```json
{
  "queryId": "adhoc_20260101_100000_xyz789",
  "status": "COMPLETED",
  "executionTimeSeconds": 5.2,
  "rowsReturned": 100,
  "bytesScanned": "500 MB",
  "costUsd": 0.0025,
  "downloadUrls": {
    "csv": "/api/v1/run/results/adhoc_20260101_100000_xyz789/download?format=csv&token=temp_token_123"
  },
  "expiresAt": "2026-01-01T18:00:00Z",
  "renderedSql": "SELECT * FROM users WHERE date = '2026-01-01'"
}
```

**Response (200 OK - Dry Run):**
```json
{
  "queryId": null,
  "status": "VALIDATED",
  "executionTimeSeconds": 0.1,
  "rowsReturned": 0,
  "bytesScanned": null,
  "costUsd": null,
  "downloadUrls": {},
  "expiresAt": null,
  "renderedSql": "SELECT * FROM users WHERE date = '2026-01-01'"
}
```

### 2.4 Download Results

**Endpoint:** `GET /api/v1/run/results/{queryId}/download`

**Query Parameters:**
- `format` (required): Download format (`csv`)
- `token` (required): Download token from execute response

**Response (200 OK):**
- Content-Type: `text/csv`
- Content-Disposition: `attachment; filename="result.csv"`
- Body: CSV file content

---

## 3. Architecture

### 3.1 Hexagonal Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     module-server-api                            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ RunController                                                ││
│  │   - GET /api/v1/run/policy                                   ││
│  │   - POST /api/v1/run/execute                                 ││
│  │   - GET /api/v1/run/results/{queryId}/download               ││
│  └──────────────────────┬──────────────────────────────────────┘│
└─────────────────────────┼───────────────────────────────────────┘
                          │ depends on
┌─────────────────────────▼───────────────────────────────────────┐
│                    module-core-domain                            │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ ExecutionService (Integrated)                                ││
│  │   - executeSQL()                                             ││
│  │   - executeDryRun()                                          ││
│  │   - renderSqlWithParameters()                                ││
│  │   - getPolicy()                                              ││
│  │   - validateExecution()                                      ││
│  │   - incrementUsage()                                         ││
│  └──────────────────────┬──────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ ResultStorageService                                         ││
│  │   - storeResults()                                           ││
│  │   - getResultForDownload()                                   ││
│  │   - convertToCsv()                                           ││
│  └─────────────────────────────────────────────────────────────┘│
│                                                                  │
│  model/adhoc/                                                    │
│  ├── ExecutionStatus (PENDING, RUNNING, COMPLETED, FAILED, etc.)│
│  ├── AdHocExecutionEntity                                        │
│  └── UserExecutionQuotaEntity                                    │
│                                                                  │
│  external/                                                       │
│  ├── QueryEngineClient (interface)                               │
│  └── MockQueryEngineClient (implementation)                      │
└─────────────────────────────────────────────────────────────────┘
                          ▲ implements
┌─────────────────────────┴───────────────────────────────────────┐
│                    module-core-infra                             │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ repository/                                                  ││
│  │ ├── AdHocExecutionRepositoryJpaImpl                          ││
│  │ ├── AdHocExecutionRepositoryDslImpl                          ││
│  │ └── UserExecutionQuotaRepositoryJpaImpl                      ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ config/                                                      ││
│  │ ├── RunConfiguration (Clock, QueryIdGenerator beans)         ││
│  │ └── RunExecutionProperties                                   ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Clock Injection** | `java.time.Clock` injected via constructor | Enables deterministic time-based testing without mocking static methods |
| **Query ID Generation** | `QueryIdGenerator` interface | Allows consistent test IDs while maintaining unique production IDs |
| **In-Memory Storage** | `ConcurrentHashMap` with expiration | MVP simplicity; production can swap to S3/GCS |
| **Rate Limiting** | Per-user sliding window (hourly + daily) | Prevents resource abuse while allowing burst usage |
| **CSV Only (MVP)** | Parquet support deferred | Reduces complexity; CSV covers 90% of use cases |

### 3.3 Testability Features

**Clock Injection:**
```kotlin
@Service
class AdHocExecutionService(
    private val clock: Clock,  // Injected for testability
    private val queryIdGenerator: QueryIdGenerator,
) {
    fun executeSQL(...) {
        val startTime = clock.millis()  // Deterministic in tests
        val queryId = queryIdGenerator.generate()  // Consistent in tests
        // ...
    }
}
```

**Test Setup:**
```kotlin
// Fixed clock for deterministic tests
private val clock = Clock.fixed(
    Instant.parse("2026-01-01T10:00:00Z"),
    ZoneId.of("UTC")
)
private val queryIdGenerator = mockk<QueryIdGenerator>()

@BeforeEach
fun setUp() {
    every { queryIdGenerator.generate() } returns "adhoc_20260101_100000_abc12345"
}
```

---

## 4. Rate Limiting

### 4.1 Rate Limit Configuration

| Limit | Default | Description |
|-------|---------|-------------|
| `queriesPerHour` | 50 | Maximum queries per user per hour |
| `queriesPerDay` | 200 | Maximum queries per user per day |
| `maxQueryDurationSeconds` | 1800 | Maximum query execution time (30 min) |
| `maxResultRows` | 10000 | Maximum rows in result |
| `maxResultSizeMb` | 100 | Maximum result size for download |
| `maxFileSizeMb` | 10 | Maximum SQL file size |

### 4.2 Sliding Window Implementation

```kotlin
class UserExecutionQuotaEntity(
    var queriesToday: Int = 0,
    var queriesThisHour: Int = 0,
    var lastQueryDate: LocalDate,
    var lastQueryHour: Int,
) {
    fun refreshIfNeeded(clock: Clock) {
        val now = LocalDateTime.now(clock)

        // Reset daily counter when day changes
        if (lastQueryDate != now.toLocalDate()) {
            queriesToday = 0
            lastQueryDate = now.toLocalDate()
        }

        // Reset hourly counter when hour changes
        if (lastQueryHour != now.hour) {
            queriesThisHour = 0
            lastQueryHour = now.hour
        }
    }
}
```

### 4.3 Error Responses

**429 Too Many Requests:**
```json
{
  "success": false,
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded: 50 queries per hour",
    "details": {
      "limitType": "queries_per_hour",
      "limit": 50,
      "currentUsage": 50,
      "resetAt": "2026-01-01T11:00:00Z"
    }
  }
}
```

---

## 5. Testing

### 5.1 Test Summary

| Test Class | Tests | Coverage |
|------------|-------|----------|
| `ExecutionServiceTest` | 32 | SQL execution, parameter substitution, rate limiting, policy validation, quota management, error handling |
| `RunControllerTest` | 12 | HTTP endpoints, request validation, response formatting |
| **Total** | **44** | All scenarios covered |

### 5.2 Test Categories

**Unit Tests (32 scenarios):**
- Dry-run validation
- Parameter substitution (string escaping, type handling)
- Rate limit enforcement (hourly, daily)
- Engine validation
- SQL size validation
- Error handling and exception wrapping

**Integration Tests (12 scenarios):**
- HTTP request/response validation
- Request body validation (@NotBlank, @Pattern)
- Error response formatting
- Download functionality

### 5.3 Test Patterns

**MockK-Based Unit Tests:**
```kotlin
@DisplayName("ExecutionService Unit Tests")
class ExecutionServiceTest {
    private val clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"))
    private val queryIdGenerator: QueryIdGenerator = mockk()

    @Test
    fun `should return COMPLETED status for successful execution`() {
        every { queryIdGenerator.generate() } returns testQueryId
        // ExecutionService now includes policy validation internally
        // ...
    }
}
```

**WebMvcTest Integration Tests:**
```kotlin
@WebMvcTest(RunController::class)
@Import(SecurityConfig::class, GlobalExceptionHandler::class)
class RunControllerTest {
    @MockkBean(relaxed = true)
    private lateinit var executionService: ExecutionService

    @Test
    fun `should return 429 for rate limit exceeded`() {
        every { executionService.executeSQL(...) } throws
            RateLimitExceededException(limitType = "queries_per_hour", ...)

        mockMvc.perform(post("/api/v1/run/execute")...)
            .andExpect(status().isTooManyRequests)
            .andExpect(jsonPath("$.error.code").value("RATE_LIMIT_EXCEEDED"))
    }
}
```

---

## 6. CLI Integration

### 6.1 Command Mapping

| CLI Command | API Endpoint | Description |
|-------------|--------------|-------------|
| `dli run --check-policy` | `GET /api/v1/run/policy` | Check rate limits before execution |
| `dli run <file.sql>` | `POST /api/v1/run/execute` | Execute SQL file |
| `dli run --dry-run` | `POST /api/v1/run/execute` | Validate SQL without execution |
| `dli run --download csv` | `POST /api/v1/run/execute` + `GET .../download` | Execute and download results |

### 6.2 Library API Integration

```python
from dli import RunAPI, ExecutionContext, ExecutionMode

# Server-based execution
ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
run_api = RunAPI(context=ctx)

# Check policy
policy = run_api.get_policy()
print(f"Hourly limit: {policy.rate_limits.queries_per_hour}")

# Execute with parameters
result = run_api.run(
    sql="SELECT * FROM users WHERE date = {date}",
    engine="bigquery",
    parameters={"date": "2026-01-01"},
)
print(f"Rows returned: {result.rows_returned}")

# Dry run validation
validation = run_api.dry_run(
    sql="SELECT * FROM users",
    engine="trino",
)
print(f"Rendered SQL: {validation.rendered_sql}")
```

---

## 7. Exception Handling

### 7.1 Exception Hierarchy

| Exception | HTTP Status | Error Code |
|-----------|-------------|------------|
| `RateLimitExceededException` | 429 | `RATE_LIMIT_EXCEEDED` |
| `QueryEngineNotSupportedException` | 400 | `UNSUPPORTED_ENGINE` |
| `QueryTooLargeException` | 400 | `QUERY_TOO_LARGE` |
| `InvalidSqlException` | 400 | `INVALID_SQL` |
| `QueryExecutionTimeoutException` | 408 | `QUERY_EXECUTION_TIMEOUT` |
| `ResultSizeLimitExceededException` | 413 | `RESULT_SIZE_LIMIT_EXCEEDED` |
| `ResultNotFoundException` | 404 | `RESULT_NOT_FOUND` |
| `InvalidDownloadTokenException` | 400 | `INVALID_DOWNLOAD_TOKEN` |
| `AdHocExecutionException` | 500 | `ADHOC_EXECUTION_ERROR` |

### 7.2 GlobalExceptionHandler Updates

```kotlin
@ExceptionHandler(RateLimitExceededException::class)
fun handleRateLimitExceeded(ex: RateLimitExceededException): ResponseEntity<ErrorResponse> {
    return ResponseEntity
        .status(HttpStatus.TOO_MANY_REQUESTS)
        .body(ErrorResponse.failure(
            code = "RATE_LIMIT_EXCEEDED",
            message = ex.message,
            details = mapOf(
                "limitType" to ex.limitType,
                "limit" to ex.limit,
                "currentUsage" to ex.currentUsage,
                "resetAt" to ex.resetAt.toString(),
            )
        ))
}
```

---

## 8. Configuration

### 8.1 Application Properties

```yaml
basecamp:
  run:
    max-query-duration-seconds: 1800
    max-result-rows: 10000
    max-result-size-mb: 100
    allowed-engines:
      - bigquery
      - trino
    allowed-file-types:
      - csv
    max-file-size-mb: 10
    queries-per-hour: 50
    queries-per-day: 200
    result-expiration-hours: 8
```

### 8.2 Bean Configuration

```kotlin
@Configuration
class RunConfiguration {
    @Bean
    fun systemClock(): Clock = Clock.systemUTC()

    @Bean
    fun queryIdGenerator(): QueryIdGenerator = DefaultQueryIdGenerator()

    @Bean
    @ConditionalOnProperty(name = ["basecamp.run.query-engine.mock"], havingValue = "true")
    fun mockQueryEngineClient(): QueryEngineClient = MockQueryEngineClient()
}
```

---

## 9. Future Improvements

### 9.1 MVP+1 Features (Not Implemented)

| Feature | Priority | Description |
|---------|----------|-------------|
| Parquet Download | Medium | Add Apache Parquet format support |
| Real BigQuery Client | High | Replace MockQueryEngineClient with real integration |
| Real Trino Client | High | Add Trino query engine support |
| S3 Result Storage | Medium | Replace in-memory storage with S3 |
| Async Execution | Medium | Long-running queries with polling |

### 9.2 Known Limitations

| Limitation | Impact | Mitigation |
|------------|--------|------------|
| In-memory result storage | Results lost on restart | MVP only; production uses S3 |
| Mock query engine | No real SQL execution | Simulates realistic responses |
| CSV only | No Parquet support | CSV covers most use cases |
| 8-hour expiration | Results expire | Download immediately after execution |

---

## 10. Build Verification

```bash
$ ./gradlew clean build
BUILD SUCCESSFUL in 45s
71 actionable tasks: 71 executed

# All tests pass
# ktlint check passes
# No critical warnings
```

---

*Last Updated: 2026-01-03*
