# Basecamp Server Development Patterns (Quick Reference)

> Spring Boot 4+ with Kotlin 2.2+, Pure Hexagonal Architecture

## 1. Repository Pattern (Critical) - DOMAIN-SPECIFIC PACKAGES

### Domain Layer (Ports) - ORGANIZED BY DOMAIN

```kotlin
// module-core-domain/repository/{domain}/
// CRITICAL: All repositories MUST be in domain-specific packages

// Quality domain repositories
module-core-domain/repository/quality/
├── QualitySpecRepositoryJpa.kt
├── QualitySpecRepositoryDsl.kt
├── QualityRunRepositoryJpa.kt
├── QualityTestRepositoryJpa.kt
└── TestResultRepositoryJpa.kt

// Workflow domain repositories
module-core-domain/repository/workflow/
├── WorkflowRepositoryJpa.kt
├── WorkflowRepositoryDsl.kt
├── WorkflowRunRepositoryJpa.kt
└── WorkflowRunRepositoryDsl.kt

// Other domains: adhoc/, airflow/, audit/, catalog/, dataset/, 
//               github/, lineage/, metric/, query/, resource/, 
//               transpile/, user/

// Package declaration:
package com.github.lambda.domain.repository.quality

interface QualitySpecRepositoryJpa {
    fun save(spec: QualitySpecEntity): QualitySpecEntity
    fun findById(id: Long): QualitySpecEntity?
}
```

### Infrastructure Layer (Adapters) - MATCHING DOMAIN PACKAGES

```kotlin
// module-core-infra/repository/{domain}/
// CRITICAL: Mirror domain package structure exactly

// Quality domain implementations
module-core-infra/repository/quality/
├── QualitySpecRepositoryJpaImpl.kt
├── QualitySpecRepositoryDslImpl.kt
├── QualityRunRepositoryJpaImpl.kt
├── QualityTestRepositoryJpaImpl.kt
└── TestResultRepositoryJpaImpl.kt

// Package declaration:
package com.github.lambda.infra.repository.quality

@Repository("qualitySpecRepositoryJpa")
class QualitySpecRepositoryJpaImpl(
    private val springData: QualitySpecRepositoryJpaSpringData,
) : QualitySpecRepositoryJpa {
    override fun save(spec: QualitySpecEntity) = springData.save(spec)
    override fun findById(id: Long) = springData.findById(id).orElse(null)
}
```

### Import Pattern - DOMAIN-SPECIFIC

```kotlin
// ✅ CORRECT: Domain-specific imports
import com.github.lambda.domain.repository.quality.QualitySpecRepositoryJpa
import com.github.lambda.domain.repository.quality.QualitySpecRepositoryDsl
import com.github.lambda.domain.repository.workflow.WorkflowRepositoryJpa

// ❌ WRONG: Old flat package imports
import com.github.lambda.domain.repository.QualitySpecRepositoryJpa
```

## 2. Service Layer (Concrete Classes Only)

```kotlin
@Service
@Transactional(readOnly = true)
class QualityService(
    private val qualitySpecRepositoryJpa: QualitySpecRepositoryJpa,    // From quality package
    private val qualitySpecRepositoryDsl: QualitySpecRepositoryDsl,    // From quality package
    private val workflowRepositoryJpa: WorkflowRepositoryJpa,          // From workflow package
) {
    @Transactional
    fun create(command: CreateQualitySpecCommand): QualitySpecEntity { ... }
    fun findById(query: GetQualitySpecQuery): QualitySpecEntity? { ... }
}
```

## 3. Domain Package Organization Structure

```
module-core-domain/repository/
├── adhoc/          # AdHocExecutionRepository*, UserExecutionQuotaRepository*
├── airflow/        # AirflowClusterRepository*
├── audit/          # AuditAccessRepository*, AuditResourceRepository*
├── catalog/        # CatalogTableRepository*, CatalogColumnRepository*, 
│                   # CatalogRepository*, SampleQueryRepository*
├── dataset/        # DatasetRepository*
├── github/         # GitHubRepository*
├── lineage/        # LineageNodeRepository*, LineageEdgeRepository*
├── metric/         # MetricRepository*
├── quality/        # QualitySpecRepository*, QualityRunRepository*, 
│                   # QualityTestRepository*, TestResultRepository*
├── query/          # QueryExecutionRepository*
├── resource/       # ResourceRepository*
├── transpile/      # TranspileRuleRepository*
├── user/           # UserRepository*, UserAuthorityRepository*
└── workflow/       # WorkflowRepository*, WorkflowRunRepository*
```

## 4. Naming Conventions

| Type | Pattern | Example |
|------|---------|---------| 
| JPA Entities | `*Entity` | `QualitySpecEntity` |
| API DTOs | `*Dto` | `QualitySpecDto` |
| Repository (CRUD) | `*RepositoryJpa` | `QualitySpecRepositoryJpa` |
| Repository (Query) | `*RepositoryDsl` | `QualitySpecRepositoryDsl` |
| Repository Impl | `*RepositoryJpaImpl` | `QualitySpecRepositoryJpaImpl` |

## 5. Data Ownership Patterns (ASK IF UNCLEAR)

| Scenario | Pattern | Example |
|----------|---------|---------| 
| **Self-managed** | JPA Entity + RepositoryJpa/Dsl | `QualitySpecEntity` |
| **External API** | External Client + Domain Models | `AirflowClient` |

⚠️ Feature Spec이 두 패턴 모두 언급하면 **반드시 사용자에게 확인!**

## 6. Implementation Order

1. Domain Entity (`module-core-domain/entity/{feature}/`)
2. Domain Repository Interfaces (`module-core-domain/repository/{domain}/`)
3. Infrastructure Implementations (`module-core-infra/repository/{domain}/`)
4. Domain Service (`module-core-domain/service/`)
5. API Controller (`module-server-api/controller/`)

## 7. Anti-Patterns (CRITICAL)

- ❌ **Repository in flat package** - Must use domain packages!
- ❌ **Wrong package imports** - Use domain-specific imports
- ❌ **Repository without Jpa/Dsl suffix** - `UserRepository` 금지
- ❌ Service interfaces (use concrete classes)
- ❌ Exposing entities from API (use DTOs)
- ❌ Field injection (use constructor)
- ❌ Missing `@Repository("beanName")`

## 8. MCP Query Optimization (CRITICAL)

### Token-Efficient Patterns

| Task | GOOD | BAD |
|------|------|-----|
| List repositories | `list_dir("repository/{domain}/")` | `search_for_pattern("Repository")` |
| Find repo by domain | `find_symbol("quality/*Repository", depth=1)` | `search_for_pattern("@Repository")` |
| Get method signature | `find_symbol("RepositoryJpa/method", include_body=False)` | `search_for_pattern("fun method")` |
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
```

### Progressive Disclosure (MANDATORY)

```
Level 1: list_dir("repository/{domain}/") → domain files only (~200 tokens)
Level 2: get_symbols_overview(file) → structure only (~300 tokens)
Level 3: find_symbol(depth=1, include_body=False) → signatures (~400 tokens)
Level 4: find_symbol(include_body=True) → specific body (~500 tokens)
Level 5: Read(file) → LAST RESORT (~5000+ tokens)
```

## 9. Essential Commands

```bash
./gradlew clean build       # Build and test
./gradlew bootRun           # Run locally (port 8080)
./gradlew ktlintFormat      # Format code
./gradlew generateQueryDsl  # Generate QueryDSL
```

## 10. Repository Package Migration Checklist

When creating new repositories or refactoring:

- [ ] ✅ Place repository interfaces in `domain/repository/{domain}/`
- [ ] ✅ Place repository implementations in `infra/repository/{domain}/`
- [ ] ✅ Use correct package declaration: `com.github.lambda.domain.repository.{domain}`
- [ ] ✅ Update import statements to use domain-specific packages
- [ ] ✅ Follow naming convention: `*RepositoryJpa`, `*RepositoryDsl`, `*RepositoryJpaImpl`
- [ ] ✅ Ensure services inject from domain-specific packages
- [ ] ✅ Verify build passes after organization changes