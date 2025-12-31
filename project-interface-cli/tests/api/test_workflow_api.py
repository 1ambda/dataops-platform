"""Tests for dli.api.workflow module.

Covers:
- WorkflowAPI initialization with context
- Mock mode operations
- CRUD operations (get, register, unregister)
- Execution (run, backfill, stop)
- Query operations (get_status, list_workflows, history)
- Schedule control (pause, unpause)
- Result model properties and validation
"""

from __future__ import annotations

from datetime import datetime
from unittest.mock import MagicMock, patch

import pytest

from dli import ExecutionContext, WorkflowAPI
from dli.core.workflow.models import (
    RunStatus,
    SourceType,
    WorkflowInfo,
    WorkflowRun,
    WorkflowStatus,
)
from dli.exceptions import (
    ConfigurationError,
    WorkflowExecutionError,
    WorkflowNotFoundError,
    WorkflowPermissionError,
    WorkflowRegistrationError,
)
from dli.models.common import ExecutionMode, ResultStatus
from dli.models.workflow import (
    WorkflowHistoryResult,
    WorkflowListResult,
    WorkflowRegisterResult,
    WorkflowRunResult,
    WorkflowStatusResult,
)


# === Fixtures ===


@pytest.fixture
def mock_context() -> ExecutionContext:
    """Create mock mode context."""
    return ExecutionContext(execution_mode=ExecutionMode.MOCK)


@pytest.fixture
def server_context() -> ExecutionContext:
    """Create server mode context."""
    return ExecutionContext(
        execution_mode=ExecutionMode.SERVER,
        server_url="http://localhost:8080",
    )


@pytest.fixture
def mock_api(mock_context: ExecutionContext) -> WorkflowAPI:
    """Create WorkflowAPI in mock mode."""
    return WorkflowAPI(context=mock_context)


# === Test Classes ===


class TestWorkflowAPIInit:
    """Tests for WorkflowAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = WorkflowAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = WorkflowAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_server_context(self) -> None:
        """Test initialization with server context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = WorkflowAPI(context=ctx)

        assert api.context.execution_mode == ExecutionMode.SERVER
        assert api.context.server_url == "http://localhost:8080"

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(
            server_url="https://test.com", execution_mode=ExecutionMode.MOCK
        )
        api = WorkflowAPI(context=ctx)

        result = repr(api)

        assert "WorkflowAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = WorkflowAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _client should be None before any operation
        assert api._client is None


class TestWorkflowAPIGet:
    """Tests for WorkflowAPI.get() method."""

    def test_get_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test get returns WorkflowInfo in mock mode."""
        result = mock_api.get("iceberg.analytics.daily_clicks")

        assert result is not None
        assert isinstance(result, WorkflowInfo)
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert result.source_type == SourceType.MANUAL
        assert result.status == WorkflowStatus.ACTIVE
        assert result.cron == "0 9 * * *"
        assert result.timezone == "UTC"

    def test_get_returns_workflow_info_properties(self, mock_api: WorkflowAPI) -> None:
        """Test that returned WorkflowInfo has helper properties."""
        result = mock_api.get("my_dataset")

        assert result is not None
        assert result.is_active is True
        assert result.is_paused is False
        assert result.is_overridden is False


class TestWorkflowAPIRegister:
    """Tests for WorkflowAPI.register() method."""

    def test_register_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test register returns success result in mock mode."""
        result = mock_api.register(
            "iceberg.analytics.daily_clicks",
            cron="0 9 * * *",
        )

        assert isinstance(result, WorkflowRegisterResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert result.source_type == SourceType.MANUAL
        assert result.workflow_info is not None
        assert result.workflow_info.cron == "0 9 * * *"

    def test_register_with_all_options(self, mock_api: WorkflowAPI) -> None:
        """Test register with all options specified."""
        result = mock_api.register(
            "iceberg.analytics.daily_clicks",
            cron="0 10 * * *",
            timezone="Asia/Seoul",
            enabled=True,
            retry_max_attempts=3,
            retry_delay_seconds=600,
            force=True,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.workflow_info is not None
        assert result.workflow_info.cron == "0 10 * * *"
        assert result.workflow_info.timezone == "Asia/Seoul"

    def test_register_disabled(self, mock_api: WorkflowAPI) -> None:
        """Test register with enabled=False."""
        result = mock_api.register(
            "iceberg.analytics.daily_clicks",
            cron="0 9 * * *",
            enabled=False,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.workflow_info is not None
        assert result.workflow_info.status == WorkflowStatus.PAUSED

    def test_register_result_is_frozen(self, mock_api: WorkflowAPI) -> None:
        """Test that WorkflowRegisterResult is immutable."""
        result = mock_api.register(
            "iceberg.analytics.daily_clicks",
            cron="0 9 * * *",
        )

        with pytest.raises(Exception):  # Pydantic frozen model raises ValidationError
            result.status = ResultStatus.FAILURE  # type: ignore[misc]


class TestWorkflowAPIUnregister:
    """Tests for WorkflowAPI.unregister() method."""

    def test_unregister_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test unregister returns success in mock mode."""
        result = mock_api.unregister("iceberg.analytics.daily_clicks")

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert "unregistered" in (result.message or "").lower()


class TestWorkflowAPIRun:
    """Tests for WorkflowAPI.run() method."""

    def test_run_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test run returns success with run_id in mock mode."""
        result = mock_api.run("iceberg.analytics.daily_clicks")

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert result.run_id is not None
        assert "mock_" in result.run_id
        assert result.run_status == RunStatus.PENDING
        assert result.dry_run is False

    def test_run_with_parameters(self, mock_api: WorkflowAPI) -> None:
        """Test run with execution parameters."""
        result = mock_api.run(
            "iceberg.analytics.daily_clicks",
            parameters={"execution_date": "2025-01-01"},
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.run_id is not None

    def test_run_dry_run(self, mock_api: WorkflowAPI) -> None:
        """Test run with dry_run=True validates only."""
        result = mock_api.run(
            "iceberg.analytics.daily_clicks",
            dry_run=True,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.dry_run is True
        assert result.run_status is None  # No actual run created
        assert "dry run" in (result.message or "").lower()

    def test_run_result_is_frozen(self, mock_api: WorkflowAPI) -> None:
        """Test that WorkflowRunResult is immutable."""
        result = mock_api.run("iceberg.analytics.daily_clicks")

        with pytest.raises(Exception):
            result.run_id = "new_id"  # type: ignore[misc]


class TestWorkflowAPIBackfill:
    """Tests for WorkflowAPI.backfill() method."""

    def test_backfill_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test backfill returns success in mock mode."""
        result = mock_api.backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2025-01-01",
            end_date="2025-01-05",
        )

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.run_id is not None
        assert "backfill" in result.run_id
        assert "2025-01-01" in result.run_id
        assert "2025-01-05" in result.run_id

    def test_backfill_with_parameters(self, mock_api: WorkflowAPI) -> None:
        """Test backfill with additional parameters."""
        result = mock_api.backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2025-01-01",
            end_date="2025-01-05",
            parameters={"env": "prod"},
        )

        assert result.status == ResultStatus.SUCCESS

    def test_backfill_dry_run(self, mock_api: WorkflowAPI) -> None:
        """Test backfill with dry_run=True."""
        result = mock_api.backfill(
            "iceberg.analytics.daily_clicks",
            start_date="2025-01-01",
            end_date="2025-01-05",
            dry_run=True,
        )

        assert result.status == ResultStatus.SUCCESS
        assert result.dry_run is True
        assert result.run_status is None


class TestWorkflowAPIStop:
    """Tests for WorkflowAPI.stop() method."""

    def test_stop_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test stop returns success in mock mode."""
        result = mock_api.stop("run_123")

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.run_id == "run_123"
        assert result.run_status == RunStatus.KILLED
        assert "stopped" in (result.message or "").lower()


class TestWorkflowAPIGetStatus:
    """Tests for WorkflowAPI.get_status() method."""

    def test_get_status_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test get_status returns WorkflowStatusResult in mock mode."""
        result = mock_api.get_status("run_123")

        assert isinstance(result, WorkflowStatusResult)
        assert result.run_id == "run_123"
        assert result.dataset_name == "mock_dataset"
        assert result.source_type == SourceType.MANUAL
        assert result.run_status == RunStatus.COMPLETED
        assert result.run_type == "adhoc"

    def test_status_result_is_running_property(self, mock_api: WorkflowAPI) -> None:
        """Test WorkflowStatusResult.is_running property."""
        result = mock_api.get_status("run_123")

        # Mock returns COMPLETED, which is not running
        assert result.is_running is False

    def test_status_result_is_terminal_property(self, mock_api: WorkflowAPI) -> None:
        """Test WorkflowStatusResult.is_terminal property."""
        result = mock_api.get_status("run_123")

        # Mock returns COMPLETED, which is terminal
        assert result.is_terminal is True

    def test_status_result_duration_seconds(self, mock_api: WorkflowAPI) -> None:
        """Test WorkflowStatusResult.duration_seconds property."""
        result = mock_api.get_status("run_123")

        # Mock sets both started_at and finished_at
        if result.started_at and result.finished_at:
            assert result.duration_seconds is not None
            assert isinstance(result.duration_seconds, float)

    def test_status_result_is_frozen(self, mock_api: WorkflowAPI) -> None:
        """Test that WorkflowStatusResult is immutable."""
        result = mock_api.get_status("run_123")

        with pytest.raises(Exception):
            result.run_status = RunStatus.FAILED  # type: ignore[misc]


class TestWorkflowAPIListWorkflows:
    """Tests for WorkflowAPI.list_workflows() method."""

    def test_list_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test list_workflows returns WorkflowListResult in mock mode."""
        result = mock_api.list_workflows()

        assert isinstance(result, WorkflowListResult)
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.workflows, list)
        assert len(result.workflows) == 2  # Mock returns 2 workflows
        assert result.total_count == 2

    def test_list_workflows_items_are_workflow_info(
        self, mock_api: WorkflowAPI
    ) -> None:
        """Test that list returns WorkflowInfo items."""
        result = mock_api.list_workflows()

        for workflow in result.workflows:
            assert isinstance(workflow, WorkflowInfo)
            assert workflow.dataset_name is not None
            assert workflow.source_type in [SourceType.MANUAL, SourceType.CODE]

    def test_list_with_source_filter(self, mock_api: WorkflowAPI) -> None:
        """Test list_workflows with source_type filter."""
        result = mock_api.list_workflows(source_type=SourceType.MANUAL)

        assert isinstance(result, WorkflowListResult)
        # Filter should be applied
        for workflow in result.workflows:
            assert workflow.source_type == SourceType.MANUAL

    def test_list_with_status_filter(self, mock_api: WorkflowAPI) -> None:
        """Test list_workflows with status filter."""
        result = mock_api.list_workflows(status=WorkflowStatus.ACTIVE)

        assert isinstance(result, WorkflowListResult)
        for workflow in result.workflows:
            assert workflow.status == WorkflowStatus.ACTIVE

    def test_list_with_limit(self, mock_api: WorkflowAPI) -> None:
        """Test list_workflows with limit."""
        result = mock_api.list_workflows(limit=1)

        assert isinstance(result, WorkflowListResult)
        assert len(result.workflows) <= 1

    def test_list_result_is_frozen(self, mock_api: WorkflowAPI) -> None:
        """Test that WorkflowListResult is immutable."""
        result = mock_api.list_workflows()

        with pytest.raises(Exception):
            result.total_count = 100  # type: ignore[misc]


class TestWorkflowAPIHistory:
    """Tests for WorkflowAPI.history() method."""

    def test_history_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test history returns WorkflowHistoryResult in mock mode."""
        result = mock_api.history()

        assert isinstance(result, WorkflowHistoryResult)
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.runs, list)

    def test_history_with_dataset_filter(self, mock_api: WorkflowAPI) -> None:
        """Test history with dataset_name filter."""
        result = mock_api.history(dataset_name="iceberg.analytics.daily_clicks")

        assert isinstance(result, WorkflowHistoryResult)
        # Mock uses the provided dataset name
        if result.runs:
            assert result.runs[0].dataset_name == "iceberg.analytics.daily_clicks"

    def test_history_with_source_filter(self, mock_api: WorkflowAPI) -> None:
        """Test history with source_type filter."""
        result = mock_api.history(source_type=SourceType.MANUAL)

        assert isinstance(result, WorkflowHistoryResult)
        assert isinstance(result.runs, list)

    def test_history_with_run_status_filter(self, mock_api: WorkflowAPI) -> None:
        """Test history with run_status filter."""
        result = mock_api.history(run_status=RunStatus.COMPLETED)

        assert isinstance(result, WorkflowHistoryResult)
        assert isinstance(result.runs, list)

    def test_history_with_limit(self, mock_api: WorkflowAPI) -> None:
        """Test history with limit."""
        result = mock_api.history(limit=5)

        assert isinstance(result, WorkflowHistoryResult)
        assert len(result.runs) <= 5

    def test_history_includes_dataset_info(self, mock_api: WorkflowAPI) -> None:
        """Test history includes dataset_info when requested."""
        result = mock_api.history(
            dataset_name="iceberg.analytics.daily_clicks",
            include_dataset_info=True,
        )

        assert isinstance(result, WorkflowHistoryResult)
        assert result.dataset_info is not None
        assert isinstance(result.dataset_info, dict)

    def test_history_runs_are_workflow_run(self, mock_api: WorkflowAPI) -> None:
        """Test that history runs are WorkflowRun objects."""
        result = mock_api.history()

        for run in result.runs:
            assert isinstance(run, WorkflowRun)
            assert run.run_id is not None
            assert run.dataset_name is not None

    def test_history_result_is_frozen(self, mock_api: WorkflowAPI) -> None:
        """Test that WorkflowHistoryResult is immutable."""
        result = mock_api.history()

        with pytest.raises(Exception):
            result.total_count = 100  # type: ignore[misc]


class TestWorkflowAPIPauseUnpause:
    """Tests for WorkflowAPI.pause() and unpause() methods."""

    def test_pause_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test pause returns success in mock mode."""
        result = mock_api.pause("iceberg.analytics.daily_clicks")

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert "paused" in (result.message or "").lower()

    def test_unpause_mock_mode(self, mock_api: WorkflowAPI) -> None:
        """Test unpause returns success in mock mode."""
        result = mock_api.unpause("iceberg.analytics.daily_clicks")

        assert isinstance(result, WorkflowRunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dataset_name == "iceberg.analytics.daily_clicks"
        assert "resumed" in (result.message or "").lower()


class TestWorkflowAPIServerMode:
    """Tests for WorkflowAPI in server mode."""

    def test_requires_server_url(self) -> None:
        """Test that server mode requires server_url."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER, server_url=None)
        api = WorkflowAPI(context=ctx)

        with pytest.raises(ConfigurationError) as exc_info:
            api._get_client()

        assert "server_url required" in exc_info.value.message

    def test_mock_mode_does_not_require_server_url(self) -> None:
        """Test that mock mode doesn't require server_url."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK, server_url=None)
        api = WorkflowAPI(context=ctx)

        # Should not raise
        client = api._get_client()
        assert client is not None
        assert client.mock_mode is True

    def test_server_mode_creates_client_with_url(self) -> None:
        """Test that server mode creates client with provided URL."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
            api_token="test-token",
        )
        api = WorkflowAPI(context=ctx)

        client = api._get_client()

        assert client is not None
        assert client.config.url == "http://localhost:8080"


class TestWorkflowAPIDependencyInjection:
    """Tests for WorkflowAPI with injected client."""

    def test_uses_injected_client(self) -> None:
        """Test that injected client is used."""
        mock_client = MagicMock()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = WorkflowAPI(context=ctx, client=mock_client)

        # Access private method to get client
        client = api._get_client()

        assert client is mock_client


class TestWorkflowResultModels:
    """Tests for workflow result model properties and validation."""

    def test_workflow_run_result_frozen(self) -> None:
        """Test WorkflowRunResult is frozen (immutable)."""
        result = WorkflowRunResult(
            dataset_name="test",
            run_id="run_123",
            status=ResultStatus.SUCCESS,
        )

        with pytest.raises(Exception):
            result.dataset_name = "new_name"  # type: ignore[misc]

    def test_workflow_status_result_is_running(self) -> None:
        """Test WorkflowStatusResult.is_running property for various states."""
        # RUNNING state
        running = WorkflowStatusResult(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.RUNNING,
            run_type="adhoc",
        )
        assert running.is_running is True
        assert running.is_terminal is False

        # PENDING state
        pending = WorkflowStatusResult(
            run_id="run_2",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert pending.is_running is True
        assert pending.is_terminal is False

    def test_workflow_status_result_is_terminal(self) -> None:
        """Test WorkflowStatusResult.is_terminal property for terminal states."""
        # COMPLETED state
        completed = WorkflowStatusResult(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.COMPLETED,
            run_type="adhoc",
        )
        assert completed.is_terminal is True
        assert completed.is_running is False

        # FAILED state
        failed = WorkflowStatusResult(
            run_id="run_2",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.FAILED,
            run_type="adhoc",
        )
        assert failed.is_terminal is True

        # KILLED state
        killed = WorkflowStatusResult(
            run_id="run_3",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.KILLED,
            run_type="adhoc",
        )
        assert killed.is_terminal is True

    def test_workflow_status_result_duration_calculation(self) -> None:
        """Test WorkflowStatusResult.duration_seconds calculation."""
        from datetime import timedelta

        now = datetime.now()
        started = now - timedelta(seconds=120)

        # With both timestamps
        result = WorkflowStatusResult(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.COMPLETED,
            run_type="adhoc",
            started_at=started,
            finished_at=now,
        )
        assert result.duration_seconds is not None
        assert 119 <= result.duration_seconds <= 121  # Allow small variance

        # Without finished_at
        result_running = WorkflowStatusResult(
            run_id="run_2",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            run_status=RunStatus.RUNNING,
            run_type="adhoc",
            started_at=started,
        )
        assert result_running.duration_seconds is None

    def test_workflow_list_result_frozen(self) -> None:
        """Test WorkflowListResult is frozen."""
        result = WorkflowListResult(
            workflows=[],
            total_count=0,
            status=ResultStatus.SUCCESS,
        )

        with pytest.raises(Exception):
            result.workflows = []  # type: ignore[misc]

    def test_workflow_history_result_frozen(self) -> None:
        """Test WorkflowHistoryResult is frozen."""
        result = WorkflowHistoryResult(
            runs=[],
            total_count=0,
            status=ResultStatus.SUCCESS,
        )

        with pytest.raises(Exception):
            result.runs = []  # type: ignore[misc]


class TestWorkflowInfoModel:
    """Tests for WorkflowInfo model properties."""

    def test_workflow_info_is_active(self) -> None:
        """Test WorkflowInfo.is_active property."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=WorkflowStatus.ACTIVE,
            cron="0 9 * * *",
        )
        assert info.is_active is True
        assert info.is_paused is False
        assert info.is_overridden is False

    def test_workflow_info_is_paused(self) -> None:
        """Test WorkflowInfo.is_paused property."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=WorkflowStatus.PAUSED,
            cron="0 9 * * *",
        )
        assert info.is_active is False
        assert info.is_paused is True

    def test_workflow_info_is_overridden(self) -> None:
        """Test WorkflowInfo.is_overridden property."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=WorkflowStatus.OVERRIDDEN,
            cron="0 9 * * *",
            overridden_by="code",
        )
        assert info.is_active is False
        assert info.is_overridden is True


class TestWorkflowRunModel:
    """Tests for WorkflowRun model properties."""

    def test_workflow_run_is_running(self) -> None:
        """Test WorkflowRun.is_running property."""
        run = WorkflowRun(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=RunStatus.RUNNING,
            run_type="adhoc",
        )
        assert run.is_running is True
        assert run.is_pending is False
        assert run.is_finished is False

    def test_workflow_run_is_pending(self) -> None:
        """Test WorkflowRun.is_pending property."""
        run = WorkflowRun(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.is_pending is True
        assert run.is_running is False

    def test_workflow_run_is_finished(self) -> None:
        """Test WorkflowRun.is_finished property for terminal states."""
        for status in [RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.KILLED]:
            run = WorkflowRun(
                run_id="run_1",
                dataset_name="test",
                source_type=SourceType.MANUAL,
                status=status,
                run_type="adhoc",
            )
            assert run.is_finished is True

    def test_workflow_run_is_success(self) -> None:
        """Test WorkflowRun.is_success property."""
        success = WorkflowRun(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
        )
        assert success.is_success is True

        failed = WorkflowRun(
            run_id="run_2",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=RunStatus.FAILED,
            run_type="adhoc",
        )
        assert failed.is_success is False

    def test_workflow_run_duration_seconds(self) -> None:
        """Test WorkflowRun.duration_seconds calculation."""
        from datetime import timedelta

        now = datetime.now()
        started = now - timedelta(seconds=60)

        run = WorkflowRun(
            run_id="run_1",
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
            started_at=started,
            finished_at=now,
        )
        assert run.duration_seconds is not None
        assert 59 <= run.duration_seconds <= 61
