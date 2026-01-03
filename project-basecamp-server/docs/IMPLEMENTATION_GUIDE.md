# Step-by-Step Implementation Guide

> **Purpose:** Detailed guidance for implementing new features in project-basecamp-server
> **Audience:** Developers implementing features, AI agents building new functionality
> **Use When:** "I'm implementing a new feature, guide me through it"

**See Also:**
- [PATTERNS.md](./PATTERNS.md) - Quick reference patterns, decision tables, naming conventions
- [ENTITY_RELATION.md](./ENTITY_RELATION.md) - Entity relationships diagram and QueryDSL join patterns
- [TESTING.md](./TESTING.md) - Comprehensive testing strategies and examples
- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error codes, exception hierarchy, response format

---

## Table of Contents

1. [Custom Rules](#custom-rules-critical)
2. [Hexagonal Architecture Patterns](#hexagonal-architecture-patterns)
3. [Module Placement Guidelines](#module-placement-guidelines)
4. [Entity Relationship Rules](#entity-relationship-rules-critical)
5. [Projection Pattern](#projection-pattern)
6. [Service Implementation Patterns](#service-implementation-patterns)
7. [Repository Layer Patterns](#repository-layer-patterns)
8. [Controller Patterns](#controller-patterns)
9. [DTO and Mapper Patterns](#dto-and-mapper-patterns)
10. [Entity Patterns](#entity-patterns)
11. [Implementation Order](#implementation-order)

---

## Custom Rules (Critical)

Before implementing any feature, understand these project-specific rules:

1. **Database Strategy**: Currently using H2 for development, planning MySQL migration. Design entities with MySQL compatibility in mind.

2. **Data Access Technology**:
   - **Queries**: Use QueryDSL for all data retrieval in `module-core-infra`
   - **Mutations**: JPA is acceptable for create/update/delete operations

3. **No JPA Relationships**: Entities must NOT use JPA relationship annotations. See [Entity Relationship Rules](#entity-relationship-rules-critical).

4. **Test Priority**:
   - Focus on Service tests (unit tests with MockK)
   - Also write Controller tests
   - Avoid heavy SpringBootApplication-based tests

5. **External Integrations**: Use Mock implementations for external systems (Airflow, BigQuery, etc.)

---

## Hexagonal Architecture Patterns

### Module Structure Overview

```
project-basecamp-server/
├── module-core-common/          # Shared utilities (NO domain dependencies)
│   ├── src/main/kotlin/common/
│   │   ├── exception/           # Base exceptions (BusinessException, etc.)
│   │   ├── constant/            # Shared constants
│   │   └── util/                # Utility classes
├── module-core-domain/          # Domain models & interfaces
│   ├── src/main/kotlin/domain/
│   │   ├── model/               # JPA entities (domain-specific)
│   │   ├── repository/          # Repository interfaces (ports)
│   │   └── service/             # Domain services (concrete)
├── module-core-infra/           # Infrastructure implementations
│   ├── src/main/kotlin/infra/
│   │   ├── repository/          # Repository implementations (adapters)
│   │   ├── external/            # External service clients (Airflow, BigQuery)
│   │   └── exception/           # Infrastructure-specific exceptions (optional)
└── module-server-api/           # REST API layer
    ├── src/main/kotlin/api/
    │   ├── controller/          # REST controllers
    │   ├── dto/                 # API request/response DTOs
    │   └── mapper/              # DTO <-> Entity mappers
```

### Dependency Flow

```
module-server-api
       │
       ▼
module-core-infra ───────► module-core-domain
       │                          │
       └──────────────────────────┼─────► module-core-common
                                  │
                                  ▼
                          module-core-common
```

### Key Principles

1. **Domain Independence**: `module-core-domain` has zero infrastructure imports
2. **No Service Interfaces**: Services are concrete classes with `@Service` annotation
3. **Direct Repository Injection**: Services inject domain repository interfaces directly
4. **DTOs at Boundaries**: API layer uses DTOs, never exposes entities

---

## Module Placement Guidelines

### Before Creating Any New Class

Ask: **"What does this class depend on?"**

| Module | Depends On | Contains | Examples |
|--------|------------|----------|----------|
| **module-core-common** | Nothing | Base exceptions, utilities, shared constants | `BusinessException`, `DateUtils` |
| **module-core-domain** | common only | Entities, repository interfaces, domain services | `MetricEntity`, `MetricRepositoryJpa`, `MetricService` |
| **module-core-infra** | common + domain | Repository impls, external clients | `MetricRepositoryJpaImpl`, `AirflowClient` |
| **module-server-api** | all modules | Controllers, API DTOs, mappers | `MetricController`, `MetricRequest` |

### Exception Placement Guidelines

This is a common source of errors. Follow these rules strictly:

```kotlin
// CORRECT: module-core-common/exception/
// - Base exceptions with NO domain dependencies
abstract class BusinessException(
    message: String,
    val errorCode: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

class ResourceNotFoundException(resourceType: String, identifier: Any) :
    BusinessException("$resourceType not found: $identifier", "RESOURCE_NOT_FOUND")

class ExternalSystemException(system: String, operation: String) :
    BusinessException("External system error: $system - $operation", "EXTERNAL_ERROR")

// CORRECT: module-core-domain (domain-specific exceptions)
// - Exceptions tied to specific domain entities or business rules
class MetricNotFoundException(name: String) :
    BusinessException("Metric not found: $name", "METRIC_NOT_FOUND")

class DatasetAlreadyExistsException(datasetName: String) :
    BusinessException("Dataset already exists: $datasetName", "DATASET_EXISTS")

// CORRECT: module-core-infra/external/ (infrastructure exceptions)
// - Exceptions for external system integrations
class AirflowConnectionException(operation: String) :
    ExternalSystemException("Airflow", operation)

class BigQueryExecutionException(query: String) :
    ExternalSystemException("BigQuery", "Query execution failed")
```

**Quick Rule:** If an exception mentions an external system (Airflow, BigQuery, Trino, S3), it belongs in `module-core-common/exception/` (generic) or `module-core-infra/` (specific).

### Bean Configuration Pattern

```kotlin
@Configuration
@ComponentScan(basePackages = [
    "com.basecamp.domain.service",
    "com.basecamp.infra.repository",
    "com.basecamp.infra.external"
])
class InfrastructureConfig {

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

## Entity Relationship Rules (CRITICAL)

> **Core Rule**: JPA Relation 사용 금지, ID 참조 + QueryDSL Join 사용
>
> **See [ENTITY_RELATION.md](./ENTITY_RELATION.md)** for complete entity relationship diagram, FK reference table, and QueryDSL join patterns.

This is a fundamental design decision for maintainability and performance.

---

### 1. Relation 규칙

| 항목 | 규칙 | 대안 |
|------|------|------|
| `@ManyToOne` | **FORBIDDEN** | ID 참조 |
| `@OneToMany` | **FORBIDDEN** | QueryDSL Join |
| `@OneToOne` | **FORBIDDEN** | ID 참조 + QueryDSL Join |
| `@ManyToMany` | **FORBIDDEN** | 중간 Entity |
| `FetchType.EAGER` | **FORBIDDEN** | QueryDSL 명시적 Join |

---

### 2. Entity 예시

#### FORBIDDEN Pattern

```kotlin
@Entity
class Dataset(
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    val owner: User,  // FORBIDDEN: Entity 참조 금지

    @OneToMany(mappedBy = "dataset")
    val columns: List<DatasetColumn> = emptyList()  // FORBIDDEN
)
```

#### Correct Pattern

```kotlin
@Entity
@Table(name = "datasets")
class DatasetEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    var status: DatasetStatus = DatasetStatus.DRAFT,

    // CORRECT: 외부 Entity는 ID로만 참조
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,

    @Column(name = "project_id", nullable = false)
    val projectId: Long,

    val createdAt: Instant = Instant.now()
)
```

---

### 3. 1:1 관계 처리

#### Correct: ID 참조 + QueryDSL Join

```kotlin
// Entity
@Entity
class DatasetEntity(
    @Column(name = "metadata_id")
    val metadataId: Long?
)

// QueryDSL
.leftJoin(metadata).on(metadata.id.eq(dataset.metadataId))
```

---

### 4. N:M 관계 처리

#### Correct: 중간 Entity 생성

```kotlin
// FORBIDDEN
@ManyToMany
val tags: List<Tag>

// CORRECT: 중간 Entity
@Entity
@Table(name = "dataset_tags")
class DatasetTagEntity(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "dataset_id", nullable = false)
    val datasetId: Long,

    @Column(name = "tag_id", nullable = false)
    val tagId: Long
)
```

---

### 5. 연관 데이터 조회

#### QueryDSL Join 사용

```kotlin
// Projection
data class DatasetDetail(
    val id: Long,
    val name: String,
    val owner: OwnerInfo,
    val project: ProjectInfo
)

// QueryDSL
fun findDetail(id: Long): DatasetDetail? {
    return queryFactory
        .select(Projections.constructor(
            DatasetDetail::class.java,
            dataset.id,
            dataset.name,
            Projections.constructor(OwnerInfo::class.java, user.id, user.name),
            Projections.constructor(ProjectInfo::class.java, project.id, project.name)
        ))
        .from(dataset)
        .leftJoin(user).on(user.id.eq(dataset.ownerId))
        .leftJoin(project).on(project.id.eq(dataset.projectId))
        .where(dataset.id.eq(id))
        .fetchOne()
}
```

---

### 6. Why No JPA Relationships?

| Problem | Issue with JPA Relations | Our Solution |
|---------|--------------------------|--------------|
| **N+1 Queries** | Lazy loading causes unpredictable query counts | QueryDSL 명시적 Join |
| **LazyInitializationException** | Session closed before access | ID 참조로 원천 차단 |
| **Circular Reference** | Bidirectional mappings cause serialization issues | 단방향 ID 참조 |
| **Unpredictable Queries** | Eager/lazy loading hides actual queries | 모든 Join 명시적 제어 |
| **Testing Complexity** | Mock setup for relationships is complex | Test entities in isolation |
| **Cascade Issues** | Cascade, orphan removal cause unexpected side effects | Handle persistence explicitly |

---

### 7. QueryDSL Aggregation Pattern

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

// Aggregation Result (Domain Model, NOT Entity)
data class OrderAggregation(
    val order: OrderEntity,
    val items: List<OrderItemEntity>,
)
```

---

### 8. Anti-Pattern Examples

```kotlin
// WRONG: Entity with relationships
@Entity
class UserEntity(
    @Id val id: Long,

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    val orders: List<OrderEntity> = emptyList(),  // FORBIDDEN
)

// WRONG: Bidirectional relationship
@Entity
class OrderItemEntity(
    @Id val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    val order: OrderEntity,  // FORBIDDEN
)

// WRONG: JPA method with too many conditions
interface UserRepositoryJpaSpringData : JpaRepository<UserEntity, Long> {
    fun findByNameContainingAndStatusAndRoleAndCreatedAtAfter(
        name: String, status: Status, role: Role, date: LocalDateTime
    ): List<UserEntity>  // Too complex - use QueryDSL
}
```

---

## Projection Pattern

When Entity or `Page<Entity>` cannot express the response structure, use Projections.

### Location

```
module-core-domain/
├── model/                 # JPA Entities
├── projection/            # Projection classes
│   ├── {Entity}List.kt    # List with pagination metadata
│   └── {Entity}Detail.kt  # Detail with optional relationships
└── repository/            # Repository interfaces
```

### When to Use Projections

| Scenario | Use |
|----------|-----|
| Simple CRUD, single entity | Entity |
| List with only entity fields | `Page<Entity>` |
| List with joined fields (owner name, counts) | `Page<{Entity}List>` |
| Detail with optional child entities | `{Entity}Detail` |
| Aggregations (count, sum, avg) | Projection |

### Projection Examples

```kotlin
// module-core-domain/projection/QualitySpecList.kt
data class QualitySpecList(
    val id: Long,
    val name: String,
    val resourceName: String,
    val resourceType: ResourceType,
    val ownerName: String,        // Joined from owner entity
    val testCount: Int,           // Count aggregation
    val lastRunStatus: TestStatus?,  // From latest run
)

// module-core-domain/projection/QualitySpecDetail.kt
data class QualitySpecDetail(
    val id: Long,
    val name: String,
    val resourceName: String,
    val resourceType: ResourceType,
    val description: String?,
    val tests: List<TestInfo>,    // Child entities
    val owner: OwnerInfo?,        // Joined owner data
    val lastRun: RunInfo?,        // Optional related entity
)

data class TestInfo(
    val id: Long,
    val name: String,
    val type: String,
    val enabled: Boolean,
)

data class OwnerInfo(
    val id: Long,
    val name: String,
    val email: String,
)

data class RunInfo(
    val runId: String,
    val status: RunStatus,
    val completedAt: Instant?,
)
```

### Repository for Projections

```kotlin
// module-core-domain/repository/QualitySpecRepositoryDsl.kt
interface QualitySpecRepositoryDsl {
    fun findListByConditions(query: QualitySpecListQuery): Page<QualitySpecList>
    fun findDetailById(id: Long): QualitySpecDetail?
}

// module-core-infra/repository/QualitySpecRepositoryDslImpl.kt
@Repository("qualitySpecRepositoryDsl")
class QualitySpecRepositoryDslImpl(
    private val entityManager: EntityManager,
) : QualitySpecRepositoryDsl {

    private val queryFactory = JPAQueryFactory(entityManager)
    private val spec = QQualitySpecEntity.qualitySpecEntity
    private val test = QQualityTestEntity.qualityTestEntity

    override fun findDetailById(id: Long): QualitySpecDetail? {
        // 1. Fetch main entity
        val specEntity = queryFactory
            .selectFrom(spec)
            .where(spec.id.eq(id))
            .fetchOne() ?: return null

        // 2. Fetch related tests via separate query (NO lazy loading)
        val tests = queryFactory
            .selectFrom(test)
            .where(test.specId.eq(id))
            .fetch()
            .map { TestInfo(it.id, it.name, it.type, it.enabled) }

        // 3. Build projection
        return QualitySpecDetail(
            id = specEntity.id,
            name = specEntity.name,
            resourceName = specEntity.resourceName,
            resourceType = specEntity.resourceType,
            description = specEntity.description,
            tests = tests,
            owner = null,  // Fetch if needed
            lastRun = null,  // Fetch if needed
        )
    }
}
```

### Service Pattern with Projections

```kotlin
@Service
@Transactional(readOnly = true)
class QualitySpecService(
    private val repositoryJpa: QualitySpecRepositoryJpa,
    private val repositoryDsl: QualitySpecRepositoryDsl,
) {
    fun listSpecs(query: QualitySpecListQuery): Page<QualitySpecList> {
        // Use projection for list with aggregations
        return repositoryDsl.findListByConditions(query)
    }

    fun getSpecDetail(id: Long): QualitySpecDetail? {
        // Use projection for detail with relationships
        return repositoryDsl.findDetailById(id)
    }

    @Transactional
    fun createSpec(command: CreateQualitySpecCommand): QualitySpecEntity {
        // Use entity for mutations
        val entity = QualitySpecEntity(...)
        return repositoryJpa.save(entity)
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
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricService::class.java)
    }

    @Transactional
    fun createMetric(command: CreateMetricCommand): MetricDto {
        logger.info("Creating metric: {}", command.name)

        // 1. Validation
        metricValidator.validate(command)

        // 2. Check for duplicates
        metricRepositoryJpa.findByName(command.name)?.let {
            throw MetricAlreadyExistsException(command.name)
        }

        // 3. Create entity
        val entity = MetricEntity(
            id = UUID.randomUUID().toString(),
            name = command.name,
            owner = command.owner,
            sql = command.sql,
            tags = command.tags.toSet(),
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        // 4. Save and return
        val saved = metricRepositoryJpa.save(entity)
        logger.info("Metric created successfully: {}", saved.name)

        return MetricMapper.toDto(saved)
    }

    fun listMetrics(query: ListMetricsQuery): List<MetricDto> {
        logger.debug("Listing metrics with filters: {}", query)

        val entities = metricRepositoryDsl.findByFilters(
            tag = query.tag,
            owner = query.owner,
            search = query.search,
            limit = query.limit,
            offset = query.offset
        )

        return entities.map { MetricMapper.toDto(it) }
    }

    fun getMetric(name: String): MetricDto? {
        return metricRepositoryJpa.findByName(name)
            ?.let { MetricMapper.toDto(it) }
    }

    @Transactional
    fun deleteMetric(name: String) {
        val entity = metricRepositoryJpa.findByName(name)
            ?: throw MetricNotFoundException(name)

        metricRepositoryJpa.delete(entity)
        logger.info("Metric deleted: {}", name)
    }
}
```

### Command/Query Pattern

Use dedicated objects for method parameters:

```kotlin
// Commands (for mutations)
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

// Queries (for reads)
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
```

---

## Repository Layer Patterns

### Domain Repository Interfaces (Ports)

```kotlin
// module-core-domain/repository/MetricRepositoryJpa.kt
interface MetricRepositoryJpa {
    fun save(metric: MetricEntity): MetricEntity
    fun findById(id: String): MetricEntity?
    fun findByName(name: String): MetricEntity?
    fun findAll(): List<MetricEntity>
    fun delete(metric: MetricEntity)
    fun existsByName(name: String): Boolean
}

// module-core-domain/repository/MetricRepositoryDsl.kt
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

### Infrastructure Implementation - Simplified Pattern (Recommended)

This pattern combines domain interface and JpaRepository into one interface:

```kotlin
// module-core-infra/repository/MetricRepositoryJpaImpl.kt
@Repository("metricRepositoryJpa")
interface MetricRepositoryJpaImpl :
    MetricRepositoryJpa,
    JpaRepository<MetricEntity, String> {

    // Spring Data JPA auto-implements these
    override fun findByName(name: String): MetricEntity?
    override fun existsByName(name: String): Boolean
}
```

Benefits:
- Eliminates the need for a separate `*SpringData` interface
- Reduces boilerplate code
- Leverages Spring Data JPA's auto-implementation

### Infrastructure Implementation - QueryDSL

```kotlin
// module-core-infra/repository/MetricRepositoryDslImpl.kt
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

        // Apply filters dynamically
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

    override fun countByOwner(owner: String): Long {
        return queryFactory
            .select(metric.count())
            .from(metric)
            .where(metric.owner.eq(owner))
            .fetchOne() ?: 0L
    }
}
```

### Critical Repository Pattern Rules

**DO NOT create `*RepositoryJpaSpringData` interfaces!**

```kotlin
// WRONG - Do NOT create this
interface ItemRepositoryJpaSpringData : JpaRepository<ItemEntity, Long>

// WRONG - Do NOT use composition pattern
@Repository
class ItemRepositoryJpaImpl(
    private val springDataRepository: ItemRepositoryJpaSpringData
) : ItemRepositoryJpa

// CORRECT - Single interface extending both
@Repository("itemRepositoryJpa")
interface ItemRepositoryJpaImpl :
    ItemRepositoryJpa,           // Domain interface
    JpaRepository<ItemEntity, Long>  // Spring Data JPA
```

---

## Controller Patterns

### Standard REST Controller

```kotlin
@RestController
@RequestMapping("/api/v1/metrics")
@CrossOrigin
@Validated
@Tag(name = "Metric", description = "Metric Management API")
class MetricController(
    private val metricService: MetricService,
    private val metricMapper: MetricMapper,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(MetricController::class.java)
    }

    @GetMapping
    @Operation(summary = "List metrics with optional filters")
    fun listMetrics(
        @RequestParam(required = false) tag: String?,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(defaultValue = "50") @Min(1) @Max(500) limit: Int,
        @RequestParam(defaultValue = "0") @Min(0) offset: Int,
    ): ResponseEntity<ApiResponse<List<MetricDto>>> {
        logger.info("GET /api/v1/metrics - tag={}, owner={}, search={}", tag, owner, search)

        val query = ListMetricsQuery(
            tag = tag,
            owner = owner,
            search = search,
            limit = limit,
            offset = offset
        )
        val metrics = metricService.listMetrics(query)

        return ResponseEntity.ok(ApiResponse.success(metrics))
    }

    @GetMapping("/{name}")
    @Operation(summary = "Get metric by name")
    fun getMetric(
        @PathVariable @NotBlank name: String,
    ): ResponseEntity<ApiResponse<MetricDto>> {
        logger.info("GET /api/v1/metrics/{}", name)

        val metric = metricService.getMetric(name)
            ?: throw MetricNotFoundException(name)

        return ResponseEntity.ok(ApiResponse.success(metric))
    }

    @PostMapping
    @Operation(summary = "Create new metric")
    fun createMetric(
        @RequestBody @Valid request: CreateMetricRequest,
    ): ResponseEntity<ApiResponse<CreateMetricResponse>> {
        logger.info("POST /api/v1/metrics - name={}", request.name)

        val command = metricMapper.toCommand(request)
        val metric = metricService.createMetric(command)

        val response = CreateMetricResponse(
            message = "Metric '${metric.name}' registered successfully",
            name = metric.name
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(response))
    }

    @DeleteMapping("/{name}")
    @Operation(summary = "Delete metric by name")
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
```

### Response DTOs

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

    fun toCommand(request: CreateMetricRequest): CreateMetricCommand {
        return CreateMetricCommand(
            name = request.name,
            owner = request.owner,
            sql = request.sql,
            tags = request.tags,
            description = request.description
        )
    }

    private fun extractSourceTable(sql: String): String? {
        val regex = Regex("FROM\\s+([\\w.]+)", RegexOption.IGNORE_CASE)
        return regex.find(sql)?.groupValues?.get(1)
    }

    private fun extractDependencies(sql: String): List<String> {
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

### Standard Entity Template

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

### Entity Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| JPA Entities | `*Entity` | `UserEntity`, `PipelineEntity` |
| Enums | No suffix | `UserRole`, `PipelineStatus` |
| API DTOs | `*Dto` | `UserDto`, `PipelineDto` |
| Request DTOs | `*Request` | `CreateUserRequest` |
| Response DTOs | `*Response` | `CreateUserResponse` |

---

## Implementation Order

When implementing a new feature, follow this order:

### Step 1: Domain Entity (module-core-domain/model/)

```kotlin
@Entity
@Table(name = "pipelines")
class PipelineEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "name", nullable = false, unique = true)
    val name: String,

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    val status: PipelineStatus = PipelineStatus.INACTIVE,
)
```

### Step 2: Domain Repository Interfaces (module-core-domain/repository/)

```kotlin
interface PipelineRepositoryJpa {
    fun save(pipeline: PipelineEntity): PipelineEntity
    fun findById(id: Long): PipelineEntity?
    fun findByName(name: String): PipelineEntity?
}

interface PipelineRepositoryDsl {
    fun findByConditions(query: PipelineQuery): Page<PipelineEntity>
}
```

### Step 3: Infrastructure Implementations (module-core-infra/repository/)

```kotlin
@Repository("pipelineRepositoryJpa")
interface PipelineRepositoryJpaImpl :
    PipelineRepositoryJpa,
    JpaRepository<PipelineEntity, Long> {

    override fun findByName(name: String): PipelineEntity?
}

@Repository("pipelineRepositoryDsl")
class PipelineRepositoryDslImpl(
    private val entityManager: EntityManager,
) : PipelineRepositoryDsl {
    // QueryDSL implementation
}
```

### Step 4: Domain Service (module-core-domain/service/)

```kotlin
@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,
) {
    @Transactional
    fun createPipeline(command: CreatePipelineCommand): PipelineDto { ... }

    fun getPipeline(id: Long): PipelineDto? { ... }
}
```

### Step 5: API Controller (module-server-api/controller/)

```kotlin
@RestController
@RequestMapping("/api/v1/pipelines")
class PipelineController(
    private val pipelineService: PipelineService,
    private val pipelineMapper: PipelineMapper,
) {
    @PostMapping
    fun createPipeline(@Valid @RequestBody request: CreatePipelineRequest): ResponseEntity<...>

    @GetMapping("/{id}")
    fun getPipeline(@PathVariable id: Long): ResponseEntity<...>
}
```

---

## Related Documentation

- **Quick Reference**: [PATTERNS.md](./PATTERNS.md) - Fast lookup patterns and templates
- **Testing Guide**: [TESTING.md](./TESTING.md) - Comprehensive testing patterns by layer
- **Error Handling**: [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Exception handling and error codes
- **Architecture Overview**: [architecture.md](../../../docs/architecture.md) - System design (platform-level)

---

*Last Updated: 2026-01-03*
