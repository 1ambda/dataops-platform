# Dataset API Feature Specification

> **Version:** 0.1.0 | **Status:** Draft | **Priority:** P0 Critical
> **CLI Commands:** `dli dataset list/get/register/run` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation Week:** Week 2 | **Estimated Effort:** 3-4 days

---

## 1. Overview

### 1.1 Purpose

The Dataset API provides CRUD operations and execution capabilities for dataset management, enabling the CLI tool (`dli dataset`) to interact with the Basecamp Server for data pipeline orchestration.

### 1.2 Scope

| Feature | CLI Command | API Endpoint | Status |
|---------|-------------|--------------|--------|
| List Datasets | `dli dataset list` | `GET /api/v1/datasets` | To Implement |
| Get Dataset | `dli dataset get` | `GET /api/v1/datasets/{name}` | To Implement |
| Register Dataset | `dli dataset register` | `POST /api/v1/datasets` | To Implement |
| Execute Dataset | `dli dataset run` | `POST /api/v1/datasets/{name}/run` | To Implement |

### 1.3 Implementation Notes

> **For feature-basecamp-server agent:**
> - Leverage existing `DatasetEntity` (already implemented in domain layer)
> - Create `DatasetService` following `PipelineService` pattern (concrete class, no interface)
> - Repository pattern: extend existing `DatasetRepositoryJpa` + add `DatasetRepositoryDsl`

---

## 2. CLI Command Mapping

### 2.1 Command to API Mapping

| CLI Command | HTTP Method | Endpoint | Query Parameters |
|-------------|-------------|----------|------------------|
| `dli dataset list` | GET | `/api/v1/datasets` | `tag`, `owner`, `search`, `limit`, `offset` |
| `dli dataset list --tag feed` | GET | `/api/v1/datasets?tag=feed` | - |
| `dli dataset get <name>` | GET | `/api/v1/datasets/{name}` | - |
| `dli dataset register <file>` | POST | `/api/v1/datasets` | Request body from YAML |
| `dli dataset run <name>` | POST | `/api/v1/datasets/{name}/run` | Request body with parameters |

### 2.2 CLI Options to Query Parameters

```bash
# List with filters
dli dataset list --tag feed --owner engineer@example.com --search daily --limit 50

# Maps to:
GET /api/v1/datasets?tag=feed&owner=engineer@example.com&search=daily&limit=50&offset=0

# Run with parameters
dli dataset run iceberg.analytics.daily_clicks --param date=2026-01-01 --limit 100

# Maps to:
POST /api/v1/datasets/iceberg.analytics.daily_clicks/run
{
  "parameters": {"date": "2026-01-01"},
  "limit": 100,
  "timeout": 600
}
```

---

## 3. API Specifications

### 3.1 List Datasets

#### `GET /api/v1/datasets`

**Purpose:** List datasets with optional filtering for `dli dataset list`

**Request:**
```http
GET /api/v1/datasets?tag=feed&owner=engineer@example.com&search=daily
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

**Response (400 Bad Request):**
```json
{
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "Invalid limit value: must be between 1 and 500",
    "details": {
      "parameter": "limit",
      "value": 1000
    }
  }
}
```

---

### 3.2 Get Dataset Details

#### `GET /api/v1/datasets/{name}`

**Purpose:** Get dataset details by fully qualified name for `dli dataset get`

**Request:**
```http
GET /api/v1/datasets/iceberg.analytics.daily_clicks
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Path Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `name` | string | Yes | Fully qualified dataset name (e.g., `catalog.schema.name`) |

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

> **Field Name Note:** Use `sql` (not `sql_expression`) to match CLI client expectations

**Response (404 Not Found):**
```json
{
  "error": {
    "code": "DATASET_NOT_FOUND",
    "message": "Dataset 'iceberg.analytics.unknown' not found",
    "details": {
      "dataset_name": "iceberg.analytics.unknown"
    }
  }
}
```

---

### 3.3 Register Dataset

#### `POST /api/v1/datasets`

**Purpose:** Register a new dataset for `dli dataset register`

**Request:**
```http
POST /api/v1/datasets
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "name": "iceberg.analytics.new_dataset",
  "type": "Dataset",
  "owner": "engineer@example.com",
  "team": "@data-eng",
  "description": "New dataset description",
  "tags": ["feed", "daily"],
  "sql": "SELECT date, COUNT(*) FROM events GROUP BY 1",
  "dependencies": ["iceberg.raw.events"],
  "schedule": {
    "cron": "0 6 * * *",
    "timezone": "UTC"
  }
}
```

**Validation Rules:**

| Field | Rule | Error Code |
|-------|------|------------|
| `name` | Required, pattern: `[catalog].[schema].[name]` | `INVALID_DATASET_NAME` |
| `sql` | Required, valid SQL expression | `INVALID_SQL` |
| `owner` | Required, valid email format | `INVALID_OWNER_EMAIL` |
| `tags` | Optional array, max 10 tags | `TOO_MANY_TAGS` |
| `schedule.cron` | Optional, valid cron expression | `INVALID_CRON` |

**Response (201 Created):**
```json
{
  "message": "Dataset 'iceberg.analytics.new_dataset' registered successfully",
  "name": "iceberg.analytics.new_dataset"
}
```

**Response (409 Conflict):**
```json
{
  "error": {
    "code": "DATASET_ALREADY_EXISTS",
    "message": "Dataset 'iceberg.analytics.new_dataset' already exists",
    "details": {
      "dataset_name": "iceberg.analytics.new_dataset"
    }
  }
}
```

**Response (422 Unprocessable Entity):**
```json
{
  "error": {
    "code": "INVALID_DATASET_NAME",
    "message": "Dataset name must follow pattern: catalog.schema.name",
    "details": {
      "dataset_name": "invalid-name",
      "expected_pattern": "[catalog].[schema].[name]"
    }
  }
}
```

---

### 3.4 Execute Dataset

#### `POST /api/v1/datasets/{name}/run`

**Purpose:** Execute a dataset and return results for `dli dataset run`

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

**Request Body:**

| Field | Type | Required | Default | Description |
|-------|------|----------|---------|-------------|
| `parameters` | object | No | `{}` | SQL template parameters |
| `limit` | int | No | 1000 | Max rows to return |
| `timeout` | int | No | 600 | Execution timeout in seconds |

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

**Response (500 Internal Server Error):**
```json
{
  "error": {
    "code": "DATASET_EXECUTION_FAILED",
    "message": "Query execution failed",
    "details": {
      "dataset_name": "iceberg.analytics.daily_clicks",
      "sql_error": "Column 'unknown_column' not found"
    }
  }
}
```

---

## 4. Domain Model

### 4.1 DatasetEntity

> **Note:** `DatasetEntity` already exists in the domain layer. Verify and extend if needed.

```kotlin
@Entity
@Table(name = "datasets")
class DatasetEntity(
    @Id
    val id: String = UUID.randomUUID().toString(),

    @Column(nullable = false, unique = true)
    val name: String,

    @Column(nullable = false)
    val owner: String,

    @Column
    val team: String? = null,

    @Column(length = 1000)
    val description: String? = null,

    @Column(name = "sql_expression", nullable = false, length = 10000)
    val sql: String,

    @ElementCollection
    @CollectionTable(name = "dataset_tags", joinColumns = [JoinColumn(name = "dataset_id")])
    @Column(name = "tag")
    val tags: Set<String> = emptySet(),

    @ElementCollection
    @CollectionTable(name = "dataset_dependencies", joinColumns = [JoinColumn(name = "dataset_id")])
    @Column(name = "dependency")
    val dependencies: Set<String> = emptySet(),

    @Column(name = "schedule_cron")
    val scheduleCron: String? = null,

    @Column(name = "schedule_timezone")
    val scheduleTimezone: String? = "UTC",

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),
)
```

### 4.2 DTOs

#### DatasetDto (Response)

```kotlin
data class DatasetDto(
    val name: String,
    val type: String = "Dataset",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val sql: String?,              // Included only for detail view
    val dependencies: List<String>?,
    val schedule: ScheduleDto?,
    val createdAt: String,         // ISO 8601 format
    val updatedAt: String,
)

data class ScheduleDto(
    val cron: String,
    val timezone: String,
)
```

#### DatasetListDto (List Response - Simplified)

```kotlin
data class DatasetListDto(
    val name: String,
    val type: String = "Dataset",
    val owner: String,
    val team: String?,
    val description: String?,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
)
```

#### CreateDatasetRequest

```kotlin
data class CreateDatasetRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*\\.[a-z][a-z0-9_]*$")
    val name: String,

    @field:NotBlank
    @field:Email
    val owner: String,

    val team: String? = null,

    @field:Size(max = 1000)
    val description: String? = null,

    @field:NotBlank
    val sql: String,

    @field:Size(max = 10)
    val tags: List<String> = emptyList(),

    val dependencies: List<String> = emptyList(),

    val schedule: ScheduleRequest? = null,
)

data class ScheduleRequest(
    @field:NotBlank
    val cron: String,

    val timezone: String = "UTC",
)
```

#### ExecuteDatasetRequest

```kotlin
data class ExecuteDatasetRequest(
    val parameters: Map<String, Any> = emptyMap(),

    @field:Min(1)
    @field:Max(10000)
    val limit: Int = 1000,

    @field:Min(1)
    @field:Max(3600)
    val timeout: Int = 600,
)
```

#### ExecutionResultDto

```kotlin
data class ExecutionResultDto(
    val rows: List<Map<String, Any>>,
    val rowCount: Int,
    val durationSeconds: Double,
    val renderedSql: String,
)
```

### 4.3 Mapper

```kotlin
object DatasetMapper {
    fun toDto(entity: DatasetEntity): DatasetDto = DatasetDto(
        name = entity.name,
        type = "Dataset",
        owner = entity.owner,
        team = entity.team,
        description = entity.description,
        tags = entity.tags.toList(),
        sql = entity.sql,
        dependencies = entity.dependencies.toList(),
        schedule = entity.scheduleCron?.let {
            ScheduleDto(cron = it, timezone = entity.scheduleTimezone ?: "UTC")
        },
        createdAt = entity.createdAt.toString(),
        updatedAt = entity.updatedAt.toString(),
    )

    fun toListDto(entity: DatasetEntity): DatasetListDto = DatasetListDto(
        name = entity.name,
        type = "Dataset",
        owner = entity.owner,
        team = entity.team,
        description = entity.description,
        tags = entity.tags.toList(),
        createdAt = entity.createdAt.toString(),
        updatedAt = entity.updatedAt.toString(),
    )

    fun toEntity(request: CreateDatasetRequest): DatasetEntity = DatasetEntity(
        name = request.name,
        owner = request.owner,
        team = request.team,
        description = request.description,
        sql = request.sql,
        tags = request.tags.toSet(),
        dependencies = request.dependencies.toSet(),
        scheduleCron = request.schedule?.cron,
        scheduleTimezone = request.schedule?.timezone ?: "UTC",
    )
}
```

---

## 5. Implementation Notes

### 5.1 Repository Layer

#### Domain Interface (Port)

```kotlin
// module-core-domain/repository/DatasetRepositoryJpa.kt
interface DatasetRepositoryJpa {
    fun save(dataset: DatasetEntity): DatasetEntity
    fun findById(id: String): DatasetEntity?
    fun findByName(name: String): DatasetEntity?
    fun existsByName(name: String): Boolean
    fun deleteByName(name: String)
}

// module-core-domain/repository/DatasetRepositoryDsl.kt
interface DatasetRepositoryDsl {
    fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<DatasetEntity>

    fun countByFilters(
        tag: String?,
        owner: String?,
        search: String?,
    ): Long
}
```

#### Infrastructure Implementation (Adapter)

```kotlin
// module-core-infra/repository/DatasetRepositoryJpaImpl.kt
@Repository("datasetRepositoryJpa")
class DatasetRepositoryJpaImpl(
    private val springDataRepository: DatasetRepositoryJpaSpringData,
) : DatasetRepositoryJpa {
    override fun save(dataset: DatasetEntity): DatasetEntity =
        springDataRepository.save(dataset)

    override fun findById(id: String): DatasetEntity? =
        springDataRepository.findById(id).orElse(null)

    override fun findByName(name: String): DatasetEntity? =
        springDataRepository.findByName(name)

    override fun existsByName(name: String): Boolean =
        springDataRepository.existsByName(name)

    override fun deleteByName(name: String) =
        springDataRepository.deleteByName(name)
}

// module-core-infra/repository/DatasetRepositoryJpaSpringData.kt
interface DatasetRepositoryJpaSpringData : JpaRepository<DatasetEntity, String> {
    fun findByName(name: String): DatasetEntity?
    fun existsByName(name: String): Boolean
    fun deleteByName(name: String)
}
```

### 5.2 Service Layer

```kotlin
// module-core-domain/service/DatasetService.kt
@Service
@Transactional(readOnly = true)
class DatasetService(
    private val datasetRepositoryJpa: DatasetRepositoryJpa,
    private val datasetRepositoryDsl: DatasetRepositoryDsl,
) {
    fun listDatasets(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<DatasetListDto> {
        val entities = datasetRepositoryDsl.findByFilters(tag, owner, search, limit, offset)
        return entities.map { DatasetMapper.toListDto(it) }
    }

    fun getDataset(name: String): DatasetDto? {
        val entity = datasetRepositoryJpa.findByName(name)
        return entity?.let { DatasetMapper.toDto(it) }
    }

    @Transactional
    fun registerDataset(request: CreateDatasetRequest): DatasetDto {
        if (datasetRepositoryJpa.existsByName(request.name)) {
            throw DatasetAlreadyExistsException(request.name)
        }
        val entity = DatasetMapper.toEntity(request)
        val saved = datasetRepositoryJpa.save(entity)
        return DatasetMapper.toDto(saved)
    }
}
```

### 5.3 Execution Service

```kotlin
// module-core-domain/service/DatasetExecutionService.kt
@Service
class DatasetExecutionService(
    private val datasetService: DatasetService,
    private val queryEngineClient: QueryEngineClient,
) {
    fun executeDataset(
        datasetName: String,
        request: ExecuteDatasetRequest,
    ): ExecutionResultDto {
        val dataset = datasetService.getDataset(datasetName)
            ?: throw DatasetNotFoundException(datasetName)

        val renderedSql = renderSqlWithParameters(dataset.sql!!, request.parameters)
        val startTime = System.currentTimeMillis()

        val rows = queryEngineClient.execute(
            sql = renderedSql,
            limit = request.limit,
            timeoutSeconds = request.timeout,
        )

        val duration = (System.currentTimeMillis() - startTime) / 1000.0

        return ExecutionResultDto(
            rows = rows,
            rowCount = rows.size,
            durationSeconds = duration,
            renderedSql = renderedSql,
        )
    }

    private fun renderSqlWithParameters(sql: String, parameters: Map<String, Any>): String {
        var rendered = sql
        parameters.forEach { (key, value) ->
            rendered = rendered.replace("{{$key}}", value.toString())
            rendered = rendered.replace("\${$key}", value.toString())
        }
        return rendered
    }
}
```

### 5.4 Controller Layer

```kotlin
// module-server-api/controller/DatasetController.kt
@RestController
@RequestMapping("/api/v1/datasets")
class DatasetController(
    private val datasetService: DatasetService,
    private val datasetExecutionService: DatasetExecutionService,
) {
    @GetMapping
    fun listDatasets(
        @RequestParam tag: String?,
        @RequestParam owner: String?,
        @RequestParam search: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(500) limit: Int,
        @RequestParam(defaultValue = "0") @Min(0) offset: Int,
    ): ResponseEntity<List<DatasetListDto>> {
        val datasets = datasetService.listDatasets(tag, owner, search, limit, offset)
        return ResponseEntity.ok(datasets)
    }

    @GetMapping("/{name}")
    fun getDataset(@PathVariable name: String): ResponseEntity<DatasetDto> {
        val dataset = datasetService.getDataset(name)
            ?: throw DatasetNotFoundException(name)
        return ResponseEntity.ok(dataset)
    }

    @PostMapping
    fun registerDataset(
        @Valid @RequestBody request: CreateDatasetRequest,
    ): ResponseEntity<Map<String, String>> {
        val dataset = datasetService.registerDataset(request)
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(mapOf(
                "message" to "Dataset '${dataset.name}' registered successfully",
                "name" to dataset.name,
            ))
    }

    @PostMapping("/{name}/run")
    fun executeDataset(
        @PathVariable name: String,
        @Valid @RequestBody request: ExecuteDatasetRequest,
    ): ResponseEntity<ExecutionResultDto> {
        val result = datasetExecutionService.executeDataset(name, request)
        return ResponseEntity.ok(result)
    }
}
```

### 5.5 Exception Handling

```kotlin
// Exceptions
class DatasetNotFoundException(val name: String) :
    RuntimeException("Dataset '$name' not found")

class DatasetAlreadyExistsException(val name: String) :
    RuntimeException("Dataset '$name' already exists")

class DatasetExecutionTimeoutException(val name: String, val timeoutSeconds: Int) :
    RuntimeException("Dataset execution timed out after $timeoutSeconds seconds")

// Exception Handler
@RestControllerAdvice
class DatasetExceptionHandler {
    @ExceptionHandler(DatasetNotFoundException::class)
    fun handleNotFound(ex: DatasetNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                code = "DATASET_NOT_FOUND",
                message = ex.message!!,
                details = mapOf("dataset_name" to ex.name),
            ))
    }

    @ExceptionHandler(DatasetAlreadyExistsException::class)
    fun handleConflict(ex: DatasetAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(
                code = "DATASET_ALREADY_EXISTS",
                message = ex.message!!,
                details = mapOf("dataset_name" to ex.name),
            ))
    }

    @ExceptionHandler(DatasetExecutionTimeoutException::class)
    fun handleTimeout(ex: DatasetExecutionTimeoutException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.REQUEST_TIMEOUT)
            .body(ErrorResponse(
                code = "DATASET_EXECUTION_TIMEOUT",
                message = ex.message!!,
                details = mapOf(
                    "dataset_name" to ex.name,
                    "timeout_seconds" to ex.timeoutSeconds,
                ),
            ))
    }
}

data class ErrorResponse(
    val error: ErrorBody,
) {
    constructor(code: String, message: String, details: Map<String, Any>) :
        this(ErrorBody(code, message, details))
}

data class ErrorBody(
    val code: String,
    val message: String,
    val details: Map<String, Any>,
)
```

---

## 6. Testing Requirements

### 6.1 Unit Tests

**Required Coverage: 80%+**

```kotlin
// DatasetServiceTest.kt
@ExtendWith(MockitoExtension::class)
class DatasetServiceTest {
    @Mock private lateinit var datasetRepositoryJpa: DatasetRepositoryJpa
    @Mock private lateinit var datasetRepositoryDsl: DatasetRepositoryDsl
    @InjectMocks private lateinit var datasetService: DatasetService

    @Test
    fun `should register dataset successfully`() {
        // Given
        val request = CreateDatasetRequest(
            name = "iceberg.test.dataset",
            owner = "test@example.com",
            sql = "SELECT 1",
        )
        given(datasetRepositoryJpa.existsByName(request.name)).willReturn(false)
        given(datasetRepositoryJpa.save(any())).willAnswer { it.arguments[0] }

        // When
        val result = datasetService.registerDataset(request)

        // Then
        assertThat(result.name).isEqualTo(request.name)
        verify(datasetRepositoryJpa).save(any())
    }

    @Test
    fun `should throw exception when dataset already exists`() {
        // Given
        val request = CreateDatasetRequest(
            name = "iceberg.test.dataset",
            owner = "test@example.com",
            sql = "SELECT 1",
        )
        given(datasetRepositoryJpa.existsByName(request.name)).willReturn(true)

        // When/Then
        assertThatThrownBy { datasetService.registerDataset(request) }
            .isInstanceOf(DatasetAlreadyExistsException::class.java)
    }

    @Test
    fun `should list datasets with filters`() {
        // Given
        val entities = listOf(createTestDatasetEntity())
        given(datasetRepositoryDsl.findByFilters("feed", null, null, 50, 0))
            .willReturn(entities)

        // When
        val result = datasetService.listDatasets("feed", null, null, 50, 0)

        // Then
        assertThat(result).hasSize(1)
        assertThat(result[0].tags).contains("feed")
    }
}
```

### 6.2 Integration Tests

```kotlin
// DatasetControllerTest.kt
@SpringBootTest(webEnvironment = RANDOM_PORT)
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
class DatasetControllerTest {
    @Autowired private lateinit var testRestTemplate: TestRestTemplate
    @Autowired private lateinit var datasetRepositoryJpa: DatasetRepositoryJpa

    @Container
    @ServiceConnection
    val mysql = MySQLContainer("mysql:8.0")

    @BeforeEach
    fun setup() {
        // Clean up test data
    }

    @Test
    fun `should list datasets with filters`() {
        // Given
        val dataset = createTestDataset()
        datasetRepositoryJpa.save(dataset)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/datasets?tag=feed",
            Array<DatasetListDto>::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body).hasSize(1)
    }

    @Test
    fun `should get dataset by name`() {
        // Given
        val dataset = createTestDataset()
        datasetRepositoryJpa.save(dataset)

        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/datasets/${dataset.name}",
            DatasetDto::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.body?.name).isEqualTo(dataset.name)
    }

    @Test
    fun `should return 404 for non-existent dataset`() {
        // When
        val response = testRestTemplate.getForEntity(
            "/api/v1/datasets/unknown.dataset",
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.NOT_FOUND)
        assertThat(response.body?.error?.code).isEqualTo("DATASET_NOT_FOUND")
    }

    @Test
    fun `should register dataset successfully`() {
        // Given
        val request = CreateDatasetRequest(
            name = "iceberg.test.new_dataset",
            owner = "test@example.com",
            sql = "SELECT 1",
        )

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/datasets",
            request,
            Map::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.CREATED)
        assertThat(response.body?.get("name")).isEqualTo(request.name)
    }

    @Test
    fun `should return 409 for duplicate dataset`() {
        // Given
        val existing = createTestDataset()
        datasetRepositoryJpa.save(existing)

        val request = CreateDatasetRequest(
            name = existing.name,
            owner = "test@example.com",
            sql = "SELECT 1",
        )

        // When
        val response = testRestTemplate.postForEntity(
            "/api/v1/datasets",
            request,
            ErrorResponse::class.java
        )

        // Then
        assertThat(response.statusCode).isEqualTo(HttpStatus.CONFLICT)
    }
}
```

### 6.3 CLI Integration Tests

```bash
# Test dataset operations against running server
dli dataset list --server-url http://localhost:8081
dli dataset list --tag feed --server-url http://localhost:8081
dli dataset get iceberg.test.dataset --server-url http://localhost:8081
dli dataset register test_dataset.yaml --server-url http://localhost:8081
dli dataset run iceberg.test.dataset --param date=2026-01-01 --server-url http://localhost:8081
```

### 6.4 Test Data Factory

```kotlin
object DatasetTestFactory {
    fun createTestDataset(
        name: String = "iceberg.test.dataset",
        owner: String = "test@example.com",
        tags: Set<String> = setOf("feed", "test"),
    ) = DatasetEntity(
        name = name,
        owner = owner,
        team = "@test-team",
        description = "Test dataset",
        sql = "SELECT 1",
        tags = tags,
        dependencies = setOf("iceberg.raw.events"),
        scheduleCron = "0 6 * * *",
        scheduleTimezone = "UTC",
    )
}
```

---

## 7. Related Documents

### Internal References

| Document | Description |
|----------|-------------|
| [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) | Implementation timeline and phases |
| [`../INTEGRATION_PATTERNS.md`](../INTEGRATION_PATTERNS.md) | Spring Boot patterns and conventions |
| [`../ERROR_CODES.md`](../ERROR_CODES.md) | Error code definitions |
| [`../CLI_API_MAPPING.md`](../CLI_API_MAPPING.md) | Complete CLI to API mapping |
| [`archive/P0_CRITICAL_APIS.md`](./archive/P0_CRITICAL_APIS.md) | P0 APIs overview |

### CLI Reference

| CLI Feature | Documentation |
|-------------|---------------|
| Library API | `project-interface-cli/features/LIBRARY_RELEASE.md` |
| CLI Patterns | `project-interface-cli/docs/PATTERNS.md` |

### Existing Code References

| Component | File | Pattern |
|-----------|------|---------|
| Entity Example | `module-core-domain/entity/PipelineEntity.kt` | JPA entity pattern |
| Service Example | `module-core-domain/service/PipelineService.kt` | Concrete service class |
| Controller Example | `module-server-api/controller/PipelineController.kt` | REST controller pattern |
| Repository Pattern | `module-core-domain/repository/PipelineRepositoryJpa.kt` | Domain interface |

---

## Appendix: Error Codes

| Code | HTTP Status | Description |
|------|-------------|-------------|
| `DATASET_NOT_FOUND` | 404 | Dataset with specified name does not exist |
| `DATASET_ALREADY_EXISTS` | 409 | Dataset with specified name already exists |
| `DATASET_EXECUTION_TIMEOUT` | 408 | Query execution exceeded timeout |
| `DATASET_EXECUTION_FAILED` | 500 | Query execution failed |
| `INVALID_DATASET_NAME` | 422 | Dataset name format is invalid |
| `INVALID_SQL` | 422 | SQL expression is invalid |
| `INVALID_PARAMETER` | 400 | Request parameter is invalid |

---

*Last Updated: 2026-01-01*

---

## Appendix C: Review Feedback

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.0/5

### Strengths
- Correctly references existing `DatasetEntity` and provides extension guidance
- Repository pattern follows hexagonal architecture
- Service implementation correctly uses concrete class pattern
- Clear CLI command mapping matches CLI_API_MAPPING.md

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | Exception classes are `RuntimeException` subclasses, not `BasecampException` | Extend `BasecampException` with proper `errorCode` and `httpStatus` |
| **High** | HTTP 422 used for validation errors but ERROR_CODES.md shows 400 | Change to HTTP 400 for validation errors |
| **Medium** | `ErrorResponse` redefined - should use common one | Use shared `ErrorResponse` from common module |
| **Low** | `DatasetDto` and `DatasetListDto` are separate - could use inheritance | Consider builder pattern to reduce duplication |

### Required Changes Before Implementation
1. Make exception classes extend `BasecampException`
2. Change HTTP 422 to 400 for validation errors
3. Use shared `ErrorResponse` from common module
4. Add `DatasetMapper` with `toListDto(entity, includeSql = false)` pattern
