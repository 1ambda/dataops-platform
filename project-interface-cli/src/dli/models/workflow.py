"""Workflow Result Models for the DLI Library API.

This module contains result models for WorkflowAPI operations.
These models are separate from core/workflow/models.py which contains
domain models (WorkflowInfo, WorkflowRun, ScheduleConfig, etc.).

Models:
    WorkflowRegisterResult: Result of workflow registration
    WorkflowRunResult: Result of run/backfill/stop/pause/unpause operations
    WorkflowListResult: Result of list_workflows query
    WorkflowStatusResult: Detailed status of a workflow run
    WorkflowHistoryResult: Result of history query

References:
    - Feature Spec: features/WORKFLOW_FEATURE.md Section 4.2
    - Pattern: models/common.py result models
"""

from __future__ import annotations

from datetime import datetime
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from dli.core.workflow.models import (
    RunStatus,
    RunType,
    SourceType,
    WorkflowInfo,
    WorkflowRun,
)
from dli.models.common import ResultStatus

__all__ = [
    "WorkflowHistoryResult",
    "WorkflowListResult",
    "WorkflowRegisterResult",
    "WorkflowRunResult",
    "WorkflowStatusResult",
]


class WorkflowRegisterResult(BaseModel):
    """Result of workflow registration.

    Returned by WorkflowAPI.register() to indicate the outcome of
    registering a local Dataset as a MANUAL workflow.

    Attributes:
        dataset_name: Fully qualified name of the registered dataset
        status: Operation status (SUCCESS or FAILURE)
        source_type: Source type (always MANUAL for register)
        workflow_info: Registered workflow information if successful
        message: Human-readable status message
        warning: Warning message if CODE workflow exists (override scenario)
    """

    model_config = ConfigDict(frozen=True)

    dataset_name: str = Field(description="Registered dataset name")
    status: ResultStatus = Field(description="Operation status")
    source_type: SourceType = Field(
        description="Source type (always MANUAL for register)"
    )
    workflow_info: WorkflowInfo | None = Field(
        default=None, description="Registered workflow info"
    )
    message: str | None = Field(default=None, description="Status message")
    warning: str | None = Field(default=None, description="Warning if CODE exists")


class WorkflowRunResult(BaseModel):
    """Result of workflow execution operation.

    Returned by WorkflowAPI.run(), backfill(), stop(), pause(), and unpause()
    to indicate the outcome of the operation.

    Attributes:
        dataset_name: Fully qualified dataset name
        run_id: Run ID if applicable (None for pause/unpause)
        status: Operation status (SUCCESS or FAILURE)
        run_status: Current run status if applicable
        dry_run: Whether this was a dry run (validation only)
        message: Human-readable status message
    """

    model_config = ConfigDict(frozen=True)

    dataset_name: str = Field(description="Dataset name")
    run_id: str | None = Field(default=None, description="Run ID (if applicable)")
    status: ResultStatus = Field(description="Operation status")
    run_status: RunStatus | None = Field(default=None, description="Current run status")
    dry_run: bool = Field(default=False, description="Whether this was a dry run")
    message: str | None = Field(default=None, description="Status message")


class WorkflowListResult(BaseModel):
    """Result of workflow list query.

    Returned by WorkflowAPI.list_workflows() with filtered workflow information.

    Attributes:
        workflows: List of workflow information objects
        total_count: Total count (may differ from len(workflows) if paginated)
        status: Query status (SUCCESS or FAILURE)
    """

    model_config = ConfigDict(frozen=True)

    workflows: list[WorkflowInfo] = Field(
        default_factory=list, description="List of workflows"
    )
    total_count: int = Field(
        description="Total count (may differ from len(workflows) if paginated)"
    )
    status: ResultStatus = Field(description="Query status")


class WorkflowStatusResult(BaseModel):
    """Detailed status of a workflow run.

    Returned by WorkflowAPI.get_status() with comprehensive run information
    including timing, parameters, and error details.

    Attributes:
        run_id: Unique run identifier
        dataset_name: Fully qualified dataset name
        source_type: Source type (MANUAL or CODE)
        run_status: Current run execution status
        run_type: Type of run (adhoc, scheduled, or backfill)
        parameters: Execution parameters passed to the run
        started_at: Run start timestamp (None if pending)
        finished_at: Run completion timestamp (None if not finished)
        error_message: Error message if the run failed
    """

    model_config = ConfigDict(frozen=True)

    run_id: str = Field(description="Run ID")
    dataset_name: str = Field(description="Dataset name")
    source_type: SourceType = Field(description="Source type")
    run_status: RunStatus = Field(description="Current run status")
    run_type: RunType = Field(description="Run type")
    parameters: dict[str, Any] = Field(
        default_factory=dict, description="Execution parameters"
    )
    started_at: datetime | None = Field(default=None, description="Start time")
    finished_at: datetime | None = Field(default=None, description="Finish time")
    error_message: str | None = Field(
        default=None, description="Error message if failed"
    )

    @property
    def is_running(self) -> bool:
        """Check if workflow is currently running."""
        return self.run_status in (RunStatus.RUNNING, RunStatus.PENDING)

    @property
    def is_terminal(self) -> bool:
        """Check if workflow has reached terminal state."""
        return self.run_status in (
            RunStatus.COMPLETED,
            RunStatus.FAILED,
            RunStatus.KILLED,
        )

    @property
    def duration_seconds(self) -> float | None:
        """Calculate run duration in seconds.

        Returns:
            Duration in seconds, or None if run hasn't started or finished.
        """
        if self.started_at and self.finished_at:
            return (self.finished_at - self.started_at).total_seconds()
        return None


class WorkflowHistoryResult(BaseModel):
    """Result of workflow history query.

    Returned by WorkflowAPI.history() with execution history and optional
    dataset metadata.

    Attributes:
        runs: List of workflow run details
        total_count: Total count of runs matching the filter
        status: Query status (SUCCESS or FAILURE)
        dataset_info: Dataset metadata if include_dataset_info=True
    """

    model_config = ConfigDict(frozen=True)

    runs: list[WorkflowRun] = Field(default_factory=list, description="List of runs")
    total_count: int = Field(description="Total count")
    status: ResultStatus = Field(description="Query status")
    dataset_info: dict[str, Any] | None = Field(
        default=None,
        description="Dataset metadata (owner, team, description) if include_dataset_info=True",
    )
