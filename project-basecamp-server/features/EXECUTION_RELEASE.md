# RELEASE: Execution API Implementation

> **Version:** 1.0.0
> **Status:** Implemented (100% - 4/4 endpoints)
> **Release Date:** 2026-01-08

---

## 1. Implementation Summary

### 1.1 Overview

Execution API provides a unified endpoint for executing CLI-rendered SQL queries through Basecamp Server. This API enables `ExecutionMode.SERVER` in the CLI, where the CLI renders SQL locally and sends it to the server for execution.

### 1.2 Completed Features

| Feature | Status | Description |
|---------|--------|-------------|
| **Execute Dataset SQL** | Completed | Execute CLI-rendered Dataset SQL via server |
| **Execute Metric SQL** | Completed | Execute CLI-rendered Metric SQL via server |
| **Execute Quality SQL** | Completed | Execute CLI-rendered Quality test SQLs via server |
| **Execute Ad-hoc SQL** | Completed | Execute raw SQL queries via server |

### 1.3 Files Created

| File | Lines | Purpose |
|------|-------|---------|
| `module-server-api/.../controller/ExecutionController.kt` | 128 | REST endpoints for CLI-rendered SQL execution |
| `module-server-api/.../dto/execution/ExecutionDtos.kt` | 202 | Request/Response DTOs with Flat + Prefix style |
| `module-server-api/.../mapper/ExecutionMapper.kt` | ~80 | DTO to domain object mapping |
| `module-core-domain/.../service/ExecutionService.kt` | 1091 | Domain service with execution logic |
| `module-core-domain/.../projection/execution/ExecutionProjections.kt` | ~120 | Result projections for different execution types |
| `module-server-api/test/.../controller/ExecutionControllerTest.kt` | 943 | Comprehensive controller tests (35 test scenarios) |

**Total Lines Added:** ~2,500+ lines

### 1.4 Files Modified

| File | Changes |
|------|---------|
| `module-core-domain/.../service/ExecutionService.kt` | +400 lines - Added `executeRendered*` methods |
| `features/_STATUS.md` | Updated with Execution API status |

---

## 2. API Endpoints

### 2.1 Endpoint Summary

| Endpoint | Method | Status | Controller Method | CLI Command |
|----------|--------|--------|-------------------|-------------|
| `/api/v1/execution/datasets/run` | POST | Completed | `executeDataset()` | `dli dataset run` (SERVER mode) |
| `/api/v1/execution/metrics/run` | POST | Completed | `executeMetric()` | `dli metric run` (SERVER mode) |
| `/api/v1/execution/quality/run` | POST | Completed | `executeQuality()` | `dli quality run` (SERVER mode) |
| `/api/v1/execution/sql/run` | POST | Completed | `executeSql()` | `dli run <file>` |

### 2.2 Execute Dataset SQL

**Endpoint:** `POST /api/v1/execution/datasets/run`

**Request Body:**

```json
{
  "rendered_sql": "SELECT * FROM table WHERE date = '2026-01-01'",
  "parameters": {"date": "2026-01-01"},
  "execution_timeout": 300,
  "execution_limit": 1000,
  "transpile_source_dialect": "trino",
  "transpile_target_dialect": "bigquery",
  "transpile_used_server_policy": false,
  "resource_name": "my_dataset",
  "original_spec": {}
}
```

**Response:**

```json
{
  "execution_id": "exec-uuid-1234",
  "status": "COMPLETED",
  "rows": [{"id": 1, "name": "Example"}],
  "row_count": 1,
  "duration_seconds": 1.523,
  "rendered_sql": "SELECT * FROM table WHERE date = '2026-01-01'"
}
```

### 2.3 Execute Metric SQL

**Endpoint:** `POST /api/v1/execution/metrics/run`

**Request Body:** Same structure as Dataset execution request.

**Response:** Same structure as Dataset execution response.

### 2.4 Execute Quality SQL

**Endpoint:** `POST /api/v1/execution/quality/run`

**Request Body:**

```json
{
  "resource_name": "user_events",
  "tests": [
    {
      "name": "not_null_check",
      "type": "not_null",
      "rendered_sql": "SELECT COUNT(*) FROM table WHERE id IS NULL"
    },
    {
      "name": "unique_check",
      "type": "unique",
      "rendered_sql": "SELECT id, COUNT(*) FROM table GROUP BY id HAVING COUNT(*) > 1"
    }
  ],
  "execution_timeout": 300,
  "transpile_source_dialect": "trino",
  "transpile_target_dialect": "bigquery"
}
```

**Response:**

```json
{
  "execution_id": "exec-uuid-5678",
  "status": "COMPLETED",
  "results": [
    {
      "test_name": "not_null_check",
      "passed": true,
      "failed_count": 0,
      "failed_rows": [],
      "duration_ms": 150
    },
    {
      "test_name": "unique_check",
      "passed": false,
      "failed_count": 3,
      "failed_rows": [{"id": 1}],
      "duration_ms": 200
    }
  ],
  "total_tests": 2,
  "passed_tests": 1,
  "failed_tests": 1,
  "total_duration_ms": 350
}
```

### 2.5 Execute Ad-hoc SQL

**Endpoint:** `POST /api/v1/execution/sql/run`

**Request Body:**

```json
{
  "sql": "SELECT * FROM catalog.schema.table LIMIT 100",
  "parameters": {},
  "execution_timeout": 300,
  "execution_limit": 1000,
  "target_dialect": "bigquery"
}
```

**Response:** Same structure as Dataset execution response.

---

## 3. Architecture

### 3.1 Hexagonal Architecture Compliance

| Layer | Component | Implementation |
|-------|-----------|----------------|
| **API** | Controller | `ExecutionController` - REST endpoints |
| **API** | DTOs | `ExecutionDtos.kt` - Request/Response separation |
| **API** | Mapper | `ExecutionMapper` - DTO to domain mapping |
| **Domain** | Services | `ExecutionService` - Execution orchestration |
| **Domain** | Projections | `ExecutionProjections.kt` - Result models |
| **Infrastructure** | QueryEngineClient | Mock/Real query engine adapters |

### 3.2 DTO Design Pattern: Flat + Prefix Style

The DTOs use a "Flat + Prefix" style for better readability and consistency:

```kotlin
data class DatasetExecutionRequest(
    // SQL (required)
    @JsonProperty("rendered_sql")
    val renderedSql: String,

    // Execution options (with execution_ prefix)
    @JsonProperty("execution_timeout")
    val executionTimeout: Int = 300,
    @JsonProperty("execution_limit")
    val executionLimit: Int? = null,

    // Transpile metadata (with transpile_ prefix)
    @JsonProperty("transpile_source_dialect")
    val transpileSourceDialect: String? = null,
    @JsonProperty("transpile_target_dialect")
    val transpileTargetDialect: String? = null,

    // Origin info
    @JsonProperty("resource_name")
    val resourceName: String? = null,
)
```

**Design Rationale:**
- Flat structure avoids nested JSON complexity
- Prefix grouping (`execution_`, `transpile_`) provides logical organization
- Jackson `@JsonProperty` ensures snake_case API contract while maintaining camelCase in Kotlin

### 3.3 Service Layer Integration

```kotlin
@Service
class ExecutionService(
    private val queryEngineClient: QueryEngineClient,
    private val executionHistoryRepositoryJpa: ExecutionHistoryRepositoryJpa,
    private val executionResultRepositoryJpa: ExecutionResultRepositoryJpa,
    // ...
) {
    fun executeRenderedDatasetSql(params: RenderedDatasetExecutionParams): RenderedExecutionResultProjection
    fun executeRenderedMetricSql(params: RenderedMetricExecutionParams): RenderedExecutionResultProjection
    fun executeRenderedQualitySql(params: RenderedQualityExecutionParams): RenderedQualityExecutionResultProjection
    fun executeRenderedAdHocSql(params: RenderedAdHocExecutionParams): RenderedExecutionResultProjection
}
```

---

## 4. Validation

### 4.1 Request Validation Rules

| Field | Validation | Error Message |
|-------|------------|---------------|
| `rendered_sql` / `sql` | @NotBlank, @Size(max=100000) | "Rendered SQL is required", "SQL must not exceed 100,000 characters" |
| `execution_timeout` | @Min(1), @Max(3600) | "Execution timeout must be at least 1 second", "...must not exceed 3600 seconds" |
| `execution_limit` | @Min(1), @Max(10000) | "Execution limit must be at least 1", "...must not exceed 10,000 rows" |
| `tests` (Quality) | @NotEmpty, @Valid | "At least one test is required" |
| `test.name` | @NotBlank | "Test name is required" |
| `test.type` | @NotBlank | "Test type is required" |

### 4.2 Error Codes

| Code | Description | HTTP Status |
|------|-------------|-------------|
| `EXEC-001` | SQL transpile/render error | 400 |
| `EXEC-002` | Query engine connection error | 503 |
| `EXEC-003` | SQL execution error | 400 |
| `EXEC-004` | Execution timeout | 408 |
| `EXEC-005` | Permission denied | 403 |
| `EXEC-006` | Resource not found | 404 |

---

## 5. Test Coverage

### 5.1 Test Summary

| Test Class | Tests | Status |
|------------|-------|--------|
| `ExecutionControllerTest` | 35 | All Passed |

### 5.2 Test Breakdown by Endpoint

| Endpoint | Test Scenarios |
|----------|----------------|
| `POST /execution/datasets/run` | Success, validation errors, timeout, SQL execution failure |
| `POST /execution/metrics/run` | Success, validation errors, timeout, SQL execution failure |
| `POST /execution/quality/run` | Success, partial failures, all tests pass/fail, validation |
| `POST /execution/sql/run` | Success, validation errors, timeout, permission errors |

### 5.3 Test Patterns Used

- **@SpringBootTest + @AutoConfigureMockMvc**: Full integration testing
- **@MockkBean**: Service mocking with MockK (Spring Boot 4 compatible)
- **@Nested + @DisplayName**: Organized test structure
- **Validation Testing**: Request body validation scenarios

---

## 6. CLI Integration

### 6.1 ExecutionMode.SERVER Flow

```
CLI (dli)                        Basecamp Server
    │                                   │
    ├── Render SQL locally ─────────────┤
    │   (parameter substitution,        │
    │    dialect conversion)            │
    │                                   │
    ├── POST /api/v1/execution/**/run ──┤
    │   (with rendered_sql)             │
    │                                   │
    │                           ┌───────┴───────┐
    │                           │ ExecutionService │
    │                           │ - Query Engine   │
    │                           │ - History Save   │
    │                           └───────┬───────┘
    │                                   │
    │◄── ExecutionResultDto ────────────┤
    │                                   │
    └── Display results ────────────────┘
```

### 6.2 CLI Command Mapping

| CLI Command | API Endpoint | Description |
|-------------|--------------|-------------|
| `dli dataset run --mode server` | `POST /api/v1/execution/datasets/run` | Execute CLI-rendered Dataset SQL |
| `dli metric run --mode server` | `POST /api/v1/execution/metrics/run` | Execute CLI-rendered Metric SQL |
| `dli quality run --mode server` | `POST /api/v1/execution/quality/run` | Execute CLI-rendered Quality SQL |
| `dli run <file>` | `POST /api/v1/execution/sql/run` | Execute Ad-hoc SQL |

---

## 7. Quality Metrics

| Metric | Value |
|--------|-------|
| **Endpoints Completed** | 4/4 (100%) |
| **Total Tests** | 35 |
| **Test Pass Rate** | 100% |
| **Lines Added** | ~2,500+ |
| **Architecture Compliance** | Full hexagonal |

---

## 8. Related Documentation

| Document | Description |
|----------|-------------|
| [`EXECUTION_FEATURE.md`](./EXECUTION_FEATURE.md) | Original feature specification |
| [`RUN_RELEASE.md`](./RUN_RELEASE.md) | Related Run API (policy, download) |
| [`_STATUS.md`](./_STATUS.md) | Overall implementation status |
| [`../docs/CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI command to API mapping |

---

*Last Updated: 2026-01-08*
