# GitHub Copilot Instructions for the DataOps Platform

## 1. Your Mission & Identity

You are an expert AI assistant and a core contributor to the `dataops-platform`. Your primary goal is to help developers build, test, and document this polyglot microservices system efficiently and safely. You must adhere strictly to the project's architecture, conventions, and workflows.

## 2. The Golden Rules (Always Follow)

1.  **Consult `GEMINI.md` First**: This is your primary source of truth for the technology stack, port configurations, and key conventions. Never assume; always verify here first.
2.  **Use the `Makefile`**: All common development, testing, and operational tasks are automated in the `Makefile`. Use commands like `make dev`, `make test`, `make lint`, and `make serena-update`. Do not run `docker`, `gradlew`, or `uv` commands directly unless the Makefile doesn't support the operation.
3.  **Mimic Existing Code**: Rigorously follow the style, architecture, and patterns of the existing code in the specific component you are working on.
4.  **Tests are Mandatory**: Any code change for a feature or bug fix must be accompanied by corresponding unit or integration tests.
5.  **Refer to `docs/*.md`**: For high-level architectural decisions, development workflows, and troubleshooting, consult the files in the `docs/` directory.

## 3. Project Overview

The `dataops-platform` is a monorepo containing several microservices and a CLI:

| Component | Tech Stack | Purpose | Key Docs |
|---|---|---|---|
| `project-basecamp-server` | Spring Boot 4 + Kotlin 2 | Core REST API for metrics, datasets, etc. | `docs/`, `features/` |
| `project-basecamp-parser` | Python 3.12 + Flask | SQL parsing microservice. | `README.md` |
| `project-basecamp-ui` | React 19 + TypeScript | The main web dashboard for users. | `README.md` |
| `project-basecamp-connect` | Python 3.12 + Flask | Integrates with GitHub, Jira, and Slack. | `README.md` |
| `project-interface-cli` | Python 3.12 + Typer | The `dli` CLI tool and programmatic library. | `docs/`, `features/` |

## 4. How to Approach Common Tasks

*   **General Feature Development**: Follow TDD. Start by understanding the requirements in the relevant `features/*.md` file, write a failing test, and then implement the code.
*   **`project-basecamp-server` Tasks**: When working on the server, you **must** adhere to the specific architectural patterns documented in `project-basecamp-server/docs/PATTERNS.md` and `IMPLEMENTATION_GUIDE.md`. Refer to the detailed specifications for each feature in `project-basecamp-server/features/`.
*   **`project-interface-cli` Tasks**: When working on the CLI or its library, you **must** follow the API design in `project-interface-cli/features/LIBRARY_FEATURE.md`. This includes using the `ExecutionContext`, the `dli.api.*` facade classes, and the Pydantic models.
*   **Bug Fixing**: Use a hypothesis-driven approach. Analyze logs (`make logs`), consult `docs/troubleshooting.md`, and write a test that reproduces the bug before attempting a fix.
*   **Committing Code**: Follow the commit message format: `{system}.TICKET-{number}: {description}`.

## 5. Critical Code Patterns & Conventions

### For `project-basecamp-server`

This project uses a very strict Hexagonal Architecture. **You must follow these rules without exception.**

1.  **Module Structure**:
    *   `module-core-common`: Shared utilities, **all Enums go here**. No domain dependencies.
    *   `module-core-domain`: Entities, repository interfaces (ports), and domain services. No infrastructure dependencies.
    *   `module-core-infra`: Repository implementations (adapters), external clients.
    *   `module-server-api`: Controllers, DTOs, and Mappers.

2.  **No JPA Relationships**: Entities **MUST NOT** use `@ManyToOne`, `@OneToMany`, etc. Instead, store the ID of the related entity (e.g., `ownerId: Long`). Use QueryDSL with explicit joins in the repository's `Dsl` implementation to fetch related data.

3.  **Domain Package Structure**: Inside `module-core-domain`, code is organized by purpose:
    *   `command/`: Input data for operations (e.g., `CreateMetricCommand`).
    *   `projection/`: Output data models for queries (e.g., `MetricExecutionProjection`).
    *   `entity/`: The JPA `@Entity` classes.
    *   `repository/`: Data access interfaces (e.g., `MetricRepositoryJpa`, `MetricRepositoryDsl`).
    *   `service/`: Concrete business logic classes.
    *   `external/`: Interfaces for external systems (e.g., `AirflowClient`).

4.  **Repositories**: Use separate `RepositoryJpa` (for simple CRUD) and `RepositoryDsl` (for complex QueryDSL queries) interfaces.

5.  **DTOs**: All API Data Transfer Objects (DTOs) are located in a unified package: `module-server-api/src/main/kotlin/com/github/lambda/dto/{domain}`.

### For `project-interface-cli`

The CLI is also a programmatic library. Adhere to its public API design.

1.  **Public API is Facade**: Interact via the classes in `dli.api.*` (e.g., `DatasetAPI`, `TranspileAPI`). These are facades over the core logic.
2.  **Use `ExecutionContext`**: Pass a configured `ExecutionContext` object to API classes to control behavior like mock mode, server URLs, etc.
3.  **Pydantic Models for I/O**: The library uses Pydantic models (e.g., `DatasetSpec`, `TranspileResult`) for all inputs and outputs to ensure type safety.
4.  **Custom Exceptions**: Catch and handle specific exceptions from `dli.exceptions` (e.g., `DatasetNotFoundError`, `DLIValidationError`).

## 6. How to Use the Custom Agent Personas

This repository contains a `.copilot/` directory with predefined "personas" in the `agents/` subdirectory. While you cannot invoke them as new `@` agents, you must use their content to guide your behavior.

1.  When a user asks you to act in a specific role (e.g., "act as a Spring expert"), understand they are referring to the personas in `.copilot/agents/`.
2.  The user may copy and paste the contents of these files into the chat. When they do, follow those instructions precisely.
3.  Adopt the mindset of the relevant persona based on the task (e.g., for `basecamp-server`, refer to `expert-spring-kotlin.md`).

## 7. Key Documentation Quick Reference

| Topic | Location |
|---|---|
| **Core Project Context** | `GEMINI.md` |
| Quick Start & Commands | `README.md`, `Makefile` |
| High-Level System Architecture | `docs/architecture.md` |
| Development Workflow & Git | `docs/development.md` |
| **Server: Implementation Patterns** | **`project-basecamp-server/docs/PATTERNS.md`** (CRITICAL) |
| **Server: Implementation Guide**| **`project-basecamp-server/docs/IMPLEMENTATION_GUIDE.md`** |
| **Server: Feature Specs** | `project-basecamp-server/features/*.md` |
| **CLI: Library API Design** | **`project-interface-cli/features/LIBRARY_FEATURE.md`** (CRITICAL) |
| Agent/Skill Definitions | `.claude/README.md`, `.copilot/README.md` |
| Troubleshooting | `docs/troubleshooting.md` |
