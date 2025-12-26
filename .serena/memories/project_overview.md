# DataOps Platform Project Overview

## Purpose
DataOps Platform is a polyglot microservices-based platform for DataOps workflows, organized as a monorepo with multiple components.

## Components
- **project-basecamp-server**: Spring Boot 4 + Kotlin 2 (multi-module API server)
- **project-basecamp-parser**: Python 3.12 + Flask (SQL parsing microservice)  
- **project-basecamp-ui**: React 19 + TypeScript (web dashboard)
- **project-interface-cli**: Python 3.12 + Typer (CLI tool named `dli`)

## Technology Stack

| Component | Languages | Key Frameworks | Build Tool |
|-----------|-----------|----------------|------------|
| basecamp-server | Kotlin 2.2.21 | Spring Boot 4.0.1 | Gradle 9.2.1 |
| basecamp-parser | Python 3.12 | Flask 3.1.2, SQLglot | uv |
| basecamp-ui | TypeScript | React 19.2.3, Vite 7.3.0 | npm/pnpm |
| interface-cli | Python 3.12 | Typer, Rich | uv |

## Architecture
project-basecamp-server follows Pure Hexagonal Architecture:
- Services are concrete classes (no interfaces)
- Domain layer has zero infrastructure imports
- Repository pattern: domain interfaces → infrastructure implementations
- DTOs should be in API layer, not domain layer
- Entities are returned from domain layer, DTOs used at API boundaries