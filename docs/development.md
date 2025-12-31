# Development Guide

Development workflows, architecture patterns, and best practices for DataOps Platform contributors.

**Contents**: [Philosophy](#philosophy) | [Setup](#setup) | [Git Workflow](#git-workflow) | [Testing](#testing) | [Code Quality](#code-quality) | [CI/CD](#cicd) | [Project Development](#project-development)

## Philosophy

**Architecture**: Pure Hexagonal Architecture, Domain-Driven Design, CQRS separation, Dependency Inversion
**Quality**: Test-driven development, mandatory code reviews, continuous integration, clean architecture

## Setup

**Prerequisites**: Docker 20.10+ with BuildKit, Java 21+, Python 3.12+, Node.js 22+, uv, make

**Quick Start**:
```bash
git clone https://github.com/YOUR_USERNAME/dataops-platform.git
cd dataops-platform && make setup && make dev && make health
```

**Development Modes**:
- **Infrastructure Only** (`make dev`): Run apps from IDE, infrastructure in Docker
- **Full Stack** (`make dev-all`): Everything in Docker for integration testing

---

## Git Workflow

**Strategy**: Git Flow → `feature/*` → `develop` → `main`

**Branch Format**: `feature/{system}.TICKET-{number}/{description}`
- Systems: `basecamp`, `interface`, `platform`
- Example: `feature/basecamp.TICKET-4500/improve-dockerfile`

**Commit Format**: `{system}.TICKET-{number}: {description}`
- Example: `basecamp.TICKET-4500: improve dockerfile caching`

**Workflow**: Create feature branch from `develop` → PR to `develop` (`[DEV]` prefix) → PR to `main` (`[RELEASE]` prefix)

---

## Development Environment

**Key Commands**: `make setup && make dev` | `make health && make logs` | `make clean && make rebuild`

Complete command reference: `make help`

**Performance**: Docker BuildKit caching provides 30-60% faster rebuilds

---

## Project Development

### basecamp-server (Spring Boot + Kotlin)

**Hexagonal Architecture Patterns**:
- Services: Concrete classes (`@Service`), no interfaces
- Repositories: Domain interfaces → Infrastructure implementations
- Naming: `UserEntity`, `UserDto`, `UserRepositoryJpa`/`UserRepositoryJpaImpl`
- CQRS: Separate `RepositoryJpa` (CRUD) from `RepositoryDsl` (complex queries)

**Development**: `cd project-basecamp-server && ./gradlew bootRun` (port 8080)

### basecamp-parser (Python + Flask)
**Development**: `cd project-basecamp-parser && uv run python main.py` (port 5000)
**Pattern**: SQLglot-based SQL parsing microservice

### basecamp-connect (Python + Flask)
**Development**: `cd project-basecamp-connect && uv run python main.py` (port 5001)
**Pattern**: GitHub/Jira/Slack integration service with SQLAlchemy ORM

### basecamp-ui (React + TypeScript)
**Development**: `cd project-basecamp-ui && pnpm run dev` (port 3000)
**Pattern**: Component-based architecture with TypeScript type safety

### interface-cli (Python + Typer)
**Development**: `cd project-interface-cli && uv run dli --help`
**Testing**: `cd project-interface-cli && uv run pytest` (1650+ tests)
**Commands**: `dli config`, `dli metric`, `dli dataset`, `dli workflow`, `dli catalog`, `dli transpile`, `dli lineage`, `dli quality`
**Structure**: Uses `metrics/` and `datasets/` directories for resource definitions (YAML specs)
**Pattern**: Rich CLI with Library API (DatasetAPI, MetricAPI, TranspileAPI, CatalogAPI, ConfigAPI, QualityAPI) for Airflow integration
**Note**: Validation is per-resource (`dli metric validate`, `dli dataset validate`), not a top-level command

See [project-interface-cli/README.md](../project-interface-cli/README.md) for complete documentation.

---

## Testing

**Unit Tests**: Each project has isolated test suites
**Integration Tests**: Use `make dev-all` for full stack testing
**CI/CD**: Path-based triggers, automated build/test/lint checks
**Commands**: `./gradlew test` | `uv run pytest` | `pnpm test`

## Code Quality

**Standards**: ktlint (Kotlin), ruff (Python), eslint/prettier (TypeScript)
**Format**: `./gradlew ktlintFormat` | `uv run ruff format` | `pnpm run format`
**Required**: All code must pass linting, type checks, and tests before merge

## CI/CD

**Path-based Triggers**: Changes to `project-*/` trigger respective CI workflows
**Automated Checks**: Build, test, lint, type check, security scan
**Docker Images**: Auto-built with tags: `feature-pr-123`, `develop-X.X.X-*`, `release-X.X.X-*`
**Registry**: GitHub Container Registry (`ghcr.io/1ambda/{project}`)

## Contributing Guidelines

**Process**: Fork → Feature branch → PR to `develop` → PR to `main`
**Requirements**: Pass all tests, follow architectural patterns, include documentation
**Review**: Mandatory peer review, automated checks must pass
**Standards**: Clean commits, proper naming conventions, comprehensive tests

For detailed project-specific information, see individual project README files.
