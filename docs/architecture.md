# System Architecture & Design

> **Last Updated:** 2026-01-01
> **Target Audience:** System architects, tech leads, senior developers
> **Purpose:** High-level design principles, policies, and architectural decisions

---

## Table of Contents

1. [System Purpose & Scope](#system-purpose--scope)
2. [Design Principles](#design-principles)
3. [Architecture Overview](#architecture-overview)
4. [Server Policies](#server-policies)
5. [Key Architectural Decisions](#key-architectural-decisions)
6. [Base Configuration](#base-configuration)

---

## System Purpose & Scope

### Mission Statement

The **DataOps Platform** provides a unified interface for data operations, workflow orchestration, and metadata management across BigQuery, Trino, and Airflow ecosystems.

**Basecamp Server API** bridges the CLI client (`dli`) and the DataOps Platform infrastructure, providing a unified REST interface for data operations, workflow orchestration, and metadata management.

### Scope Definition

**In Scope:**
- REST API endpoints supporting all `dli` commands
- OAuth2 authentication and authorization
- Data catalog and lineage operations
- Workflow orchestration through Airflow
- Quality testing and validation
- Query execution and metadata management

**Out of Scope:**
- Direct database access from CLI
- Client-side caching beyond configuration
- Real-time notifications or WebSocket connections
- Workflow definition language (uses existing YAML specs)

### API Source of Truth

| Component | Source | Purpose |
|-----------|--------|---------|
| **API Contracts** | `project-interface-cli/src/dli/core/client.py` | Mock implementations define expected request/response shapes |
| **Response Models** | `BasecampClient.ServerResponse` dataclass | Standard response wrapper |
| **Enums & Constants** | `WorkflowSource`, `RunStatus` enums | Allowed values and state definitions |

---

## Design Principles

### Core Principles

| Principle | Description | Implementation Note |
|-----------|-------------|-------------------|
| **RESTful Design** | Standard HTTP methods (GET, POST, DELETE) with resource-based URLs | Follow existing `basecamp-server` patterns |
| **JSON API** | All requests/responses use `application/json` | Use `@RestController` with proper serialization |
| **Consistent Pagination** | Use `limit` and `offset` query parameters | Default: `limit=50`, `offset=0` |
| **Error Codes** | Standard HTTP status codes with structured error responses | See [ERROR_HANDLING.md](./ERROR_HANDLING.md) for details |
| **Stateless** | Server does not maintain client session state | OAuth2 token-based authentication |
| **Hexagonal Architecture** | Domain services as concrete classes, repository interfaces | Follow existing `PipelineService` pattern |

### Quality Attributes

| Attribute | Target | Measurement |
|-----------|--------|-------------|
| **Performance** | < 2s response time (95th percentile) | API gateway metrics |
| **Availability** | 99.9% uptime | Health check success rate |
| **Security** | OAuth2 + RBAC | Authentication/authorization coverage |
| **Scalability** | 1000 concurrent requests | Load testing benchmarks |
| **Maintainability** | Hexagonal architecture compliance | Code review checklist |

### API Design Standards

```http
# Standard Request Format
GET /api/v1/{resource}?limit=50&offset=0&filter=value
Authorization: Bearer <oauth2-token>
Content-Type: application/json

# Standard Response Format (Success)
HTTP/1.1 200 OK
Content-Type: application/json
{
  "data": [...],
  "metadata": {
    "total": 150,
    "limit": 50,
    "offset": 0
  }
}

# Standard Response Format (Error)
HTTP/1.1 400 Bad Request
Content-Type: application/json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid parameter 'limit'",
    "details": {"parameter": "limit", "value": "-1"}
  }
}
```

---

## Architecture Overview

### Hexagonal Architecture Pattern

```
┌─────────────────────────────────────┐
│           API Layer (Ports)         │
│  ┌─────────────────────────────────┐ │
│  │      REST Controllers          │ │
│  │  @RestController               │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
                    │
┌─────────────────────────────────────┐
│        Domain Layer (Core)          │
│  ┌─────────────────────────────────┐ │
│  │     Services (Concrete)        │ │
│  │  @Service @Transactional       │ │
│  │                                │ │
│  │  Repository Interfaces         │ │
│  │  (Domain Contracts)            │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
                    │
┌─────────────────────────────────────┐
│     Infrastructure (Adapters)      │
│  ┌─────────────────────────────────┐ │
│  │   Repository Implementations   │ │
│  │  @Repository + Spring Data     │ │
│  │                                │ │
│  │  External Service Clients      │ │
│  │  (Airflow, Keycloak, etc.)     │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### Service Layer Pattern

```kotlin
// ✅ Services are CONCRETE CLASSES (no interfaces)
@Service
@Transactional(readOnly = true)
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,  // Inject domain interfaces
    private val metricRepositoryDsl: MetricRepositoryDsl,  // Complex queries
) {
    @Transactional
    fun createMetric(command: CreateMetricCommand): MetricDto {
        // Domain logic implementation
    }

    fun getMetric(query: GetMetricQuery): MetricDto? {
        // Query implementation
    }
}
```

**Dataset Service Example:**
```kotlin
@Service
@Transactional(readOnly = true)
class DatasetService(
    private val datasetRepositoryJpa: DatasetRepositoryJpa,  // Inject domain interfaces
    private val datasetRepositoryDsl: DatasetRepositoryDsl,  // Complex queries
) {
    @Transactional
    fun createDataset(command: CreateDatasetCommand): DatasetDto {
        // Business validation (email, cron, naming patterns)
        // Domain logic implementation
    }

    fun getDataset(query: GetDatasetQuery): DatasetDto? {
        // Query implementation with soft delete handling
    }

    fun executeDataset(command: ExecuteDatasetCommand): ExecutionResult {
        // SQL execution with parameter substitution
    }
}
```

### Repository Layer Structure

```
Domain Layer (module-core-domain/repository/)
├── MetricRepositoryJpa.kt     # Simple CRUD operations (interface)
├── MetricRepositoryDsl.kt     # Complex queries (interface)
├── DatasetRepositoryJpa.kt    # Dataset CRUD operations (interface)
└── DatasetRepositoryDsl.kt    # Dataset complex queries (interface)

Infrastructure Layer (module-core-infra/repository/)
├── MetricRepositoryJpaImpl.kt         # Domain interface implementation (class)
├── MetricRepositoryJpaSpringData.kt   # Spring Data JPA interface
├── MetricRepositoryDslImpl.kt         # QueryDSL implementation (class)
├── DatasetRepositoryJpaImpl.kt        # Dataset domain interface implementation (class)
├── DatasetRepositoryJpaSpringData.kt  # Dataset Spring Data JPA interface
└── DatasetRepositoryDslImpl.kt        # Dataset QueryDSL implementation (class)
```

### Integration Points

| External System | Integration Method | Auth Method | Purpose |
|----------------|-------------------|-------------|---------|
| **Keycloak** | REST API | Service Account | OAuth2 token validation |
| **Airflow** | REST API | Basic Auth | Workflow orchestration |
| **BigQuery/Trino** | SDK/Driver | Service Account | Query execution |
| **MySQL** | JPA/JDBC | Connection Pool | Metadata storage |
| **Redis** | Lettuce Client | No auth (internal) | Caching layer |

---

## Current Implementation Status

### ✅ Completed APIs (28% - 10/36 endpoints)

| API Category | Status | Endpoints | Features | Documentation |
|--------------|--------|-----------|----------|---------------|
| **Health API** | ✅ Complete | 2/2 | Basic health checks, system info | Built-in |
| **Metrics API** | ✅ 80% Complete | 4/5 | CRUD, SQL execution, business validation | [METRIC_RELEASE.md](../project-basecamp-server/features/METRIC_RELEASE.md) |
| **Datasets API** | ✅ Complete | 4/4 | CRUD, SQL execution with parameters, validation | [DATASET_RELEASE.md](../project-basecamp-server/features/DATASET_RELEASE.md) |

### 🚧 In Development

| API Category | Priority | Estimated Timeline | Key Features |
|--------------|----------|-------------------|--------------|
| **Catalog API** | P1 High | Week 3-4 | BigQuery/Trino metadata, PII masking |
| **Lineage API** | P1 High | Week 5 | Dependency graphs, SQL parsing |
| **Workflow API** | P2 Medium | Week 6-9 | Airflow integration, execution control |

### 🏗️ Architecture Maturity

| Component | Maturity | Description |
|-----------|----------|-------------|
| **Hexagonal Architecture** | ✅ Production Ready | Pure ports and adapters pattern implemented |
| **Exception Handling** | ✅ Production Ready | Centralized error handling with proper HTTP status codes |
| **Testing Framework** | ✅ Production Ready | 80+ tests across service, repository, and controller layers |
| **Security Integration** | ✅ Production Ready | OAuth2 authentication, role-based authorization |
| **Database Schema** | ✅ Production Ready | Flyway migrations, audit fields, soft delete patterns |

### Key Accomplishments

- **✅ Complete Dataset API Foundation**: 4/4 endpoints with comprehensive business validation
- **✅ Robust Testing Suite**: 80+ tests covering all architectural layers
- **✅ Exception Refactoring**: Centralized error handling in common module
- **✅ Repository Pattern Fixes**: Eliminated SpringData exposure to domain layer
- **✅ Cross-Review Validation**: Multi-agent review process completed
- **✅ Production-Ready Infrastructure**: 98% infrastructure readiness

---

## Server Policies

### PII Masking Policy

**Application Scope:** Catalog API responses

| Rule | Implementation | Example |
|------|---------------|---------|
| **Column Detection** | Columns with `is_pii: true` metadata are masked | `user_email` → `***@***.***` |
| **Pattern Detection** | Email, phone, SSN patterns auto-detected | `555-1234` → `***-****` |
| **Sample Data** | All PII values replaced with `***` in responses | `John Doe` → `***` |
| **Query Results** | Full masking for `include_sample=true` responses | See [project-basecamp-server/features/CATALOG_FEATURE.md](../project-basecamp-server/features/CATALOG_FEATURE.md) |

### Workflow Source Type Policy

**Application Scope:** Workflow API operations

| Source Type | Control Level | API Permissions | S3 Location |
|-------------|---------------|----------------|-------------|
| **CODE** | Limited | Pause/Unpause only | `s3://bucket/workflows/code/` |
| **MANUAL** | Full | All CRUD operations | `s3://bucket/workflows/manual/` |
| **Priority Rule** | CODE overrides MANUAL when both exist | Auto-fallback to MANUAL when CODE deleted | |

### Query Scope Policy

**Application Scope:** Query Metadata API access control

| Scope | Required Permission | Description |
|-------|-------------------|-------------|
| `my` | All authenticated users | User's own queries |
| `system` | All authenticated users | System-generated queries |
| `user` | `query:read:all` role | All user queries |
| `all` | `query:read:all` role | System + user queries |

### Transpile Rule Policy

**Application Scope:** Transpile API rule management

| Policy | Implementation | Benefit |
|--------|---------------|---------|
| **Version Control** | Rules versioned with format `YYYY-MM-DD-NNN` | Rollback capability |
| **Deployment** | Rules deployed via CI/CD only | Consistency across environments |
| **Client Sync** | CLI fetches and caches rules locally | Offline transpilation support |
| **Priority Order** | Rule array order determines application precedence | Predictable transformation behavior |

---

## Key Architectural Decisions

### Technical Decisions

| Decision Area | Choice | Alternative Considered | Rationale |
|---------------|--------|----------------------|-----------|
| **API Versioning** | URL path (`/api/v1`) | Header versioning | Simpler client implementation, clearer routing |
| **Pagination** | `limit`/`offset` | Cursor-based | Sufficient for current scale, simpler implementation |
| **Error Format** | Structured JSON with error codes | Simple HTTP status only | Better debugging, client-side error handling |
| **Authentication** | OAuth2 + API Key hybrid | OAuth2 only | Flexibility for service-to-service auth |
| **State Management** | Stateless with token validation | Session-based | Better scalability, simpler deployment |

### Security Decisions

| Security Aspect | Implementation | Justification |
|------------------|----------------|---------------|
| **PII Protection** | Server-side masking only | Guarantee data protection regardless of client bugs |
| **Authorization** | Role-based access control (RBAC) | Fine-grained permissions for different user types |
| **API Rate Limiting** | 1000 req/min per user | Prevent abuse while supporting normal usage |
| **Audit Logging** | All mutations logged | Compliance and debugging requirements |
| **HTTPS Only** | TLS 1.2+ required | Data in transit protection |

### Performance Decisions

| Performance Aspect | Implementation | Trade-off |
|--------------------|----------------|-----------|
| **Workflow Control** | Server-mediated Airflow calls | Direct client access vs centralized audit |
| **Metadata Caching** | Redis for catalog data | Memory usage vs query speed |
| **Connection Pooling** | HikariCP with 20 max connections | Resource usage vs concurrent capacity |
| **Query Timeout** | 30s for ad-hoc queries | User experience vs resource protection |

---

## Base Configuration

### URL Structure

```bash
# Production Environment
Base URL: https://basecamp.example.com/api/v1

# Development Environments
Docker:   http://localhost:8081/api/v1  # With Keycloak
Local:    http://localhost:8080/api/v1  # Direct Spring Boot
```

### Authentication Methods

| Method | Header Format | Use Case | Example |
|--------|---------------|----------|---------|
| **OAuth2** | `Authorization: Bearer <jwt-token>` | User authentication | CLI user sessions |
| **API Key** | `X-API-Key: <service-key>` | Service-to-service | Internal service calls |

### Standard Response Headers

```http
# All responses include:
Content-Type: application/json
X-Request-ID: <uuid>
X-Response-Time-Ms: <milliseconds>
Cache-Control: no-cache  # For mutable resources
```

### Environment Variables

| Variable | Purpose | Default | Required |
|----------|---------|---------|----------|
| `KEYCLOAK_URL` | OAuth2 provider | `http://localhost:8080` | Yes |
| `AIRFLOW_URL` | Workflow orchestrator | `http://localhost:8082` | Yes |
| `REDIS_URL` | Cache server | `redis://localhost:6379` | No |
| `API_RATE_LIMIT` | Requests per minute | `1000` | No |

---

## Related Documentation

### Implementation Guides
- **Implementation Patterns**: [IMPLEMENTATION_GUIDE.md](./IMPLEMENTATION_GUIDE.md) - Service, repository, and controller patterns
- **Error Handling**: [ERROR_HANDLING.md](./ERROR_HANDLING.md) - Error codes and exception handling
- **Development Patterns**: [../project-basecamp-server/docs/PATTERNS.md](../project-basecamp-server/docs/PATTERNS.md) - Quick reference templates

### Project-Specific Documentation
- **Basecamp Server**: [../project-basecamp-server/README.md](../project-basecamp-server/README.md) - Module structure and quick start
- **Interface CLI**: [../project-interface-cli/README.md](../project-interface-cli/README.md) - CLI commands and Library API

### Feature Specifications
- **Implementation Plan**: [../project-basecamp-server/features/IMPLEMENTATION_PLAN.md](../project-basecamp-server/features/IMPLEMENTATION_PLAN.md)
- **API Endpoints**: [../project-basecamp-server/features/METRIC_FEATURE.md](../project-basecamp-server/features/METRIC_FEATURE.md), [DATASET_FEATURE.md](../project-basecamp-server/features/DATASET_FEATURE.md)

---

*This architecture overview provides the foundation for implementing the DataOps Platform according to hexagonal architecture principles and enterprise-grade quality standards.*
