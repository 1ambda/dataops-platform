# Execution Feature Specification

> **Version:** 1.0.0-draft-r1
> **Last Updated:** 2026-01-04
> **Status:** Draft - Review Completed
> **Review Status:** feature-basecamp-server Agent Review Completed (4 Critical, 6 Warning, 5 Suggestion)

---

## 1. Overview

### 1.1 Purpose

Execution Feature는 Basecamp Server를 통해 Dataset/Quality/Raw SQL 쿼리를 실행하고 결과를 관리하는 통합 실행 시스템입니다.

### 1.2 Goals

1. **통합 실행 인프라**: Dataset Spec, Quality Spec, Raw SQL 실행을 위한 공통 인프라 제공
2. **Query Engine 추상화**: BigQuery, Trino 등 다양한 Query Engine 지원
3. **실행 이력 관리**: 감사 추적 및 실행 결과 저장
4. **확장 가능한 아키텍처**: 향후 Kafka/Redis 기반 비동기 실행 지원 대비

### 1.3 Non-Goals (현재 버전)

- 권한/보안 관리 (추후 구현)
- Query Engine 실제 연동 (MockClient 사용)
- Remote Execution (Kafka/Redis) 구현

---

## 2. Architecture

### 2.1 System Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              CLI (dli)                                  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                     │
│  │ dli dataset │  │ dli quality │  │  dli run    │                     │
│  │     run     │  │     run     │  │             │                     │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘                     │
│         │                │                │                             │
│         └────────────────┼────────────────┘                             │
│                          ▼                                              │
│              ExecutionMode.SERVER                                       │
└─────────────────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                        Basecamp Server                                  │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Controller Layer                            │   │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐              │   │
│  │  │  Dataset    │  │  Quality    │  │    Run      │              │   │
│  │  │ Controller  │  │ Controller  │  │ Controller  │              │   │
│  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘              │   │
│  └─────────┼────────────────┼────────────────┼──────────────────────┘   │
│            │                │                │                          │
│            └────────────────┼────────────────┘                          │
│                             ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                      Service Layer                               │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │                  ExecutionService                        │    │   │
│  │  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │    │   │
│  │  │  │ executeData │  │executeQual- │  │executeRaw-  │      │    │   │
│  │  │  │    set()    │  │   ity()     │  │   Sql()     │      │    │   │
│  │  │  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘      │    │   │
│  │  └─────────┼────────────────┼────────────────┼──────────────┘    │   │
│  │            │                │                │                   │   │
│  │            └────────────────┼────────────────┘                   │   │
│  │                             ▼                                    │   │
│  │  ┌─────────────────────────────────────────────────────────┐    │   │
│  │  │               TranspileService                           │    │   │
│  │  │  (Spec → Rendered SQL via Basecamp Parser)              │    │   │
│  │  └─────────────────────────┬───────────────────────────────┘    │   │
│  └─────────────────────────────┼────────────────────────────────────┘   │
│                                ▼                                        │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Infrastructure Layer                          │   │
│  │  ┌─────────────────┐              ┌─────────────────────────┐   │   │
│  │  │ ParserClient    │              │  QueryExecutionPort     │   │   │
│  │  │ (HTTP → Parser) │              │  (Domain Interface)     │   │   │
│  │  └────────┬────────┘              └───────────┬─────────────┘   │   │
│  │           │                                   │                  │   │
│  │           ▼                                   ▼                  │   │
│  │  ┌─────────────────┐              ┌─────────────────────────┐   │   │
│  │  │ Basecamp Parser │              │      Adapters           │   │   │
│  │  │ (Flask Service) │              │ ├─ BigQueryAdapter      │   │   │
│  │  └─────────────────┘              │ ├─ TrinoAdapter         │   │   │
│  │                                   │ └─ MockQueryAdapter     │   │   │
│  │                                   └─────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Execution Modes

| Mode | Description | Flow |
|------|-------------|------|
| **LOCAL** | CLI가 직접 Query Engine 호출 | CLI → BigQuery/Trino |
| **SERVER** | Basecamp Server가 Query Engine 호출 | CLI → Server → Query Engine |
| **REMOTE** | (Future) Kafka/Redis 기반 비동기 실행 | CLI → Server → Kafka → Worker → Query Engine |

### 2.3 Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Execution Strategy | Sync + Async Ready | 현재는 동기식, Interface는 비동기 확장 가능하게 |
| Transpile Orchestration | Server-Orchestrated | CLI는 Server만 호출, Parser 직접 호출 안함 |
| Query Engine Pattern | Hexagonal Port-Adapter | domain에 interface, infra에 구현체 |
| API Endpoints | Separate (Domain-aligned) | 기존 도메인별 엔드포인트 유지 |
| Result Format | Separate Results + Shared Error | 도메인별 결과 포맷 유지, 에러만 통일 |
| History Persistence | Full (Separate Entities) | History/Result 분리하여 용량 관리 |
| Parameter Handling | Typed DTOs | 타입별 별도 파라미터 클래스 |

---

## 3. API Endpoints

### 3.1 Dataset Execution

```http
POST /api/v1/datasets/{name}/run
Content-Type: application/json

{
  "parameters": {
    "date": "2026-01-01",
    "limit": 1000
  },
  "dryRun": false,
  "dialect": "bigquery",
  "reason": "Daily batch execution"
}
```

**Response (Success):**
```json
{
  "executionId": "exec-uuid-1234",
  "status": "SUCCESS",
  "data": {
    "rows": [...],
    "rowCount": 150,
    "schema": [
      {"name": "id", "type": "INTEGER"},
      {"name": "name", "type": "STRING"}
    ]
  },
  "executionTime": 1523,
  "transpiledSql": "SELECT id, name FROM dataset WHERE date = '2026-01-01' LIMIT 1000"
}
```

### 3.2 Quality Execution

```http
POST /api/v1/quality/test/{resourceName}
Content-Type: application/json

{
  "parameters": {
    "threshold": 0.95
  },
  "testFilter": ["not_null_check", "unique_check"],
  "failFast": false,
  "reason": "Pre-release validation"
}
```

**Response (Success):**
```json
{
  "executionId": "exec-uuid-5678",
  "status": "SUCCESS",
  "data": {
    "passed": false,
    "totalTests": 5,
    "passedTests": 3,
    "failedTests": 2,
    "failures": [
      {
        "testName": "not_null_check",
        "column": "email",
        "message": "Found 15 null values",
        "rowsAffected": 15
      }
    ]
  },
  "executionTime": 2341
}
```

### 3.3 Raw SQL Execution

```http
POST /api/v1/run/execute
Content-Type: application/json

{
  "sql": "SELECT * FROM catalog.schema.table WHERE created_at > @date",
  "parameters": {
    "date": "2026-01-01"
  },
  "dialect": "trino",
  "dryRun": false,
  "reason": "Ad-hoc analysis"
}
```

**Response (Success):**
```json
{
  "executionId": "exec-uuid-9012",
  "status": "SUCCESS",
  "data": {
    "rows": [...],
    "rowCount": 500,
    "schema": [...]
  },
  "executionTime": 890,
  "transpiledSql": "SELECT * FROM catalog.schema.table WHERE created_at > '2026-01-01'"
}
```

### 3.4 Error Response (Unified)

```json
{
  "executionId": "exec-uuid-failed",
  "status": "FAILED",
  "error": {
    "code": "EXEC-003",
    "message": "Query execution failed",
    "details": {
      "sqlState": "42000",
      "line": 5,
      "column": 12
    },
    "cause": "Column 'invalid_col' not found in table"
  }
}
```

---

## 4. Domain Model

### 4.1 Enums

```kotlin
// module-core-domain/domain/execution/ExecutionEnums.kt

enum class ExecutionType {
    DATASET,
    QUALITY,
    RAW_SQL
}

enum class ExecutionStatus {
    PENDING,      // 대기 중 (Remote mode)
    RUNNING,      // 실행 중
    SUCCESS,      // 성공
    FAILED,       // 실패
    TIMEOUT,      // 시간 초과
    CANCELLED     // 취소됨
}

enum class SqlDialect {
    BIGQUERY,
    TRINO,
    SPARK,
    DUCKDB
}
```

### 4.2 Entities

```kotlin
// Entity 1: Execution History (Metadata - Always Retained)
// module-core-domain/domain/execution/ExecutionHistoryEntity.kt

@Entity
@Table(name = "execution_history")
class ExecutionHistoryEntity(
    @Column(nullable = false, unique = true)
    val executionId: String,                    // UUID

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val executionType: ExecutionType,           // DATASET, QUALITY, RAW_SQL

    @Column(nullable = true)
    val resourceName: String?,                  // spec name (null for RAW_SQL)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: ExecutionStatus,

    @Column(nullable = false)
    val startedAt: Instant,

    @Column(nullable = true)
    val completedAt: Instant?,

    @Column(nullable = true)
    val durationMs: Long?,

    @Column(nullable = false)
    val userId: String,                         // who triggered

    @Column(nullable = false, columnDefinition = "TEXT")
    val transpiledSql: String,                  // Audit: actual executed SQL

    @Column(nullable = true, columnDefinition = "JSONB")
    val parameters: String?,                    // Input parameters as JSON

    @Column(nullable = true)
    val reason: String?,                        // Execution reason

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    val dialect: SqlDialect?,

    @Column(nullable = true)
    val errorCode: String?,

    @Column(nullable = true, columnDefinition = "TEXT")
    val errorMessage: String?
) : BaseEntity()


// Entity 2: Execution Result (Data - Size Managed, Can Be Purged)
// module-core-domain/domain/execution/ExecutionResultEntity.kt

@Entity
@Table(name = "execution_result")
class ExecutionResultEntity(
    @Column(nullable = false)
    val executionId: String,                    // FK to ExecutionHistory.executionId

    @Column(nullable = false, columnDefinition = "JSONB")
    val resultData: String,                     // Query result rows as JSON

    @Column(nullable = false)
    val rowCount: Int,

    @Column(nullable = true, columnDefinition = "JSONB")
    val schema: String?                         // Column info as JSON
) : BaseEntity()
```

### 4.3 Parameter DTOs

```kotlin
// module-core-domain/domain/execution/ExecutionParams.kt

data class DatasetRunParams(
    val parameters: Map<String, Any> = emptyMap(),
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
    val dryRun: Boolean = false,
    val limit: Int? = null,
    val reason: String? = null
)

data class QualityTestParams(
    val parameters: Map<String, Any> = emptyMap(),
    val testFilter: List<String>? = null,
    val failFast: Boolean = false,
    val reason: String? = null
)

data class SqlExecutionParams(
    val sql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val dialect: SqlDialect = SqlDialect.BIGQUERY,
    val dryRun: Boolean = false,
    val reason: String? = null
)
```

### 4.4 Result DTOs

```kotlin
// module-core-domain/domain/execution/ExecutionResults.kt

// Dataset/SQL Result
data class QueryExecutionResult(
    val executionId: String,
    val status: ExecutionStatus,
    val rows: List<Map<String, Any>>?,
    val rowCount: Int?,
    val schema: List<ColumnInfo>?,
    val executionTime: Long,
    val transpiledSql: String?,
    val error: ExecutionError?
)

// Quality Result
data class QualityExecutionResult(
    val executionId: String,
    val status: ExecutionStatus,
    val passed: Boolean?,
    val totalTests: Int?,
    val passedTests: Int?,
    val failedTests: Int?,
    val failures: List<TestFailure>?,
    val executionTime: Long,
    val error: ExecutionError?
)

// Shared Error
data class ExecutionError(
    val code: String,                // EXEC-001 ~ EXEC-006
    val message: String,
    val details: Map<String, Any>?,
    val cause: String?
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean = true
)

data class TestFailure(
    val testName: String,
    val column: String?,
    val message: String,
    val rowsAffected: Int?
)
```

---

## 5. Port-Adapter Design

### 5.1 Domain Port (Interface)

```kotlin
// module-core-domain/domain/execution/port/QueryExecutionPort.kt

interface QueryExecutionPort {
    /**
     * Execute SQL query synchronously
     */
    fun execute(request: QueryExecutionRequest): QueryExecutionResponse

    /**
     * Test connection to query engine
     */
    fun testConnection(): ConnectionStatus

    /**
     * Get supported dialect
     */
    fun supportedDialect(): SqlDialect
}

data class QueryExecutionRequest(
    val sql: String,
    val dialect: SqlDialect,
    val parameters: Map<String, Any> = emptyMap(),
    val timeout: Duration = Duration.ofMinutes(5)
)

data class QueryExecutionResponse(
    val success: Boolean,
    val rows: List<Map<String, Any>>?,
    val rowCount: Int?,
    val schema: List<ColumnInfo>?,
    val executionTimeMs: Long,
    val error: String?
)

data class ConnectionStatus(
    val connected: Boolean,
    val message: String?,
    val latencyMs: Long?
)
```

### 5.2 Infrastructure Adapters

```kotlin
// module-core-infra/adapter/execution/BigQueryAdapter.kt

@Component("bigQueryExecutionAdapter")
@Profile("bigquery")
class BigQueryAdapter(
    private val bigQueryClient: BigQueryClient  // Google Cloud SDK
) : QueryExecutionPort {

    override fun execute(request: QueryExecutionRequest): QueryExecutionResponse {
        // BigQuery-specific implementation
    }

    override fun testConnection(): ConnectionStatus {
        // Test BigQuery connectivity
    }

    override fun supportedDialect() = SqlDialect.BIGQUERY
}


// module-core-infra/adapter/execution/TrinoAdapter.kt

@Component("trinoExecutionAdapter")
@Profile("trino")
class TrinoAdapter(
    private val trinoClient: TrinoClient
) : QueryExecutionPort {
    // Trino-specific implementation
}


// module-core-infra/adapter/execution/MockQueryAdapter.kt

@Component("mockQueryExecutionAdapter")
@Profile("mock", "test", "local")
class MockQueryAdapter : QueryExecutionPort {

    override fun execute(request: QueryExecutionRequest): QueryExecutionResponse {
        // Return mock data for testing
        return QueryExecutionResponse(
            success = true,
            rows = listOf(
                mapOf("id" to 1, "name" to "Test"),
                mapOf("id" to 2, "name" to "Mock")
            ),
            rowCount = 2,
            schema = listOf(
                ColumnInfo("id", "INTEGER"),
                ColumnInfo("name", "STRING")
            ),
            executionTimeMs = 100,
            error = null
        )
    }

    override fun testConnection() = ConnectionStatus(true, "Mock connection OK", 1)

    override fun supportedDialect() = SqlDialect.BIGQUERY
}
```

### 5.3 Parser Client (Transpile)

```kotlin
// module-core-domain/domain/external/ParserClient.kt (Interface)

interface ParserClient {
    fun transpile(request: TranspileRequest): TranspileResponse
}

data class TranspileRequest(
    val sql: String,
    val parameters: Map<String, Any>,
    val sourceDialect: SqlDialect,
    val targetDialect: SqlDialect
)

data class TranspileResponse(
    val success: Boolean,
    val renderedSql: String?,
    val error: String?
)


// module-core-infra/client/MockParserClient.kt

@Component
@Profile("mock", "test", "local")
class MockParserClient : ParserClient {

    override fun transpile(request: TranspileRequest): TranspileResponse {
        // Simple parameter substitution for testing
        var renderedSql = request.sql
        request.parameters.forEach { (key, value) ->
            renderedSql = renderedSql.replace("@$key", "'$value'")
            renderedSql = renderedSql.replace("\${$key}", "'$value'")
        }
        return TranspileResponse(true, renderedSql, null)
    }
}


// module-core-infra/client/RestParserClient.kt

@Component
@Profile("!mock & !test & !local")
class RestParserClient(
    private val restTemplate: RestTemplate,
    @Value("\${basecamp.parser.url}") private val parserUrl: String
) : ParserClient {

    override fun transpile(request: TranspileRequest): TranspileResponse {
        val response = restTemplate.postForEntity(
            "$parserUrl/api/v1/transpile",
            request,
            TranspileResponse::class.java
        )
        return response.body ?: TranspileResponse(false, null, "Empty response")
    }
}
```

---

## 6. Service Layer

### 6.1 ExecutionService

```kotlin
// module-core-domain/domain/service/ExecutionService.kt

@Service
@Transactional
class ExecutionService(
    private val parserClient: ParserClient,
    private val queryExecutionPort: QueryExecutionPort,
    private val executionHistoryRepository: ExecutionHistoryRepositoryJpa,
    private val executionResultRepository: ExecutionResultRepositoryJpa,
    private val datasetRepository: DatasetRepositoryJpa,
    private val qualitySpecRepository: QualitySpecRepositoryJpa
) {

    fun executeDataset(name: String, params: DatasetRunParams): QueryExecutionResult {
        val executionId = UUID.randomUUID().toString()
        val startedAt = Instant.now()

        try {
            // 1. Load dataset spec
            val dataset = datasetRepository.findByName(name)
                ?: throw ExecutionException("EXEC-006", "Dataset not found: $name")

            // 2. Transpile SQL with parameters
            val transpileResponse = parserClient.transpile(
                TranspileRequest(
                    sql = dataset.sql,
                    parameters = params.parameters,
                    sourceDialect = SqlDialect.TRINO,  // spec source
                    targetDialect = params.dialect
                )
            )

            if (!transpileResponse.success) {
                throw ExecutionException("EXEC-001", transpileResponse.error ?: "Transpile failed")
            }

            val renderedSql = transpileResponse.renderedSql!!

            // 3. Execute query
            val queryResponse = queryExecutionPort.execute(
                QueryExecutionRequest(
                    sql = renderedSql,
                    dialect = params.dialect,
                    parameters = params.parameters
                )
            )

            val completedAt = Instant.now()
            val durationMs = Duration.between(startedAt, completedAt).toMillis()

            // 4. Save history
            val history = saveHistory(
                executionId = executionId,
                type = ExecutionType.DATASET,
                resourceName = name,
                status = if (queryResponse.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = completedAt,
                durationMs = durationMs,
                transpiledSql = renderedSql,
                params = params,
                error = if (!queryResponse.success) queryResponse.error else null
            )

            // 5. Save result (if success)
            if (queryResponse.success && queryResponse.rows != null) {
                saveResult(executionId, queryResponse)
            }

            return QueryExecutionResult(
                executionId = executionId,
                status = if (queryResponse.success) ExecutionStatus.SUCCESS else ExecutionStatus.FAILED,
                rows = queryResponse.rows,
                rowCount = queryResponse.rowCount,
                schema = queryResponse.schema,
                executionTime = durationMs,
                transpiledSql = renderedSql,
                error = if (!queryResponse.success) ExecutionError(
                    code = "EXEC-003",
                    message = queryResponse.error ?: "Query failed",
                    details = null,
                    cause = null
                ) else null
            )
        } catch (e: ExecutionException) {
            saveHistory(
                executionId = executionId,
                type = ExecutionType.DATASET,
                resourceName = name,
                status = ExecutionStatus.FAILED,
                startedAt = startedAt,
                completedAt = Instant.now(),
                durationMs = Duration.between(startedAt, Instant.now()).toMillis(),
                transpiledSql = "",
                params = params,
                errorCode = e.code,
                errorMessage = e.message
            )
            throw e
        }
    }

    fun executeQuality(resourceName: String, params: QualityTestParams): QualityExecutionResult {
        // Similar pattern for quality execution
        // ...
    }

    fun executeRawSql(params: SqlExecutionParams): QueryExecutionResult {
        // Similar pattern for raw SQL execution
        // ...
    }

    private fun saveHistory(...): ExecutionHistoryEntity { ... }
    private fun saveResult(...): ExecutionResultEntity { ... }
}
```

---

## 7. Error Codes

| Code | Category | Description | HTTP Status |
|------|----------|-------------|-------------|
| EXEC-001 | Transpile Error | SQL 파라미터 치환 또는 방언 변환 실패 | 400 |
| EXEC-002 | Connection Error | Query Engine 연결 실패 | 503 |
| EXEC-003 | Query Error | SQL 실행 오류 (문법, 권한 등) | 400 |
| EXEC-004 | Timeout Error | 실행 시간 초과 | 408 |
| EXEC-005 | Permission Error | 권한 부족 (테이블 접근 등) | 403 |
| EXEC-006 | Resource Error | 리소스 없음 (Spec, 테이블 등) | 404 |

---

## 8. Database Schema

```sql
-- Execution History (Metadata - Always Retained)
CREATE TABLE execution_history (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(36) NOT NULL UNIQUE,
    execution_type VARCHAR(20) NOT NULL,
    resource_name VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    duration_ms BIGINT,
    user_id VARCHAR(100) NOT NULL,
    transpiled_sql TEXT NOT NULL,
    parameters JSON,
    reason VARCHAR(500),
    dialect VARCHAR(20),
    error_code VARCHAR(20),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100),

    INDEX idx_execution_id (execution_id),
    INDEX idx_execution_type (execution_type),
    INDEX idx_resource_name (resource_name),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at),
    INDEX idx_user_id (user_id)
);

-- Execution Result (Data - Size Managed)
CREATE TABLE execution_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(36) NOT NULL,
    result_data JSON NOT NULL,
    row_count INT NOT NULL,
    schema JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    updated_by VARCHAR(100),
    deleted_at TIMESTAMP,
    deleted_by VARCHAR(100),

    INDEX idx_execution_id (execution_id),
    FOREIGN KEY (execution_id) REFERENCES execution_history(execution_id)
);
```

---

## 9. Implementation Phases

### Phase 1: Foundation (Week 1)
- [ ] Domain enums 및 entities 생성
- [ ] Port interface 정의 (QueryExecutionPort, ParserClient)
- [ ] MockQueryAdapter, MockParserClient 구현
- [ ] Repository 인터페이스 및 구현체

### Phase 2: Service Layer (Week 2)
- [ ] ExecutionService 구현
- [ ] TranspileService 구현
- [ ] ExecutionHistoryService 구현
- [ ] 단위 테스트 작성

### Phase 3: API Layer (Week 3)
- [ ] DatasetController.run() 연동
- [ ] QualityController.test() 연동
- [ ] RunController.execute() 연동
- [ ] 통합 테스트 작성

### Phase 4: Polish (Week 4)
- [ ] 에러 처리 강화
- [ ] 로깅 및 모니터링
- [ ] API 문서화 (OpenAPI)
- [ ] 성능 테스트

---

## 10. Version History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0.0-draft | 2026-01-04 | AI Assistant | Initial draft |
| 1.0.0-draft-r1 | 2026-01-04 | AI Assistant | Review feedback incorporated (4 Critical, 6 Warning resolved) |

---

## 11. Review Notes

> **Review Completed**: feature-basecamp-server Agent (2026-01-04)

### Design Decisions Confirmed with User
1. ✅ Execution Strategy: Sync + Async Ready
2. ✅ Transpile: Server-Orchestrated (CLI → Server → Parser)
3. ✅ Query Engine: Hexagonal Port-Adapter
4. ✅ Endpoints: Separate (Domain-aligned)
5. ✅ Result Format: Separate Results + Shared Error
6. ✅ History: Full Persistence (Separate Entities)
7. ✅ Parameters: Typed DTOs

---

## 12. Review Findings and Resolutions

### Critical Issues

| ID | Issue | Resolution |
|----|-------|------------|
| CRIT-1 | `QueryExecutionPort` 중복 - 기존 `QueryEngineClient` 존재 | 기존 `QueryEngineClient` 인터페이스 확장하여 사용. 새 인터페이스 생성 안함 |
| CRIT-2 | `QueryExecutionResult` DTO 이름 충돌 | 기존 `QueryEngineResponse.kt` 내 `QueryExecutionResult` 재사용 |
| CRIT-3 | Entity 경로 오류 (`domain/execution/`) | 올바른 경로: `domain/model/execution/` |
| CRIT-4 | FK 제약조건 오류 (execution_id 참조) | 애플리케이션 레벨 일관성 유지, FK 제거 또는 `id` 참조로 변경 |

### Warnings

| ID | Issue | Resolution |
|----|-------|------------|
| WARN-1 | `ParserClient` 이름 불일치 | 기존 `BasecampParserClient` 사용 |
| WARN-2 | `JSONB` vs `JSON` 타입 불일치 | MySQL 호환을 위해 `JSON` 사용 |
| WARN-3 | Bean Validation 어노테이션 누락 | `@NotBlank`, `@Size` 등 추가 필요 |
| WARN-4 | `BaseEntity` vs `BaseAuditableEntity` 혼동 | `BaseEntity` 사용 (audit 필드 포함) |
| WARN-5 | `execution_result.execution_id` 인덱스 | UNIQUE 인덱스로 변경 권장 |
| WARN-6 | Adapter Bean 이름 충돌 가능성 | `@Profile`만 사용, 명시적 이름 제거 |

### Corrected Patterns (Implementation Guide)

#### Entity Location
```
# Correct paths
module-core-domain/domain/model/execution/ExecutionHistoryEntity.kt
module-core-domain/domain/model/execution/ExecutionResultEntity.kt
module-core-domain/domain/model/execution/ExecutionEnums.kt
```

#### Use Existing Interfaces
```kotlin
// Use existing QueryEngineClient instead of creating QueryExecutionPort
@Service
class ExecutionService(
    private val basecampParserClient: BasecampParserClient,  // 기존 인터페이스
    private val queryEngineClient: QueryEngineClient,        // 기존 인터페이스
    private val executionHistoryRepositoryJpa: ExecutionHistoryRepositoryJpa,
    private val executionResultRepositoryJpa: ExecutionResultRepositoryJpa,
    // ...
)
```

#### Entity with Validation
```kotlin
@Entity
@Table(name = "execution_history")
class ExecutionHistoryEntity(
    @field:NotBlank(message = "Execution ID is required")
    @field:Size(max = 36, message = "Execution ID must not exceed 36 characters")
    @Column(nullable = false, unique = true, length = 36)
    val executionId: String,

    @Column(nullable = true, columnDefinition = "JSON")  // JSON, not JSONB
    val parameters: String?,
    // ...
) : BaseEntity()
```

#### Database Schema Fix
```sql
-- Option 1: Remove FK, use application-level consistency
CREATE TABLE execution_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_id VARCHAR(36) NOT NULL,
    -- ... other columns ...
    UNIQUE INDEX idx_execution_result_execution_id (execution_id)
    -- No FK constraint
);

-- Option 2: Use id reference
CREATE TABLE execution_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    execution_history_id BIGINT NOT NULL,
    -- ... other columns ...
    FOREIGN KEY (execution_history_id) REFERENCES execution_history(id)
);
```

### Implementation Checklist (Updated)

- [ ] Verify `QueryEngineClient` interface and extend if needed
- [ ] Verify `BasecampParserClient` interface and extend if needed
- [ ] Create entities in `domain/model/execution/` path
- [ ] Use `JSON` type instead of `JSONB` for MySQL
- [ ] Add Bean Validation annotations to entities
- [ ] Check for existing `SqlDialect` enum before creating
- [ ] Choose FK strategy (application-level or id-based)
- [ ] Update ENTITY_RELATION.md when entities are created
