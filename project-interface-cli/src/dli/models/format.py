"""Format result models.

This module provides models for format operation results.

Example:
    >>> from dli.models.format import FormatResult, FormatStatus
    >>> result = api.format("my_dataset")
    >>> print(f"Status: {result.status}")
    >>> print(f"Changed files: {result.changed_count}")
"""

from __future__ import annotations

from enum import Enum

from pydantic import BaseModel, Field


class FormatStatus(str, Enum):
    """Format operation status.

    Indicates the overall result of a format operation.
    """

    SUCCESS = "success"  # No changes needed
    CHANGED = "changed"  # Files were changed (or would be changed in check mode)
    FAILED = "failed"  # Formatting failed


class FileFormatStatus(str, Enum):
    """Individual file format status.

    Indicates the status of formatting for a single file.
    """

    UNCHANGED = "unchanged"  # No changes needed
    CHANGED = "changed"  # File was changed (or would be changed in check mode)
    ERROR = "error"  # Formatting failed for this file


class LintViolation(BaseModel):
    """A single lint violation.

    Attributes:
        rule: The lint rule code (e.g., L010, L031).
        line: Line number where violation occurred.
        column: Column number where violation occurred.
        description: Human-readable description of the violation.
        severity: Violation severity (error, warning, info).
    """

    rule: str = Field(..., description="Lint rule code")
    line: int = Field(..., description="Line number")
    column: int = Field(default=1, description="Column number")
    description: str = Field(..., description="Violation description")
    severity: str = Field(default="warning", description="Severity level")


class FileFormatResult(BaseModel):
    """Format result for a single file.

    Contains information about the formatting result for one file,
    including status, changes made, and any lint violations found.

    Attributes:
        path: Relative path to the file.
        status: Format status for this file.
        original: Original file content (for diff).
        formatted: Formatted file content (for diff).
        changes: List of diff lines showing changes.
        lint_violations: List of lint violations found.
        error_message: Error message if formatting failed.
    """

    path: str = Field(..., description="Relative file path")
    status: FileFormatStatus = Field(..., description="Format status")
    original: str | None = Field(default=None, description="Original content")
    formatted: str | None = Field(default=None, description="Formatted content")
    changes: list[str] = Field(default_factory=list, description="Diff lines")
    lint_violations: list[LintViolation] = Field(
        default_factory=list, description="Lint violations"
    )
    error_message: str | None = Field(default=None, description="Error message")

    @property
    def has_changes(self) -> bool:
        """Check if file has changes."""
        return self.status == FileFormatStatus.CHANGED

    @property
    def has_errors(self) -> bool:
        """Check if file has errors."""
        return self.status == FileFormatStatus.ERROR

    @property
    def lint_violation_count(self) -> int:
        """Count of lint violations."""
        return len(self.lint_violations)


class FormatResult(BaseModel):
    """Overall format result.

    Contains the complete result of a format operation, including
    all files that were processed and their individual results.

    Attributes:
        name: Resource name that was formatted.
        resource_type: Type of resource (dataset or metric).
        status: Overall format status.
        files: List of individual file results.
        message: Optional status message.
        check_mode: Whether this was a check-only operation.
        lint_enabled: Whether lint was enabled.
    """

    name: str = Field(..., description="Resource name")
    resource_type: str = Field(..., description="Resource type (dataset/metric)")
    status: FormatStatus = Field(..., description="Overall format status")
    files: list[FileFormatResult] = Field(
        default_factory=list, description="File results"
    )
    message: str | None = Field(default=None, description="Status message")
    check_mode: bool = Field(default=False, description="Check-only mode")
    lint_enabled: bool = Field(default=False, description="Lint enabled")

    @property
    def changed_count(self) -> int:
        """Count of files that were changed."""
        return sum(1 for f in self.files if f.status == FileFormatStatus.CHANGED)

    @property
    def unchanged_count(self) -> int:
        """Count of files that were unchanged."""
        return sum(1 for f in self.files if f.status == FileFormatStatus.UNCHANGED)

    @property
    def error_count(self) -> int:
        """Count of files that had errors."""
        return sum(1 for f in self.files if f.status == FileFormatStatus.ERROR)

    @property
    def total_lint_violations(self) -> int:
        """Total count of lint violations across all files."""
        return sum(f.lint_violation_count for f in self.files)

    @property
    def has_changes(self) -> bool:
        """Check if any files have changes."""
        return self.changed_count > 0

    @property
    def has_errors(self) -> bool:
        """Check if any files have errors."""
        return self.error_count > 0


__all__ = [
    "FileFormatResult",
    "FileFormatStatus",
    "FormatResult",
    "FormatStatus",
    "LintViolation",
]
