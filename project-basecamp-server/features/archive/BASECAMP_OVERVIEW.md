# Basecamp Server API - Architecture & Design Overview

> **Target Audience:** System architects, tech leads, senior developers
> **Purpose:** High-level design principles, policies, and architectural decisions
> **Cross-Reference:** [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for execution roadmap

---

## 📋 Table of Contents

1. [System Purpose & Scope](#1-system-purpose--scope)
2. [Design Principles](#2-design-principles)
3. [Architecture Overview](#3-architecture-overview)
4. [Server Policies](#4-server-policies)
5. [Key Decisions Summary](#5-key-decisions-summary)
6. [Base Configuration](#6-base-configuration)

---

## 1. System Purpose & Scope

### 1.1 Mission Statement

The **Basecamp Server API** bridges the CLI client (`dli`) and the DataOps Platform infrastructure, providing a unified REST interface for data operations, workflow orchestration, and metadata management.

### 1.2 Scope Definition

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

### 1.3 API Source of Truth

| Component | Source | Purpose |
|-----------|--------|---------|
| **API Contracts** | `project-interface-cli/src/dli/core/client.py` | Mock implementations define expected request/response shapes |
| **Response Models** | `BasecampClient.ServerResponse` dataclass | Standard response wrapper |
| **Enums & Constants** | `WorkflowSource`, `RunStatus` enums | Allowed values and state definitions |

---

## 2. Design Principles

### 2.1 Core Principles

| Principle | Description | Implementation Note |
|-----------|-------------|-------------------|
| **RESTful Design** | Standard HTTP methods (GET, POST, DELETE) with resource-based URLs | Follow existing `basecamp-server` patterns |
| **JSON API** | All requests/responses use `application/json` | Use `@RestController` with proper serialization |
| **Consistent Pagination** | Use `limit` and `offset` query parameters | Default: `limit=50`, `offset=0` |
| **Error Codes** | Standard HTTP status codes with structured error responses | See [`ERROR_CODES.md`](./ERROR_CODES.md) for details |
| **Stateless** | Server does not maintain client session state | OAuth2 token-based authentication |
| **Hexagonal Architecture** | Domain services as concrete classes, repository interfaces | Follow existing `PipelineService` pattern |

### 2.2 Quality Attributes

| Attribute | Target | Measurement |
|-----------|--------|-------------|
| **Performance** | < 2s response time (95th percentile) | API gateway metrics |
| **Availability** | 99.9% uptime | Health check success rate |
| **Security** | OAuth2 + RBAC | Authentication/authorization coverage |
| **Scalability** | 1000 concurrent requests | Load testing benchmarks |
| **Maintainability** | Hexagonal architecture compliance | Code review checklist |

### 2.3 API Design Standards

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

## 3. Architecture Overview

### 3.1 Hexagonal Architecture Pattern

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

### 3.2 Service Layer Pattern

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

### 3.3 Repository Layer Structure

```
Domain Layer (module-core-domain/repository/)
├── MetricRepositoryJpa.kt     # Simple CRUD operations (interface)
└── MetricRepositoryDsl.kt     # Complex queries (interface)

Infrastructure Layer (module-core-infra/repository/)
├── MetricRepositoryJpaImpl.kt         # Domain interface implementation (class)
├── MetricRepositoryJpaSpringData.kt   # Spring Data JPA interface
└── MetricRepositoryDslImpl.kt         # QueryDSL implementation (class)
```

### 3.4 Integration Points

| External System | Integration Method | Auth Method | Purpose |
|----------------|-------------------|-------------|---------|
| **Keycloak** | REST API | Service Account | OAuth2 token validation |
| **Airflow** | REST API | Basic Auth | Workflow orchestration |
| **BigQuery/Trino** | SDK/Driver | Service Account | Query execution |
| **MySQL** | JPA/JDBC | Connection Pool | Metadata storage |
| **Redis** | Lettuce Client | No auth (internal) | Caching layer |

---

## 4. Server Policies

### 4.1 PII Masking Policy

**Application Scope:** Catalog API responses

| Rule | Implementation | Example |
|------|---------------|---------|
| **Column Detection** | Columns with `is_pii: true` metadata are masked | `user_email` → `***@***.***` |
| **Pattern Detection** | Email, phone, SSN patterns auto-detected | `555-1234` → `***-****` |
| **Sample Data** | All PII values replaced with `***` in responses | `John Doe` → `***` |
| **Query Results** | Full masking for `include_sample=true` responses | See [`CATALOG_FEATURE.md`](./CATALOG_FEATURE.md) |

### 4.2 Workflow Source Type Policy

**Application Scope:** Workflow API operations

| Source Type | Control Level | API Permissions | S3 Location |
|-------------|---------------|----------------|-------------|
| **CODE** | Limited | Pause/Unpause only | `s3://bucket/workflows/code/` |
| **MANUAL** | Full | All CRUD operations | `s3://bucket/workflows/manual/` |
| **Priority Rule** | CODE overrides MANUAL when both exist | Auto-fallback to MANUAL when CODE deleted | |

### 4.3 Query Scope Policy

**Application Scope:** Query Metadata API access control

| Scope | Required Permission | Description |
|-------|-------------------|-------------|
| `my` | All authenticated users | User's own queries |
| `system` | All authenticated users | System-generated queries |
| `user` | `query:read:all` role | All user queries |
| `all` | `query:read:all` role | System + user queries |

### 4.4 Transpile Rule Policy

**Application Scope:** Transpile API rule management

| Policy | Implementation | Benefit |
|--------|---------------|---------|
| **Version Control** | Rules versioned with format `YYYY-MM-DD-NNN` | Rollback capability |
| **Deployment** | Rules deployed via CI/CD only | Consistency across environments |
| **Client Sync** | CLI fetches and caches rules locally | Offline transpilation support |
| **Priority Order** | Rule array order determines application precedence | Predictable transformation behavior |

---

## 5. Key Decisions Summary

### 5.1 Architectural Decisions

| Decision Area | Choice | Alternative Considered | Rationale |
|---------------|--------|----------------------|-----------|
| **API Versioning** | URL path (`/api/v1`) | Header versioning | Simpler client implementation, clearer routing |
| **Pagination** | `limit`/`offset` | Cursor-based | Sufficient for current scale, simpler implementation |
| **Error Format** | Structured JSON with error codes | Simple HTTP status only | Better debugging, client-side error handling |
| **Authentication** | OAuth2 + API Key hybrid | OAuth2 only | Flexibility for service-to-service auth |
| **State Management** | Stateless with token validation | Session-based | Better scalability, simpler deployment |

### 5.2 Security Decisions

| Security Aspect | Implementation | Justification |
|------------------|----------------|---------------|
| **PII Protection** | Server-side masking only | Guarantee data protection regardless of client bugs |
| **Authorization** | Role-based access control (RBAC) | Fine-grained permissions for different user types |
| **API Rate Limiting** | 1000 req/min per user | Prevent abuse while supporting normal usage |
| **Audit Logging** | All mutations logged | Compliance and debugging requirements |
| **HTTPS Only** | TLS 1.2+ required | Data in transit protection |

### 5.3 Performance Decisions

| Performance Aspect | Implementation | Trade-off |
|--------------------|----------------|-----------|
| **Workflow Control** | Server-mediated Airflow calls | Direct client access vs centralized audit |
| **Metadata Caching** | Redis for catalog data | Memory usage vs query speed |
| **Connection Pooling** | HikariCP with 20 max connections | Resource usage vs concurrent capacity |
| **Query Timeout** | 30s for ad-hoc queries | User experience vs resource protection |

---

## 6. Base Configuration

### 6.1 URL Structure

```bash
# Production Environment
Base URL: https://basecamp.example.com/api/v1

# Development Environments
Docker:   http://localhost:8081/api/v1  # With Keycloak
Local:    http://localhost:8080/api/v1  # Direct Spring Boot
```

### 6.2 Authentication Methods

| Method | Header Format | Use Case | Example |
|--------|---------------|----------|---------|
| **OAuth2** | `Authorization: Bearer <jwt-token>` | User authentication | CLI user sessions |
| **API Key** | `X-API-Key: <service-key>` | Service-to-service | Internal service calls |

### 6.3 Standard Response Headers

```http
# All responses include:
Content-Type: application/json
X-Request-ID: <uuid>
X-Response-Time-Ms: <milliseconds>
Cache-Control: no-cache  # For mutable resources
```

### 6.4 Environment Variables

| Variable | Purpose | Default | Required |
|----------|---------|---------|----------|
| `KEYCLOAK_URL` | OAuth2 provider | `http://localhost:8080` | Yes |
| `AIRFLOW_URL` | Workflow orchestrator | `http://localhost:8082` | Yes |
| `REDIS_URL` | Cache server | `redis://localhost:6379` | No |
| `API_RATE_LIMIT` | Requests per minute | `1000` | No |

---

## 🔗 Related Documentation

- **Implementation Roadmap**: [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md)
- **Spring Boot Patterns**: [`INTEGRATION_PATTERNS.md`](./INTEGRATION_PATTERNS.md)
- **Error Handling**: [`ERROR_CODES.md`](./ERROR_CODES.md)
- **API Endpoints**: [`METRIC_FEATURE.md`](./METRIC_FEATURE.md), [`DATASET_FEATURE.md`](./DATASET_FEATURE.md)
- **CLI Mapping**: [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md)

---

*This architecture overview provides the foundation for implementing the Basecamp Server API according to DataOps Platform standards and hexagonal architecture principles.*