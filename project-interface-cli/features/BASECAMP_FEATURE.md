# FEATURE: Basecamp Server API Specification

> **Version:** 1.0.0
> **Status:** Specification Complete
> **Created:** 2026-01-01
> **Last Updated:** 2026-01-01

---

## 1. Overview

### 1.1 Purpose

This document specifies the REST API endpoints that **project-basecamp-server** must implement to support the CLI client (`dli`). The API specification is derived from the `BasecampClient` mock implementations in `project-interface-cli/src/dli/core/client.py`.

### 1.2 Design Principles

| Principle | Description |
|-----------|-------------|
| **RESTful Design** | Standard HTTP methods (GET, POST, DELETE) with resource-based URLs |
| **JSON API** | All requests/responses use `application/json` |
| **Consistent Pagination** | Use `limit` and `offset` query parameters |
| **Error Codes** | Standard HTTP status codes with structured error responses |
| **Stateless** | Server does not maintain client session state |

### 1.3 Base URL

```
Production: https://basecamp.example.com/api/v1
Development: http://localhost:8081/api/v1 (Docker) or http://localhost:8080/api/v1 (Local)
```

### 1.4 Authentication

| Method | Header | Description |
|--------|--------|-------------|
| **API Key** | `X-API-Key: <key>` | Service-to-service authentication |
| **OAuth2** | `Authorization: Bearer <token>` | User authentication via Keycloak |

---

## 2. API Priority Matrix

### 2.1 Implementation Priority

| Priority | Category | Endpoints | CLI Commands | Effort |
|----------|----------|-----------|--------------|--------|
| **P0 (Critical)** | Health, Metrics, Datasets | 7 endpoints | `metric`, `dataset` | 2 weeks |
| **P1 (High)** | Catalog, Lineage | 6 endpoints | `catalog`, `lineage` | 2 weeks |
| **P2 (Medium)** | Workflow | 9 endpoints | `workflow` | 3 weeks |
| **P3 (Low)** | Quality, Query, Transpile, Run | 8 endpoints | `quality`, `query`, `run` | 2 weeks |

### 2.2 Dependency Graph

```
P0: Health -> Metrics/Datasets (CRUD foundation)
       |
       v
P1: Catalog -> Lineage (Data discovery requires catalog)
       |
       v
P2: Workflow (Requires Metrics/Datasets + Airflow integration)
       |
       v
P3: Quality/Query/Run (Advanced features, requires all above)
```

---

## 3. API Endpoints Specification

### 3.1 Health & System (P0)

#### GET /api/v1/health

Check server health status.

**Request:**
```http
GET /api/v1/health
```

**Response (200 OK):**
```json
{
  "status": "healthy",
  "version": "1.0.0",
  "timestamp": "2026-01-01T10:00:00Z",
  "components": {
    "database": "healthy",
    "redis": "healthy",
    "airflow": "healthy"
  }
}
```

**Response (503 Service Unavailable):**
```json
{
  "status": "unhealthy",
  "version": "1.0.0",
  "components": {
    "database": "healthy",
    "redis": "unhealthy",
    "airflow": "healthy"
  },
  "error": "Redis connection failed"
}
```

---

### 3.2 Metrics API (P0)

#### GET /api/v1/metrics

List metrics with optional filtering.

**Request:**
```http
GET /api/v1/metrics?tag=revenue&owner=data@example.com&search=daily&limit=50&offset=0
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `tag` | string | No | Filter by tag (exact match) |
| `owner` | string | No | Filter by owner (partial match) |
| `search` | string | No | Search in name and description |
| `limit` | int | No | Max results (default: 50) |
| `offset` | int | No | Pagination offset (default: 0) |

**Response (200 OK):**
```json
[
  {
    "name": "iceberg.reporting.user_summary",
    "type": "Metric",
    "owner": "analyst@example.com",
    "team": "@analytics",
    "description": "User summary metrics",
    "tags": ["reporting", "daily"],
    "created_at": "2025-12-01T10:00:00Z",
    "updated_at": "2025-12-15T14:30:00Z"
  }
]
```

---

#### GET /api/v1/metrics/{name}

Get metric details by fully qualified name.

**Request:**
```http
GET /api/v1/metrics/iceberg.reporting.user_summary
```

**Response (200 OK):**
```json
{
  "name": "iceberg.reporting.user_summary",
  "type": "Metric",
  "owner": "analyst@example.com",
  "team": "@analytics",
  "description": "User summary metrics",
  "tags": ["reporting", "daily"],
  "sql_expression": "SELECT user_id, COUNT(*) FROM events GROUP BY 1",
  "source_table": "iceberg.raw.events",
  "dependencies": ["iceberg.raw.events", "iceberg.dim.users"],
  "created_at": "2025-12-01T10:00:00Z",
  "updated_at": "2025-12-15T14:30:00Z"
}
```

**Response (404 Not Found):**
```json
{
  "error": "Metric 'iceberg.reporting.user_summary' not found",
  "code": "METRIC_NOT_FOUND"
}
```

---

#### POST /api/v1/metrics

Register a new metric.

**Request:**
```http
POST /api/v1/metrics
Content-Type: application/json

{
  "name": "iceberg.reporting.new_metric",
  "type": "Metric",
  "owner": "analyst@example.com",
  "team": "@analytics",
  "description": "New metric description",
  "tags": ["reporting"],
  "sql_expression": "SELECT COUNT(*) FROM events",
  "source_table": "iceberg.raw.events"
}
```

**Response (201 Created):**
```json
{
  "message": "Metric 'iceberg.reporting.new_metric' registered successfully",
  "name": "iceberg.reporting.new_metric"
}
```

**Response (409 Conflict):**
```json
{
  "error": "Metric 'iceberg.reporting.new_metric' already exists",
  "code": "METRIC_ALREADY_EXISTS"
}
```

---

### 3.3 Datasets API (P0)

#### GET /api/v1/datasets

List datasets with optional filtering. (Same pattern as Metrics API)

**Request:**
```http
GET /api/v1/datasets?tag=feed&owner=engineer@example.com&search=daily
```

**Response (200 OK):**
```json
[
  {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "engineer@example.com",
    "team": "@data-eng",
    "description": "Daily click aggregations",
    "tags": ["feed", "daily"]
  }
]
```

---

#### GET /api/v1/datasets/{name}

Get dataset details by fully qualified name.

**Request:**
```http
GET /api/v1/datasets/iceberg.analytics.daily_clicks
```

**Response (200 OK):**
```json
{
  "name": "iceberg.analytics.daily_clicks",
  "type": "Dataset",
  "owner": "engineer@example.com",
  "team": "@data-eng",
  "description": "Daily click aggregations",
  "tags": ["feed", "daily"],
  "sql": "SELECT date, COUNT(*) FROM events GROUP BY 1",
  "dependencies": ["iceberg.raw.events"],
  "schedule": {
    "cron": "0 6 * * *",
    "timezone": "UTC"
  }
}
```

---

#### POST /api/v1/datasets

Register a new dataset. (Same pattern as Metrics API)

---

### 3.4 Catalog API (P1)

#### GET /api/v1/catalog/tables

List tables from the data catalog.

**Request:**
```http
GET /api/v1/catalog/tables?project=my-project&dataset=analytics&owner=data-team@example.com&team=@data-eng&tags=tier::critical,pii&limit=50&offset=0
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `project` | string | No | Filter by project/catalog name |
| `dataset` | string | No | Filter by dataset/schema name |
| `owner` | string | No | Filter by owner email |
| `team` | string | No | Filter by team |
| `tags` | string | No | Comma-separated tags (AND condition) |
| `limit` | int | No | Max results (default: 50) |
| `offset` | int | No | Pagination offset (default: 0) |

**Response (200 OK):**
```json
[
  {
    "name": "my-project.analytics.users",
    "engine": "bigquery",
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "tags": ["tier::critical", "domain::analytics", "pii"],
    "row_count": 1500000,
    "last_updated": "2026-01-01T08:00:00Z"
  }
]
```

---

#### GET /api/v1/catalog/search

Search tables by keyword across names, columns, descriptions, and tags.

**Request:**
```http
GET /api/v1/catalog/search?keyword=user&project=my-project&limit=20
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `keyword` | string | **Yes** | Search keyword |
| `project` | string | No | Limit search to project |
| `limit` | int | No | Max results (default: 20) |

**Response (200 OK):**
```json
[
  {
    "name": "my-project.analytics.users",
    "engine": "bigquery",
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "tags": ["tier::critical", "pii"],
    "row_count": 1500000,
    "last_updated": "2026-01-01T08:00:00Z",
    "match_context": "Column: user_id, Description: User dimension table"
  }
]
```

---

#### GET /api/v1/catalog/tables/{table_ref}

Get detailed table information.

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users?include_sample=true
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `include_sample` | bool | No | Include sample data (default: false) |

**Response (200 OK):**
```json
{
  "name": "my-project.analytics.users",
  "engine": "bigquery",
  "owner": "data-team@example.com",
  "team": "@data-eng",
  "description": "User dimension table with profile information",
  "tags": ["tier::critical", "domain::analytics", "pii"],
  "row_count": 1500000,
  "last_updated": "2026-01-01T08:00:00Z",
  "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.users",
  "columns": [
    {
      "name": "user_id",
      "data_type": "STRING",
      "description": "Unique user identifier",
      "is_pii": false,
      "fill_rate": 1.0,
      "distinct_count": 1500000
    },
    {
      "name": "email",
      "data_type": "STRING",
      "description": "User email address",
      "is_pii": true,
      "fill_rate": 0.98,
      "distinct_count": 1470000
    }
  ],
  "ownership": {
    "owner": "data-team@example.com",
    "team": "@data-eng",
    "stewards": ["alice@example.com", "bob@example.com"],
    "consumers": ["@analytics", "@marketing", "@product"]
  },
  "freshness": {
    "last_updated": "2026-01-01T08:00:00Z",
    "avg_update_lag_hours": 1.5,
    "update_frequency": "hourly",
    "is_stale": false,
    "stale_threshold_hours": 6
  },
  "quality": {
    "score": 92,
    "total_tests": 15,
    "passed_tests": 14,
    "failed_tests": 1,
    "warnings": 0,
    "recent_tests": [
      {
        "test_name": "user_id_not_null",
        "test_type": "not_null",
        "status": "pass",
        "failed_rows": 0
      }
    ]
  },
  "sample_data": [
    {"user_id": "user_001", "email": "***@example.com", "name": "***", "created_at": "2024-01-15T10:30:00Z", "country": "US"}
  ]
}
```

**Policy: PII Masking**
- Columns with `is_pii: true` MUST have their sample data values masked with `***`
- PII detection based on column metadata or configurable patterns

---

#### GET /api/v1/catalog/tables/{table_ref}/queries

Get sample queries for a table.

**Request:**
```http
GET /api/v1/catalog/tables/my-project.analytics.users/queries?limit=5
```

**Response (200 OK):**
```json
[
  {
    "title": "Active users by country",
    "sql": "SELECT country, COUNT(*) FROM `my-project.analytics.users` WHERE last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1",
    "author": "analyst@example.com",
    "run_count": 156,
    "last_run": "2026-01-01T09:00:00Z"
  }
]
```

---

### 3.5 Lineage API (P1)

#### GET /api/v1/lineage/{resource_name}

Get lineage (dependencies and dependents) for a resource.

**Request:**
```http
GET /api/v1/lineage/iceberg.analytics.daily_clicks?direction=both&depth=-1
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `direction` | string | No | `upstream`, `downstream`, or `both` (default: `both`) |
| `depth` | int | No | Max traversal depth, -1 for unlimited (default: -1) |

**Response (200 OK):**
```json
{
  "root": {
    "name": "iceberg.analytics.daily_clicks",
    "type": "Dataset",
    "owner": "engineer@example.com",
    "team": "@data-eng",
    "description": "Daily click aggregations",
    "tags": ["feed", "daily"]
  },
  "nodes": [
    {
      "name": "iceberg.raw.clicks",
      "type": "Dataset",
      "owner": "ingestion@example.com",
      "team": "@data-platform",
      "description": "Raw source data for clicks",
      "tags": ["raw", "source"],
      "depth": -1
    },
    {
      "name": "iceberg.reporting.clicks_report",
      "type": "Dataset",
      "owner": "analyst@example.com",
      "team": "@analytics",
      "description": "Reporting view for clicks",
      "tags": ["reporting", "bi"],
      "depth": 1
    }
  ],
  "edges": [
    {
      "source": "iceberg.raw.clicks",
      "target": "iceberg.analytics.daily_clicks",
      "edge_type": "direct"
    },
    {
      "source": "iceberg.analytics.daily_clicks",
      "target": "iceberg.reporting.clicks_report",
      "edge_type": "direct"
    }
  ],
  "total_upstream": 2,
  "total_downstream": 1
}
```

---

### 3.6 Workflow API (P2)

#### POST /api/v1/workflows/{dataset_name}/run

Trigger an adhoc workflow run.

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/run
Content-Type: application/json

{
  "params": {
    "date": "2026-01-01"
  },
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

#### POST /api/v1/workflows/{dataset_name}/backfill

Trigger backfill runs for a date range.

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/backfill
Content-Type: application/json

{
  "start_date": "2025-12-01",
  "end_date": "2025-12-31",
  "params": {}
}
```

**Response (202 Accepted):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "start_date": "2025-12-01",
  "end_date": "2025-12-31",
  "total_runs": 31,
  "runs": [
    {
      "run_id": "iceberg.analytics.daily_clicks_20251201_000000",
      "date": "2025-12-01",
      "status": "PENDING"
    }
  ]
}
```

---

#### POST /api/v1/workflows/runs/{run_id}/stop

Stop a running workflow.

**Request:**
```http
POST /api/v1/workflows/runs/iceberg.analytics.daily_clicks_20260101_100000/stop
```

**Response (200 OK):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "status": "KILLED",
  "message": "Workflow run stopped successfully"
}
```

**Response (400 Bad Request):**
```json
{
  "error": "Cannot stop run with status 'COMPLETED'",
  "code": "WORKFLOW_INVALID_STATE"
}
```

---

#### GET /api/v1/workflows/runs/{run_id}

Get status of a workflow run.

**Request:**
```http
GET /api/v1/workflows/runs/iceberg.analytics.daily_clicks_20260101_100000
```

**Response (200 OK):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "dataset_name": "iceberg.analytics.daily_clicks",
  "source": "manual",
  "status": "RUNNING",
  "started_at": "2026-01-01T10:00:00Z",
  "ended_at": null,
  "duration_seconds": null,
  "triggered_by": "user@example.com",
  "params": {"date": "2026-01-01"}
}
```

---

#### GET /api/v1/workflows

List registered workflows.

**Request:**
```http
GET /api/v1/workflows?dataset=daily&source=code&status=active&limit=50
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `dataset` | string | No | Filter by dataset name pattern |
| `source` | string | No | Filter by source type (`code` or `manual`) |
| `status` | string | No | Filter by status (`active`, `paused`, `overridden`) |
| `running_only` | bool | No | Show only workflows with running jobs |
| `enabled_only` | bool | No | Show only enabled workflows |
| `limit` | int | No | Max results (default: 50) |

**Response (200 OK):**
```json
[
  {
    "dataset_name": "iceberg.analytics.daily_clicks",
    "source": "code",
    "schedule": "0 6 * * *",
    "enabled": true,
    "paused": false,
    "owner": "engineer@example.com",
    "team": "@data-eng",
    "last_run_at": "2026-01-01T06:00:00Z",
    "last_run_status": "COMPLETED",
    "next_run_at": "2026-01-02T06:00:00Z"
  }
]
```

---

#### GET /api/v1/workflows/history

Get workflow execution history.

**Request:**
```http
GET /api/v1/workflows/history?dataset=daily_clicks&source=code&status=COMPLETED&limit=20
```

**Response (200 OK):**
```json
[
  {
    "run_id": "iceberg.analytics.daily_clicks_20260101_060000",
    "dataset_name": "iceberg.analytics.daily_clicks",
    "source": "code",
    "status": "COMPLETED",
    "started_at": "2026-01-01T06:00:00Z",
    "ended_at": "2026-01-01T06:15:00Z",
    "duration_seconds": 900,
    "triggered_by": "scheduler"
  }
]
```

---

#### POST /api/v1/workflows/{dataset_name}/pause

Pause a workflow (disable scheduled runs).

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/pause
```

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "paused": true,
  "message": "Workflow 'iceberg.analytics.daily_clicks' paused successfully"
}
```

---

#### POST /api/v1/workflows/{dataset_name}/unpause

Unpause a workflow (enable scheduled runs).

---

#### POST /api/v1/workflows/register

Register a local Dataset as MANUAL workflow.

**Request:**
```http
POST /api/v1/workflows/register
Content-Type: application/json

{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "cron": "0 9 * * *",
  "timezone": "UTC",
  "enabled": true,
  "retry_max_attempts": 1,
  "retry_delay_seconds": 300,
  "force": false
}
```

**Response (201 Created):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "source_type": "manual",
  "status": "active",
  "cron": "0 9 * * *",
  "timezone": "UTC",
  "next_run": "2026-01-02T09:00:00Z",
  "retry_max_attempts": 1,
  "retry_delay_seconds": 300
}
```

**Response (403 Forbidden):**
```json
{
  "error": "Cannot register: CODE workflow exists for 'iceberg.analytics.daily_clicks'",
  "code": "WORKFLOW_PERMISSION_DENIED"
}
```

**Response (409 Conflict):**
```json
{
  "error": "Workflow for 'iceberg.analytics.daily_clicks' already exists. Use --force to overwrite.",
  "code": "WORKFLOW_ALREADY_EXISTS"
}
```

---

#### DELETE /api/v1/workflows/{dataset_name}

Unregister a MANUAL workflow.

**Request:**
```http
DELETE /api/v1/workflows/iceberg.analytics.daily_clicks?force=false
```

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "message": "Workflow 'iceberg.analytics.daily_clicks' unregistered successfully"
}
```

**Response (403 Forbidden):**
```json
{
  "error": "Cannot unregister CODE workflow 'iceberg.analytics.daily_clicks'. Use Git to remove.",
  "code": "WORKFLOW_PERMISSION_DENIED"
}
```

**Policy: Workflow Source Types**

| Source Type | Description | Management |
|-------------|-------------|------------|
| `code` | Registered via CI/CD from Git | Modify/Delete via Git only |
| `manual` | Registered via CLI/API | Full CLI/API control |

**Policy: S3 Storage Structure**
```
s3://bucket/
+-- code/                    # CI/CD managed
|   +-- daily_clicks.yaml
+-- manual/                  # Basecamp managed
    +-- ad_hoc_report.yaml
```

---

### 3.7 Quality API (P3)

#### POST /api/v1/quality/test/{resource_name}

Execute a quality test on the server.

**Request:**
```http
POST /api/v1/quality/test/my-project.analytics.users
Content-Type: application/json

{
  "test_name": "email_not_null",
  "test_type": "not_null",
  "columns": ["email"],
  "params": {},
  "severity": "error"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `test_name` | string | Yes | Test identifier |
| `test_type` | string | Yes | Test type (`not_null`, `unique`, `accepted_values`, `expression`) |
| `columns` | array | No | Columns to test |
| `params` | object | No | Test parameters |
| `severity` | string | No | `error` or `warn` (default: `error`) |

**Response (200 OK):**
```json
{
  "status": "pass",
  "failed_rows": 0,
  "failed_samples": [],
  "execution_time_ms": 150,
  "rendered_sql": "SELECT COUNT(*) FROM `my-project.analytics.users` WHERE email IS NULL"
}
```

**Response (200 OK - Test Failed):**
```json
{
  "status": "fail",
  "failed_rows": 42,
  "failed_samples": [
    {"user_id": "user_123", "email": null, "row_number": 1},
    {"user_id": "user_456", "email": null, "row_number": 2}
  ],
  "execution_time_ms": 230,
  "rendered_sql": "SELECT * FROM `my-project.analytics.users` WHERE email IS NULL LIMIT 10"
}
```

---

### 3.8 Query Metadata API (P3)

#### GET /api/v1/catalog/queries

List query execution metadata.

**Request:**
```http
GET /api/v1/catalog/queries?scope=my&account=current_user&sql=SELECT&state=success&tags=pipeline::daily&engine=bigquery&since=2026-01-01T00:00:00Z&until=2026-01-01T23:59:59Z&limit=10&offset=0
```

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `scope` | string | No | `my`, `system`, `user`, or `all` (default: `my`) |
| `account` | string | No | Filter by account name (partial match) |
| `sql` | string | No | Filter by SQL content (partial match) |
| `state` | string | No | Filter by state (`pending`, `running`, `success`, `failed`, `cancelled`) |
| `tags` | string | No | Filter by tags (comma-separated, AND logic) |
| `engine` | string | No | Filter by engine (`bigquery`, `trino`) |
| `since` | string | No | Start time (ISO8601) |
| `until` | string | No | End time (ISO8601) |
| `limit` | int | No | Max results (default: 10) |
| `offset` | int | No | Pagination offset (default: 0) |

**Policy: Query Scope Permissions**

| Scope | Description | Permission |
|-------|-------------|------------|
| `my` | Current user's queries | All authenticated users |
| `system` | System account queries (airflow, dbt) | All authenticated users |
| `user` | All personal user queries | Requires `query:read:all` permission |
| `all` | All queries | Requires `query:read:all` permission |

**Response (200 OK):**
```json
{
  "queries": [
    {
      "query_id": "bq_job_abc123",
      "engine": "bigquery",
      "state": "success",
      "account": "current_user@company.com",
      "account_type": "personal",
      "started_at": "2026-01-01T08:00:00Z",
      "finished_at": "2026-01-01T08:00:12Z",
      "duration_seconds": 12.5,
      "tables_used_count": 3,
      "tags": ["team::analytics", "pipeline::daily"],
      "query_preview": "SELECT user_id, COUNT(*) as event_count FROM analytics.raw_events..."
    }
  ],
  "total_count": 150,
  "has_more": true
}
```

---

#### GET /api/v1/catalog/queries/{query_id}

Get detailed query metadata.

**Request:**
```http
GET /api/v1/catalog/queries/bq_job_abc123?include_full_query=true
```

**Response (200 OK):**
```json
{
  "query_id": "bq_job_abc123",
  "engine": "bigquery",
  "state": "success",
  "account": "current_user@company.com",
  "account_type": "personal",
  "started_at": "2026-01-01T08:00:00Z",
  "finished_at": "2026-01-01T08:00:12Z",
  "duration_seconds": 12.5,
  "queue_time_seconds": 0.2,
  "bytes_processed": 1200000000,
  "bytes_billed": 1200000000,
  "slot_time_seconds": 45.0,
  "rows_affected": 50000,
  "tables_used": [
    {"name": "analytics.raw_events", "operation": "read", "alias": null},
    {"name": "analytics.users", "operation": "read", "alias": "u"}
  ],
  "tags": ["team::analytics"],
  "query_preview": "SELECT user_id, COUNT(*) as event_count FROM analytics.raw_events...",
  "query_text": "SELECT user_id, COUNT(*) as event_count\nFROM analytics.raw_events e\nJOIN analytics.users u ON e.user_id = u.id\nWHERE event_date = '2026-01-01'\nGROUP BY user_id"
}
```

---

#### POST /api/v1/catalog/queries/{query_id}/cancel

Cancel a running query.

**Request:**
```http
POST /api/v1/catalog/queries/bq_job_abc123/cancel
Content-Type: application/json

{
  "dry_run": false
}
```

**Response (200 OK):**
```json
{
  "cancelled_count": 1,
  "queries": [
    {
      "query_id": "bq_job_abc123",
      "state": "cancelled"
    }
  ]
}
```

**Response (200 OK - Already Completed):**
```json
{
  "cancelled_count": 0,
  "queries": [],
  "warning": "Query 'bq_job_abc123' already completed (state: success)"
}
```

---

### 3.9 Transpile API (P3)

#### GET /api/v1/transpile/rules

Fetch transpile rules from server.

**Request:**
```http
GET /api/v1/transpile/rules
```

**Response (200 OK):**
```json
{
  "rules": [
    {
      "id": "rule-001",
      "type": "table_substitution",
      "source": "raw.events",
      "target": "warehouse.events_v2",
      "enabled": true,
      "description": "Events table migration",
      "created_at": "2025-12-01T10:00:00Z"
    }
  ],
  "version": "2026-01-01-001"
}
```

**Policy: Transpile Rule Management**
- Rules are version-controlled and deployed via CI/CD
- CLI fetches rules for local SQL transpilation
- Rule types: `table_substitution`, `function_mapping`, `dialect_conversion`

---

#### GET /api/v1/transpile/metrics/{metric_name}

Fetch metric SQL expression for transpilation.

**Request:**
```http
GET /api/v1/transpile/metrics/revenue
```

**Response (200 OK):**
```json
{
  "name": "revenue",
  "sql_expression": "SUM(amount * quantity)",
  "source_table": "analytics.orders",
  "description": "Total revenue",
  "dependencies": ["analytics.orders"]
}
```

---

### 3.10 Run (Ad-hoc Execution) API (P3)

#### GET /api/v1/run/policy

Get execution policy from server.

**Request:**
```http
GET /api/v1/run/policy
```

**Response (200 OK):**
```json
{
  "allow_local": true,
  "server_available": true,
  "default_mode": "server",
  "max_timeout_seconds": 3600,
  "max_result_rows": 100000,
  "allowed_dialects": ["bigquery", "trino"]
}
```

---

#### POST /api/v1/run/execute

Execute ad-hoc SQL query.

**Request:**
```http
POST /api/v1/run/execute
Content-Type: application/json

{
  "sql": "SELECT user_id, COUNT(*) FROM events GROUP BY 1 LIMIT 100",
  "dialect": "bigquery",
  "limit": 100,
  "timeout": 300
}
```

**Response (200 OK):**
```json
{
  "rows": [
    {"user_id": "user_001", "count": 150},
    {"user_id": "user_002", "count": 120}
  ],
  "row_count": 2,
  "duration_seconds": 0.5,
  "bytes_processed": 1024,
  "bytes_billed": 1024
}
```

**Response (408 Request Timeout):**
```json
{
  "error": "Query execution timed out after 300 seconds",
  "code": "QUERY_TIMEOUT"
}
```

---

## 4. Error Response Format

### 4.1 Standard Error Response

All error responses follow this format:

```json
{
  "error": "Human-readable error message",
  "code": "ERROR_CODE",
  "details": {
    "field": "Additional context"
  },
  "timestamp": "2026-01-01T10:00:00Z",
  "trace_id": "abc123"
}
```

### 4.2 Error Codes

| HTTP Status | Code | Description |
|-------------|------|-------------|
| 400 | `BAD_REQUEST` | Invalid request parameters |
| 400 | `WORKFLOW_INVALID_STATE` | Invalid workflow state for operation |
| 400 | `INVALID_CRON` | Invalid cron expression |
| 401 | `UNAUTHORIZED` | Missing or invalid authentication |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 403 | `WORKFLOW_PERMISSION_DENIED` | Cannot modify CODE workflow via API |
| 404 | `NOT_FOUND` | Resource not found |
| 404 | `METRIC_NOT_FOUND` | Metric not found |
| 404 | `DATASET_NOT_FOUND` | Dataset not found |
| 404 | `TABLE_NOT_FOUND` | Catalog table not found |
| 404 | `WORKFLOW_NOT_FOUND` | Workflow not found |
| 404 | `QUERY_NOT_FOUND` | Query not found |
| 408 | `QUERY_TIMEOUT` | Query execution timed out |
| 409 | `CONFLICT` | Resource already exists |
| 409 | `METRIC_ALREADY_EXISTS` | Metric already registered |
| 409 | `DATASET_ALREADY_EXISTS` | Dataset already registered |
| 409 | `WORKFLOW_ALREADY_EXISTS` | Workflow already registered |
| 500 | `INTERNAL_ERROR` | Internal server error |
| 503 | `SERVICE_UNAVAILABLE` | Dependent service unavailable |

---

## 5. Implementation Effort Estimates

### 5.1 Phase 1: P0 APIs (Critical) - 2 weeks

| API | Endpoints | Effort | Dependencies |
|-----|-----------|--------|--------------|
| Health | 1 | 1 day | None |
| Metrics | 3 | 3 days | Database, S3 |
| Datasets | 3 | 3 days | Database, S3 |
| **Total** | **7** | **7 days** | |

### 5.2 Phase 2: P1 APIs (High) - 2 weeks

| API | Endpoints | Effort | Dependencies |
|-----|-----------|--------|--------------|
| Catalog List/Search | 3 | 4 days | BigQuery/Trino metadata |
| Catalog Detail | 1 | 2 days | PII masking service |
| Lineage | 1 | 4 days | Lineage graph storage |
| **Total** | **5** | **10 days** | |

### 5.3 Phase 3: P2 APIs (Medium) - 3 weeks

| API | Endpoints | Effort | Dependencies |
|-----|-----------|--------|--------------|
| Workflow CRUD | 3 | 3 days | Airflow API |
| Workflow Execution | 4 | 5 days | Airflow DAG trigger |
| Workflow Registration | 2 | 4 days | S3 upload, Airflow |
| **Total** | **9** | **12 days** | |

### 5.4 Phase 4: P3 APIs (Low) - 2 weeks

| API | Endpoints | Effort | Dependencies |
|-----|-----------|--------|--------------|
| Quality | 1 | 3 days | Query engine |
| Query Metadata | 3 | 3 days | Query history storage |
| Transpile | 2 | 2 days | Rule storage |
| Run | 2 | 2 days | Query engine |
| **Total** | **8** | **10 days** | |

### 5.5 Total Estimate

| Phase | Priority | Endpoints | Duration |
|-------|----------|-----------|----------|
| Phase 1 | P0 (Critical) | 7 | 2 weeks |
| Phase 2 | P1 (High) | 5 | 2 weeks |
| Phase 3 | P2 (Medium) | 9 | 3 weeks |
| Phase 4 | P3 (Low) | 8 | 2 weeks |
| **Total** | | **29** | **9 weeks** |

---

## 6. Server Policies

### 6.1 PII Masking Policy

**Location:** Applied in Catalog API responses

| Rule | Description |
|------|-------------|
| **Column Detection** | Columns with `is_pii: true` metadata are masked |
| **Pattern Detection** | Email, phone, SSN patterns auto-detected |
| **Sample Data** | All PII values replaced with `***` |
| **Query Results** | Full masking for `include_sample=true` responses |

### 6.2 Workflow Source Type Policy

**Location:** Applied in Workflow API

| Rule | Description |
|------|-------------|
| **CODE Priority** | CODE workflows override MANUAL |
| **Permission Model** | MANUAL: full API control; CODE: pause/unpause only |
| **S3 Structure** | `code/` for CI/CD, `manual/` for API |
| **Auto Fallback** | MANUAL activates when CODE deleted |

### 6.3 Query Scope Policy

**Location:** Applied in Query Metadata API

| Scope | Access Level |
|-------|--------------|
| `my` | All authenticated users |
| `system` | All authenticated users |
| `user` | Requires `query:read:all` role |
| `all` | Requires `query:read:all` role |

### 6.4 Transpile Rule Policy

**Location:** Applied in Transpile API

| Rule | Description |
|------|-------------|
| **Version Control** | Rules versioned with format `YYYY-MM-DD-NNN` |
| **Deployment** | Rules deployed via CI/CD only |
| **Client Sync** | CLI fetches and caches rules locally |
| **Priority** | Rule order determines application precedence |

---

## 7. Key Decisions Summary

| Item | Decision | Trade-off | Rationale |
|------|----------|-----------|-----------|
| **API Versioning** | URL path (`/api/v1`) | Path vs header | Simpler client implementation |
| **Pagination** | `limit`/`offset` | Offset vs cursor | Simpler, sufficient for current scale |
| **Error Format** | Structured JSON with `code` | Verbose vs minimal | Better debugging, client-side handling |
| **Authentication** | OAuth2 + API Key | Complexity vs flexibility | OAuth for users, API key for services |
| **PII Masking** | Server-side only | Server vs client | Security guarantee |
| **Workflow Control** | Server-mediated Airflow | Direct vs proxy | Centralized audit, permission control |

---

## 8. Reference Implementation

### 8.1 CLI Client Reference

**File:** `project-interface-cli/src/dli/core/client.py`

The `BasecampClient` class provides the reference for all API contracts:
- Mock implementations define expected request/response shapes
- `ServerResponse` dataclass defines the response wrapper
- `WorkflowSource` and `RunStatus` enums define allowed values

### 8.2 Server Implementation Pattern

**Location:** `project-basecamp-server` following hexagonal architecture

```kotlin
// Controller (API Layer)
@RestController
@RequestMapping("/api/v1/metrics")
class MetricController(
    private val metricService: MetricService,
) {
    @GetMapping
    fun listMetrics(
        @RequestParam tag: String?,
        @RequestParam owner: String?,
        @RequestParam search: String?,
    ): ResponseEntity<List<MetricDto>> {
        return ResponseEntity.ok(metricService.listMetrics(tag, owner, search))
    }
}

// Service (Domain Layer)
@Service
class MetricService(
    private val metricRepositoryJpa: MetricRepositoryJpa,
) {
    fun listMetrics(tag: String?, owner: String?, search: String?): List<MetricDto> {
        // Implementation
    }
}
```

---

## Appendix A: Endpoint Quick Reference

```
# Health
GET    /api/v1/health

# Metrics (P0)
GET    /api/v1/metrics
GET    /api/v1/metrics/{name}
POST   /api/v1/metrics

# Datasets (P0)
GET    /api/v1/datasets
GET    /api/v1/datasets/{name}
POST   /api/v1/datasets

# Catalog (P1)
GET    /api/v1/catalog/tables
GET    /api/v1/catalog/search
GET    /api/v1/catalog/tables/{table_ref}
GET    /api/v1/catalog/tables/{table_ref}/queries

# Lineage (P1)
GET    /api/v1/lineage/{resource_name}

# Workflow (P2)
POST   /api/v1/workflows/{dataset_name}/run
POST   /api/v1/workflows/{dataset_name}/backfill
POST   /api/v1/workflows/runs/{run_id}/stop
GET    /api/v1/workflows/runs/{run_id}
GET    /api/v1/workflows
GET    /api/v1/workflows/history
POST   /api/v1/workflows/{dataset_name}/pause
POST   /api/v1/workflows/{dataset_name}/unpause
POST   /api/v1/workflows/register
DELETE /api/v1/workflows/{dataset_name}

# Quality (P3)
POST   /api/v1/quality/test/{resource_name}

# Query (P3)
GET    /api/v1/catalog/queries
GET    /api/v1/catalog/queries/{query_id}
POST   /api/v1/catalog/queries/{query_id}/cancel

# Transpile (P3)
GET    /api/v1/transpile/rules
GET    /api/v1/transpile/metrics/{metric_name}

# Run (P3)
GET    /api/v1/run/policy
POST   /api/v1/run/execute
```

---

## Appendix B: CLI to API Mapping

| CLI Command | HTTP Method | API Endpoint |
|-------------|-------------|--------------|
| `dli metric list` | GET | /api/v1/metrics |
| `dli metric get NAME` | GET | /api/v1/metrics/{name} |
| `dli metric register` | POST | /api/v1/metrics |
| `dli dataset list` | GET | /api/v1/datasets |
| `dli dataset get NAME` | GET | /api/v1/datasets/{name} |
| `dli dataset register` | POST | /api/v1/datasets |
| `dli catalog` | GET | /api/v1/catalog/tables |
| `dli catalog TABLE` | GET | /api/v1/catalog/tables/{table_ref} |
| `dli lineage show NAME` | GET | /api/v1/lineage/{resource_name} |
| `dli workflow list` | GET | /api/v1/workflows |
| `dli workflow run NAME` | POST | /api/v1/workflows/{dataset_name}/run |
| `dli workflow register NAME` | POST | /api/v1/workflows/register |
| `dli workflow status RUN_ID` | GET | /api/v1/workflows/runs/{run_id} |
| `dli quality run NAME` | POST | /api/v1/quality/test/{resource_name} |
| `dli query list` | GET | /api/v1/catalog/queries |
| `dli query show ID` | GET | /api/v1/catalog/queries/{query_id} |
| `dli query cancel ID` | POST | /api/v1/catalog/queries/{query_id}/cancel |
| `dli run FILE.sql` | POST | /api/v1/run/execute |

---

## Appendix C: Implementation Agent Review Checklist

### Domain Implementer Review (feature-basecamp-server)

- [ ] All 29 endpoints documented with request/response schemas
- [ ] Error codes align with CLI error handling (DLI-xxx)
- [ ] Pagination parameters consistent across all list endpoints
- [ ] Authentication headers documented for all endpoints
- [ ] PII masking policy clearly defined for Catalog API

### Technical Senior Review (expert-spring-kotlin)

- [ ] Hexagonal architecture patterns applicable
- [ ] Repository interface signatures match API contracts
- [ ] DTO naming follows basecamp-server conventions
- [ ] Transaction boundaries defined for write operations
- [ ] Async handling for long-running operations (Run, Quality)

---

## Appendix D: Agent Review Findings (2026-01-01)

### D.1 CLI-API Compatibility Review (feature-interface-cli)

#### Missing API Endpoints (P1 Priority)

| CLI Command | Required Endpoint | Status |
|-------------|-------------------|--------|
| `dli metric run NAME` | `POST /api/v1/metrics/{name}/run` | **TO ADD** |
| `dli dataset run NAME` | `POST /api/v1/datasets/{name}/run` | **TO ADD** |
| `dli quality list` | `GET /api/v1/quality` | **TO ADD** |
| `dli quality get NAME` | `GET /api/v1/quality/{name}` | **TO ADD** |
| `dli debug` | `GET /api/v1/health/extended` | **TO ADD** |
| `dli metric/dataset transpile --server` | `POST /api/v1/transpile/execute` | Optional |

#### Schema Mismatches (MUST FIX)

| Issue | API Field | Python Model Field | Resolution |
|-------|-----------|-------------------|------------|
| Metric SQL | `sql_expression` | `sql` | Use `sql` (matches CLI) |
| Workflow next run | `next_run_at` | `next_run` | Use `next_run` |
| Search context | Missing | `match_context` | Add to TableInfo response |

#### Error Codes to Add

| HTTP Status | Server Code | DLI Code | Description |
|-------------|-------------|----------|-------------|
| 400 | `SCHEMA_TOO_LARGE` | DLI-706 | Table schema exceeds limit |
| 400 | `LINEAGE_DEPTH_EXCEEDED` | DLI-901 | Lineage depth limit reached |
| 400 | `LINEAGE_CYCLE_DETECTED` | DLI-902 | Circular dependency found |

### D.2 Python API Compatibility Review (expert-python)

#### Positive Findings

- ✅ Consistent pagination (`limit`/`offset`)
- ✅ ISO8601 datetime format compatible with Pydantic
- ✅ Enum values match Python models
- ✅ snake_case naming convention followed
- ✅ Exception hierarchy covers all API errors

#### Python Model Additions Required

```python
# 1. TableInfo - add for search results
match_context: str | None = Field(default=None)

# 2. New result models needed
class MetricRegisterResult(BaseModel):
    message: str
    name: str
    status: ResultStatus

class DatasetRegisterResult(BaseModel):
    message: str
    name: str
    status: ResultStatus

class RunPolicyResult(BaseModel):
    allow_local: bool
    server_available: bool
    default_mode: str
    max_timeout_seconds: int
    max_result_rows: int
    allowed_dialects: list[str]
```

### D.3 Updated Appendix B (Complete CLI to API Mapping)

| CLI Command | HTTP Method | API Endpoint | Status |
|-------------|-------------|--------------|--------|
| `dli metric list` | GET | /api/v1/metrics | ✅ |
| `dli metric get NAME` | GET | /api/v1/metrics/{name} | ✅ |
| `dli metric register` | POST | /api/v1/metrics | ✅ |
| `dli metric run NAME` | POST | /api/v1/metrics/{name}/run | **TO ADD** |
| `dli dataset list` | GET | /api/v1/datasets | ✅ |
| `dli dataset get NAME` | GET | /api/v1/datasets/{name} | ✅ |
| `dli dataset register` | POST | /api/v1/datasets | ✅ |
| `dli dataset run NAME` | POST | /api/v1/datasets/{name}/run | **TO ADD** |
| `dli catalog` | GET | /api/v1/catalog/tables | ✅ |
| `dli catalog TABLE` | GET | /api/v1/catalog/tables/{table_ref} | ✅ |
| `dli lineage show NAME` | GET | /api/v1/lineage/{resource_name} | ✅ |
| `dli workflow list` | GET | /api/v1/workflows | ✅ |
| `dli workflow run NAME` | POST | /api/v1/workflows/{dataset_name}/run | ✅ |
| `dli workflow register NAME` | POST | /api/v1/workflows/register | ✅ |
| `dli workflow status RUN_ID` | GET | /api/v1/workflows/runs/{run_id} | ✅ |
| `dli quality list` | GET | /api/v1/quality | **TO ADD** |
| `dli quality get NAME` | GET | /api/v1/quality/{name} | **TO ADD** |
| `dli quality run NAME` | POST | /api/v1/quality/test/{resource_name} | ✅ |
| `dli query list` | GET | /api/v1/catalog/queries | ✅ |
| `dli query show ID` | GET | /api/v1/catalog/queries/{query_id} | ✅ |
| `dli query cancel ID` | POST | /api/v1/catalog/queries/{query_id}/cancel | ✅ |
| `dli run FILE.sql` | POST | /api/v1/run/execute | ✅ |
| `dli debug` | GET | /api/v1/health/extended | **TO ADD** |

### D.4 Updated Priority Matrix

| Priority | Original Endpoints | + Review Additions | Total |
|----------|-------------------|-------------------|-------|
| P0 (Critical) | 7 | +2 (metric/dataset run) | 9 |
| P1 (High) | 5 | +1 (health/extended) | 6 |
| P2 (Medium) | 9 | - | 9 |
| P3 (Low) | 8 | +2 (quality list/get) | 10 |
| **Total** | **29** | **+5** | **34** |
