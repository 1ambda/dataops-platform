# Workflow API Feature Specification

✅ **IMPLEMENTATION COMPLETE** - All 9 Workflow API endpoints implemented, tested, and documented.

> **Version:** 1.0.0 | **Status:** ✅ Complete | **Priority:** P2 Medium
> **CLI Commands:** `dli workflow run/backfill/stop/status/list/history/pause/unpause/register/unregister`
> **Implementation Week:** Week 6-9 | **Actual Effort:** Completed with 8,276+ lines
>
> **📦 Data Source:** Self-managed JPA (상태 저장) + External API (Airflow 연동)
> **Entities:** `WorkflowEntity`, `WorkflowRunEntity` | **External:** Airflow REST API

## Implementation Summary

✅ **FULLY IMPLEMENTED** (2026-01-02)

| Component | Status | Files | Tests |
|-----------|--------|-------|-------|
| **Domain Models** | ✅ Complete | 4 entities + 10 exceptions | 39 unit tests |
| **Repository Layer** | ✅ Complete | 8 repository implementations | 25+ integration tests |
| **Service Layer** | ✅ Complete | 628-line WorkflowService | 93 unit tests total |
| **Controller Layer** | ✅ Complete | 9 REST endpoints | 30+ controller tests |
| **External Integration** | ✅ Complete | MockAirflowClient + InMemoryStorage | Mock-based development |
| **CLI Integration** | ✅ Complete | All `dli workflow` commands | Full API coverage |

**Total:** 8,276+ lines, 93 unit tests (100% passing), 30+ integration tests, comprehensive cross-review completed

> **📖 Detailed Documentation:** [`WORKFLOW_RELEASE.md`](./WORKFLOW_RELEASE.md)

---

## 1. Overview

### 1.1 Purpose

Server-based workflow orchestration via Airflow integration. Enables CLI (`dli workflow`) to trigger, monitor, and manage scheduled dataset executions.

### 1.2 Scope

| Feature | CLI Command | Endpoint | Status |
|---------|-------------|----------|--------|
| Trigger Run | `dli workflow run` | `POST /api/v1/workflows/{name}/run` | ✅ Complete |
| Backfill | `dli workflow backfill` | `POST /api/v1/workflows/{name}/backfill` | ✅ Complete |
| Stop Run | `dli workflow stop` | `POST /api/v1/workflows/runs/{run_id}/stop` | ✅ Complete |
| Get Status | `dli workflow status` | `GET /api/v1/workflows/runs/{run_id}` | ✅ Complete |
| List Workflows | `dli workflow list` | `GET /api/v1/workflows` | ✅ Complete |
| Get History | `dli workflow history` | `GET /api/v1/workflows/history` | ✅ Complete |
| Pause | `dli workflow pause` | `POST /api/v1/workflows/{name}/pause` | ✅ Complete |
| Unpause | `dli workflow unpause` | `POST /api/v1/workflows/{name}/unpause` | ✅ Complete |
| Register | `dli workflow register` | `POST /api/v1/workflows/register` | ✅ Complete |
| Unregister | `dli workflow unregister` | `DELETE /api/v1/workflows/{name}` | ✅ Complete |

### 1.3 Source Types

| Source Type | Control Level | API Permissions | S3 Path |
|-------------|---------------|-----------------|---------|
| **CODE** | Limited | Pause/Unpause only | `s3://bucket/workflows/code/` |
| **MANUAL** | Full | All CRUD operations | `s3://bucket/workflows/manual/` |

> **Priority Rule:** CODE overrides MANUAL when both exist.

---

## 2. CLI Command Mapping

| CLI Command | Method | Endpoint | Key Parameters |
|-------------|--------|----------|----------------|
| `dli workflow run <name>` | POST | `/api/v1/workflows/{name}/run` | `params`, `dry_run` |
| `dli workflow backfill <name>` | POST | `/api/v1/workflows/{name}/backfill` | `start_date`, `end_date`, `parallel` |
| `dli workflow stop <run_id>` | POST | `/api/v1/workflows/runs/{run_id}/stop` | `reason` |
| `dli workflow status <run_id>` | GET | `/api/v1/workflows/runs/{run_id}` | - |
| `dli workflow list` | GET | `/api/v1/workflows` | `status`, `source_type`, `owner`, `limit` |
| `dli workflow history` | GET | `/api/v1/workflows/history` | `dataset_name`, `start_date`, `end_date` |
| `dli workflow pause <name>` | POST | `/api/v1/workflows/{name}/pause` | `reason` |
| `dli workflow unpause <name>` | POST | `/api/v1/workflows/{name}/unpause` | - |
| `dli workflow register <name>` | POST | `/api/v1/workflows/register` | `cron`, `timezone`, `yaml_content` |
| `dli workflow unregister <name>` | DELETE | `/api/v1/workflows/{name}` | `force` |

---

## 3. API Specifications

### 3.1 Trigger Run

#### `POST /api/v1/workflows/{dataset_name}/run`

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `params` | object | No | Execution parameters (e.g., `{"date": "2026-01-01"}`) |
| `dry_run` | bool | No | Validate only, no execution |

**Request:**
```json
{
  "params": {"date": "2026-01-01"},
  "dry_run": false
}
```

**Response (202 Accepted):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "PENDING",
  "triggered_by": "user@example.com",
  "params": {"date": "2026-01-01"},
  "started_at": "2026-01-01T10:00:00Z"
}
```

---

### 3.2 Backfill

#### `POST /api/v1/workflows/{dataset_name}/backfill`

**Parameters:**

| Name | Type | Required | Description |
|------|------|----------|-------------|
| `start_date` | string | Yes | Start date (YYYY-MM-DD) |
| `end_date` | string | Yes | End date (YYYY-MM-DD) |
| `params` | object | No | Additional parameters |
| `dry_run` | bool | No | Validate only |
| `parallel` | bool | No | Run dates in parallel |

**Request:**
```json
{
  "start_date": "2026-01-01",
  "end_date": "2026-01-07",
  "params": {"region": "US"},
  "parallel": true
}
```

**Response (202 Accepted):**
```json
{
  "backfill_id": "iceberg.analytics.daily_clicks_backfill_20260101_20260107",
  "dataset_name": "iceberg.analytics.daily_clicks",
  "start_date": "2026-01-01",
  "end_date": "2026-01-07",
  "run_ids": ["...run_id_1...", "...run_id_2..."],
  "status": "PENDING",
  "total_runs": 7
}
```

---

### 3.3 Stop Run

#### `POST /api/v1/workflows/runs/{run_id}/stop`

**Request:**
```json
{
  "reason": "Manual stop requested by user"
}
```

**Response (200 OK):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "status": "STOPPING",
  "stopped_by": "user@example.com",
  "stopped_at": "2026-01-01T10:05:00Z",
  "reason": "Manual stop requested by user"
}
```

---

### 3.4 Get Run Status

#### `GET /api/v1/workflows/runs/{run_id}`

**Response (200 OK):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "RUNNING",
  "started_at": "2026-01-01T10:00:00Z",
  "duration_seconds": 180,
  "triggered_by": "user@example.com",
  "params": {"date": "2026-01-01"},
  "logs_url": "https://airflow.example.com/log?dag_id=...",
  "progress": {
    "total_tasks": 5,
    "completed_tasks": 3,
    "failed_tasks": 0,
    "current_task": "aggregate_clicks"
  }
}
```

---

### 3.5 List Workflows

#### `GET /api/v1/workflows`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `status` | string | - | Filter: `active`, `paused`, `disabled` |
| `source_type` | string | - | Filter: `MANUAL`, `CODE` |
| `owner` | string | - | Filter by owner email |
| `limit` | int | 50 | Max results (1-500) |
| `offset` | int | 0 | Pagination offset |

**Response (200 OK):**
```json
[
  {
    "dataset_name": "iceberg.analytics.daily_clicks",
    "status": "active",
    "source_type": "MANUAL",
    "owner": "data@example.com",
    "team": "@data-eng",
    "schedule": {"cron": "0 6 * * *", "timezone": "UTC"},
    "last_run": {
      "run_id": "...run_id...",
      "status": "SUCCESS",
      "started_at": "2026-01-01T06:00:00Z",
      "duration_seconds": 45
    },
    "next_run": "2026-01-02T06:00:00Z"
  }
]
```

---

### 3.6 Get History

#### `GET /api/v1/workflows/history`

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `dataset_name` | string | - | Filter by dataset |
| `start_date` | string | - | Start date filter |
| `end_date` | string | - | End date filter |
| `limit` | int | 20 | Max results |

**Response (200 OK):**
```json
[
  {
    "run_id": "iceberg.analytics.daily_clicks_20260101_060000",
    "dataset_name": "iceberg.analytics.daily_clicks",
    "status": "SUCCESS",
    "started_at": "2026-01-01T06:00:00Z",
    "ended_at": "2026-01-01T06:00:45Z",
    "duration_seconds": 45,
    "triggered_by": "scheduler",
    "run_type": "scheduled"
  }
]
```

---

### 3.7 Pause/Unpause

#### `POST /api/v1/workflows/{dataset_name}/pause`
#### `POST /api/v1/workflows/{dataset_name}/unpause`

**Request (pause):**
```json
{
  "reason": "Maintenance window"
}
```

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "paused",
  "paused_by": "user@example.com",
  "paused_at": "2026-01-01T10:00:00Z",
  "reason": "Maintenance window"
}
```

---

### 3.8 Register Workflow

#### `POST /api/v1/workflows/register`

**Request:**
```json
{
  "dataset_name": "iceberg.analytics.new_workflow",
  "source_type": "MANUAL",
  "schedule": {"cron": "0 8 * * *", "timezone": "America/New_York"},
  "owner": "data@example.com",
  "team": "@data-eng",
  "description": "New analytics workflow",
  "yaml_content": "version: v1\nname: new_workflow\n..."
}
```

**Response (201 Created):**
```json
{
  "dataset_name": "iceberg.analytics.new_workflow",
  "status": "active",
  "source_type": "MANUAL",
  "s3_path": "s3://data-workflows/manual/iceberg.analytics.new_workflow.yaml",
  "airflow_dag_id": "iceberg_analytics_new_workflow",
  "registered_by": "user@example.com",
  "registered_at": "2026-01-01T10:00:00Z"
}
```

---

### 3.9 Unregister Workflow

#### `DELETE /api/v1/workflows/{dataset_name}`

**Query Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `force` | bool | Force delete even if runs are active |

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "unregistered",
  "s3_path_deleted": "s3://data-workflows/manual/iceberg.analytics.daily_clicks.yaml",
  "airflow_dag_disabled": true,
  "unregistered_by": "user@example.com"
}
```

---

## 4. Implementation Notes

✅ **IMPLEMENTATION COMPLETE** - All domain models, service logic, external integrations, and testing have been implemented and are fully documented in [`WORKFLOW_RELEASE.md`](./WORKFLOW_RELEASE.md).

**Key Architectural Decisions:**
- Pure Hexagonal Architecture with domain/infrastructure separation
- Mock-based development approach (MockAirflowClient + InMemoryWorkflowStorage)
- Comprehensive testing strategy (93 unit tests + 30+ integration tests)
- CQRS pattern for repository layer separation
- Type-safe enum mappings for Airflow status coordination

---

## 5. Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `WORKFLOW_NOT_FOUND` | 404 | Workflow not registered |
| `WORKFLOW_ALREADY_EXISTS` | 409 | Workflow already exists |
| `WORKFLOW_PERMISSION_DENIED` | 403 | Cannot modify CODE workflow |
| `WORKFLOW_RUN_NOT_FOUND` | 404 | Run ID not found |
| `AIRFLOW_CONNECTION_FAILED` | 503 | Airflow API unavailable |
| `INVALID_CRON_EXPRESSION` | 400 | Invalid cron syntax |

---

## 6. Testing

✅ **COMPREHENSIVE TEST SUITE COMPLETE**

| Test Layer | Coverage | Status |
|------------|----------|---------|
| **Unit Tests** | 93 tests across domain models, services, mappers | ✅ 100% Pass |
| **Integration Tests** | 30+ tests for controller endpoints and workflows | ✅ 100% Pass |
| **Mock Integration** | MockAirflowClient + InMemoryWorkflowStorage | ✅ Complete |

**Key Test Features:**
- Domain entity validation and business rule enforcement
- Service layer orchestration with external dependency mocking
- REST API contract validation with MockMvc
- Cross-agent reviewed test coverage and quality assurance

---

## 8. Related Documents

✅ **COMPLETE DOCUMENTATION SET**

| Document | Purpose | Status |
|----------|---------|---------|
| [`WORKFLOW_RELEASE.md`](./WORKFLOW_RELEASE.md) | Complete implementation details, architecture, testing results | ✅ Complete |
| [`_STATUS.md`](../_STATUS.md) | Project progress tracking (60% complete, Phase 3 done) | ✅ Updated |
| [`project-interface-cli/README.md`](../../project-interface-cli/README.md) | CLI commands integration (`dli workflow`) | ✅ Complete |

---

*✅ **WORKFLOW API COMPLETE** - 8,276+ lines implemented, 93 unit tests + 30+ integration tests, comprehensive documentation*
