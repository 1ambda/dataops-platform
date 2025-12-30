# DataOps Platform

Polyglot microservices platform for data pipeline management, SQL processing, and workflow orchestration.

**Tech Stack**: Spring Boot 4 + Kotlin 2 | Python 3.12 + Flask | React 19 + TypeScript | Docker BuildKit

## Quick Start

**Requirements**: Docker Desktop 20.10+ with BuildKit, Docker Compose v2.0+

```bash
git clone https://github.com/your-org/dataops-platform.git
cd dataops-platform
make setup && make dev-all  # Full stack
make health                 # Verify services
```

**Access Points**:
- **UI**: http://localhost:3000
- **API**: http://localhost:8081/swagger-ui.html
- **SQL Parser**: http://localhost:5000/health
- **Integration**: http://localhost:5001/health
- **Keycloak**: http://localhost:8080 (admin/admin)

**Local Development**: Use `make dev` (infrastructure only) and run services from IDE

---

## Components

**Core Services**:
- **basecamp-server** (8081): Spring Boot 4 + Kotlin 2 - API server & pipeline management
- **basecamp-parser** (5000): Python 3.12 + Flask - SQL parsing & validation
- **basecamp-ui** (3000): React 19 + TypeScript - Web dashboard
- **basecamp-connect** (5001): Python 3.12 + Flask - GitHub/Jira/Slack integration
- **interface-cli**: Python 3.12 + Typer - CLI (`dli`) for metric/dataset management, workflow orchestration, catalog browsing, SQL transpilation, lineage analysis, quality testing

**Infrastructure**: MySQL (3306), Redis (6379), PostgreSQL (5432), Keycloak (8080)

**Features**: Pipeline orchestration, Trino/Presto SQL processing, modern React UI, CLI automation, BuildKit optimization (30-60% faster builds)

---

## Essential Commands

```bash
# Setup & Development
make setup && make dev-all    # First-time full stack setup
make dev                      # Infrastructure only (for IDE development)
make health && make logs      # Check status and view logs

# Common Operations
make restart                  # Restart services
make db-shell                 # MySQL CLI
make clean && make rebuild    # Clean rebuild

# Complete reference
make help                     # View all available commands
```

---

## Project Structure

```
dataops-platform/
├── project-basecamp-server/    # Spring Boot API
├── project-basecamp-parser/    # SQL parser service
├── project-basecamp-ui/        # React dashboard
├── project-basecamp-connect/   # Integration service
├── project-interface-cli/      # CLI tools
├── docs/                       # System documentation
├── docker-compose*.yaml        # Docker configurations
├── Makefile                    # Development automation
└── CLAUDE.md                   # AI assistant instructions
```

---

## Documentation

**System Guides**: [Architecture](./docs/architecture.md) | [Development](./docs/development.md) | [Deployment](./docs/deployment.md) | [Troubleshooting](./docs/troubleshooting.md)

**Project Docs**: [server](./project-basecamp-server/README.md) | [parser](./project-basecamp-parser/README.md) | [ui](./project-basecamp-ui/README.md) | [connect](./project-basecamp-connect/README.md) | [cli](./project-interface-cli/README.md)

---

## Development Modes

**Full Stack** (`make dev-all`): All services in Docker - production-like environment
**Infrastructure Only** (`make dev`): MySQL/Redis/PostgreSQL/Keycloak in Docker, apps run from IDE

**Tech Stack**: Kotlin 2 + Spring Boot 4 | Python 3.12 + Flask | React 19 + TypeScript | Gradle + uv + Vite | Docker BuildKit (30-60% faster builds)

---

## Troubleshooting

**Services won't start**: `make logs` → check port conflicts → `make restart`
**Build issues**: `make clean && make rebuild`
**Database errors**: `make health` → `make db-reset` (⚠️ deletes data)

See [Troubleshooting Guide](./docs/troubleshooting.md) for comprehensive solutions.

---

## Contributing & Development

**Quick Start**: Fork → `make setup && make dev` → Create feature branch → Submit PR

**Key Guidelines**:
- Git Flow: `feature/*` → `develop` → `main`
- Hexagonal Architecture (basecamp-server)
- Include tests for all changes
See [Development Guide](./docs/development.md) for complete workflows, testing, and release management.

---

## License & Support

**License**: Copyright 2025 DataOps Platform Contributors. All rights reserved.

**Resources**: [Documentation](./docs/) | [Issues](https://github.com/your-org/dataops-platform/issues) | [AI Instructions](./CLAUDE.md)
