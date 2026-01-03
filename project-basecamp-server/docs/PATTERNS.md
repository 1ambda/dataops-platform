# Spring Boot 4 + Kotlin Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.

---

## 🚨 CRITICAL: Module Placement Rules (MUST READ)

**Before creating ANY new class, verify which module it belongs to:**

| Module | Purpose | What Goes Here | What Does NOT Go Here |
|--------|---------|----------------|----------------------|
| **module-core-common** | Shared utilities, no domain dependencies | Base exceptions, common enums, utilities, constants, shared DTOs | Domain entities, domain-specific exceptions |
| **module-core-domain** | Domain models & business logic | JPA entities, domain services, repository interfaces (ports), domain-specific exceptions, domain enums | Infrastructure implementations, external client implementations |
| **module-core-infra** | Infrastructure implementations | Repository implementations (adapters), external API clients, infrastructure exceptions | Domain entities, controllers, API DTOs |
| **module-server-api** | REST API layer | Controllers, API request/response DTOs, mappers, API configuration | Domain services, entities, repository implementations |

### Exception Placement Rules

```kotlin
// module-core-common: Base exceptions and shared exceptions
// - Has NO dependencies on domain entities
abstract class BusinessException(...)
class ResourceNotFoundException(...)      // Generic, reusable
class ExternalSystemException(...)        // Generic external system error

// module-core-infra: Infrastructure-specific exceptions
// - For external system integrations (Airflow, BigQuery, etc.)
// - Extends BusinessException from common
class AirflowConnectionException(...)     // External system specific
class WorkflowStorageException(...)       // External storage specific
class BigQueryExecutionException(...)     // External query engine specific

// module-core-domain: Domain-specific exceptions
// - Only for exceptions tied to domain concepts/entities
class MetricNotFoundException(...)        // Tied to MetricEntity
class DatasetValidationException(...)     // Tied to Dataset domain rules
```

### Quick Decision Tree for Module Placement

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

## 🚨 CRITICAL: Repository Naming Convention (MUST READ)

**모든 Repository 클래스/인터페이스는 반드시 `Jpa` 또는 `Dsl` 접미사를 포함해야 합니다:**

| Layer | Pattern | Example |
|-------|---------|---------|
| **module-core-domain** | `{Entity}RepositoryJpa` | `CatalogTableRepositoryJpa` |
| **module-core-domain** | `{Entity}RepositoryDsl` | `CatalogTableRepositoryDsl` |
| **module-core-infra** | `{Entity}RepositoryJpaImpl` | `CatalogTableRepositoryJpaImpl` |
| **module-core-infra** | `{Entity}RepositoryDslImpl` | `CatalogTableRepositoryDslImpl` |

**❌ 절대 금지 (이 패턴은 거부됩니다):**
```kotlin
interface SampleQueryRepository      // ❌ Jpa/Dsl 없음 - 금지!
class SampleQueryRepositoryImpl      // ❌ Jpa/Dsl 없음 - 금지!
interface ItemRepositoryJpaSpringData // ❌ SpringData 별도 인터페이스 - 금지!
```

**✅ 올바른 패턴:**
```kotlin
// Domain (module-core-domain/repository/)
interface SampleQueryRepositoryJpa { ... }   // CRUD
interface SampleQueryRepositoryDsl { ... }   // Complex queries

// Infra (module-core-infra/repository/)
@Repository("sampleQueryRepositoryJpa")
interface SampleQueryRepositoryJpaImpl : SampleQueryRepositoryJpa, JpaRepository<...>

@Repository("sampleQueryRepositoryDsl")
class SampleQueryRepositoryDslImpl : SampleQueryRepositoryDsl { ... }
```

---

## 🚨 CRITICAL: Entity Relationship Rules (NO JPA Associations)

**Entities must NOT use JPA relationship annotations.** This is a fundamental design decision for maintainability and performance.

### Forbidden Annotations

```kotlin
// ❌ ABSOLUTELY FORBIDDEN - Never use these in entities
@OneToMany
@ManyToOne
@OneToOne
@ManyToMany
```

### Why No JPA Relationships?

1. **N+1 Query Prevention**: Lazy loading causes unpredictable query counts
2. **Explicit Data Access**: QueryDSL makes data fetching visible and controllable
3. **Simpler Testing**: No cascade/orphan removal complexity
4. **Clear Boundaries**: Services control aggregation, not entities

### Correct vs Wrong Patterns

```kotlin
// ❌ WRONG: Entity with JPA relationships
@Entity
class OrderEntity(
    @Id val id: Long,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    val user: UserEntity,  // ❌ FORBIDDEN

    @OneToMany(mappedBy = "order", cascade = [CascadeType.ALL])
    val items: List<OrderItemEntity> = emptyList(),  // ❌ FORBIDDEN
)

// ✅ CORRECT: Entity with only direct fields (foreign key as ID)
@Entity
class OrderEntity(
    @Id val id: Long,
    @Column(name = "user_id", nullable = false)
    val userId: Long,  // ✅ Store FK as simple field
    // items are fetched via QueryDSL when needed
)
```

### JPA vs QueryDSL Decision Guide

| Scenario | Use | Example |
|----------|-----|---------|
| Create/Update/Delete single entity | JPA | `repository.save(entity)` |
| Find by 1-2 simple fields | JPA | `findById()`, `findByName()` |
| Find by 3+ conditions or dynamic filters | QueryDSL | Variable WHERE clauses |
| Fetch related entities (aggregation) | QueryDSL | Order + OrderItems |
| Projection with joined data | QueryDSL | User with order count |
| Paginated list with sorting | QueryDSL | Complex list queries |

### The "3-Word Rule" for JPA Methods

If a JPA method name exceeds **3 words** (counting `And`/`Or` separators), switch to QueryDSL:

```kotlin
// ✅ OK for JPA (1-2 conditions)
fun findByName(name: String): Entity?
fun findByStatusAndType(status: Status, type: Type): List<Entity>

// ❌ TOO COMPLEX for JPA - Use QueryDSL instead
fun findByNameAndStatusAndTypeAndCreatedAtAfter(...)  // 4+ conditions
fun findByOwnerContainingOrDescriptionContaining(...)  // Complex OR logic
```

### Aggregation Root Pattern (QueryDSL)

When fetching related entities, use QueryDSL projections:

```kotlin
// Domain Repository Interface
interface OrderRepositoryDsl {
    fun findOrderWithItems(orderId: Long): OrderAggregation?
    fun findOrdersByUserWithItemCount(userId: Long): List<OrderSummary>
}

// Infrastructure Implementation
@Repository("orderRepositoryDsl")
class OrderRepositoryDslImpl(
    private val entityManager: EntityManager,
) : OrderRepositoryDsl {
    private val queryFactory = JPAQueryFactory(entityManager)
    private val order = QOrderEntity.orderEntity
    private val item = QOrderItemEntity.orderItemEntity

    override fun findOrderWithItems(orderId: Long): OrderAggregation? {
        val orderEntity = queryFactory
            .selectFrom(order)
            .where(order.id.eq(orderId))
            .fetchOne() ?: return null

        val items = queryFactory
            .selectFrom(item)
            .where(item.orderId.eq(orderId))
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

### Quick Reference Table

| Task | Layer | Pattern |
|------|-------|---------|
| Simple CRUD | JPA Repository | `save()`, `findById()`, `delete()` |
| 1-2 field lookup | JPA Repository | `findByName()`, `findByStatus()` |
| 3+ conditions | QueryDSL Repository | Dynamic WHERE with BooleanBuilder |
| Parent + Children fetch | QueryDSL Repository | Separate queries, aggregate in code |
| Complex projections | QueryDSL Repository | DTO projections with Projections.constructor() |
| Batch updates | JPA Repository | `saveAll()` |

---

## 🎯 Data Ownership Patterns (ASK IF UNCLEAR)

Feature 구현 전 **반드시 데이터 소유권을 확인**하세요:

| Scenario | Pattern | When to Use | Example |
|----------|---------|-------------|---------|
| **Self-managed** | JPA Entity + RepositoryJpa/Dsl | 데이터가 우리 DB에 저장됨 | `CatalogTableEntity`, `DatasetEntity` |
| **External API** | External Client + Domain Models | 외부 시스템에서 실시간 조회 | `BigQueryClient`, `TrinoClient` |
| **Hybrid** | JPA Entity (캐시) + External Client | 외부 데이터를 로컬에 캐싱 | 메타데이터 캐시 |

**⚠️ Feature Spec이 두 패턴을 모두 언급하면, 반드시 사용자에게 확인하세요!**

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

## Deep Dive Documentation

For comprehensive implementation guides, see:

| Topic | Document | Description |
|-------|----------|-------------|
| **Architecture** | [architecture.md](../../../docs/architecture.md) | System design, policies, and architectural decisions (platform-level) |
| **Implementation** | [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) | Service, repository, controller, DTO, and entity patterns |
| **Error Handling** | [ERROR_HANDLING.md](./ERROR_HANDLING.md) | Error codes, exception hierarchy, and response format |
| **Testing** | [TESTING.md](./TESTING.md) | Comprehensive testing guide with troubleshooting |

---

## Quick Reference

| Task | Reference File | Key Pattern |
|------|----------------|-------------|
| Controller test | `controller/PipelineControllerTest.kt` | @SpringBootTest + @AutoConfigureMockMvc |
| Service test | `domain/service/UserServiceTest.kt` | @MockkBean + Kotest |
| Repository test | `infra/repository/*Test.kt` | @DataJpaTest + TestEntityManager |
| Entity model | `domain/model/pipeline/PipelineEntity.kt` | JPA Entity + QueryDSL |
| DTO mapping | `mapper/PipelineMapper.kt` | Manual mapping functions |
| API endpoint | `controller/PipelineController.kt` | @RestController + validation |

---

## 1. Controller Test Pattern

### Required Annotations

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

### Required Imports (Spring Boot 4.x)

```kotlin
// Jackson 3 (NOT Jackson 2)
import tools.jackson.databind.json.JsonMapper

// Web MVC Test (NEW package in Spring Boot 4)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc

// MockK with Spring (requires springmockk 5.0.1+)
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify

// Parallel execution control
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
```

### Test Template

```kotlin
@Nested
@DisplayName("GET /api/v1/items")
inner class GetItems {
    @Test
    @DisplayName("아이템 목록을 조회할 수 있다")
    fun `should return item list`() {
        // Given
        every { itemService.getItems(any()) } returns PageImpl(listOf(testItem))
        every { itemMapper.toResponse(testItem) } returns testResponse

        // When & Then
        mockMvc
            .perform(get("/api/v1/items"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content[0].name").value("test-item"))

        verify { itemService.getItems(any()) }
    }
}

@Nested
@DisplayName("POST /api/v1/items")
inner class CreateItem {
    @Test
    @DisplayName("아이템을 생성할 수 있다")
    fun `should create item`() {
        // Given
        val request = CreateItemRequest(name = "new-item")
        every { itemMapper.toCommand(request) } returns CreateItemCommand(name = "new-item")
        every { itemService.createItem(any()) } returns testItem
        every { itemMapper.toResponse(testItem) } returns testResponse

        // When & Then
        mockMvc
            .perform(
                post("/api/v1/items")
                    .with(csrf())  // Required for POST/PUT/DELETE
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(jsonMapper.writeValueAsString(request))
            )
            .andExpect(status().isCreated)

        verify { itemService.createItem(any()) }
    }
}
```

---

## 2. Service Test Pattern

### Template

```kotlin
class MyServiceTest : DescribeSpec({
    val repository = mockk<MyRepositoryJpa>()
    val service = MyService(repository)

    describe("getItem") {
        context("존재하는 아이템 조회 시") {
            it("아이템을 반환해야 한다") {
                // Given
                val item = MyEntity(id = 1L, name = "test")
                every { repository.findById(1L) } returns item

                // When
                val result = service.getItem(GetItemQuery(id = 1L))

                // Then
                result shouldNotBe null
                result!!.name shouldBe "test"
            }
        }

        context("존재하지 않는 아이템 조회 시") {
            it("null을 반환해야 한다") {
                every { repository.findById(999L) } returns null

                val result = service.getItem(GetItemQuery(id = 999L))

                result shouldBe null
            }
        }
    }
})
```

---

## 3. Repository Pattern

### Domain Interface (Port)

```kotlin
// module-core-domain/repository/ItemRepositoryJpa.kt
interface ItemRepositoryJpa {
    fun save(item: ItemEntity): ItemEntity
    fun deleteById(id: Long)
    fun existsById(id: Long): Boolean
    fun findAll(): List<ItemEntity>
    // Domain-specific queries
    fun findByName(name: String): ItemEntity?
}

// module-core-domain/repository/ItemRepositoryDsl.kt
interface ItemRepositoryDsl {
    fun findByConditions(query: GetItemsQuery): Page<ItemEntity>
}
```

### Infrastructure Implementation (Adapter) - Simplified Pattern (Recommended)

> **Note:** Use this simplified pattern that combines domain interface and JpaRepository into one interface.

```kotlin
// module-core-infra/repository/ItemRepositoryJpaImpl.kt
@Repository("itemRepositoryJpa")
interface ItemRepositoryJpaImpl :
    ItemRepositoryJpa,
    JpaRepository<ItemEntity, Long> {

    // Domain-specific queries (Spring Data JPA auto-implements)
    override fun findByName(name: String): ItemEntity?
}
```

This pattern:
- ✅ Eliminates the need for a separate `*SpringData` interface
- ✅ Reduces boilerplate code
- ✅ Leverages Spring Data JPA's auto-implementation
- ✅ Same approach used in `ResourceRepositoryJpaImpl`

### ⚠️ Critical Repository Pattern Guidelines

**DO NOT create `*RepositoryJpaSpringData` interfaces!** This is the most common mistake:

```kotlin
// ❌ WRONG - Do NOT create this
interface ItemRepositoryJpaSpringData : JpaRepository<ItemEntity, Long>

// ❌ WRONG - Do NOT use composition pattern
@Repository
class ItemRepositoryJpaImpl(
    private val springDataRepository: ItemRepositoryJpaSpringData
) : ItemRepositoryJpa
```

**Instead, use the Simplified Pattern:**

```kotlin
// ✅ CORRECT - Single interface extending both
@Repository("itemRepositoryJpa")
interface ItemRepositoryJpaImpl :
    ItemRepositoryJpa,           // Domain interface
    JpaRepository<ItemEntity, Long> {  // Spring Data JPA
}
```

**Key Rules:**
1. **No separate SpringData interfaces** - Extends JpaRepository directly
2. **Complex business logic goes to Service layer** - Repository provides only primitive operations
3. **Domain methods with LocalDateTime.now()** → Move to Service
4. **Custom @Query methods are OK** - Keep them simple and focused

### Infrastructure Implementation (Adapter) - Composition Pattern (Legacy)

```kotlin
// module-core-infra/repository/ItemRepositoryJpaImpl.kt
@Repository("itemRepositoryJpa")
class ItemRepositoryJpaImpl(
    private val springDataRepository: ItemRepositoryJpaSpringData,
) : ItemRepositoryJpa {
    override fun save(item: ItemEntity) = springDataRepository.save(item)
    override fun findById(id: Long) = springDataRepository.findById(id).orElse(null)
    override fun findAll() = springDataRepository.findAll()
}

// module-core-infra/repository/ItemRepositoryJpaSpringData.kt
interface ItemRepositoryJpaSpringData : JpaRepository<ItemEntity, Long>
```

---

## 4. Controller Pattern

### Template

```kotlin
@RestController
@RequestMapping("\${CommonConstants.Api.V1_PATH}/items")
@Validated
@Tag(name = "Item", description = "아이템 관리 API")
class ItemController(
    private val itemService: ItemService,
    private val itemMapper: ItemMapper,
) {
    @Operation(summary = "아이템 목록 조회")
    @GetMapping
    fun getItems(
        @RequestParam(required = false) status: ItemStatus?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ApiResponse<PagedResponse<ItemResponse>>> {
        val query = itemMapper.toQuery(status, PageRequest.of(page, size))
        val items = itemService.getItems(query)
        val response = items.map { itemMapper.toResponse(it) }
        return ResponseEntity.ok(ApiResponse.success(PagedResponse.from(response)))
    }

    @Operation(summary = "아이템 생성")
    @PostMapping
    fun createItem(
        @Valid @RequestBody request: CreateItemRequest,
    ): ResponseEntity<ApiResponse<ItemResponse>> {
        val command = itemMapper.toCommand(request)
        val item = itemService.createItem(command)
        val response = itemMapper.toResponse(item)
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response))
    }
}
```

---

## 5. Dependency Versions (Critical)

```kotlin
// build.gradle.kts - These versions are REQUIRED for Spring Boot 4.x
ext {
    set("springMockkVersion", "5.0.1")   // NOT 4.x
    set("springdocVersion", "3.0.0")      // NOT 2.x
}
```

---

## 6. New Feature Checklist

### Adding a New Entity

- [ ] Create `{Entity}Entity.kt` in `module-core-domain/model/{feature}/`
- [ ] Create `{Entity}RepositoryJpa.kt` interface in `module-core-domain/repository/`
- [ ] Create `{Entity}RepositoryDsl.kt` interface (if complex queries needed)
- [ ] Create `{Entity}RepositoryJpaImpl.kt` interface in `module-core-infra/repository/` (extends both domain interface and JpaRepository)
- [ ] Add QueryDSL Q-class generation (kapt)

### Adding a New API Endpoint

- [ ] Create `{Feature}Controller.kt` in `module-server-api/controller/`
- [ ] Create `{Feature}Service.kt` in `module-core-domain/service/`
- [ ] Create DTOs: `{Feature}Request.kt`, `{Feature}Response.kt`
- [ ] Create `{Feature}Mapper.kt` for DTO ↔ Domain conversion
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

## See Also

- [docs/TESTING.md](./TESTING.md) - Detailed testing guide with troubleshooting
- [README.md](../README.md) - Project overview and quick start
