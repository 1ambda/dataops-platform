"""Debug data models for diagnostic results.

This module provides models for representing diagnostic check results:
- CheckStatus: Enum for check status (PASS, FAIL, WARN, SKIP)
- CheckCategory: Enum for check categories
- CheckResult: Individual check result
- DebugResult: Complete diagnostic result

Example:
    >>> from dli.core.debug.models import CheckResult, CheckStatus, CheckCategory
    >>> result = CheckResult(
    ...     name="Python version",
    ...     category=CheckCategory.SYSTEM,
    ...     status=CheckStatus.PASS,
    ...     message="Python 3.12.1",
    ... )
"""

from __future__ import annotations

from datetime import UTC, datetime
from enum import Enum
from functools import cached_property
from typing import Any

from pydantic import BaseModel, ConfigDict, Field


class CheckStatus(str, Enum):
    """Status of a diagnostic check.

    Attributes:
        PASS: Check passed successfully.
        FAIL: Check failed.
        WARN: Check passed with warnings.
        SKIP: Check was skipped.
    """

    PASS = "pass"
    FAIL = "fail"
    WARN = "warn"
    SKIP = "skip"


class CheckCategory(str, Enum):
    """Category of diagnostic check.

    Attributes:
        SYSTEM: System environment checks (Python, OS, etc.)
        CONFIG: Configuration file checks.
        SERVER: Basecamp Server connectivity checks.
        AUTH: Authentication and credential checks.
        DATABASE: Database connectivity checks.
        NETWORK: Network connectivity checks.
    """

    SYSTEM = "system"
    CONFIG = "config"
    SERVER = "server"
    AUTH = "auth"
    DATABASE = "database"
    NETWORK = "network"


class CheckResult(BaseModel):
    """Result of a single diagnostic check.

    Attributes:
        name: Check name.
        category: Check category.
        status: Check status.
        message: Status message.
        details: Additional details as key-value pairs.
        error: Error message if check failed.
        remediation: Suggested fix if check failed.
        duration_ms: Check duration in milliseconds.

    Example:
        >>> result = CheckResult(
        ...     name="Python version",
        ...     category=CheckCategory.SYSTEM,
        ...     status=CheckStatus.PASS,
        ...     message="Python 3.12.1",
        ...     details={"version": "3.12.1"},
        ... )
    """

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., description="Check name")
    category: CheckCategory = Field(..., description="Check category")
    status: CheckStatus = Field(..., description="Check status")
    message: str = Field(..., description="Status message")
    details: dict[str, Any] | None = Field(
        default=None, description="Additional details"
    )
    error: str | None = Field(default=None, description="Error message if failed")
    remediation: str | None = Field(
        default=None, description="Fix suggestion if failed"
    )
    duration_ms: int = Field(default=0, description="Check duration in milliseconds")


class DebugResult(BaseModel):
    """Complete debug diagnostic result.

    Attributes:
        version: dli version.
        timestamp: When diagnostics were run.
        success: Whether all checks passed.
        checks: List of individual check results.

    Properties:
        passed_count: Number of checks that passed.
        failed_count: Number of checks that failed.
        warned_count: Number of checks with warnings.
        skipped_count: Number of checks that were skipped.
        total_count: Total number of checks.
        by_category: Checks grouped by category.

    Example:
        >>> result = DebugResult(version="0.7.0", success=True, checks=[...])
        >>> print(f"Passed: {result.passed_count}/{result.total_count}")
    """

    model_config = ConfigDict(frozen=False)

    version: str = Field(..., description="dli version")
    timestamp: datetime = Field(
        default_factory=lambda: datetime.now(UTC),
        description="Diagnostic timestamp",
    )
    success: bool = Field(..., description="All checks passed")
    checks: list[CheckResult] = Field(default_factory=list, description="Check results")

    @property
    def passed_count(self) -> int:
        """Count of checks that passed."""
        return sum(1 for c in self.checks if c.status == CheckStatus.PASS)

    @property
    def failed_count(self) -> int:
        """Count of checks that failed."""
        return sum(1 for c in self.checks if c.status == CheckStatus.FAIL)

    @property
    def warned_count(self) -> int:
        """Count of checks with warnings."""
        return sum(1 for c in self.checks if c.status == CheckStatus.WARN)

    @property
    def skipped_count(self) -> int:
        """Count of checks that were skipped."""
        return sum(1 for c in self.checks if c.status == CheckStatus.SKIP)

    @property
    def total_count(self) -> int:
        """Total number of checks."""
        return len(self.checks)

    @cached_property
    def by_category(self) -> dict[CheckCategory, list[CheckResult]]:
        """Group checks by category.

        Returns:
            Dictionary mapping category to list of check results.
        """
        result: dict[CheckCategory, list[CheckResult]] = {}
        for check in self.checks:
            if check.category not in result:
                result[check.category] = []
            result[check.category].append(check)
        return result


__all__ = [
    "CheckCategory",
    "CheckResult",
    "CheckStatus",
    "DebugResult",
]
