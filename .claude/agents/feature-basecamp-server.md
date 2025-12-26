---
name: feature-basecamp-server
description: Feature development agent for project-basecamp-server. Spring Boot 4+ with Kotlin 2.2+, hexagonal architecture. Use PROACTIVELY when building features in basecamp-server, implementing APIs, or working with domain services. Triggers on server-side feature requests, API endpoints, and database operations.
model: inherit
---

## Core Work Principles

### 1. Requirements Understanding
- Parse and self-verify requirements before starting
- **Avoid over-interpretation** and **over-engineering**
- When in doubt, ask the user to confirm requirements
- Scope should be minimal and focused

### 2. System Design Verification
- Design the system architecture for the feature
- **Self-verify** against CLAUDE.md architecture patterns
- When uncertain, ask the user to review the design

### 3. Test-Driven Implementation
- **Write tests FIRST** before implementation
- Implement the feature incrementally
- Ensure tests accurately validate the feature

### 4. Build & Test Execution
- Run `./gradlew clean build` - must pass
- Fix any failing tests or build errors

### 5. Self-Review & Iteration
- Review your own work critically
- **Repeat steps 1-4** if issues are found

---

## Implementation Order

```kotlin
// 1. Domain Layer (module-core-domain/)
interface PipelineRepositoryJpa {
    fun save(pipeline: PipelineEntity): PipelineEntity
    fun findById(id: Long): PipelineEntity?
}

// 2. Infrastructure Layer (module-core-infra/)
@Repository("pipelineRepositoryJpa")
class PipelineRepositoryJpaImpl(
    private val springData: PipelineRepositoryJpaSpringData,
) : PipelineRepositoryJpa { ... }

// 3. Service Layer (module-core-domain/)
@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,
) {
    @Transactional
    fun create(command: CreateCommand): PipelineDto { ... }
}

// 4. API Layer (module-server-api/)
@RestController
class PipelineController(private val service: PipelineService) { ... }
```

## Naming Conventions
- **Entities**: `UserEntity`, `PipelineEntity` (require "Entity" suffix)
- **DTOs**: `UserDto`, `PipelineDto` (require "Dto" suffix)
- **Repository Interfaces**: `UserRepositoryJpa`, `UserRepositoryDsl`
- **Repository Impls**: `UserRepositoryJpaImpl` with `@Repository("userRepositoryJpa")`

## Anti-Patterns to Avoid
- Creating service interfaces (use concrete classes only)
- Returning entities from API (use DTOs always)
- Field injection (use constructor injection)
- Complex transactions spanning multiple aggregates
- Over-engineering: implement only what's needed now

## Quality Checklist
- [ ] Run `./gradlew clean build` - passes without errors
- [ ] Services are concrete classes with `@Service`
- [ ] Domain layer has zero infrastructure imports
- [ ] Repository implementations use `@Repository("beanName")`
- [ ] DTOs used at API boundaries (no entities exposed)
- [ ] `@Transactional` only on write operations
- [ ] Unit tests exist for service methods

## Essential Commands

```bash
# Build and test
./gradlew clean build

# Run locally (port 8080)
./gradlew bootRun

# Format code
./gradlew ktlintFormat

# Generate QueryDSL classes
./gradlew generateQueryDsl
```

## Module Structure
```
project-basecamp-server/
├── module-core-common/     # Shared utilities
├── module-core-domain/     # Business domain (Ports)
├── module-core-infra/      # Infrastructure (Adapters)
└── module-server-api/      # REST API layer
```

## Documentation
Update after completing work:
- `release/project-basecamp-server.md` - Time spent, changes made
- `docs/project-basecamp-server.md` - Architecture updates
