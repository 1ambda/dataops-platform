# Architecture Documentation

DataOps Platform architectural patterns, design principles, and system structure.

## Design Principles

**Architecture**: Pure Hexagonal Architecture, Domain-Driven Design, Microservices
**Infrastructure**: Container-first, polyglot services, composition over inheritance
**Flow**: UI (React) → Server (Spring Boot) → Parser (Flask) + Connect (Flask)

## System Components

**basecamp-ui**: React 19 presentation layer
**basecamp-server**: Spring Boot orchestration (ports: Domain interfaces, adapters: Infrastructure)
**basecamp-parser**: Flask SQL processing service
**basecamp-connect**: Flask integration service (GitHub/Jira/Slack)
**Data Layer**: MySQL (primary), Redis (cache), PostgreSQL (Keycloak)

## Hexagonal Architecture (basecamp-server)
**Module Structure**:
- `module-core-common`: Shared utilities
- `module-core-domain`: Business domain (Ports - interfaces)
- `module-core-infra`: Infrastructure (Adapters - implementations)
- `module-server-api`: REST API layer

**Key Patterns**:
- Domain defines repository interfaces (UserRepositoryJpa/Dsl)
- Infrastructure implements with composition (UserRepositoryJpaImpl)
- Services are concrete classes, inject domain interfaces
- CQRS separation: JPA (CRUD) vs DSL (queries)
- Naming: UserEntity, UserDto, UserRepositoryJpa/Impl

## Data Flow

**Request Flow**: API → Service → Repository → Database
**Response Flow**: Database → Repository → Service → API → UI
**Cross-cutting**: Security (Keycloak), Caching (Redis), Logging
