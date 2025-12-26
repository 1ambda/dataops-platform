# Development Guide

This guide covers development methodologies, architectural principles, and best practices for contributing to the DataOps Platform.

---

## Development Philosophy

### Architectural Principles

1. **Pure Hexagonal Architecture**: True ports and adapters pattern with no bridge coupling
2. **Domain-Driven Design**: Business logic encapsulated in domain services
3. **CQRS Separation**: Clear distinction between Command and Query operations
4. **Dependency Inversion**: Domain defines contracts, infrastructure implements them
5. **Composition over Inheritance**: Infrastructure uses composition patterns

### Code Quality Standards

- **Clean Architecture**: Each layer has clear responsibilities and dependencies
- **Test-Driven Development**: Write tests before implementation
- **Continuous Integration**: All code must pass automated checks
- **Code Reviews**: Mandatory peer review for all changes

---

## Quick Start

### Prerequisites

- **Docker Desktop** 20.10+ with BuildKit support
- **Git** for version control
- **Make** (recommended for automation)

### First-Time Setup

```bash
# Clone and initialize
git clone https://github.com/your-org/dataops-platform.git
cd dataops-platform

# Setup infrastructure
make setup && make dev

# Verify services
make health
```

---

## Architecture-Driven Development

### project-basecamp-server (Spring Boot + Kotlin)

**Hexagonal Architecture Implementation:**

```kotlin
// Domain Layer - Pure business logic
@Service
@Transactional(readOnly = true)
class PipelineService(
    private val pipelineRepositoryJpa: PipelineRepositoryJpa,  // Port (interface)
    private val pipelineRepositoryDsl: PipelineRepositoryDsl,  // Port (interface)
) {
    @Transactional
    fun createPipeline(command: CreatePipelineCommand): PipelineDto {
        // Business logic here
    }
}

// Infrastructure Layer - Adapters
@Repository("pipelineRepositoryJpa")
class PipelineRepositoryJpaImpl(
    private val springDataRepository: PipelineRepositoryJpaSpringData,
) : PipelineRepositoryJpa {
    // Adapter implementation using composition
}
```

**Development Workflow:**

```bash
cd project-basecamp-server

# Build and test
./gradlew build

# Run with infrastructure
./gradlew bootRun

# Code quality checks
./gradlew ktlintFormat ktlintCheck
```

**Key Patterns:**
- Services are concrete classes (no interfaces)
- Repository interfaces define contracts (ports)
- Repository implementations use composition (adapters)
- Command/Query objects for CQRS

---

### project-basecamp-parser (Python + Flask)

**Microservice Pattern:**

```python
# Clean API design with separation of concerns
@app.route('/parse-sql', methods=['POST'])
def parse_sql():
    request_data = SQLParseRequest.model_validate(request.json)
    result = sql_parser_service.parse(request_data.sql_query)
    return SQLParseResponse.model_dump(result)
```

**Development Workflow:**

```bash
cd project-basecamp-parser

# Setup environment
uv sync

# Run development server
uv run python main.py

# Quality checks
uv run ruff check --fix
uv run pytest --cov=src
```

---

### project-basecamp-ui (React + TypeScript)

**Component-Based Architecture:**

```typescript
// Clean component design with type safety
interface PipelineListProps {
  pipelines: Pipeline[];
  onPipelineSelect: (id: string) => void;
}

export const PipelineList: React.FC<PipelineListProps> = ({
  pipelines,
  onPipelineSelect
}) => {
  // Component logic here
};
```

**Development Workflow:**

```bash
cd project-basecamp-ui

# Setup and run
pnpm install && pnpm run dev

# Quality checks
pnpm run type-check && pnpm run lint
```

---

## Development Environment

### Infrastructure-First Development (Recommended)

```bash
# Start infrastructure services
make dev

# Run application services locally for fast iteration
# - IDE debugging support
# - Hot reloading
# - Direct access to logs
```

### Full Docker Stack (Integration Testing)

```bash
# Start everything in Docker
make dev-all

# Production-like environment
# Cross-service integration testing
# Simplified onboarding
```

---

## Testing Strategy

### Test Pyramid

1. **Unit Tests** (Fast, Isolated)
   - Service layer with mocked repositories
   - Pure functions and business logic
   - Domain model validation

2. **Integration Tests** (Component Interaction)
   - Repository implementations with Testcontainers
   - API endpoints with real HTTP calls
   - Cross-module communication

3. **End-to-End Tests** (Full Workflow)
   - Complete user journeys
   - Cross-service communication
   - Production-like environment

### Testing Commands

```bash
# Run all tests
cd project-basecamp-server && ./gradlew test
cd project-basecamp-parser && uv run pytest
cd project-basecamp-ui && pnpm test

# Coverage reports
./gradlew jacocoTestReport  # Java/Kotlin
uv run pytest --cov=src    # Python
```

---

## Code Quality

### Language-Specific Guidelines

**Kotlin (basecamp-server):**
- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use ktlint for formatting
- Prefer data classes and null safety
- Hexagonal architecture patterns

**Class Naming Conventions (basecamp-server):**
- **JPA Entities** (module-core-domain): Must have "Entity" suffix - `UserEntity`, `PipelineEntity`
- **Domain Enums** (module-core-domain): No suffix needed - `UserRole`, `PipelineStatus`
- **API DTOs** (module-server-api): Must have "Dto" suffix - `UserDto`, `PipelineDto`

**Python (basecamp-parser, interface-cli):**
- PEP 8 compliance with type hints
- Black formatting (88 character limit)
- Ruff linting with Pyright type checking
- Functional programming patterns

**TypeScript (basecamp-ui):**
- Strict TypeScript configuration
- ESLint + Prettier enforcement
- Functional components with hooks
- Clean component architecture

### Commit Conventions

```bash
feat: add pipeline execution API
fix: resolve database connection timeout
refactor: simplify repository architecture
docs: update architecture documentation
test: add integration test suite
```

---

## Essential Commands

### Development Workflow

```bash
# Environment setup
make setup              # First-time initialization
make dev                # Infrastructure services only
make dev-all           # Full stack in Docker

# Service control
make health            # Check all services
make logs              # Follow all logs
make stop              # Stop services
make clean             # Clean containers
```

### Building and Testing

```bash
# Build all services
make build

# Individual service builds
make build-server      # basecamp-server
make build-parser      # basecamp-parser
make build-ui          # basecamp-ui
```

### Database Management

```bash
make db-shell          # MySQL shell access
make redis-cli         # Redis CLI access
make db-reset          # Reset database (warning: deletes data)
```

---

## Contributing

### Development Workflow

1. **Fork & Clone**
   ```bash
   git clone https://github.com/your-username/dataops-platform.git
   ```

2. **Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Setup Environment**
   ```bash
   make setup && make dev
   ```

4. **Follow Architecture Patterns**
   - Implement hexagonal architecture for basecamp-server
   - Use clean component design for basecamp-ui
   - Follow microservice patterns for basecamp-parser

5. **Test & Quality Checks**
   ```bash
   # Run tests and quality checks for each service
   # Build Docker images and verify health
   make build && make health
   ```

6. **Submit Pull Request**
   - Clear description of changes
   - Reference related issues
   - Pass all CI checks

### Pull Request Checklist

- [ ] Follows architectural principles (hexagonal, CQRS, DDD)
- [ ] Service layer tested with mocked repositories
- [ ] Repository layer tested with integration tests
- [ ] Code style compliance (ktlint, ruff, eslint)
- [ ] Documentation updated
- [ ] All tests passing
- [ ] Docker builds successful

---

## Architecture References

- **[Architecture Documentation](./architecture.md)** - System design and patterns
- **[project-basecamp-server/README.md](../project-basecamp-server/README.md)** - Hexagonal architecture details
- **[CLAUDE.md](../CLAUDE.md)** - AI agent architecture reference

---

**Remember: Architecture drives implementation. Always consider the hexagonal architecture principles when making changes to basecamp-server.**