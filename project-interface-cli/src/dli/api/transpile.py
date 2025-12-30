"""TranspileAPI - Library API for SQL transpilation.

This module provides the TranspileAPI class which wraps the TranspileEngine
for programmatic access to SQL transpilation operations.

Example:
    >>> from dli import TranspileAPI, ExecutionContext
    >>> ctx = ExecutionContext(server_url="https://basecamp.example.com")
    >>> api = TranspileAPI(context=ctx)
    >>> result = api.transpile("SELECT * FROM raw.events")
    >>> print(result.transpiled_sql)
"""

from __future__ import annotations

import time
from typing import Any

from dli.exceptions import TranspileError
from dli.models.common import (
    ExecutionContext,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    ValidationResult,
)


class TranspileAPI:
    """SQL Transpile Library API.

    Provides programmatic access to SQL transpilation including:
    - Table substitution rules
    - METRIC() function expansion
    - SQL dialect conversion
    - SQL validation and formatting

    Thread Safety:
        This class is NOT thread-safe. Create separate instances for
        concurrent operations.

    Example:
        >>> from dli import TranspileAPI, ExecutionContext
        >>> ctx = ExecutionContext(
        ...     server_url="http://basecamp:8080",
        ...     dialect="trino",
        ... )
        >>> api = TranspileAPI(context=ctx)
        >>> result = api.transpile(
        ...     "SELECT METRIC('revenue') FROM orders",
        ...     expand_metrics=True,
        ... )
        >>> print(result.transpiled_sql)
    """

    def __init__(self, context: ExecutionContext | None = None) -> None:
        """Initialize TranspileAPI.

        Args:
            context: Execution context with settings. If None, creates
                     default context from environment variables.
        """
        self.context = context or ExecutionContext()
        self._engine: Any | None = None

    def __repr__(self) -> str:
        """Return concise representation."""
        return f"TranspileAPI(context={self.context!r})"

    def _get_engine(self) -> Any:
        """Get or create TranspileEngine instance (lazy initialization).

        Returns:
            TranspileEngine instance.
        """
        if self._engine is None:
            from dli.core.transpile import TranspileConfig, TranspileEngine
            from dli.core.transpile.models import Dialect

            # Convert SQLDialect string to Dialect enum
            dialect_map = {
                "trino": Dialect.TRINO,
                "bigquery": Dialect.BIGQUERY,
            }
            dialect = dialect_map.get(self.context.dialect, Dialect.TRINO)

            config = TranspileConfig(
                dialect=dialect,
                strict_mode=False,  # API handles errors gracefully
            )

            self._engine = TranspileEngine(config=config)

        return self._engine

    def transpile(
        self,
        sql: str,
        *,
        source_dialect: str = "trino",
        target_dialect: str = "trino",
        apply_rules: bool = True,
        expand_metrics: bool = True,
        strict: bool = False,
    ) -> TranspileResult:
        """Perform SQL transpilation.

        Args:
            sql: Original SQL to transpile.
            source_dialect: Source SQL dialect.
            target_dialect: Target SQL dialect.
            apply_rules: Whether to apply table substitution rules.
            expand_metrics: Whether to expand METRIC() functions.
            strict: If True, raise on warnings.

        Returns:
            TranspileResult with transpiled SQL and metadata.

        Raises:
            TranspileError: In strict mode, if transpilation fails.
        """
        start_time = time.time()

        if self.context.mock_mode:
            duration_ms = int((time.time() - start_time) * 1000)
            return TranspileResult(
                original_sql=sql,
                transpiled_sql=sql,
                success=True,
                applied_rules=[],
                warnings=[],
                duration_ms=duration_ms,
            )

        try:
            engine = self._get_engine()

            # Perform transpilation
            result = engine.transpile(sql)

            duration_ms = int((time.time() - start_time) * 1000)

            # Convert engine result to API result
            applied_rules = [
                TranspileRule(
                    source_table=rule.source,
                    target_table=rule.target,
                    priority=0,
                    enabled=True,
                )
                for rule in result.applied_rules
            ]

            warnings = [
                TranspileWarning(
                    message=w.message,
                    line=getattr(w, "line", None),
                    column=getattr(w, "column", None),
                    rule=getattr(w, "rule_id", None),
                )
                for w in result.warnings
            ]

            if strict and warnings:
                raise TranspileError(
                    message=f"Transpilation has {len(warnings)} warnings",
                    sql=sql,
                )

            return TranspileResult(
                original_sql=sql,
                transpiled_sql=result.sql,
                success=result.success,
                applied_rules=applied_rules,
                warnings=warnings,
                duration_ms=duration_ms,
            )

        except TranspileError:
            raise
        except Exception as e:
            duration_ms = int((time.time() - start_time) * 1000)

            if strict:
                raise TranspileError(
                    message=f"Transpilation failed: {e}",
                    sql=sql,
                ) from e

            return TranspileResult(
                original_sql=sql,
                transpiled_sql=sql,
                success=False,
                applied_rules=[],
                warnings=[
                    TranspileWarning(message=str(e)),
                ],
                duration_ms=duration_ms,
            )

    def validate_sql(
        self,
        sql: str,
        *,
        dialect: str = "trino",
    ) -> ValidationResult:
        """Validate SQL syntax (parse only, no execution).

        Args:
            sql: SQL to validate.
            dialect: SQL dialect for validation.

        Returns:
            ValidationResult with validation status.
        """
        if self.context.mock_mode:
            # Basic mock validation
            if not sql.strip():
                return ValidationResult(
                    valid=False,
                    errors=["Empty SQL"],
                )
            return ValidationResult(valid=True)

        try:
            engine = self._get_engine()

            # Use engine's validate_sql method
            errors = engine.validate_sql(sql)

            return ValidationResult(
                valid=len(errors) == 0,
                errors=errors,
            )

        except Exception as e:
            return ValidationResult(
                valid=False,
                errors=[str(e)],
            )

    def get_rules(self) -> list[TranspileRule]:
        """Get transpile rules from server.

        Returns:
            List of TranspileRule objects.
        """
        if self.context.mock_mode:
            return [
                TranspileRule(
                    source_table="raw.events",
                    target_table="warehouse.events_v2",
                    priority=10,
                    enabled=True,
                ),
            ]

        try:
            engine = self._get_engine()

            # Fetch rules from client
            rules = engine.client.get_rules(project_id=None)

            return [
                TranspileRule(
                    source_table=rule.source,
                    target_table=rule.target,
                    priority=0,
                    enabled=True,
                )
                for rule in rules
            ]

        except Exception:
            return []

    def format_sql(
        self,
        sql: str,
        *,
        dialect: str = "trino",
        indent: int = 2,
    ) -> str:
        """Format SQL for readability.

        Args:
            sql: SQL to format.
            dialect: SQL dialect for formatting.
            indent: Indentation spaces.

        Returns:
            Formatted SQL string.
        """
        if self.context.mock_mode:
            return sql

        try:
            import sqlglot

            # Parse and format
            expression = sqlglot.parse_one(sql, dialect=dialect)
            return expression.sql(dialect=dialect, pretty=True)

        except Exception:
            # Return original if formatting fails
            return sql


__all__ = ["TranspileAPI"]
