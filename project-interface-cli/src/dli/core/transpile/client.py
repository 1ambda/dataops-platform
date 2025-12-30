"""
Transpile rule client implementations.

This module provides the client protocol and implementations for
fetching transpile rules and metric definitions:
- TranspileRuleClient: Protocol interface (dependency inversion)
- MockTranspileClient: Local mock for testing/development (Phase 1)
"""

from __future__ import annotations

from typing import Protocol

from dli.core.transpile.exceptions import MetricNotFoundError, RuleFetchError
from dli.core.transpile.models import (
    MetricDefinition,
    RuleType,
    TranspileRule,
)

__all__ = [
    "MockTranspileClient",
    "TranspileRuleClient",
]


class TranspileRuleClient(Protocol):
    """Protocol for transpile rule retrieval.

    This protocol defines the interface for fetching transpile rules
    and metric definitions. Implementations can be mocked for testing
    or connect to the real Basecamp Server.

    Implementations:
    - MockTranspileClient: For testing and development (Phase 1)
    - BasecampTranspileClient: For production (Phase 3, not yet implemented)
    """

    def get_rules(self, project_id: str | None = None) -> list[TranspileRule]:
        """Fetch transpile rules.

        Args:
            project_id: Optional project identifier for scoped rules.

        Returns:
            List of TranspileRule objects (may be empty).

        Raises:
            TranspileError: For network errors or timeouts.
            RuleFetchError: For server response errors (4xx/5xx).
        """
        ...

    def get_metric(self, name: str) -> MetricDefinition:
        """Fetch metric definition by name.

        Args:
            name: Metric identifier (case-sensitive).

        Returns:
            MetricDefinition with SQL expression.

        Raises:
            TranspileError: For network errors or timeouts.
            RuleFetchError: For server response errors (4xx/5xx, except 404).
            MetricNotFoundError: When metric doesn't exist (404).
        """
        ...

    def list_metric_names(self) -> list[str]:
        """List available metric names for suggestions.

        Returns:
            List of available metric names.
        """
        ...


class MockTranspileClient:
    """Mock client for testing and development.

    Provides hardcoded mock data for transpile rules and metrics.
    Used during Phase 1 development when server API is not available.

    Example:
        client = MockTranspileClient()
        rules = client.get_rules()
        metric = client.get_metric("revenue")
    """

    def __init__(self) -> None:
        """Initialize with hardcoded mock data."""
        self._rules: list[TranspileRule] = [
            TranspileRule(
                id="rule-001",
                type=RuleType.TABLE_SUBSTITUTION,
                source="raw.events",
                target="warehouse.events_v2",
                description="Events table migration to warehouse",
                enabled=True,
            ),
            TranspileRule(
                id="rule-002",
                type=RuleType.TABLE_SUBSTITUTION,
                source="analytics.users",
                target="analytics.users_v2",
                description="Users table migration to v2",
                enabled=True,
            ),
            TranspileRule(
                id="rule-003",
                type=RuleType.TABLE_SUBSTITUTION,
                source="legacy.orders",
                target="warehouse.orders",
                description="Legacy orders table migration",
                enabled=True,
            ),
        ]

        self._metrics: dict[str, MetricDefinition] = {
            "revenue": MetricDefinition(
                name="revenue",
                expression="SUM(amount * quantity)",
                source_table="analytics.orders",
                description="Total revenue from orders",
            ),
            "total_orders_from_active_customers": MetricDefinition(
                name="total_orders_from_active_customers",
                expression="SUM(CASE WHEN customer_status = 'active' THEN order_count ELSE 0 END)",
                source_table="analytics.orders",
                description="Total orders from active customers",
            ),
            "daily_active_users": MetricDefinition(
                name="daily_active_users",
                expression="COUNT(DISTINCT user_id)",
                source_table="analytics.user_events",
                description="Daily active users count",
            ),
            "conversion_rate": MetricDefinition(
                name="conversion_rate",
                expression="CAST(COUNT(DISTINCT CASE WHEN converted THEN user_id END) AS DOUBLE) / NULLIF(COUNT(DISTINCT user_id), 0)",
                source_table="analytics.conversions",
                description="User conversion rate",
            ),
        }

    def get_rules(
        self,
        project_id: str | None = None,  # noqa: ARG002
    ) -> list[TranspileRule]:
        """Return mock transpile rules.

        Args:
            project_id: Ignored in mock implementation.

        Returns:
            List of predefined mock rules.

        Note:
            In production, this would fetch rules from Basecamp Server
            filtered by project_id.
        """
        # Return only enabled rules
        return [r for r in self._rules if r.enabled]

    def get_metric(self, name: str) -> MetricDefinition:
        """Return mock metric definition.

        Args:
            name: Metric name to look up.

        Returns:
            MetricDefinition if found.

        Raises:
            MetricNotFoundError: If metric doesn't exist.
        """
        if not name:
            raise RuleFetchError(
                message="Invalid metric name",
                detail="Metric name cannot be empty",
            )

        if name not in self._metrics:
            raise MetricNotFoundError(
                metric_name=name,
                available_metrics=list(self._metrics.keys()),
            )

        return self._metrics[name]

    def list_metric_names(self) -> list[str]:
        """Return list of available mock metric names.

        Returns:
            List of metric names.
        """
        return list(self._metrics.keys())

    def add_rule(self, rule: TranspileRule) -> None:
        """Add a rule for testing.

        Args:
            rule: Rule to add to mock data.
        """
        self._rules.append(rule)

    def add_metric(self, metric: MetricDefinition) -> None:
        """Add a metric for testing.

        Args:
            metric: Metric to add to mock data.
        """
        self._metrics[metric.name] = metric

    def clear(self) -> None:
        """Clear all mock data for testing."""
        self._rules.clear()
        self._metrics.clear()
