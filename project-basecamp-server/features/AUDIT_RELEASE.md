# Audit Logging - Release Document

> **Version:** 1.0.0
> **Release Date:** 2026-01-10
> **Status:** Phase 1 Complete (3/3 endpoints + AOP automatic logging)

---

## Executive Summary

This release introduces the Audit Logging feature Phase 1, providing automatic API call logging with AOP-based interception for the Basecamp data platform. All API calls are automatically logged (except those marked with `@NoAudit`), enabling user behavior analysis, audit trails, and system monitoring.

### Key Metrics

| Metric | Value |
|--------|-------|
| **Management Endpoints** | 3 (List logs + Get detail + Stats) |
| **Total Tests** | 78 (15 Service + 8 Controller + 55 Aspect) |
| **Test Success Rate** | 100% |
| **Architecture** | Pure Hexagonal (Port-Adapter) + AOP |
| **Entity Columns** | 19 columns including request_body |

---

## Phase 1 Scope

### Implemented Features

| Feature | Description | Status |
|---------|-------------|--------|
| **AuditLogEntity** | 19-column entity for comprehensive logging | Complete |
| **TraceIdFilter** | X-Trace-Id header handling for request tracing | Complete |
| **AuditAspect** | AOP-based automatic logging for @RestController | Complete |
| **@NoAudit Annotation** | Exclude specific endpoints from logging | Complete |
| **@AuditExcludeKeys** | Filter sensitive keys from request body | Complete |
| **Request Body Filtering** | Global + endpoint-specific key exclusion | Complete |
| **Management API** | List, search, and stats endpoints | Complete |

### Deferred to Phase 2+

| Feature | Target Phase |
|---------|--------------|
| Asynchronous Logging (@Async) | Phase 2 |
| Data Retention Policy | Phase 2 |
| Dashboard Statistics API | Phase 2 |
| PII Masking Enhancement | Phase 2 |

---

## API Endpoints

### Management API (3 endpoints)

| Method | Endpoint | Description | Auth |
|--------|----------|-------------|------|
| GET | `/api/v1/management/audit/logs` | List/search audit logs | Admin |
| GET | `/api/v1/management/audit/logs/{id}` | Get audit log detail | Admin |
| GET | `/api/v1/management/audit/stats` | Get audit statistics | Admin |

---

## Entity Model

### AuditLogEntity (19 columns)

```kotlin
@Entity
@Table(name = "audit_log")
class AuditLogEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    // User Information
    val userId: String,                    // User ID (String for flexibility)
    val userEmail: String?,                // User email address

    // Request Tracing
    val traceId: String?,                  // UUID for distributed tracing

    // Action & Resource
    @Enumerated(EnumType.STRING)
    val action: AuditAction,               // 33 action types
    @Enumerated(EnumType.STRING)
    val resource: AuditResource,           // 25 resource types

    // HTTP Request
    val httpMethod: String,                // GET, POST, PUT, DELETE
    val requestUrl: String,                // Full request URL
    @JdbcTypeCode(SqlTypes.JSON)
    val pathVariables: String?,            // JSON: {"name": "value"}
    @JdbcTypeCode(SqlTypes.JSON)
    val queryParameters: String?,          // JSON: {"page": "1"}
    @JdbcTypeCode(SqlTypes.JSON)
    val requestBody: String?,              // JSON: Filtered request body

    // Response
    val responseStatus: Int,               // HTTP status code
    val responseMessage: String?,          // Error message if any

    // Performance
    val durationMs: Long,                  // Request duration in ms

    // Client Information
    val clientType: String?,               // CLI, WEB, API
    val clientIp: String?,                 // Client IP address
    val userAgent: String?,                // User-Agent header
    @JdbcTypeCode(SqlTypes.JSON)
    val clientMetadata: String?,           // Parsed User-Agent JSON

    // Context
    val resourceId: String?,               // e.g., metric name, dataset id
    val teamId: Long?,                     // FK to Team

    // Timestamp
    val createdAt: LocalDateTime,          // Record creation time
)
```

### Enums

```kotlin
// AuditAction - 33 action types
enum class AuditAction {
    // Session
    LOGIN, LOGOUT,
    // CRUD
    LIST, READ, CREATE, UPDATE, DELETE,
    // Execution
    EXECUTE, CANCEL, STOP, DOWNLOAD,
    // Workflow
    REGISTER, UNREGISTER, TRIGGER, BACKFILL, PAUSE, UNPAUSE,
    // Resource
    LOCK, RELEASE,
    // Search
    SEARCH,
    // Lineage/Transpile
    LINEAGE, TRANSPILE,
    // Sync/Compare
    SYNC, COMPARE,
    // Flag Management
    OVERRIDE_SET, OVERRIDE_REMOVE, PERMISSION_SET, PERMISSION_REMOVE, EVALUATE,
    // Health
    HEALTH_CHECK,
}

// AuditResource - 25 resource types
enum class AuditResource {
    // Core
    METRIC, DATASET, WORKFLOW, QUALITY, QUERY,
    // Catalog
    CATALOG, TABLE,
    // Team & SQL
    TEAM, SQL_FOLDER, SQL_SNIPPET,
    // Flag
    FLAG, FLAG_OVERRIDE, FLAG_PERMISSION,
    // GitHub
    GITHUB_REPOSITORY, GITHUB_BRANCH, GITHUB_PULL_REQUEST,
    // Other
    LINEAGE, TRANSPILE_RULE, RUN, EXECUTION, RESOURCE_LOCK, AIRFLOW_SYNC, SESSION, SYSTEM,
}
```

---

## Implementation Details

### Architecture Compliance

| Component | Pattern | Location |
|-----------|---------|----------|
| **Entity** | Standalone (no BaseEntity) | `module-core-domain/entity/audit/` |
| **Service** | Concrete class | `module-core-domain/service/` |
| **Repository Interfaces** | Jpa/Dsl separation | `module-core-domain/repository/audit/` |
| **Repository Implementations** | QueryDSL + Spring Data | `module-core-infra/repository/audit/` |
| **Controller** | AuditController | `module-server-api/controller/` |
| **Aspect** | AuditAspect (AOP) | `module-server-api/aspect/` |
| **Filter** | TraceIdFilter | `module-server-api/filter/` |
| **Annotations** | @NoAudit, @AuditExcludeKeys | `module-server-api/annotation/` |

### Key Design Decisions

1. **AOP-Based Automatic Logging**: All `@RestController` methods are automatically logged
2. **Opt-Out Pattern**: Use `@NoAudit` to exclude specific endpoints
3. **Request Body Filtering**: Sensitive keys are filtered before logging
4. **Trace ID Support**: X-Trace-Id header for distributed tracing via MDC
5. **Synchronous Logging (Phase 1)**: Ensures data consistency, async deferred to Phase 2
6. **Immutable Entity**: No soft delete, audit logs are append-only

### Request Body Filtering

**Global Exclude Keys:**
```kotlin
val GLOBAL_EXCLUDE_KEYS = setOf(
    "password", "token", "secret", "api_key", "apiKey",
    "secretKey", "accessToken", "refreshToken",
    "credential", "credentials", "privateKey", "private_key"
)
```

**Endpoint-Specific Exclusion:**
```kotlin
// ExecutionController methods
@AuditExcludeKeys(["rendered_sql", "sql"])
@PostMapping("/datasets/run")
fun executeDataset(@RequestBody request: DatasetExecutionRequest): ResponseEntity<...>
```

### @NoAudit Applied To

| Controller | Target | Reason |
|------------|--------|--------|
| `HealthController` | Class level | System health checks not audited |
| `SessionController` | `whoami()` method | Frequent calls, no sensitive data |

### Files Created

#### New Files (module-core-common)
- `enums/AuditEnums.kt` - AuditAction (33 values), AuditResource (25 values)

#### New Files (module-core-domain)
- `entity/audit/AuditLogEntity.kt` - 19-column audit entity
- `service/AuditService.kt` - saveLog, findById, searchLogs, getStats
- `repository/audit/AuditLogRepositoryJpa.kt` - CRUD interface
- `repository/audit/AuditLogRepositoryDsl.kt` - Query interface

#### New Files (module-core-infra)
- `repository/audit/AuditLogRepositoryJpaImpl.kt` - CRUD implementation
- `repository/audit/AuditLogRepositoryJpaSpringData.kt` - Spring Data interface
- `repository/audit/AuditLogRepositoryDslImpl.kt` - QueryDSL implementation

#### New Files (module-server-api)
- `annotation/NoAudit.kt` - Exclude from audit logging
- `annotation/AuditExcludeKeys.kt` - Filter request body keys
- `aspect/AuditAspect.kt` - AOP automatic logging
- `filter/TraceIdFilter.kt` - Trace ID generation and MDC storage
- `controller/AuditController.kt` - Management API (3 endpoints)
- `dto/audit/AuditDtos.kt` - Request/Response DTOs

#### Modified Files
- `controller/HealthController.kt` - Added `@NoAudit` class annotation
- `controller/SessionController.kt` - Added `@NoAudit` to `whoami()`
- `controller/ExecutionController.kt` - Added `@AuditExcludeKeys` annotations

---

## Test Coverage

### Service Tests (15 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| AuditServiceTest | 15 | Full CRUD + search + stats |

**Test Categories:**
- saveLog: success, with all fields, with nulls
- findById: found, not found
- searchLogs: by userId, by action, by resource, by date range, pagination
- getStats: basic stats, with date filters

### Controller Tests (8 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| AuditControllerTest | 8 | All 3 endpoints |

**Test Categories:**
- listLogs: success, with filters, pagination
- getLog: found, not found (404)
- getStats: success, with date range

### Aspect Tests (55 tests)

| Test Class | Test Count | Coverage |
|------------|------------|----------|
| AuditAspectTest | 55 | Comprehensive AOP testing |

**Test Categories:**
- Action inference (15 tests): LIST, READ, CREATE, UPDATE, DELETE, EXECUTE, etc.
- Resource inference (12 tests): METRIC, DATASET, WORKFLOW, QUALITY, etc.
- Request body filtering (10 tests): Global keys, endpoint-specific keys
- Client extraction (8 tests): clientType (CLI/WEB/API), clientIp, userAgent
- Path/Query extraction (5 tests): PathVariable, QueryParameter extraction
- Exception handling (5 tests): Status code mapping for various exceptions

---

## Exception Handling

| Exception | HTTP Status | Trigger |
|-----------|-------------|---------|
| ResourceNotFoundException | 404 | Audit log not found |

> Note: Audit logging itself does not throw business exceptions. Failed audit saves are logged and suppressed to not affect main request flow.

---

## Verification

```bash
# Run Audit tests
./gradlew :module-core-domain:test --tests "com.dataops.basecamp.domain.service.AuditServiceTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.controller.AuditControllerTest"
./gradlew :module-server-api:test --tests "com.dataops.basecamp.aspect.AuditAspectTest"

# Expected results
# AuditServiceTest: 15 tests passed
# AuditControllerTest: 8 tests passed
# AuditAspectTest: 55 tests passed
# Total: 78 tests passed
```

---

## Related Documentation

- **Feature Specification:** [`AUDIT_FEATURE.md`](./AUDIT_FEATURE.md) - Full feature specification
- **Entity Relationships:** [`../docs/ENTITY_RELATION.md`](../docs/ENTITY_RELATION.md) - Audit domain relationships
- **Team Management:** [`TEAM_RELEASE.md`](./TEAM_RELEASE.md) - Team context for audit logs

---

## Roadmap

### Phase 2: Performance & Retention (Planned)

- Asynchronous logging with `@Async` + `TransactionalEventListener`
- Data retention policy (archive/delete old logs)
- Enhanced dashboard statistics API
- Logback integration with `%X{traceId}` pattern

### Phase 3: Advanced Features (Future)

- Real-time audit streaming
- Anomaly detection alerts
- User session aggregation
- PII detection and masking

---

*Document Version: 1.0.0 | Last Updated: 2026-01-10*
