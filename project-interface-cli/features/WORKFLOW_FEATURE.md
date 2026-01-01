# FEATURE: Workflow Library API and Enhanced Commands

> **Version:** 3.0.0
> **Status:** Draft
> **Created:** 2025-12-30
> **Last Updated:** 2025-12-31

---

## 1. Overview

### 1.1 Purpose

`dli workflow` provides programmatic and CLI access to manage Dataset schedules on Airflow through Basecamp Server. This version adds:

- **WorkflowAPI**: Library API for programmatic workflow management
- **`workflow register`**: Register local Dataset as MANUAL workflow
- **Enhanced `workflow list`**: Filter by source type (MANUAL/CODE)
- **Enhanced `workflow history`**: Display Dataset metadata in execution history

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

### 4.1 WorkflowAPI Class

```python
# dli/api/workflow.py

from __future__ import annotations

from datetime import datetime
from typing import Any

from dli.core.client import BasecampClient
from dli.core.workflow.models import (
    SourceType,
    WorkflowInfo,
    WorkflowRun,
    WorkflowStatus,
    RunStatus,
    ScheduleConfig,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    WorkflowNotFoundError,
    WorkflowRegistrationError,
    WorkflowExecutionError,
    WorkflowPermissionError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.workflow import (
    WorkflowRegisterResult,
    WorkflowRunResult,
    WorkflowListResult,
    WorkflowStatusResult,
    WorkflowHistoryResult,
)

__all__ = ["WorkflowAPI"]


class WorkflowAPI:
    """Library API for workflow management.

    Provides programmatic access to workflow operations including
    registration, execution, and monitoring.

    Example:
        >>> from dli import WorkflowAPI, ExecutionContext, ExecutionMode
        >>> ctx = ExecutionContext(
        ...     execution_mode=ExecutionMode.SERVER,
        ...     server_url="http://basecamp:8080",
        ... )
        >>> api = WorkflowAPI(context=ctx)
        >>> result = api.run("iceberg.analytics.daily_clicks", parameters={"date": "2025-01-01"})
        >>> print(result.run_id)

    Attributes:
        context: Execution context with mode, server URL, project path, etc.
    """

    def __init__(
        self,
        context: ExecutionContext | None = None,
        *,
        client: BasecampClient | None = None,  # DI for testing
    ) -> None:
        """Initialize WorkflowAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        return f"WorkflowAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance."""
        if self._client is not None:
            return self._client

        if self._is_mock_mode:
            return BasecampClient(mock_mode=True)

        if not self.context.server_url:
            raise ConfigurationError(
                message="server_url required for SERVER mode",
                code=ErrorCode.CONFIG_INVALID,
            )

        return BasecampClient(
            server_url=self.context.server_url,
            api_token=self.context.api_token,
            mock_mode=False,
        )

    # =========================================================================
    # Get / Lookup
    # =========================================================================

    def get(self, dataset_name: str) -> WorkflowInfo | None:
        """Get workflow info for a dataset.

        Args:
            dataset_name: Fully qualified dataset name.

        Returns:
            WorkflowInfo if workflow exists, None otherwise.

        Example:
            >>> info = api.get("iceberg.analytics.daily_clicks")
            >>> if info:
            ...     print(info.status, info.cron)
        """
        if self._is_mock_mode:
            return WorkflowInfo(
                dataset_name=dataset_name,
                source_type=SourceType.MANUAL,
                status=WorkflowStatus.ACTIVE,
                cron="0 9 * * *",
                timezone="UTC",
                next_run=datetime.now(),
            )

        client = self._get_client()
        response = client.workflow_list(dataset_name=dataset_name)

        if not response.success or not response.data:
            return None

        workflows = response.data.get("workflows", [])
        if not workflows:
            return None

        return WorkflowInfo.model_validate(workflows[0])

    # =========================================================================
    # Registration
    # =========================================================================

    def register(
        self,
        dataset_name: str,
        *,
        cron: str,
        timezone: str = "UTC",
        enabled: bool = True,
        retry_max_attempts: int = 1,
        retry_delay_seconds: int = 300,
        force: bool = False,
    ) -> WorkflowRegisterResult:
        """Register a local Dataset as MANUAL workflow.

        Uploads the Dataset Spec to S3 manual/ path and registers
        the schedule with Airflow via Basecamp Server.

        Args:
            dataset_name: Fully qualified dataset name (e.g., "iceberg.analytics.daily_clicks")
            cron: Cron expression (5-field format, e.g., "0 9 * * *")
            timezone: IANA timezone (default: "UTC")
            enabled: Whether to enable schedule immediately (default: True)
            retry_max_attempts: Max retry attempts on failure (default: 1)
            retry_delay_seconds: Delay between retries in seconds (default: 300)
            force: If True, overwrite existing MANUAL registration (default: False)

        Returns:
            WorkflowRegisterResult with registration status and workflow info.

        Raises:
            WorkflowRegistrationError: If registration fails.
            WorkflowPermissionError: If CODE workflow exists (cannot override).
            DatasetNotFoundError: If local Dataset Spec not found.

        Example:
            >>> result = api.register(
            ...     "iceberg.analytics.daily_clicks",
            ...     cron="0 9 * * *",
            ...     timezone="Asia/Seoul",
            ... )
            >>> print(result.workflow_info.status)
            active
        """
        if self._is_mock_mode:
            return WorkflowRegisterResult(
                dataset_name=dataset_name,
                status=ResultStatus.SUCCESS,
                source_type=SourceType.MANUAL,
                workflow_info=WorkflowInfo(
                    dataset_name=dataset_name,
                    source_type=SourceType.MANUAL,
                    status=WorkflowStatus.ACTIVE if enabled else WorkflowStatus.PAUSED,
                    cron=cron,
                    timezone=timezone,
                    next_run=datetime.now() if enabled else None,
                ),
                message=f"Mock: Workflow '{dataset_name}' registered successfully",
            )

        client = self._get_client()
        schedule_config = ScheduleConfig(
            enabled=enabled,
            cron=cron,
            timezone=timezone,
            retry_max_attempts=retry_max_attempts,
            retry_delay_seconds=retry_delay_seconds,
        )

        response = client.workflow_register(
            dataset_name=dataset_name,
            schedule_config=schedule_config,
            force=force,
        )

        if not response.success:
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    dataset_name=dataset_name,
                    message=response.error or "Cannot register: CODE workflow exists",
                )
            raise WorkflowRegistrationError(
                dataset_name=dataset_name,
                message=response.error or "Registration failed",
            )

        workflow_info = WorkflowInfo.model_validate(response.data)
        return WorkflowRegisterResult(
            dataset_name=dataset_name,
            status=ResultStatus.SUCCESS,
            source_type=SourceType.MANUAL,
            workflow_info=workflow_info,
            message=f"Workflow '{dataset_name}' registered successfully",
        )

    def unregister(self, dataset_name: str) -> WorkflowRunResult:
        """Unregister a MANUAL workflow.

        Removes the workflow from S3 manual/ path and unschedules from Airflow.
        Only MANUAL workflows can be unregistered via CLI/API.

        Args:
            dataset_name: Fully qualified dataset name.

        Returns:
            WorkflowRunResult indicating success.

        Raises:
            WorkflowNotFoundError: If workflow not found.
            WorkflowPermissionError: If CODE workflow (cannot delete).

        Example:
            >>> result = api.unregister("iceberg.analytics.daily_clicks")
            >>> print(result.message)
            Workflow 'iceberg.analytics.daily_clicks' unregistered
        """
        if self._is_mock_mode:
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=None,
                status=ResultStatus.SUCCESS,
                message=f"Mock: Workflow '{dataset_name}' unregistered",
            )

        client = self._get_client()
        response = client.workflow_unregister(dataset_name=dataset_name)

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(dataset_name=dataset_name)
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    dataset_name=dataset_name,
                    message="Cannot unregister: CODE workflow. Use Git to remove.",
                )
            raise WorkflowRegistrationError(
                dataset_name=dataset_name,
                message=response.error or "Unregister failed",
                code=ErrorCode.WORKFLOW_UNREGISTER_FAILED,
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=None,
            status=ResultStatus.SUCCESS,
            message=f"Workflow '{dataset_name}' unregistered",
        )

    # =========================================================================
    # Execution
    # =========================================================================

    def run(
        self,
        dataset_name: str,
        *,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
    ) -> WorkflowRunResult:
        """Trigger adhoc workflow execution on server.

        Args:
            dataset_name: Fully qualified dataset name.
            parameters: Execution parameters (e.g., {"execution_date": "2025-01-01"}).
            dry_run: If True, validate only without actual execution.

        Returns:
            WorkflowRunResult with run_id and status.

        Raises:
            WorkflowNotFoundError: If workflow not registered.
            WorkflowExecutionError: If execution trigger fails.
            WorkflowPermissionError: If workflow is overridden.

        Example:
            >>> result = api.run(
            ...     "iceberg.analytics.daily_clicks",
            ...     parameters={"execution_date": "2025-01-01"},
            ... )
            >>> print(result.run_id)
        """
        if self._is_mock_mode:
            mock_run_id = f"mock_{dataset_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=mock_run_id,
                status=ResultStatus.SUCCESS,
                run_status=RunStatus.PENDING if not dry_run else None,
                dry_run=dry_run,
                message="Mock: Run triggered successfully" if not dry_run else "Mock: Dry run validated",
            )

        client = self._get_client()
        response = client.workflow_run(
            dataset_name=dataset_name,
            parameters=parameters or {},
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(dataset_name=dataset_name)
            raise WorkflowExecutionError(
                dataset_name=dataset_name,
                message=response.error or "Execution trigger failed",
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=response.data.get("run_id"),
            status=ResultStatus.SUCCESS,
            run_status=RunStatus(response.data.get("status", "PENDING")),
            dry_run=dry_run,
            message=response.data.get("message"),
        )

    def backfill(
        self,
        dataset_name: str,
        *,
        start_date: str,
        end_date: str,
        parameters: dict[str, Any] | None = None,
        dry_run: bool = False,
    ) -> WorkflowRunResult:
        """Run backfill for date range.

        Executes workflow sequentially from start_date to end_date.
        Stops on first failure.

        Args:
            dataset_name: Fully qualified dataset name.
            start_date: Start date (YYYY-MM-DD format).
            end_date: End date (YYYY-MM-DD format).
            parameters: Additional parameters.
            dry_run: If True, validate only.

        Returns:
            WorkflowRunResult with backfill run_id.

        Raises:
            WorkflowNotFoundError: If workflow not registered.
            WorkflowExecutionError: If backfill trigger fails.
        """
        if self._is_mock_mode:
            mock_run_id = f"mock_backfill_{dataset_name}_{start_date}_{end_date}"
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=mock_run_id,
                status=ResultStatus.SUCCESS,
                run_status=RunStatus.PENDING if not dry_run else None,
                dry_run=dry_run,
                message=f"Mock: Backfill {start_date} to {end_date} triggered",
            )

        client = self._get_client()
        response = client.workflow_backfill(
            dataset_name=dataset_name,
            start_date=start_date,
            end_date=end_date,
            parameters=parameters or {},
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(dataset_name=dataset_name)
            raise WorkflowExecutionError(
                dataset_name=dataset_name,
                message=response.error or "Backfill trigger failed",
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=response.data.get("run_id"),
            status=ResultStatus.SUCCESS,
            run_status=RunStatus(response.data.get("status", "PENDING")),
            dry_run=dry_run,
            message=response.data.get("message"),
        )

    def stop(self, run_id: str) -> WorkflowRunResult:
        """Stop a running workflow execution.

        Args:
            run_id: The run ID to stop.

        Returns:
            WorkflowRunResult with updated status.

        Raises:
            WorkflowExecutionError: If stop fails.
        """
        if self._is_mock_mode:
            return WorkflowRunResult(
                dataset_name="unknown",
                run_id=run_id,
                status=ResultStatus.SUCCESS,
                run_status=RunStatus.KILLED,
                message="Mock: Run stopped successfully",
            )

        client = self._get_client()
        response = client.workflow_stop(run_id=run_id)

        if not response.success:
            raise WorkflowExecutionError(
                dataset_name="unknown",
                run_id=run_id,
                message=response.error or "Stop failed",
            )

        return WorkflowRunResult(
            dataset_name=response.data.get("dataset_name", "unknown"),
            run_id=run_id,
            status=ResultStatus.SUCCESS,
            run_status=RunStatus.KILLED,
            message="Run stopped successfully",
        )

    # =========================================================================
    # Query
    # =========================================================================

    def get_status(self, run_id: str) -> WorkflowStatusResult:
        """Get status of a workflow run.

        Args:
            run_id: The run ID to query.

        Returns:
            WorkflowStatusResult with detailed run information.

        Raises:
            WorkflowNotFoundError: If run_id not found.
        """
        if self._is_mock_mode:
            return WorkflowStatusResult(
                run_id=run_id,
                dataset_name="mock_dataset",
                source_type=SourceType.MANUAL,
                run_status=RunStatus.COMPLETED,
                run_type="adhoc",
                parameters={},
                started_at=datetime.now(),
                finished_at=datetime.now(),
            )

        client = self._get_client()
        response = client.workflow_status(run_id=run_id)

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(
                    dataset_name="unknown",
                    message=f"Run '{run_id}' not found",
                )
            raise WorkflowExecutionError(
                dataset_name="unknown",
                run_id=run_id,
                message=response.error or "Status query failed",
            )

        run_data = response.data
        return WorkflowStatusResult(
            run_id=run_id,
            dataset_name=run_data.get("dataset_name"),
            source_type=SourceType(run_data.get("source_type", "manual")),
            run_status=RunStatus(run_data.get("status")),
            run_type=run_data.get("run_type", "adhoc"),
            parameters=run_data.get("parameters", {}),
            started_at=run_data.get("started_at"),
            finished_at=run_data.get("finished_at"),
            error_message=run_data.get("error_message"),
        )

    def list_workflows(
        self,
        *,
        source_type: SourceType | None = None,
        status: WorkflowStatus | None = None,
        dataset_filter: str | None = None,
        running_only: bool = False,
        enabled_only: bool = False,
        limit: int = 100,
    ) -> WorkflowListResult:
        """List registered workflows.

        Args:
            source_type: Filter by source type (MANUAL/CODE).
            status: Filter by workflow status.
            dataset_filter: Filter by dataset name pattern.
            running_only: Show only currently running workflows.
            enabled_only: Show only enabled (not paused) workflows.
            limit: Maximum number of results.

        Returns:
            WorkflowListResult with list of WorkflowInfo.

        Example:
            >>> result = api.list_workflows(source_type=SourceType.MANUAL)
            >>> for wf in result.workflows:
            ...     print(f"{wf.dataset_name}: {wf.status}")
        """
        if self._is_mock_mode:
            mock_workflows = [
                WorkflowInfo(
                    dataset_name="mock.dataset.one",
                    source_type=SourceType.MANUAL,
                    status=WorkflowStatus.ACTIVE,
                    cron="0 9 * * *",
                    timezone="UTC",
                    next_run=datetime.now(),
                ),
                WorkflowInfo(
                    dataset_name="mock.dataset.two",
                    source_type=SourceType.CODE,
                    status=WorkflowStatus.PAUSED,
                    cron="0 10 * * *",
                    timezone="Asia/Seoul",
                ),
            ]
            # Apply filters
            if source_type:
                mock_workflows = [w for w in mock_workflows if w.source_type == source_type]
            if status:
                mock_workflows = [w for w in mock_workflows if w.status == status]

            return WorkflowListResult(
                workflows=mock_workflows[:limit],
                total_count=len(mock_workflows),
                status=ResultStatus.SUCCESS,
            )

        client = self._get_client()
        response = client.workflow_list(
            source_type=source_type.value if source_type else None,
            status=status.value if status else None,
            dataset_filter=dataset_filter,
            running_only=running_only,
            enabled_only=enabled_only,
            limit=limit,
        )

        if not response.success:
            raise WorkflowExecutionError(
                dataset_name="unknown",
                message=response.error or "List query failed",
            )

        workflows = [WorkflowInfo.model_validate(w) for w in response.data.get("workflows", [])]
        return WorkflowListResult(
            workflows=workflows,
            total_count=response.data.get("total_count", len(workflows)),
            status=ResultStatus.SUCCESS,
        )

    def history(
        self,
        *,
        dataset_name: str | None = None,
        source_type: SourceType | None = None,
        run_status: RunStatus | None = None,
        limit: int = 20,
        include_dataset_info: bool = True,
    ) -> WorkflowHistoryResult:
        """Get workflow execution history.

        Args:
            dataset_name: Filter by dataset name.
            source_type: Filter by source type.
            run_status: Filter by run status.
            limit: Maximum number of results.
            include_dataset_info: Include Dataset metadata in results.

        Returns:
            WorkflowHistoryResult with list of WorkflowRun.

        Example:
            >>> result = api.history(dataset_name="iceberg.analytics.daily_clicks", limit=10)
            >>> for run in result.runs:
            ...     print(f"{run.run_id}: {run.status} ({run.duration_seconds}s)")
        """
        if self._is_mock_mode:
            mock_runs = [
                WorkflowRun(
                    run_id="mock_run_001",
                    dataset_name=dataset_name or "mock.dataset",
                    source_type=SourceType.MANUAL,
                    status=RunStatus.COMPLETED,
                    run_type="adhoc",
                    parameters={"execution_date": "2025-01-01"},
                    started_at=datetime.now(),
                    finished_at=datetime.now(),
                ),
            ]
            return WorkflowHistoryResult(
                runs=mock_runs[:limit],
                total_count=len(mock_runs),
                status=ResultStatus.SUCCESS,
            )

        client = self._get_client()
        response = client.workflow_history(
            dataset_name=dataset_name,
            source_type=source_type.value if source_type else None,
            status=run_status.value if run_status else None,
            limit=limit,
            include_dataset_info=include_dataset_info,
        )

        if not response.success:
            raise WorkflowExecutionError(
                dataset_name=dataset_name or "unknown",
                message=response.error or "History query failed",
            )

        runs = [WorkflowRun.model_validate(r) for r in response.data.get("runs", [])]
        return WorkflowHistoryResult(
            runs=runs,
            total_count=response.data.get("total_count", len(runs)),
            status=ResultStatus.SUCCESS,
            dataset_info=response.data.get("dataset_info"),
        )

    # =========================================================================
    # Schedule Control
    # =========================================================================

    def pause(self, dataset_name: str) -> WorkflowRunResult:
        """Pause a workflow schedule.

        Args:
            dataset_name: Fully qualified dataset name.

        Returns:
            WorkflowRunResult indicating success.

        Raises:
            WorkflowNotFoundError: If workflow not found.
        """
        if self._is_mock_mode:
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=None,
                status=ResultStatus.SUCCESS,
                message=f"Mock: Workflow '{dataset_name}' paused",
            )

        client = self._get_client()
        response = client.workflow_pause(dataset_name=dataset_name)

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(dataset_name=dataset_name)
            raise WorkflowExecutionError(
                dataset_name=dataset_name,
                message=response.error or "Pause failed",
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=None,
            status=ResultStatus.SUCCESS,
            message=f"Workflow '{dataset_name}' paused",
        )

    def unpause(self, dataset_name: str) -> WorkflowRunResult:
        """Resume a paused workflow schedule.

        Args:
            dataset_name: Fully qualified dataset name.

        Returns:
            WorkflowRunResult indicating success.

        Raises:
            WorkflowNotFoundError: If workflow not found.
            WorkflowPermissionError: If workflow is overridden.
        """
        if self._is_mock_mode:
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=None,
                status=ResultStatus.SUCCESS,
                message=f"Mock: Workflow '{dataset_name}' resumed",
            )

        client = self._get_client()
        response = client.workflow_unpause(dataset_name=dataset_name)

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(dataset_name=dataset_name)
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    dataset_name=dataset_name,
                    message="Cannot unpause: Workflow is overridden by CODE",
                )
            raise WorkflowExecutionError(
                dataset_name=dataset_name,
                message=response.error or "Unpause failed",
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=None,
            status=ResultStatus.SUCCESS,
            message=f"Workflow '{dataset_name}' resumed",
        )
```

### 4.2 Result Models

```python
# dli/models/workflow.py
# Note: Separate from models/common.py due to workflow-specific complexity.

from __future__ import annotations

from datetime import datetime
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from dli.core.workflow.models import (
    SourceType,
    WorkflowInfo,
    WorkflowRun,
    WorkflowStatus,
    RunStatus,
)
from dli.models.common import ResultStatus

__all__ = [
    "WorkflowRegisterResult",
    "WorkflowRunResult",
    "WorkflowListResult",
    "WorkflowStatusResult",
    "WorkflowHistoryResult",
]


class WorkflowRegisterResult(BaseModel):
    """Result of workflow registration."""

    model_config = ConfigDict(frozen=True)

    dataset_name: str = Field(description="Registered dataset name")
    status: ResultStatus = Field(description="Operation status")
    source_type: SourceType = Field(description="Source type (always MANUAL for register)")
    workflow_info: WorkflowInfo | None = Field(default=None, description="Registered workflow info")
    message: str | None = Field(default=None, description="Status message")
    warning: str | None = Field(default=None, description="Warning if CODE exists")


class WorkflowRunResult(BaseModel):
    """Result of workflow execution operation (run/backfill/stop/pause/unpause)."""

    model_config = ConfigDict(frozen=True)

    dataset_name: str = Field(description="Dataset name")
    run_id: str | None = Field(default=None, description="Run ID (if applicable)")
    status: ResultStatus = Field(description="Operation status")
    run_status: RunStatus | None = Field(default=None, description="Current run status")
    dry_run: bool = Field(default=False, description="Whether this was a dry run")
    message: str | None = Field(default=None, description="Status message")


class WorkflowListResult(BaseModel):
    """Result of workflow list query."""

    model_config = ConfigDict(frozen=True)

    workflows: list[WorkflowInfo] = Field(default_factory=list, description="List of workflows")
    total_count: int = Field(description="Total count (may differ from len(workflows) if paginated)")
    status: ResultStatus = Field(description="Query status")


class WorkflowStatusResult(BaseModel):
    """Detailed status of a workflow run."""

    model_config = ConfigDict(frozen=True)

    run_id: str = Field(description="Run ID")
    dataset_name: str = Field(description="Dataset name")
    source_type: SourceType = Field(description="Source type")
    run_status: RunStatus = Field(description="Current run status")
    run_type: Literal["adhoc", "scheduled", "backfill"] = Field(description="Run type")
    parameters: dict[str, Any] = Field(default_factory=dict, description="Execution parameters")
    started_at: datetime | None = Field(default=None, description="Start time")
    finished_at: datetime | None = Field(default=None, description="Finish time")
    error_message: str | None = Field(default=None, description="Error message if failed")

    @property
    def is_running(self) -> bool:
        """Check if workflow is currently running."""
        return self.run_status in (RunStatus.RUNNING, RunStatus.PENDING)

    @property
    def is_terminal(self) -> bool:
        """Check if workflow has reached terminal state."""
        return self.run_status in (RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.KILLED)

    @property
    def duration_seconds(self) -> float | None:
        """Calculate run duration in seconds."""
        if self.started_at and self.finished_at:
            return (self.finished_at - self.started_at).total_seconds()
        return None


class WorkflowHistoryResult(BaseModel):
    """Result of workflow history query."""

    model_config = ConfigDict(frozen=True)

    runs: list[WorkflowRun] = Field(default_factory=list, description="List of runs")
    total_count: int = Field(description="Total count")
    status: ResultStatus = Field(description="Query status")
    dataset_info: dict[str, Any] | None = Field(
        default=None,
        description="Dataset metadata (owner, team, description) if include_dataset_info=True",
    )
```

### 4.3 Error Codes (DLI-8xx)

> **Note:** `WORKFLOW_NOT_FOUND`를 기존 `DLI-104`에서 `DLI-800`으로 이동합니다.
> 모든 Workflow 관련 에러 코드는 DLI-8xx 범위로 통합됩니다.

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

### Phase 1 (MVP)

- [ ] **WorkflowAPI class** with mock support
- [ ] **Result models** (`WorkflowRegisterResult`, etc.)
- [ ] **Error codes** (DLI-8xx) and exceptions
- [ ] **`workflow register`** CLI command
- [ ] **Enhanced `workflow list`** with `--source` filter
- [ ] **Unit tests** for WorkflowAPI mock mode

### Phase 2

- [ ] **Enhanced `workflow history`** with `--show-dataset-info`
- [ ] **BasecampClient.workflow_register()** mock implementation
- [ ] **Integration tests** with mock server
- [ ] **Public exports** in `dli/__init__.py`

### Phase 3 (Server Implementation Required)

- [ ] **Basecamp Server API** endpoints for workflow management
- [ ] **S3 integration** for manual/ path uploads
- [ ] **Airflow integration** for schedule registration
- [ ] **End-to-end tests** with real server

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
