# Basecamp Server Development Patterns (Quick Reference)

> Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture

## 1. Repository Pattern (Critical)

### Domain Layer (Ports)
```kotlin
// module-core-domain/repository/
interface UserRepositoryJpa {
    fun save(user: UserEntity): UserEntity
    fun findById(id: Long): UserEntity?
}
interface UserRepositoryDsl {
    fun findByFilters(query: UserQuery): List<UserEntity>
}
```

### Infrastructure Layer (Adapters)
```kotlin
// module-core-infra/repository/
@Repository("userRepositoryJpa")
class UserRepositoryJpaImpl(
    private val springData: UserRepositoryJpaSpringData,
) : UserRepositoryJpa {
    override fun save(user: UserEntity) = springData.save(user)
    override fun findById(id: Long) = springData.findById(id).orElse(null)
}
```

## 2. Service Layer (Concrete Classes Only)

```kotlin
@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,
) {
    @Transactional
    fun create(command: CreatePipelineCommand): PipelineDto { ... }
    fun findById(query: GetPipelineQuery): PipelineDto? { ... }
}
```

## 3. Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| JPA Entities | `*Entity` | `UserEntity` |
| API DTOs | `*Dto` | `UserDto` |
| Repository (CRUD) | `*RepositoryJpa` | `UserRepositoryJpa` |
| Repository (Query) | `*RepositoryDsl` | `UserRepositoryDsl` |
| Repository Impl | `*RepositoryJpaImpl` | `UserRepositoryJpaImpl` |

## 4. Data Ownership Patterns (ASK IF UNCLEAR)

| Scenario | Pattern | Example |
|----------|---------|---------|
| **Self-managed** | JPA Entity + RepositoryJpa/Dsl | `CatalogTableEntity` |
| **External API** | External Client + Domain Models | `BigQueryClient` |

⚠️ Feature Spec이 두 패턴 모두 언급하면 **반드시 사용자에게 확인!**

## 5. Implementation Order

1. Domain Entity (`module-core-domain/model/`)
2. Domain Repository Interfaces (`module-core-domain/repository/`)
3. Infrastructure Implementations (`module-core-infra/repository/`)
4. Domain Service (`module-core-domain/service/`)
5. API Controller (`module-server-api/controller/`)

## 5. Anti-Patterns (CRITICAL)

- ❌ **Repository without Jpa/Dsl suffix** - `UserRepository` 금지, `UserRepositoryJpa` 사용!
- ❌ **Impl without Jpa/Dsl suffix** - `UserRepositoryImpl` 금지, `UserRepositoryJpaImpl` 사용!
- ❌ Service interfaces (use concrete classes)
- ❌ Exposing entities from API (use DTOs)
- ❌ Field injection (use constructor)
- ❌ Missing `@Repository("beanName")`
- ❌ Separate SpringData interface (`*RepositoryJpaSpringData` 금지)

## 6. MCP Query Optimization (CRITICAL)

### Token-Efficient Patterns

| Task | GOOD | BAD |
|------|------|-----|
| List controllers | `list_dir(controller/)` | `search_for_pattern("@RestController")` |
| Find API paths | `find_symbol("Controller", depth=1)` | `search_for_pattern("@RequestMapping")` |
| Get method signature | `find_symbol("Service/method", include_body=False)` | `search_for_pattern("fun methodName")` |
| Get implementation | `find_symbol("Class/method", include_body=True)` | Read full file |

### Context Settings (ALWAYS minimize)

```python
# ALWAYS use minimal context
search_for_pattern(
    ...,
    context_lines_before=0,  # Default: 0
    context_lines_after=0,   # Default: 0
    max_answer_chars=5000,   # Limit output
)

# Only add context when SPECIFICALLY needed
search_for_pattern(
    ...,
    context_lines_before=1,  # Just the annotation
    context_lines_after=2,   # Just the class signature
)
```

### Progressive Disclosure (MANDATORY)

```
Level 1: list_dir() → file names only (~200 tokens)
Level 2: get_symbols_overview(file) → structure only (~300 tokens)
Level 3: find_symbol(depth=1, include_body=False) → signatures (~400 tokens)
Level 4: find_symbol(include_body=True) → specific body (~500 tokens)
Level 5: Read(file) → LAST RESORT (~5000+ tokens)
```

## 7. Essential Commands

```bash
./gradlew clean build       # Build and test
./gradlew bootRun           # Run locally (port 8080)
./gradlew ktlintFormat      # Format code
./gradlew generateQueryDsl  # Generate QueryDSL
```
