---
name: feature-basecamp-server
description: Feature development agent for project-basecamp-server. Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture. Use PROACTIVELY when building features in basecamp-server, implementing APIs, or working with domain services. Triggers on server-side feature requests, API endpoints, and database operations.
model: inherit
skills:
  - doc-search         # Document index search BEFORE reading docs (94% token savings)
  - mcp-efficiency     # Read Serena memory before file reads
  - kotlin-testing     # MockK, JUnit 5, @DataJpaTest patterns
  - architecture       # Hexagonal port/adapter boundary validation
  - implementation-checklist    # FEATURE → 체크리스트 자동 생성
  - integration-finder          # 기존 모듈 연동점 탐색
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
3. **TDD**: Write test → implement → refine. `./gradlew clean build` must pass.

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

> `completion-gate` skill 참조

### Project Commands

| Action | Command |
|--------|---------|
| Build & Test | `./gradlew clean build` |
| Test Only | `./gradlew test` |
| Format | `./gradlew ktlintFormat` |
| Run | `./gradlew bootRun` |

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
□ ./gradlew clean build 테스트/빌드 통과 확인
□ make serena-server              # Symbol 캐시 동기화
□ Serena memory 업데이트 (server_patterns)
□ README.md 변경사항 반영
```

---


## MCP 활용 (Token Efficiency CRITICAL)

> **상세 가이드**: `mcp-efficiency` skill 참조

### MCP Query Anti-Patterns (AVOID)

```python
# BAD: Returns 20k+ tokens (entire controller bodies)
search_for_pattern("@RequestMapping.*", context_lines_after=10)

# BAD: Broad search without scope
search_for_pattern("@Service", restrict_search_to_code_files=True)

# BAD: Reading files before understanding structure
Read("controller/PipelineController.kt")  # 5000+ tokens
```

### Token-Efficient Patterns (USE)

```python
# GOOD: List files first (~200 tokens)
list_dir("module-server-api/src/.../controller", recursive=False)

# GOOD: Get structure without bodies (~300 tokens)
get_symbols_overview("module-server-api/.../PipelineController.kt")

# GOOD: Signatures only (~400 tokens)
find_symbol("PipelineController", depth=1, include_body=False)

# GOOD: Specific method body only when needed (~500 tokens)
find_symbol("PipelineController/createPipeline", include_body=True)

# GOOD: Minimal context for pattern search
search_for_pattern(
    "@RequestMapping",
    context_lines_before=0,
    context_lines_after=1,
    max_answer_chars=3000
)
```

### Decision Tree

```
Need file list?       → list_dir()
Need class structure? → get_symbols_overview()
Need method list?     → find_symbol(depth=1, include_body=False)
Need implementation?  → find_symbol(include_body=True) for SPECIFIC method
Need to find pattern? → search_for_pattern with context=0
LAST RESORT          → Read() full file
```
