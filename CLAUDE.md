# DataOps Platform - AI Assistant Instructions

This file provides essential context for AI assistants working on the DataOps Platform codebase.

## Project Overview

**dataops-platform** is a polyglot microservices-based platform for DataOps workflows, organized as a monorepo with multiple components:

- ✅ **project-basecamp-server** - Spring Boot 4 + Kotlin 2 (multi-module API server)
- ✅ **project-basecamp-parser** - Python 3.12 + Flask (SQL parsing microservice)
- ✅ **project-basecamp-ui** - React 19 + TypeScript (web dashboard)
- ✅ **project-basecamp-connect** - Python 3.12 + Flask (GitHub/Jira/Slack integration service)
- ✅ **project-interface-cli** - Python 3.12 + Typer (CLI tool named `dli` - metric/dataset CRUD with SQL transpilation, catalog browsing, workflow orchestration, lineage analysis, query metadata, **Quality Spec** 기반 데이터 품질 검증, **Ad-hoc SQL execution**, **SQL/YAML formatting**, **Library API v0.9.1** with ENV hierarchical config)

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
- **[project-interface-cli/README.md](./project-interface-cli/README.md)** - CLI commands, Library API, configuration

---

## Quick Reference for AI Agents

### Technology Stack Summary

| Component | Languages | Key Frameworks | Build Tool |
|-----------|-----------|----------------|------------|
| basecamp-server | Kotlin 2.2.21 | Spring Boot 4.0.1 | Gradle 9.2.1 |
| basecamp-parser | Python 3.12 | Flask 3.1.2, SQLglot | uv |
| basecamp-ui | TypeScript | React 19.2.3, Vite 7.3.0 | npm/pnpm |
| basecamp-connect | Python 3.12 | Flask 3.1+, SQLAlchemy 2.0+ | uv |
| interface-cli | Python 3.12 | Typer, Rich, Pydantic | uv |

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
cd project-interface-cli && uv run pytest
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

## project-interface-cli Development Guide

**IMPORTANT FOR AI AGENTS:** 단일 패턴 문서를 참조하여 구현하세요.

### Quick Reference (토큰 효율 순)

| 우선순위 | 참조 | 용도 |
|----------|------|------|
| 1 | `mcp__serena__read_memory("cli_patterns")` | 핵심 패턴 요약 |
| 2 | `mcp__serena__read_memory("cli_test_patterns")` | 테스트 패턴 요약 |
| 3 | `project-interface-cli/docs/PATTERNS.md` | 상세 패턴 (필요시만) |
| 4 | `project-interface-cli/features/LIBRARY_RELEASE.md` | Library API 구현 상세 |
| 5 | `project-interface-cli/features/EXECUTION_RELEASE.md` | ExecutionMode 구현 상세 |

### 참조 불필요 (위 문서에 통합됨)

- `dataset.py`, `workflow.py` - 코드 템플릿이 PATTERNS.md에 있음
- `test_workflow_cmd.py` - 테스트 패턴이 cli_test_patterns에 있음
- `api/*.py` - Library API 패턴이 LIBRARY_RELEASE.md에 있음

### Pre-Implementation Checklist

1. **Read Serena memory** (`cli_patterns`) or PATTERNS.md
2. **Check existing enums** in `client.py` before creating new ones
3. **Check `commands/utils.py`** for shared helpers (`format_datetime`, etc.)

### CLI Commands (v0.9.1)

| Command | Description |
|---------|-------------|
| `dli version/info` | CLI version and environment |
| `dli config` | Settings management (show, show --show-source, env, init, set) |
| `dli metric` | Metric CRUD (list, get, run, validate, register, format, transpile) |
| `dli dataset` | Dataset CRUD (list, get, run, validate, register, format, transpile) |
| `dli workflow` | Server-based Airflow execution (run, backfill, stop, status, list, history, pause, unpause, register, unregister) |
| `dli catalog` | Data catalog browsing with implicit routing (1-4 part identifiers) |
| `dli lineage` | Dependency visualization (show, upstream, downstream) |
| `dli quality` | Quality Spec 실행 및 검증 (list, get, run, validate) |
| `dli query` | Query execution metadata (list, show, cancel) |
| `dli run` | Ad-hoc SQL file execution with result download |
| `dli debug` | Environment diagnostics (system, config, server, auth, network checks) |

### Library API (v0.9.1)

프로그래매틱 호출을 위한 Python Library Interface:

| API Class | Methods | Description |
|-----------|---------|-------------|
| `DatasetAPI` | list_datasets, get, run, run_sql, validate, register, render_sql, get_tables, get_columns, test_connection, format, transpile | Dataset CRUD + 실행 + 스키마 조회 + 포맷팅 + SQL 변환 |
| `MetricAPI` | list_metrics, get, run, validate, register, render_sql, get_tables, get_columns, test_connection, format, transpile | Metric CRUD + 실행 + 스키마 조회 + 포맷팅 + SQL 변환 |
| `CatalogAPI` | list_tables, get, search | 카탈로그 브라우징 |
| `ConfigAPI` | get, get_all, get_with_source, validate, get_environment, list_environments, get_current_environment, get_server_status | 설정 조회 + ENV 관리 |
| `QualityAPI` | list_qualities, get, run, validate | Quality Spec 실행 및 검증 |
| `WorkflowAPI` | get, register, unregister, run, backfill, stop, get_status, list_workflows, history, pause, unpause | Server-based workflow orchestration |
| `QueryAPI` | list_queries, get, cancel | Query execution metadata access |
| `RunAPI` | run, dry_run, render_sql | Ad-hoc SQL execution |
| `DebugAPI` | run_all, check_system, check_project, check_server, check_auth, check_connection, check_network | Environment diagnostics and connection testing |

**ExecutionMode (v0.2.1):**

| Mode | Description | Use Case |
|------|-------------|----------|
| `LOCAL` | 로컬 Query Engine 직접 실행 | BigQuery/Trino 직접 연결 |
| `SERVER` | Basecamp Server API 실행 | 프로덕션 환경 |
| `MOCK` | 테스트용 Mock 실행 | 단위 테스트 |

```python
from dli import DatasetAPI, ExecutionContext, ExecutionMode

# 환경변수 기반 자동 설정 (v0.9.0)
ctx = ExecutionContext.from_environment()  # DLI_* 환경변수 자동 로드

# 로컬 실행
ctx = ExecutionContext(
    execution_mode=ExecutionMode.LOCAL,
    project_path=Path("/opt/airflow/dags/models"),
)
api = DatasetAPI(context=ctx)
result = api.run("my_dataset", parameters={"date": "2025-01-01"})

# Mock 테스트 (DI 지원)
from dli.core.executor import MockExecutor
mock_executor = MockExecutor(mock_data=[{"id": 1}])
api = DatasetAPI(context=ctx, executor=mock_executor)
```

**Hierarchical Config (v0.9.0):**
- Priority: `~/.dli/config.yaml` < `dli.yaml` < `.dli.local.yaml` < ENV vars (`DLI_*`) < CLI args
- Template syntax: `${VAR}`, `${VAR:-default}`, `${VAR:?error}`
- Secret masking: `DLI_SECRET_*` prefix auto-masked in output

**Exception Hierarchy:** `DLIError` (base) with error codes (DLI-001 ~ DLI-8xx)

### Directory Structure

```
project-interface-cli/src/dli/
├── __init__.py           # Public exports (API classes, exceptions, models)
├── exceptions.py         # DLIError hierarchy (ErrorCode, typed exceptions)
├── models/
│   ├── __init__.py       # Model exports
│   └── common.py         # ExecutionContext, ResultStatus, *Result models
├── api/                  # Library API (v0.9.1)
│   ├── __init__.py       # API exports
│   ├── dataset.py        # DatasetAPI (includes transpile)
│   ├── metric.py         # MetricAPI (includes transpile)
│   ├── catalog.py        # CatalogAPI
│   ├── config.py         # ConfigAPI
│   ├── quality.py        # QualityAPI
│   ├── workflow.py       # WorkflowAPI
│   └── query.py          # QueryAPI
├── commands/             # CLI commands (Typer)
│   ├── __init__.py       # Export all *_app
│   ├── base.py           # Shared utilities (get_client, get_project_path)
│   ├── utils.py          # Rich output helpers (console, print_*)
│   ├── metric.py         # Metric CRUD commands (includes transpile subcommand)
│   ├── dataset.py        # Dataset CRUD commands (includes transpile subcommand)
│   ├── catalog.py        # Catalog browsing
│   ├── config.py         # Settings management
│   ├── lineage.py        # Lineage commands
│   ├── quality.py        # Quality test commands
│   ├── workflow.py       # Workflow operations
│   └── query.py          # Query metadata commands
├── core/
│   ├── __init__.py
│   ├── client.py         # BasecampClient (mock + real API)
│   ├── models/           # Shared data models (metric, dataset specs)
│   ├── validation/       # Spec and dependency validation
│   ├── lineage/          # Lineage client and models
│   ├── quality/          # Quality test executor, registry, builtin tests
│   ├── workflow/         # Workflow models and operations
│   └── catalog/          # Catalog models (TableInfo, TableDetail)
└── main.py               # Register subcommand apps here
```

### Full Patterns Documentation

See [project-interface-cli/docs/PATTERNS.md](./project-interface-cli/docs/PATTERNS.md) for complete templates and examples.

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

## Implementation Verification (CRITICAL)

> **"구현 완료" 선언 전 반드시 검증** - 거짓 보고 방지

### 위험 패턴 (절대 금지)

```
❌ "이미 구현되어 있습니다" → grep 확인 없이 판단
❌ "명세를 작성했습니다" → 코드 작성 없이 완료 선언
❌ "테스트가 통과합니다" → 실제 테스트 실행 없이 판단
```

### 구현 완료 선언 조건

"구현 완료" 또는 "done" 선언 시 반드시 아래 정보 제시:

| 항목 | 예시 |
|------|------|
| **새로 작성한 파일:라인** | `src/dli/models/common.py:405-485 (+81 lines)` |
| **수정한 파일:라인** | `src/dli/api/catalog.py:89-174` |
| **테스트 결과** | `pytest tests/api/ → 30 passed` |
| **검증 명령어** | `grep -r "ClassName" src/` 결과 |

### 구현 완료 체크리스트

```
□ grep으로 새 클래스/함수가 코드에 실제 존재하는지 확인
□ pytest 실행하여 테스트 통과 결과 제시
□ 변경된 파일:라인번호 목록 제시
□ (선택) git diff --stat 으로 변경 내역 요약
```

---

## Important Notes for AI Assistants

1. **Use agents and skills** - Leverage `.claude/agents/` for specialized tasks
2. **MCP-first exploration** - Use `serena.*` tools before reading full files
3. **Follow Architecture Patterns** - Use hexagonal architecture for basecamp-server
4. **Port awareness** - basecamp-server: 8080 (local) vs 8081 (Docker with Keycloak)
5. **Python package manager** - Use `uv` (not pip/poetry)
6. **Repository patterns** - Domain interfaces → infrastructure implementations → Spring Data composition
7. **Implementation verification** - 구현 완료 전 반드시 grep/pytest 결과 제시 (`.claude/skills/implementation-verification/`)

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
| CLI & Library API | [project-interface-cli/README.md](./project-interface-cli/README.md) |
| Development commands | [Makefile](./Makefile) (run `make help`) |

---

**Last Updated:** 2026-01-01

