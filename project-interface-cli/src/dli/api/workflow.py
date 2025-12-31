"""WorkflowAPI - Library API for Workflow operations.

This module provides the WorkflowAPI class which wraps the BasecampClient
for programmatic access to workflow operations including registration,
execution, monitoring, and schedule control.

Example:
    >>> from dli import WorkflowAPI, ExecutionContext, ExecutionMode
    >>> ctx = ExecutionContext(
    ...     execution_mode=ExecutionMode.SERVER,
    ...     server_url="http://basecamp:8080",
    ... )
    >>> api = WorkflowAPI(context=ctx)
    >>> result = api.run("iceberg.analytics.daily_clicks", parameters={"date": "2025-01-01"})
    >>> print(result.run_id)

    >>> # List workflows by source type
    >>> from dli.core.workflow.models import SourceType
    >>> result = api.list_workflows(source_type=SourceType.MANUAL)
    >>> for wf in result.workflows:
    ...     print(f"{wf.dataset_name}: {wf.status}")
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any

from dli.core.client import BasecampClient, ServerConfig
from dli.core.workflow.models import (
    RunStatus,
    SourceType,
    WorkflowInfo,
    WorkflowRun,
    WorkflowStatus,
)
from dli.exceptions import (
    ConfigurationError,
    ErrorCode,
    WorkflowExecutionError,
    WorkflowNotFoundError,
    WorkflowPermissionError,
    WorkflowRegistrationError,
)
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus
from dli.models.workflow import (
    WorkflowHistoryResult,
    WorkflowListResult,
    WorkflowRegisterResult,
    WorkflowRunResult,
    WorkflowStatusResult,
)

if TYPE_CHECKING:
    pass

__all__ = ["WorkflowAPI"]


class WorkflowAPI:
    """Library API for workflow management.

    Provides programmatic access to workflow operations including
    registration, execution, and monitoring.

    This class follows the same patterns as DatasetAPI and MetricAPI:
    - Facade pattern over BasecampClient
    - Full mock mode support for testing
    - Dependency injection for client

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations (standard pattern for Kubernetes Airflow).

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
        client: BasecampClient | None = None,
    ) -> None:
        """Initialize WorkflowAPI.

        Args:
            context: Execution context. Defaults to ExecutionContext().
            client: Optional BasecampClient for dependency injection.
        """
        self.context = context or ExecutionContext()
        self._client = client

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"WorkflowAPI(context={self.context!r})"

    @property
    def _is_mock_mode(self) -> bool:
        """Check if running in mock mode."""
        return self.context.execution_mode == ExecutionMode.MOCK

    def _get_client(self) -> BasecampClient:
        """Get or create BasecampClient instance (lazy initialization).

        Returns:
            BasecampClient instance.

        Raises:
            ConfigurationError: If server_url is required but not set.
        """
        if self._client is not None:
            return self._client

        if self._is_mock_mode:
            config = ServerConfig(url="http://mock-server")
            self._client = BasecampClient(config, mock_mode=True)
            return self._client

        if not self.context.server_url:
            raise ConfigurationError(
                message="server_url required for SERVER mode",
                code=ErrorCode.CONFIG_INVALID,
            )

        config = ServerConfig(
            url=self.context.server_url,
            api_key=self.context.api_token,
        )
        self._client = BasecampClient(config, mock_mode=False)
        return self._client

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
        response = client.workflow_list(dataset_filter=dataset_name)

        if not response.success or not response.data:
            return None

        # Find exact match
        workflows = response.data if isinstance(response.data, list) else []
        for wf_data in workflows:
            if wf_data.get("dataset_name") == dataset_name:
                return WorkflowInfo(
                    dataset_name=wf_data["dataset_name"],
                    source_type=SourceType(wf_data.get("source", "manual")),
                    status=WorkflowStatus.PAUSED
                    if wf_data.get("paused")
                    else WorkflowStatus.ACTIVE,
                    cron=wf_data.get("schedule", ""),
                    timezone=wf_data.get("timezone", "UTC"),
                    next_run=wf_data.get("next_run_at"),
                )

        return None

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
        response = client.workflow_register(
            dataset_name=dataset_name,
            cron=cron,
            timezone=timezone,
            enabled=enabled,
            retry_max_attempts=retry_max_attempts,
            retry_delay_seconds=retry_delay_seconds,
            force=force,
        )

        if not response.success:
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    dataset_name=dataset_name,
                    message=response.error or "Cannot register: CODE workflow exists",
                )
            if response.status_code == 409:
                raise WorkflowRegistrationError(
                    dataset_name=dataset_name,
                    message=response.error or "Workflow already exists",
                    code=ErrorCode.WORKFLOW_ALREADY_EXISTS,
                )
            raise WorkflowRegistrationError(
                dataset_name=dataset_name,
                message=response.error or "Registration failed",
            )

        # Parse response data into WorkflowInfo
        data = response.data if isinstance(response.data, dict) else {}
        workflow_info = WorkflowInfo(
            dataset_name=data.get("dataset_name", dataset_name),
            source_type=SourceType(data.get("source_type", "manual")),
            status=WorkflowStatus(data.get("status", "active")),
            cron=data.get("cron", cron),
            timezone=data.get("timezone", timezone),
            next_run=data.get("next_run"),
        )

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
                raise WorkflowNotFoundError(
                    message=f"Workflow for dataset '{dataset_name}' not found",
                    dataset_name=dataset_name,
                )
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    message="Cannot unregister: CODE workflow. Use Git to remove.",
                    dataset_name=dataset_name,
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
            mock_run_id = (
                f"mock_{dataset_name}_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
            )
            return WorkflowRunResult(
                dataset_name=dataset_name,
                run_id=mock_run_id,
                status=ResultStatus.SUCCESS,
                run_status=RunStatus.PENDING if not dry_run else None,
                dry_run=dry_run,
                message="Mock: Run triggered successfully"
                if not dry_run
                else "Mock: Dry run validated",
            )

        client = self._get_client()
        response = client.workflow_run(
            dataset_name=dataset_name,
            params=parameters or {},
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(
                    message=f"Workflow for dataset '{dataset_name}' not found",
                    dataset_name=dataset_name,
                )
            raise WorkflowExecutionError(
                message=response.error or "Execution trigger failed",
                dataset_name=dataset_name,
            )

        data = response.data if isinstance(response.data, dict) else {}
        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=data.get("run_id"),
            status=ResultStatus.SUCCESS,
            run_status=RunStatus(data.get("status", "PENDING"))
            if not dry_run
            else None,
            dry_run=dry_run,
            message=data.get("message"),
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
            params=parameters or {},
            dry_run=dry_run,
        )

        if not response.success:
            if response.status_code == 404:
                raise WorkflowNotFoundError(
                    message=f"Workflow for dataset '{dataset_name}' not found",
                    dataset_name=dataset_name,
                )
            raise WorkflowExecutionError(
                message=response.error or "Backfill trigger failed",
                dataset_name=dataset_name,
            )

        data = response.data if isinstance(response.data, dict) else {}
        # Get first run ID from the backfill response
        runs = data.get("runs", [])
        run_id = runs[0].get("run_id") if runs else None

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=run_id,
            status=ResultStatus.SUCCESS,
            run_status=RunStatus.PENDING if not dry_run else None,
            dry_run=dry_run,
            message=f"Backfill started: {data.get('total_runs', 0)} runs",
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

        data = response.data if isinstance(response.data, dict) else {}
        return WorkflowRunResult(
            dataset_name=data.get("dataset_name", "unknown"),
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

        run_data = response.data if isinstance(response.data, dict) else {}
        return WorkflowStatusResult(
            run_id=run_id,
            dataset_name=run_data.get("dataset_name", "unknown"),
            source_type=SourceType(run_data.get("source", "manual")),
            run_status=RunStatus(run_data.get("status", "PENDING")),
            run_type=run_data.get("run_type", "adhoc"),
            parameters=run_data.get("params", {}),
            started_at=run_data.get("started_at"),
            finished_at=run_data.get("ended_at"),
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
                mock_workflows = [
                    w for w in mock_workflows if w.source_type == source_type
                ]
            if status:
                mock_workflows = [w for w in mock_workflows if w.status == status]

            return WorkflowListResult(
                workflows=mock_workflows[:limit],
                total_count=len(mock_workflows),
                status=ResultStatus.SUCCESS,
            )

        client = self._get_client()
        response = client.workflow_list(
            source=source_type.value if source_type else None,
            dataset_filter=dataset_filter,
            running_only=running_only,
            enabled_only=enabled_only,
        )

        if not response.success:
            raise WorkflowExecutionError(
                dataset_name="unknown",
                message=response.error or "List query failed",
            )

        # Parse workflow data
        workflows_data = response.data if isinstance(response.data, list) else []
        workflows = []
        for wf_data in workflows_data[:limit]:
            wf_status = (
                WorkflowStatus.PAUSED
                if wf_data.get("paused")
                else WorkflowStatus.ACTIVE
            )
            if status and wf_status != status:
                continue
            workflows.append(
                WorkflowInfo(
                    dataset_name=wf_data["dataset_name"],
                    source_type=SourceType(wf_data.get("source", "manual")),
                    status=wf_status,
                    cron=wf_data.get("schedule", ""),
                    timezone=wf_data.get("timezone", "UTC"),
                    next_run=wf_data.get("next_run_at"),
                )
            )

        return WorkflowListResult(
            workflows=workflows,
            total_count=len(workflows),
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
                dataset_info={"owner": "mock@example.com", "team": "@mock-team"}
                if include_dataset_info
                else None,
            )

        client = self._get_client()
        response = client.workflow_history(
            dataset_filter=dataset_name,
            source=source_type.value if source_type else None,
            status_filter=run_status.value if run_status else None,
            limit=limit,
        )

        if not response.success:
            raise WorkflowExecutionError(
                dataset_name=dataset_name or "unknown",
                message=response.error or "History query failed",
            )

        # Parse run data
        runs_data = response.data if isinstance(response.data, list) else []
        runs = []
        for run_data in runs_data:
            runs.append(
                WorkflowRun(
                    run_id=run_data["run_id"],
                    dataset_name=run_data["dataset_name"],
                    source_type=SourceType(run_data.get("source", "manual")),
                    status=RunStatus(run_data.get("status", "PENDING")),
                    run_type=run_data.get("triggered_by", "adhoc"),
                    parameters=run_data.get("params", {}),
                    started_at=run_data.get("started_at"),
                    finished_at=run_data.get("ended_at"),
                )
            )

        # Get dataset info if requested and dataset_name is provided
        dataset_info = None
        if include_dataset_info and dataset_name and runs:
            # In a real implementation, we would fetch this from the server
            # For now, extract from runs if available
            dataset_info = {
                "owner": "unknown",
                "team": "unknown",
                "description": f"Workflow for {dataset_name}",
            }

        return WorkflowHistoryResult(
            runs=runs,
            total_count=len(runs),
            status=ResultStatus.SUCCESS,
            dataset_info=dataset_info,
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
                raise WorkflowNotFoundError(
                    message=f"Workflow for dataset '{dataset_name}' not found",
                    dataset_name=dataset_name,
                )
            raise WorkflowExecutionError(
                message=response.error or "Pause failed",
                dataset_name=dataset_name,
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
                raise WorkflowNotFoundError(
                    message=f"Workflow for dataset '{dataset_name}' not found",
                    dataset_name=dataset_name,
                )
            if response.status_code == 403:
                raise WorkflowPermissionError(
                    message="Cannot unpause: Workflow is overridden by CODE",
                    dataset_name=dataset_name,
                )
            raise WorkflowExecutionError(
                message=response.error or "Unpause failed",
                dataset_name=dataset_name,
            )

        return WorkflowRunResult(
            dataset_name=dataset_name,
            run_id=None,
            status=ResultStatus.SUCCESS,
            message=f"Workflow '{dataset_name}' resumed",
        )
