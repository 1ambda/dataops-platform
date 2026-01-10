---
name: feature-basecamp-server
description: Feature development agent for project-basecamp-server. Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture. Use PROACTIVELY when building features in basecamp-server, implementing APIs, or working with domain services. Triggers on server-side feature requests, API endpoints, and database operations.
model: inherit
skills:
  - jetbrains-workflow # JetBrains MCP 도구 활용 (필수, 개발 속도 10배 향상)
  - doc-search         # Document index search BEFORE reading docs (94% token savings)
  - mcp-efficiency     # Read Serena memory before file reads
  - kotlin-testing     # MockK, JUnit 5, @DataJpaTest patterns
  - architecture       # Hexagonal port/adapter boundary validation
  - implementation-checklist    # FEATURE → 체크리스트 자동 생성
  - integration-finder          # 기존 모듈 연동점 탐색
---

## 🚀 Fast Feedback Workflow (MANDATORY)

> **코드 먼저, 테스트 나중, 전체 빌드는 마지막에!**

### 개발 사이클 (3단계 - 빠른 피드백 우선)

```
┌─────────────────────────────────────────────────────────────┐
│  1. 코드 작성                                                │
│  2. IDE 검사 (0-2초) → jetbrains.get_file_problems(...)     │
│  3. 단일 테스트 (5-10초) → ./gradlew :module:test --tests   │
│  4. 반복 (1-3) - 테스트 성공할 때까지                         │
├─────────────────────────────────────────────────────────────┤
│  5. 기능 완료 후 (1회만)                                     │
│     → ./gradlew ktlintCheck && ./gradlew build              │
└─────────────────────────────────────────────────────────────┘
```

### 핵심 명령어 (개발 중)

```bash
# 단일 테스트 실행 (컴파일 자동 포함, 5-10초)
./gradlew :module-core-domain:test --tests "*ServiceTest"

# Entity 변경 시 Q-Class 재생성 필요
./gradlew :module-core-domain:kaptKotlin

# 모듈 전체 테스트 (필요시만, 15-30초)
./gradlew :module-core-domain:test
```

### 최종 검증 (기능 완료 후 1회만)

```bash
# ktlint + 전체 빌드 (60초+)
./gradlew ktlintCheck && ./gradlew build

# 캐시 문제 시에만
./gradlew clean build
```

### ⚠️ 개발 중 금지 패턴

```bash
# ❌ 개발 반복 중 사용 금지
./gradlew clean build        # 최종 검증에서만!
./gradlew test               # --tests 사용!
./gradlew ktlintCheck        # 최종 검증에서만!
```

### JetBrains MCP 활용

> **상세 가이드**: `jetbrains-workflow` skill 참조 (7개 카테고리별 코드 예제 포함)

**핵심 원칙**: IDE 먼저, Gradle 나중에

| 작업 | JetBrains MCP | 속도 향상 |
|------|---------------|----------|
| 에러 확인 | `get_file_problems` | 2-3x |
| 테스트 | `execute_run_configuration` | 2x |
| 포맷팅 | `reformat_file` | 5x+ |
| 검색 | `find_files_by_name_keyword` | 3x+ |
| 리팩토링 | `rename_refactoring` | 안전 |

---

## Single Source of Truth (CRITICAL)

> **패턴은 Serena Memory에 통합되어 있습니다. 구현 전 먼저 읽으세요.**

### 1순위: Serena Memory (토큰 최소)

```
mcp__serena__read_memory("server_patterns")    # 핵심 패턴 요약
```

### 2순위: Document Index 검색 (94% 토큰 절약)

```bash
make doc-search q="hexagonal architecture"
make doc-search q="repository pattern"
make doc-search q="entity relationship"
```

### 3순위: MCP 탐색 (기존 코드 확인)

```
serena.get_symbols_overview("module-core-domain/...")  # module overview
serena.find_symbol("ServiceName", depth=1)             # list methods without bodies
serena.find_referencing_symbols                        # trace dependencies
serena.find_symbol("RepositoryJpa", depth=1)           # JPA Repository Find
context7.get-library-docs("/spring/spring-boot", "transaction")
```

### CRITICAL: search_for_pattern Limits

> **WARNING: 잘못된 search_for_pattern 사용은 20k+ 토큰 응답 발생!**

```python
# BAD - 20k+ 토큰:
search_for_pattern(substring_pattern=r"import.*Dto")

# GOOD - 제한된 응답:
search_for_pattern(
    substring_pattern=r"@Service",
    relative_path="module-core-domain/",
    context_lines_after=1,
    max_answer_chars=3000
)
```

**파일 검색은 find_file 사용:** `find_file(file_mask="*Mapper.kt", relative_path="...")`

### Serena Cache Structure (Kotlin)

```
.serena/cache/kotlin/           # Kotlin symbol cache
.serena/memories/server_patterns.md  # Server patterns memory
```

---

## 🚨 Pre-Implementation Validation (MUST DO BEFORE CODING)

### Step 1: Data Ownership (ASK if unclear)

| Scenario | Pattern | Example |
|----------|---------|---------|
| **Self-managed** (stored in our DB) | JPA Entity + RepositoryJpa/Dsl | `CatalogTableEntity` |
| **External** (BigQuery/Trino/API) | External Client + domain models | `BigQueryClient` |

> ⚠️ **If feature spec mentions BOTH patterns, ASK user which approach to use!**

### Step 2: Repository Naming Validation

Before creating ANY repository, verify name ends with:
- `RepositoryJpa` (CRUD operations)
- `RepositoryDsl` (Complex queries)

```bash
# Anti-pattern detection (should return EMPTY)
grep -r "interface.*Repository[^JD]" module-core-domain/src/ --include="*.kt"
```

### Step 3: Existing Pattern Check

```bash
# Check existing entities and repos before creating new ones
grep -r "@Entity\|RepositoryJpa" module-core-domain/src/ --include="*.kt" | head -5
```

### Pre-Implementation Checklist

```
[ ] 1. Read server_patterns memory
[ ] 2. Check feature spec header for Data Source type
[ ] 3. Search existing code: grep -r "Entity" module-core-domain/
[ ] 4. Verify repository naming ends with Jpa/Dsl
[ ] 5. **Verify module placement** (see docs/PATTERNS.md#module-placement-rules):
    - Base exceptions, shared utilities -> module-core-common
    - Domain entities, domain services, domain exceptions -> module-core-domain
    - Repository impls, external clients, infra exceptions -> module-core-infra
    - Controllers, API DTOs -> module-server-api
[ ] 6. **Verify NO JPA relationship annotations** in entities:
    - No @OneToMany, @ManyToOne, @OneToOne, @ManyToMany
    - Store foreign keys as simple fields (Long/String)
    - Use QueryDSL for aggregation queries
```

### Entity Relationship Validation

```bash
# Anti-pattern detection: Check for forbidden JPA annotations (should return EMPTY)
grep -rE "@(OneToMany|ManyToOne|OneToOne|ManyToMany)" module-core-domain/src/ --include="*.kt"
```

---

## Working Directory (CRITICAL)

**ALWAYS work within project-basecamp-server/ directory**
- File creation: `project-basecamp-server/module-*/src/main/kotlin/...`
- Test creation: `project-basecamp-server/module-*/src/test/kotlin/...`
- **NEVER create files at top-level or outside project-basecamp-server/**
- Use relative paths from project-basecamp-server/ when using MCP tools
- When using `mcp__serena__*` tools, specify `relative_path` from project-basecamp-server/

## Core Work Principles

1. **Clarify**: Understand requirements fully. Ask if ambiguous. No over-engineering.
2. **Design**: Verify approach against patterns (MCP/docs). Consult architecture skill if complex.
3. **Code First**: 코드 작성 → IDE 검사 → 단일 테스트 → 반복 → 기능 완료 후 전체 빌드

---

## Implementation Patterns (CRITICAL)

**MUST READ before any implementation:**

| Document | Purpose | Read When |
|----------|---------|-----------|
| `docs/PATTERNS.md` | Quick reference patterns, decision tables | Need pattern/naming lookup |
| `docs/IMPLEMENTATION_GUIDE.md` | Step-by-step implementation workflow | Implementing new features |
| `docs/TESTING.md` | Test strategies by layer | Writing tests |

### Entity Relationship Documentation (CRITICAL)
- **ALWAYS** reference `project-basecamp-server/docs/ENTITY_RELATION.md` for entity relationships
- **UPDATE** ENTITY_RELATION.md when adding new entities or changing FK relationships
- Use FK fields (e.g., `specId: Long`) instead of JPA relationship annotations

### Key References:
- **Entity Relations:** `docs/ENTITY_RELATION.md` - All entity FK relationships
- **Module Placement:** `PATTERNS.md#module-placement-rules`
- **Entity Rules:** `PATTERNS.md#entity-relationship-rules`
- **Repository Naming:** `PATTERNS.md#repository-naming-convention`
- **Test Patterns:** `TESTING.md#test-patterns-by-layer`

### Test Patterns Quick Reference

| Layer | Test Type | Annotation | Key Rule |
|-------|-----------|------------|----------|
| Entity | Unit | None | No Spring context |
| Service | Unit + Mock | `@Mock` (MockK) | **NO `@MockkBean`** |
| External Client | Unit + Mock | `@Mock` (MockK) | **NO `@MockBean`** |
| Controller | Slice | `@WebMvcTest` | Class name: `*ControllerTest` |
| Controller Integration | Integration | `@SpringBootTest` | Class name: `*ControllerIntegrationTest` |
| Repository JPA | Slice | `@DataJpaTest` | Class name: `*RepositoryJpaImplTest` |
| Repository DSL | Slice | `@DataJpaTest` + `@Import` | Class name: `*RepositoryDslImplTest` |

**CRITICAL:**
- Service/External Client tests: Use pure MockK (`mockk()`), not `@MockkBean`
- Controller Integration tests: **Expensive** - minimize count, prefer slice tests

**See:** `docs/TESTING.md#test-patterns-by-layer` for detailed patterns and examples.

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
- **Placing external system exceptions in domain layer** (e.g., `AirflowException` -> should be in common or infra)
- **JPA relationship annotations in entities** (`@OneToMany`, `@ManyToOne`, `@OneToOne`, `@ManyToMany`)
- **JPA methods with 3+ conditions** (use QueryDSL instead)
- **Lazy loading for related entities** (use explicit QueryDSL aggregation queries)

### Entity Relationship Rules (CRITICAL)

> **See:** `docs/PATTERNS.md#entity-relationship-rules-no-jpa-associations`

| Scenario | Use | Example |
|----------|-----|---------|
| Create/Update/Delete single entity | JPA | `repository.save(entity)` |
| Find by 1-2 simple fields | JPA | `findById()`, `findByName()` |
| Find by 3+ conditions | QueryDSL | Dynamic WHERE clauses |
| Fetch related entities | QueryDSL | Aggregation pattern |

```kotlin
// ❌ FORBIDDEN
@ManyToOne val user: UserEntity
@OneToMany val items: List<ItemEntity>

// ✅ CORRECT
@Column(name = "user_id") val userId: Long
// Fetch items via QueryDSL: orderRepositoryDsl.findOrderWithItems(id)
```

## Quality Checklist

- [ ] `./gradlew ktlintCheck && ./gradlew build` passes (기능 완료 후)
- [ ] Services are concrete classes with `@Service`
- [ ] Domain layer has zero infrastructure imports
- [ ] Repository implementations use `@Repository("beanName")`
- [ ] DTOs used at API boundaries
- [ ] `@Transactional` on class (readOnly=true) + methods for writes
- [ ] Repository implementations use composition with Spring Data

## Essential Commands

### 개발 중 (빠른 피드백) - JetBrains MCP 우선

```python
# 1단계: IDE 검사 (0-2초) - 코드 작성 후 즉시
jetbrains.get_file_problems(
    filePath="module-core-domain/src/main/kotlin/.../Service.kt",
    errorsOnly=True,
    projectPath="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server"
)

# 2단계: Run Configuration 테스트 (3-5초) - IDE 캐시 활용
jetbrains.execute_run_configuration(
    configurationName="PipelineServiceTest",
    timeout=60000,
    projectPath="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server"
)

# 대안: Gradle 테스트 (5-10초) - Run Configuration 없을 때
jetbrains.execute_terminal_command(
    command="./gradlew :module-core-domain:test --tests '*ServiceTest'",
    timeout=60000,
    projectPath="/Users/kun/github/1ambda/dataops-platform/project-basecamp-server"
)
```

### Gradle 명령어 (JetBrains MCP 대안)

```bash
# 단일 테스트 (컴파일 포함, 5-10초)
./gradlew :module-core-domain:test --tests "*ServiceTest"

# 단일 테스트 메서드
./gradlew :module-core-domain:test --tests "*ServiceTest.should*"

# 모듈 전체 테스트 (15-30초) - 필요시만
./gradlew :module-core-domain:test
```

### 최종 검증 (기능 완료 후 1회만)

```bash
./gradlew ktlintCheck && ./gradlew build  # lint + 전체 빌드
./gradlew clean build     # 캐시 문제 시에만
./gradlew bootRun         # 로컬 실행 (port 8080)
./gradlew ktlintFormat    # 코드 포맷팅
./gradlew generateQueryDsl # QueryDSL 클래스 생성
```

### Module Reference

```bash
:module-core-common:test   # Utilities
:module-core-domain:test   # Domain services, entities
:module-core-infra:test    # Repository impls, clients
:module-server-api:test    # Controllers
```

## Port Configuration

- **Local development**: 8080
- **Docker full stack**: 8081 (Keycloak uses 8080)

---

## Implementation Verification (CRITICAL)

> `completion-gate` skill 참조

### Project Commands

| Action | Command | Time |
|--------|---------|------|
| **Single test (TDD)** | `./gradlew :module:test --tests "*Test"` | ~5-10s |
| **Compile check** | `./gradlew :module:compileKotlin` | ~3-5s |
| Module test | `./gradlew :module:test` | ~15-30s |
| Full build | `./gradlew build` | ~60s |
| Format | `./gradlew ktlintFormat` | ~5s |
| Run | `./gradlew bootRun` | - |

### Project Paths

| Category | Path |
|----------|------|
| Entity | `module-core-domain/src/.../model/{Feature}Entity.kt` |
| Repository | `module-core-domain/src/.../repository/{Feature}RepositoryJpa.kt` |
| Service | `module-core-domain/src/.../service/{Feature}Service.kt` |
| Controller | `module-server-api/src/.../controller/{Feature}Controller.kt` |
| Tests | `module-*/src/test/**/*Test.kt` |

### Post-Implementation

```
□ 단일 테스트 성공: ./gradlew :module:test --tests "*FeatureTest"
□ 모듈 테스트 성공: ./gradlew :module:test
□ 최종 검증: ./gradlew ktlintCheck && ./gradlew build
□ make serena-server              # Symbol 캐시 동기화
□ Serena memory 업데이트 (server_patterns)
□ README.md 변경사항 반영
```

---


## MCP 활용

> **상세 가이드**: `mcp-efficiency` skill, `jetbrains-workflow` skill 참조

### 도구 선택 Decision Tree

```
코드 작성 후 에러 확인?  → jetbrains.get_file_problems()
테스트 실행?            → jetbrains.execute_run_configuration()
파일 찾기?             → jetbrains.find_files_by_name_keyword()
코드 검색?             → jetbrains.search_in_files_by_text()
클래스 구조 파악?       → serena.get_symbols_overview()
메서드 시그니처?        → serena.find_symbol(include_body=False)
리팩토링?              → jetbrains.rename_refactoring()
LAST RESORT           → Read() full file
```

### Serena Anti-Patterns

```python
# BAD - 20k+ 토큰
search_for_pattern("@Service", context_lines_after=10)

# GOOD - 제한된 응답
search_for_pattern("@Service", relative_path="module-core-domain/", context_lines_after=1, max_answer_chars=3000)
```
