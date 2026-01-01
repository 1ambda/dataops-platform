# FEATURE: Workflow Library API and Enhanced Commands

> **Version:** 3.0.0
> **Status:** ✅ Phase 1 Complete (v0.4.0)
> **Created:** 2025-12-30
> **Last Updated:** 2026-01-01

---

## 1. Overview

### 1.1 Purpose

`dli workflow` provides programmatic and CLI access to manage Dataset schedules on Airflow through Basecamp Server. This version adds:

- ✅ **WorkflowAPI**: Library API for programmatic workflow management (v0.4.0)
- ⏳ **`workflow register`**: Register local Dataset as MANUAL workflow (Phase 2)
- ⏳ **Enhanced `workflow list`**: Filter by source type (MANUAL/CODE) (Phase 2)
- ⏳ **Enhanced `workflow history`**: Display Dataset metadata in execution history (Phase 2)

### 1.2 Core Principles

| Principle | Description |
|-----------|-------------|
| **Execution Engine** | Airflow as single execution engine |
| **Schedule Definition** | Defined in Dataset Spec YAML only |
| **Basecamp Role** | Stateless control plane (Airflow API calls) |
| **CLI Role** | Execution, status query, enable/disable toggle |
| **Library API** | Consistent programmatic interface following DatasetAPI pattern |

### 1.3 Key Features

| Feature | Description |
|---------|-------------|
| **Source Type Management** | Manual/Code registration support |
| **Workflow Registration** | Register local Dataset Spec as MANUAL workflow |
| **Adhoc Execution** | Immediate execution with parameters |
| **Backfill** | Sequential execution for date range |
| **Status Management** | Enable/disable schedule (pause/unpause) |
| **Monitoring** | Execution status query, history with Dataset info |
| **Execution Control** | Stop running workflow (Force Kill) |
| **WorkflowAPI** | Library API for programmatic access |

### 1.4 Existing System Integration Points

| Integration Area | Existing Pattern | New Feature Application |
|------------------|------------------|-------------------------|
| **Library API** | `DatasetAPI`, `MetricAPI` pattern | `WorkflowAPI` follows same facade pattern |
| **CLI** | `commands/workflow.py` Typer commands | Add `register` command |
| **Models** | `core/workflow/models.py` Pydantic | Extend with API result models |
| **Client** | `BasecampClient.workflow_*` methods | Add `workflow_register` method |
| **Exceptions** | `DLIError` hierarchy (DLI-1xx~7xx) | Add DLI-8xx for Workflow errors |

---

## 2. Source Type: Manual vs Code

### 2.1 Concept

Dataset schedules can be registered in two ways:

| Source Type | Description | Registration Path |
|-------------|-------------|-------------------|
| **Manual** | User registers via CLI/API | CLI/API -> Basecamp -> S3 manual/ |
| **Code** | Auto-registered via Git CI/CD | Git -> CI/CD -> S3 code/ |

### 2.2 System Architecture

```
+---------------------------------------------------------------------+
|                         Source Type: Code                            |
+---------------------------------------------------------------------+
|  User -> Git (YAML) -> CI/CD Pipeline -> S3 code/ -> Airflow DAG    |
+---------------------------------------------------------------------+

+---------------------------------------------------------------------+
|                        Source Type: Manual                           |
+---------------------------------------------------------------------+
|  User -> CLI (dli workflow register) -> Basecamp -> S3 manual/      |
|                                                        |             |
|                                                  Airflow DAG         |
+---------------------------------------------------------------------+

+---------------------------------------------------------------------+
|                      Basecamp Server Role                            |
+---------------------------------------------------------------------+
|  - Stateless control plane (schedule info from Airflow)              |
|  - S3 + Airflow as Source of Truth                                   |
|  - Airflow REST API calls: adhoc/backfill/status/history             |
|  - Periodic S3 check for Code/Manual Override handling               |
+---------------------------------------------------------------------+
```

### 2.3 S3 Storage Structure

```
s3://bucket/
+-- code/                    # CI/CD managed (Git -> CI/CD -> S3)
|   +-- daily_clicks.yaml
|   +-- user_metrics.yaml
+-- manual/                  # Basecamp managed (CLI/API -> S3)
    +-- ad_hoc_report.yaml
    +-- daily_clicks.yaml    # Can be overridden by Code
```

### 2.4 Conflict Policy (Override)

When same Dataset name exists in both `code/` and `manual/`:

| Rule | Description |
|------|-------------|
| **Code Priority** | Code overrides Manual automatically |
| **Manual File Kept** | Manual file not deleted (user data protection) |
| **Auto Fallback** | Manual activates when Code deleted |
| **Periodic Check** | Basecamp periodically checks S3 for Override status |

### 2.5 Permission Model

| Source Type | Modify | Delete | pause/unpause |
|-------------|:------:|:------:|:-------------:|
| **Manual** | CLI/API | CLI/API | CLI/API |
| **Code** | Git only | Git only | CLI/API |

### 2.6 Status

| Status | Description |
|--------|-------------|
| `active` | Schedule enabled (normal execution) |
| `paused` | Schedule paused |
| `overridden` | Overridden by Code (Manual only) |

---

## 3. Architecture

### 3.1 Component Relationship

```
+------------------------------------------------------------------+
|                          User                                     |
+------------------------------------------------------------------+
           |                                    |
           v                                    v
+---------------------+              +---------------------+
|    CLI Commands     |              |    Library API      |
|  (commands/workflow)|              |   (api/workflow)    |
+---------------------+              +---------------------+
           |                                    |
           +----------------+-------------------+
                            |
                            v
+------------------------------------------------------------------+
|                     BasecampClient                                |
|                   (core/client.py)                                |
+------------------------------------------------------------------+
                            |
                            v
+------------------------------------------------------------------+
|                   Basecamp Server API                             |
+------------------------------------------------------------------+
                            |
                            v
+------------------------------------------------------------------+
|                      Airflow REST API                             |
+------------------------------------------------------------------+
```

### 3.2 Key Decisions

| Item | Decision | Rationale |
|------|----------|-----------|
| **API Pattern** | Facade over BasecampClient | Consistent with DatasetAPI/MetricAPI |
| **Result Models** | Pydantic models in `models/workflow.py` | Type safety, JSON serialization |
| **Error Codes** | DLI-8xx range for Workflow | Avoid collision with existing 0xx-7xx |
| **Mock Mode** | Full mock support in WorkflowAPI | Test without server dependency |
| **CLI Integration** | `workflow register` command added | Register local Dataset as MANUAL |

---

## 4. Library API Design (WorkflowAPI)

> **Status:** ✅ Implemented in v0.4.0
>
> See [WORKFLOW_RELEASE.md](./WORKFLOW_RELEASE.md) for complete implementation details.

### 4.1 WorkflowAPI Class

✅ **Implemented** - 11 methods available:

| Method | Status | Description |
|--------|--------|-------------|
| `get()` | ✅ | Get workflow info for a dataset |
| `register()` | ✅ | Register local Dataset as MANUAL workflow |
| `unregister()` | ✅ | Unregister MANUAL workflow |
| `run()` | ✅ | Trigger adhoc execution |
| `backfill()` | ✅ | Run backfill for date range |
| `stop()` | ✅ | Stop running workflow |
| `get_status()` | ✅ | Get status of a workflow run |
| `list_workflows()` | ✅ | List registered workflows |
| `history()` | ✅ | Get workflow execution history |
| `pause()` | ✅ | Pause workflow schedule |
| `unpause()` | ✅ | Resume workflow schedule |

Example usage:

```python
from dli import WorkflowAPI, ExecutionContext, ExecutionMode

ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER, server_url="http://basecamp:8080")
api = WorkflowAPI(context=ctx)

# Get workflow info
info = api.get("iceberg.analytics.daily_clicks")

# Register workflow
result = api.register(
    "iceberg.analytics.daily_clicks",
    cron="0 9 * * *",
    timezone="Asia/Seoul",
)

# Run adhoc execution
run_result = api.run("iceberg.analytics.daily_clicks", parameters={"date": "2025-01-01"})

# Check status
status = api.get_status(run_result.run_id)
```

### 4.2 Result Models

✅ **Implemented** - 5 result models in `dli/models/workflow.py`:

| Model | Purpose | Key Fields |
|-------|---------|------------|
| `WorkflowRegisterResult` | Registration result | dataset_name, source_type, workflow_info |
| `WorkflowRunResult` | Execution operations | run_id, run_status, dry_run |
| `WorkflowListResult` | List query result | workflows, total_count |
| `WorkflowStatusResult` | Run status details | is_running, is_terminal, duration_seconds |
| `WorkflowHistoryResult` | Execution history | runs, dataset_info |

All models use `Pydantic BaseModel` with `ConfigDict(frozen=True)` for immutability.

### 4.3 Error Codes (DLI-8xx)

✅ **Implemented** - All workflow errors in DLI-8xx range:

```python
# Add to dli/exceptions.py

class ErrorCode(str, Enum):
    # ... existing codes ...

    # Workflow Errors (DLI-8xx) - All workflow errors consolidated
    WORKFLOW_NOT_FOUND = "DLI-800"           # Moved from DLI-104
    WORKFLOW_REGISTRATION_FAILED = "DLI-801"
    WORKFLOW_EXECUTION_FAILED = "DLI-802"
    WORKFLOW_PERMISSION_DENIED = "DLI-803"
    WORKFLOW_ALREADY_EXISTS = "DLI-804"
    WORKFLOW_INVALID_CRON = "DLI-805"
    WORKFLOW_OVERRIDDEN = "DLI-806"
    WORKFLOW_INVALID_STATE = "DLI-807"
    WORKFLOW_UNREGISTER_FAILED = "DLI-808"


# Update existing WorkflowNotFoundError to use new code
# WorkflowNotFoundError.code = ErrorCode.WORKFLOW_NOT_FOUND  # DLI-800


# NEW exceptions - Use @dataclass pattern (consistent with existing DLI exceptions)
@dataclass
class WorkflowRegistrationError(DLIError):
    """Error during workflow registration."""

    code: ErrorCode = ErrorCode.WORKFLOW_REGISTRATION_FAILED
    dataset_name: str = ""

    def __str__(self) -> str:
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Failed to register workflow for '{self.dataset_name}'"


@dataclass
class WorkflowExecutionError(DLIError):
    """Error during workflow execution."""

    code: ErrorCode = ErrorCode.WORKFLOW_EXECUTION_FAILED
    dataset_name: str = ""
    run_id: str = ""

    def __str__(self) -> str:
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Workflow execution failed for '{self.dataset_name}'"


@dataclass
class WorkflowPermissionError(DLIError):
    """Permission denied for workflow operation."""

    code: ErrorCode = ErrorCode.WORKFLOW_PERMISSION_DENIED
    dataset_name: str = ""

    def __str__(self) -> str:
        if self.message:
            return f"[{self.code.value}] {self.message}"
        return f"[{self.code.value}] Permission denied for workflow '{self.dataset_name}'"
```

---

## 5. CLI Commands

### 5.1 Command Structure

```
dli workflow <subcommand> [options]
```

| Subcommand | Description |
|------------|-------------|
| `register` | **NEW**: Register local Dataset as MANUAL workflow |
| `run` | Trigger adhoc execution |
| `backfill` | Run backfill for date range |
| `stop` | Stop running workflow |
| `status` | Get run status |
| `list` | List workflows (enhanced with source filter) |
| `history` | View execution history (enhanced with Dataset info) |
| `pause` | Disable schedule |
| `unpause` | Enable schedule |

### 5.2 `register` - Register MANUAL Workflow (NEW)

```bash
dli workflow register <dataset_name> [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--cron` | `-c` | **Required** | Cron expression (5-field) |
| `--timezone` | `-tz` | `UTC` | IANA timezone |
| `--enabled/--disabled` | | `--enabled` | Enable schedule on registration |
| `--retry-attempts` | | `1` | Max retry attempts |
| `--retry-delay` | | `300` | Retry delay in seconds |
| `--force` | `-f` | `False` | Overwrite existing MANUAL registration |
| `--path` | | `.` | Project path |

**Usage Examples:**

```bash
# Register with cron schedule
$ dli workflow register iceberg.analytics.daily_clicks --cron "0 9 * * *"
Workflow registered: iceberg.analytics.daily_clicks
  Source: manual
  Status: active
  Cron: 0 9 * * *
  Timezone: UTC
  Next Run: 2025-01-02 09:00:00

# Register with timezone and disabled initially
$ dli workflow register iceberg.analytics.user_metrics \
    --cron "0 10 * * *" \
    --timezone "Asia/Seoul" \
    --disabled
Workflow registered: iceberg.analytics.user_metrics
  Source: manual
  Status: paused
  Cron: 0 10 * * *
  Timezone: Asia/Seoul

# Force overwrite existing MANUAL registration
$ dli workflow register iceberg.analytics.daily_clicks \
    --cron "0 8 * * *" \
    --force
Workflow updated: iceberg.analytics.daily_clicks
  Previous cron: 0 9 * * *
  New cron: 0 8 * * *
```

**Error Cases:**

```bash
# CODE workflow already exists
$ dli workflow register iceberg.analytics.daily_clicks --cron "0 9 * * *"
Error [DLI-804]: Permission denied for workflow 'iceberg.analytics.daily_clicks'
       This dataset is managed by Code (GitOps).
       Only pause/unpause operations are allowed via CLI.

# Dataset not found locally
$ dli workflow register nonexistent.dataset --cron "0 9 * * *"
Error [DLI-101]: Dataset 'nonexistent.dataset' not found
       Check the dataset name or project path.

# Invalid cron expression
$ dli workflow register iceberg.analytics.daily_clicks --cron "invalid"
Error [DLI-806]: Invalid cron expression: 'invalid'
       Expected 5-field format: minute hour day month weekday
```

### 5.3 `list` - List Workflows (Enhanced)

```bash
dli workflow list [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--source` | `-s` | `all` | Filter by source type (`code`/`manual`/`all`) |
| `--status` | | `all` | Filter by status (`active`/`paused`/`overridden`) |
| `--running` | | `False` | Show only running workflows |
| `--enabled-only` | | `False` | Show only enabled workflows |
| `--dataset` | `-d` | | Filter by dataset name pattern |
| `--format` | `-f` | `table` | Output format (`table`/`json`) |

**Output Example:**

```bash
$ dli workflow list --source manual
DATASET                              SOURCE   STATUS      CRON          NEXT RUN
iceberg.analytics.ad_hoc_report      manual   active      0 12 * * 1    2025-01-06 12:00
iceberg.analytics.daily_clicks       manual   overridden  0 8 * * *     - (by code)

Total: 2 workflows (1 active, 1 overridden)

$ dli workflow list --source code --enabled-only
DATASET                              SOURCE   STATUS      CRON          NEXT RUN
iceberg.analytics.daily_clicks       code     active      0 9 * * *     2025-01-02 09:00
iceberg.analytics.user_metrics       code     active      0 10 * * *    2025-01-02 10:00

Total: 2 workflows
```

### 5.4 `history` - Execution History (Enhanced)

```bash
dli workflow history [options]
```

| Option | Short | Default | Description |
|--------|-------|---------|-------------|
| `--dataset` | `-d` | | Filter by dataset name |
| `--source` | `-s` | `all` | Filter by source type |
| `--status` | | | Filter by run status |
| `--limit` | `-n` | `20` | Number of records |
| `--show-dataset-info` | | `False` | **NEW**: Show Dataset metadata |
| `--format` | `-f` | `table` | Output format |

**Output Example (with Dataset info):**

```bash
$ dli workflow history -d iceberg.analytics.daily_clicks --show-dataset-info
Dataset Info:
  Name: iceberg.analytics.daily_clicks
  Owner: engineer@example.com
  Team: @data-engineering
  Description: Daily click aggregation for analytics

Run History:
RUN_ID                                  STATUS     TYPE      STARTED              DURATION
daily_clicks_20250101_090045            completed  scheduled 2025-01-01 09:00:45  125s
daily_clicks_20241231_090032            completed  scheduled 2024-12-31 09:00:32  118s
daily_clicks_20241230_090128            failed     scheduled 2024-12-30 09:01:28  45s
  Error: BigQuery quota exceeded

Total: 3 runs shown (20 available)
```

### 5.5 Other Commands (Existing)

#### `run` - Adhoc Execution

```bash
dli workflow run <dataset_name> [options]
```

| Option | Short | Description |
|--------|-------|-------------|
| `--param` | `-p` | Parameter (`key=value`, multiple allowed) |
| `--dry-run` | | Validate only (no execution) |

#### `backfill` - Backfill Execution

```bash
dli workflow backfill <dataset_name> --start <date> --end <date> [options]
```

| Option | Short | Description |
|--------|-------|-------------|
| `--start` | `-s` | Start date (YYYY-MM-DD) |
| `--end` | `-e` | End date (YYYY-MM-DD) |
| `--param` | `-p` | Additional parameter |
| `--dry-run` | | Validate only |

#### `stop` - Stop Execution

```bash
dli workflow stop <run_id>
```

#### `status` - Run Status

```bash
dli workflow status <run_id>
```

#### `pause` / `unpause` - Schedule Control

```bash
dli workflow pause <dataset_name>
dli workflow unpause <dataset_name>
```

---

## 6. BasecampClient Extension

### 6.1 New Method: `workflow_register`

```python
# Add to dli/core/client.py

def workflow_register(
    self,
    dataset_name: str,
    schedule_config: ScheduleConfig,
    force: bool = False,
) -> ServerResponse:
    """Register a local Dataset as MANUAL workflow.

    Args:
        dataset_name: Fully qualified dataset name.
        schedule_config: Schedule configuration (cron, timezone, retry, etc.).
        force: Overwrite existing MANUAL registration.

    Returns:
        ServerResponse with registered workflow info.
    """
    if self.mock_mode:
        return ServerResponse(
            success=True,
            data={
                "dataset_name": dataset_name,
                "source_type": "manual",
                "status": "active" if schedule_config.enabled else "paused",
                "cron": schedule_config.cron,
                "timezone": schedule_config.timezone,
                "next_run": datetime.now().isoformat() if schedule_config.enabled else None,
            },
        )

    # Real implementation would call Basecamp Server API
    # POST /api/v1/workflows/register
    return ServerResponse(success=False, error="Not implemented", status_code=501)
```

---

## 7. User Flows

### 7.1 DA Registers Local Dataset as MANUAL Workflow

```
1. DA has local Dataset Spec file:
   models/dataset.iceberg.analytics.daily_clicks.yaml

2. DA runs register command:
   $ dli workflow register iceberg.analytics.daily_clicks --cron "0 9 * * *"

3. CLI:
   a. Validates local Dataset Spec exists
   b. Validates cron expression
   c. Calls WorkflowAPI.register() or BasecampClient.workflow_register()

4. Basecamp Server:
   a. Checks if CODE workflow exists (returns 403 if yes)
   b. Uploads Dataset Spec to S3 manual/ path
   c. Registers schedule with Airflow
   d. Returns WorkflowInfo

5. CLI displays success message with next_run time
```

### 7.2 User Lists Workflows by Source Type

```
1. User wants to see only MANUAL workflows:
   $ dli workflow list --source manual

2. CLI calls WorkflowAPI.list_workflows(source_type=SourceType.MANUAL)

3. Server returns filtered list

4. CLI displays table with source type column highlighted
```

### 7.3 User Views History with Dataset Info

```
1. User wants to see execution history with Dataset metadata:
   $ dli workflow history -d iceberg.analytics.daily_clicks --show-dataset-info

2. CLI calls WorkflowAPI.history(dataset_name=..., include_dataset_info=True)

3. Server returns:
   - List of WorkflowRun objects
   - Dataset metadata (owner, team, description)

4. CLI displays Dataset info section followed by run history table
```

---

## 8. Implementation Priority

### Phase 1 (MVP) - ✅ Complete (v0.4.0)

- ✅ **WorkflowAPI class** with mock support (11 methods implemented)
- ✅ **Result models** (5 models: WorkflowRegisterResult, WorkflowRunResult, WorkflowListResult, WorkflowStatusResult, WorkflowHistoryResult)
- ✅ **Error codes** (DLI-800 ~ DLI-803) and exceptions (4 exception classes)
- ✅ **BasecampClient extension** (workflow_register, workflow_unregister methods)
- ✅ **Public exports** in `dli/__init__.py` (WorkflowAPI, exceptions, models)
- ✅ **Unit tests** (59 tests covering all API methods in mock mode)

### Phase 2 (CLI Enhancement) - ⏳ Planned

- [ ] **`workflow register`** CLI command (programmatic API ready, CLI pending)
- [ ] **`workflow unregister`** CLI command (programmatic API ready, CLI pending)
- [ ] **Enhanced `workflow list`** with `--source` filter
- [ ] **Enhanced `workflow history`** with `--show-dataset-info`
- [ ] **Integration tests** with Basecamp Server

### Phase 3 (Server Implementation) - ⏳ Planned

- [ ] **Basecamp Server API** endpoints for workflow registration
- [ ] **S3 integration** for manual/ path uploads (server-side)
- [ ] **Airflow integration** for schedule registration (server-side)
- [ ] **End-to-end tests** with real server and Airflow

---

## 9. Success Criteria (Technical Quality)

### 9.1 Requirements Completion

| Feature | Completion Condition |
|---------|---------------------|
| WorkflowAPI | All methods implemented with mock support |
| CLI register | Command works with mock mode |
| CLI list | `--source` filter functional |
| CLI history | `--show-dataset-info` displays metadata |
| Error handling | DLI-8xx codes return appropriate messages |

### 9.2 Test Quality

| Metric | Target | Measurement |
|--------|--------|-------------|
| Unit test coverage | >= 80% | `pytest --cov` |
| Mock mode tests | Each API method has mock test | Test file count |
| CLI command tests | Each subcommand tested | `typer.testing.CliRunner` |

### 9.3 Code Quality

| Principle | Verification |
|-----------|--------------|
| **Single Responsibility** | WorkflowAPI delegates to BasecampClient |
| **Open-Closed** | New run types addable without modifying WorkflowAPI |
| **Dependency Inversion** | WorkflowAPI accepts optional client via DI |
| **Consistent Pattern** | Follows DatasetAPI/MetricAPI facade pattern |

---

## 10. Test Plan

### 10.1 Unit Tests

```python
# tests/api/test_workflow.py

import pytest
from datetime import datetime

from dli import WorkflowAPI, ExecutionContext, ExecutionMode
from dli.core.workflow.models import SourceType, WorkflowStatus, RunStatus
from dli.models.common import ResultStatus


@pytest.fixture
def mock_api() -> WorkflowAPI:
    """Create WorkflowAPI in mock mode."""
    return WorkflowAPI(
        context=ExecutionContext(execution_mode=ExecutionMode.MOCK)
    )


class TestWorkflowAPIRegister:
    """Tests for WorkflowAPI.register()."""

    def test_register_mock_success(self, mock_api: WorkflowAPI) -> None:
        """Register returns success in mock mode."""
        result = mock_api.register(
            "test.dataset",
            cron="0 9 * * *",
            timezone="Asia/Seoul",
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.source_type == SourceType.MANUAL
        assert result.workflow_info is not None
        assert result.workflow_info.cron == "0 9 * * *"
        assert result.workflow_info.timezone == "Asia/Seoul"

    def test_register_mock_disabled(self, mock_api: WorkflowAPI) -> None:
        """Register with enabled=False creates paused workflow."""
        result = mock_api.register(
            "test.dataset",
            cron="0 9 * * *",
            enabled=False,
        )

        assert result.workflow_info.status == WorkflowStatus.PAUSED
        assert result.workflow_info.next_run is None


class TestWorkflowAPIRun:
    """Tests for WorkflowAPI.run()."""

    def test_run_mock_success(self, mock_api: WorkflowAPI) -> None:
        """Run returns mock run_id."""
        result = mock_api.run(
            "test.dataset",
            parameters={"date": "2025-01-01"},
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.run_id is not None
        assert "mock_" in result.run_id

    def test_run_mock_dry_run(self, mock_api: WorkflowAPI) -> None:
        """Dry run does not return run status."""
        result = mock_api.run("test.dataset", dry_run=True)

        assert result.dry_run is True
        assert result.run_status is None


class TestWorkflowAPIList:
    """Tests for WorkflowAPI.list_workflows()."""

    def test_list_mock_returns_workflows(self, mock_api: WorkflowAPI) -> None:
        """List returns mock workflows."""
        result = mock_api.list_workflows()

        assert result.status == ResultStatus.SUCCESS
        assert len(result.workflows) > 0
        assert result.total_count > 0

    def test_list_filter_by_source_type(self, mock_api: WorkflowAPI) -> None:
        """List filters by source type."""
        result = mock_api.list_workflows(source_type=SourceType.MANUAL)

        for wf in result.workflows:
            assert wf.source_type == SourceType.MANUAL


class TestWorkflowAPIHistory:
    """Tests for WorkflowAPI.history()."""

    def test_history_mock_returns_runs(self, mock_api: WorkflowAPI) -> None:
        """History returns mock runs."""
        result = mock_api.history(dataset_name="test.dataset")

        assert result.status == ResultStatus.SUCCESS
        assert len(result.runs) > 0


class TestWorkflowAPIPauseUnpause:
    """Tests for WorkflowAPI.pause() and unpause()."""

    def test_pause_mock_success(self, mock_api: WorkflowAPI) -> None:
        """Pause returns success."""
        result = mock_api.pause("test.dataset")

        assert result.status == ResultStatus.SUCCESS
        assert "paused" in result.message.lower()

    def test_unpause_mock_success(self, mock_api: WorkflowAPI) -> None:
        """Unpause returns success."""
        result = mock_api.unpause("test.dataset")

        assert result.status == ResultStatus.SUCCESS
        assert "resumed" in result.message.lower()
```

### 10.2 CLI Command Tests

```python
# tests/commands/test_workflow_cmd.py

import pytest
from typer.testing import CliRunner

from dli.main import app


@pytest.fixture
def runner() -> CliRunner:
    return CliRunner()


class TestWorkflowRegisterCommand:
    """Tests for `dli workflow register` command."""

    def test_register_success(self, runner: CliRunner) -> None:
        """Register command succeeds with valid args."""
        result = runner.invoke(
            app,
            ["workflow", "register", "test.dataset", "--cron", "0 9 * * *"],
        )

        assert result.exit_code == 0
        assert "registered" in result.output.lower()

    def test_register_missing_cron(self, runner: CliRunner) -> None:
        """Register fails without --cron."""
        result = runner.invoke(
            app,
            ["workflow", "register", "test.dataset"],
        )

        assert result.exit_code != 0

    def test_register_with_timezone(self, runner: CliRunner) -> None:
        """Register with timezone option."""
        result = runner.invoke(
            app,
            [
                "workflow", "register", "test.dataset",
                "--cron", "0 9 * * *",
                "--timezone", "Asia/Seoul",
            ],
        )

        assert result.exit_code == 0
        assert "Asia/Seoul" in result.output


class TestWorkflowListCommand:
    """Tests for `dli workflow list` command."""

    def test_list_all(self, runner: CliRunner) -> None:
        """List all workflows."""
        result = runner.invoke(app, ["workflow", "list"])

        assert result.exit_code == 0

    def test_list_filter_source(self, runner: CliRunner) -> None:
        """List with source filter."""
        result = runner.invoke(
            app,
            ["workflow", "list", "--source", "manual"],
        )

        assert result.exit_code == 0


class TestWorkflowHistoryCommand:
    """Tests for `dli workflow history` command."""

    def test_history_basic(self, runner: CliRunner) -> None:
        """History command basic execution."""
        result = runner.invoke(app, ["workflow", "history"])

        assert result.exit_code == 0

    def test_history_with_dataset_info(self, runner: CliRunner) -> None:
        """History with dataset info flag."""
        result = runner.invoke(
            app,
            ["workflow", "history", "-d", "test.dataset", "--show-dataset-info"],
        )

        assert result.exit_code == 0
```

---

## 11. Reference Patterns

### 11.1 Directory Structure

```
project-interface-cli/src/dli/
+-- __init__.py           # Add WorkflowAPI export
+-- api/
|   +-- __init__.py       # Add WorkflowAPI export
|   +-- workflow.py       # NEW: WorkflowAPI class
+-- models/
|   +-- __init__.py       # Add workflow model exports
|   +-- workflow.py       # NEW: Result models
+-- commands/
|   +-- workflow.py       # ADD: register command
+-- core/
|   +-- client.py         # ADD: workflow_register method
|   +-- workflow/
|       +-- models.py     # EXISTING: WorkflowInfo, WorkflowRun, etc.
+-- exceptions.py         # ADD: DLI-8xx codes, Workflow exceptions
```

### 11.2 File References

| Implementation | Reference File | Pattern to Follow |
|----------------|----------------|-------------------|
| WorkflowAPI | `api/dataset.py` | Facade pattern, mock mode, DI |
| Result models | `models/common.py` | Pydantic BaseModel with Field |
| CLI register | `commands/dataset.py` | Typer command, Rich output |
| Client method | `core/client.py` | ServerResponse, mock_mode check |
| Exceptions | `exceptions.py` | DLIError inheritance, ErrorCode |

---

## Appendix A: Command Summary

```bash
# Registration (NEW)
dli workflow register <dataset> --cron "0 9 * * *" [--timezone tz] [--disabled] [--force]

# Execution
dli workflow run <dataset> -p key=value [--dry-run]
dli workflow backfill <dataset> -s <start> -e <end> [--dry-run]
dli workflow stop <run_id>

# Query
dli workflow status <run_id>
dli workflow list [--source code|manual|all] [--running] [--enabled-only]
dli workflow history [-d dataset] [--source] [-n limit] [--show-dataset-info]

# Schedule Control
dli workflow pause <dataset>
dli workflow unpause <dataset>
```

---

## Appendix B: Decision Summary (Interview-Based)

| Item | Decision | Trade-off Analysis | Rationale |
|------|----------|-------------------|-----------|
| **API Pattern** | Facade over BasecampClient | Direct client access vs abstraction | Consistent with DatasetAPI pattern |
| **Error Code Range** | DLI-8xx | Extend existing vs new scheme | Avoid collision with existing 0xx-7xx |
| **Register Source** | Local Dataset Spec | Local vs Server-defined | Reuse existing Dataset validation |
| **Mock Priority** | Full mock support first | Mock vs real server | Enable development without server |
| **Enhanced Features** | Phase 2 | MVP vs full feature | Deliver core value first |
| **Source Type Filter** | CLI `--source` option | Query param vs separate commands | Consistent with existing patterns |
| **Dataset Info** | Optional `--show-dataset-info` | Always vs optional | Avoid unnecessary API calls |

---

## Appendix C: Implementation Agent Review

### Domain Implementer Review (feature-interface-cli)

**Reviewer**: `feature-interface-cli` Agent
**Review Date**: 2025-12-31

| Priority | Issue | Resolution |
|----------|-------|------------|
| **HIGH** | `WORKFLOW_NOT_FOUND` error code location | ✅ Fixed: Move from DLI-104 to DLI-800 (all workflow errors in 8xx) |
| **HIGH** | Missing `workflow_register` in BasecampClient method list | ✅ Documented: Add to client.py implementation |
| **MEDIUM** | Result model location inconsistency with common.py | ✅ Clarified: Separate `models/workflow.py` is acceptable |
| **MEDIUM** | `WorkflowStatusResult` overlaps with `WorkflowRun` | Noted: Consider composition in Phase 2 |
| **LOW** | Missing `WorkflowStatusFilter` Literal for CLI `--status` | ✅ To add in CLI implementation |
| **SUGGESTION** | Add `get()` method for API completeness | ✅ Added: `get(dataset_name)` method |
| **SUGGESTION** | Add `unregister()` method for MANUAL deletion | ✅ Added: `unregister(dataset_name)` method |
| **SUGGESTION** | Add cron expression validation | Noted: Add croniter validation in implementation |

### Technical Senior Review (expert-python)

**Reviewer**: `expert-python` Agent
**Review Date**: 2025-12-31

| Priority | Issue | Resolution |
|----------|-------|------------|
| **HIGH** | Exception classes use `__init__` instead of `@dataclass` | ✅ Fixed: Converted to @dataclass pattern |
| **HIGH** | `WorkflowNotFoundError` error code consolidation | ✅ Fixed: Move to DLI-800 (8xx range for all workflow errors) |
| **MEDIUM** | Missing `model_config = ConfigDict(frozen=True)` | ✅ Fixed: Added to all result models |
| **MEDIUM** | Two workflow model files may cause confusion | ✅ Documented: Added note clarifying separation |
| **MEDIUM** | Enum import consistency needs verification | Noted: Verify enums exist in core/workflow/models.py |
| **LOW** | `WorkflowRunResult` reused for multiple operations | Noted: Acceptable, consider specific types in Phase 2 |
| **SUGGESTION** | Add `__repr__` to result models | Noted: Add for debugging in implementation |
| **SUGGESTION** | Add `is_running`, `is_terminal` properties | ✅ Added: Properties to WorkflowStatusResult |
| **SUGGESTION** | Consolidate test fixtures to `conftest.py` | Noted: Apply in test implementation |

### Summary of Changes Applied

| Category | Change |
|----------|--------|
| **Error Codes** | Move WORKFLOW_NOT_FOUND to DLI-800, all workflow errors in DLI-8xx range |
| **Exception Design** | Convert to @dataclass pattern (consistent with existing exceptions) |
| **Result Models** | Add `model_config = ConfigDict(frozen=True)` to all models |
| **WorkflowAPI Methods** | Add `get()` and `unregister()` methods |
| **WorkflowStatusResult** | Add `is_running` and `is_terminal` computed properties |
| **Documentation** | Clarify models/workflow.py separation from models/common.py |
