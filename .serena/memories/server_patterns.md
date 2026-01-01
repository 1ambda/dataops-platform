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

## 6. Essential Commands

```bash
./gradlew clean build       # Build and test
./gradlew bootRun           # Run locally (port 8080)
./gradlew ktlintFormat      # Format code
./gradlew generateQueryDsl  # Generate QueryDSL
```
