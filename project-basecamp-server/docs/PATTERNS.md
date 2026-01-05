# Quick Reference Patterns & Templates

> **Purpose:** Fast lookup for experienced developers - code snippets, decision tables, naming conventions
> **Audience:** Senior engineers, AI agents
> **Use When:** "I know what I need, show me the pattern"

**See Also:**
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Step-by-step implementation guidance with detailed explanations
- [ENTITY_RELATION.md](./ENTITY_RELATION.md) - Entity relationships diagram and QueryDSL join patterns
- [TESTING.md](./TESTING.md) - Comprehensive testing strategies and examples
- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error codes, exception hierarchy, response format

---

## Table of Contents

1. [Module Placement Rules](#module-placement-rules)
2. [Domain Package Organization Rules](#domain-package-organization-rules)
3. [Entity Organization Rules](#entity-organization-rules)
4. [DTO Organization Rules](#dto-organization-rules)
5. [Repository Naming Convention](#repository-naming-convention)
6. [Entity Relation Rules](#entity-relation-rules)
7. [JPA vs QueryDSL Decision](#jpa-vs-querydsl-decision)
8. [Projection Pattern](#projection-pattern)
9. [Command Pattern](#command-pattern)
10. [Data Ownership Patterns](#data-ownership-patterns)
11. [External System Integration](#external-system-integration)
12. [Code Templates](#code-templates)
13. [Dependency Versions](#dependency-versions)
14. [New Feature Checklist](#new-feature-checklist)

---

## Module Placement Rules

### Quick Reference Table

| Module | Purpose | What Goes Here | What Does NOT Go Here |
|--------|---------|----------------|----------------------|
| **module-core-common** | Shared utilities, no domain dependencies | Base exceptions, **all enums**, utilities, constants, shared DTOs | Domain entities, domain-specific exceptions |
| **module-core-domain** | Domain models & business logic | JPA entities, domain services, repository interfaces (ports), domain-specific exceptions | Infrastructure implementations, external client implementations, enums |
| **module-core-infra** | Infrastructure implementations | Repository implementations (adapters), external API clients, infrastructure exceptions | Domain entities, controllers, API DTOs |
| **module-server-api** | REST API layer | Controllers, API request/response DTOs, mappers, API configuration | Domain services, entities, repository implementations |

### Exception Placement Quick Guide

```kotlin
// module-core-common: Base exceptions (NO domain dependencies)
abstract class BusinessException(...)
class ResourceNotFoundException(...)      // Generic, reusable
class ExternalSystemException(...)        // Generic external system error

// module-core-infra: External system exceptions
class AirflowConnectionException(...)     // Airflow-specific
class BigQueryExecutionException(...)     // BigQuery-specific

// module-core-domain: Domain-specific exceptions
class MetricNotFoundException(...)        // Tied to MetricEntity
class DatasetValidationException(...)     // Tied to Dataset domain rules
```

### Decision Tree

```
Is the class an enum or dependency-free utility?
├── YES → module-core-common
│   ├── Is it an enum? → common/enums/
│   └── Is it a utility? → common/util/
└── NO → Does it depend on domain entities or domain-specific logic?
    ├── YES → module-core-domain
    │   ├── Is it a repository interface? → domain/repository/
    │   ├── Is it a service? → domain/service/
    │   └── Is it an entity? → domain/entity/{domain}/
    └── NO → Check if it's infrastructure
        ├── External API client? → module-core-infra/external/
        ├── Repository implementation? → module-core-infra/repository/
        ├── External system exception? → module-core-common/exception/ (or infra)
        └── Shared utility/base class? → module-core-common/
```

### Anti-Pattern Detection

```bash
# Check for misplaced exceptions (external exceptions in domain)
grep -r "class.*Exception" module-core-domain/src/ --include="*.kt" | grep -v "Entity\|Service\|Repository"

# Verify domain has no infrastructure imports
grep -r "import.*infra\." module-core-domain/src/ --include="*.kt"
```

---

## Domain Package Organization Rules

> **⚠️ CRITICAL FOR AI AGENTS:** These package organization rules ensure clean hexagonal architecture boundaries.

### Package Purpose and Placement

| Package | Purpose | When to Use | Examples |
|---------|---------|-------------|----------|
| **command/** | Incoming requests, write operations, query filters | Commands that modify state, query parameters, filter objects | `CreateMetricCommand`, `CancelQueryCommand`, `ListQueriesQuery`, `CatalogFilters` |
| **projection/** | Outgoing read models, external API responses | Data returned to external consumers, read-only views | `MetricStatisticsProjection`, `TableInfo`, `GitHubPullRequest` |
| **external/** | External system integration | Port interfaces and request/response models for external systems | `QueryEngineClient`, `AirflowResponse`, `BasecampParserRequest` |
| **internal/** | Domain-only usage | Value objects used only within domain layer | `UserAggregate`, `ScheduleInfo` (JPA @Embeddable) |
| **entity/** | JPA entities only | Persistent domain objects | `MetricEntity`, `QueryExecutionEntity` |
| **service/** | Domain business logic | Core domain operations | `MetricService`, `QueryService` |
| **repository/** | Data access interfaces (ports) | Repository contracts | `MetricRepositoryJpa`, `MetricRepositoryDsl` |
| **util/** | Dependency-free utilities (DEPRECATED - use common/util/) | Pure utility functions | ❌ DEPRECATED: Use module-core-common/util/ |

### Package Migration Rules

#### ❌ DEPRECATED Packages (Clean These Up)

```kotlin
// OLD: Legacy package structures
domain/model/query/QueryModels.kt          // ❌ DEPRECATED: Moved to command/query/ or projection/query/
domain/model/catalog/CatalogModels.kt      // ❌ DEPRECATED: Split to command/catalog/ and projection/catalog/
domain/model/github/GitHubPullRequest.kt   // ❌ DEPRECATED: Moved to projection/github/
domain/model/health/ComponentHealth.kt     // ❌ DEPRECATED: Moved to projection/health/
domain/model/user/UserAggregate.kt         // ❌ DEPRECATED: Moved to internal/user/
domain/model/workflow/ScheduleInfo.kt      // ❌ DEPRECATED: Moved to internal/workflow/
domain/util/QueryIdGenerator.kt            // ❌ DEPRECATED: Moved to common/util/
domain/model/*/*Enums.kt                   // ❌ DEPRECATED: All enums moved to common/enums/

// NEW: Correct locations
domain/command/query/QueryModels.kt        // ✅ CORRECT: Query filters and commands
domain/command/catalog/CatalogCommands.kt  // ✅ CORRECT: CatalogFilters (query parameter)
domain/projection/catalog/CatalogProjections.kt // ✅ CORRECT: TableInfo, TableDetail (read models)
domain/projection/github/GitHubProjections.kt   // ✅ CORRECT: GitHubPullRequest (external API response)
domain/projection/health/HealthProjections.kt   // ✅ CORRECT: ComponentHealth (health check results)
domain/internal/user/UserAggregate.kt      // ✅ CORRECT: Domain aggregate (internal only)
domain/internal/workflow/ScheduleInfo.kt   // ✅ CORRECT: JPA @Embeddable (internal only)
common/util/QueryUtility.kt                // ✅ CORRECT: Utility classes (dependency-free)
common/enums/QueryEnums.kt                 // ✅ CORRECT: All enums go here
```

#### ✅ CORRECT Package Organization

```kotlin
// Query-related objects
domain/command/query/QueryCommands.kt      // Actions: CancelQueryCommand
domain/command/query/QueryModels.kt        // Filters: ListQueriesQuery, QueryScopeFilter
domain/entity/query/QueryExecutionEntity.kt // Persistence: QueryExecutionEntity
domain/external/queryengine/QueryEngineClient.kt // Port: External system interface
domain/projection/query/QueryProjections.kt     // Results: QueryDetailProjection

// Catalog-related objects
domain/command/catalog/CatalogCommands.kt  // Filters: CatalogFilters
domain/projection/catalog/CatalogProjections.kt // Read models: TableInfo, TableDetail
domain/service/CatalogService.kt           // Logic: Catalog domain service

// GitHub-related objects
domain/command/github/GitHubCommands.kt    // Filters: PullRequestFilter
domain/projection/github/GitHubProjections.kt   // Read models: GitHubPullRequest, GitHubBranch
domain/external/github/GitHubClient.kt     // Port: GitHub API interface

// Health-related objects
domain/projection/health/HealthProjections.kt   // Read models: ComponentHealth
domain/external/health/HealthIndicator.kt  // Port: Health check interface
domain/service/HealthService.kt            // Logic: Health domain service

// Workflow-related objects
domain/command/workflow/WorkflowSpec.kt    // Command: YAML parsed workflow spec
domain/internal/workflow/ScheduleInfo.kt   // Internal: JPA @Embeddable value object
domain/entity/workflow/WorkflowEntity.kt   // Persistence: WorkflowEntity
domain/service/WorkflowService.kt          // Logic: Workflow domain service

// User-related objects
domain/internal/user/UserAggregate.kt      // Internal: Domain aggregate (not exposed)
domain/entity/user/UserEntity.kt           // Persistence: UserEntity
domain/service/UserService.kt              // Logic: User domain service
```

### Hexagonal Architecture Rules

#### Port vs External Interface Decision

| Scenario | Package | Reasoning |
|----------|---------|-----------|
| External system clients | `external/` | Port interfaces for adapters to implement |
| Repository interfaces | `repository/` | Data access ports (specialized hexagonal pattern) |
| Internal domain contracts | `model/` or `command/` | Not external system interfaces |

#### Utility Placement Rules

| Utility Type | Location | Examples |
|--------------|----------|----------|
| **Dependency-free** | `util/` (root level) | `QueryIdGenerator`, `StringUtils` |
| **Domain-specific** | `util/{domain}/` (deprecated) | Move to root util or service |
| **With dependencies** | Appropriate domain package | Utilities that need Spring/JPA |

### Package Placement Decision Tree

```
What is this class?
├── Is it a JPA Entity (@Entity)?
│   └── YES → domain/entity/{domain}/
│
├── Is it incoming data (from external requests)?
│   ├── Command to modify state? → domain/command/{domain}/
│   ├── Query filter or parameter? → domain/command/{domain}/
│   └── Request DTO? → domain/command/{domain}/
│
├── Is it outgoing data (returned to external consumers)?
│   ├── Read model or view? → domain/projection/{domain}/
│   ├── External API response? → domain/projection/{domain}/
│   └── Statistics or aggregation? → domain/projection/{domain}/
│
├── Is it for external system integration?
│   ├── Client interface? → domain/external/{system}/
│   ├── Request model? → domain/external/{system}/
│   └── Response model? → domain/external/{system}/
│
├── Is it used ONLY within domain layer?
│   ├── Domain aggregate? → domain/internal/{domain}/
│   ├── JPA @Embeddable? → domain/internal/{domain}/
│   └── Value object? → domain/internal/{domain}/
│
├── Is it business logic?
│   └── YES → domain/service/
│
└── Is it data access contract?
    └── YES → domain/repository/{domain}/
```

### Migration Checklist for AI Agents

When refactoring domain packages:

- [ ] ✅ Categorize each model class (Command/Projection/External/Internal)
- [ ] ✅ Create new package directories (command, projection, internal)
- [ ] ✅ Move files to appropriate packages
- [ ] ✅ Update package declarations in moved files
- [ ] ✅ Update all import statements across service, infra, and API layers
- [ ] ✅ Remove old model directories and files
- [ ] ✅ Run ktlintFormat to fix code style
- [ ] ✅ Verify build passes (compile + test)

### Anti-Pattern Detection

```bash
# Check for deprecated package usage
find . -path "*/domain/query/query/*" -name "*.kt"
find . -path "*/domain/port/*" -name "*.kt"
find . -path "*/domain/util/*/query*" -name "*.kt"

# Verify correct package imports
grep -r "import.*domain\.query\.query\." --include="*.kt" .
grep -r "import.*domain\.port\." --include="*.kt" .
```

---

## Entity Organization Rules

> **⚠️ CRITICAL:** ALL JPA Entity classes MUST be in `domain/entity/{domain}/`
>
> **📖 Detailed Guide:** See [IMPLEMENTATION_GUIDE.md - Entity Organization Rules](./IMPLEMENTATION_GUIDE.md#entity-organization-rules-critical)

### Quick Reference

| Rule | Pattern | Example |
|------|---------|---------|
| **Package** | `domain.entity.{domain}` | `domain.entity.quality` |
| **Base Entities** | `domain.entity` | `BaseEntity.kt` |
| **Import** | `domain.entity.*` | `import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity` |

### FORBIDDEN Patterns

```kotlin
// ❌ WRONG: Old model package
import com.dataops.basecamp.domain.model.quality.QualitySpecEntity

// ✅ CORRECT: Entity package
import com.dataops.basecamp.domain.entity.quality.QualitySpecEntity
```

---

## DTO Organization Rules

> **⚠️ CRITICAL FOR AI AGENTS:** These DTO placement rules are MANDATORY and must be followed exactly.

### Quick Reference Table

| Pattern | Location | Example | Status |
|---------|----------|---------|--------|
| **✅ CORRECT** | `module-server-api/dto/{domain}/` | `dto/transpile/TranspileDtos.kt` | ✅ Use This |
| **❌ FORBIDDEN** | `module-server-api/api/dto/` | `api/dto/transpile/TranspileDtos.kt` | ❌ Deprecated |
| **❌ FORBIDDEN** | `module-server-api/controller/dto/` | `controller/dto/SomeDto.kt` | ❌ Never Use |

### Unified DTO Package Structure

```
module-server-api/src/main/kotlin/com/github/lambda/dto/
├── catalog/CatalogDtos.kt          ✅ Domain-specific DTOs
├── dataset/DatasetDtos.kt          ✅ Domain-specific DTOs
├── metric/MetricDtos.kt            ✅ Domain-specific DTOs
├── quality/QualityDtos.kt          ✅ Domain-specific DTOs
├── transpile/TranspileDtos.kt      ✅ Domain-specific DTOs
├── workflow/WorkflowDtos.kt        ✅ Domain-specific DTOs
├── run/RunDtos.kt                  ✅ Domain-specific DTOs
├── query/QueryDtos.kt              ✅ Domain-specific DTOs
├── lineage/LineageDtos.kt          ✅ Domain-specific DTOs
├── health/HealthDtos.kt            ✅ Domain-specific DTOs
├── airflow/AirflowSyncDtos.kt      ✅ Domain-specific DTOs
├── github/GitHubDtos.kt            ✅ Domain-specific DTOs
└── CommonDto.kt                    ✅ Cross-domain DTOs
```

### DTO Import Patterns

```kotlin
// ✅ CORRECT: Unified package imports
import com.dataops.basecamp.dto.transpile.TranspileResultDto
import com.dataops.basecamp.dto.transpile.TranspileRulesDto
import com.dataops.basecamp.dto.workflow.*

// ❌ FORBIDDEN: Old deprecated packages
import com.dataops.basecamp.api.dto.transpile.TranspileResultDto
import com.dataops.basecamp.controller.dto.SomeDto
```

### File Naming Convention

| Pattern | Example | Purpose |
|---------|---------|---------|
| `{Domain}Dtos.kt` | `TranspileDtos.kt` | All DTOs for a domain |
| `{Domain}Dto` | `TranspileResultDto` | Individual DTO class |

### Migration Checklist for AI Agents

When working with DTOs:

- [ ] ✅ Place all DTOs in `com.dataops.basecamp.dto.{domain}` package
- [ ] ❌ Never use `api.dto.*` or controller-specific DTO packages
- [ ] ✅ Update import statements to use unified DTO package
- [ ] ✅ Follow `*Dto` suffix naming convention
- [ ] ✅ Verify compilation after DTO package changes
- [ ] ✅ Group related DTOs by domain in subdirectories

### Anti-Pattern Detection

```bash
# Check for deprecated DTO packages
find . -path "*/api/dto/*" -name "*.kt" | grep -v test

# Check for incorrect import statements
grep -r "import.*api\.dto\." --include="*.kt" .

# Verify unified DTO structure
ls -la module-server-api/src/main/kotlin/com/github/lambda/dto/
```

---

## Repository Naming Convention

| Layer | Pattern | Example |
|-------|---------|---------|
| **module-core-domain** | `{Entity}RepositoryJpa` | `CatalogTableRepositoryJpa` |
| **module-core-domain** | `{Entity}RepositoryDsl` | `CatalogTableRepositoryDsl` |
| **module-core-infra** | `{Entity}RepositoryJpaImpl` | `CatalogTableRepositoryJpaImpl` |
| **module-core-infra** | `{Entity}RepositoryDslImpl` | `CatalogTableRepositoryDslImpl` |

### Repository Package Organization (Domain-Specific)

> **⚠️ CRITICAL FOR AI AGENTS:** All repository interfaces and implementations MUST be organized in domain-specific packages.

#### Package Structure (Mandatory)

```
module-core-domain/src/main/kotlin/com/github/lambda/domain/repository/
├── adhoc/                           # Ad-hoc execution repositories
│   ├── AdHocExecutionRepositoryJpa.kt
│   ├── AdHocExecutionRepositoryDsl.kt
│   └── UserExecutionQuotaRepositoryJpa.kt
├── airflow/                         # Airflow cluster repositories
│   ├── AirflowClusterRepositoryJpa.kt
│   └── AirflowClusterRepositoryDsl.kt
├── audit/                           # Audit repositories
│   ├── AuditAccessRepositoryJpa.kt
│   └── AuditResourceRepositoryJpa.kt
├── catalog/                         # Catalog repositories
│   ├── CatalogTableRepositoryJpa.kt
│   ├── CatalogTableRepositoryDsl.kt
│   ├── CatalogColumnRepositoryJpa.kt
│   ├── CatalogRepositoryJpa.kt
│   ├── CatalogRepositoryDsl.kt
│   ├── SampleQueryRepositoryJpa.kt
│   └── SampleQueryRepositoryDsl.kt
├── dataset/                         # Dataset repositories
│   ├── DatasetRepositoryJpa.kt
│   └── DatasetRepositoryDsl.kt
├── github/                          # GitHub repositories
│   ├── GitHubRepositoryJpa.kt
│   └── GitHubRepositoryDsl.kt
├── lineage/                         # Lineage repositories
│   ├── LineageNodeRepositoryJpa.kt
│   ├── LineageNodeRepositoryDsl.kt
│   ├── LineageEdgeRepositoryJpa.kt
│   └── LineageEdgeRepositoryDsl.kt
├── metric/                          # Metric repositories
│   ├── MetricRepositoryJpa.kt
│   └── MetricRepositoryDsl.kt
├── quality/                         # Quality repositories
│   ├── QualitySpecRepositoryJpa.kt
│   ├── QualitySpecRepositoryDsl.kt
│   ├── QualityRunRepositoryJpa.kt
│   ├── QualityTestRepositoryJpa.kt
│   └── TestResultRepositoryJpa.kt
├── query/                           # Query repositories
│   ├── QueryExecutionRepositoryJpa.kt
│   └── QueryExecutionRepositoryDsl.kt
├── resource/                        # Resource repositories
│   ├── ResourceRepositoryJpa.kt
│   └── ResourceRepositoryDsl.kt
├── transpile/                       # Transpile repositories
│   ├── TranspileRuleRepositoryJpa.kt
│   └── TranspileRuleRepositoryDsl.kt
├── user/                            # User repositories
│   ├── UserRepositoryJpa.kt
│   ├── UserRepositoryDsl.kt
│   ├── UserAuthorityRepositoryJpa.kt
│   └── UserAuthorityRepositoryDsl.kt
└── workflow/                        # Workflow repositories
    ├── WorkflowRepositoryJpa.kt
    ├── WorkflowRepositoryDsl.kt
    ├── WorkflowRunRepositoryJpa.kt
    └── WorkflowRunRepositoryDsl.kt
```

#### Infrastructure Implementation Package Structure

```
module-core-infra/src/main/kotlin/com/github/lambda/infra/repository/
├── adhoc/
│   ├── AdHocExecutionRepositoryJpaImpl.kt
│   ├── AdHocExecutionRepositoryDslImpl.kt
│   └── UserExecutionQuotaRepositoryJpaImpl.kt
├── airflow/
│   ├── AirflowClusterRepositoryJpaImpl.kt
│   └── AirflowClusterRepositoryDslImpl.kt
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
    # ... (corresponding *Impl.kt files)
```

#### Package Declaration Rules

```kotlin
// ✅ CORRECT: Domain repository interfaces
package com.dataops.basecamp.domain.repository.quality

interface QualitySpecRepositoryJpa {
    fun save(spec: QualitySpecEntity): QualitySpecEntity
    fun findById(id: Long): QualitySpecEntity?
}

// ✅ CORRECT: Infrastructure implementations
package com.dataops.basecamp.infra.repository.quality

@Repository("qualitySpecRepositoryJpa")
class QualitySpecRepositoryJpaImpl(
    // ...
) : QualitySpecRepositoryJpa {
    // ...
}
```

#### Import Pattern

```kotlin
// ✅ CORRECT: Domain-specific imports
import com.dataops.basecamp.domain.repository.quality.QualitySpecRepositoryJpa
import com.dataops.basecamp.domain.repository.quality.QualitySpecRepositoryDsl
import com.dataops.basecamp.domain.repository.workflow.WorkflowRepositoryJpa

// ❌ WRONG: Old flat package imports
import com.dataops.basecamp.domain.repository.QualitySpecRepositoryJpa
```

#### Service Injection Pattern

```kotlin
@Service
@Transactional(readOnly = true)
class QualityService(
    private val qualitySpecRepositoryJpa: QualitySpecRepositoryJpa,  // Auto-injected from quality package
    private val qualitySpecRepositoryDsl: QualitySpecRepositoryDsl,  // Auto-injected from quality package
) {
    // ...
}
```

### Forbidden Patterns

```kotlin
// ❌ REJECTED - Missing Jpa/Dsl suffix
interface SampleQueryRepository
class SampleQueryRepositoryImpl

// ❌ REJECTED - Separate SpringData interface
interface ItemRepositoryJpaSpringData : JpaRepository<...>
```

### Correct Patterns

```kotlin
// Domain (module-core-domain/repository/)
interface SampleQueryRepositoryJpa { ... }   // CRUD
interface SampleQueryRepositoryDsl { ... }   // Complex queries

// Infra (module-core-infra/repository/) - Simplified Pattern (Recommended)
@Repository("sampleQueryRepositoryJpa")
interface SampleQueryRepositoryJpaImpl :
    SampleQueryRepositoryJpa,
    JpaRepository<SampleQueryEntity, Long>

@Repository("sampleQueryRepositoryDsl")
class SampleQueryRepositoryDslImpl : SampleQueryRepositoryDsl { ... }
```

---

## Entity Relation Rules

> JPA Relation 사용 금지, ID 참조 + QueryDSL Join 사용
> **📖 상세**: [ENTITY_RELATION.md](./ENTITY_RELATION.md) | [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md#entity-relation-rules)

### Quick Reference

| 항목 | 규칙 | 대안 |
|------|------|------|
| `@ManyToOne` | ❌ 금지 | ID 필드 (e.g., `ownerId: Long`) |
| `@OneToMany` | ❌ 금지 | QueryDSL Join |
| `@OneToOne` | ❌ 금지 | ID 필드 + QueryDSL Join |
| `@ManyToMany` | ❌ 금지 | 중간 Entity |
| `FetchType.EAGER` | ❌ 금지 | QueryDSL 명시적 Join |

### Entity Pattern

```kotlin
// ✅ 권장: ID로만 참조
@Entity
class DatasetEntity(
    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,  // ✅ ID 참조

    // ❌ 금지: Entity 참조
    // val owner: UserEntity
)
```

### 이유

| 문제 | 해결 |
|------|------|
| N+1 쿼리 | QueryDSL 명시적 Join |
| LazyInitializationException | ID 참조로 원천 차단 |
| 순환 참조 | 단방향 ID 참조 |

---

## JPA vs QueryDSL Decision

### Quick Decision Table

| Scenario | Use | Example |
|----------|-----|---------|
| Create/Update/Delete single entity | JPA | `repository.save(entity)` |
| Find by 1-2 simple fields | JPA | `findById()`, `findByName()` |
| Find by 3+ conditions or dynamic filters | QueryDSL | Variable WHERE clauses |
| Fetch related entities (aggregation) | QueryDSL | Order + OrderItems |
| Projection with joined data | QueryDSL | User with order count |
| Paginated list with sorting | QueryDSL | Complex list queries |
| Batch updates | JPA | `saveAll()` |

### The "3-Word Rule"

If a JPA method name exceeds **3 words** (counting `And`/`Or` separators), switch to QueryDSL:

```kotlin
// ✅ OK for JPA (1-2 conditions)
fun findByName(name: String): Entity?
fun findByStatusAndType(status: Status, type: Type): List<Entity>

// ❌ TOO COMPLEX for JPA - Use QueryDSL
fun findByNameAndStatusAndTypeAndCreatedAtAfter(...)  // 4+ conditions
```

---

## Projection Pattern

> **⚠️ CRITICAL:** Service return values MUST use Projection postfix, NO inline classes in service files
>
> **📖 Detailed Guide:** See [IMPLEMENTATION_GUIDE.md - Projection Pattern](./IMPLEMENTATION_GUIDE.md#projection-pattern)

### Quick Reference

| Type | Pattern | Package | When to Use |
|------|---------|---------|-------------|
| **QueryDSL Results** | `{Entity}{Purpose}Projection` | `projection.{domain}` | Repository DSL complex queries |
| **Service Returns** | `{Feature}Projection` | `projection.{domain}` | Service → Controller data |

### CRITICAL Rules

```kotlin
// ✅ CORRECT - Projection in separate file
// File: domain/projection/workflow/WorkflowProjections.kt
data class WorkflowRunStatisticsProjection(...)

interface WorkflowRunRepositoryDsl {
    fun getRunStatistics(): WorkflowRunStatisticsProjection
}

// ❌ WRONG - Inline class or wrong suffix
@Service
class WorkflowService {
    data class WorkflowStats(...)  // FORBIDDEN - no classes in service files
    fun getStats(): WorkflowStatsDto { ... }  // Wrong suffix
}
```

---

## Command Pattern

> **⚠️ CRITICAL:** Controller-to-Service data MUST use Command postfix, NO inline classes in service files
>
> **📖 Detailed Guide:** See [IMPLEMENTATION_GUIDE.md - Command Pattern](./IMPLEMENTATION_GUIDE.md#command-pattern)

### Quick Reference

| Type | Pattern | Package | When to Use |
|------|---------|---------|-------------|
| **Controller → Service** | `{Action}{Entity}Command` | `command.{domain}` | Create/Update/Delete operations |
| **Query Filters** | `{Entity}Query` | `command.{domain}` | List/Search parameters |

### CRITICAL Rules

```kotlin
// ✅ CORRECT - Command in separate file
// File: domain/command/metric/MetricCommands.kt
data class CreateMetricCommand(
    val name: String,
    val sql: String,
) {
    init {
        require(name.isNotBlank()) { "Name cannot be blank" }
    }
}

@Service
class MetricService {
    fun createMetric(command: CreateMetricCommand): MetricEntity { ... }
}

// ❌ WRONG - Inline class or wrong suffix
@Service
class MetricService {
    data class CreateMetricParams(...)  // FORBIDDEN - no classes in service files
    fun createMetric(params: CreateMetricParams) { ... }  // Wrong suffix
}
```

---

## Data Ownership Patterns

> **ASK IF UNCLEAR** - Feature spec mentions both patterns? Ask the user!

| Scenario | Pattern | When to Use | Example |
|----------|---------|-------------|---------|
| **Self-managed** | JPA Entity + RepositoryJpa/Dsl | Data stored in our DB | `CatalogTableEntity`, `DatasetEntity` |
| **External API** | External Client + Domain Models | Real-time from external system | `BigQueryClient`, `TrinoClient` |
| **Hybrid** | JPA Entity (cache) + External Client | External data cached locally | Metadata cache |

```kotlin
// Self-managed: JPA Entity
@Entity
@Table(name = "catalog_tables")
class CatalogTableEntity(...) : BaseEntity()

// External: Domain Model (Not Entity)
data class TableInfo(
    val name: String,
    val engine: String,  // "bigquery" or "trino"
)
```

---

## External System Integration

> **⚠️ CRITICAL:** External system clients MUST use system-specific packages
>
> **📖 Detailed Guide:** See [IMPLEMENTATION_GUIDE.md - External System Integration](./IMPLEMENTATION_GUIDE.md#external-system-integration-patterns)

### Quick Reference

| Rule | Pattern | Example |
|------|---------|---------|
| **Package** | `external.{system}` | `external.airflow`, `external.github` |
| **Client Interface** | `{System}Client.kt` | `AirflowClient.kt` |
| **Response Models** | `{System}Response.kt` | `AirflowResponse.kt` |
| **Naming** | `*Response` postfix | `AirflowDAGRunStatusResponse` |

### CRITICAL Rules

```kotlin
// ✅ CORRECT - System-specific package
package com.dataops.basecamp.domain.external.airflow

interface AirflowClient {
    fun getDAGRun(...): AirflowDAGRunStatusResponse  // Response postfix
}

// ❌ WRONG - Generic package or missing Response postfix
package com.dataops.basecamp.domain.external
data class AirflowDAGRunStatus(...)  // Missing "Response"
```

---

## Code Templates

### Domain Repository Interface

```kotlin
// module-core-domain/repository/ItemRepositoryJpa.kt
interface ItemRepositoryJpa {
    fun save(item: ItemEntity): ItemEntity
    fun deleteById(id: Long)
    fun existsById(id: Long): Boolean
    fun findAll(): List<ItemEntity>
    fun findByName(name: String): ItemEntity?
}

// module-core-domain/repository/ItemRepositoryDsl.kt
interface ItemRepositoryDsl {
    fun findByConditions(query: GetItemsQuery): Page<ItemEntity>
}
```

### Infrastructure Implementation (Simplified Pattern)

```kotlin
// module-core-infra/repository/ItemRepositoryJpaImpl.kt
@Repository("itemRepositoryJpa")
interface ItemRepositoryJpaImpl :
    ItemRepositoryJpa,
    JpaRepository<ItemEntity, Long> {

    override fun findByName(name: String): ItemEntity?
}
```

### Service Pattern

```kotlin
@Service
@Transactional(readOnly = true)
class ItemService(
    private val itemRepositoryJpa: ItemRepositoryJpa,
    private val itemRepositoryDsl: ItemRepositoryDsl,
) {
    @Transactional
    fun createItem(command: CreateItemCommand): ItemDto { ... }

    fun getItem(query: GetItemQuery): ItemDto? { ... }
}
```

### Controller Pattern

```kotlin
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/items")
@Validated
@Tag(name = "Item", description = "Item API")
class ItemController(
    private val itemService: ItemService,
    private val itemMapper: ItemMapper,
) {
    @Operation(summary = "Get items")
    @GetMapping
    fun getItems(
        @RequestParam(required = false) status: ItemStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedResponse<ItemResponse>>> { ... }
}
```

### Test Patterns

> **📖 Complete Testing Guide:** See [TESTING.md](./TESTING.md) for comprehensive testing strategies and patterns

**Quick Links:**
- [Controller Test Pattern](./TESTING.md#controller-test---slice-module-server-api)
- [Service Test Pattern](./TESTING.md#service-test-module-core-domain)
- [Repository Test Pattern](./TESTING.md#repository-test---jpa-module-core-infra)
- [Dependency Versions](./TESTING.md#dependency-versions)
- [Spring Boot 4.x Migration](./TESTING.md#spring-boot-4x-migration-changes)

---

## New Feature Checklist

### Adding a New Entity

- [ ] Create `{Entity}Entity.kt` in `module-core-domain/entity/{feature}/`
- [ ] Create `{Entity}RepositoryJpa.kt` interface in `module-core-domain/repository/`
- [ ] Create `{Entity}RepositoryDsl.kt` interface (if complex queries needed)
- [ ] Create `{Entity}RepositoryJpaImpl.kt` interface in `module-core-infra/repository/`
- [ ] Add QueryDSL Q-class generation (kapt)

### Adding a New API Endpoint

- [ ] Create `{Feature}Controller.kt` in `module-server-api/controller/`
- [ ] Create `{Feature}Service.kt` in `module-core-domain/service/`
- [ ] Create DTOs in unified location: `module-server-api/dto/{feature}/{Feature}Dtos.kt`
- [ ] Use correct package: `com.dataops.basecamp.dto.{feature}`
- [ ] Follow naming convention: `{Feature}RequestDto`, `{Feature}ResponseDto`
- [ ] Create `{Feature}Mapper.kt` for DTO <-> Domain conversion
- [ ] Create `{Feature}ControllerTest.kt` with proper annotations
- [ ] Verify package is in `scanBasePackages` of `BasecampServerApplication`

### Adding a Controller Test

- [ ] Use `@SpringBootTest` + `@AutoConfigureMockMvc` (NOT `@WebMvcTest`)
- [ ] Use `JsonMapper` (NOT `ObjectMapper`)
- [ ] Add `@Execution(ExecutionMode.SAME_THREAD)`
- [ ] Add `@MockkBean(relaxed = true)` for all dependencies
- [ ] Use `.with(csrf())` for POST/PUT/DELETE requests
- [ ] Use `@WithMockUser` for authentication

---

## Enum and Utility Placement Rules

> **⚠️ CRITICAL:** ALL enums and dependency-free utilities belong in `module-core-common`

### Enum Placement

**Rule:** ALL enums go to `module-core-common/src/main/kotlin/com/github/lambda/common/enums/`

```kotlin
// ✅ CORRECT: All enums in common/enums/
common/enums/QueryEnums.kt          // QueryStatus, QueryEngine, QueryScope
common/enums/ExecutionStatus.kt     // Ad-hoc execution statuses
common/enums/WorkflowEnums.kt       // Workflow-related enums
common/enums/QualityEnums.kt        // Quality test enums
common/enums/UserRole.kt            // User role enum
common/enums/AirflowEnums.kt        // Airflow state enums
common/enums/LineageEnums.kt        // Lineage-related enums
common/enums/GitHubEnums.kt         // GitHub integration enums

// ❌ WRONG: No enums in domain
domain/model/*/SomeEnum.kt          // Move to common/enums/
domain/entity/*/SomeEnum.kt         // Move to common/enums/
```

### Utility Placement

**Rule:** Dependency-free utilities go to `module-core-common/src/main/kotlin/com/github/lambda/common/util/`

```kotlin
// ✅ CORRECT: Utilities in common/util/
common/util/QueryUtility.kt         // Query ID generation utilities
common/util/DateTimeUtils.kt        // Date/time utilities

// ❌ WRONG: No utilities in domain
domain/util/*/SomeUtil.kt           // Move to common/util/
```

### Import Pattern

```kotlin
// Entity imports from common
import com.dataops.basecamp.common.enums.QueryStatus
import com.dataops.basecamp.common.enums.ExecutionStatus
import com.dataops.basecamp.common.util.QueryUtility

// Services inject from common
class SomeService(
    private val queryUtility: QueryUtility, // ✅ From common
)
```

---

## Quick Reference Table

| Task | Reference | Key Pattern |
|------|-----------|-------------|
| Controller test | [TESTING.md#controller-test](./TESTING.md#controller-test---slice-module-server-api) | @SpringBootTest + @AutoConfigureMockMvc |
| Service test | [TESTING.md#service-test](./TESTING.md#service-test-module-core-domain) | Pure MockK, no Spring context |
| Repository test | [TESTING.md#repository-test](./TESTING.md#repository-test---jpa-module-core-infra) | @DataJpaTest + TestEntityManager |
| Entity model | [IMPLEMENTATION_GUIDE.md#entity-patterns](./IMPLEMENTATION_GUIDE.md#entity-patterns) | JPA Entity + QueryDSL |
| DTO mapping | [IMPLEMENTATION_GUIDE.md#dto-and-mapper-patterns](./IMPLEMENTATION_GUIDE.md#dto-and-mapper-patterns) | Manual mapping functions |
| API endpoint | [IMPLEMENTATION_GUIDE.md#controller-patterns](./IMPLEMENTATION_GUIDE.md#controller-patterns) | @RestController + validation |

---

*Last Updated: 2026-01-03*
