"""Workflow Models for the DLI Core Engine.

This module contains the core models for workflow management, including
schedule configuration, workflow info, and run status tracking.

Models:
    SourceType: Enum for dataset registration source (manual vs code)
    WorkflowStatus: Enum for workflow activation status
    RunStatus: Enum for workflow run execution status
    RunType: Literal type for run type classification
    RetryConfig: Configuration for retry behavior
    NotificationTarget: Single notification target configuration
    NotificationConfig: Aggregated notification settings
    ScheduleConfig: Complete schedule configuration
    WorkflowInfo: Workflow status and schedule information
    WorkflowRun: Individual workflow run details

References:
    - Airflow REST API: https://airflow.apache.org/docs/apache-airflow/stable/stable-rest-api-ref.html
    - Feature Spec: features/WORKFLOW_FEATURE.md
"""

from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from typing import Any, Literal

from pydantic import BaseModel, Field


def _utc_now() -> datetime:
    """Return current UTC time as a timezone-aware datetime."""
    return datetime.now(UTC)


class SourceType(str, Enum):
    """Source type for dataset registration.

    Indicates how the dataset workflow was registered:
    - MANUAL: User registered via CLI/API
    - CODE: Registered via CI/CD pipeline from Git

    The source type determines what operations are allowed:
    - Manual datasets can be modified/deleted via CLI
    - Code datasets can only be paused/unpaused via CLI
    """

    MANUAL = "manual"
    CODE = "code"


class WorkflowStatus(str, Enum):
    """Workflow activation status.

    Status indicates the current state of the workflow schedule:
    - ACTIVE: Schedule is active and will run on cron
    - PAUSED: Schedule is paused (manually disabled)
    - OVERRIDDEN: Manual workflow overridden by Code version

    Note:
        OVERRIDDEN status only applies to Manual workflows when
        a Code version with the same dataset name exists.
    """

    ACTIVE = "active"
    PAUSED = "paused"
    OVERRIDDEN = "overridden"


class RunStatus(str, Enum):
    """Workflow run execution status.

    Status of an individual workflow run:
    - PENDING: Run is queued, waiting to start
    - RUNNING: Run is currently executing
    - COMPLETED: Run finished successfully
    - FAILED: Run finished with errors
    - KILLED: Run was forcefully terminated
    """

    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"
    KILLED = "KILLED"


# Type alias for run type classification
RunType = Literal["adhoc", "scheduled", "backfill"]


class RetryConfig(BaseModel):
    """Retry configuration for workflow execution.

    Attributes:
        max_attempts: Maximum number of retry attempts (default: 1 = no retry)
        delay_seconds: Delay between retry attempts in seconds (default: 300)
    """

    max_attempts: int = Field(default=1, ge=1, description="Maximum retry attempts")
    delay_seconds: int = Field(
        default=300, ge=0, description="Delay between retries in seconds"
    )


class NotificationTarget(BaseModel):
    """Single notification target configuration.

    Represents a notification destination for workflow events.

    Attributes:
        type: Notification type (e.g., 'slack', 'email', 'webhook')
        channel: Target channel or address (e.g., '#data-alerts', 'team@example.com')
    """

    type: str = Field(description="Notification type (slack, email, webhook)")
    channel: str = Field(description="Target channel or address")


class NotificationConfig(BaseModel):
    """Notification configuration for workflow events.

    Configures notifications for various workflow events.

    Attributes:
        on_failure: Notifications to send on workflow failure
        on_success: Notifications to send on workflow success
        on_source_change: Notifications when source type changes (Code/Manual override)
    """

    on_failure: list[NotificationTarget] = Field(
        default_factory=list, description="Notifications on failure"
    )
    on_success: list[NotificationTarget] = Field(
        default_factory=list, description="Notifications on success"
    )
    on_source_change: list[NotificationTarget] = Field(
        default_factory=list, description="Notifications on source type change"
    )


class ScheduleConfig(BaseModel):
    """Complete schedule configuration from Dataset Spec.

    Defines how and when a workflow should be executed.

    Attributes:
        enabled: Whether the schedule is enabled (default: True)
        cron: Cron expression for scheduling (5-field format)
        timezone: IANA timezone identifier (default: 'UTC')
        retry: Retry configuration for failed runs
        notifications: Notification settings for workflow events

    Example:
        ```yaml
        schedule:
          enabled: true
          cron: "0 9 * * *"
          timezone: "Asia/Seoul"
          retry:
            max_attempts: 3
            delay_seconds: 300
          notifications:
            on_failure:
              - type: slack
                channel: "#data-alerts"
        ```
    """

    enabled: bool = Field(default=True, description="Whether schedule is enabled")
    cron: str = Field(description="Cron expression (5-field format)")
    timezone: str = Field(default="UTC", description="IANA timezone identifier")
    retry: RetryConfig = Field(
        default_factory=RetryConfig, description="Retry configuration"
    )
    notifications: NotificationConfig = Field(
        default_factory=NotificationConfig, description="Notification settings"
    )


class WorkflowInfo(BaseModel):
    """Workflow status and schedule information.

    Represents the current state of a workflow as retrieved from
    the server. Used for listing and status display.

    Attributes:
        dataset_name: Fully qualified dataset name
        source_type: How the workflow was registered (manual/code)
        status: Current workflow status (active/paused/overridden)
        cron: Cron expression for the schedule
        timezone: IANA timezone for the schedule
        next_run: Next scheduled run time (None if paused/overridden)
        overridden_by: Source that overrode this workflow (if applicable)
    """

    dataset_name: str = Field(description="Fully qualified dataset name")
    source_type: SourceType = Field(description="Registration source type")
    status: WorkflowStatus = Field(description="Current workflow status")
    cron: str = Field(description="Cron expression")
    timezone: str = Field(default="UTC", description="IANA timezone")
    next_run: datetime | None = Field(
        default=None, description="Next scheduled run time"
    )
    overridden_by: str | None = Field(
        default=None, description="Source that overrode this workflow"
    )

    @property
    def is_active(self) -> bool:
        """Check if workflow is currently active."""
        return self.status == WorkflowStatus.ACTIVE

    @property
    def is_paused(self) -> bool:
        """Check if workflow is paused."""
        return self.status == WorkflowStatus.PAUSED

    @property
    def is_overridden(self) -> bool:
        """Check if workflow is overridden by another source."""
        return self.status == WorkflowStatus.OVERRIDDEN


class WorkflowRun(BaseModel):
    """Individual workflow run details.

    Represents a single execution of a workflow, whether triggered
    manually (adhoc), by schedule, or as part of a backfill.

    Attributes:
        run_id: Unique identifier for this run
        dataset_name: Fully qualified dataset name
        source_type: Registration source of the workflow
        status: Current run execution status
        run_type: Type of run (adhoc, scheduled, backfill)
        parameters: Parameters passed to the run
        started_at: Run start timestamp (None if pending)
        finished_at: Run completion timestamp (None if not finished)
    """

    run_id: str = Field(description="Unique run identifier")
    dataset_name: str = Field(description="Fully qualified dataset name")
    source_type: SourceType = Field(description="Registration source type")
    status: RunStatus = Field(description="Run execution status")
    run_type: RunType = Field(description="Type of run")
    parameters: dict[str, Any] = Field(
        default_factory=dict, description="Run parameters"
    )
    started_at: datetime | None = Field(default=None, description="Run start time")
    finished_at: datetime | None = Field(
        default=None, description="Run completion time"
    )

    @property
    def is_running(self) -> bool:
        """Check if run is currently executing."""
        return self.status == RunStatus.RUNNING

    @property
    def is_pending(self) -> bool:
        """Check if run is waiting to start."""
        return self.status == RunStatus.PENDING

    @property
    def is_finished(self) -> bool:
        """Check if run has completed (success or failure)."""
        return self.status in (
            RunStatus.COMPLETED,
            RunStatus.FAILED,
            RunStatus.KILLED,
        )

    @property
    def is_success(self) -> bool:
        """Check if run completed successfully."""
        return self.status == RunStatus.COMPLETED

    @property
    def duration_seconds(self) -> float | None:
        """Calculate run duration in seconds.

        Returns:
            Duration in seconds, or None if run hasn't started or finished.
        """
        if self.started_at is None:
            return None
        end_time = self.finished_at or _utc_now()
        return (end_time - self.started_at).total_seconds()


__all__ = [
    "NotificationConfig",
    "NotificationTarget",
    "RetryConfig",
    "RunStatus",
    "RunType",
    "ScheduleConfig",
    "SourceType",
    "WorkflowInfo",
    "WorkflowRun",
    "WorkflowStatus",
]
