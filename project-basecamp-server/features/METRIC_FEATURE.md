# Metric API Feature Specification

> **Version:** 0.1.0 | **Status:** Draft | **Priority:** P0 Critical
> **CLI Commands:** `dli metric list/get/register/run` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Time:** Week 1 of P0 Phase | **Cross-Reference:** [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md)

> **📦 Data Source:** ✅ Self-managed JPA (메타데이터 저장) + ✅ External API (쿼리 실행)
> **Entities:** `MetricEntity`
> **External:** `QueryEngineClient` → BigQuery/Trino 실행

---

## 1. Overview

### 1.1 Purpose

The Metric API enables CLI and programmatic access to metrics management in the DataOps platform. Metrics are SQL-based calculations that can be registered, queried, and executed on-demand.

### 1.2 Key Features

| Feature | Description | CLI Command |
|---------|-------------|-------------|
| **List Metrics** | Filter and paginate metrics by tag, owner, search | `dli metric list` |
| **Get Metric** | Retrieve metric details by fully qualified name | `dli metric get <name>` |
| **Register Metric** | Create new metric from YAML specification | `dli metric register <file>` |
| **Run Metric** | Execute metric SQL with parameters | `dli metric run <name>` |

### 1.3 Success Criteria

- All 4 endpoints functional with proper error handling
- CLI commands work seamlessly with server API
- Unit test coverage >= 80%
- Integration tests for all endpoints
- Response times < 200ms for list/get operations

---

## 2. CLI Command Mapping

### 2.1 Command-to-API Mapping

| CLI Command | HTTP Method | API Endpoint | Description |
|-------------|-------------|--------------|-------------|
| `dli metric list` | GET | `/api/v1/metrics` | List metrics with filters |
| `dli metric get <name>` | GET | `/api/v1/metrics/{name}` | Get metric details |
| `dli metric register <file>` | POST | `/api/v1/metrics` | Register new metric |
| `dli metric run <name>` | POST | `/api/v1/metrics/{name}/run` | Execute metric |

### 2.2 CLI Option Mapping

```bash
# List with filters
dli metric list --tag revenue --owner data@example.com --search daily --limit 50

# Maps to:
GET /api/v1/metrics?tag=revenue&owner=data@example.com&search=daily&limit=50

# Run with parameters
dli metric run iceberg.reporting.user_summary --param date=2026-01-01 --limit 100

# Maps to:
POST /api/v1/metrics/iceberg.reporting.user_summary/run
{
  "parameters": {"date": "2026-01-01"},
  "limit": 100
}
```

---

## 3. API Specifications

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

**Implementation:**
```kotlin
@GetMapping
fun listMetrics(
    @RequestParam tag: String?,
    @RequestParam owner: String?,
    @RequestParam search: String?,
    @RequestParam(defaultValue = "50") @Min(1) @Max(500) limit: Int,
    @RequestParam(defaultValue = "0") @Min(0) offset: Int,
): ResponseEntity<List<MetricDto>> {
    return ResponseEntity.ok(metricService.listMetrics(tag, owner, search, limit, offset))
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

> **Field Name Note:** Use `sql` (not `sql_expression`) to match CLI client expectations

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

**Implementation:**
```kotlin
@GetMapping("/{name}")
fun getMetric(
    @PathVariable @NotBlank name: String,
): ResponseEntity<MetricDto> {
    val metric = metricService.getMetric(name)
        ?: throw MetricNotFoundException(name)
    return ResponseEntity.ok(metric)
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

| Field | Rule | Error Code |
|-------|------|------------|
| `name` | Required, pattern: `[catalog].[schema].[name]` | `VALIDATION_ERROR` |
| `sql` | Required, valid SQL expression | `VALIDATION_ERROR` |
| `owner` | Required, valid email format | `VALIDATION_ERROR` |
| `tags` | Optional array, max 10 tags | `VALIDATION_ERROR` |

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

**Implementation:**
```kotlin
@PostMapping
fun createMetric(
    @RequestBody @Valid request: CreateMetricRequest,
): ResponseEntity<CreateMetricResponse> {
    val metric = metricService.createMetric(request)
    val response = CreateMetricResponse(
        message = "Metric '${metric.name}' registered successfully",
        name = metric.name
    )
    return ResponseEntity.status(HttpStatus.CREATED).body(response)
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

**Request Parameters:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `parameters` | object | No | {} | SQL template parameters |
| `limit` | int | No | null | Max rows to return (1-10000) |
| `timeout` | int | No | 300 | Execution timeout in seconds (1-3600) |

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

**Response (404 Not Found):**
```json
{
  "error": {
    "code": "METRIC_NOT_FOUND",
    "message": "Metric 'iceberg.reporting.user_summary' not found"
  }
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

**Implementation:**
```kotlin
@PostMapping("/{name}/run")
fun runMetric(
    @PathVariable @NotBlank name: String,
    @RequestBody @Valid request: RunMetricRequest,
): ResponseEntity<MetricExecutionResult> {
    val result = metricExecutionService.executeMetric(
        metricName = name,
        parameters = request.parameters,
        limit = request.limit,
        timeout = request.timeout
    )
    return ResponseEntity.ok(result)
}
```

---

## 4. Domain Model

### 4.1 MetricEntity

```kotlin
@Entity
@Table(
    name = "metrics",
    indexes = [
        Index(name = "idx_metrics_name", columnList = "name", unique = true),
        Index(name = "idx_metrics_owner", columnList = "owner"),
        Index(name = "idx_metrics_updated_at", columnList = "updated_at")
    ]
)
class MetricEntity(
    @Id
    val id: String,

    @Column(name = "name", nullable = false, unique = true, length = 255)
    val name: String,

    @Column(name = "owner", nullable = false, length = 100)
    val owner: String,

    @Column(name = "team", length = 100)
    val team: String?,

    @Column(name = "description", length = 1000)
    val description: String?,

    @Column(name = "sql_expression", nullable = false, columnDefinition = "TEXT")
    val sql: String,

    @Column(name = "source_table", length = 255)
    val sourceTable: String?,

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "metric_tags",
        joinColumns = [JoinColumn(name = "metric_id")]
    )
    @Column(name = "tag", length = 50)
    val tags: Set<String> = emptySet(),

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
        name = "metric_dependencies",
        joinColumns = [JoinColumn(name = "metric_id")]
    )
    @Column(name = "dependency", length = 255)
    val dependencies: Set<String> = emptySet(),

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime,
) {
    // JPA requires no-arg constructor
    constructor() : this(
        id = "",
        name = "",
        owner = "",
        team = null,
        description = null,
        sql = "",
        sourceTable = null,
        tags = emptySet(),
        dependencies = emptySet(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )
}
```

### 4.2 DTOs

#### Request DTOs

```kotlin
data class CreateMetricRequest(
    @field:NotBlank(message = "Name is required")
    @field:Pattern(
        regexp = "^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+$",
        message = "Name must follow pattern: catalog.schema.name"
    )
    val name: String,

    @field:NotBlank(message = "Owner is required")
    @field:Email(message = "Owner must be a valid email")
    val owner: String,

    val team: String? = null,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String? = null,

    @field:NotBlank(message = "SQL is required")
    @field:Size(max = 10000, message = "SQL must not exceed 10000 characters")
    val sql: String,

    val sourceTable: String? = null,

    @field:Size(max = 10, message = "Maximum 10 tags allowed")
    val tags: List<String> = emptyList(),
)

data class RunMetricRequest(
    val parameters: Map<String, Any> = emptyMap(),

    @field:Min(value = 1, message = "Limit must be at least 1")
    @field:Max(value = 10000, message = "Limit must not exceed 10000")
    val limit: Int? = null,

    @field:Min(value = 1, message = "Timeout must be at least 1 second")
    @field:Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    val timeout: Int = 300,
)
```

#### Response DTOs

```kotlin
data class MetricDto(
    val name: String,
    val type: String = "Metric",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val sql: String?,
    val sourceTable: String?,
    val dependencies: List<String>?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
)

data class CreateMetricResponse(
    val message: String,
    val name: String,
)

data class MetricExecutionResult(
    val rows: List<Map<String, Any>>,
    val rowCount: Int,
    val durationSeconds: Double,
    val renderedSql: String,
)
```

### 4.3 MetricMapper

```kotlin
@Component
object MetricMapper {
    fun toDto(entity: MetricEntity, includeSql: Boolean = true): MetricDto {
        return MetricDto(
            name = entity.name,
            type = "Metric",
            owner = entity.owner,
            team = entity.team,
            description = entity.description,
            tags = entity.tags.sorted(),
            sql = if (includeSql) entity.sql else null,
            sourceTable = entity.sourceTable,
            dependencies = entity.dependencies.sorted(),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toListDto(entity: MetricEntity): MetricDto {
        return toDto(entity, includeSql = false)
    }
}
```

---

## 5. Implementation Notes

### 5.1 Repository Interfaces (Domain Layer)

```kotlin
// module-core-domain/src/main/kotlin/domain/repository/

// Simple CRUD operations
interface MetricRepositoryJpa {
    fun save(metric: MetricEntity): MetricEntity
    fun findById(id: String): MetricEntity?
    fun findByName(name: String): MetricEntity?
    fun findAll(): List<MetricEntity>
    fun delete(metric: MetricEntity)
    fun existsByName(name: String): Boolean
}

// Complex query operations
interface MetricRepositoryDsl {
    fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity>

    fun findByTagsIn(tags: List<String>): List<MetricEntity>
    fun countByOwner(owner: String): Long
}
```

### 5.2 Repository Implementations (Infrastructure Layer)

```kotlin
// module-core-infra/src/main/kotlin/infra/repository/

// Simplified Pattern: Interface extends both domain interface and JpaRepository
// (Same approach used in ResourceRepositoryJpaImpl)
@Repository("metricRepositoryJpa")
interface MetricRepositoryJpaImpl :
    MetricRepositoryJpa,
    JpaRepository<MetricEntity, Long> {

    // Domain-specific queries (Spring Data JPA auto-implements)
    override fun findByName(name: String): MetricEntity?
    override fun existsByName(name: String): Boolean
    override fun findByOwner(owner: String): List<MetricEntity>
    override fun countByOwner(owner: String): Long
}

// QueryDSL implementation
@Repository("metricRepositoryDsl")
class MetricRepositoryDslImpl(
    private val entityManager: EntityManager,
) : MetricRepositoryDsl {
    private val queryFactory = JPAQueryFactory(entityManager)
    private val metric = QMetricEntity.metricEntity

    override fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity> {
        var query = queryFactory
            .selectFrom(metric)
            .orderBy(metric.updatedAt.desc())

        tag?.let { query = query.where(metric.tags.contains(it)) }
        owner?.let { query = query.where(metric.owner.containsIgnoreCase(it)) }
        search?.let { term ->
            query = query.where(
                metric.name.containsIgnoreCase(term)
                    .or(metric.description.containsIgnoreCase(term))
            )
        }

        return query.offset(offset.toLong()).limit(limit.toLong()).fetch()
    }
}
```

### 5.3 Service Layer

```kotlin
// module-core-domain/src/main/kotlin/domain/service/

@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricService::class.java)
    }

    @Transactional
    fun createMetric(request: CreateMetricRequest): MetricDto {
        logger.info("Creating metric: {}", request.name)

        // Check for duplicates
        if (metricRepositoryJpa.existsByName(request.name)) {
            throw MetricAlreadyExistsException(request.name)
        }

        val entity = MetricEntity(
            id = UUID.randomUUID().toString(),
            name = request.name,
            owner = request.owner,
            team = request.team,
            description = request.description,
            sql = request.sql,
            sourceTable = request.sourceTable,
            tags = request.tags.toSet(),
            dependencies = extractDependencies(request.sql),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        val saved = metricRepositoryJpa.save(entity)
        logger.info("Metric created: {}", saved.name)
        return MetricMapper.toDto(saved)
    }

    fun getMetric(name: String): MetricDto? {
        return metricRepositoryJpa.findByName(name)?.let { MetricMapper.toDto(it) }
    }

    fun listMetrics(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricDto> {
        return metricRepositoryDsl.findByFilters(tag, owner, search, limit, offset)
            .map { MetricMapper.toListDto(it) }
    }

    private fun extractDependencies(sql: String): Set<String> {
        val regex = Regex("(?:FROM|JOIN)\\s+([\\w.]+)", RegexOption.IGNORE_CASE)
        return regex.findAll(sql).map { it.groupValues[1] }.distinct().toSet()
    }
}
```

### 5.4 Execution Methods (Integrated into MetricService)

```kotlin
@Service
class MetricService(
    // ... other dependencies ...
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricService::class.java)
    }

    // ... CRUD methods ...

    fun executeMetric(
        metricName: String,
        parameters: Map<String, Any>,
        limit: Int?,
        timeout: Int,
    ): MetricExecutionResult {
        logger.info("Executing metric: {} with params: {}", metricName, parameters)

        val metric = metricService.getMetric(metricName)
            ?: throw MetricNotFoundException(metricName)

        val renderedSql = renderSqlWithParameters(metric.sql!!, parameters)

        try {
            val result = queryEngineClient.execute(
                sql = renderedSql,
                limit = limit,
                timeoutSeconds = timeout
            )

            return MetricExecutionResult(
                rows = result.rows,
                rowCount = result.rows.size,
                durationSeconds = result.durationSeconds,
                renderedSql = renderedSql
            )
        } catch (e: TimeoutException) {
            throw MetricExecutionTimeoutException(metricName, timeout)
        }
    }

    private fun renderSqlWithParameters(sql: String, parameters: Map<String, Any>): String {
        var result = sql
        parameters.forEach { (key, value) ->
            val placeholder = "{{$key}}"
            val replacement = when (value) {
                is String -> "'$value'"
                else -> value.toString()
            }
            result = result.replace(placeholder, replacement)
        }
        return result
    }
}
```

### 5.5 Controller

```kotlin
// module-server-api/src/main/kotlin/api/controller/

@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin
@Validated
class MetricController(
    private val metricService: MetricService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricController::class.java)
    }

    @GetMapping
    fun listMetrics(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(500) limit: Int,
        @RequestParam(defaultValue = "0") @Min(0) offset: Int,
    ): ResponseEntity<List<MetricDto>> {
        logger.info("GET /api/v1/metrics - tag: {}, owner: {}, search: {}", tag, owner, search)
        return ResponseEntity.ok(metricService.listMetrics(tag, owner, search, limit, offset))
    }

    @GetMapping("/{name}")
    fun getMetric(
        @PathVariable @NotBlank name: String,
    ): ResponseEntity<MetricDto> {
        logger.info("GET /api/v1/metrics/{}", name)
        val metric = metricService.getMetric(name)
            ?: throw MetricNotFoundException(name)
        return ResponseEntity.ok(metric)
    }

    @PostMapping
    fun createMetric(
        @RequestBody @Valid request: CreateMetricRequest,
    ): ResponseEntity<CreateMetricResponse> {
        logger.info("POST /api/v1/metrics - name: {}", request.name)
        val metric = metricService.createMetric(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(CreateMetricResponse(
                message = "Metric '${metric.name}' registered successfully",
                name = metric.name
            ))
    }

    @PostMapping("/{name}/run")
    fun runMetric(
        @PathVariable @NotBlank name: String,
        @RequestBody @Valid request: RunMetricRequest,
    ): ResponseEntity<MetricExecutionResult> {
        logger.info("POST /api/v1/metrics/{}/run", name)
        val result = metricExecutionService.executeMetric(
            metricName = name,
            parameters = request.parameters,
            limit = request.limit,
            timeout = request.timeout
        )
        return ResponseEntity.ok(result)
    }
}
```

### 5.6 Exception Handling

```kotlin
// Domain Exceptions
class MetricNotFoundException(name: String) : BasecampException(
    "Metric '$name' not found"
) {
    override val errorCode = "METRIC_NOT_FOUND"
}

class MetricAlreadyExistsException(name: String) : BasecampException(
    "Metric '$name' already exists"
) {
    override val errorCode = "METRIC_ALREADY_EXISTS"
}

class MetricExecutionTimeoutException(name: String, timeout: Int) : BasecampException(
    "Metric '$name' execution timed out after $timeout seconds"
) {
    override val errorCode = "METRIC_EXECUTION_TIMEOUT"
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
    fun `should create metric successfully`() {
        // Given
        val request = CreateMetricRequest(
            name = "test.metric.example",
            owner = "test@example.com",
            sql = "SELECT COUNT(*) FROM users"
        )
        given(metricRepositoryJpa.existsByName(request.name)).willReturn(false)
        given(metricRepositoryJpa.save(any())).willAnswer { it.arguments[0] }

        // When
        val result = metricService.createMetric(request)

        // Then
        assertThat(result.name).isEqualTo(request.name)
        assertThat(result.owner).isEqualTo(request.owner)
        verify(metricRepositoryJpa).save(any())
    }

    @Test
    fun `should throw exception when metric already exists`() {
        // Given
        val request = CreateMetricRequest(
            name = "existing.metric.name",
            owner = "test@example.com",
            sql = "SELECT 1"
        )
        given(metricRepositoryJpa.existsByName(request.name)).willReturn(true)

        // When & Then
        assertThrows<MetricAlreadyExistsException> {
            metricService.createMetric(request)
        }
        verify(metricRepositoryJpa, never()).save(any())
    }

    @Test
    fun `should list metrics with filters`() {
        // Given
        val entities = listOf(createTestMetricEntity())
        given(metricRepositoryDsl.findByFilters(
            tag = "test",
            owner = null,
            search = null,
            limit = 50,
            offset = 0
        )).willReturn(entities)

        // When
        val result = metricService.listMetrics("test", null, null, 50, 0)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].tags).contains("test")
    }
}
```

### 6.2 Integration Tests

```kotlin
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestDatabase(replace = NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class MetricControllerIntegrationTest {
    @Autowired private lateinit var testRestTemplate: TestRestTemplate
    @Autowired private lateinit var metricRepository: MetricRepositoryJpaImpl

    @BeforeEach
    fun setUp() {
        metricRepository.deleteAll()
    }

    @Test
    fun `should list metrics with filters`() {
        // Given
        val metric = createTestMetric(tags = setOf("revenue"))
        metricRepository.save(metric)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics?tag=revenue",
            Array<MetricDto>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
    }

    @Test
    fun `should get metric by name`() {
        // Given
        val metric = createTestMetric(name = "test.get.metric")
        metricRepository.save(metric)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics/test.get.metric",
            MetricDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.name).isEqualTo("test.get.metric")
        assertThat(response.body?.sql).isNotNull()
    }

    @Test
    fun `should return 404 for non-existent metric`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/metrics/non.existent.metric",
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.error?.code).isEqualTo("METRIC_NOT_FOUND")
    }

    @Test
    fun `should create metric via REST API`() {
        // Given
        val request = CreateMetricRequest(
            name = "test.create.metric",
            owner = "test@example.com",
            sql = "SELECT COUNT(*) FROM users"
        )

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/metrics",
            request,
            CreateMetricResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.name).isEqualTo(request.name)

        val saved = metricRepository.findByName(request.name)
        assertThat(saved).isNotNull
    }

    @Test
    fun `should return 409 when creating duplicate metric`() {
        // Given
        val existing = createTestMetric(name = "duplicate.metric")
        metricRepository.save(existing)

        val request = CreateMetricRequest(
            name = "duplicate.metric",
            owner = "new@example.com",
            sql = "SELECT 1"
        )

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/metrics",
            request,
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
        assertThat(response.body?.error?.code).isEqualTo("METRIC_ALREADY_EXISTS")
    }
}
```

### 6.3 CLI Integration Tests

```bash
# Test metric list
dli metric list --server-url http://localhost:8081
dli metric list --tag revenue --server-url http://localhost:8081
dli metric list --owner data@example.com --limit 10 --server-url http://localhost:8081

# Test metric get
dli metric get iceberg.reporting.user_summary --server-url http://localhost:8081

# Test metric register
dli metric register test_metric.yaml --server-url http://localhost:8081

# Test metric run
dli metric run iceberg.reporting.user_summary --param date=2026-01-01 --server-url http://localhost:8081
```

---

## 7. Related Documents

| Document | Description |
|----------|-------------|
| [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md) | Spring Boot + Kotlin implementation patterns |
| [`archive/P0_CRITICAL_APIS.md`](./archive/P0_CRITICAL_APIS.md) | Full P0 API specifications |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error code definitions |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI to API mapping reference |
| [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) | Implementation timeline |

### CLI Reference

| Document | Description |
|----------|-------------|
| [`project-interface-cli/docs/PATTERNS.md`](../../project-interface-cli/docs/PATTERNS.md) | CLI implementation patterns |
| [`project-interface-cli/features/LIBRARY_RELEASE.md`](../../project-interface-cli/features/LIBRARY_RELEASE.md) | Library API implementation |

---

## Appendix: Database Schema

```sql
CREATE TABLE metrics (
    id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    owner VARCHAR(100) NOT NULL,
    team VARCHAR(100),
    description VARCHAR(1000),
    sql_expression TEXT NOT NULL,
    source_table VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_metrics_name (name),
    INDEX idx_metrics_owner (owner),
    INDEX idx_metrics_updated_at (updated_at)
);

CREATE TABLE metric_tags (
    metric_id VARCHAR(36) NOT NULL,
    tag VARCHAR(50) NOT NULL,

    PRIMARY KEY (metric_id, tag),
    FOREIGN KEY (metric_id) REFERENCES metrics(id) ON DELETE CASCADE
);

CREATE TABLE metric_dependencies (
    metric_id VARCHAR(36) NOT NULL,
    dependency VARCHAR(255) NOT NULL,

    PRIMARY KEY (metric_id, dependency),
    FOREIGN KEY (metric_id) REFERENCES metrics(id) ON DELETE CASCADE
);
```

---

*This document provides implementation-ready specifications for the Metric API, enabling core CLI functionality for metric management.*

---

## Appendix C: Review Feedback

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Strengths
- Comprehensive API specifications following INTEGRATION_PATTERNS.md patterns exactly
- Entity definition follows all naming conventions (`MetricEntity`)
- Repository pattern correctly split into `MetricRepositoryJpa` and `MetricRepositoryDsl`
- Service implementation uses concrete class with `@Transactional(readOnly = true)` default
- Error handling follows `BasecampException` pattern with `errorCode` property

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | Exception classes missing `httpStatus` property per ERROR_CODES.md | Add `override val httpStatus = HttpStatus.NOT_FOUND` to exceptions |
| **Medium** | `MetricMapper` marked as `@Component object` - mixing both is incorrect | Remove `@Component` from object OR convert to class |
| **Low** | Entity uses `sql_expression` column but DTO uses `sql` field - potential confusion | Add explicit mapping note in documentation |

### Required Changes Before Implementation
1. Add `httpStatus` property to all exception classes
2. Fix `MetricMapper` annotation pattern
3. Add validation for SQL length before execution
