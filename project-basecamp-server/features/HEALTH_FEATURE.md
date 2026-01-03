# Health API Feature Specification

> **Version:** 1.0.0 | **Status:** ✅ Implemented | **Priority:** P0 Critical
> **CLI Command:** `dli debug` | **Target:** Spring Boot 4 + Kotlin 2
> **Implementation:** Week 2.5 (Completed 2026-01-03)

> **📦 Implementation:** [`HEALTH_RELEASE.md`](./HEALTH_RELEASE.md) - Full implementation details, test coverage, architecture

---

## 1. Overview

### 1.1 Purpose

The Health API provides system health status and diagnostics endpoints for:
- Load balancer health checks (basic health)
- CLI `dli debug` command diagnostics (extended health)
- Monitoring and alerting integration
- Component status verification (database, Redis, Airflow)

### 1.2 Scope

| Feature | Priority | CLI Integration | Status |
|---------|----------|-----------------|--------|
| Basic Health Check | P0 | `dli debug --server` | ✅ Implemented |
| Extended Health Check | P0 | `dli debug` (full diagnostics) | ✅ Implemented |
| Component Health Details | P0 | `dli debug --verbose` | ✅ Implemented |
| System Resource Metrics | P1 | `dli debug --json` | ⏳ Deferred |

### 1.3 Implementation Status

| Endpoint | Status | Notes |
|----------|--------|-------|
| `GET /api/health` | ✅ Implemented | Legacy endpoint (backward compatible) |
| `GET /api/info` | ✅ Implemented | Build info available |
| `GET /api/v1/health` | ✅ Implemented | Component health checks |
| `GET /api/v1/health/extended` | ✅ Implemented | Extended diagnostics for `dli debug` |

---

## 2. CLI Command Mapping

### 2.1 `dli debug` Command Options

| CLI Option | Server Endpoint | Status |
|------------|-----------------|--------|
| `dli debug` | `GET /api/v1/health/extended` | ✅ Ready |
| `dli debug --server` | `GET /api/v1/health` | ✅ Ready |
| `dli debug --connection` | N/A (client-side) | N/A |
| `dli debug --auth` | N/A (client-side) | N/A |
| `dli debug --network` | N/A (client-side) | N/A |
| `dli debug --json` | Same + JSON format | ✅ Ready |

---

## 3. API Specifications

> **Note:** See [`HEALTH_RELEASE.md`](./HEALTH_RELEASE.md) for actual response examples and implementation details.

### 3.1 Basic Health Check

**Endpoint:** `GET /api/v1/health`
**Authentication:** None required (public endpoint for load balancers)

**Response:**
- `200 OK` - All components UP or UNKNOWN
- `503 Service Unavailable` - Any component DOWN

**Components Checked:** database, redis, airflow

### 3.2 Extended Health Check

**Endpoint:** `GET /api/v1/health/extended`
**Authentication:** Required (OAuth2 Bearer token)

**Response includes:**
- Overall status (UP, DOWN, UNKNOWN)
- Version info (API version, build time)
- Component details (database pool, Redis mode/version, Airflow status)

---

## 4. Architecture

### 4.1 Hexagonal Architecture (Implemented)

```
Domain Layer (module-core-domain)
├── model/health/HealthStatus.kt         # UP, DOWN, UNKNOWN enum
├── model/health/ComponentHealth.kt      # Component health value object
├── port/HealthIndicator.kt              # Port interface
└── service/HealthService.kt             # Domain service

Infrastructure Layer (module-core-infra)
└── health/
    ├── DatabaseHealthIndicator.kt       # HikariCP adapter
    ├── RedisHealthIndicator.kt          # Redis adapter
    └── AirflowHealthIndicator.kt        # Mock adapter (MVP)

API Layer (module-server-api)
├── dto/health/HealthDtos.kt             # Response DTOs
└── controller/HealthController.kt       # REST controller
```

---

## 5. Testing

**Total Tests:** 58

| Test Class | Tests |
|------------|-------|
| `HealthServiceTest` | 16 |
| `DatabaseHealthIndicatorTest` | 8 |
| `RedisHealthIndicatorTest` | 10 |
| `AirflowHealthIndicatorTest` | 6 |
| `HealthControllerTest` | 18 |

---

## 6. Related Documents

| Document | Purpose |
|----------|---------|
| [`HEALTH_RELEASE.md`](./HEALTH_RELEASE.md) | Implementation details |
| [`CLI_API_MAPPING.md`](../docs/CLI_API_MAPPING.md) | CLI to API mapping |
| [`ERROR_CODES.md`](./ERROR_CODES.md) | Error handling standards |

---

## Appendix: Review Feedback (Resolved)

> **Reviewed by:** feature-basecamp-server Agent | **Date:** 2026-01-01 | **Rating:** 4.5/5

### Issues Addressed

| Priority | Issue | Resolution |
|----------|-------|------------|
| **High** | `ExtendedHealthService` in domain layer has infrastructure dependencies - violates hexagonal architecture | ✅ Resolved: Created `HealthIndicator` port in domain, implementations in infrastructure |
| **Medium** | Error codes don't align with ERROR_CODES.md patterns | ✅ Resolved: Using standard HTTP status codes (200, 503) |
| **Low** | DTOs not following `*Dto` suffix consistently | ✅ Resolved: Consistent naming in `HealthDtos.kt` |

---

*Implementation completed: 2026-01-03 | See [`HEALTH_RELEASE.md`](./HEALTH_RELEASE.md) for full details*
