"""SQL formatter using sqlfluff.

This module provides SQL formatting capabilities using sqlfluff,
with support for Jinja templates and multiple SQL dialects.
"""

from __future__ import annotations

from dataclasses import dataclass, field
import difflib
from pathlib import Path

from dli.exceptions import FormatDialectError, FormatSqlError

from .config import DEFAULT_DIALECT, SUPPORTED_DIALECTS, FormatConfig


@dataclass
class SqlFormatResult:
    """Result of SQL formatting.

    Attributes:
        original: Original SQL content.
        formatted: Formatted SQL content.
        changed: Whether the content was changed.
        violations: List of lint violations found.
        error: Error message if formatting failed.
    """

    original: str
    formatted: str
    changed: bool = False
    violations: list[dict[str, str]] = field(default_factory=list)
    error: str | None = None

    def get_diff(self) -> list[str]:
        """Get diff between original and formatted content.

        Returns:
            List of diff lines.
        """
        if not self.changed:
            return []

        original_lines = self.original.splitlines(keepends=True)
        formatted_lines = self.formatted.splitlines(keepends=True)

        diff = difflib.unified_diff(
            original_lines,
            formatted_lines,
            fromfile="original",
            tofile="formatted",
            lineterm="",
        )
        return list(diff)


class SqlFormatter:
    """SQL formatter using sqlfluff.

    Provides SQL formatting with:
    - Jinja template preservation
    - Multiple SQL dialect support
    - Optional lint rule checking

    Example:
        >>> formatter = SqlFormatter(dialect="bigquery")
        >>> result = formatter.format("select a,b from t where x=1")
        >>> print(result.formatted)
        SELECT
            a,
            b
        FROM t
        WHERE x = 1
    """

    def __init__(
        self,
        dialect: str = DEFAULT_DIALECT,
        config: FormatConfig | None = None,
        project_path: Path | None = None,
    ) -> None:
        """Initialize SQL formatter.

        Args:
            dialect: SQL dialect (bigquery, trino, snowflake, etc.).
            config: Optional format configuration.
            project_path: Optional project path for .sqlfluff discovery.

        Raises:
            FormatDialectError: If dialect is not supported.
        """
        # Validate dialect
        if dialect not in SUPPORTED_DIALECTS:
            raise FormatDialectError(
                message=f"Unsupported SQL dialect: {dialect}",
                dialect=dialect,
                supported=SUPPORTED_DIALECTS,
            )

        self.dialect = dialect
        self.config = config
        self.project_path = project_path
        self._linter = None  # Lazy initialization

    def _get_linter(self) -> Linter:  # type: ignore[name-defined]  # noqa: F821
        """Get or create the sqlfluff Linter instance.

        Returns:
            Configured Linter instance.

        Raises:
            FormatSqlError: If sqlfluff is not available.
        """
        if self._linter is not None:
            return self._linter

        try:
            from sqlfluff.core import Linter
        except ImportError as e:
            raise FormatSqlError(
                message="sqlfluff is not installed. Install with: uv pip install sqlfluff",
            ) from e

        # Configure linter with dialect and Jinja templater
        self._linter = Linter(
            dialect=self.dialect,
            config_path=str(self.project_path / ".sqlfluff")
            if self.project_path and (self.project_path / ".sqlfluff").exists()
            else None,
        )
        return self._linter

    def format(
        self,
        sql: str,
        *,
        file_path: str | None = None,
        lint: bool = False,
    ) -> SqlFormatResult:
        """Format SQL content.

        Args:
            sql: SQL content to format.
            file_path: Optional file path for error context.
            lint: Whether to also check lint rules.

        Returns:
            SqlFormatResult with formatted content and metadata.
        """
        if not sql.strip():
            return SqlFormatResult(original=sql, formatted=sql, changed=False)

        try:
            linter = self._get_linter()

            # Format the SQL
            fix_result = linter.fix_string(sql)
            formatted_sql = fix_result.output_string if hasattr(fix_result, "output_string") else str(fix_result)

            # Check if content changed
            changed = formatted_sql != sql

            # Get lint violations if requested
            violations: list[dict[str, str]] = []
            if lint:
                lint_result = linter.lint_string(formatted_sql)
                for violation in lint_result.violations if hasattr(lint_result, "violations") else []:
                    violations.append({
                        "rule": getattr(violation, "rule_code", "unknown"),
                        "line": str(getattr(violation, "line_no", 0)),
                        "column": str(getattr(violation, "line_pos", 0)),
                        "description": getattr(violation, "description", str(violation)),
                    })

            return SqlFormatResult(
                original=sql,
                formatted=formatted_sql,
                changed=changed,
                violations=violations,
            )

        except ImportError:
            # sqlfluff not installed, return original
            return SqlFormatResult(
                original=sql,
                formatted=sql,
                changed=False,
                error="sqlfluff not installed",
            )
        except Exception as e:
            # Return error result instead of raising
            return SqlFormatResult(
                original=sql,
                formatted=sql,
                changed=False,
                error=str(e),
            )

    def lint(
        self,
        sql: str,
        *,
        file_path: str | None = None,
    ) -> list[dict[str, str]]:
        """Lint SQL content without formatting.

        Args:
            sql: SQL content to lint.
            file_path: Optional file path for error context.

        Returns:
            List of lint violations.
        """
        if not sql.strip():
            return []

        try:
            linter = self._get_linter()
            result = linter.lint_string(sql)

            violations: list[dict[str, str]] = []
            for violation in result.violations if hasattr(result, "violations") else []:
                violations.append({
                    "rule": getattr(violation, "rule_code", "unknown"),
                    "line": str(getattr(violation, "line_no", 0)),
                    "column": str(getattr(violation, "line_pos", 0)),
                    "description": getattr(violation, "description", str(violation)),
                })

            return violations

        except Exception:
            return []

    def format_file(
        self,
        file_path: Path,
        *,
        check_only: bool = False,
        lint: bool = False,
    ) -> SqlFormatResult:
        """Format a SQL file.

        Args:
            file_path: Path to the SQL file.
            check_only: If True, don't modify the file.
            lint: Whether to also check lint rules.

        Returns:
            SqlFormatResult with formatting result.

        Raises:
            FormatSqlError: If file cannot be read.
        """
        try:
            sql = file_path.read_text(encoding="utf-8")
        except Exception as e:
            raise FormatSqlError(
                message=f"Cannot read SQL file: {e}",
                file_path=str(file_path),
            ) from e

        result = self.format(sql, file_path=str(file_path), lint=lint)

        # Write formatted content if not check_only and content changed
        if not check_only and result.changed and result.error is None:
            try:
                file_path.write_text(result.formatted, encoding="utf-8")
            except Exception as e:
                raise FormatSqlError(
                    message=f"Cannot write SQL file: {e}",
                    file_path=str(file_path),
                ) from e

        return result


__all__ = [
    "SqlFormatResult",
    "SqlFormatter",
]
