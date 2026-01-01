"""Quality Models for the DLI Library API.

This module provides Pydantic models for Quality Spec YML parsing
and execution results.

Example:
    >>> from dli.models.quality import QualitySpec, QualityTargetType
    >>> spec = QualitySpec.from_yaml_file(Path("quality.iceberg.analytics.daily_clicks.yaml"))
    >>> print(spec.target.urn)
    dataset:iceberg.analytics.daily_clicks

Note:
    Model classes use "Dq" prefix where compatibility with existing
    dataclass models is needed. New models use "Quality" prefix.

References:
    - QUALITY_FEATURE.md: Quality Spec design and schema
    - dbt Data Tests: https://docs.getdbt.com/docs/build/data-tests
"""

from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from pathlib import Path
from typing import TYPE_CHECKING, Any

from pydantic import BaseModel, ConfigDict, Field
import yaml

# Import existing enums from core.quality.models for compatibility
from dli.core.quality.models import DqSeverity, DqStatus, DqTestType

if TYPE_CHECKING:
    from dli.core.quality.models import DqTestDefinition


def _utc_now() -> datetime:
    """Return current UTC time as a timezone-aware datetime."""
    return datetime.now(UTC)


class QualityTargetType(str, Enum):
    """Quality test target type.

    Defines what type of data asset the Quality Spec targets.
    """

    DATASET = "dataset"
    METRIC = "metric"


class QualityTarget(BaseModel):
    """Quality test target information.

    Attributes:
        type: Type of target (dataset or metric).
        name: Fully qualified name (catalog.schema.name).
    """

    model_config = ConfigDict(frozen=True)

    type: QualityTargetType = Field(..., description="Target type (dataset or metric)")
    name: str = Field(..., description="Fully qualified name (catalog.schema.name)")

    @property
    def urn(self) -> str:
        """Return URN for the target (e.g., 'dataset:iceberg.analytics.daily_clicks')."""
        return f"{self.type.value}:{self.name}"


class DqTestDefinitionSpec(BaseModel):
    """Quality test definition (Pydantic version).

    This is the Pydantic version of DqTestDefinition for YAML parsing.
    Use `to_test_definition()` to convert to the dataclass version
    used by the executor.

    Attributes:
        name: Unique test name.
        type: Test type (not_null, unique, singular, etc.).
        severity: Test severity (error or warn).
        description: Human-readable description.
        enabled: Whether the test is enabled.
        columns: Columns to test (for generic tests).
        column: Single column (alternative to columns).
        values: Accepted values list.
        values_query: SQL query to get accepted values.
        to: Target table for relationships test.
        to_column: Target column for relationships test.
        expression: SQL expression for expression test.
        min: Minimum value for range/row_count tests.
        max: Maximum value for range/row_count tests.
        sql: SQL for singular tests.
        file: SQL file path for singular tests.
        params: Additional parameters.
    """

    model_config = ConfigDict(extra="allow")

    name: str = Field(..., description="Test name")
    type: DqTestType = Field(..., description="Test type")
    severity: DqSeverity = Field(default=DqSeverity.ERROR, description="Test severity")
    description: str | None = Field(default=None, description="Test description")
    enabled: bool = Field(default=True, description="Whether test is enabled")

    # Generic test parameters
    columns: list[str] | None = Field(default=None, description="Columns to test")
    column: str | None = Field(default=None, description="Single column to test")
    values: list[str] | None = Field(default=None, description="Accepted values")
    values_query: str | None = Field(
        default=None, description="Query to get accepted values"
    )
    to: str | None = Field(default=None, description="Target table for relationships")
    to_column: str | None = Field(
        default=None, description="Target column for relationships"
    )
    expression: str | None = Field(default=None, description="SQL expression")
    min: int | None = Field(default=None, description="Minimum value")
    max: int | None = Field(default=None, description="Maximum value")

    # Singular test parameters
    sql: str | None = Field(default=None, description="Custom SQL for singular test")
    file: str | None = Field(default=None, description="SQL file path")
    params: dict[str, Any] = Field(default_factory=dict, description="Extra parameters")

    def to_test_definition(self, resource_name: str) -> DqTestDefinition:
        """Convert to DqTestDefinition dataclass for executor compatibility.

        Args:
            resource_name: Name of the resource being tested.

        Returns:
            DqTestDefinition instance.
        """
        # Import here to avoid circular import between models
        from dli.core.quality.models import DqTestDefinition  # noqa: PLC0415

        # Merge columns/column into single list
        columns = self.columns or ([self.column] if self.column else None)

        return DqTestDefinition(
            name=self.name,
            test_type=self.type,
            resource_name=resource_name,
            columns=columns,
            params=self.params,
            description=self.description,
            severity=self.severity,
            sql=self.sql,
            file=self.file,
            enabled=self.enabled,
        )


class QualitySchedule(BaseModel):
    """Airflow DAG scheduling information.

    Attributes:
        cron: Cron expression for scheduling.
        timezone: Timezone for the schedule.
        enabled: Whether scheduling is enabled.
    """

    model_config = ConfigDict(frozen=True)

    cron: str = Field(..., description="Cron expression")
    timezone: str = Field(default="UTC", description="Timezone")
    enabled: bool = Field(default=True, description="Whether scheduling is enabled")


class SlackNotification(BaseModel):
    """Slack notification settings.

    Attributes:
        channel: Slack channel to notify.
        on_failure: Notify on test failure.
        on_success: Notify on test success.
    """

    model_config = ConfigDict(frozen=True)

    channel: str = Field(..., description="Slack channel")
    on_failure: bool = Field(default=True, description="Notify on failure")
    on_success: bool = Field(default=False, description="Notify on success")


class EmailNotification(BaseModel):
    """Email notification settings.

    Attributes:
        recipients: List of email recipients.
        on_failure: Notify on test failure.
        on_success: Notify on test success.
    """

    model_config = ConfigDict(frozen=True)

    recipients: list[str] = Field(..., description="Email recipients")
    on_failure: bool = Field(default=True, description="Notify on failure")
    on_success: bool = Field(default=False, description="Notify on success")


class QualityNotifications(BaseModel):
    """Notification settings for Quality Spec.

    Attributes:
        slack: Slack notification settings.
        email: Email notification settings.
    """

    model_config = ConfigDict(frozen=True)

    slack: SlackNotification | None = Field(
        default=None, description="Slack notification"
    )
    email: EmailNotification | None = Field(
        default=None, description="Email notification"
    )


class QualityMetadata(BaseModel):
    """Quality Spec metadata.

    Attributes:
        owner: Owner email or identifier.
        team: Team name or Slack handle.
        description: Human-readable description.
        tags: List of tags for categorization.
    """

    model_config = ConfigDict(frozen=True)

    owner: str = Field(..., description="Owner identifier")
    team: str | None = Field(default=None, description="Team name")
    description: str | None = Field(default=None, description="Description")
    tags: list[str] = Field(default_factory=list, description="Tags")


class QualitySpec(BaseModel):
    """Quality Spec YML root model.

    Represents a complete Quality Spec file that defines quality tests
    for a Dataset or Metric.

    Example YAML:
        version: 1
        target:
          type: dataset
          name: iceberg.analytics.daily_clicks
        metadata:
          owner: analyst@example.com
          team: "@data-quality"
        tests:
          - name: pk_unique
            type: unique
            columns: [id]
            severity: error
    """

    model_config = ConfigDict(frozen=True)

    version: int = Field(default=1, description="Spec version")
    target: QualityTarget = Field(..., description="Target information")
    metadata: QualityMetadata = Field(..., description="Metadata")
    schedule: QualitySchedule | None = Field(
        default=None, description="Schedule settings"
    )
    notifications: QualityNotifications | None = Field(
        default=None, description="Notification settings"
    )
    tests: list[DqTestDefinitionSpec] = Field(
        default_factory=list, description="Test definitions"
    )

    @property
    def target_urn(self) -> str:
        """Return URN for the target."""
        return self.target.urn

    @classmethod
    def from_yaml(cls, yaml_content: str) -> QualitySpec:
        """Parse QualitySpec from YAML string.

        Args:
            yaml_content: YAML string content.

        Returns:
            QualitySpec instance.

        Raises:
            yaml.YAMLError: If YAML parsing fails.
            pydantic.ValidationError: If validation fails.
        """
        data = yaml.safe_load(yaml_content)
        return cls.model_validate(data)

    @classmethod
    def from_yaml_file(cls, path: Path) -> QualitySpec:
        """Load QualitySpec from a YAML file.

        Args:
            path: Path to the YAML file.

        Returns:
            QualitySpec instance.

        Raises:
            FileNotFoundError: If file does not exist.
            yaml.YAMLError: If YAML parsing fails.
            pydantic.ValidationError: If validation fails.
        """
        content = path.read_text(encoding="utf-8")
        return cls.from_yaml(content)

    def get_test_definitions(self) -> list[DqTestDefinition]:
        """Convert all tests to DqTestDefinition for executor.

        Returns:
            List of DqTestDefinition instances.
        """
        return [test.to_test_definition(self.target.name) for test in self.tests]

    def get_test(self, name: str) -> DqTestDefinitionSpec | None:
        """Get a specific test by name.

        Args:
            name: Test name.

        Returns:
            DqTestDefinitionSpec if found, None otherwise.
        """
        for test in self.tests:
            if test.name == name:
                return test
        return None


class DqQualityResult(BaseModel):
    """Quality Spec execution result.

    Aggregates results from all tests in a Quality Spec execution.

    Attributes:
        target_urn: URN of the tested target.
        execution_mode: Where tests were executed (local/server/mock).
        execution_id: Server execution ID (SERVER mode only).
        started_at: When execution started.
        finished_at: When execution finished.
        test_results: Individual test results.
    """

    model_config = ConfigDict(frozen=True)

    target_urn: str = Field(..., description="Target URN")
    execution_mode: str = Field(default="local", description="Execution mode")
    execution_id: str | None = Field(
        default=None, description="Server execution ID (SERVER mode)"
    )
    started_at: datetime = Field(
        default_factory=_utc_now, description="Execution start time"
    )
    finished_at: datetime = Field(
        default_factory=_utc_now, description="Execution end time"
    )
    test_results: list[dict[str, Any]] = Field(
        default_factory=list, description="Individual test results"
    )

    @property
    def status(self) -> DqStatus:
        """Return overall status (most severe status from all tests)."""
        if not self.test_results:
            return DqStatus.PASS

        priority = {
            DqStatus.ERROR: 0,
            DqStatus.FAIL: 1,
            DqStatus.WARN: 2,
            DqStatus.PASS: 3,
            DqStatus.SKIPPED: 4,
        }

        statuses = []
        for result in self.test_results:
            status_str = result.get("status", "pass")
            try:
                statuses.append(DqStatus(status_str))
            except ValueError:
                statuses.append(DqStatus.ERROR)

        return min(statuses, key=lambda s: priority.get(s, 99), default=DqStatus.PASS)

    @property
    def passed_count(self) -> int:
        """Return number of passed tests."""
        return sum(
            1
            for r in self.test_results
            if r.get("status") == DqStatus.PASS.value
            or r.get("status") == DqStatus.PASS
        )

    @property
    def failed_count(self) -> int:
        """Return number of failed tests (FAIL + ERROR)."""
        fail_statuses = {DqStatus.FAIL.value, DqStatus.ERROR.value, "fail", "error"}
        return sum(1 for r in self.test_results if r.get("status") in fail_statuses)

    @property
    def duration_ms(self) -> int:
        """Return execution duration in milliseconds."""
        delta = self.finished_at - self.started_at
        return int(delta.total_seconds() * 1000)


class QualityInfo(BaseModel):
    """Quality information from server.

    Used for list/get operations that query the server.

    Attributes:
        name: Quality name.
        target_urn: Target URN.
        target_type: Target type (dataset or metric).
        target_name: Target name.
        test_type: Primary test type (generic or singular).
        status: Current status (active or inactive).
        severity: Test severity.
        description: Description.
        schedule: Cron schedule if configured.
        last_run: Last run timestamp.
        last_status: Last run status.
    """

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., description="Quality name")
    target_urn: str = Field(..., description="Target URN")
    target_type: QualityTargetType = Field(..., description="Target type")
    target_name: str = Field(..., description="Target name")
    test_type: str = Field(default="generic", description="Test type")
    status: str = Field(default="active", description="Quality status")
    severity: DqSeverity = Field(default=DqSeverity.ERROR, description="Severity")
    description: str | None = Field(default=None, description="Description")
    schedule: str | None = Field(default=None, description="Cron schedule")
    last_run: datetime | None = Field(default=None, description="Last run time")
    last_status: DqStatus | None = Field(default=None, description="Last run status")


__all__ = [
    "DqQualityResult",
    "DqSeverity",
    "DqStatus",
    "DqTestDefinitionSpec",
    "DqTestType",
    "EmailNotification",
    "QualityInfo",
    "QualityMetadata",
    "QualityNotifications",
    "QualitySchedule",
    "QualitySpec",
    "QualityTarget",
    "QualityTargetType",
    "SlackNotification",
]
