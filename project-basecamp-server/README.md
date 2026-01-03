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

### ✅ Implemented APIs (28% Complete - 10/36 endpoints)

| API | Status | Endpoints | Documentation |
|-----|--------|-----------|---------------|
| **Health API** | ✅ Complete (2/2) | `/api/health`, `/api/info` | Basic health checks |
| **Metrics API** | ✅ 80% (4/5) | `/api/v1/metrics/*` | [METRIC_RELEASE.md](./features/METRIC_RELEASE.md) |
| **Datasets API** | ✅ Complete (4/4) | `/api/v1/datasets/*` | [DATASET_RELEASE.md](./features/DATASET_RELEASE.md) |

**Key Features Implemented:**
- REST CRUD operations for metrics and datasets
- Business validation (email, cron expressions, naming patterns)
- SQL execution with parameter substitution
- Hexagonal architecture with 80+ comprehensive tests
- OAuth2 authentication and authorization ready

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

## Server Policies

The Basecamp Server implements several key policies for data protection and access control:

| Policy | Application Scope | Summary |
|--------|------------------|---------|
| **PII Masking** | Catalog API | Auto-masking of PII columns in sample data (email, phone, SSN patterns) |
| **Workflow Source Type** | Workflow API | CODE workflows limited to pause/unpause; MANUAL workflows allow full CRUD |
| **Query Scope** | Query Metadata API | RBAC-based access control (`my`, `system`, `user`, `all` scopes) |
| **Transpile Rule** | Transpile API | Version-controlled rules with CI/CD deployment and client-side caching |

For detailed policy specifications, see [docs/architecture.md](../../docs/architecture.md#server-policies).

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

> **📖 Code examples**: See [IMPLEMENTATION_GUIDE.md - Repository Layer Patterns](./docs/IMPLEMENTATION_GUIDE.md#repository-layer-patterns)

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

| Task | Reference |
|------|-----------|
| Quick Reference | [docs/TESTING.md](./docs/TESTING.md#quick-reference) |
| Test Patterns by Layer | [docs/TESTING.md](./docs/TESTING.md#test-patterns-by-layer) |
| Troubleshooting | [docs/TESTING.md](./docs/TESTING.md#troubleshooting) |

### Test Commands

```bash
./gradlew test                        # All tests
./gradlew :module-server-api:test     # API module only
./gradlew :module-core-domain:test    # Domain module only
```

### Documentation

**Quick Start (Project-Level):**
- **[docs/PATTERNS.md](./docs/PATTERNS.md)** - Development patterns & templates (Spring Boot 4.x)
- **[docs/TESTING.md](./docs/TESTING.md)** - Testing guide & troubleshooting (Spring Boot 4.x)
- **[docs/CLI_API_MAPPING.md](./docs/CLI_API_MAPPING.md)** - CLI command to API endpoint mapping

**Implementation Status:**
- **[features/_STATUS.md](./features/_STATUS.md)** - 🆕 Current progress (28%, 10/36 endpoints)

**✅ Completed Implementations:**
- **[features/METRIC_RELEASE.md](./features/METRIC_RELEASE.md)** - Metrics API (4/5 endpoints, 23 tests)
- **[features/DATASET_RELEASE.md](./features/DATASET_RELEASE.md)** - Dataset API (4/4 endpoints, 80+ tests)

**Feature Specifications:**
- **[features/README.md](./features/README.md)** - Feature specifications overview
- **[features/METRIC_FEATURE.md](./features/METRIC_FEATURE.md)** - Metric API specification (P0)
- **[features/DATASET_FEATURE.md](./features/DATASET_FEATURE.md)** - Dataset API specification (P0)
- **[features/CATALOG_FEATURE.md](./features/CATALOG_FEATURE.md)** - Catalog API specification (P1)
- **[features/LINEAGE_FEATURE.md](./features/LINEAGE_FEATURE.md)** - Lineage API specification (P1)
- **[features/WORKFLOW_FEATURE.md](./features/WORKFLOW_FEATURE.md)** - Workflow API specification (P2)
- **[features/QUALITY_FEATURE.md](./features/QUALITY_FEATURE.md)** - Quality API specification (P3)
- **[features/QUERY_FEATURE.md](./features/QUERY_FEATURE.md)** - Query API specification (P3)
- **[features/RUN_FEATURE.md](./features/RUN_FEATURE.md)** - Run API specification (P3)
- **[features/TRANSPILE_FEATURE.md](./features/TRANSPILE_FEATURE.md)** - Transpile API specification (P3)
- **[features/HEALTH_FEATURE.md](./features/HEALTH_FEATURE.md)** - Health API specification (P0)

**Implementation Guides:**
- **[docs/IMPLEMENTATION_GUIDE.md](./docs/IMPLEMENTATION_GUIDE.md)** - Service, repository, controller patterns
- **[docs/ERROR_HANDLING.md](./docs/ERROR_HANDLING.md)** - Error codes and exception handling

**Platform-Level Architecture (Shared):**
- **[../../docs/architecture.md](../../docs/architecture.md)** - System design, policies, and architectural decisions

**Archive:**
- **[features/archive/](./features/archive/)** - Historical specifications and planning documents

---

## Contributing

Follow clean architecture principles:
- Keep domain layer pure (no infrastructure dependencies)
- Use composition in infrastructure implementations
- Maintain clear separation between Commands and Queries
- Follow Kotlin coding conventions

---

**For platform-wide documentation, see the main repository README and docs/ directory.**
