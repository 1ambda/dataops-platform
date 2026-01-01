# Integration Patterns - Spring Boot + Kotlin Implementation

> **Target Audience:** Backend developers implementing Basecamp Server APIs
> **Purpose:** Spring Boot + Kotlin patterns following hexagonal architecture
> **Cross-Reference:** [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md) for architecture details

---

## 📋 Table of Contents

1. [Hexagonal Architecture Patterns](#1-hexagonal-architecture-patterns)
2. [Service Implementation Patterns](#2-service-implementation-patterns)
3. [Repository Layer Patterns](#3-repository-layer-patterns)
4. [Controller Patterns](#4-controller-patterns)
5. [Error Handling Patterns](#5-error-handling-patterns)
6. [Testing Patterns](#6-testing-patterns)

---

## 1. Hexagonal Architecture Patterns

### 1.1 Module Structure

```
project-basecamp-server/
├── module-core-common/          # Shared utilities
├── module-core-domain/          # Domain models & interfaces
│   ├── src/main/kotlin/domain/
│   │   ├── entity/              # JPA entities
│   │   ├── repository/          # Repository interfaces (ports)
│   │   └── service/            # Domain services (concrete)
├── module-core-infra/           # Infrastructure implementations
│   ├── src/main/kotlin/infra/
│   │   ├── repository/          # Repository implementations (adapters)
│   │   └── external/           # External service clients
└── module-server-api/           # REST API layer
    ├── src/main/kotlin/api/
    │   ├── controller/          # REST controllers
    │   ├── dto/                # Data transfer objects
    │   └── mapper/             # DTO ↔ Entity mappers
```

### 1.2 Dependency Rules

```kotlin
// ✅ CORRECT: Domain → Infrastructure dependency injection
@Service
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,    // Domain interface
    private val metricRepositoryDsl: MetricRepositoryDsl,    // Domain interface
) {
    // Service implementation
}

// ❌ INCORRECT: Do not create service interfaces
interface MetricService {  // Don't do this
    fun createMetric(...): MetricDto
}
```

### 1.3 Bean Configuration Pattern

```kotlin
// Infrastructure Layer Configuration
@Configuration
@ComponentScan(basePackages = [
    "com.basecamp.domain.service",      // Domain services
    "com.basecamp.infra.repository",    // Infrastructure implementations
    "com.basecamp.infra.external"       // External clients
])
class InfrastructureConfig {

    // Repository bean naming for proper injection
    @Bean("metricRepositoryJpa")
    fun metricRepositoryJpa(springDataRepo: MetricRepositoryJpaSpringData): MetricRepositoryJpa {
        return MetricRepositoryJpaImpl(springDataRepo)
    }

    @Bean("metricRepositoryDsl")
    fun metricRepositoryDsl(entityManager: EntityManager): MetricRepositoryDsl {
        return MetricRepositoryDslImpl(entityManager)
    }
}
```

---

## 2. Service Implementation Patterns

### 2.1 Standard Service Pattern

```kotlin
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
    private val metricValidator: MetricValidator,
    private val metricExecutor: MetricExecutor,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricService::class.java)
    }

    @Transactional
    fun createMetric(request: CreateMetricRequest): MetricDto {
        logger.info("Creating metric: {}", request.name)

        // 1. Validation
        metricValidator.validate(request)

        // 2. Check for duplicates
        metricRepositoryJpa.findByName(request.name)?.let {
            throw MetricAlreadyExistsException(request.name)
        }

        // 3. Create entity
        val entity = MetricEntity(
            id = UUID.randomUUID().toString(),
            name = request.name,
            owner = request.owner,
            sql = request.sql,
            tags = request.tags.toSet(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // 4. Save and return
        val saved = metricRepositoryJpa.save(entity)
        logger.info("Metric created successfully: {}", saved.name)

        return MetricMapper.toDto(saved)
    }

    fun listMetrics(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricDto> {
        logger.debug("Listing metrics with filters: tag={}, owner={}, search={}", tag, owner, search)

        val entities = metricRepositoryDsl.findByFilters(
            tag = tag,
            owner = owner,
            search = search,
            limit = limit,
            offset = offset
        )

        return entities.map { MetricMapper.toDto(it) }
    }

    fun getMetric(name: String): MetricDto? {
        logger.debug("Getting metric: {}", name)

        return metricRepositoryJpa.findByName(name)
            ?.let { MetricMapper.toDto(it) }
    }

    @Transactional
    fun deleteMetric(name: String) {
        logger.info("Deleting metric: {}", name)

        val entity = metricRepositoryJpa.findByName(name)
            ?: throw MetricNotFoundException(name)

        metricRepositoryJpa.delete(entity)
        logger.info("Metric deleted successfully: {}", name)
    }
}
```

### 2.2 Command/Query Pattern

```kotlin
// Command Objects (for mutations)
data class CreateMetricCommand(
    val name: String,
    val owner: String,
    val sql: String,
    val tags: List<String> = emptyList(),
    val description: String? = null,
)

data class UpdateMetricCommand(
    val name: String,
    val owner: String?,
    val sql: String?,
    val tags: List<String>?,
    val description: String?,
)

// Query Objects (for reads)
data class ListMetricsQuery(
    val tag: String? = null,
    val owner: String? = null,
    val search: String? = null,
    val limit: Int = 50,
    val offset: Int = 0,
)

data class GetMetricQuery(
    val name: String,
    val includeSQL: Boolean = true,
)

// Service with Command/Query pattern
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
) {
    @Transactional
    fun createMetric(command: CreateMetricCommand): MetricDto {
        // Implementation using command
    }

    fun listMetrics(query: ListMetricsQuery): List<MetricDto> {
        // Implementation using query
    }
}
```

---

## 3. Repository Layer Patterns

### 3.1 Domain Repository Interfaces

```kotlin
// Domain Layer - Simple CRUD interface
interface MetricRepositoryJpa {
    fun save(metric: MetricEntity): MetricEntity
    fun findById(id: String): MetricEntity?
    fun findByName(name: String): MetricEntity?
    fun findAll(): List<MetricEntity>
    fun delete(metric: MetricEntity)
    fun existsByName(name: String): Boolean
}

// Domain Layer - Complex Query interface
interface MetricRepositoryDsl {
    fun findByFilters(
        tag: String?,
        owner: String?,
        search: String?,
        limit: Int,
        offset: Int,
    ): List<MetricEntity>

    fun findByTagsIn(tags: List<String>): List<MetricEntity>
    fun findByOwnerContaining(ownerPattern: String): List<MetricEntity>
    fun countByOwner(owner: String): Long
}
```

### 3.2 Infrastructure Repository Implementations

```kotlin
// Infrastructure Layer - Spring Data interface
@Repository
interface MetricRepositoryJpaSpringData : JpaRepository<MetricEntity, String> {
    fun findByName(name: String): MetricEntity?
    fun existsByName(name: String): Boolean
}

// Infrastructure Layer - JPA implementation
@Repository("metricRepositoryJpa")
class MetricRepositoryJpaImpl(
    private val springDataRepository: MetricRepositoryJpaSpringData,
) : MetricRepositoryJpa {

    override fun save(metric: MetricEntity): MetricEntity {
        return springDataRepository.save(metric)
    }

    override fun findById(id: String): MetricEntity? {
        return springDataRepository.findById(id).orElse(null)
    }

    override fun findByName(name: String): MetricEntity? {
        return springDataRepository.findByName(name)
    }

    override fun findAll(): List<MetricEntity> {
        return springDataRepository.findAll()
    }

    override fun delete(metric: MetricEntity) {
        springDataRepository.delete(metric)
    }

    override fun existsByName(name: String): Boolean {
        return springDataRepository.existsByName(name)
    }
}

// Infrastructure Layer - QueryDSL implementation
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

        // Apply filters
        tag?.let { query = query.where(metric.tags.contains(it)) }

        owner?.let { query = query.where(metric.owner.containsIgnoreCase(it)) }

        search?.let { searchTerm ->
            query = query.where(
                metric.name.containsIgnoreCase(searchTerm)
                    .or(metric.description.containsIgnoreCase(searchTerm))
            )
        }

        return query
            .offset(offset.toLong())
            .limit(limit.toLong())
            .fetch()
    }

    override fun findByTagsIn(tags: List<String>): List<MetricEntity> {
        return queryFactory
            .selectFrom(metric)
            .where(metric.tags.any().`in`(tags))
            .fetch()
    }
}
```

### 3.3 Entity Pattern

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

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "metric_tags",
        joinColumns = [JoinColumn(name = "metric_id")]
    )
    @Column(name = "tag", length = 50)
    val tags: Set<String> = emptySet(),

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
        tags = emptySet(),
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MetricEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()

    override fun toString(): String {
        return "MetricEntity(id='$id', name='$name', owner='$owner')"
    }
}
```

---

## 4. Controller Patterns

### 4.1 Standard REST Controller

```kotlin
@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin
@Validated
class MetricController(
    private val metricService: MetricService,
    private val metricValidator: MetricValidator,
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
        logger.info("GET /api/v1/metrics - tag: {}, owner: {}, search: {}, limit: {}, offset: {}",
                   tag, owner, search, limit, offset)

        val metrics = metricService.listMetrics(
            tag = tag,
            owner = owner,
            search = search,
            limit = limit,
            offset = offset
        )

        return ResponseEntity.ok(metrics)
    }

    @GetMapping("/{name}")
    fun getMetric(
        @PathVariable @NotBlank name: String,
    ): ResponseEntity<MetricDto> {
        logger.info("GET /api/v1/metrics/{}", name)

        val metric = metricService.getMetric(name)
            ?: return ResponseEntity.notFound().build()

        return ResponseEntity.ok(metric)
    }

    @PostMapping
    fun createMetric(
        @RequestBody @Valid request: CreateMetricRequest,
    ): ResponseEntity<CreateMetricResponse> {
        logger.info("POST /api/v1/metrics - name: {}", request.name)

        // Additional validation
        metricValidator.validateCreate(request)

        val metric = metricService.createMetric(request)

        val response = CreateMetricResponse(
            message = "Metric '${metric.name}' registered successfully",
            name = metric.name
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/{name}/run")
    fun runMetric(
        @PathVariable @NotBlank name: String,
        @RequestBody @Valid request: RunMetricRequest,
    ): ResponseEntity<MetricExecutionResult> {
        logger.info("POST /api/v1/metrics/{}/run", name)

        val result = metricService.executeMetric(
            metricName = name,
            parameters = request.parameters,
            limit = request.limit,
            timeout = request.timeout
        )

        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{name}")
    fun deleteMetric(
        @PathVariable @NotBlank name: String,
    ): ResponseEntity<Void> {
        logger.info("DELETE /api/v1/metrics/{}", name)

        metricService.deleteMetric(name)

        return ResponseEntity.noContent().build()
    }
}
```

### 4.2 DTO and Mapper Patterns

```kotlin
// Request DTOs
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

    val team: String?,

    @field:Size(max = 1000, message = "Description must not exceed 1000 characters")
    val description: String?,

    @field:NotBlank(message = "SQL is required")
    @field:Size(max = 10000, message = "SQL must not exceed 10000 characters")
    val sql: String,

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

// Response DTOs
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

// Mapper
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
            sourceTable = extractSourceTable(entity.sql),
            dependencies = extractDependencies(entity.sql),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    private fun extractSourceTable(sql: String): String? {
        // Simple regex to extract main table - implement properly
        val regex = Regex("FROM\\s+([\\w.]+)", RegexOption.IGNORE_CASE)
        return regex.find(sql)?.groupValues?.get(1)
    }

    private fun extractDependencies(sql: String): List<String> {
        // Extract all table references - implement properly
        val regex = Regex("(?:FROM|JOIN)\\s+([\\w.]+)", RegexOption.IGNORE_CASE)
        return regex.findAll(sql)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }
}
```

---

## 5. Error Handling Patterns

### 5.1 Exception Hierarchy

```kotlin
// Base exception
sealed class BasecampException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    abstract val errorCode: String
}

// Domain exceptions
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

class ValidationException(
    field: String,
    value: String,
    reason: String,
) : BasecampException(
    "Validation failed for field '$field' with value '$value': $reason"
) {
    override val errorCode = "VALIDATION_ERROR"
    val field = field
    val value = value
}
```

### 5.2 Global Exception Handler

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    companion object {
        private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)
    }

    @ExceptionHandler(MetricNotFoundException::class)
    fun handleMetricNotFound(ex: MetricNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Metric not found: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.builder()
                .code(ex.errorCode)
                .message(ex.message!!)
                .build())
    }

    @ExceptionHandler(MetricAlreadyExistsException::class)
    fun handleMetricAlreadyExists(ex: MetricAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.warn("Metric already exists: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.builder()
                .code(ex.errorCode)
                .message(ex.message!!)
                .build())
    }

    @ExceptionHandler(ValidationException::class)
    fun handleValidation(ex: ValidationException): ResponseEntity<ErrorResponse> {
        logger.warn("Validation error: {}", ex.message)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .code(ex.errorCode)
                .message(ex.message!!)
                .details(mapOf(
                    "field" to ex.field,
                    "value" to ex.value
                ))
                .build())
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleMethodArgumentNotValid(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val fieldErrors = ex.bindingResult.fieldErrors.associate { error ->
            error.field to (error.defaultMessage ?: "Invalid value")
        }

        logger.warn("Request validation failed: {}", fieldErrors)

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .details(fieldErrors)
                .build())
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneral(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error", ex)

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")
                .build())
    }
}

data class ErrorResponse(
    val error: ErrorDetails,
) {
    data class ErrorDetails(
        val code: String,
        val message: String,
        val details: Map<String, Any>? = null,
    )

    companion object {
        fun builder() = ErrorResponseBuilder()
    }
}

class ErrorResponseBuilder {
    private var code: String = ""
    private var message: String = ""
    private var details: Map<String, Any>? = null

    fun code(code: String) = apply { this.code = code }
    fun message(message: String) = apply { this.message = message }
    fun details(details: Map<String, Any>) = apply { this.details = details }

    fun build() = ErrorResponse(
        error = ErrorResponse.ErrorDetails(code, message, details)
    )
}
```

---

## 6. Testing Patterns

### 6.1 Unit Testing with MockK

```kotlin
@ExtendWith(MockitoExtension::class)
class MetricServiceTest {
    @Mock
    private lateinit var metricRepositoryJpa: MetricRepositoryJpa

    @Mock
    private lateinit var metricRepositoryDsl: MetricRepositoryDsl

    @Mock
    private lateinit var metricValidator: MetricValidator

    @InjectMocks
    private lateinit var metricService: MetricService

    @Test
    fun `should create metric successfully`() {
        // Given
        val request = CreateMetricRequest(
            name = "test.metric.example",
            owner = "test@example.com",
            sql = "SELECT 1"
        )

        val entity = MetricEntity(
            id = "test-id",
            name = request.name,
            owner = request.owner,
            sql = request.sql,
            tags = emptySet(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        given(metricRepositoryJpa.findByName(request.name)).willReturn(null)
        given(metricRepositoryJpa.save(any())).willReturn(entity)

        // When
        val result = metricService.createMetric(request)

        // Then
        assertThat(result.name).isEqualTo(request.name)
        assertThat(result.owner).isEqualTo(request.owner)
        verify(metricValidator).validate(request)
        verify(metricRepositoryJpa).save(any())
    }

    @Test
    fun `should throw exception when metric already exists`() {
        // Given
        val request = CreateMetricRequest(
            name = "existing.metric",
            owner = "test@example.com",
            sql = "SELECT 1"
        )

        val existingEntity = MetricEntity(
            id = "existing-id",
            name = request.name,
            owner = "other@example.com",
            sql = "SELECT 2",
            tags = emptySet(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        given(metricRepositoryJpa.findByName(request.name)).willReturn(existingEntity)

        // When & Then
        assertThrows<MetricAlreadyExistsException> {
            metricService.createMetric(request)
        }

        verify(metricRepositoryJpa, never()).save(any())
    }
}
```

### 6.2 Integration Testing

```kotlin
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
])
class MetricControllerIntegrationTest {

    @Autowired
    private lateinit var testRestTemplate: TestRestTemplate

    @Autowired
    private lateinit var metricRepository: MetricRepositoryJpaSpringData

    @BeforeEach
    fun setUp() {
        metricRepository.deleteAll()
    }

    @Test
    fun `should create metric via REST API`() {
        // Given
        val request = CreateMetricRequest(
            name = "test.example.metric",
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
        assertThat(saved?.owner).isEqualTo(request.owner)
    }

    @Test
    fun `should return 409 when creating duplicate metric`() {
        // Given
        val existingMetric = MetricEntity(
            id = UUID.randomUUID().toString(),
            name = "duplicate.metric",
            owner = "existing@example.com",
            sql = "SELECT 1",
            tags = emptySet(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        metricRepository.save(existingMetric)

        val request = CreateMetricRequest(
            name = "duplicate.metric",
            owner = "new@example.com",
            sql = "SELECT 2"
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

---

## 🔗 Related Documentation

- **Architecture Overview**: [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md)
- **Implementation Plan**: [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md)
- **API Specifications**: [`METRIC_FEATURE.md`](./METRIC_FEATURE.md), [`DATASET_FEATURE.md`](./DATASET_FEATURE.md)
- **Error Handling**: [`ERROR_CODES.md`](./ERROR_CODES.md)

---

*This document provides implementation-ready Spring Boot + Kotlin patterns following hexagonal architecture principles for the Basecamp Server API.*