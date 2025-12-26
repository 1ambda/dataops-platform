# DataOps Platform - AI Assistant Instructions

This file provides essential context for AI assistants working on the DataOps Platform codebase.

## Project Overview

**dataops-platform** is a polyglot microservices-based platform for DataOps workflows, organized as a monorepo with multiple components:

- ✅ **project-basecamp-server** - Spring Boot 4 + Kotlin 2 (multi-module API server)
- ✅ **project-basecamp-parser** - Python 3.12 + Flask (SQL parsing microservice)
- ✅ **project-basecamp-ui** - React 19 + TypeScript (web dashboard)
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
- **[project-interface-cli/README.md](./project-interface-cli/README.md)** - CLI commands, configuration, installation

---

## Quick Reference for AI Agents

### Technology Stack Summary

| Component | Languages | Key Frameworks | Build Tool |
|-----------|-----------|----------------|------------|
| basecamp-server | Kotlin 2.2.21 | Spring Boot 4.0.1 | Gradle 9.2.1 |
| basecamp-parser | Python 3.12 | Flask 3.1.2, SQLglot | uv |
| basecamp-ui | TypeScript | React 19.2.3, Vite 7.3.0 | npm/pnpm |
| interface-cli | Python 3.12 | Typer, Rich | uv |

### Port Configuration (Docker Full Stack Mode)

- **basecamp-ui**: 3000
- **basecamp-server**: 8081 (8080 in local dev mode)
- **basecamp-parser**: 5000
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

# Run tests
cd project-basecamp-server && ./gradlew test
cd project-basecamp-parser && uv run pytest
cd project-basecamp-ui && pnpm test
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

## Important Notes for AI Assistants

1. **Always check existing docs first** - Before providing detailed explanations, reference the appropriate doc file
2. **Use project-specific READMEs** - Each project has detailed technical information in its own README
3. **Refer to Makefile for commands** - Development workflows are automated in the Makefile
4. **Port awareness** - basecamp-server uses different ports in local (8080) vs Docker (8081) modes
5. **Multi-module structure** - basecamp-server is a multi-module Gradle project; understand the module boundaries
6. **Python package manager** - Use `uv` (not pip/poetry) for Python projects
7. **Keycloak port conflict** - Keycloak uses 8080, hence basecamp-server uses 8081 in full Docker mode

---

## Where to Find Information

| Topic | Location |
|-------|----------|
| Quick start & setup | [README.md](./README.md) |
| System architecture | [docs/architecture.md](./docs/architecture.md) |
| Development workflow | [docs/development.md](./docs/development.md) |
| Deployment guide | [docs/deployment.md](./docs/deployment.md) |
| Troubleshooting | [docs/troubleshooting.md](./docs/troubleshooting.md) |
| API server details | [project-basecamp-server/README.md](./project-basecamp-server/README.md) |
| SQL parser details | [project-basecamp-parser/README.md](./project-basecamp-parser/README.md) |
| UI details | [project-basecamp-ui/README.md](./project-basecamp-ui/README.md) |
| CLI details | [project-interface-cli/README.md](./project-interface-cli/README.md) |
| Development commands | [Makefile](./Makefile) (run `make help`) |

---

**Last Updated:** 2025-12-27

