# P2 Medium Priority APIs - Workflow Management

> **Priority:** P2 Medium | **Implementation Time:** 4 weeks | **CLI Impact:** Server-based workflow orchestration
> **Target Audience:** Backend developers implementing Airflow integration
> **Cross-Reference:** [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 3 details

---

## 📋 Table of Contents

1. [Implementation Overview](#1-implementation-overview)
2. [Workflow Execution APIs](#2-workflow-execution-apis)
3. [Workflow Management APIs](#3-workflow-management-apis)
4. [Workflow Registration APIs](#4-workflow-registration-apis)
5. [Implementation Patterns](#5-implementation-patterns)
6. [Airflow Integration](#6-airflow-integration)

---

## 1. Implementation Overview

### 1.1 P2 APIs Summary

| API Group | Endpoints | CLI Commands Enabled | Implementation Timeline |
|-----------|-----------|---------------------|----------------------|
| **Execution** | 3 endpoints | `dli workflow run/backfill/stop` | Week 6-7 |
| **Management** | 4 endpoints | `dli workflow list/status/history/pause/unpause` | Week 8-9 |
| **Registration** | 2 endpoints | `dli workflow register/unregister` | Week 8-9 |

**Total: 9 endpoints enabling server-based workflow orchestration**

### 1.2 Workflow Source Types

| Source Type | Control Level | API Permissions | Storage Location |
|-------------|---------------|----------------|------------------|
| **CODE** | Limited | Pause/Unpause only | `s3://bucket/workflows/code/` |
| **MANUAL** | Full | All CRUD operations | `s3://bucket/workflows/manual/` |
| **Priority Rule** | CODE overrides MANUAL when both exist | Auto-fallback to MANUAL when CODE deleted | |

---

## 2. Workflow Execution APIs

### 2.1 Trigger Workflow Run

#### `POST /api/v1/workflows/{dataset_name}/run`

**Purpose**: Trigger an ad-hoc workflow run for `dli workflow run`

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/run
Content-Type: application/json
Authorization: Bearer <oauth2-token>

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

### 2.2 Backfill Workflow

#### `POST /api/v1/workflows/{dataset_name}/backfill`

**Purpose**: Run workflow for multiple dates for `dli workflow backfill`

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/backfill
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "start_date": "2026-01-01",
  "end_date": "2026-01-07",
  "params": {
    "region": "US"
  },
  "dry_run": false,
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
  "run_ids": [
    "iceberg.analytics.daily_clicks_20260101_100000",
    "iceberg.analytics.daily_clicks_20260102_100000"
  ],
  "status": "PENDING",
  "triggered_by": "user@example.com",
  "total_runs": 7
}
```

---

### 2.3 Stop Workflow Run

#### `POST /api/v1/workflows/runs/{run_id}/stop`

**Purpose**: Stop a running workflow for `dli workflow stop`

**Request:**
```http
POST /api/v1/workflows/runs/iceberg.analytics.daily_clicks_20260101_100000/stop
Content-Type: application/json
Authorization: Bearer <oauth2-token>

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

## 3. Workflow Management APIs

### 3.1 Get Run Status

#### `GET /api/v1/workflows/runs/{run_id}`

**Purpose**: Get workflow run status for `dli workflow status`

**Request:**
```http
GET /api/v1/workflows/runs/iceberg.analytics.daily_clicks_20260101_100000
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Response (200 OK):**
```json
{
  "run_id": "iceberg.analytics.daily_clicks_20260101_100000",
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "RUNNING",
  "started_at": "2026-01-01T10:00:00Z",
  "updated_at": "2026-01-01T10:03:00Z",
  "duration_seconds": 180,
  "triggered_by": "user@example.com",
  "params": {"date": "2026-01-01"},
  "logs_url": "https://airflow.example.com/log?dag_id=daily_clicks&run_id=...",
  "progress": {
    "total_tasks": 5,
    "completed_tasks": 3,
    "failed_tasks": 0,
    "current_task": "aggregate_clicks"
  }
}
```

**Status Values:**
- `PENDING`: Queued for execution
- `RUNNING`: Currently executing
- `SUCCESS`: Completed successfully
- `FAILED`: Execution failed
- `STOPPING`: Stop requested
- `STOPPED`: Manually stopped

---

### 3.2 List Workflows

#### `GET /api/v1/workflows`

**Purpose**: List registered workflows for `dli workflow list`

**Request:**
```http
GET /api/v1/workflows?status=active&source_type=MANUAL&owner=data@example.com&limit=50&offset=0
Accept: application/json
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `status` | string | No | - | Filter by status (`active`, `paused`, `disabled`) |
| `source_type` | string | No | - | Filter by source (`MANUAL`, `CODE`) |
| `owner` | string | No | - | Filter by owner email |
| `limit` | int | No | 50 | Max results (1-500) |
| `offset` | int | No | 0 | Pagination offset |

**Response (200 OK):**
```json
[
  {
    "dataset_name": "iceberg.analytics.daily_clicks",
    "status": "active",
    "source_type": "MANUAL",
    "owner": "data@example.com",
    "team": "@data-eng",
    "schedule": {
      "cron": "0 6 * * *",
      "timezone": "UTC"
    },
    "last_run": {
      "run_id": "iceberg.analytics.daily_clicks_20260101_060000",
      "status": "SUCCESS",
      "started_at": "2026-01-01T06:00:00Z",
      "duration_seconds": 45
    },
    "next_run": "2026-01-02T06:00:00Z",
    "created_at": "2025-12-01T10:00:00Z"
  }
]
```

---

### 3.3 Get Workflow History

#### `GET /api/v1/workflows/history`

**Purpose**: Get workflow execution history for `dli workflow history`

**Request:**
```http
GET /api/v1/workflows/history?dataset_name=iceberg.analytics.daily_clicks&start_date=2026-01-01&end_date=2026-01-07&limit=20
Accept: application/json
Authorization: Bearer <oauth2-token>
```

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

### 3.4 Pause/Unpause Workflow

#### `POST /api/v1/workflows/{dataset_name}/pause`

**Purpose**: Pause a workflow for `dli workflow pause`

**Request:**
```http
POST /api/v1/workflows/iceberg.analytics.daily_clicks/pause
Content-Type: application/json
Authorization: Bearer <oauth2-token>

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

#### `POST /api/v1/workflows/{dataset_name}/unpause`

**Purpose**: Unpause a workflow for `dli workflow unpause`

Similar pattern to pause endpoint.

---

## 4. Workflow Registration APIs

### 4.1 Register Workflow

#### `POST /api/v1/workflows/register`

**Purpose**: Register a manual workflow for `dli workflow register`

**Request:**
```http
POST /api/v1/workflows/register
Content-Type: application/json
Authorization: Bearer <oauth2-token>

{
  "dataset_name": "iceberg.analytics.new_workflow",
  "source_type": "MANUAL",
  "schedule": {
    "cron": "0 8 * * *",
    "timezone": "America/New_York"
  },
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

### 4.2 Unregister Workflow

#### `DELETE /api/v1/workflows/{dataset_name}`

**Purpose**: Unregister a workflow for `dli workflow unregister`

**Request:**
```http
DELETE /api/v1/workflows/iceberg.analytics.daily_clicks
Authorization: Bearer <oauth2-token>
```

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `force` | bool | No | Force delete even if runs are active |

**Response (200 OK):**
```json
{
  "dataset_name": "iceberg.analytics.daily_clicks",
  "status": "unregistered",
  "s3_path_deleted": "s3://data-workflows/manual/iceberg.analytics.daily_clicks.yaml",
  "airflow_dag_disabled": true,
  "unregistered_by": "user@example.com",
  "unregistered_at": "2026-01-01T10:00:00Z"
}
```

---

## 5. Implementation Patterns

### 5.1 Workflow Domain Layer

```kotlin
// Workflow Entity
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

// Workflow Run Entity
@Entity
@Table(name = "workflow_runs")
class WorkflowRunEntity(
    @Id val runId: String,
    @Column val datasetName: String,
    @Enumerated(STRING) val status: WorkflowRunStatus,
    @Column val triggeredBy: String,
    @Column val runType: String, // "scheduled", "manual", "backfill"
    @Column val startedAt: LocalDateTime?,
    @Column val endedAt: LocalDateTime?,
    @Column(columnDefinition = "TEXT") val params: String, // JSON
    @Column val logsUrl: String?,
)

enum class WorkflowSourceType { MANUAL, CODE }
enum class WorkflowStatus { ACTIVE, PAUSED, DISABLED }
enum class WorkflowRunStatus { PENDING, RUNNING, SUCCESS, FAILED, STOPPING, STOPPED }
```

### 5.2 Workflow Service Layer

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
        // 1. Validate CRON expression
        validateCronExpression(request.schedule.cron)

        // 2. Store YAML in S3
        val s3Path = s3WorkflowStorage.saveWorkflowYaml(
            request.datasetName,
            request.sourceType,
            request.yamlContent
        )

        // 3. Create/Enable DAG in Airflow
        val airflowDagId = airflowClient.createDAG(
            datasetName = request.datasetName,
            schedule = request.schedule,
            s3YamlPath = s3Path
        )

        // 4. Save to database
        val entity = WorkflowEntity(
            datasetName = request.datasetName,
            sourceType = request.sourceType,
            status = WorkflowStatus.ACTIVE,
            owner = request.owner,
            s3Path = s3Path,
            airflowDagId = airflowDagId,
            schedule = request.schedule,
        )

        val saved = workflowRepositoryJpa.save(entity)
        return WorkflowMapper.toDto(saved)
    }

    @Transactional
    fun triggerRun(datasetName: String, params: Map<String, Any>, dryRun: Boolean): WorkflowRunDto {
        val workflow = workflowRepositoryJpa.findByDatasetName(datasetName)
            ?: throw WorkflowNotFoundException(datasetName)

        val runId = generateRunId(datasetName)

        // Trigger in Airflow
        val airflowRunId = airflowClient.triggerDAGRun(
            dagId = workflow.airflowDagId,
            runId = runId,
            conf = params + mapOf("dry_run" to dryRun)
        )

        // Save run record
        val runEntity = WorkflowRunEntity(
            runId = runId,
            datasetName = datasetName,
            status = WorkflowRunStatus.PENDING,
            triggeredBy = getCurrentUser(),
            runType = "manual",
            params = JsonUtil.toJson(params)
        )

        workflowRunRepositoryJpa.save(runEntity)
        return WorkflowRunMapper.toDto(runEntity)
    }
}
```

---

## 6. Airflow Integration

### 6.1 Airflow Client

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
        val request = mapOf(
            "dag_run_id" to runId,
            "conf" to conf
        )

        val response = restTemplate.postForEntity(
            "$airflowBaseUrl/api/v1/dags/$dagId/dagRuns",
            request,
            AirflowDAGRunResponse::class.java
        )

        if (!response.statusCode.is2xxSuccessful) {
            throw AirflowException("Failed to trigger DAG run: ${response.statusCode}")
        }

        return response.body?.dagRunId ?: runId
    }

    fun getDAGRun(dagId: String, runId: String): AirflowDAGRunStatus {
        val response = restTemplate.getForEntity(
            "$airflowBaseUrl/api/v1/dags/$dagId/dagRuns/$runId",
            AirflowDAGRunResponse::class.java
        )

        return response.body?.let { AirflowDAGRunStatus.from(it) }
            ?: throw AirflowException("DAG run not found: $runId")
    }

    fun stopDAGRun(dagId: String, runId: String): Boolean {
        // Airflow doesn't have direct stop API, use state update
        val request = mapOf("state" to "failed")

        val response = restTemplate.patchForObject(
            "$airflowBaseUrl/api/v1/dags/$dagId/dagRuns/$runId",
            request,
            AirflowDAGRunResponse::class.java
        )

        return response != null
    }
}

data class AirflowDAGRunResponse(
    val dagRunId: String,
    val state: String,
    val executionDate: String,
    val startDate: String?,
    val endDate: String?,
    val logUrl: String?
)

data class AirflowDAGRunStatus(
    val runId: String,
    val status: WorkflowRunStatus,
    val startedAt: LocalDateTime?,
    val endedAt: LocalDateTime?,
    val logsUrl: String?
) {
    companion object {
        fun from(response: AirflowDAGRunResponse): AirflowDAGRunStatus {
            return AirflowDAGRunStatus(
                runId = response.dagRunId,
                status = mapAirflowState(response.state),
                startedAt = response.startDate?.let { LocalDateTime.parse(it) },
                endedAt = response.endDate?.let { LocalDateTime.parse(it) },
                logsUrl = response.logUrl
            )
        }

        private fun mapAirflowState(airflowState: String): WorkflowRunStatus {
            return when (airflowState.lowercase()) {
                "queued" -> WorkflowRunStatus.PENDING
                "running" -> WorkflowRunStatus.RUNNING
                "success" -> WorkflowRunStatus.SUCCESS
                "failed" -> WorkflowRunStatus.FAILED
                "up_for_retry" -> WorkflowRunStatus.RUNNING
                "up_for_reschedule" -> WorkflowRunStatus.PENDING
                "upstream_failed" -> WorkflowRunStatus.FAILED
                "skipped" -> WorkflowRunStatus.SUCCESS
                else -> WorkflowRunStatus.FAILED
            }
        }
    }
}
```

### 6.2 S3 Workflow Storage

```kotlin
@Service
class S3WorkflowStorage(
    private val s3Client: S3Client,
    @Value("\${workflows.s3.bucket}") private val bucketName: String,
) {
    fun saveWorkflowYaml(
        datasetName: String,
        sourceType: WorkflowSourceType,
        yamlContent: String,
    ): String {
        val prefix = when (sourceType) {
            WorkflowSourceType.MANUAL -> "manual"
            WorkflowSourceType.CODE -> "code"
        }

        val key = "workflows/$prefix/$datasetName.yaml"

        s3Client.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType("application/x-yaml")
                .build(),
            RequestBody.fromString(yamlContent)
        )

        return "s3://$bucketName/$key"
    }

    fun deleteWorkflowYaml(s3Path: String) {
        val key = s3Path.removePrefix("s3://$bucketName/")
        s3Client.deleteObject(
            DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        )
    }

    fun getWorkflowYaml(s3Path: String): String {
        val key = s3Path.removePrefix("s3://$bucketName/")
        val response = s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build()
        )

        return response.use { it.readAllBytes().toString(Charsets.UTF_8) }
    }
}
```

---

## 🔗 Related Documentation

- **Implementation Timeline**: [`../IMPLEMENTATION_PLAN.md`](../IMPLEMENTATION_PLAN.md) Phase 3
- **Architecture Overview**: [`../BASECAMP_OVERVIEW.md`](../BASECAMP_OVERVIEW.md)
- **Error Handling**: [`../ERROR_CODES.md`](../ERROR_CODES.md)
- **Previous APIs**: [`P1_HIGH_APIS.md`](./P1_HIGH_APIS.md)

### Next Priority
- **P3 Low APIs**: [`P3_LOW_APIS.md`](./P3_LOW_APIS.md) - Quality, Query, Run & Transpile APIs

---

*This document provides implementation-ready specifications for P2 Medium Priority APIs, enabling server-based workflow orchestration within 4 weeks.*