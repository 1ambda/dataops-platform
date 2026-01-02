# Workflow API Feature Specification

> **Version:** 0.1.0 | **Status:** Draft | **Priority:** P2 Medium
> **CLI Commands:** `dli workflow run/backfill/stop/status/list/history/pause/unpause/register/unregister`
> **Implementation Week:** Week 6-9 | **Estimated Effort:** 4 weeks
>
> **📦 Data Source:** Self-managed JPA (상태 저장) + External API (Airflow 연동)
> **Entities:** `WorkflowEntity`, `WorkflowRunEntity` | **External:** Airflow REST API

---

## 1. Overview

### 1.1 Purpose

Server-based workflow orchestration via Airflow integration. Enables CLI (`dli workflow`) to trigger, monitor, and manage scheduled dataset executions.

### 1.2 Scope

| Feature | CLI Command | Endpoint | Status |
|---------|-------------|----------|--------|
| Trigger Run | `dli workflow run` | `POST /api/v1/workflows/{name}/run` | To Implement |
| Backfill | `dli workflow backfill` | `POST /api/v1/workflows/{name}/backfill` | To Implement |
| Stop Run | `dli workflow stop` | `POST /api/v1/workflows/runs/{run_id}/stop` | To Implement |
| Get Status | `dli workflow status` | `GET /api/v1/workflows/runs/{run_id}` | To Implement |
| List Workflows | `dli workflow list` | `GET /api/v1/workflows` | To Implement |
| Get History | `dli workflow history` | `GET /api/v1/workflows/history` | To Implement |
| Pause | `dli workflow pause` | `POST /api/v1/workflows/{name}/pause` | To Implement |
| Unpause | `dli workflow unpause` | `POST /api/v1/workflows/{name}/unpause` | To Implement |
| Register | `dli workflow register` | `POST /api/v1/workflows/register` | To Implement |
| Unregister | `dli workflow unregister` | `DELETE /api/v1/workflows/{name}` | To Implement |

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

## 4. Domain Model

### 4.1 Entities

```kotlin
@Entity
@Table(name = "workflows")
class WorkflowEntity(
    @Id val datasetName: String,
    @Enumerated(STRING) val sourceType: WorkflowSourceType,
    @Enumerated(STRING) val status: WorkflowStatus,
    @Column val owner: String,
    @Column val team: String?,
    @Column val s3Path: String,
    @Column val airflowDagId: String,
    @Embedded val schedule: ScheduleInfo,
    @CreationTimestamp val createdAt: LocalDateTime,
    @UpdateTimestamp val updatedAt: LocalDateTime,
)

@Entity
@Table(name = "workflow_runs")
class WorkflowRunEntity(
    @Id val runId: String,
    @Column val datasetName: String,
    @Enumerated(STRING) val status: WorkflowRunStatus,
    @Column val triggeredBy: String,
    @Column val runType: String,  // "scheduled", "manual", "backfill"
    @Column val startedAt: LocalDateTime?,
    @Column val endedAt: LocalDateTime?,
    @Column(columnDefinition = "TEXT") val params: String,  // JSON
    @Column val logsUrl: String?,
)

@Embeddable
class ScheduleInfo(
    @Column val cron: String,
    @Column val timezone: String,
)
```

### 4.2 Enums

| Enum | Values |
|------|--------|
| `WorkflowSourceType` | `MANUAL`, `CODE` |
| `WorkflowStatus` | `ACTIVE`, `PAUSED`, `DISABLED` |
| `WorkflowRunStatus` | `PENDING`, `RUNNING`, `SUCCESS`, `FAILED`, `STOPPING`, `STOPPED` |

---

## 5. Service Layer

```kotlin
@Service
@Transactional(readOnly = true)
class WorkflowService(
    private val workflowRepositoryJpa: WorkflowRepositoryJpa,
    private val workflowRunRepositoryJpa: WorkflowRunRepositoryJpa,
    private val airflowClient: AirflowClient,
    private val s3WorkflowStorage: S3WorkflowStorage,
) {
    @Transactional
    fun registerWorkflow(request: RegisterWorkflowRequest): WorkflowDto {
        validateCronExpression(request.schedule.cron)
        val s3Path = s3WorkflowStorage.saveWorkflowYaml(request.datasetName, request.sourceType, request.yamlContent)
        val airflowDagId = airflowClient.createDAG(request.datasetName, request.schedule, s3Path)
        val entity = WorkflowEntity(...)
        return WorkflowMapper.toDto(workflowRepositoryJpa.save(entity))
    }

    @Transactional
    fun triggerRun(datasetName: String, params: Map<String, Any>, dryRun: Boolean): WorkflowRunDto {
        val workflow = workflowRepositoryJpa.findByDatasetName(datasetName)
            ?: throw WorkflowNotFoundException(datasetName)
        val runId = generateRunId(datasetName)
        airflowClient.triggerDAGRun(workflow.airflowDagId, runId, params + mapOf("dry_run" to dryRun))
        val runEntity = WorkflowRunEntity(runId, datasetName, PENDING, getCurrentUser(), "manual", params.toJson())
        return WorkflowRunMapper.toDto(workflowRunRepositoryJpa.save(runEntity))
    }

    fun getRunStatus(runId: String): WorkflowRunDto { ... }
    fun listWorkflows(filters: WorkflowFilters): List<WorkflowDto> { ... }
    fun getHistory(filters: HistoryFilters): List<WorkflowRunDto> { ... }
    @Transactional fun pauseWorkflow(datasetName: String, reason: String): WorkflowDto { ... }
    @Transactional fun unpauseWorkflow(datasetName: String): WorkflowDto { ... }
    @Transactional fun stopRun(runId: String, reason: String): WorkflowRunDto { ... }
    @Transactional fun unregisterWorkflow(datasetName: String, force: Boolean): WorkflowDto { ... }
}
```

---

## 6. Airflow Integration

### 6.1 AirflowClient

```kotlin
@Component
class AirflowClient(
    @Value("\${airflow.base-url}") private val airflowBaseUrl: String,
    @Value("\${airflow.username}") private val username: String,
    @Value("\${airflow.password}") private val password: String,
) {
    private val restTemplate = RestTemplate().apply {
        interceptors.add(BasicAuthenticationInterceptor(username, password))
    }

    fun triggerDAGRun(dagId: String, runId: String, conf: Map<String, Any>): String {
        val request = mapOf("dag_run_id" to runId, "conf" to conf)
        val response = restTemplate.postForEntity(
            "$airflowBaseUrl/api/v1/dags/$dagId/dagRuns", request, AirflowDAGRunResponse::class.java
        )
        return response.body?.dagRunId ?: runId
    }

    fun getDAGRun(dagId: String, runId: String): AirflowDAGRunStatus { ... }
    fun stopDAGRun(dagId: String, runId: String): Boolean { ... }
    fun pauseDAG(dagId: String, isPaused: Boolean): Boolean { ... }
}
```

### 6.2 Status Mapping

| Airflow State | WorkflowRunStatus |
|---------------|-------------------|
| `queued` | `PENDING` |
| `running` | `RUNNING` |
| `success` | `SUCCESS` |
| `failed` | `FAILED` |
| `up_for_retry` | `RUNNING` |
| `upstream_failed` | `FAILED` |

---

## 7. S3 Storage

```kotlin
@Service
class S3WorkflowStorage(
    private val s3Client: S3Client,
    @Value("\${workflows.s3.bucket}") private val bucketName: String,
) {
    fun saveWorkflowYaml(datasetName: String, sourceType: WorkflowSourceType, yamlContent: String): String {
        val prefix = when (sourceType) {
            MANUAL -> "manual"
            CODE -> "code"
        }
        val key = "workflows/$prefix/$datasetName.yaml"
        s3Client.putObject(PutObjectRequest.builder().bucket(bucketName).key(key).build(), RequestBody.fromString(yamlContent))
        return "s3://$bucketName/$key"
    }

    fun deleteWorkflowYaml(s3Path: String) { ... }
    fun getWorkflowYaml(s3Path: String): String { ... }
}
```

---

## 8. Error Codes

| Code | HTTP | Description |
|------|------|-------------|
| `WORKFLOW_NOT_FOUND` | 404 | Workflow not registered |
| `WORKFLOW_ALREADY_EXISTS` | 409 | Workflow already exists |
| `WORKFLOW_PERMISSION_DENIED` | 403 | Cannot modify CODE workflow |
| `WORKFLOW_RUN_NOT_FOUND` | 404 | Run ID not found |
| `AIRFLOW_CONNECTION_FAILED` | 503 | Airflow API unavailable |
| `INVALID_CRON_EXPRESSION` | 400 | Invalid cron syntax |

---

## 9. Testing

### 9.1 Unit Tests

| Test Class | Coverage |
|------------|----------|
| `WorkflowServiceTest` | Service logic, validation |
| `AirflowClientTest` | Airflow API mocking |
| `S3WorkflowStorageTest` | S3 operations mocking |

### 9.2 Integration Tests

| Test Class | Coverage |
|------------|----------|
| `WorkflowApiIntegrationTest` | Full API flow with testcontainers |
| `AirflowIntegrationTest` | Real Airflow connection (optional) |

---

## 10. Configuration

```yaml
# application.yaml
airflow:
  base-url: ${AIRFLOW_BASE_URL:http://localhost:8080}
  username: ${AIRFLOW_USERNAME:admin}
  password: ${AIRFLOW_PASSWORD:admin}

workflows:
  s3:
    bucket: ${WORKFLOWS_S3_BUCKET:data-workflows}
```

---

## 11. Related Documents

- **Implementation Plan:** [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) Phase 3
- **Architecture:** [`BASECAMP_OVERVIEW.md`](./BASECAMP_OVERVIEW.md)
- **Error Codes:** [`ERROR_CODES.md`](./ERROR_CODES.md)
- **CLI Implementation:** `project-interface-cli/src/dli/api/workflow.py`

---

*Total: ~450 lines | Implementation-ready specification for P2 Workflow APIs*

---

## Appendix A: Review Feedback

> **Reviewed by:** expert-spring-kotlin Agent | **Date:** 2026-01-01 | **Rating:** 4.0/5

### Strengths
- Clean hexagonal architecture with proper service/repository separation
- Correct use of constructor injection (no `@Autowired` field injection)
- Good CQRS separation with `WorkflowRepositoryJpa` vs `WorkflowRepositoryDsl`
- Proper `@Transactional(readOnly = true)` at class level

### Issues to Address

| Priority | Issue | Recommendation |
|----------|-------|----------------|
| **High** | Using `RestTemplate` is outdated | Use `RestClient` (Spring Boot 4 preferred) or `WebClient` |
| **High** | No error handling for Airflow API failures | Add try-catch with circuit breaker pattern |
| **Medium** | `@Id val datasetName: String` - business key as primary key | Consider using generated ID with `datasetName` as unique constraint |
| **Medium** | `params: String` for JSON - should use proper converter | Use `@Type(JsonBinaryType::class)` with `jsonb` column |
| **Low** | Exception `WorkflowNotFoundException` used but not defined | Add exception class definition |

### Required Changes Before Implementation
1. Replace `RestTemplate` with `RestClient` (Spring Boot 4 standard)
2. Add circuit breaker pattern for Airflow integration (Resilience4j)
3. Use JSON type converters for JSON columns
4. Define all exception classes properly
