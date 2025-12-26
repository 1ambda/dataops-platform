# DataOps Platform - AI Assistant Instructions

This file provides essential context for AI assistants working on the DataOps Platform codebase.

## Project Overview

**dataops-platform** is a polyglot microservices-based platform for DataOps workflows, organized as a monorepo with multiple components:

- ✅ **project-basecamp-server** - Spring Boot 4 + Kotlin 2 (multi-module API server)
- ✅ **project-basecamp-parser** - Python 3.12 + Flask (SQL parsing microservice)
- ✅ **project-basecamp-ui** - React 19 + TypeScript (web dashboard)
- ✅ **project-basecamp-connect** - Python 3.12 + Flask (GitHub/Jira/Slack integration service)
- ✅ **project-interface-cli** - Python 3.12 + Typer (CLI tool named `dli`)
- 🚧 **project-interface-library** - Planned shared library (placeholder)

---

## Documentation Structure

> **Important:** This project follows a structured documentation approach. Detailed information is in dedicated files.

### Core Documentation

- **[README.md](./README.md)** - Quick start, setup, and essential commands
- **[Makefile](./Makefile)** - Development automation commands (run `make help`)
- **[docs/](./docs/)** - Comprehensive system documentation:
  - `architecture.md` - System design, components, data flow, and design decisions
  - `development.md` - Local development, testing, and contribution guidelines
  - `deployment.md` - Docker, Kubernetes, CI/CD, and production deployment
  - `troubleshooting.md` - Common issues, debugging, and solutions

### Project-Specific Documentation

Each project directory contains its own `README.md` with detailed technical information:
- **[project-basecamp-server/README.md](./project-basecamp-server/README.md)** - Module structure, API endpoints, database schema
- **[project-basecamp-parser/README.md](./project-basecamp-parser/README.md)** - SQL parsing API, SQLglot integration
- **[project-basecamp-ui/README.md](./project-basecamp-ui/README.md)** - Component structure, routing, styling
- **[project-basecamp-connect/README.md](./project-basecamp-connect/README.md)** - Integration APIs, GitHub/Jira/Slack workflows
- **[project-interface-cli/README.md](./project-interface-cli/README.md)** - CLI commands, configuration, installation

---

## Quick Reference for AI Agents

### Technology Stack Summary

| Component | Languages | Key Frameworks | Build Tool |
|-----------|-----------|----------------|------------|
| basecamp-server | Kotlin 2.2.21 | Spring Boot 4.0.1 | Gradle 9.2.1 |
| basecamp-parser | Python 3.12 | Flask 3.1.2, SQLglot | uv |
| basecamp-ui | TypeScript | React 19.2.3, Vite 7.3.0 | npm/pnpm |
| basecamp-connect | Python 3.12 | Flask 3.1+, SQLAlchemy 2.0+ | uv |
| interface-cli | Python 3.12 | Typer, Rich | uv |

### Port Configuration (Docker Full Stack Mode)

- **basecamp-ui**: 3000
- **basecamp-server**: 8081 (8080 in local dev mode)
- **basecamp-parser**: 5000
- **basecamp-connect**: 5001
- **Keycloak**: 8080
- **MySQL**: 3306
- **Redis**: 6379
- **PostgreSQL**: 5432

### Common Development Commands

```bash
# Setup and start infrastructure (MySQL, Redis, etc.)
make setup && make dev

# Start full stack in Docker
make dev-all

# Check service health
make health

# View logs
make logs

# Run individual services locally
cd project-basecamp-server && ./gradlew bootRun
cd project-basecamp-parser && uv run python main.py
cd project-basecamp-ui && pnpm run dev
cd project-basecamp-connect && uv run python main.py

# Run tests
cd project-basecamp-server && ./gradlew test
cd project-basecamp-parser && uv run pytest
cd project-basecamp-ui && pnpm test
cd project-basecamp-connect && uv run pytest
```

---

## Key Conventions

### Repository Structure

```
dataops-platform/
├── project-basecamp-server/   # Multi-module Gradle project (Kotlin DSL)
│   ├── module-core-common/     # Shared utilities
│   ├── module-core-domain/     # Domain models
│   ├── module-core-infra/      # Data access layer
│   └── module-server-api/      # REST API
├── project-basecamp-parser/   # Python Flask microservice
├── project-basecamp-ui/        # React TypeScript SPA
├── project-basecamp-connect/  # Python Flask integration service
├── project-interface-cli/      # Python Typer CLI
├── docs/                       # System documentation
├── docker-compose.yaml         # Infrastructure only
├── docker-compose.all.yaml     # Full stack
├── Makefile                    # Development automation
├── CLAUDE.md                   # This file
└── README.md                   # Quick start guide
```

### Code Style

- **Kotlin**: Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Python**: PEP 8 with Black formatter, type hints preferred
- **TypeScript**: ESLint + Prettier configuration in project root

### Docker & BuildKit

All services use Docker BuildKit optimization with aggressive caching:
- Gradle cache: `/workspace/.gradle`
- uv cache: `/root/.cache/uv`
- npm cache: `/root/.cache/npm`

---

## project-basecamp-server Architecture (AI Agent Reference)

### Pure Hexagonal Architecture Pattern

**CRITICAL FOR AI AGENTS:** This project uses **Pure Hexagonal Architecture** with specific patterns:

#### Service Layer (Domain)
```kotlin
// Services are CONCRETE CLASSES (no interfaces)
@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,  // Inject domain interfaces
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,  // Inject domain interfaces
) {
    @Transactional
    fun createPipeline(command: CreatePipelineCommand): PipelineDto { ... }

    fun getPipeline(query: GetPipelineQuery): PipelineDto? { ... }
}
```

#### Repository Layer Structure
```
Domain Layer (module-core-domain/repository/)
├── [Entity]RepositoryJpa.kt     # Simple CRUD operations (interface)
└── [Entity]RepositoryDsl.kt     # Complex queries (interface)

Infrastructure Layer (module-core-infra/repository/)
├── [Entity]RepositoryJpaImpl.kt         # Domain interface implementation (class)
├── [Entity]RepositoryJpaSpringData.kt   # Spring Data JPA interface
└── [Entity]RepositoryDslImpl.kt         # QueryDSL implementation (class)
```

#### Repository Implementation Pattern
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
    override fun save(user: UserEntity): UserEntity = springDataRepository.save(user)
    override fun findById(id: Long): UserEntity? = springDataRepository.findById(id).orElse(null)
}
```

### Key Architecture Rules for AI Agents

1. **No Service Interfaces**: Services are concrete classes with `@Service` annotation
2. **Direct Repository Injection**: Services inject domain repository interfaces directly
3. **Class Naming Conventions**:
   - Domain JPA Entities: `UserEntity`, `PipelineEntity` (require "Entity" suffix)
   - Domain Enums: `UserRole`, `PipelineStatus` (no suffix needed)
   - API DTOs: `UserDto`, `PipelineDto` (require "Dto" suffix)
4. **Repository Naming Convention**:
   - Domain: `UserRepositoryJpa` / `UserRepositoryDsl` (interfaces)
   - Infrastructure: `UserRepositoryJpaImpl` / `UserRepositoryDslImpl` (classes)
5. **Repository Bean Naming**: Use `@Repository("entityRepositoryJpa")` for proper injection
6. **Composition Pattern**: Infrastructure implementations use composition with Spring Data repositories
7. **CQRS Separation**: Separate `RepositoryJpa` (CRUD) from `RepositoryDsl` (complex queries)

---

## Claude Code Agent System

This project uses a structured agent/skill system for AI-assisted development:

```
.claude/
├── README.md         # Agent system overview
├── agents/           # Task-specific agents (feature-*, expert-*, code-*)
└── skills/           # Language-neutral capabilities (testing, debugging, etc.)
```

**Key agents:**
- `feature-basecamp-*` - Project-specific implementation
- `expert-spring-kotlin`, `expert-react-typescript`, `expert-python` - Language specialists
- `code-review`, `code-searcher` - Cross-cutting workflows

See [.claude/README.md](./.claude/README.md) for complete agent/skill documentation.

---

## Important Notes for AI Assistants

1. **Use agents and skills** - Leverage `.claude/agents/` for specialized tasks
2. **MCP-first exploration** - Use `serena.*` tools before reading full files
3. **Follow Architecture Patterns** - Use hexagonal architecture for basecamp-server
4. **Port awareness** - basecamp-server: 8080 (local) vs 8081 (Docker with Keycloak)
5. **Python package manager** - Use `uv` (not pip/poetry)
6. **Repository patterns** - Domain interfaces → infrastructure implementations → Spring Data composition

---

## Where to Find Information

| Topic | Location |
|-------|----------|
| Quick start & setup | [README.md](./README.md) |
| **Agent/Skill system** | [.claude/README.md](./.claude/README.md) |
| System architecture | [docs/architecture.md](./docs/architecture.md) |
| Development workflow | [docs/development.md](./docs/development.md) |
| Deployment guide | [docs/deployment.md](./docs/deployment.md) |
| Troubleshooting | [docs/troubleshooting.md](./docs/troubleshooting.md) |
| API server details | [project-basecamp-server/README.md](./project-basecamp-server/README.md) |
| SQL parser details | [project-basecamp-parser/README.md](./project-basecamp-parser/README.md) |
| UI details | [project-basecamp-ui/README.md](./project-basecamp-ui/README.md) |
| Integration service details | [project-basecamp-connect/README.md](./project-basecamp-connect/README.md) |
| CLI details | [project-interface-cli/README.md](./project-interface-cli/README.md) |
| Development commands | [Makefile](./Makefile) (run `make help`) |

---

**Last Updated:** 2025-12-27

