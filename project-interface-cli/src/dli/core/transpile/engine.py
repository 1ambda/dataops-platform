"""
TranspileEngine - main orchestrator for SQL transpilation.

This module provides the TranspileEngine class that coordinates
all transpile operations: rule fetching, table substitution,
metric expansion, and warning detection.
"""

from __future__ import annotations

from datetime import UTC, datetime
import time
from typing import Any

from dli.core.transpile.client import MockTranspileClient, TranspileRuleClient
from dli.core.transpile.exceptions import (
    MetricNotFoundError,
    SqlParseError,
    TranspileError,
)
from dli.core.transpile.metrics import expand_metrics
from dli.core.transpile.models import (
    MetricDefinition,
    RuleType,
    TranspileConfig,
    TranspileMetadata,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    WarningType,
)
from dli.core.transpile.rules import apply_table_substitutions
from dli.core.transpile.warnings import detect_warnings

__all__ = [
    "TranspileEngine",
]


class TranspileEngine:
    """Main engine for SQL transpilation.

    Orchestrates the complete transpile workflow:
    1. Fetch rules from client
    2. Expand METRIC() functions
    3. Apply table substitutions
    4. Detect warnings
    5. Return comprehensive result

    Example:
        >>> from dli.core.transpile import TranspileEngine, TranspileConfig
        >>> engine = TranspileEngine(config=TranspileConfig())
        >>> result = engine.transpile("SELECT * FROM users")
        >>> print(result.sql)
        SELECT * FROM users_v2

    Attributes:
        client: Rule client for fetching rules and metrics.
        config: Engine configuration.
    """

    def __init__(
        self,
        client: TranspileRuleClient | None = None,
        config: TranspileConfig | None = None,
    ) -> None:
        """Initialize the transpile engine.

        Args:
            client: Rule client implementation. Defaults to MockTranspileClient.
            config: Engine configuration. Defaults to TranspileConfig().
        """
        self.client: TranspileRuleClient = client or MockTranspileClient()
        self.config = config or TranspileConfig()

    def transpile(
        self,
        sql: str,
        context: dict[str, Any] | None = None,
    ) -> TranspileResult:
        """Transpile SQL using configured rules.

        This is the main entry point for SQL transpilation. It performs:
        1. Rule fetching with retry logic
        2. METRIC() function expansion
        3. Table substitution
        4. Warning detection
        5. Result assembly

        Args:
            sql: SQL string to transpile.
            context: Optional context dict (e.g., environment, project_id).
                Currently used for:
                - project_id: Scope rules to specific project

        Returns:
            TranspileResult with:
            - success: Whether transpilation completed
            - sql: Transpiled SQL (or original on failure)
            - applied_rules: Rules that were applied
            - warnings: Detected issues
            - metadata: Timing and audit information
            - error: Error message if failed

        Raises:
            TranspileError: In strict mode, raised on any failure.
            SqlParseError: In strict mode, raised on SQL parse errors.
            MetricNotFoundError: In strict mode, raised when metric not found.
        """
        start_time = time.time()
        context = context or {}
        project_id = context.get("project_id")

        applied_rules: list[TranspileRule] = []
        warnings: list[TranspileWarning] = []
        error_message: str | None = None
        current_sql = sql

        try:
            # Step 1: Fetch rules
            rules = self._fetch_rules_with_retry(project_id)

            # Step 2: Expand METRIC() functions
            current_sql, metric_rules, metric_errors = self._expand_metrics(
                current_sql, rules
            )
            applied_rules.extend(metric_rules)

            if metric_errors:
                if self.config.strict_mode:
                    raise TranspileError(
                        f"Metric expansion failed: {'; '.join(metric_errors)}"
                    )
                # Add as warnings in non-strict mode
                for err in metric_errors:
                    warnings.append(
                        TranspileWarning(
                            type=WarningType.METRIC_ERROR,
                            message=f"Metric error: {err}",
                        )
                    )

            # Step 3: Apply table substitutions
            current_sql, table_rules = apply_table_substitutions(
                current_sql,
                rules,
                self.config.dialect,
            )
            applied_rules.extend(table_rules)

            # Step 4: Detect warnings
            warnings.extend(detect_warnings(current_sql, self.config.dialect))

            success = True

        except SqlParseError as e:
            if self.config.strict_mode:
                raise
            error_message = str(e)
            current_sql = sql  # Fall back to original
            success = False

        except MetricNotFoundError as e:
            if self.config.strict_mode:
                raise
            error_message = str(e)
            current_sql = sql  # Fall back to original
            success = False

        except TranspileError as e:
            if self.config.strict_mode:
                raise
            error_message = str(e)
            current_sql = sql  # Fall back to original
            success = False

        except Exception as e:
            if self.config.strict_mode:
                raise TranspileError(f"Unexpected error: {e}") from e
            error_message = f"Unexpected error: {e}"
            current_sql = sql
            success = False

        # Calculate duration
        duration_ms = int((time.time() - start_time) * 1000)

        # Build metadata
        metadata = TranspileMetadata(
            original_sql=sql,
            transpiled_at=datetime.now(tz=UTC),
            dialect=self.config.dialect,
            duration_ms=duration_ms,
        )

        return TranspileResult(
            success=success,
            sql=current_sql,
            applied_rules=applied_rules,
            warnings=warnings,
            metadata=metadata,
            error=error_message,
        )

    def _fetch_rules_with_retry(
        self,
        project_id: str | None,
    ) -> list[TranspileRule]:
        """Fetch rules with retry logic.

        Args:
            project_id: Optional project ID for rule scoping.

        Returns:
            List of TranspileRule objects.

        Raises:
            TranspileError: In strict mode if all retries fail.
        """
        last_error: Exception | None = None

        for attempt in range(self.config.retry_count + 1):
            try:
                return self.client.get_rules(project_id)
            except TranspileError as e:
                last_error = e
                if attempt < self.config.retry_count:
                    # Simple backoff: wait 0.5s * attempt
                    time.sleep(0.5 * (attempt + 1))

        if self.config.strict_mode and last_error:
            raise last_error

        # Graceful degradation: return empty rules
        return []

    def _expand_metrics(
        self,
        sql: str,
        rules: list[TranspileRule],  # noqa: ARG002 - reserved for future use
    ) -> tuple[str, list[TranspileRule], list[str]]:
        """Expand METRIC() functions in SQL.

        Args:
            sql: SQL with potential METRIC() functions.
            rules: Rules list (for future metric rule support).

        Returns:
            Tuple of:
            - Expanded SQL
            - Applied metric rules
            - Error messages
        """

        def metric_resolver(name: str) -> str | None:
            """Resolve metric name to SQL expression."""
            try:
                metric: MetricDefinition = self.client.get_metric(name)
            except MetricNotFoundError:
                return None
            except TranspileError:
                return None
            else:
                return metric.expression

        expanded_sql, errors = expand_metrics(sql, metric_resolver)

        # Build applied rules for metrics
        applied_rules: list[TranspileRule] = []

        # If expansion changed the SQL and no errors, track it
        if expanded_sql != sql and not errors:
            # Create a synthetic rule for tracking
            applied_rules.append(
                TranspileRule(
                    id="metric-expansion",
                    type=RuleType.METRIC_EXPANSION,
                    source="METRIC()",
                    target="<expanded>",
                    description="METRIC() function expanded to SQL expression",
                )
            )

        return expanded_sql, applied_rules, errors

    def validate_sql(self, sql: str) -> list[str]:
        """Validate SQL syntax.

        Attempts to parse the SQL and returns any errors.
        This is used when --validate option is specified.

        Args:
            sql: SQL to validate.

        Returns:
            List of validation error messages (empty if valid).
        """
        try:
            apply_table_substitutions(sql, [], self.config.dialect)
        except SqlParseError as e:
            return [str(e)]
        except Exception as e:
            return [f"Validation error: {e}"]
        else:
            return []
