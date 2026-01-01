---
name: feature-basecamp-server
description: Feature development agent for project-basecamp-server. Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture. Use PROACTIVELY when building features in basecamp-server, implementing APIs, or working with domain services. Triggers on server-side feature requests, API endpoints, and database operations.
model: inherit
skills:
  - mcp-efficiency     # Read Serena memory before file reads
  - kotlin-testing     # MockK, JUnit 5, @DataJpaTest patterns
  - architecture       # Hexagonal port/adapter boundary validation
  - completion-gate             # 완료 선언 Gate + 코드 존재 검증
  - implementation-checklist    # FEATURE → 체크리스트 자동 생성
  - dependency-coordination     # 크로스 Agent 의존성 추적
  - integration-finder          # 기존 모듈 연동점 탐색
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
[ ] 5. ASK user if spec mentions both JPA and external integration
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

## Implementation Patterns (CRITICAL)

- Read docs/IMPLEMENTATION_PATTERNS.md
- Read docs/PATTERNS.md
- Read docs/TESTINGS.md

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

> **Protocol**: `completion-gate` skill 참조
> **Gate**: `completion-gate` skill 참조

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
□ Serena memory 업데이트 (server_patterns)
□ README.md 변경사항 반영
```

---

## FEATURE → Implementation Workflow (CRITICAL)

> **Workflow**: `implementation-checklist` skill 참조
> **Gate**: `completion-gate` skill 참조

### 구현 순서

```
Entity → Repository → Service → Controller → Tests
```

### FEATURE 섹션별 검증

| FEATURE 섹션 | 필수 구현 | 검증 방법 |
|--------------|-----------|-----------|
| Domain Model | `*Entity` | `grep -r "class.*Entity" module-core-domain/` |
| Repository | `*RepositoryJpa` | `grep -r "interface.*Repository" module-core-domain/` |
| Service | `*Service` | `grep -r "@Service" module-core-domain/` |
| Controller | `*Controller` | `grep -r "@RestController" module-server-api/` |
| Tests | 테스트 파일 | `ls module-core-*/src/test/**/*Test.kt` |

---

## MCP 활용

> **상세 가이드**: `mcp-efficiency` skill 참조

| 도구 | 용도 |
|------|------|
| `serena.read_memory("server_patterns")` | Server 패턴 로드 |
| `serena.get_symbols_overview("module-core-domain/")` | 도메인 구조 파악 |
| `serena.find_symbol("PipelineService")` | 서비스 상세 조회 |
| `claude-mem.search("PipelineService")` | 과거 구현 참조 |
| `jetbrains.search_in_files_by_text("@Service")` | 패턴 검색 |
