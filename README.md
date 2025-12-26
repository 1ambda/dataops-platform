# DataOps Platform

> A polyglot microservices-based platform for modern DataOps workflows with BuildKit-optimized Docker infrastructure

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blue.svg)](https://kotlinlang.org/)
[![Python](https://img.shields.io/badge/Python-3.12-blue.svg)](https://www.python.org/)
[![React](https://img.shields.io/badge/React-19.2.3-61DAFB.svg)](https://reactjs.org/)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.x-blue.svg)](https://www.typescriptlang.org/)
[![Docker](https://img.shields.io/badge/Docker-BuildKit-2496ED.svg)](https://www.docker.com/)

---

## Quick Start

### Prerequisites

- **Docker Desktop** 20.10+ with BuildKit support
- **Docker Compose** v2.0+
- **Make** (optional but recommended)

### 5-Minute Setup

**Option 1: Full Stack (Everything in Docker)**
```bash
# 1. Clone the repository
git clone https://github.com/your-org/dataops-platform.git
cd dataops-platform

# 2. Validate dependencies and initialize
make setup

# 3. Start full stack (BuildKit-optimized)
make dev-all

# 4. Verify services are healthy
make health

# 5. Open the UI in your browser
make open
# Visit http://localhost:3000
```

**Option 2: Infrastructure Only (For Local Development)**
```bash
# 1. Clone and setup
git clone https://github.com/your-org/dataops-platform.git
cd dataops-platform
make setup

# 2. Start infrastructure services only
make dev

# 3. Run application services locally from your IDE
# - basecamp-server: ./gradlew bootRun (port 8080)
# - basecamp-parser: flask run --port=5000
# - basecamp-ui: npm run dev (port 3000)
```

### Access Points

**Full Stack Mode:**
- **Web Dashboard**: http://localhost:3000
- **API Server**: http://localhost:8081/api/health
- **API Documentation**: http://localhost:8081/swagger-ui.html
- **SQL Parser**: http://localhost:5000/health
- **Keycloak**: http://localhost:8080 (admin/admin)

**Infrastructure Mode:**
- **MySQL**: localhost:3306 (user: `dataops_user`, password: `dataops_password`)
- **Redis**: localhost:6379
- **PostgreSQL**: localhost:5432 (user: `postgres`, password: `postgres`)
- **Keycloak**: http://localhost:8080 (admin/admin)

---

## Platform Overview

**DataOps Platform** provides comprehensive data pipeline management, SQL parsing capabilities, and a modern web dashboard for monitoring and orchestration.

### Key Features

- 🚀 **Pipeline Management**: Orchestrate complex data workflows with job scheduling and monitoring
- 🔍 **SQL Processing**: Parse and validate Trino/Presto SQL with dedicated microservice
- 🎨 **Modern UI**: React 19 dashboard with dark mode, RTL support, and responsive design
- 💻 **CLI Tools**: Rich terminal interface for administrative tasks and automation
- ⚡ **High Performance**: Docker BuildKit optimization delivers 30-60% faster rebuilds
- 🔒 **Production Ready**: tini process manager, health checks, resource limits, and observability

### Platform Components

| Component | Status | Technology | Port (Docker) |
|-----------|--------|------------|---------------|
| **project-basecamp-server** | ✅ Complete | Spring Boot 4 + Kotlin 2 | 8081 |
| **project-basecamp-parser** | ✅ Complete | Python 3.12 + Flask | 5000 |
| **project-basecamp-ui** | ✅ Complete | React 19 + TypeScript | 3000 |
| **project-interface-cli** | ✅ Complete | Python 3.12 + Typer | N/A |
| **MySQL** | ✅ Active | MySQL 8 Alpine | 3306 |
| **Redis** | ✅ Active | Redis 7 Alpine | 6379 |
| **PostgreSQL** | ✅ Active | PostgreSQL 15 | 5432 |
| **Keycloak** | ✅ Active | Keycloak Latest | 8080 |

---

## Essential Commands

### Quick Reference

```bash
# Environment Setup
make setup            # First-time setup
make check-deps       # Validate dependencies

# Development
make dev              # Start infrastructure only
make dev-all          # Start full stack
make stop             # Stop all services
make restart          # Restart services

# Building
make build            # Build all service images
make rebuild          # Clean and rebuild from scratch

# Monitoring
make status           # Container status
make health           # Health check all services
make logs             # Follow all logs
make stats            # Real-time resource usage

# Database
make db-shell         # MySQL shell
make redis-cli        # Redis CLI

# Cleanup
make clean            # Remove containers
make clean-all        # Nuclear option (removes everything)
make prune            # Docker system prune
```

For complete command reference, run `make help`.

---

## Project Structure

```
dataops-platform/
├── project-basecamp-server/   # Spring Boot 4 + Kotlin 2 API server
├── project-basecamp-parser/   # Python 3.12 + Flask SQL parser
├── project-basecamp-ui/        # React 19 + TypeScript dashboard
├── project-interface-cli/      # Python 3.12 + Typer CLI tool
├── docs/                       # Comprehensive documentation
│   ├── architecture.md         # System architecture and design
│   ├── development.md          # Development workflows and testing
│   ├── deployment.md           # Production deployment guide
│   └── troubleshooting.md      # Common issues and solutions
├── docker-compose.yaml         # Infrastructure services only
├── docker-compose.all.yaml     # Full stack deployment
├── Makefile                    # Development automation
├── CLAUDE.md                   # AI assistant instructions
└── README.md                   # This file
```

---

## Documentation

### 📚 Comprehensive Guides

- **[Architecture Documentation](./docs/architecture.md)** - System design, components, and data flow
- **[Development Guide](./docs/development.md)** - Local development, testing, and contributing
- **[Deployment Guide](./docs/deployment.md)** - Docker, Kubernetes, and CI/CD
- **[Troubleshooting](./docs/troubleshooting.md)** - Common issues and solutions

### 📦 Project-Specific Documentation

- **[basecamp-server](./project-basecamp-server/README.md)** - Spring Boot API server
- **[basecamp-parser](./project-basecamp-parser/README.md)** - SQL parsing microservice
- **[basecamp-ui](./project-basecamp-ui/README.md)** - React web dashboard
- **[interface-cli](./project-interface-cli/README.md)** - Command-line interface

---

## Technology Stack

### Languages & Frameworks

| Technology | Version | Purpose | Service |
|------------|---------|---------|---------|
| **Kotlin** | 2.2.21 | Primary JVM language | basecamp-server |
| **Spring Boot** | 4.0.1 | Backend framework | basecamp-server |
| **Python** | 3.12 | Data processing & CLI | basecamp-parser, interface-cli |
| **TypeScript** | 5.x | Frontend type safety | basecamp-ui |
| **React** | 19.2.3 | Web interface | basecamp-ui |
| **Flask** | 3.1.2 | Microservice framework | basecamp-parser |
| **Typer** | Latest | CLI framework | interface-cli |

### Build Tools & Package Managers

- **Gradle** 9.2.1 - Kotlin DSL build automation
- **uv** - Fast Python package manager (10-100x faster than pip)
- **Vite** 7.3.0 - Frontend build with SWC compiler
- **Docker BuildKit** - Optimized container builds (30-60% faster)

### Infrastructure & Data

- **MySQL** 8+ - Primary relational database
- **Redis** 7+ - Caching & session management
- **PostgreSQL** 15+ - Keycloak database backend
- **Keycloak** - Identity & access management
- **Flyway** - Database schema migration
- **tini** v0.19.0 - PID 1 process manager

---

## Docker Compose Modes

### Infrastructure Mode (`docker-compose.yaml`)

**Use Case:** Local development - run apps from IDE, use Docker for dependencies

**Services:** MySQL, Redis, PostgreSQL, Keycloak

```bash
make dev
```

**Benefits:** Fast code-reload cycles, direct debugging, reduced Docker overhead

---

### Full Stack Mode (`docker-compose.all.yaml`)

**Use Case:** Complete containerized environment for testing or production-like deployment

**Services:** All infrastructure + basecamp-server, basecamp-parser, basecamp-ui

```bash
make dev-all
```

**Benefits:** Production-like environment, simplified onboarding, consistent across team

> **Note:** In full stack mode, basecamp-server runs on port **8081** (not 8080) to avoid port conflict with Keycloak.

---

## BuildKit Performance

Docker BuildKit optimization delivers significant build performance improvements:

| Build Type | Duration | Improvement |
|------------|----------|-------------|
| **First build (cold)** | 3-5 minutes | Baseline |
| **Rebuild (warm cache)** | 2-3 minutes | 30-60% faster |
| **No-change rebuild** | 10-30 seconds | 85-95% faster |

**Cache Mount Locations:**
- Gradle: `/workspace/.gradle`
- uv (Python): `/root/.cache/uv`
- npm: `/root/.cache/npm`

---

## Release Management

### Version Files

Each project maintains its version in a `.VERSION` file:

```
project-basecamp-server/.VERSION   # e.g., 0.0.1
project-basecamp-parser/.VERSION   # e.g., 0.0.1
project-basecamp-ui/.VERSION       # e.g., 0.0.1
project-interface-cli/.VERSION     # e.g., 0.0.1
```

### Docker Image Tagging Strategy

| Branch/Event | Tag Format | Example |
|--------------|------------|---------|
| **PR (Feature)** | `feature-pr-{NUM}` | `feature-pr-123` |
| **Develop** | `develop-{VERSION}-{BUILDDATE}` | `develop-0.0.1-202512281430` |
| **Main (Release)** | `release-{VERSION}-{BUILDDATE}` | `release-1.0.0-202512281500` |

**Additional Tags:** `sha-{hash}`, `develop`, `main`, `latest`

**Build Date Format:** `YYYYMMDDhhmm` (Asia/Seoul timezone)

### Release Workflow

```
feature/* ──PR──► develop ──merge──► main
    │                │                 │
    │                │                 │
feature-pr-123   develop-0.0.1-*   release-1.0.0-*
```

```bash
# Update version before release
echo "1.0.0" > project-basecamp-server/.VERSION
git add project-basecamp-server/.VERSION
git commit -m "basecamp.TICKET-XXX: bump version to 1.0.0"
```

For detailed release management, see [.github/DEVELOPMENT.md](./.github/DEVELOPMENT.md).

---

## Quick Troubleshooting

### Services Won't Start

```bash
# Check logs
make logs

# Verify port availability
lsof -i :8080  # Keycloak
lsof -i :8081  # basecamp-server (Docker mode)
lsof -i :3000  # basecamp-ui

# Restart services
make restart
```

### Database Connection Errors

```bash
# Check database health
make health

# Reset database (WARNING: deletes data)
make db-reset
```

### Build Issues

```bash
# Clean rebuild
make clean-images
make build

# Or complete reset
make clean-all
make setup
make dev
```

For detailed troubleshooting, see [Troubleshooting Guide](./docs/troubleshooting.md).

---

## Development Workflow

### Local Development

```bash
# 1. Start infrastructure
make dev

# 2. Run services locally
cd project-basecamp-server && ./gradlew bootRun
cd project-basecamp-parser && uv run python main.py
cd project-basecamp-ui && pnpm run dev
```

### Testing

```bash
# Test individual services
cd project-basecamp-server && ./gradlew test
cd project-basecamp-parser && uv run pytest
cd project-basecamp-ui && pnpm test
cd project-interface-cli && uv run pytest

# Test Docker builds
make build
make health
```

For complete development guide, see [Development Documentation](./docs/development.md).

---

## Contributing

We welcome contributions! Please follow these steps:

1. **Fork & Clone**: Fork the repository and clone your fork
2. **Create Branch**: `git checkout -b feature/your-feature-name`
3. **Setup**: `make setup && make dev`
4. **Make Changes**: Follow language-specific style guides
5. **Test**: Run tests and ensure builds succeed
6. **Commit**: Follow [Conventional Commits](https://www.conventionalcommits.org/)
7. **Push**: Push to your fork
8. **Pull Request**: Create PR with clear description

### Code Style

- **Kotlin**: [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- **Python**: PEP 8 with Black formatter
- **TypeScript**: ESLint + Prettier configuration

See [Development Guide](./docs/development.md) for detailed contribution guidelines.

---

## License

Copyright 2025 DataOps Platform Contributors

This project is proprietary software. All rights reserved.

---

## Support & Resources

- **📖 Documentation**: [docs/](./docs/)
- **🐛 Issue Tracker**: GitHub Issues
- **🔄 CI/CD Status**: [.github/workflows/](./.github/workflows/)
- **💬 Project Instructions**: [CLAUDE.md](./CLAUDE.md)

---

**Built with** Spring Boot 4, Kotlin 2, Python 3.12, React 19, and Docker BuildKit optimization ⚡
