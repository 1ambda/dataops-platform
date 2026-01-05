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
2. [Entity Organization Rules](#entity-organization-rules-critical)
3. [Hexagonal Architecture Patterns](#hexagonal-architecture-patterns)
4. [Module Placement Guidelines](#module-placement-guidelines)
5. [External System Integration Patterns](#external-system-integration-patterns)
6. [Entity Relationship Rules](#entity-relationship-rules-critical)
7. [Projection Pattern](#projection-pattern)
8. [Command Pattern](#command-pattern)
9. [Service Implementation Patterns](#service-implementation-patterns)
10. [Repository Layer Patterns](#repository-layer-patterns)
11. [Controller Patterns](#controller-patterns)
12. [DTO and Mapper Patterns](#dto-and-mapper-patterns)
13. [Entity Patterns](#entity-patterns)
14. [Implementation Order](#implementation-order)

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

## Entity Organization Rules (Critical)

> **⚠️ MANDATORY for AI agents:** ALL Entity classes must follow this package organization

### Directory Structure

**BEFORE (Old Structure - REMOVED):**
```
❌ REMOVED: domain/model/ (split into command/, projection/, external/, internal/)
```

**AFTER (New Structure - MANDATORY):**
```
module-core-domain/src/main/kotlin/com/github/lambda/domain/
└── entity/                           # ✅ ALL Entity classes here
    ├── BaseEntity.kt                # Base entities at root
    ├── BaseAuditableEntity.kt
    ├── adhoc/                       # Domain-specific entities
    │   ├── AdHocExecutionEntity.kt
    │   └── UserExecutionQuotaEntity.kt
    ├── audit/
    ├── catalog/
    ├── dataset/
    ├── github/
    ├── lineage/
    ├── metric/
    ├── pipeline/
    ├── quality/
    ├── query/
    ├── resource/
    ├── transpile/
    ├── user/
    └── workflow/
```

### Implementation Rules

1. **Package Declaration:**
   ```kotlin
   // ✅ CORRECT: Base entities
   package com.dataops.basecamp.domain.entity

   // ✅ CORRECT: Domain-specific entities
   package com.dataops.basecamp.domain.entity.quality
   package com.dataops.basecamp.domain.entity.user
   ```

2. **Entity Placement:**
   ```kotlin
   // ✅ CORRECT: New entity creation
   // File: module-core-domain/entity/metric/MetricEntity.kt
   package com.dataops.basecamp.domain.entity.metric

   @Entity
   @Table(name = "metrics")
   class MetricEntity(...)
   ```

3. **Import Statements:**
   ```kotlin
   // ✅ CORRECT: Import entities from entity package
   import com.dataops.basecamp.domain.entity.BaseEntity
   import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity

   // ❌ WRONG: Old model package imports (removed)
   // import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity
   ```

### AI Agent Checklist

When creating new entities:
- [ ] Create in `domain/entity/{domain}/` (NOT `domain/model/`)
- [ ] Use package `com.dataops.basecamp.domain.entity.{domain}`
- [ ] Import other entities from `com.dataops.basecamp.domain.entity.*`
- [ ] Split models into command/, projection/, external/, internal/ based on usage

### Verification

```bash
# Check entities are in correct location (should show 29+ entities)
find module-core-domain/src/main/kotlin/com/github/lambda/domain/entity -name "*Entity.kt" | wc -l

# Verify old locations are empty (should return 0)
find module-core-domain/src/main/kotlin/com/github/lambda/domain/model -name "*Entity.kt" | wc -l
```

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
│   │   ├── command/              # Incoming requests/filters
│   │   ├── repository/          # Repository interfaces (ports)
│   │   └── service/             # Domain services (concrete)
├── module-core-infra/           # Infrastructure implementations
│   ├── src/main/kotlin/infra/
│   │   ├── repository/          # Repository implementations (adapters)
│   │   ├── external/            # External service clients (Airflow, BigQuery)
│   │   └── exception/           # Infrastructure-specific exceptions (optional)
└── module-server-api/           # REST API layer
    ├── src/main/kotlin/
    │   ├── controller/          # REST controllers
    │   ├── dto/                 # API request/response DTOs (UNIFIED LOCATION)
    │   │   ├── catalog/         # Domain-specific DTOs
    │   │   ├── dataset/         # Domain-specific DTOs
    │   │   ├── transpile/       # Domain-specific DTOs
    │   │   └── workflow/        # Domain-specific DTOs
    │   └── mapper/              # DTO <-> Entity mappers
```

> **📖 Detailed Package Organization:** See [PATTERNS.md - Domain Package Organization Rules](./PATTERNS.md#domain-package-organization-rules) for comprehensive package placement rules, including where to place commands, models, external interfaces, and utilities within the domain layer.

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
| **module-core-common** | Nothing | Base exceptions, **ALL ENUMS**, utilities, shared constants | `BusinessException`, `QueryUtility`, **ALL** enums |
| **module-core-domain** | common only | Entities, repository interfaces, domain services | `MetricEntity`, `MetricRepositoryJpa`, `MetricService` |
| **module-core-infra** | common + domain | Repository impls, external clients | `MetricRepositoryJpaImpl`, `AirflowClient` |
| **module-server-api** | all modules | Controllers, API DTOs, mappers | `MetricController`, `MetricRequest` |

### 🔴 CRITICAL: Enum and Utility Placement Rules

> **⚠️ MANDATORY:** These rules are CRITICAL for architecture compliance. See [Domain Package Organization Rules](./PATTERNS.md#domain-package-organization-rules) for complete details.

**Rule 1: ALL Enums → module-core-common**
```kotlin
// ✅ CORRECT: ALL enums go here
module-core-common/src/main/kotlin/com/github/lambda/common/enums/
├── QueryEnums.kt        // QueryStatus, QueryEngine, QueryScope
├── WorkflowEnums.kt     // WorkflowStatus, WorkflowRunStatus, etc.
├── QualityEnums.kt      // ResourceType, TestType, Severity, etc.
└── UserRole.kt          // Single enum files

// ❌ FORBIDDEN: No enums in domain
module-core-domain/**/*Enum*.kt        // WRONG!
```

**Rule 2: Dependency-Free Utilities → module-core-common**
```kotlin
// ✅ CORRECT: QueryUtility (renamed from QueryIdGenerator)
module-core-common/src/main/kotlin/com/github/lambda/common/util/QueryUtility.kt

// ❌ FORBIDDEN: No utilities in domain
module-core-domain/src/main/kotlin/com/github/lambda/domain/util/   // WRONG!
```

**Import Pattern:**
```kotlin
// Entity imports
import com.dataops.basecamp.common.enums.QueryStatus
import com.dataops.basecamp.common.enums.WorkflowStatus
import com.dataops.basecamp.common.util.QueryUtility

// Services inject utilities from common
class SomeService(
    private val queryUtility: QueryUtility,  // ✅ From common module
)
```

**Anti-Pattern Detection:**
```bash
# Find misplaced enums (should return empty)
find module-core-domain -name "*Enum*.kt" -o -name "*Status.kt" -o -name "*Type.kt"

# Find misplaced utilities (should return empty)
find module-core-domain -path "*/util/*" -name "*.kt"
```

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

## External System Integration Patterns

> **⚠️ CRITICAL FOR AI AGENTS:** These external communication rules are MANDATORY and must be followed exactly. Violations will break the architecture.
> **Purpose**: Step-by-step guide for implementing external system integrations using Port-Adapter pattern with strict package organization

### MANDATORY Package Structure

All External System integrations MUST follow this exact package structure:

```
module-core-domain/src/main/kotlin/com/github/lambda/domain/external/
├── {system}/                        # System-specific package (MANDATORY)
│   ├── {System}Client.kt           # Interface ONLY - NO Response models
│   └── {System}Response.kt         # Response models ONLY - NO Interfaces
```

**Examples:**
```
module-core-domain/src/main/kotlin/com/github/lambda/domain/external/
├── airflow/
│   ├── AirflowClient.kt            # ✅ Interface only
│   └── AirflowResponse.kt          # ✅ Response models only
├── github/
│   ├── GitHubClient.kt             # ✅ Interface only
│   └── GitHubResponse.kt           # ✅ Response models only
└── storage/
    └── WorkflowStorage.kt          # ✅ Interface only (no response models needed)
```

### Step 1: Create Domain Layer (Ports) - UPDATED

#### 1.1 Create System-Specific Package (MANDATORY)

```bash
# Create system-specific package directory
mkdir -p module-core-domain/src/main/kotlin/com/github/lambda/domain/external/{system}
```

#### 1.2 Create Interface File (MANDATORY)

**File**: `module-core-domain/external/{system}/{System}Client.kt`

```kotlin
package com.dataops.basecamp.domain.external.{system}

// ✅ CORRECT - Interface only, no data classes
interface AirflowClient {
    fun getDAGRun(dagId: String, runId: String): AirflowDAGRunStatusResponse
    fun listRecentDagRuns(dagIdPrefix: String, limit: Int): List<AirflowDagRunResponse>
    fun createBackfill(dagId: String, startDate: LocalDateTime): BackfillCreateResponse
}
```

#### 1.3 Create Response Models File (MANDATORY)

**File**: `module-core-domain/external/{system}/{System}Response.kt`

```kotlin
package com.dataops.basecamp.domain.external.{system}

// ✅ CORRECT - Response models only, no interfaces
data class AirflowDAGRunStatusResponse(
    val dagRunId: String,
    val state: AirflowDAGRunState,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val executionDate: LocalDateTime,
    val logsUrl: String?,
)

data class AirflowDagRunResponse(
    val dagId: String,
    val dagRunId: String,
    val state: AirflowDAGRunState,
    val logicalDate: LocalDateTime,
    val startDate: LocalDateTime?,
    val endDate: LocalDateTime?,
)

enum class AirflowDAGRunState {
    QUEUED, RUNNING, SUCCESS, FAILED
}
```

### Step 2: Create Infrastructure Implementation - UPDATED

#### 2.1 Mock Implementation with Correct Imports

**File**: `module-core-infra/external/Mock{System}Client.kt`

```kotlin
package com.dataops.basecamp.infra.external

import com.dataops.basecamp.domain.external.{system}.{System}Client  // ✅ System-specific import
import com.dataops.basecamp.domain.external.{system}.*              // ✅ Import all response models

@Repository("{system}Client")
@ConditionalOnProperty(name = "app.external.{system}.enabled", havingValue = "false")
class Mock{System}Client : {System}Client {

    // ✅ Use Response postfix models in implementation
    override fun getDAGRun(dagId: String, runId: String): AirflowDAGRunStatusResponse {
        return AirflowDAGRunStatusResponse(
            dagRunId = runId,
            state = AirflowDAGRunState.SUCCESS,
            startDate = LocalDateTime.now().minusMinutes(30),
            endDate = LocalDateTime.now(),
            executionDate = LocalDateTime.now().minusMinutes(30),
            logsUrl = "https://airflow.example.com/logs/$dagId/$runId"
        )
    }
}
```

### Step 3: Service Integration - UPDATED

#### 3.1 Service with System-Specific Imports

```kotlin
package com.dataops.basecamp.domain.service

import com.dataops.basecamp.domain.external.airflow.AirflowClient        // ✅ System-specific import
import com.dataops.basecamp.domain.external.airflow.AirflowDAGRunState   // ✅ System-specific import
import com.dataops.basecamp.domain.external.storage.WorkflowStorage      // ✅ System-specific import

@Service
@Transactional(readOnly = true)
class WorkflowService(
    private val airflowClient: AirflowClient,      // ✅ Interface, not implementation
    private val workflowStorage: WorkflowStorage,  // ✅ Interface, not implementation
) {
    fun executeWorkflow(workflowId: String) {
        val dagRun = airflowClient.getDAGRun(workflowId, "run_${System.currentTimeMillis()}")
        // Use dagRun.state, dagRun.startDate, etc.
    }
}
```

### CRITICAL Rules for AI Agents

#### Rule 1: Never Mix Interfaces and Response Models in Same File

```kotlin
// ❌ WRONG - Mixed content in single file
// File: domain/external/airflow/AirflowClient.kt
interface AirflowClient { ... }
data class AirflowDAGRunStatusResponse(...) // Should be in AirflowResponse.kt

// ✅ CORRECT - Separated files
// File: domain/external/airflow/AirflowClient.kt
interface AirflowClient { ... }

// File: domain/external/airflow/AirflowResponse.kt
data class AirflowDAGRunStatusResponse(...)
```

#### Rule 2: Never Mix Multiple Client Interfaces in Same File

```kotlin
// ❌ WRONG - Multiple interfaces in one file
// File: domain/external/AirflowClient.kt
interface AirflowClient { ... }
interface WorkflowStorage { ... }  // Should be in separate system package

// ✅ CORRECT - System-specific separation
// File: domain/external/airflow/AirflowClient.kt
interface AirflowClient { ... }

// File: domain/external/storage/WorkflowStorage.kt
interface WorkflowStorage { ... }
```

#### Rule 3: Always Use Response Postfix for External API Models

```kotlin
// ✅ CORRECT - Response postfix naming
data class AirflowDAGRunStatusResponse(...)
data class GitHubPullRequestResponse(...)
data class BackfillCreateResponse(...)

// ❌ WRONG - Missing Response postfix
data class AirflowDAGRunStatus(...)     // Should be AirflowDAGRunStatusResponse
data class GitHubPullRequest(...)       // Should be GitHubPullRequestResponse
data class BackfillResult(...)          // Should be BackfillCreateResponse
```

### Implementation Checklist - UPDATED

When implementing external system integration:

- [ ] ✅ Create system-specific package under `domain/external/{system}/`
- [ ] ✅ Create `{System}Client.kt` interface file with ONLY interfaces
- [ ] ✅ Create `{System}Response.kt` file with ONLY response models
- [ ] ✅ Use Response postfix for ALL external API response models
- [ ] ✅ Use Request postfix for external API request models (when needed)
- [ ] ✅ Never mix interfaces and response models in same file
- [ ] ✅ Never mix multiple client interfaces in same file
- [ ] ✅ Implement `Mock{System}Client.kt` in module-core-infra with correct imports
- [ ] ✅ Update service imports to use system-specific packages
- [ ] ✅ Add Spring configuration properties for enable/disable
- [ ] ✅ Update tests to use new Response model names
- [ ] ✅ Verify no multiple interfaces coexist in single files

### Anti-Pattern Detection Commands

```bash
# Check for mixed client/response in single file (should return empty)
grep -l "interface.*Client" module-core-domain/src/main/kotlin/com/github/lambda/domain/external/**/*.kt | xargs grep "data class"

# Check for multiple interfaces in single file (should return empty)
grep -c "^interface " module-core-domain/src/main/kotlin/com/github/lambda/domain/external/**/*.kt | awk -F: '$2>1'

# Check for missing Response postfix (should return empty)
grep -r "data class.*" module-core-domain/src/main/kotlin/com/github/lambda/domain/external/ | grep -v "Response\|Request"

# Verify system-specific packages exist
find module-core-domain/src/main/kotlin/com/github/lambda/domain/external -mindepth 1 -maxdepth 1 -type d
```

### Migration Guide for Existing External Systems

If you find existing external system code that violates these rules:

1. **Identify the violation** using anti-pattern detection commands
2. **Create system-specific package** if it doesn't exist
3. **Separate interfaces and response models** into different files
4. **Add Response postfix** to all external API models
5. **Update all imports** in services, tests, and infrastructure
6. **Update mock implementations** to use new model names
7. **Verify with anti-pattern detection** commands
- [ ] Inject interface (not implementation) in services
- [ ] Write unit tests for mock implementation

### Common Patterns

| Pattern | When to Use |
|---------|-------------|
| **Companion Object Factories** | Success/Error patterns (`LineageResult.success()`) |
| **Default Parameters** | Optional configuration (`dialect: String = "bigquery"`) |
| **@ConditionalOnProperty** | Enable/disable external systems via config |

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

> **⚠️ CRITICAL:** This section defines mandatory rules for AI agents. Violations will break the architecture.

Projection classes are the standardized way to handle:
1. **QueryDSL return values** (Repository DSL complex query results)
2. **Service-to-Controller return values** (Service method returns)

### MANDATORY Package Structure

```
module-core-domain/src/main/kotlin/com/github/lambda/domain/
├── entity/                          # JPA Entities
command/                          # Incoming requests, commands, filters
projection/                       # Outgoing read models, results
external/                         # External system integration
internal/                         # Domain-only value objects
│   ├── BaseEntity.kt
│   ├── adhoc/                      # Ad-hoc execution entities
│   ├── workflow/                   # Workflow entities
│   └── ...                         # Other domain entities
├── projection/                      # Projection classes (REQUIRED)
│   ├── execution/                  # Ad-hoc execution projections
│   │   ├── ExecutionPolicyProjection.kt
│   │   ├── RateLimitsProjection.kt
│   │   └── CurrentUsageProjection.kt
│   ├── transpile/                  # SQL transpilation projections
│   │   ├── TranspileRulesProjection.kt
│   │   ├── MetricTranspileProjection.kt
│   │   └── DatasetTranspileProjection.kt
│   ├── workflow/                   # Workflow statistics projections
│   │   └── WorkflowStatisticsProjections.kt
│   └── quality/                    # Quality spec projections
│       └── QualityStatisticsProjections.kt
├── service/                        # Domain services (NO model classes allowed)
└── repository/                     # Repository interfaces
```

### CRITICAL Naming Rules

| Type | Pattern | Package | Example |
|------|---------|---------|---------|
| **QueryDSL Return Values** | `{Entity}{Purpose}Projection` | `domain.projection.{domain}` | `WorkflowRunStatisticsProjection` |
| **Service-to-Controller** | `{Feature}Projection` | `domain.projection.{domain}` | `ExecutionPolicyProjection` |

### When to Use Projections

| Scenario | Use | Example |
|----------|-----|---------|
| Repository DSL aggregation results | `{Purpose}Projection` | `WorkflowRunStatisticsProjection` |
| Service method returns for controllers | `{Feature}Projection` | `ExecutionPolicyProjection` |
| Simple CRUD, single entity | Entity | `WorkflowRunEntity` |
| Complex QueryDSL statistics/aggregations | Projection | `QualitySpecStatisticsProjection` |

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

### Anti-Pattern Detection

> **⚠️ MANDATORY:** Run these commands to detect violations. Failures indicate architecture breaches.

```bash
# Check for wrong Map return types in Repository DSL (CRITICAL)
grep -r "Map<String" module-core-domain/src/main/kotlin/com/github/lambda/domain/repository/

# Check for DTO suffix in projections (should be Projection)
grep -r "Dto" module-core-domain/src/main/kotlin/com/github/lambda/domain/projection/

# Check for model classes in service files (FORBIDDEN)
grep -r "data class.*\|class.*(" module-core-domain/src/main/kotlin/com/github/lambda/domain/service/

# Verify projection package structure
find module-core-domain/src/main/kotlin/com/github/lambda/domain/projection/ -name "*.kt" | grep -v "Projection.kt"
```

**Expected Results:**
- **No Map<String** return types in Repository DSL interfaces
- **No Dto** suffix in projection package
- **No class** definitions in service files (only @Service classes)
- **All files** in projection/ must end with `Projection.kt`

---

## Command Pattern

> **⚠️ CRITICAL:** Commands represent input data for domain operations. They are separate from Projection classes and must follow strict architectural rules.

### Overview

Command classes encapsulate data for write operations (create, update, delete) and complex domain operations. They serve as:

1. **Controller-to-Service Interface**: Structured data passed from API layer to domain services
2. **Domain Operation Input**: Parameters for internal domain logic
3. **Validation Container**: Business rule validation at domain boundaries

### Package Structure

```
module-core-domain/
└── domain/
    └── command/
        ├── metric/
        │   └── MetricCommands.kt       # CreateMetricCommand, UpdateMetricCommand, etc.
        ├── dataset/
        │   └── DatasetCommands.kt      # CreateDatasetCommand, UpdateDatasetCommand, etc.
        ├── quality/
        │   └── QualityCommands.kt      # CreateQualitySpecCommand, ExecuteQualityCommand, etc.
        └── workflow/
            └── WorkflowCommands.kt     # TriggerWorkflowCommand, StopWorkflowCommand, etc.
```

### Implementation Example

#### 1. Command Definition

```kotlin
// module-core-domain/domain/command/metric/MetricCommands.kt

/**
 * Command to create a new metric
 * Used for Controller-to-Service data transfer
 */
data class CreateMetricCommand(
    val name: String,
    val owner: String,
    val sql: String,
    val description: String? = null,
    val team: String? = null,
    val sourceTable: String? = null,
    val tags: Set<String> = emptySet(),
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        require(name.matches(Regex("^[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\\.[a-zA-Z0-9_.-]+\$"))) {
            "Metric name must follow pattern: catalog.schema.name"
        }
        require(owner.isNotBlank()) { "Owner cannot be blank" }
        require(sql.isNotBlank()) { "SQL cannot be blank" }
        description?.let {
            require(it.length <= 1000) { "Description must not exceed 1000 characters" }
        }
    }
}

/**
 * Command to update an existing metric
 */
data class UpdateMetricCommand(
    val name: String,
    val sql: String? = null,
    val description: String? = null,
    val team: String? = null,
    val sourceTable: String? = null,
    val tags: Set<String>? = null,
) {
    init {
        require(name.isNotBlank()) { "Metric name cannot be blank" }
        sql?.let { require(it.isNotBlank()) { "SQL cannot be blank if provided" } }
    }
}
```

#### 2. Mapper Implementation

```kotlin
// module-server-api/mapper/MetricMapper.kt

@Component
class MetricMapper {
    /**
     * Extract command from API request DTO
     */
    fun extractCreateCommand(request: CreateMetricRequest): CreateMetricCommand =
        CreateMetricCommand(
            name = request.name,
            owner = request.owner,
            team = request.team,
            description = request.description,
            sql = request.sql,
            sourceTable = request.sourceTable,
            tags = request.tags.toSet(),  // Convert List to Set as expected by Command
        )

    fun extractUpdateCommand(name: String, request: UpdateMetricRequest): UpdateMetricCommand =
        UpdateMetricCommand(
            name = name,
            sql = request.sql,
            description = request.description,
            team = request.team,
            sourceTable = request.sourceTable,
            tags = request.tags?.toSet(),
        )
}
```

#### 3. Service Usage

```kotlin
// module-core-domain/service/MetricService.kt

@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
    private val metricRepositoryDsl: MetricRepositoryDsl,
) {
    @Transactional
    fun createMetric(command: CreateMetricCommand): MetricEntity {
        // Business logic validation
        if (metricRepositoryJpa.existsByName(command.name)) {
            throw MetricAlreadyExistsException(command.name)
        }

        // Create entity from command
        val metric = MetricEntity(
            name = command.name,
            owner = command.owner,
            team = command.team,
            description = command.description,
            sql = command.sql,
            sourceTable = command.sourceTable,
            tags = command.tags.toMutableSet(),
        )

        return metricRepositoryJpa.save(metric)
    }

    @Transactional
    fun updateMetric(command: UpdateMetricCommand): MetricEntity {
        val existingMetric = metricRepositoryJpa.findByName(command.name)
            ?: throw MetricNotFoundException(command.name)

        // Apply updates from command
        val updatedMetric = existingMetric.copy(
            sql = command.sql ?: existingMetric.sql,
            description = command.description ?: existingMetric.description,
            team = command.team ?: existingMetric.team,
            sourceTable = command.sourceTable ?: existingMetric.sourceTable,
            tags = command.tags?.toMutableSet() ?: existingMetric.tags,
        )

        return metricRepositoryJpa.save(updatedMetric)
    }
}
```

#### 4. Controller Integration

```kotlin
// module-server-api/controller/MetricController.kt

@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/metrics")
class MetricController(
    private val metricService: MetricService,
    private val metricMapper: MetricMapper,
) {
    @PostMapping
    fun createMetric(
        @Valid @RequestBody request: CreateMetricRequest,
    ): ResponseEntity<CreateMetricResponse> {
        // Extract command from request DTO
        val command = metricMapper.extractCreateCommand(request)

        // Execute business logic with command
        val metric = metricService.createMetric(command)

        val response = CreateMetricResponse(
            message = "Metric '${metric.name}' created successfully",
            name = metric.name,
        )

        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PutMapping("/{name}")
    fun updateMetric(
        @PathVariable name: String,
        @Valid @RequestBody request: UpdateMetricRequest,
    ): ResponseEntity<UpdateMetricResponse> {
        val command = metricMapper.extractUpdateCommand(name, request)
        val metric = metricService.updateMetric(command)

        val response = UpdateMetricResponse(
            message = "Metric '${metric.name}' updated successfully",
            name = metric.name,
        )

        return ResponseEntity.ok(response)
    }
}
```

### Design Principles

#### 1. Validation at Domain Boundaries

Commands should validate their data at construction time:

```kotlin
data class CreateDatasetCommand(
    val name: String,
    val sql: String,
) {
    init {
        require(name.isNotBlank()) { "Dataset name cannot be blank" }
        require(sql.isNotBlank()) { "SQL cannot be blank" }
        require(sql.length <= 50000) { "SQL must not exceed 50000 characters" }
    }
}
```

#### 2. Immutability

Commands are immutable data classes representing a single operation:

```kotlin
// ✅ CORRECT - Immutable data class
data class ExecuteQueryCommand(
    val sql: String,
    val parameters: Map<String, Any> = emptyMap(),
    val timeout: Duration = Duration.ofMinutes(5),
)

// ❌ WRONG - Mutable class
class ExecuteQueryCommand {
    var sql: String = ""
    var parameters: MutableMap<String, Any> = mutableMapOf()
}
```

#### 3. Single Responsibility

Each command represents one specific operation:

```kotlin
// ✅ CORRECT - Specific operation
data class CreateMetricCommand(...)
data class UpdateMetricCommand(...)
data class DeleteMetricCommand(...)

// ❌ WRONG - Multiple operations
data class MetricCommand(
    val action: String,  // "create", "update", "delete"
    val data: Any,       // Different data for different actions
)
```

### Architecture Rules

#### 1. No Model Classes in Service Files

```kotlin
// ❌ WRONG - Command defined inline in service
@Service
class MetricService {
    fun createMetric(params: CreateMetricParams): MetricEntity { ... }
}

data class CreateMetricParams(...)  // FORBIDDEN - inline class definition

// ✅ CORRECT - Command in separate package
// File: domain/command/metric/MetricCommands.kt
data class CreateMetricCommand(...)

@Service
class MetricService {
    fun createMetric(command: CreateMetricCommand): MetricEntity { ... }
}
```

#### 2. Consistent Naming Convention

```kotlin
// ✅ CORRECT - Command suffix
data class CreateMetricCommand(...)
data class UpdateDatasetCommand(...)
data class ExecuteQualityCommand(...)

// ❌ WRONG - Other suffixes
data class CreateMetricParams(...)   // Wrong suffix
data class CreateMetricDto(...)      // Wrong suffix
data class CreateMetricRequest(...)  // This is for API layer, not domain
```

#### 3. Type-Safe Conversions

```kotlin
// ✅ CORRECT - Explicit type conversions
fun extractCreateCommand(request: CreateMetricRequest): CreateMetricCommand =
    CreateMetricCommand(
        name = request.name,
        tags = request.tags.toSet(),  // Explicit List -> Set conversion
    )

// ❌ WRONG - Implicit or unsafe conversions
fun extractCreateParams(request: CreateMetricRequest): CreateMetricParams =
    CreateMetricParams(
        tags = request.tags,  // List used where Set expected
    )
```

### Testing Commands

```kotlin
class MetricServiceTest : DescribeSpec({
    val metricRepositoryJpa = mockk<MetricRepositoryJpa>()
    val service = MetricService(metricRepositoryJpa, mockk())

    describe("createMetric") {
        context("when command is valid") {
            it("should create and return metric") {
                // Given
                val command = CreateMetricCommand(
                    name = "test.catalog.metric",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

                every { metricRepositoryJpa.existsByName(command.name) } returns false
                every { metricRepositoryJpa.save(any()) } returnsArgument 0

                // When
                val result = service.createMetric(command)

                // Then
                result.name shouldBe command.name
                result.owner shouldBe command.owner
                verify { metricRepositoryJpa.save(any()) }
            }
        }

        context("when metric already exists") {
            it("should throw MetricAlreadyExistsException") {
                // Given
                val command = CreateMetricCommand(
                    name = "existing.catalog.metric",
                    owner = "test@example.com",
                    sql = "SELECT 1",
                )

                every { metricRepositoryJpa.existsByName(command.name) } returns true

                // When & Then
                shouldThrow<MetricAlreadyExistsException> {
                    service.createMetric(command)
                }
            }
        }
    }
})
```

### Command vs Projection vs Entity

| Type | Purpose | Location | Example |
|------|---------|----------|---------|
| **Command** | Input for operations | `domain.command.{domain}` | `CreateMetricCommand` |
| **Projection** | Output from operations | `domain.projection.{domain}` | `MetricExecutionProjection` |
| **Entity** | Persistent data model | `domain.entity.{domain}` | `MetricEntity` |

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

> **⚠️ CRITICAL FOR AI AGENTS:** All repository interfaces and implementations MUST be organized in domain-specific packages.

### Domain-Specific Package Organization (Mandatory)

All repository files must be organized by business domain to maintain clean architecture boundaries:

#### Domain Repository Package Structure

```
module-core-domain/src/main/kotlin/com/github/lambda/domain/repository/
├── adhoc/                           # Ad-hoc execution domain
├── airflow/                         # Airflow cluster management
├── audit/                           # Audit logging
├── catalog/                         # Data catalog
├── dataset/                         # Dataset management
├── github/                          # GitHub integration
├── lineage/                         # Data lineage
├── metric/                          # Metric definitions
├── quality/                         # Data quality
├── query/                           # Query execution
├── resource/                        # Resource management
├── transpile/                       # SQL transpilation
├── user/                            # User management
└── workflow/                        # Workflow orchestration
```

#### Infrastructure Implementation Package Structure

```
module-core-infra/src/main/kotlin/com/github/lambda/infra/repository/
├── adhoc/
├── airflow/
├── audit/
├── catalog/
├── dataset/
├── github/
├── lineage/
├── metric/
├── quality/
├── query/
├── resource/
├── transpile/
├── user/
└── workflow/
```

#### Package Declaration Pattern

```kotlin
// ✅ CORRECT: Domain repository interface
package com.dataops.basecamp.domain.repository.metric

interface MetricRepositoryJpa {
    fun save(metric: MetricEntity): MetricEntity
    fun findById(id: String): MetricEntity?
}

// ✅ CORRECT: Infrastructure implementation
package com.dataops.basecamp.infra.repository.metric

@Repository("metricRepositoryJpa")
interface MetricRepositoryJpaImpl :
    MetricRepositoryJpa,
    JpaRepository<MetricEntity, String> {
    // ...
}
```

#### Service Injection Pattern

```kotlin
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,      // Injected from metric package
    private val metricRepositoryDsl: MetricRepositoryDsl,      // Injected from metric package
    private val datasetRepositoryJpa: DatasetRepositoryJpa,    // Injected from dataset package
) {
    // Business logic here
}
```

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

> **⚠️ CRITICAL FOR AI AGENTS:** All DTOs MUST follow unified package structure

### DTO Package Organization Rules (MANDATORY)

**✅ CORRECT Package Structure:**
```
module-server-api/src/main/kotlin/com/github/lambda/dto/
├── catalog/CatalogDtos.kt          # Domain-specific DTOs
├── dataset/DatasetDtos.kt          # Domain-specific DTOs
├── metric/MetricDtos.kt            # Domain-specific DTOs
├── quality/QualityDtos.kt          # Domain-specific DTOs
├── transpile/TranspileDtos.kt      # Domain-specific DTOs
├── workflow/WorkflowDtos.kt        # Domain-specific DTOs
└── CommonDto.kt                    # Cross-domain DTOs
```

**❌ FORBIDDEN Package Structures:**
```
com.dataops.basecamp.api.dto.transpile.*     # DEPRECATED - Never use
com.dataops.basecamp.controller.dto.*        # WRONG - Controller-specific DTOs not allowed
com.dataops.basecamp.api.dto.*               # OLD PATTERN - Deprecated
```

### DTO Import Rules

```kotlin
// ✅ CORRECT: Unified package imports
import com.dataops.basecamp.dto.transpile.TranspileResultDto
import com.dataops.basecamp.dto.transpile.TranspileRulesDto
import com.dataops.basecamp.dto.workflow.*

// ❌ FORBIDDEN: Old deprecated packages
import com.dataops.basecamp.api.dto.transpile.TranspileResultDto
import com.dataops.basecamp.controller.dto.SomeDto
```

### Pre-Implementation DTO Checklist

Before creating any new DTOs:

- [ ] ✅ Place DTOs in `com.dataops.basecamp.dto.{domain}` package
- [ ] ✅ Follow `{Domain}Dtos.kt` file naming pattern
- [ ] ✅ Use `*Dto` suffix for all DTO class names
- [ ] ❌ Never use `api.dto.*` or controller-specific packages
- [ ] ✅ Update import statements if refactoring existing DTOs
- [ ] ✅ Verify compilation after DTO package changes

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

### Step 1: Domain Entity (module-core-domain/entity/)

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

### Step 5: API DTOs (module-server-api/dto/{domain}/)

**CRITICAL: Use unified DTO package structure**

```kotlin
// module-server-api/src/main/kotlin/com/github/lambda/dto/pipeline/PipelineDtos.kt
package com.dataops.basecamp.dto.pipeline

data class CreatePipelineRequestDto(
    @field:NotBlank val name: String,
    val description: String?
)

data class PipelineResponseDto(
    val id: Long,
    val name: String,
    val status: String,
    val createdAt: LocalDateTime
)
```

### Step 6: API Controller (module-server-api/controller/)

```kotlin
@RestController
@RequestMapping("/api/v1/pipelines")
class PipelineController(
    private val pipelineService: PipelineService,
    private val pipelineMapper: PipelineMapper,
) {
    @PostMapping
    fun createPipeline(@Valid @RequestBody request: CreatePipelineRequestDto): ResponseEntity<...>

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
