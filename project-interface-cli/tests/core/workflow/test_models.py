"""Tests for Workflow Models.

This module tests the core workflow models including
SourceType, WorkflowStatus, RunStatus enums, and model classes
ScheduleConfig, WorkflowInfo, WorkflowRun, RetryConfig, NotificationConfig.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest
from pydantic import ValidationError

from dli.core.workflow.models import (
    NotificationConfig,
    NotificationTarget,
    RetryConfig,
    RunStatus,
    RunType,
    ScheduleConfig,
    SourceType,
    WorkflowInfo,
    WorkflowRun,
    WorkflowStatus,
)


# =============================================================================
# SourceType Enum Tests
# =============================================================================


class TestSourceTypeEnum:
    """Tests for SourceType enum."""

    def test_all_types_defined(self) -> None:
        """All expected source types should be defined."""
        expected_types = {"MANUAL", "CODE"}
        actual_types = {t.name for t in SourceType}
        assert actual_types == expected_types

    def test_type_values(self) -> None:
        """Source type values should be lowercase strings."""
        assert SourceType.MANUAL.value == "manual"
        assert SourceType.CODE.value == "code"

    def test_type_is_string_enum(self) -> None:
        """SourceType should be a string enum."""
        assert isinstance(SourceType.MANUAL.value, str)
        # String enum comparison
        assert SourceType.MANUAL == "manual"
        assert SourceType.CODE == "code"

    def test_type_from_string(self) -> None:
        """SourceType should be creatable from string."""
        assert SourceType("manual") == SourceType.MANUAL
        assert SourceType("code") == SourceType.CODE

    def test_invalid_type_raises_error(self) -> None:
        """Invalid source type should raise ValueError."""
        with pytest.raises(ValueError):
            SourceType("invalid")


# =============================================================================
# WorkflowStatus Enum Tests
# =============================================================================


class TestWorkflowStatusEnum:
    """Tests for WorkflowStatus enum."""

    def test_all_statuses_defined(self) -> None:
        """All expected workflow statuses should be defined."""
        expected_statuses = {"ACTIVE", "PAUSED", "OVERRIDDEN"}
        actual_statuses = {s.name for s in WorkflowStatus}
        assert actual_statuses == expected_statuses

    def test_status_values(self) -> None:
        """Workflow status values should be lowercase strings."""
        assert WorkflowStatus.ACTIVE.value == "active"
        assert WorkflowStatus.PAUSED.value == "paused"
        assert WorkflowStatus.OVERRIDDEN.value == "overridden"

    def test_status_is_string_enum(self) -> None:
        """WorkflowStatus should be a string enum."""
        assert isinstance(WorkflowStatus.ACTIVE.value, str)
        assert WorkflowStatus.ACTIVE == "active"
        assert WorkflowStatus.PAUSED == "paused"
        assert WorkflowStatus.OVERRIDDEN == "overridden"

    def test_status_from_string(self) -> None:
        """WorkflowStatus should be creatable from string."""
        assert WorkflowStatus("active") == WorkflowStatus.ACTIVE
        assert WorkflowStatus("paused") == WorkflowStatus.PAUSED
        assert WorkflowStatus("overridden") == WorkflowStatus.OVERRIDDEN


# =============================================================================
# RunStatus Enum Tests
# =============================================================================


class TestRunStatusEnum:
    """Tests for RunStatus enum."""

    def test_all_statuses_defined(self) -> None:
        """All expected run statuses should be defined."""
        expected_statuses = {"PENDING", "RUNNING", "COMPLETED", "FAILED", "KILLED"}
        actual_statuses = {s.name for s in RunStatus}
        assert actual_statuses == expected_statuses

    def test_status_values(self) -> None:
        """Run status values should be uppercase strings."""
        assert RunStatus.PENDING.value == "PENDING"
        assert RunStatus.RUNNING.value == "RUNNING"
        assert RunStatus.COMPLETED.value == "COMPLETED"
        assert RunStatus.FAILED.value == "FAILED"
        assert RunStatus.KILLED.value == "KILLED"

    def test_status_is_string_enum(self) -> None:
        """RunStatus should be a string enum."""
        assert isinstance(RunStatus.PENDING.value, str)
        # String enum comparison
        assert RunStatus.PENDING == "PENDING"
        assert RunStatus.COMPLETED == "COMPLETED"

    def test_status_from_string(self) -> None:
        """RunStatus should be creatable from string."""
        assert RunStatus("PENDING") == RunStatus.PENDING
        assert RunStatus("RUNNING") == RunStatus.RUNNING
        assert RunStatus("COMPLETED") == RunStatus.COMPLETED
        assert RunStatus("FAILED") == RunStatus.FAILED
        assert RunStatus("KILLED") == RunStatus.KILLED


# =============================================================================
# RetryConfig Tests
# =============================================================================


class TestRetryConfigCreation:
    """Tests for RetryConfig creation."""

    def test_default_values(self) -> None:
        """RetryConfig should have sensible defaults."""
        config = RetryConfig()
        assert config.max_attempts == 1
        assert config.delay_seconds == 300

    def test_custom_values(self) -> None:
        """RetryConfig should accept custom values."""
        config = RetryConfig(max_attempts=5, delay_seconds=600)
        assert config.max_attempts == 5
        assert config.delay_seconds == 600

    def test_min_max_attempts(self) -> None:
        """max_attempts should be at least 1."""
        config = RetryConfig(max_attempts=1)
        assert config.max_attempts == 1

    def test_zero_delay_allowed(self) -> None:
        """delay_seconds can be zero."""
        config = RetryConfig(delay_seconds=0)
        assert config.delay_seconds == 0


class TestRetryConfigValidation:
    """Tests for RetryConfig validation."""

    def test_max_attempts_must_be_at_least_one(self) -> None:
        """max_attempts must be >= 1."""
        with pytest.raises(ValidationError) as exc_info:
            RetryConfig(max_attempts=0)
        assert "max_attempts" in str(exc_info.value)

    def test_negative_max_attempts_rejected(self) -> None:
        """Negative max_attempts should be rejected."""
        with pytest.raises(ValidationError):
            RetryConfig(max_attempts=-1)

    def test_negative_delay_rejected(self) -> None:
        """Negative delay_seconds should be rejected."""
        with pytest.raises(ValidationError) as exc_info:
            RetryConfig(delay_seconds=-1)
        assert "delay_seconds" in str(exc_info.value)


# =============================================================================
# NotificationTarget Tests
# =============================================================================


class TestNotificationTargetCreation:
    """Tests for NotificationTarget creation."""

    def test_slack_target(self) -> None:
        """Create a Slack notification target."""
        target = NotificationTarget(type="slack", channel="#data-alerts")
        assert target.type == "slack"
        assert target.channel == "#data-alerts"

    def test_email_target(self) -> None:
        """Create an email notification target."""
        target = NotificationTarget(type="email", channel="team@example.com")
        assert target.type == "email"
        assert target.channel == "team@example.com"

    def test_webhook_target(self) -> None:
        """Create a webhook notification target."""
        target = NotificationTarget(type="webhook", channel="https://hooks.example.com/abc")
        assert target.type == "webhook"
        assert target.channel == "https://hooks.example.com/abc"


# =============================================================================
# NotificationConfig Tests
# =============================================================================


class TestNotificationConfigCreation:
    """Tests for NotificationConfig creation."""

    def test_default_empty_lists(self) -> None:
        """NotificationConfig should default to empty lists."""
        config = NotificationConfig()
        assert config.on_failure == []
        assert config.on_success == []
        assert config.on_source_change == []

    def test_single_failure_notification(self) -> None:
        """NotificationConfig with single failure notification."""
        config = NotificationConfig(
            on_failure=[NotificationTarget(type="slack", channel="#alerts")]
        )
        assert len(config.on_failure) == 1
        assert config.on_failure[0].type == "slack"

    def test_multiple_notifications(self) -> None:
        """NotificationConfig with multiple notifications."""
        config = NotificationConfig(
            on_failure=[
                NotificationTarget(type="slack", channel="#alerts"),
                NotificationTarget(type="email", channel="oncall@example.com"),
            ],
            on_success=[NotificationTarget(type="slack", channel="#success")],
        )
        assert len(config.on_failure) == 2
        assert len(config.on_success) == 1
        assert len(config.on_source_change) == 0


# =============================================================================
# ScheduleConfig Tests
# =============================================================================


class TestScheduleConfigCreation:
    """Tests for ScheduleConfig creation."""

    def test_minimal_config(self) -> None:
        """ScheduleConfig with cron only (required field)."""
        config = ScheduleConfig(cron="0 9 * * *")
        assert config.cron == "0 9 * * *"
        assert config.enabled is True
        assert config.timezone == "UTC"

    def test_default_retry_config(self) -> None:
        """ScheduleConfig should have default RetryConfig."""
        config = ScheduleConfig(cron="0 9 * * *")
        assert config.retry.max_attempts == 1
        assert config.retry.delay_seconds == 300

    def test_default_notification_config(self) -> None:
        """ScheduleConfig should have default NotificationConfig."""
        config = ScheduleConfig(cron="0 9 * * *")
        assert config.notifications.on_failure == []
        assert config.notifications.on_success == []

    def test_custom_timezone(self) -> None:
        """ScheduleConfig with custom timezone."""
        config = ScheduleConfig(cron="0 9 * * *", timezone="Asia/Seoul")
        assert config.timezone == "Asia/Seoul"

    def test_disabled_schedule(self) -> None:
        """ScheduleConfig with enabled=False."""
        config = ScheduleConfig(cron="0 9 * * *", enabled=False)
        assert config.enabled is False

    def test_custom_retry(self) -> None:
        """ScheduleConfig with custom retry configuration."""
        config = ScheduleConfig(
            cron="0 9 * * *",
            retry=RetryConfig(max_attempts=3, delay_seconds=600),
        )
        assert config.retry.max_attempts == 3
        assert config.retry.delay_seconds == 600

    def test_full_config(self) -> None:
        """ScheduleConfig with all options specified."""
        config = ScheduleConfig(
            enabled=True,
            cron="0 9 * * *",
            timezone="America/New_York",
            retry=RetryConfig(max_attempts=5, delay_seconds=120),
            notifications=NotificationConfig(
                on_failure=[NotificationTarget(type="slack", channel="#alerts")],
            ),
        )
        assert config.enabled is True
        assert config.cron == "0 9 * * *"
        assert config.timezone == "America/New_York"
        assert config.retry.max_attempts == 5
        assert len(config.notifications.on_failure) == 1


# =============================================================================
# WorkflowInfo Tests
# =============================================================================


class TestWorkflowInfoCreation:
    """Tests for WorkflowInfo creation."""

    def test_minimal_info(self) -> None:
        """WorkflowInfo with required fields only."""
        info = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron="0 9 * * *",
        )
        assert info.dataset_name == "iceberg.analytics.users"
        assert info.source_type == SourceType.CODE
        assert info.status == WorkflowStatus.ACTIVE
        assert info.cron == "0 9 * * *"
        assert info.timezone == "UTC"
        assert info.next_run is None
        assert info.overridden_by is None

    def test_with_next_run(self) -> None:
        """WorkflowInfo with next_run scheduled."""
        next_run = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        info = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron="0 9 * * *",
            next_run=next_run,
        )
        assert info.next_run == next_run

    def test_overridden_workflow(self) -> None:
        """WorkflowInfo for an overridden workflow."""
        info = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.MANUAL,
            status=WorkflowStatus.OVERRIDDEN,
            cron="0 9 * * *",
            overridden_by="code",
        )
        assert info.status == WorkflowStatus.OVERRIDDEN
        assert info.overridden_by == "code"


class TestWorkflowInfoConvenienceProperties:
    """Tests for WorkflowInfo convenience properties."""

    def test_is_active_when_active(self) -> None:
        """is_active should be True when status is ACTIVE."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron="0 * * * *",
        )
        assert info.is_active is True
        assert info.is_paused is False
        assert info.is_overridden is False

    def test_is_paused_when_paused(self) -> None:
        """is_paused should be True when status is PAUSED."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.CODE,
            status=WorkflowStatus.PAUSED,
            cron="0 * * * *",
        )
        assert info.is_active is False
        assert info.is_paused is True
        assert info.is_overridden is False

    def test_is_overridden_when_overridden(self) -> None:
        """is_overridden should be True when status is OVERRIDDEN."""
        info = WorkflowInfo(
            dataset_name="test",
            source_type=SourceType.MANUAL,
            status=WorkflowStatus.OVERRIDDEN,
            cron="0 * * * *",
            overridden_by="code",
        )
        assert info.is_active is False
        assert info.is_paused is False
        assert info.is_overridden is True


# =============================================================================
# WorkflowRun Tests
# =============================================================================


class TestWorkflowRunCreation:
    """Tests for WorkflowRun creation."""

    def test_minimal_run(self) -> None:
        """WorkflowRun with required fields only."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.run_id == "run-001"
        assert run.dataset_name == "iceberg.analytics.users"
        assert run.source_type == SourceType.CODE
        assert run.status == RunStatus.PENDING
        assert run.run_type == "adhoc"
        assert run.parameters == {}
        assert run.started_at is None
        assert run.finished_at is None

    def test_scheduled_run(self) -> None:
        """WorkflowRun for a scheduled execution."""
        run = WorkflowRun(
            run_id="run-002",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.RUNNING,
            run_type="scheduled",
            started_at=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
        )
        assert run.run_type == "scheduled"
        assert run.started_at is not None

    def test_backfill_run(self) -> None:
        """WorkflowRun for a backfill execution."""
        run = WorkflowRun(
            run_id="run-003",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="backfill",
            parameters={"start_date": "2024-01-01", "end_date": "2024-01-07"},
        )
        assert run.run_type == "backfill"
        assert run.parameters["start_date"] == "2024-01-01"

    def test_completed_run(self) -> None:
        """WorkflowRun that has completed."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        run = WorkflowRun(
            run_id="run-004",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="scheduled",
            started_at=started,
            finished_at=finished,
        )
        assert run.finished_at == finished

    def test_failed_run(self) -> None:
        """WorkflowRun that has failed."""
        run = WorkflowRun(
            run_id="run-005",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.FAILED,
            run_type="adhoc",
            started_at=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
            finished_at=datetime(2024, 1, 15, 9, 2, 0, tzinfo=UTC),
        )
        assert run.status == RunStatus.FAILED

    def test_killed_run(self) -> None:
        """WorkflowRun that was killed."""
        run = WorkflowRun(
            run_id="run-006",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.KILLED,
            run_type="scheduled",
        )
        assert run.status == RunStatus.KILLED


class TestWorkflowRunConvenienceProperties:
    """Tests for WorkflowRun convenience properties."""

    def test_is_running_when_running(self) -> None:
        """is_running should be True when status is RUNNING."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.RUNNING,
            run_type="adhoc",
        )
        assert run.is_running is True
        assert run.is_pending is False
        assert run.is_finished is False
        assert run.is_success is False

    def test_is_pending_when_pending(self) -> None:
        """is_pending should be True when status is PENDING."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.is_pending is True
        assert run.is_running is False
        assert run.is_finished is False

    def test_is_finished_when_completed(self) -> None:
        """is_finished should be True when status is COMPLETED."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
        )
        assert run.is_finished is True
        assert run.is_success is True
        assert run.is_running is False

    def test_is_finished_when_failed(self) -> None:
        """is_finished should be True when status is FAILED."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.FAILED,
            run_type="adhoc",
        )
        assert run.is_finished is True
        assert run.is_success is False

    def test_is_finished_when_killed(self) -> None:
        """is_finished should be True when status is KILLED."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.KILLED,
            run_type="adhoc",
        )
        assert run.is_finished is True
        assert run.is_success is False

    def test_is_not_finished_when_pending(self) -> None:
        """is_finished should be False when status is PENDING."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.is_finished is False

    def test_is_not_finished_when_running(self) -> None:
        """is_finished should be False when status is RUNNING."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.RUNNING,
            run_type="adhoc",
        )
        assert run.is_finished is False


class TestWorkflowRunDuration:
    """Tests for WorkflowRun duration_seconds property."""

    def test_duration_when_not_started(self) -> None:
        """duration_seconds should be None when not started."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.duration_seconds is None

    def test_duration_when_finished(self) -> None:
        """duration_seconds should calculate from started_at to finished_at."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
            started_at=started,
            finished_at=finished,
        )
        assert run.duration_seconds == 300.0  # 5 minutes

    def test_duration_when_running(self) -> None:
        """duration_seconds should calculate from started_at to now when running."""
        # Set started_at to 10 seconds ago
        started = datetime.now(UTC) - timedelta(seconds=10)
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.RUNNING,
            run_type="adhoc",
            started_at=started,
        )
        duration = run.duration_seconds
        assert duration is not None
        assert duration >= 10.0
        assert duration < 15.0  # Allow some margin

    def test_duration_precise_calculation(self) -> None:
        """duration_seconds should handle sub-second precision."""
        started = datetime(2024, 1, 15, 9, 0, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 0, 2, 500000, tzinfo=UTC)  # 2.5 seconds
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
            started_at=started,
            finished_at=finished,
        )
        assert run.duration_seconds == 2.5


# =============================================================================
# JSON Serialization Tests
# =============================================================================


class TestRetryConfigSerialization:
    """Tests for RetryConfig JSON serialization."""

    def test_to_json(self) -> None:
        """RetryConfig should serialize to JSON dict."""
        config = RetryConfig(max_attempts=3, delay_seconds=600)
        data = config.model_dump()
        assert data == {"max_attempts": 3, "delay_seconds": 600}

    def test_from_json(self) -> None:
        """RetryConfig should deserialize from JSON dict."""
        data = {"max_attempts": 3, "delay_seconds": 600}
        config = RetryConfig.model_validate(data)
        assert config.max_attempts == 3
        assert config.delay_seconds == 600

    def test_json_roundtrip(self) -> None:
        """RetryConfig should survive JSON roundtrip."""
        original = RetryConfig(max_attempts=5, delay_seconds=120)
        json_str = original.model_dump_json()
        restored = RetryConfig.model_validate_json(json_str)
        assert restored == original


class TestScheduleConfigSerialization:
    """Tests for ScheduleConfig JSON serialization."""

    def test_to_json(self) -> None:
        """ScheduleConfig should serialize to JSON dict."""
        config = ScheduleConfig(cron="0 9 * * *", timezone="Asia/Seoul")
        data = config.model_dump()
        assert data["cron"] == "0 9 * * *"
        assert data["timezone"] == "Asia/Seoul"
        assert data["enabled"] is True
        assert "retry" in data
        assert "notifications" in data

    def test_from_json(self) -> None:
        """ScheduleConfig should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "enabled": True,
            "cron": "0 9 * * *",
            "timezone": "UTC",
            "retry": {"max_attempts": 3, "delay_seconds": 300},
            "notifications": {"on_failure": [], "on_success": [], "on_source_change": []},
        }
        config = ScheduleConfig.model_validate(data)
        assert config.cron == "0 9 * * *"
        assert config.retry.max_attempts == 3

    def test_json_roundtrip(self) -> None:
        """ScheduleConfig should survive JSON roundtrip."""
        original = ScheduleConfig(
            cron="0 9 * * *",
            timezone="America/New_York",
            retry=RetryConfig(max_attempts=5, delay_seconds=120),
        )
        json_str = original.model_dump_json()
        restored = ScheduleConfig.model_validate_json(json_str)
        assert restored == original


class TestWorkflowInfoSerialization:
    """Tests for WorkflowInfo JSON serialization."""

    def test_to_json(self) -> None:
        """WorkflowInfo should serialize to JSON dict."""
        info = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron="0 9 * * *",
            timezone="UTC",
        )
        data = info.model_dump()
        assert data["dataset_name"] == "iceberg.analytics.users"
        assert data["source_type"] == "code"
        assert data["status"] == "active"
        assert data["cron"] == "0 9 * * *"

    def test_from_json(self) -> None:
        """WorkflowInfo should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "dataset_name": "iceberg.analytics.users",
            "source_type": "code",
            "status": "active",
            "cron": "0 9 * * *",
            "timezone": "UTC",
            "next_run": None,
            "overridden_by": None,
        }
        info = WorkflowInfo.model_validate(data)
        assert info.dataset_name == "iceberg.analytics.users"
        assert info.source_type == SourceType.CODE
        assert info.status == WorkflowStatus.ACTIVE

    def test_json_roundtrip(self) -> None:
        """WorkflowInfo should survive JSON roundtrip."""
        original = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron="0 9 * * *",
            timezone="Asia/Seoul",
            next_run=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
        )
        json_str = original.model_dump_json()
        restored = WorkflowInfo.model_validate_json(json_str)
        assert restored.dataset_name == original.dataset_name
        assert restored.source_type == original.source_type
        assert restored.status == original.status
        assert restored.cron == original.cron
        assert restored.next_run == original.next_run

    def test_from_json_with_next_run(self) -> None:
        """WorkflowInfo should deserialize next_run datetime correctly."""
        data: dict[str, Any] = {
            "dataset_name": "test",
            "source_type": "code",
            "status": "active",
            "cron": "0 * * * *",
            "timezone": "UTC",
            "next_run": "2024-01-15T09:00:00Z",
            "overridden_by": None,
        }
        info = WorkflowInfo.model_validate(data)
        assert info.next_run is not None
        assert info.next_run.year == 2024
        assert info.next_run.month == 1
        assert info.next_run.day == 15


class TestWorkflowRunSerialization:
    """Tests for WorkflowRun JSON serialization."""

    def test_to_json(self) -> None:
        """WorkflowRun should serialize to JSON dict."""
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
            parameters={"date": "2024-01-15"},
        )
        data = run.model_dump()
        assert data["run_id"] == "run-001"
        assert data["dataset_name"] == "iceberg.analytics.users"
        assert data["source_type"] == "code"
        assert data["status"] == "COMPLETED"
        assert data["run_type"] == "adhoc"
        assert data["parameters"]["date"] == "2024-01-15"

    def test_from_json(self) -> None:
        """WorkflowRun should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "run_id": "run-001",
            "dataset_name": "iceberg.analytics.users",
            "source_type": "code",
            "status": "COMPLETED",
            "run_type": "scheduled",
            "parameters": {},
            "started_at": "2024-01-15T09:00:00Z",
            "finished_at": "2024-01-15T09:05:00Z",
        }
        run = WorkflowRun.model_validate(data)
        assert run.run_id == "run-001"
        assert run.status == RunStatus.COMPLETED
        assert run.started_at is not None
        assert run.finished_at is not None

    def test_json_roundtrip(self) -> None:
        """WorkflowRun should survive JSON roundtrip."""
        original = WorkflowRun(
            run_id="run-001",
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=RunStatus.COMPLETED,
            run_type="adhoc",
            parameters={"date": "2024-01-15"},
            started_at=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
            finished_at=datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC),
        )
        json_str = original.model_dump_json()
        restored = WorkflowRun.model_validate_json(json_str)
        assert restored.run_id == original.run_id
        assert restored.status == original.status
        assert restored.parameters == original.parameters
        assert restored.started_at == original.started_at
        assert restored.finished_at == original.finished_at


# =============================================================================
# Integration Tests
# =============================================================================


class TestModelIntegration:
    """Integration tests for workflow model interactions."""

    def test_schedule_config_in_workflow_context(self) -> None:
        """ScheduleConfig can be used alongside WorkflowInfo."""
        schedule = ScheduleConfig(
            cron="0 9 * * *",
            timezone="Asia/Seoul",
            retry=RetryConfig(max_attempts=3, delay_seconds=600),
            notifications=NotificationConfig(
                on_failure=[NotificationTarget(type="slack", channel="#alerts")]
            ),
        )

        info = WorkflowInfo(
            dataset_name="iceberg.analytics.users",
            source_type=SourceType.CODE,
            status=WorkflowStatus.ACTIVE,
            cron=schedule.cron,
            timezone=schedule.timezone,
        )

        # Both models should coexist properly
        assert info.cron == schedule.cron
        assert info.timezone == schedule.timezone

    def test_workflow_run_lifecycle(self) -> None:
        """Test WorkflowRun through its lifecycle states."""
        # Create pending run
        run = WorkflowRun(
            run_id="run-001",
            dataset_name="test",
            source_type=SourceType.CODE,
            status=RunStatus.PENDING,
            run_type="adhoc",
        )
        assert run.is_pending is True
        assert run.duration_seconds is None

        # Simulate transition to running
        started = datetime.now(UTC)
        running_run = WorkflowRun(
            run_id=run.run_id,
            dataset_name=run.dataset_name,
            source_type=run.source_type,
            status=RunStatus.RUNNING,
            run_type=run.run_type,
            started_at=started,
        )
        assert running_run.is_running is True
        assert running_run.duration_seconds is not None

        # Simulate completion
        finished = started + timedelta(minutes=5)
        completed_run = WorkflowRun(
            run_id=run.run_id,
            dataset_name=run.dataset_name,
            source_type=run.source_type,
            status=RunStatus.COMPLETED,
            run_type=run.run_type,
            started_at=started,
            finished_at=finished,
        )
        assert completed_run.is_finished is True
        assert completed_run.is_success is True
        assert completed_run.duration_seconds == 300.0

    def test_all_run_types_are_valid(self) -> None:
        """All RunType values should be valid for WorkflowRun."""
        run_types: list[RunType] = ["adhoc", "scheduled", "backfill"]
        for run_type in run_types:
            run = WorkflowRun(
                run_id=f"run-{run_type}",
                dataset_name="test",
                source_type=SourceType.CODE,
                status=RunStatus.PENDING,
                run_type=run_type,
            )
            assert run.run_type == run_type

    def test_all_source_and_status_combinations(self) -> None:
        """All SourceType and WorkflowStatus combinations should be valid."""
        for source_type in SourceType:
            for status in WorkflowStatus:
                info = WorkflowInfo(
                    dataset_name="test",
                    source_type=source_type,
                    status=status,
                    cron="0 * * * *",
                )
                assert info.source_type == source_type
                assert info.status == status
