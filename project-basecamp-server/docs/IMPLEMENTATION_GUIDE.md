# Implementation Guide - Spring Boot + Kotlin

> **Last Updated:** 2026-01-01
> **Target Audience:** Backend developers implementing Basecamp Server APIs
> **Purpose:** Spring Boot + Kotlin patterns following hexagonal architecture
> **Cross-Reference:** [architecture.md](./architecture.md) for architecture details

---

## Table of Contents

0. [Custom Rules](#custom-rules)
1. [Hexagonal Architecture Patterns](#hexagonal-architecture-patterns)
2. [Service Implementation Patterns](#service-implementation-patterns)
3. [Repository Layer Patterns](#repository-layer-patterns)
4. [Controller Patterns](#controller-patterns)
5. [DTO and Mapper Patterns](#dto-and-mapper-patterns)
6. [Entity Patterns](#entity-patterns)
7. [Testing Patterns](#testing-patterns)
8. 
---

## Custom Rules (Critical)

- 미래에는 MySQL 을 사용할 것이나, 현재는 H2 기반의 Entity 를 설계해 사용합니다. 단, MySQL 마이그레이션을 고려해주세요.
- module-core-infra 에서 데이터 조회는 QueryDSL 을 사용합니다. 데이터 추가 / 변경 / 삭제는 JPA 를 사용할 수 있습니다.
- **module-core-domain 내 Entity 는 JPA 연관 관계를 절대로 (중요) 사용하지 않습니다.** (See [Entity Relationship Rules](#entity-relationship-rules-critical))
- Test 는 ServiceTest 를 위주로 작성합니다. ControllerTest 도 작성합니다.
- SpringBootApplication 을 띄우는 무거운 테스트는 지양합니다.
- Basecamp Server 의 자체 기능이 아니거나 외부와의 연동 (e.g, Airflow 연동, BigQuery 실행 등) 의 경우에는 Mock 방식으로 작업합니다.

---

## Entity Relationship Rules (CRITICAL)

> **Core Rule**: Entities must NOT use JPA relationship annotations (`@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`).

### Entity Design Principles

1. **Store Foreign Keys as Simple Fields**: Use `Long` or `String` IDs instead of entity references
2. **No Cascading Operations**: Handle related entity persistence explicitly in services
3. **QueryDSL for Aggregations**: Fetch related data through QueryDSL, not lazy loading

### Correct Entity Pattern

```kotlin
@Entity
@Table(name = "orders")
class OrderEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // ✅ CORRECT: Foreign key as simple field
    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    val status: OrderStatus,

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    // ❌ NO relationship fields like:
    // val user: UserEntity
    // val items: List<OrderItemEntity>
}
```

### JPA vs QueryDSL Decision Guide

| Use Case | Technology | Rationale |
|----------|------------|-----------|
| **Create/Update/Delete** single entity | JPA | Simple persistence operations |
| **Find by 1-2 fields** | JPA | `findById()`, `findByName()` are fine |
| **Find by 3+ fields** | QueryDSL | Method names become unwieldy |
| **Dynamic conditions** (user-selected filters) | QueryDSL | BooleanBuilder for optional conditions |
| **Fetch related entities** | QueryDSL | Explicit joins, no lazy loading surprises |
| **Aggregation projections** | QueryDSL | DTO projections with joined data |

### QueryDSL Aggregation Pattern

When you need to fetch an entity with its related entities:

```kotlin
// Service Layer - Aggregation Logic
@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepositoryJpa: OrderRepositoryJpa,
    private val orderRepositoryDsl: OrderRepositoryDsl,
) {
    fun getOrderWithItems(orderId: Long): OrderDetailDto? {
        // Fetch aggregated data via QueryDSL
        val aggregation = orderRepositoryDsl.findOrderWithItems(orderId)
            ?: return null

        return OrderDetailDto(
            id = aggregation.order.id,
            userId = aggregation.order.userId,
            status = aggregation.order.status,
            items = aggregation.items.map { item ->
                OrderItemDto(
                    id = item.id,
                    productName = item.productName,
                    quantity = item.quantity,
                )
            },
        )
    }
}

// QueryDSL Repository Implementation
@Repository("orderRepositoryDsl")
class OrderRepositoryDslImpl(
    private val entityManager: EntityManager,
) : OrderRepositoryDsl {
    private val queryFactory = JPAQueryFactory(entityManager)
    private val order = QOrderEntity.orderEntity
    private val item = QOrderItemEntity.orderItemEntity

    override fun findOrderWithItems(orderId: Long): OrderAggregation? {
        // Separate queries - explicit and predictable
        val orderEntity = queryFactory
            .selectFrom(order)
            .where(order.id.eq(orderId))
            .fetchOne() ?: return null

        val items = queryFactory
            .selectFrom(item)
            .where(item.orderId.eq(orderId))
            .orderBy(item.createdAt.asc())
            .fetch()

        return OrderAggregation(order = orderEntity, items = items)
    }
}
```

### The "3-Word Rule"

If a Spring Data JPA method name exceeds 3 words (by `And`/`Or` count), use QueryDSL:

```kotlin
// ✅ JPA OK: 1-2 conditions
fun findByStatus(status: Status): List<Entity>
fun findByNameAndType(name: String, type: Type): Entity?

// ❌ Too complex for JPA: 3+ conditions -> Use QueryDSL
// findByStatusAndTypeAndOwnerAndCreatedAtAfter(...)
```

### Anti-Pattern Examples

```kotlin
// ❌ WRONG: Entity with relationships
@Entity
class UserEntity(
    @Id val id: Long,

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val orders: List<OrderEntity> = emptyList(),  // ❌ FORBIDDEN
)

// ❌ WRONG: Bidirectional relationship
@Entity
class OrderItemEntity(
    @Id val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    val order: OrderEntity,  // ❌ FORBIDDEN
)

// ❌ WRONG: JPA method with too many conditions
interface UserRepositoryJpaSpringData : JpaRepository<UserEntity, Long> {
    fun findByNameContainingAndStatusAndRoleAndCreatedAtAfter(
        name: String, status: Status, role: Role, date: LocalDateTime
    ): List<UserEntity>  // ❌ Too complex - use QueryDSL
}
```

---

## Hexagonal Architecture Patterns

### Module Structure

```
project-basecamp-server/
├── module-core-common/          # Shared utilities (NO domain dependencies)
│   ├── src/main/kotlin/common/
│   │   ├── exception/           # Base exceptions (BusinessException, etc.)
│   │   ├── constant/            # Shared constants
│   │   └── util/               # Utility classes
├── module-core-domain/          # Domain models & interfaces
│   ├── src/main/kotlin/domain/
│   │   ├── model/               # JPA entities (domain-specific)
│   │   ├── repository/          # Repository interfaces (ports)
│   │   └── service/            # Domain services (concrete)
├── module-core-infra/           # Infrastructure implementations
│   ├── src/main/kotlin/infra/
│   │   ├── repository/          # Repository implementations (adapters)
│   │   ├── external/           # External service clients (Airflow, BigQuery)
│   │   └── exception/          # Infrastructure-specific exceptions (optional)
└── module-server-api/           # REST API layer
    ├── src/main/kotlin/api/
    │   ├── controller/          # REST controllers
    │   ├── dto/                # API request/response DTOs
    │   └── mapper/             # DTO ↔ Entity mappers
```

### Module Placement Decision Guide (CRITICAL)

**Before creating ANY new class, ask: "What does this class depend on?"**

| Module | Depends On | Contains | Examples |
|--------|------------|----------|----------|
| **module-core-common** | Nothing (base module) | Base exceptions, utilities, shared constants | `BusinessException`, `DateUtils`, `CommonConstants` |
| **module-core-domain** | common only | Entities, repository interfaces, domain services, domain exceptions | `MetricEntity`, `MetricRepositoryJpa`, `MetricService`, `MetricNotFoundException` |
| **module-core-infra** | common + domain | Repository impls, external clients, infra exceptions | `MetricRepositoryJpaImpl`, `AirflowClient`, `AirflowConnectionException` |
| **module-server-api** | common + domain + infra | Controllers, API DTOs, mappers | `MetricController`, `MetricRequest`, `MetricMapper` |

### Exception Placement Guidelines

```kotlin
// CORRECT: module-core-common/exception/
// - Base exceptions with NO domain dependencies
abstract class BusinessException(message: String, errorCode: String, cause: Throwable?)
class ResourceNotFoundException(resourceType: String, identifier: Any)
class ExternalSystemException(system: String, operation: String)

// CORRECT: module-core-domain (domain-specific exceptions)
// - Exceptions tied to specific domain entities or business rules
class MetricNotFoundException(name: String)         // Uses MetricEntity concept
class DatasetAlreadyExistsException(datasetName: String)  // Uses Dataset domain

// CORRECT: module-core-infra/external/ or infra/exception/ (infrastructure exceptions)
// - Exceptions for external system integrations
class AirflowConnectionException(operation: String) // Airflow-specific
class BigQueryExecutionException(query: String)     // BigQuery-specific
class WorkflowStorageException(operation: String)   // Storage-specific

// WRONG: module-core-domain/external/ ❌
// - External system exceptions should NOT be in domain layer
// - They don't represent domain concepts
```

**Quick Rule:** If an exception mentions an external system (Airflow, BigQuery, Trino, S3, etc.), it belongs in `module-core-common/exception/` (generic) or `module-core-infra/` (specific implementation).

### Dependency Rules

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

### Bean Configuration Pattern

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

## Service Implementation Patterns

### Standard Service Pattern

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

### Command/Query Pattern

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

## Repository Layer Patterns

### Domain Repository Interfaces

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

### Infrastructure Repository Implementations

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

---

## Controller Patterns

### Standard REST Controller

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

---

## DTO and Mapper Patterns

### Request DTOs

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
```

### Response DTOs

```kotlin
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
```

### Mapper Pattern

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

## Entity Patterns

### Entity Pattern

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

## Testing Patterns

### Unit Testing with MockK

```kotlin
@ExtendWith(MockKExtension::class)
class MetricServiceTest {
    @MockK
    private lateinit var metricRepositoryJpa: MetricRepositoryJpa

    @MockK
    private lateinit var metricRepositoryDsl: MetricRepositoryDsl

    @MockK
    private lateinit var metricValidator: MetricValidator

    @InjectMockKs
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

        every { metricRepositoryJpa.findByName(request.name) } returns null
        every { metricRepositoryJpa.save(any()) } returns entity

        // When
        val result = metricService.createMetric(request)

        // Then
        assertThat(result.name).isEqualTo(request.name)
        assertThat(result.owner).isEqualTo(request.owner)
        verify { metricValidator.validate(request) }
        verify { metricRepositoryJpa.save(any()) }
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

        every { metricRepositoryJpa.findByName(request.name) } returns existingEntity

        // When & Then
        assertThrows<MetricAlreadyExistsException> {
            metricService.createMetric(request)
        }

        verify(exactly = 0) { metricRepositoryJpa.save(any()) }
    }
}
```

### Integration Testing

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

## Related Documentation

- **Architecture Overview**: [architecture.md](../../../docs/architecture.md) - System design and policies (platform-level)
- **Error Handling**: [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Exception handling and error codes
- **Development Patterns**: [PATTERNS.md](./PATTERNS.md) - Quick reference templates
- **Testing Guide**: [TESTING.md](./TESTING.md) - Comprehensive testing patterns

---

*This implementation guide provides production-ready Spring Boot + Kotlin patterns following hexagonal architecture principles for the Basecamp Server API.*
