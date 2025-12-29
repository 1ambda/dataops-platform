# project-basecamp-server

**Central DataOps Platform Server - Spring Boot 4 + Kotlin 2 Multi-Module Application**

## Overview

`project-basecamp-server` is the central orchestration server for the DataOps Platform, implementing pure hexagonal architecture with clean separation of concerns.

**Technology:** Spring Boot 4.0.1 + Kotlin 2.2.21 + Gradle 9.2.1

**Port Configuration:**
- **Local development**: 8080
- **Docker full stack mode**: 8081 (to avoid Keycloak port conflict)

---

## Quick Start

### Prerequisites

- JDK 24+
- Gradle 8+ (bundled via Gradle wrapper)
- MySQL 8+ (or use Docker via `make dev`)
- Redis 7+ (or use Docker via `make dev`)

### Local Development

```bash
# From project root, start infrastructure services
cd ..
make dev

# Return to project directory
cd project-basecamp-server

# Build the project
./gradlew build

# Run with local profile (port 8080)
./gradlew bootRun

# Run tests
./gradlew test
```

### Access Points

- **API Server**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/api/health
- **Actuator**: http://localhost:8080/actuator/health

---

## Architecture Overview

The project implements **Pure Hexagonal Architecture** (Ports and Adapters) with clean module separation:

```
project-basecamp-server/
├── module-core-common/       # Shared utilities and constants
├── module-core-domain/       # Business domain (Ports)
├── module-core-infra/        # Infrastructure (Adapters)
└── module-server-api/        # REST API layer
```

### Key Architectural Principles

1. **Dependency Inversion**: Domain defines contracts, infrastructure implements them
2. **Pure Ports and Adapters**: No bridge coupling between layers
3. **CQRS Support**: Separate interfaces for reads vs writes
4. **Command/Query Separation**: Clear distinction between write and read operations
5. **Composition over Inheritance**: Infrastructure uses composition to implement domain contracts

---

## Module Architecture

### 1. module-core-common

**Purpose:** Shared utilities and cross-cutting concerns

```
module-core-common/src/main/kotlin/com/github/lambda/common/
├── annotation/               # Kotlin annotations
├── constant/                 # Common constants
├── exception/                # Exception handling framework
└── util/                     # Date/time utilities
```

**Key Components:**
- Centralized exception handling framework
- DateTime utilities and common constants
- Cross-cutting annotations

---

### 2. module-core-domain (Ports)

**Purpose:** Business domain models, services, and repository interfaces (ports)

```
module-core-domain/src/main/kotlin/com/github/lambda/domain/
├── command/
│   └── pipeline/             # Command pattern (CQRS)
├── dto/
│   └── pipeline/             # Domain data transfer objects
├── model/
│   ├── BaseEntity.kt         # Base entity with audit fields
│   ├── dataset/              # Dataset domain models
│   ├── pipeline/             # Pipeline domain models with business logic
│   └── user/                 # User domain models and aggregates
├── query/
│   └── pipeline/             # Query pattern (CQRS)
├── repository/               # Pure domain interfaces (Ports)
│   ├── PipelineRepositoryJpa.kt    # Simple CRUD operations
│   ├── PipelineRepositoryDsl.kt    # Complex queries
│   ├── UserRepositoryJpa.kt        # User CRUD operations
│   └── UserRepositoryDsl.kt        # User complex queries
└── service/
    ├── PipelineService.kt          # Pipeline business logic (concrete class)
    ├── UserService.kt              # User business logic (concrete class)
    ├── AuditService.kt             # Audit business logic (concrete class)
    └── ResourceService.kt          # Resource business logic (concrete class)
```

**Service Architecture:**
- **No Service Interfaces**: Services are concrete classes with @Service annotation
- **Direct Dependency Injection**: Services directly depend on repository interfaces
- **Command/Query Separation**: Clear distinction using Command and Query objects
- **Transaction Management**: @Transactional annotations for data consistency

---

### 3. module-core-infra (Adapters)

**Purpose:** Infrastructure implementations that adapt to domain interfaces

```
module-core-infra/src/main/kotlin/com/github/lambda/infra/
├── config/
│   ├── DatabaseConfig.kt           # MySQL database configuration
│   ├── CacheConfig.kt              # Redis cache configuration
│   └── QueryDslConfig.kt           # QueryDSL configuration
└── repository/
    ├── PipelineRepositoryJpaImpl.kt      # Domain interface implementation
    ├── PipelineRepositoryDslImpl.kt      # QueryDSL implementation
    ├── UserRepositoryJpaImpl.kt          # User domain interface implementation
    └── UserRepositoryDslImpl.kt          # User QueryDSL implementation
```

**Repository Implementation Pattern:**
```kotlin
// Domain Interface (Port)
interface UserRepositoryJpa {
    fun save(user: UserEntity): UserEntity
    fun findById(id: Long): UserEntity?
}

// Infrastructure Implementation (Adapter)
@Repository("userRepositoryJpa")
class UserRepositoryJpaImpl(
    private val springDataRepository: UserRepositoryJpaSpringData,
) : UserRepositoryJpa {
    override fun save(user: UserEntity): UserEntity =
        springDataRepository.save(user)
    override fun findById(id: Long): UserEntity? =
        springDataRepository.findById(id).orElse(null)
}

// Spring Data JPA Interface (Internal)
@Repository
interface UserRepositoryJpaSpringData : JpaRepository<UserEntity, Long> {
    fun findByEmail(email: String): UserEntity?
}
```

---

### 4. module-server-api

**Purpose:** REST API endpoints and web layer

```
module-server-api/src/main/kotlin/com/github/lambda/
├── BasecampServerApplication.kt    # Main application class
├── api/
│   ├── controller/                 # REST controllers
│   ├── dto/                        # API DTOs
│   ├── config/                     # Security and web configuration
│   └── exception/                  # Global exception handling
└── resources/
    ├── application.yml             # Configuration
    └── db/migration/               # Flyway migrations
```

---

## Pure Hexagonal Repository Architecture

### Class Naming Conventions

| Layer | Class Type | Pattern | Purpose | Example |
|-------|------------|---------|---------|---------|
| **Domain** | JPA Entity | `[Entity]Entity` | Database entities | `UserEntity`, `PipelineEntity` |
| **Domain** | Enum | `[Name]` | Domain enums (no suffix) | `UserRole`, `PipelineStatus` |
| **API** | DTO | `[Name]Dto` | API data transfer | `UserDto`, `PipelineDto` |

### Repository Naming Conventions

| Layer | Type | Pattern | Purpose | Example |
|-------|------|---------|---------|---------|
| **Domain** | Interface | `[Entity]RepositoryJpa` | CRUD operations | `UserRepositoryJpa` |
| **Domain** | Interface | `[Entity]RepositoryDsl` | Complex queries | `UserRepositoryDsl` |
| **Infra** | Implementation Class | `[Entity]RepositoryJpaImpl` | Domain interface impl | `UserRepositoryJpaImpl` |
| **Infra** | Kotlin Class | `[Entity]RepositoryDslImpl` | QueryDSL impl | `UserRepositoryDslImpl` |
| **Infra** | Spring Interface | `[Entity]RepositoryJpaSpringData` | Spring Data JPA | `UserRepositoryJpaSpringData` |

### Repository Structure Flow

```
┌─────────────────────────────────────────────────────────────┐
│                     Domain Layer                            │
│  ┌─────────────────────────────────────────────────────────┤
│  │ Repository Interfaces (Ports)                          │
│  │ • [Entity]RepositoryJpa  (Simple CRUD operations)      │
│  │ • [Entity]RepositoryDsl  (Complex queries)             │
│  │ • Services directly inject these interfaces            │
│  └─────────────────────────────────────────────────────────┤
└─────────────────────────────────────────────────────────────┘
                              ↑
                    Dependency Inversion
                              ↓
┌─────────────────────────────────────────────────────────────┐
│               Infrastructure Layer                          │
│  ┌─────────────────────────────────────────────────────────┤
│  │ Repository Implementations (Adapters)                  │
│  │ • [Entity]RepositoryJpaImpl  (Domain interface impl)   │
│  │ • [Entity]RepositoryDslImpl  (QueryDSL implementation) │
│  └─────────────────────────────────────────────────────────┤
└─────────────────────────────────────────────────────────────┘
```

### Benefits

- **Pure Hexagonal Architecture**: True ports and adapters pattern with no bridge coupling
- **Composition over Inheritance**: Infrastructure uses composition to implement domain contracts
- **Testability**: Business logic can be tested with mocked repository ports
- **Flexibility**: Easy to swap adapters (JPA → MongoDB, etc.) without domain changes
- **Performance**: Optimized implementations for different query patterns

---

## Technology Stack

### Core Framework
- **Spring Boot** 4.0.1
- **Kotlin** 2.2.21
- **Java** 24 (runtime)
- **Gradle** 9.2.1 (Kotlin DSL)

### Database & Persistence
- **MySQL** 8+ (primary database)
- **HikariCP** (connection pooling)
- **Spring Data JPA** (data access)
- **QueryDSL** (type-safe queries)
- **Flyway** (schema migration)

### Cache & Session
- **Redis** 7+ (distributed cache)
- **Lettuce** (Redis client)

### Security & Monitoring
- **Spring Security** 6 (authentication/authorization)
- **Spring Actuator** (monitoring)
- **Micrometer + Prometheus** (metrics)
- **SpringDoc OpenAPI 3** (API documentation)

### Testing
- **JUnit 5** (unit testing)
- **MockK** (Kotlin mocking)
- **Testcontainers** (integration testing)

---

## Configuration Profiles

- **local**: Local development with MySQL/H2 and detailed logging
- **dev**: Development server with external MySQL and Redis
- **test**: Unit testing with H2 in-memory database
- **prod**: Production environment with optimized settings

---

## Development

### Build Commands

```bash
# Clean build
./gradlew clean build

# Run tests
./gradlew test

# Format code (ktlint)
./gradlew ktlintFormat

# Generate coverage report
./gradlew jacocoTestReport
```

### Docker Deployment

```bash
# From project root - start infrastructure
make dev

# Or start full stack
make dev-all
```

---

## Testing (Spring Boot 4.x)

### Quick Reference

| Task | Reference | Key Pattern |
|------|-----------|-------------|
| Controller test | [docs/PATTERNS.md](./docs/PATTERNS.md#1-controller-test-pattern) | @SpringBootTest + @AutoConfigureMockMvc |
| Service test | [docs/PATTERNS.md](./docs/PATTERNS.md#2-service-test-pattern) | @MockkBean + Kotest |
| Troubleshooting | [docs/TESTING.md](./docs/TESTING.md#troubleshooting) | Common errors & solutions |

### Controller Test Template

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

### Critical Imports (Spring Boot 4.x)

```kotlin
// Jackson 3 (NOT Jackson 2)
import tools.jackson.databind.json.JsonMapper

// Web MVC Test (NEW package)
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc

// MockK (requires springmockk 5.0.1+)
import com.ninjasquad.springmockk.MockkBean

// Parallel execution control
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
```

### Required Dependency Versions

```kotlin
// build.gradle.kts - REQUIRED for Spring Boot 4.x
set("springMockkVersion", "5.0.1")  // NOT 4.x
set("springdocVersion", "3.0.0")     // NOT 2.x
```

### Test Commands

```bash
./gradlew test                        # All tests
./gradlew :module-server-api:test     # API module only
./gradlew :module-core-domain:test    # Domain module only
```

### Documentation

- **[docs/PATTERNS.md](./docs/PATTERNS.md)** - Development patterns & templates
- **[docs/TESTING.md](./docs/TESTING.md)** - Detailed testing guide & troubleshooting

---

## Contributing

Follow clean architecture principles:
- Keep domain layer pure (no infrastructure dependencies)
- Use composition in infrastructure implementations
- Maintain clear separation between Commands and Queries
- Follow Kotlin coding conventions

---

**For platform-wide documentation, see the main repository README and docs/ directory.**
