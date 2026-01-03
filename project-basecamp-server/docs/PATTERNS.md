# Quick Reference Patterns & Templates

> **Purpose:** Fast lookup for experienced developers - code snippets, decision tables, naming conventions
> **Audience:** Senior engineers, AI agents
> **Use When:** "I know what I need, show me the pattern"

**See Also:**
- [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Step-by-step implementation guidance with detailed explanations
- [TESTING.md](./TESTING.md) - Comprehensive testing strategies and examples
- [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error codes, exception hierarchy, response format

---

## Table of Contents

1. [Module Placement Rules](#module-placement-rules)
2. [Repository Naming Convention](#repository-naming-convention)
3. [Entity Relationship Rules](#entity-relationship-rules)
4. [JPA vs QueryDSL Decision](#jpa-vs-querydsl-decision)
5. [Data Ownership Patterns](#data-ownership-patterns)
6. [Code Templates](#code-templates)
7. [Dependency Versions](#dependency-versions)
8. [New Feature Checklist](#new-feature-checklist)

---

## Module Placement Rules

### Quick Reference Table

| Module | Purpose | What Goes Here | What Does NOT Go Here |
|--------|---------|----------------|----------------------|
| **module-core-common** | Shared utilities, no domain dependencies | Base exceptions, common enums, utilities, constants, shared DTOs | Domain entities, domain-specific exceptions |
| **module-core-domain** | Domain models & business logic | JPA entities, domain services, repository interfaces (ports), domain-specific exceptions, domain enums | Infrastructure implementations, external client implementations |
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
Does the class depend on domain entities or domain-specific logic?
├── YES → module-core-domain
│   ├── Is it a repository interface? → domain/repository/
│   ├── Is it a service? → domain/service/
│   └── Is it an entity? → domain/model/
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

## Repository Naming Convention

| Layer | Pattern | Example |
|-------|---------|---------|
| **module-core-domain** | `{Entity}RepositoryJpa` | `CatalogTableRepositoryJpa` |
| **module-core-domain** | `{Entity}RepositoryDsl` | `CatalogTableRepositoryDsl` |
| **module-core-infra** | `{Entity}RepositoryJpaImpl` | `CatalogTableRepositoryJpaImpl` |
| **module-core-infra** | `{Entity}RepositoryDslImpl` | `CatalogTableRepositoryDslImpl` |

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

## Entity Relationship Rules

### Forbidden Annotations

```kotlin
// ❌ ABSOLUTELY FORBIDDEN - Never use these in entities
@OneToMany
@ManyToOne
@OneToOne
@ManyToMany
```

### Correct vs Wrong

```kotlin
// ❌ WRONG: Entity with JPA relationships
@Entity
class OrderEntity(
    @Id val id: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: UserEntity,  // ❌ FORBIDDEN
)

// ✅ CORRECT: Entity with FK as simple field
@Entity
class OrderEntity(
    @Id val id: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,  // ✅ Store FK as simple field
)
```

### Why No JPA Relationships?

| Reason | Benefit |
|--------|---------|
| N+1 Query Prevention | No unpredictable lazy loading queries |
| Explicit Data Access | QueryDSL makes fetching visible and controllable |
| Simpler Testing | No cascade/orphan removal complexity |
| Clear Boundaries | Services control aggregation, not entities |

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

### Controller Test Pattern

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@WithMockUser(username = "testuser", roles = ["USER"])
class MyControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jsonMapper: JsonMapper  // Jackson 3, NOT ObjectMapper

    @MockkBean(relaxed = true)
    private lateinit var myService: MyService
}
```

### Service Test Pattern

```kotlin
class MyServiceTest : DescribeSpec({
    val repository = mockk<MyRepositoryJpa>()
    val service = MyService(repository)

    describe("getItem") {
        context("when item exists") {
            it("should return item") {
                every { repository.findById(1L) } returns testItem
                val result = service.getItem(GetItemQuery(id = 1L))
                result shouldNotBe null
            }
        }
    }
})
```

---

## Dependency Versions

> **Critical for Spring Boot 4.x compatibility**

```kotlin
// build.gradle.kts - REQUIRED versions
ext {
    set("kotestVersion", "5.9.1")
    set("mockkVersion", "1.13.12")
    set("springMockkVersion", "5.0.1")      // NOT 4.x - required for Spring Boot 4
    set("testcontainersVersion", "1.19.3")
    set("restAssuredVersion", "5.4.0")
    set("springdocVersion", "3.0.0")        // NOT 2.x - required for Spring Boot 4
}
```

### Compatibility Matrix

| Library | Minimum Version | Notes |
|---------|-----------------|-------|
| springmockk | 5.0.1 | Spring Boot 4 support |
| springdoc-openapi | 3.0.0 | Spring Boot 4 + jakarta.* |
| MockK | 1.13.12 | Kotlin 2.x support |
| Kotest | 5.9.1 | Kotlin 2.x support |
| Testcontainers | 1.19.3 | Stable with JDK 24 |

### Critical Imports (Spring Boot 4.x)

```kotlin
// Jackson 3 (NOT Jackson 2)
import tools.jackson.databind.json.JsonMapper

// Web MVC Test (NEW package)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc

// MockK with Spring
import com.ninjasquad.springmockk.MockkBean
```

---

## New Feature Checklist

### Adding a New Entity

- [ ] Create `{Entity}Entity.kt` in `module-core-domain/model/{feature}/`
- [ ] Create `{Entity}RepositoryJpa.kt` interface in `module-core-domain/repository/`
- [ ] Create `{Entity}RepositoryDsl.kt` interface (if complex queries needed)
- [ ] Create `{Entity}RepositoryJpaImpl.kt` interface in `module-core-infra/repository/`
- [ ] Add QueryDSL Q-class generation (kapt)

### Adding a New API Endpoint

- [ ] Create `{Feature}Controller.kt` in `module-server-api/controller/`
- [ ] Create `{Feature}Service.kt` in `module-core-domain/service/`
- [ ] Create DTOs: `{Feature}Request.kt`, `{Feature}Response.kt`
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
