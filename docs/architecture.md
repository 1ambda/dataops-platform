# Architecture Documentation

This document outlines the DataOps Platform's architectural principles, design philosophy, and structural patterns.

---

## Architectural Philosophy

### Core Design Principles

1. **Pure Hexagonal Architecture**: Complete separation between business logic and infrastructure concerns
2. **Domain-Driven Design**: Business domain drives the architecture, not technical frameworks
3. **Polyglot Microservices**: Right technology for each service boundary
4. **Container-First Infrastructure**: Immutable, scalable, and reproducible deployments
5. **Composition over Inheritance**: Favor composition patterns for flexibility and maintainability

---

## System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        DataOps Platform                         │
│                    Polyglot Microservices                       │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────┐      ┌──────────────────┐      ┌──────────────────┐
│  basecamp-ui     │─────▶│ basecamp-server  │─────▶│ basecamp-parser  │
│  React 19        │      │ Spring Boot 4    │      │ Flask 3.1        │
│  (Presentation)  │      │ (Orchestration)  │      │ (SQL Processing) │
└──────────────────┘      └──────────────────┘      └──────────────────┘
                                   │
                          ┌─────────────────┐
                          │   Data Layer    │
                          │ MySQL + Redis   │
                          └─────────────────┘
```

---

## Hexagonal Architecture Pattern (basecamp-server)

### Multi-Module Structure

The basecamp-server implements pure hexagonal architecture through four distinct modules:

```
project-basecamp-server/
├── module-core-common/       # Shared utilities and constants
├── module-core-domain/       # Business domain (Ports)
├── module-core-infra/        # Infrastructure (Adapters)
└── module-server-api/        # REST API layer
```

### Repository Architecture Pattern

**Domain Layer (Ports):**
```kotlin
// Domain defines contracts, not implementations
interface UserRepositoryJpa {
    fun save(user: UserEntity): UserEntity
    fun findById(id: Long): UserEntity?
}

interface UserRepositoryDsl {
    fun searchUsersWithComplexConditions(...): Page<UserEntity>
}
```

**Infrastructure Layer (Adapters):**
```kotlin
// Infrastructure implements domain contracts using composition
@Repository("userRepositoryJpa")
class UserRepositoryJpaImpl(
    private val springDataRepository: UserRepositoryJpaSpringData,
) : UserRepositoryJpa {
    override fun save(user: UserEntity): UserEntity =
        springDataRepository.save(user)
}
```

**Service Layer:**
```kotlin
// Services are concrete classes, not interfaces
@Service
@Transactional(readOnly = true)
class UserService(
    private val userRepositoryJpa: UserRepositoryJpa,    // Port injection
    private val userRepositoryDsl: UserRepositoryDsl,    // Port injection
) {
    @Transactional
    fun createUser(command: CreateUserCommand): UserDto { ... }
}
```

### Benefits of This Pattern

- **Pure Hexagonal Architecture**: Complete separation between business logic and infrastructure
- **Dependency Inversion**: Domain defines contracts, infrastructure implements them
- **Testability**: Business logic tested with mocked repository ports
- **Flexibility**: Easy to swap adapters without domain changes
- **Composition over Inheritance**: Infrastructure uses composition patterns

---

## Service Boundary Design

### Microservice Decomposition Strategy

1. **basecamp-server**: Central orchestration and business logic
2. **basecamp-parser**: Dedicated SQL parsing and validation
3. **basecamp-ui**: User interface and presentation layer
4. **interface-cli**: Command-line automation and scripting

### Communication Patterns

- **Synchronous**: HTTP REST for real-time interactions
- **Asynchronous**: Redis for caching and session management
- **Data Consistency**: MySQL transactions for ACID compliance

---

## Container Architecture

### Docker BuildKit Optimization

**Multi-Stage Builds with Aggressive Caching:**
- Gradle builds: Cache dependencies separately from application code
- Python builds: Cache uv dependencies and virtual environments
- Node builds: Cache npm modules and build artifacts

**Spring Boot Layer Strategy:**
1. **Dependencies Layer**: External libraries (changes rarely)
2. **Application Layer**: Business code (changes frequently)

This approach achieves 85-95% faster rebuilds for code-only changes.

---

## Scalability & Resilience Patterns

### Horizontal Scaling Strategy

**Stateless Services:**
- basecamp-server: Scale with load balancer + Redis sessions
- basecamp-parser: Scale based on SQL parsing demand
- basecamp-ui: Static assets via CDN

**Stateful Services:**
- MySQL: Read replicas for query scaling
- Redis: Clustering for cache scaling

### Circuit Breaker Pattern

Services implement fallback mechanisms for dependency failures, ensuring system resilience.

---

## Security Architecture

### OAuth2 + JWT Pattern

```
User → UI → Keycloak (OAuth2) → JWT Token → API Gateway → Services
```

**Key Components:**
- **Keycloak**: Identity provider and token issuer
- **Spring Security 6**: Resource server with JWT validation
- **Stateless Authentication**: No server-side session storage

---

## Development Methodology

### Architecture-First Development

1. **Domain Modeling**: Define business entities and use cases first
2. **Port Definition**: Create repository and service contracts
3. **Infrastructure Implementation**: Build adapters using composition
4. **API Layer**: Expose domain services through REST endpoints
5. **Testing Strategy**: Unit tests with mocked ports, integration tests with real adapters

### CQRS Implementation

- **Command Operations**: Write operations with transaction boundaries
- **Query Operations**: Read operations optimized for specific use cases
- **Separation Benefits**: Independent scaling and optimization strategies

---

## References

- **[Development Guide](./development.md)** - Development methodology and patterns
- **[Deployment Guide](./deployment.md)** - Container orchestration and production strategies
- **[project-basecamp-server/README.md](../project-basecamp-server/README.md)** - Detailed hexagonal architecture implementation