# Spring Boot 4 + Kotlin Development Patterns

> **Purpose:** Accelerate new feature development by providing reference patterns for common tasks.

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
