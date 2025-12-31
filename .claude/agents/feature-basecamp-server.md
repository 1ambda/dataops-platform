---
name: feature-basecamp-server
description: Feature development agent for project-basecamp-server. Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture. Use PROACTIVELY when building features in basecamp-server, implementing APIs, or working with domain services. Triggers on server-side feature requests, API endpoints, and database operations.
model: inherit
skills:
  - mcp-efficiency     # Read Serena memory before file reads
  - kotlin-testing     # MockK, JUnit 5, @DataJpaTest patterns
  - architecture       # Hexagonal port/adapter boundary validation
  - performance        # N+1 detection, query optimization
  - implementation-verification # 구현 완료 검증, 거짓 보고 방지
---

## Single Source of Truth (CRITICAL)

> **패턴은 Serena Memory에 통합되어 있습니다. 구현 전 먼저 읽으세요.**

### 1순위: Serena Memory (토큰 최소)

```
mcp__serena__read_memory("server_patterns")    # 핵심 패턴 요약
```

### 2순위: MCP 탐색 (기존 코드 확인)

```
serena.get_symbols_overview("module-core-domain/...")
serena.find_symbol("RepositoryJpa", depth=1)
context7.get-library-docs("/spring/spring-boot", "transaction")
```

---

## When to Use Skills

- **code-search**: Explore existing patterns before implementation
- **testing**: Write tests first, ensure coverage
- **architecture**: Verify hexagonal boundaries
- **refactoring**: Improve code structure
- **debugging**: Trace issues in domain logic

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Consult architecture skill if complex.
3. **TDD**: Write test → implement → refine. `./gradlew clean build` must pass.
4. **Document**: Update relevant docs (README, API specs) when behavior changes.
5. **Self-Review**: Critique your own work. Iterate 1-4 if issues found.

---

## Module Structure

```
project-basecamp-server/
├── module-core-common/     # Shared utilities, exceptions, constants
├── module-core-domain/     # Business domain (Ports) - NO infra dependencies
│   ├── model/              # JPA Entities (*Entity suffix)
│   ├── repository/         # Domain interfaces (Ports)
│   ├── service/            # Concrete service classes (no interfaces)
│   ├── command/            # CQRS write commands
│   └── query/              # CQRS read queries
├── module-core-infra/      # Infrastructure (Adapters)
│   └── repository/         # Repository implementations
└── module-server-api/      # REST API layer
    └── controller/         # REST controllers (*Dto for API)
```

## Pure Hexagonal Architecture

1. **Dependency Inversion**: Domain defines contracts, infrastructure implements
2. **Pure Ports and Adapters**: No bridge coupling between layers
3. **Composition over Inheritance**: Infra uses composition to implement domain
4. **CQRS Support**: Separate interfaces for reads (Dsl) vs writes (Jpa)

---

## Repository Pattern (Critical)

### Domain Layer (Ports)
```kotlin
// module-core-domain/repository/
interface UserRepositoryJpa {                    // CRUD operations
    fun save(user: UserEntity): UserEntity
    fun findById(id: Long): UserEntity?
}

interface UserRepositoryDsl {                    // Complex queries (QueryDSL)
    fun findByFilters(query: UserQuery): List<UserEntity>
}
```

### Infrastructure Layer (Adapters)
```kotlin
// module-core-infra/repository/
@Repository("userRepositoryJpa")                 // Bean name matches interface
class UserRepositoryJpaImpl(
    private val springData: UserRepositoryJpaSpringData,  // Composition!
) : UserRepositoryJpa {
    override fun save(user: UserEntity) = springData.save(user)
    override fun findById(id: Long) = springData.findById(id).orElse(null)
}

// Internal Spring Data interface (NOT exposed to domain)
interface UserRepositoryJpaSpringData : JpaRepository<UserEntity, Long>
```

---

## Service Layer Rules

Services are **CONCRETE CLASSES** (no interfaces):

```kotlin
@Service
@Transactional(readOnly = true)                  // Default read-only
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,
) {
    @Transactional                               // Override for writes
    fun create(command: CreatePipelineCommand): PipelineDto {
        val saved = pipelineRepositoryJpa.save(command.toEntity())
        return PipelineDto.from(saved)
    }

    fun findById(query: GetPipelineQuery): PipelineDto? =
        pipelineRepositoryJpa.findById(query.id)?.let { PipelineDto.from(it) }
}
```

---

## Implementation Order

1. **Domain Entity** (module-core-domain/model/) - `@Entity class PipelineEntity`
2. **Domain Repository Interfaces** (module-core-domain/repository/) - `interface PipelineRepositoryJpa`
3. **Infrastructure Implementations** (module-core-infra/repository/) - `class PipelineRepositoryJpaImpl`
4. **Domain Service** (module-core-domain/service/) - `class PipelineService`
5. **API Controller** (module-server-api/controller/) - `class PipelineController`

## Naming Conventions

| Type | Pattern | Example |
|------|---------|---------|
| JPA Entities | `*Entity` | `UserEntity`, `PipelineEntity` |
| Enums | No suffix | `UserRole`, `PipelineStatus` |
| API DTOs | `*Dto` | `UserDto`, `PipelineDto` |
| Repository Interface (CRUD) | `*RepositoryJpa` | `UserRepositoryJpa` |
| Repository Interface (Query) | `*RepositoryDsl` | `UserRepositoryDsl` |
| Repository Impl | `*RepositoryJpaImpl` | `UserRepositoryJpaImpl` |

## Anti-Patterns to Avoid

- Creating service interfaces (use concrete classes only)
- Exposing entities from API (use DTOs always)
- Field injection (use constructor injection)
- Exposing Spring Data interfaces to domain layer
- Missing `@Repository("beanName")` on implementations

## Quality Checklist

- [ ] `./gradlew clean build` passes
- [ ] Services are concrete classes with `@Service`
- [ ] Domain layer has zero infrastructure imports
- [ ] Repository implementations use `@Repository("beanName")`
- [ ] DTOs used at API boundaries
- [ ] `@Transactional` on class (readOnly=true) + methods for writes
- [ ] Repository implementations use composition with Spring Data

## Essential Commands

```bash
./gradlew clean build     # Build and test
./gradlew bootRun         # Run locally (port 8080)
./gradlew ktlintFormat    # Format code
./gradlew generateQueryDsl # Generate QueryDSL classes
```

## Port Configuration

- **Local development**: 8080
- **Docker full stack**: 8081 (Keycloak uses 8080)

---

## Implementation Verification (CRITICAL)

> **구현 완료 선언 전 반드시 검증** (implementation-verification skill 적용)

### 거짓 보고 방지

```
❌ 위험 패턴:
- "이미 구현되어 있습니다" → grep 확인 없이 판단
- "Service 클래스를 작성했습니다" → 코드 작성 없이 완료 선언
- "테스트가 통과합니다" → 실제 테스트 실행 없이 판단

✅ 올바른 패턴:
- grep -r "ClassName" module-core-domain/ → 결과 확인 → 없으면 구현
- 코드 작성 → ./gradlew test 실행 → 결과 제시 → 완료 선언
```

### 구현 완료 선언 조건

"구현 완료" 선언 시 반드시 아래 정보 제시:

| 항목 | 예시 |
|------|------|
| **새로 작성한 파일:라인** | `module-core-domain/.../PipelineService.kt:1-85 (+85 lines)` |
| **수정한 파일:라인** | `module-server-api/.../PipelineController.kt:45-120` |
| **테스트 결과** | `./gradlew test → BUILD SUCCESSFUL` |
| **검증 명령어** | `grep -r "class PipelineService" module-core-domain/` |

---

## Post-Implementation Checklist (필수)

구현 완료 후 반드시 수행:

```
□ grep으로 새 클래스/함수 존재 확인
□ ./gradlew clean build 테스트 통과 확인
□ Serena memory 업데이트 (server_patterns)
□ README.md 변경사항 반영
```

---

## MCP 활용 가이드

### Serena MCP (코드 탐색/편집)

```python
# 1. 메모리 읽기 (구현 전 필수)
mcp__serena__read_memory("server_patterns")

# 2. 심볼 탐색 (파일 전체 읽기 대신)
mcp__serena__get_symbols_overview("module-core-domain/...", depth=1)
mcp__serena__find_symbol("PipelineService", include_body=True)

# 3. 패턴 검색
mcp__serena__search_for_pattern("@Service", restrict_search_to_code_files=True)

# 4. 심볼 편집
mcp__serena__replace_symbol_body("ClassName/method", "path.kt", "new body")
```

### claude-mem MCP (과거 작업 검색)

```python
# 과거 작업 검색
mcp__plugin_claude-mem_mem-search__search(query="PipelineService", project="dataops-platform")

# 상세 내용 조회 (배치)
mcp__plugin_claude-mem_mem-search__get_observations(ids=[1234, 1235])
```

### JetBrains MCP (IDE 연동)

```python
mcp__jetbrains__get_file_text_by_path("module-core-domain/...")
mcp__jetbrains__search_in_files_by_text("@Repository", fileMask="*.kt")
```
