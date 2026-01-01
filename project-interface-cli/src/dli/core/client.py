"""Basecamp Server API Client.

This module provides a client for interacting with the Basecamp Server API
to manage metrics, datasets, and queries remotely.

The client supports:
- Listing/searching specs on the server
- Fetching spec details
- Registering local specs to the server
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timedelta
from enum import Enum
import logging
from typing import Any

logger = logging.getLogger(__name__)


class WorkflowSource(str, Enum):
    """Source type for workflow runs.

    Indicates how the workflow was registered:
    - CODE: Registered via CI/CD pipeline from Git
    - MANUAL: User registered via CLI/API
    """

    CODE = "code"
    MANUAL = "manual"


class RunStatus(str, Enum):
    """Status of a workflow run.

    Status values match the server API response format:
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


@dataclass
class ServerConfig:
    """Server connection configuration."""

    url: str
    timeout: int = 30
    api_key: str | None = None


@dataclass
class ServerResponse:
    """Response from server API calls."""

    success: bool
    data: dict[str, Any] | list[dict[str, Any]] | None = None
    error: str | None = None
    status_code: int = 200


class BasecampClient:
    """Client for Basecamp Server API.

    This client provides methods to interact with the Basecamp Server
    for managing metrics, datasets, and queries.

    In mock mode, all operations return simulated responses without
    making actual HTTP requests.

    Attributes:
        config: Server connection configuration
        mock_mode: Whether to use mock responses
    """

    def __init__(self, config: ServerConfig, mock_mode: bool = False):
        """Initialize the client.

        Args:
            config: Server connection configuration
            mock_mode: If True, use mock responses instead of real API calls
        """
        self.config = config
        self.mock_mode = mock_mode
        self._mock_data = self._init_mock_data()

    def _init_mock_data(self) -> dict[str, list[dict[str, Any]]]:
        """Initialize mock data for testing."""
        now = datetime.now()
        return {
            "catalog_tables": self._init_mock_catalog_tables(now),
            "queries": self._init_mock_queries(now),
            "metrics": [
                {
                    "name": "iceberg.reporting.user_summary",
                    "type": "Metric",
                    "owner": "analyst@example.com",
                    "team": "@analytics",
                    "description": "User summary metrics",
                    "tags": ["reporting", "daily"],
                },
                {
                    "name": "iceberg.analytics.revenue_daily",
                    "type": "Metric",
                    "owner": "data@example.com",
                    "team": "@data-eng",
                    "description": "Daily revenue metrics",
                    "tags": ["revenue", "kpi"],
                },
            ],
            "datasets": [
                {
                    "name": "iceberg.analytics.daily_clicks",
                    "type": "Dataset",
                    "owner": "engineer@example.com",
                    "team": "@data-eng",
                    "description": "Daily click aggregations",
                    "tags": ["feed", "daily"],
                },
                {
                    "name": "iceberg.warehouse.user_events",
                    "type": "Dataset",
                    "owner": "warehouse@example.com",
                    "team": "@warehouse",
                    "description": "User events fact table",
                    "tags": ["events", "fact"],
                },
            ],
            "workflows": [
                {
                    "dataset_name": "iceberg.analytics.daily_clicks",
                    "source": WorkflowSource.CODE.value,
                    "schedule": "0 6 * * *",
                    "enabled": True,
                    "paused": False,
                    "owner": "engineer@example.com",
                    "team": "@data-eng",
                    "last_run_at": (now - timedelta(hours=6)).isoformat(),
                    "last_run_status": RunStatus.COMPLETED.value,
                    "next_run_at": (now + timedelta(hours=18)).isoformat(),
                },
                {
                    "dataset_name": "iceberg.warehouse.user_events",
                    "source": WorkflowSource.CODE.value,
                    "schedule": "0 */4 * * *",
                    "enabled": True,
                    "paused": False,
                    "owner": "warehouse@example.com",
                    "team": "@warehouse",
                    "last_run_at": (now - timedelta(hours=2)).isoformat(),
                    "last_run_status": RunStatus.COMPLETED.value,
                    "next_run_at": (now + timedelta(hours=2)).isoformat(),
                },
                {
                    "dataset_name": "iceberg.reporting.weekly_summary",
                    "source": WorkflowSource.MANUAL.value,
                    "schedule": None,
                    "enabled": True,
                    "paused": True,
                    "owner": "analyst@example.com",
                    "team": "@analytics",
                    "last_run_at": (now - timedelta(days=7)).isoformat(),
                    "last_run_status": RunStatus.FAILED.value,
                    "next_run_at": None,
                },
            ],
            "workflow_runs": [
                {
                    "run_id": "iceberg.analytics.daily_clicks_20240115_060000",
                    "dataset_name": "iceberg.analytics.daily_clicks",
                    "source": WorkflowSource.CODE.value,
                    "status": RunStatus.COMPLETED.value,
                    "started_at": (now - timedelta(hours=6)).isoformat(),
                    "ended_at": (now - timedelta(hours=6) + timedelta(minutes=15)).isoformat(),
                    "duration_seconds": 900,
                    "triggered_by": "scheduler",
                },
                {
                    "run_id": "iceberg.warehouse.user_events_20240115_040000",
                    "dataset_name": "iceberg.warehouse.user_events",
                    "source": WorkflowSource.CODE.value,
                    "status": RunStatus.COMPLETED.value,
                    "started_at": (now - timedelta(hours=4)).isoformat(),
                    "ended_at": (now - timedelta(hours=4) + timedelta(minutes=30)).isoformat(),
                    "duration_seconds": 1800,
                    "triggered_by": "scheduler",
                },
                {
                    "run_id": "iceberg.reporting.weekly_summary_20240108_100000",
                    "dataset_name": "iceberg.reporting.weekly_summary",
                    "source": WorkflowSource.MANUAL.value,
                    "status": RunStatus.FAILED.value,
                    "started_at": (now - timedelta(days=7)).isoformat(),
                    "ended_at": (now - timedelta(days=7) + timedelta(minutes=5)).isoformat(),
                    "duration_seconds": 300,
                    "triggered_by": "user@example.com",
                    "error_message": "Query timeout: exceeded 30 minute limit",
                },
                {
                    "run_id": "iceberg.analytics.daily_clicks_20240115_120000",
                    "dataset_name": "iceberg.analytics.daily_clicks",
                    "source": WorkflowSource.MANUAL.value,
                    "status": RunStatus.RUNNING.value,
                    "started_at": (now - timedelta(minutes=10)).isoformat(),
                    "ended_at": None,
                    "duration_seconds": None,
                    "triggered_by": "user@example.com",
                },
            ],
        }

    def _init_mock_catalog_tables(self, now: datetime) -> list[dict[str, Any]]:
        """Initialize mock catalog table data for testing."""
        return [
            {
                "name": "my-project.analytics.users",
                "engine": "bigquery",
                "owner": "data-team@example.com",
                "team": "@data-eng",
                "description": "User dimension table with profile information",
                "tags": ["tier::critical", "domain::analytics", "pii"],
                "row_count": 1500000,
                "last_updated": (now - timedelta(hours=2)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.users",
                "columns": [
                    {"name": "user_id", "data_type": "STRING", "description": "Unique user identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1500000},
                    {"name": "email", "data_type": "STRING", "description": "User email address", "is_pii": True, "fill_rate": 0.98, "distinct_count": 1470000},
                    {"name": "name", "data_type": "STRING", "description": "Full name", "is_pii": True, "fill_rate": 0.95, "distinct_count": 1200000},
                    {"name": "created_at", "data_type": "TIMESTAMP", "description": "Account creation time", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1000000},
                    {"name": "country", "data_type": "STRING", "description": "User country code", "is_pii": False, "fill_rate": 0.92, "distinct_count": 195},
                ],
                "ownership": {
                    "owner": "data-team@example.com",
                    "team": "@data-eng",
                    "stewards": ["alice@example.com", "bob@example.com"],
                    "consumers": ["@analytics", "@marketing", "@product"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=2)).isoformat(),
                    "avg_update_lag_hours": 1.5,
                    "update_frequency": "hourly",
                    "is_stale": False,
                    "stale_threshold_hours": 6,
                },
                "quality": {
                    "score": 92,
                    "total_tests": 15,
                    "passed_tests": 14,
                    "failed_tests": 1,
                    "warnings": 0,
                    "recent_tests": [
                        {"test_name": "user_id_not_null", "test_type": "not_null", "status": "pass", "failed_rows": 0},
                        {"test_name": "email_unique", "test_type": "unique", "status": "pass", "failed_rows": 0},
                        {"test_name": "country_in_set", "test_type": "accepted_values", "status": "fail", "failed_rows": 42},
                    ],
                },
                "sample_queries": [
                    {"title": "Active users by country", "sql": "SELECT country, COUNT(*) FROM `my-project.analytics.users` WHERE last_login > DATE_SUB(CURRENT_DATE(), INTERVAL 30 DAY) GROUP BY 1", "author": "analyst@example.com", "run_count": 156, "last_run": (now - timedelta(hours=1)).isoformat()},
                    {"title": "User growth trend", "sql": "SELECT DATE(created_at) as date, COUNT(*) as new_users FROM `my-project.analytics.users` GROUP BY 1 ORDER BY 1", "author": "growth@example.com", "run_count": 89, "last_run": (now - timedelta(hours=3)).isoformat()},
                ],
            },
            {
                "name": "my-project.analytics.orders",
                "engine": "bigquery",
                "owner": "commerce@example.com",
                "team": "@commerce",
                "description": "Order transaction fact table",
                "tags": ["tier::critical", "domain::commerce", "fact"],
                "row_count": 25000000,
                "last_updated": (now - timedelta(hours=1)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.analytics.orders",
                "columns": [
                    {"name": "order_id", "data_type": "STRING", "description": "Unique order identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 25000000},
                    {"name": "user_id", "data_type": "STRING", "description": "Customer user ID", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1200000},
                    {"name": "total_amount", "data_type": "FLOAT64", "description": "Order total in USD", "is_pii": False, "fill_rate": 1.0, "distinct_count": 50000},
                    {"name": "status", "data_type": "STRING", "description": "Order status", "is_pii": False, "fill_rate": 1.0, "distinct_count": 5},
                    {"name": "created_at", "data_type": "TIMESTAMP", "description": "Order creation time", "is_pii": False, "fill_rate": 1.0, "distinct_count": 20000000},
                ],
                "ownership": {
                    "owner": "commerce@example.com",
                    "team": "@commerce",
                    "stewards": ["commerce-data@example.com"],
                    "consumers": ["@finance", "@analytics", "@fulfillment"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=1)).isoformat(),
                    "avg_update_lag_hours": 0.5,
                    "update_frequency": "hourly",
                    "is_stale": False,
                    "stale_threshold_hours": 4,
                },
                "quality": {
                    "score": 98,
                    "total_tests": 20,
                    "passed_tests": 20,
                    "failed_tests": 0,
                    "warnings": 0,
                    "recent_tests": [
                        {"test_name": "order_id_unique", "test_type": "unique", "status": "pass", "failed_rows": 0},
                        {"test_name": "total_amount_positive", "test_type": "expression", "status": "pass", "failed_rows": 0},
                    ],
                },
                "sample_queries": [],
            },
            {
                "name": "my-project.warehouse.events",
                "engine": "bigquery",
                "owner": "platform@example.com",
                "team": "@data-platform",
                "description": "Raw event stream from mobile and web",
                "tags": ["tier::standard", "domain::platform", "raw"],
                "row_count": 500000000,
                "last_updated": (now - timedelta(minutes=15)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/my-project.warehouse.events",
                "columns": [
                    {"name": "event_id", "data_type": "STRING", "description": "Unique event identifier", "is_pii": False, "fill_rate": 1.0, "distinct_count": 500000000},
                    {"name": "event_type", "data_type": "STRING", "description": "Event type name", "is_pii": False, "fill_rate": 1.0, "distinct_count": 150},
                    {"name": "user_id", "data_type": "STRING", "description": "User identifier", "is_pii": False, "fill_rate": 0.85, "distinct_count": 2000000},
                    {"name": "properties", "data_type": "JSON", "description": "Event properties", "is_pii": True, "fill_rate": 0.95, "distinct_count": None},
                    {"name": "timestamp", "data_type": "TIMESTAMP", "description": "Event timestamp", "is_pii": False, "fill_rate": 1.0, "distinct_count": 400000000},
                ],
                "ownership": {
                    "owner": "platform@example.com",
                    "team": "@data-platform",
                    "stewards": [],
                    "consumers": ["@analytics", "@data-science", "@product"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(minutes=15)).isoformat(),
                    "avg_update_lag_hours": 0.1,
                    "update_frequency": "streaming",
                    "is_stale": False,
                    "stale_threshold_hours": 1,
                },
                "quality": {
                    "score": 85,
                    "total_tests": 10,
                    "passed_tests": 8,
                    "failed_tests": 1,
                    "warnings": 1,
                    "recent_tests": [
                        {"test_name": "event_id_not_null", "test_type": "not_null", "status": "pass", "failed_rows": 0},
                        {"test_name": "timestamp_recent", "test_type": "expression", "status": "warn", "failed_rows": 100},
                    ],
                },
                "sample_queries": [],
            },
            {
                "name": "other-project.reporting.sales_summary",
                "engine": "trino",
                "owner": "sales@example.com",
                "team": "@sales",
                "description": "Daily sales summary by region",
                "tags": ["tier::standard", "domain::sales", "aggregated"],
                "row_count": 50000,
                "last_updated": (now - timedelta(hours=6)).isoformat(),
                "basecamp_url": "https://basecamp.example.com/catalog/other-project.reporting.sales_summary",
                "columns": [
                    {"name": "date", "data_type": "DATE", "description": "Report date", "is_pii": False, "fill_rate": 1.0, "distinct_count": 365},
                    {"name": "region", "data_type": "VARCHAR", "description": "Sales region", "is_pii": False, "fill_rate": 1.0, "distinct_count": 12},
                    {"name": "total_sales", "data_type": "DECIMAL", "description": "Total sales amount", "is_pii": False, "fill_rate": 1.0, "distinct_count": 45000},
                    {"name": "order_count", "data_type": "INTEGER", "description": "Number of orders", "is_pii": False, "fill_rate": 1.0, "distinct_count": 1000},
                ],
                "ownership": {
                    "owner": "sales@example.com",
                    "team": "@sales",
                    "stewards": ["sales-ops@example.com"],
                    "consumers": ["@executive", "@finance"],
                },
                "freshness": {
                    "last_updated": (now - timedelta(hours=6)).isoformat(),
                    "avg_update_lag_hours": 6.0,
                    "update_frequency": "daily",
                    "is_stale": False,
                    "stale_threshold_hours": 24,
                },
                "quality": {
                    "score": 100,
                    "total_tests": 5,
                    "passed_tests": 5,
                    "failed_tests": 0,
                    "warnings": 0,
                    "recent_tests": [],
                },
                "sample_queries": [],
            },
        ]

    def _init_mock_queries(self, now: datetime) -> list[dict[str, Any]]:
        """Initialize mock query execution data for testing."""
        return [
            # Personal query - current user, successful
            {
                "query_id": "bq_job_abc123",
                "engine": "bigquery",
                "state": "success",
                "account": "current_user@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=2)).isoformat(),
                "finished_at": (now - timedelta(hours=2) + timedelta(seconds=12.5)).isoformat(),
                "duration_seconds": 12.5,
                "tables_used_count": 3,
                "tags": ["team::analytics", "pipeline::daily"],
                "query_preview": "SELECT user_id, COUNT(*) as event_count FROM analytics.raw_events...",
            },
            # Personal query - current user, failed
            {
                "query_id": "bq_job_def456",
                "engine": "bigquery",
                "state": "failed",
                "account": "current_user@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=3)).isoformat(),
                "finished_at": (now - timedelta(hours=3) + timedelta(seconds=0.8)).isoformat(),
                "duration_seconds": 0.8,
                "tables_used_count": 1,
                "error_message": "Quota exceeded: Your project exceeded quota for free query bytes scanned.",
                "tags": [],
                "query_preview": "SELECT * FROM analytics.large_table WHERE date = '2026-01-01'...",
            },
            # System account query - airflow, running
            {
                "query_id": "airflow_job_001",
                "engine": "trino",
                "state": "running",
                "account": "airflow-prod",
                "account_type": "system",
                "started_at": (now - timedelta(minutes=5)).isoformat(),
                "finished_at": None,
                "duration_seconds": None,
                "tables_used_count": 5,
                "tags": ["pipeline::daily_aggregation", "team::data-eng"],
                "query_preview": "INSERT INTO warehouse.daily_metrics SELECT date, SUM(amount)...",
            },
            # System account query - airflow, completed
            {
                "query_id": "airflow_job_002",
                "engine": "bigquery",
                "state": "success",
                "account": "airflow-prod",
                "account_type": "system",
                "started_at": (now - timedelta(hours=1)).isoformat(),
                "finished_at": (now - timedelta(hours=1) + timedelta(minutes=2)).isoformat(),
                "duration_seconds": 120.5,
                "tables_used_count": 8,
                "tags": ["pipeline::hourly_sync", "team::data-eng"],
                "query_preview": "MERGE INTO warehouse.users USING staging.users_delta...",
            },
            # System account query - dbt-runner, pending
            {
                "query_id": "dbt_run_xyz789",
                "engine": "trino",
                "state": "pending",
                "account": "dbt-runner",
                "account_type": "system",
                "started_at": (now - timedelta(seconds=30)).isoformat(),
                "finished_at": None,
                "duration_seconds": None,
                "tables_used_count": 0,
                "tags": ["dbt::model", "team::analytics"],
                "query_preview": "CREATE TABLE analytics.user_summary AS SELECT...",
            },
            # Other user query - alice
            {
                "query_id": "trino_alice_001",
                "engine": "trino",
                "state": "success",
                "account": "alice@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=4)).isoformat(),
                "finished_at": (now - timedelta(hours=4) + timedelta(seconds=45.2)).isoformat(),
                "duration_seconds": 45.2,
                "tables_used_count": 2,
                "tags": ["experiment::ab_test_v2"],
                "query_preview": "SELECT * FROM users WHERE experiment_group = 'treatment'...",
            },
            # Other user query - bob, cancelled
            {
                "query_id": "bq_bob_cancelled",
                "engine": "bigquery",
                "state": "cancelled",
                "account": "bob@company.com",
                "account_type": "personal",
                "started_at": (now - timedelta(hours=5)).isoformat(),
                "finished_at": (now - timedelta(hours=5) + timedelta(minutes=3)).isoformat(),
                "duration_seconds": 180.0,
                "tables_used_count": 1,
                "tags": [],
                "query_preview": "SELECT * FROM warehouse.events WHERE date BETWEEN...",
            },
        ]

    def health_check(self) -> ServerResponse:
        """Check server health status.

        Returns:
            ServerResponse with health status
        """
        if self.mock_mode:
            return ServerResponse(
                success=True,
                data={"status": "healthy", "version": "1.0.0"},
            )

        # TODO: Implement actual HTTP call
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Metric operations

    def list_metrics(
        self,
        tag: str | None = None,
        owner: str | None = None,
        search: str | None = None,
    ) -> ServerResponse:
        """List metrics from server.

        Args:
            tag: Filter by tag
            owner: Filter by owner
            search: Search in name/description

        Returns:
            ServerResponse with list of metrics
        """
        if self.mock_mode:
            metrics = self._mock_data["metrics"]
            if tag:
                metrics = [m for m in metrics if tag in m.get("tags", [])]
            if owner:
                metrics = [m for m in metrics if owner in m.get("owner", "")]
            if search:
                search_lower = search.lower()
                metrics = [
                    m
                    for m in metrics
                    if search_lower in m.get("name", "").lower()
                    or search_lower in m.get("description", "").lower()
                ]
            return ServerResponse(success=True, data=metrics)

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def get_metric(self, name: str) -> ServerResponse:
        """Get metric details from server.

        Args:
            name: Metric name

        Returns:
            ServerResponse with metric details
        """
        if self.mock_mode:
            for metric in self._mock_data["metrics"]:
                if metric["name"] == name:
                    return ServerResponse(success=True, data=metric)
            return ServerResponse(
                success=False,
                error=f"Metric '{name}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def register_metric(self, spec_data: dict[str, Any]) -> ServerResponse:
        """Register a metric to the server.

        Args:
            spec_data: Metric spec data

        Returns:
            ServerResponse with registration result
        """
        if self.mock_mode:
            name = spec_data.get("name", "unknown")
            # Check for duplicates
            for metric in self._mock_data["metrics"]:
                if metric["name"] == name:
                    return ServerResponse(
                        success=False,
                        error=f"Metric '{name}' already exists",
                        status_code=409,
                    )
            self._mock_data["metrics"].append(spec_data)
            return ServerResponse(
                success=True,
                data={"message": f"Metric '{name}' registered successfully"},
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Dataset operations

    def list_datasets(
        self,
        tag: str | None = None,
        owner: str | None = None,
        search: str | None = None,
    ) -> ServerResponse:
        """List datasets from server.

        Args:
            tag: Filter by tag
            owner: Filter by owner
            search: Search in name/description

        Returns:
            ServerResponse with list of datasets
        """
        if self.mock_mode:
            datasets = self._mock_data["datasets"]
            if tag:
                datasets = [d for d in datasets if tag in d.get("tags", [])]
            if owner:
                datasets = [d for d in datasets if owner in d.get("owner", "")]
            if search:
                search_lower = search.lower()
                datasets = [
                    d
                    for d in datasets
                    if search_lower in d.get("name", "").lower()
                    or search_lower in d.get("description", "").lower()
                ]
            return ServerResponse(success=True, data=datasets)

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def get_dataset(self, name: str) -> ServerResponse:
        """Get dataset details from server.

        Args:
            name: Dataset name

        Returns:
            ServerResponse with dataset details
        """
        if self.mock_mode:
            for dataset in self._mock_data["datasets"]:
                if dataset["name"] == name:
                    return ServerResponse(success=True, data=dataset)
            return ServerResponse(
                success=False,
                error=f"Dataset '{name}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def register_dataset(self, spec_data: dict[str, Any]) -> ServerResponse:
        """Register a dataset to the server.

        Args:
            spec_data: Dataset spec data

        Returns:
            ServerResponse with registration result
        """
        if self.mock_mode:
            name = spec_data.get("name", "unknown")
            # Check for duplicates
            for dataset in self._mock_data["datasets"]:
                if dataset["name"] == name:
                    return ServerResponse(
                        success=False,
                        error=f"Dataset '{name}' already exists",
                        status_code=409,
                    )
            self._mock_data["datasets"].append(spec_data)
            return ServerResponse(
                success=True,
                data={"message": f"Dataset '{name}' registered successfully"},
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Lineage operations

    def get_lineage(
        self,
        resource_name: str,
        direction: str = "both",
        depth: int = -1,
    ) -> ServerResponse:
        """Get lineage information for a resource.

        Queries lineage (dependencies and dependents) for the specified
        resource from the server.

        Args:
            resource_name: Fully qualified resource name
                (e.g., 'iceberg.analytics.daily_clicks')
            direction: Lineage direction ('upstream', 'downstream', 'both')
            depth: Maximum traversal depth (-1 for unlimited)

        Returns:
            ServerResponse with lineage data containing:
            - root: The queried resource node
            - nodes: List of related nodes
            - edges: List of dependency edges
            - total_upstream: Count of upstream dependencies
            - total_downstream: Count of downstream dependents
        """
        if self.mock_mode:
            return self._get_mock_lineage(resource_name, direction, depth)

        # TODO: Implement actual HTTP call
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def _get_mock_lineage(
        self,
        resource_name: str,
        direction: str,
        depth: int,
    ) -> ServerResponse:
        """Generate mock lineage data for testing.

        Creates realistic mock lineage data based on the resource name
        and registered datasets/metrics.

        Args:
            resource_name: The resource to get lineage for
            direction: Lineage direction
            depth: Maximum depth (-1 for unlimited)

        Returns:
            ServerResponse with mock lineage data
        """
        # Check if resource exists
        all_resources = self._mock_data["datasets"] + self._mock_data["metrics"]
        resource = None
        for r in all_resources:
            if r["name"] == resource_name:
                resource = r
                break

        if resource is None:
            return ServerResponse(
                success=False,
                error=f"Resource '{resource_name}' not found",
                status_code=404,
            )

        # Build mock lineage based on resource name patterns
        nodes: list[dict[str, Any]] = []
        edges: list[dict[str, Any]] = []

        # Mock upstream dependencies (what this resource depends on)
        mock_upstream = self._generate_mock_upstream(resource_name, depth)

        # Mock downstream dependents (what depends on this resource)
        mock_downstream = self._generate_mock_downstream(resource_name, depth)

        if direction in ("upstream", "both"):
            nodes.extend(mock_upstream["nodes"])
            edges.extend(mock_upstream["edges"])

        if direction in ("downstream", "both"):
            nodes.extend(mock_downstream["nodes"])
            edges.extend(mock_downstream["edges"])

        return ServerResponse(
            success=True,
            data={
                "root": {
                    "name": resource_name,
                    "type": resource.get("type", "Dataset"),
                    "owner": resource.get("owner"),
                    "team": resource.get("team"),
                    "description": resource.get("description"),
                    "tags": resource.get("tags", []),
                },
                "nodes": nodes,
                "edges": edges,
                "total_upstream": len(mock_upstream["nodes"]),
                "total_downstream": len(mock_downstream["nodes"]),
            },
        )

    def _generate_mock_upstream(
        self,
        resource_name: str,
        depth: int,
    ) -> dict[str, list[dict[str, Any]]]:
        """Generate mock upstream dependencies.

        Args:
            resource_name: The resource name
            depth: Maximum depth

        Returns:
            Dictionary with 'nodes' and 'edges' lists
        """
        nodes: list[dict[str, Any]] = []
        edges: list[dict[str, Any]] = []

        # Create mock upstream based on naming conventions
        # e.g., iceberg.analytics.daily_clicks depends on iceberg.raw.clicks
        parts = resource_name.split(".")
        if len(parts) >= 3:
            catalog, schema, table = parts[0], parts[1], parts[2]

            # Generate raw source dependency
            if schema in ("analytics", "reporting", "warehouse"):
                raw_source = f"{catalog}.raw.{table.replace('daily_', '').replace('_summary', '')}"
                nodes.append({
                    "name": raw_source,
                    "type": "Dataset",
                    "owner": "ingestion@example.com",
                    "team": "@data-platform",
                    "description": f"Raw source data for {table}",
                    "tags": ["raw", "source"],
                    "depth": -1,
                })
                edges.append({
                    "source": raw_source,
                    "target": resource_name,
                    "edge_type": "direct",
                })

                # Add second level if depth allows
                if depth == -1 or depth >= 2:
                    external_source = f"external.kafka.{table.replace('daily_', '')}_events"
                    nodes.append({
                        "name": external_source,
                        "type": "External",
                        "owner": "streaming@example.com",
                        "team": "@streaming",
                        "description": "Kafka event stream",
                        "tags": ["kafka", "streaming"],
                        "depth": -2,
                    })
                    edges.append({
                        "source": external_source,
                        "target": raw_source,
                        "edge_type": "direct",
                    })

        return {"nodes": nodes, "edges": edges}

    def _generate_mock_downstream(
        self,
        resource_name: str,
        depth: int,
    ) -> dict[str, list[dict[str, Any]]]:
        """Generate mock downstream dependents.

        Args:
            resource_name: The resource name
            depth: Maximum depth

        Returns:
            Dictionary with 'nodes' and 'edges' lists
        """
        nodes: list[dict[str, Any]] = []
        edges: list[dict[str, Any]] = []

        # Create mock downstream based on naming conventions
        parts = resource_name.split(".")
        if len(parts) >= 3:
            catalog, schema, table = parts[0], parts[1], parts[2]

            # Generate reporting dependency
            if schema in ("raw", "analytics", "warehouse"):
                reporting_target = f"{catalog}.reporting.{table}_report"
                nodes.append({
                    "name": reporting_target,
                    "type": "Dataset",
                    "owner": "analyst@example.com",
                    "team": "@analytics",
                    "description": f"Reporting view for {table}",
                    "tags": ["reporting", "bi"],
                    "depth": 1,
                })
                edges.append({
                    "source": resource_name,
                    "target": reporting_target,
                    "edge_type": "direct",
                })

                # Add metric dependency if depth allows
                if depth == -1 or depth >= 2:
                    metric_target = f"{catalog}.metrics.{table}_kpi"
                    nodes.append({
                        "name": metric_target,
                        "type": "Metric",
                        "owner": "bi@example.com",
                        "team": "@bi",
                        "description": "KPI metric derived from this dataset",
                        "tags": ["kpi", "metric"],
                        "depth": 2,
                    })
                    edges.append({
                        "source": reporting_target,
                        "target": metric_target,
                        "edge_type": "direct",
                    })

        return {"nodes": nodes, "edges": edges}

    # Quality test operations

    def execute_quality_test(
        self,
        resource_name: str,
        test_name: str,
        test_type: str,
        columns: list[str] | None = None,
        params: dict[str, Any] | None = None,
        severity: str = "error",
    ) -> ServerResponse:
        """Execute a quality test on the server.

        The server runs the test against the actual data and returns results.
        This is useful when the local environment doesn't have access to
        production data.

        Args:
            resource_name: Name of the resource to test (table/dataset)
            test_name: Name of the test
            test_type: Type of test (not_null, unique, etc.)
            columns: Columns to test (for column-based tests)
            params: Additional test parameters
            severity: Test severity (error or warn)

        Returns:
            ServerResponse with test execution result:
                - status: pass, fail, warn, or error
                - failed_rows: Number of failing rows
                - failed_samples: Sample of failing rows
                - execution_time_ms: Execution time
        """
        if self.mock_mode:
            # Mock response simulating a passing test
            # In real usage, the server would execute the test
            return ServerResponse(
                success=True,
                data={
                    "status": "pass",
                    "failed_rows": 0,
                    "failed_samples": [],
                    "execution_time_ms": 150,
                    "rendered_sql": f"-- Mock SQL for {test_type} on {resource_name}",
                },
            )

        # Real API call (not implemented yet)
        # endpoint = f"/api/v1/quality/test/{resource_name}"
        # payload = {
        #     "test_name": test_name,
        #     "test_type": test_type,
        #     "columns": columns,
        #     "params": params,
        #     "severity": severity,
        # }
        # return self._post(endpoint, json=payload)
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Workflow operations

    def workflow_run(
        self,
        dataset_name: str,
        params: dict[str, Any] | None = None,
        dry_run: bool = False,
    ) -> ServerResponse:
        """Trigger a workflow run for a dataset.

        Args:
            dataset_name: Name of the dataset to run workflow for
            params: Optional parameters to pass to the workflow
            dry_run: If True, validate without actually running

        Returns:
            ServerResponse with run_id if successful
        """
        if self.mock_mode:
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            run_id = f"{dataset_name}_{timestamp}"

            if dry_run:
                return ServerResponse(
                    success=True,
                    data={
                        "message": "Dry run validation passed",
                        "dataset_name": dataset_name,
                        "params": params or {},
                        "would_create_run_id": run_id,
                    },
                )

            return ServerResponse(
                success=True,
                data={
                    "run_id": run_id,
                    "dataset_name": dataset_name,
                    "status": RunStatus.PENDING.value,
                    "triggered_by": "cli",
                    "params": params or {},
                },
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_backfill(
        self,
        dataset_name: str,
        start_date: str,
        end_date: str,
        params: dict[str, Any] | None = None,
    ) -> ServerResponse:
        """Trigger backfill runs for a date range.

        Args:
            dataset_name: Name of the dataset to backfill
            start_date: Start date (YYYY-MM-DD format)
            end_date: End date (YYYY-MM-DD format)
            params: Optional parameters to pass to each run

        Returns:
            ServerResponse with list of run_ids for each date
        """
        if self.mock_mode:
            # Parse dates and generate run_ids for each date
            start = datetime.strptime(start_date, "%Y-%m-%d")
            end = datetime.strptime(end_date, "%Y-%m-%d")

            run_ids: list[dict[str, Any]] = []
            current = start
            while current <= end:
                date_str = current.strftime("%Y%m%d")
                run_id = f"{dataset_name}_{date_str}_000000"
                run_ids.append({
                    "run_id": run_id,
                    "date": current.strftime("%Y-%m-%d"),
                    "status": RunStatus.PENDING.value,
                })
                current += timedelta(days=1)

            return ServerResponse(
                success=True,
                data={
                    "dataset_name": dataset_name,
                    "start_date": start_date,
                    "end_date": end_date,
                    "total_runs": len(run_ids),
                    "runs": run_ids,
                },
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_stop(self, run_id: str) -> ServerResponse:
        """Stop a running workflow.

        Args:
            run_id: ID of the run to stop

        Returns:
            ServerResponse indicating success or failure
        """
        if self.mock_mode:
            # Check if run exists and is running
            for run in self._mock_data["workflow_runs"]:
                if run["run_id"] == run_id:
                    if run["status"] == RunStatus.RUNNING.value:
                        return ServerResponse(
                            success=True,
                            data={
                                "run_id": run_id,
                                "status": RunStatus.KILLED.value,
                                "message": "Workflow run stopped successfully",
                            },
                        )
                    return ServerResponse(
                        success=False,
                        error=f"Cannot stop run with status '{run['status']}'",
                        status_code=400,
                    )

            return ServerResponse(
                success=False,
                error=f"Run '{run_id}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_status(self, run_id: str) -> ServerResponse:
        """Get status of a workflow run.

        Args:
            run_id: ID of the run to check

        Returns:
            ServerResponse with run status information
        """
        if self.mock_mode:
            for run in self._mock_data["workflow_runs"]:
                if run["run_id"] == run_id:
                    return ServerResponse(
                        success=True,
                        data=run,
                    )

            return ServerResponse(
                success=False,
                error=f"Run '{run_id}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_list(
        self,
        source: str | None = None,
        running_only: bool = False,
        enabled_only: bool = False,
        dataset_filter: str | None = None,
    ) -> ServerResponse:
        """List workflows (scheduled datasets).

        Args:
            source: Filter by source type ('Code' or 'Manual')
            running_only: If True, only show workflows with running jobs
            enabled_only: If True, only show enabled workflows
            dataset_filter: Filter by dataset name pattern

        Returns:
            ServerResponse with list of WorkflowInfo dicts
        """
        if self.mock_mode:
            workflows = self._mock_data["workflows"].copy()

            # Apply filters
            if source:
                workflows = [w for w in workflows if w["source"] == source]

            if enabled_only:
                workflows = [w for w in workflows if w["enabled"]]

            if dataset_filter:
                filter_lower = dataset_filter.lower()
                workflows = [
                    w for w in workflows
                    if filter_lower in w["dataset_name"].lower()
                ]

            if running_only:
                # Check for running jobs
                running_datasets = {
                    r["dataset_name"]
                    for r in self._mock_data["workflow_runs"]
                    if r["status"] == RunStatus.RUNNING.value
                }
                workflows = [
                    w for w in workflows
                    if w["dataset_name"] in running_datasets
                ]

            return ServerResponse(
                success=True,
                data=workflows,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_history(
        self,
        dataset_filter: str | None = None,
        source: str | None = None,
        limit: int = 20,
        status_filter: str | None = None,
    ) -> ServerResponse:
        """Get workflow run history.

        Args:
            dataset_filter: Filter by dataset name pattern
            source: Filter by source type ('Code' or 'Manual')
            limit: Maximum number of runs to return
            status_filter: Filter by run status

        Returns:
            ServerResponse with list of WorkflowRun dicts
        """
        if self.mock_mode:
            runs = self._mock_data["workflow_runs"].copy()

            # Apply filters
            if dataset_filter:
                filter_lower = dataset_filter.lower()
                runs = [
                    r for r in runs
                    if filter_lower in r["dataset_name"].lower()
                ]

            if source:
                runs = [r for r in runs if r["source"] == source]

            if status_filter:
                runs = [r for r in runs if r["status"] == status_filter]

            # Sort by started_at descending and apply limit
            runs = sorted(
                runs,
                key=lambda r: r.get("started_at", ""),
                reverse=True,
            )[:limit]

            return ServerResponse(
                success=True,
                data=runs,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_pause(self, dataset_name: str) -> ServerResponse:
        """Pause a workflow (disable scheduled runs).

        Args:
            dataset_name: Name of the dataset workflow to pause

        Returns:
            ServerResponse indicating success or failure
        """
        if self.mock_mode:
            for workflow in self._mock_data["workflows"]:
                if workflow["dataset_name"] == dataset_name:
                    if workflow["paused"]:
                        return ServerResponse(
                            success=False,
                            error=f"Workflow '{dataset_name}' is already paused",
                            status_code=400,
                        )

                    workflow["paused"] = True
                    return ServerResponse(
                        success=True,
                        data={
                            "dataset_name": dataset_name,
                            "paused": True,
                            "message": f"Workflow '{dataset_name}' paused successfully",
                        },
                    )

            return ServerResponse(
                success=False,
                error=f"Workflow for dataset '{dataset_name}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_unpause(self, dataset_name: str) -> ServerResponse:
        """Unpause a workflow (enable scheduled runs).

        Args:
            dataset_name: Name of the dataset workflow to unpause

        Returns:
            ServerResponse indicating success or failure
        """
        if self.mock_mode:
            for workflow in self._mock_data["workflows"]:
                if workflow["dataset_name"] == dataset_name:
                    if not workflow["paused"]:
                        return ServerResponse(
                            success=False,
                            error=f"Workflow '{dataset_name}' is not paused",
                            status_code=400,
                        )

                    workflow["paused"] = False
                    return ServerResponse(
                        success=True,
                        data={
                            "dataset_name": dataset_name,
                            "paused": False,
                            "message": f"Workflow '{dataset_name}' unpaused successfully",
                        },
                    )

            return ServerResponse(
                success=False,
                error=f"Workflow for dataset '{dataset_name}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_register(
        self,
        dataset_name: str,
        cron: str,
        *,
        timezone: str = "UTC",
        enabled: bool = True,
        retry_max_attempts: int = 1,
        retry_delay_seconds: int = 300,
        force: bool = False,
    ) -> ServerResponse:
        """Register a local Dataset as MANUAL workflow.

        Uploads the Dataset Spec to S3 manual/ path and registers
        the schedule with Airflow via Basecamp Server.

        Args:
            dataset_name: Fully qualified dataset name
            cron: Cron expression (5-field format, e.g., "0 9 * * *")
            timezone: IANA timezone (default: "UTC")
            enabled: Whether to enable schedule immediately (default: True)
            retry_max_attempts: Max retry attempts on failure (default: 1)
            retry_delay_seconds: Delay between retries in seconds (default: 300)
            force: If True, overwrite existing MANUAL registration (default: False)

        Returns:
            ServerResponse with registered workflow info
        """
        if self.mock_mode:
            # Check if a CODE workflow already exists (simulate permission error)
            for workflow in self._mock_data["workflows"]:
                if (
                    workflow["dataset_name"] == dataset_name
                    and workflow["source"] == WorkflowSource.CODE.value
                ):
                    return ServerResponse(
                        success=False,
                        error=f"Cannot register: CODE workflow exists for '{dataset_name}'",
                        status_code=403,
                    )

            # Check if MANUAL workflow exists and force is not set
            for workflow in self._mock_data["workflows"]:
                if (
                    workflow["dataset_name"] == dataset_name
                    and workflow["source"] == WorkflowSource.MANUAL.value
                    and not force
                ):
                    return ServerResponse(
                        success=False,
                        error=f"Workflow for '{dataset_name}' already exists. Use --force to overwrite.",
                        status_code=409,
                    )

            # Calculate next run time
            next_run = None
            if enabled:
                next_run = (datetime.now() + timedelta(hours=24)).isoformat()

            workflow_info = {
                "dataset_name": dataset_name,
                "source_type": "manual",
                "status": "active" if enabled else "paused",
                "cron": cron,
                "timezone": timezone,
                "next_run": next_run,
                "retry_max_attempts": retry_max_attempts,
                "retry_delay_seconds": retry_delay_seconds,
            }

            return ServerResponse(
                success=True,
                data=workflow_info,
            )

        # Real implementation would call Basecamp Server API
        # POST /api/v1/workflows/register
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def workflow_unregister(self, dataset_name: str) -> ServerResponse:
        """Unregister a MANUAL workflow.

        Removes the workflow from S3 manual/ path and unschedules from Airflow.
        Only MANUAL workflows can be unregistered via CLI/API.

        Args:
            dataset_name: Fully qualified dataset name

        Returns:
            ServerResponse indicating success or failure
        """
        if self.mock_mode:
            # Find the workflow
            for i, workflow in enumerate(self._mock_data["workflows"]):
                if workflow["dataset_name"] == dataset_name:
                    # Check if it's a CODE workflow (cannot delete)
                    if workflow["source"] == WorkflowSource.CODE.value:
                        return ServerResponse(
                            success=False,
                            error=f"Cannot unregister CODE workflow '{dataset_name}'. Use Git to remove.",
                            status_code=403,
                        )

                    # Remove the workflow
                    self._mock_data["workflows"].pop(i)
                    return ServerResponse(
                        success=True,
                        data={
                            "dataset_name": dataset_name,
                            "message": f"Workflow '{dataset_name}' unregistered successfully",
                        },
                    )

            return ServerResponse(
                success=False,
                error=f"Workflow for dataset '{dataset_name}' not found",
                status_code=404,
            )

        # Real implementation would call Basecamp Server API
        # DELETE /api/v1/workflows/{dataset_name}
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Catalog operations

    def catalog_list(
        self,
        *,
        project: str | None = None,
        dataset: str | None = None,
        owner: str | None = None,
        team: str | None = None,
        tags: list[str] | None = None,
        limit: int = 50,
        offset: int = 0,
    ) -> ServerResponse:
        """List tables from the catalog.

        Args:
            project: Filter by project name
            dataset: Filter by dataset name
            owner: Filter by owner
            team: Filter by team
            tags: Filter by tags (AND condition)
            limit: Maximum number of results
            offset: Pagination offset

        Returns:
            ServerResponse with list of TableInfo dicts
        """
        if self.mock_mode:
            tables = self._mock_data["catalog_tables"].copy()

            # Apply filters
            if project:
                project_lower = project.lower()
                tables = [
                    t for t in tables
                    if t["name"].lower().startswith(project_lower + ".")
                ]

            if dataset:
                dataset_lower = dataset.lower()
                tables = [
                    t for t in tables
                    if dataset_lower in t["name"].lower().split(".")[1]
                    if len(t["name"].split(".")) > 1
                ]

            if owner:
                owner_lower = owner.lower()
                tables = [
                    t for t in tables
                    if owner_lower in (t.get("owner") or "").lower()
                ]

            if team:
                team_lower = team.lower()
                tables = [
                    t for t in tables
                    if team_lower in (t.get("team") or "").lower()
                ]

            if tags:
                # AND condition for tags
                for tag in tags:
                    tag_lower = tag.lower()
                    tables = [
                        t for t in tables
                        if any(tag_lower in table_tag.lower() for table_tag in t.get("tags", []))
                    ]

            # Apply pagination
            total = len(tables)
            tables = tables[offset:offset + limit]

            # Return lightweight TableInfo format
            result = [
                {
                    "name": t["name"],
                    "engine": t["engine"],
                    "owner": t.get("owner"),
                    "team": t.get("team"),
                    "tags": t.get("tags", []),
                    "row_count": t.get("row_count"),
                    "last_updated": t.get("last_updated"),
                }
                for t in tables
            ]

            return ServerResponse(
                success=True,
                data=result,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def catalog_search(
        self,
        keyword: str,
        *,
        project: str | None = None,
        limit: int = 20,
    ) -> ServerResponse:
        """Search tables by keyword.

        Searches in table names, column names, descriptions, and tags.

        Args:
            keyword: Search keyword
            project: Optional project filter
            limit: Maximum number of results

        Returns:
            ServerResponse with list of TableInfo dicts
        """
        if self.mock_mode:
            tables = self._mock_data["catalog_tables"].copy()
            keyword_lower = keyword.lower()

            # Filter by project first if specified
            if project:
                project_lower = project.lower()
                tables = [
                    t for t in tables
                    if t["name"].lower().startswith(project_lower + ".")
                ]

            # Search in name, description, columns, and tags
            matching_tables = []
            for t in tables:
                match = False

                # Check table name
                if keyword_lower in t["name"].lower():
                    match = True

                # Check description
                if not match and keyword_lower in (t.get("description") or "").lower():
                    match = True

                # Check column names and descriptions
                if not match:
                    for col in t.get("columns", []):
                        if keyword_lower in col.get("name", "").lower():
                            match = True
                            break
                        if keyword_lower in (col.get("description") or "").lower():
                            match = True
                            break

                # Check tags
                if not match:
                    for tag in t.get("tags", []):
                        if keyword_lower in tag.lower():
                            match = True
                            break

                if match:
                    matching_tables.append(t)

            # Apply limit
            matching_tables = matching_tables[:limit]

            # Return lightweight TableInfo format
            result = [
                {
                    "name": t["name"],
                    "engine": t["engine"],
                    "owner": t.get("owner"),
                    "team": t.get("team"),
                    "tags": t.get("tags", []),
                    "row_count": t.get("row_count"),
                    "last_updated": t.get("last_updated"),
                }
                for t in matching_tables
            ]

            return ServerResponse(
                success=True,
                data=result,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def catalog_get(
        self,
        table_ref: str,
        *,
        include_sample: bool = False,
    ) -> ServerResponse:
        """Get table details from the catalog.

        Args:
            table_ref: Table reference (project.dataset.table)
            include_sample: Whether to include sample data

        Returns:
            ServerResponse with TableDetail dict
        """
        if self.mock_mode:
            table_ref_lower = table_ref.lower()

            for t in self._mock_data["catalog_tables"]:
                if t["name"].lower() == table_ref_lower:
                    result = t.copy()
                    if not include_sample:
                        result["sample_data"] = None
                    else:
                        # Mock sample data with PII masked
                        if t["name"] == "my-project.analytics.users":
                            result["sample_data"] = [
                                {"user_id": "user_001", "email": "***@example.com", "name": "***", "created_at": "2024-01-15T10:30:00Z", "country": "US"},
                                {"user_id": "user_002", "email": "***@example.com", "name": "***", "created_at": "2024-01-14T08:15:00Z", "country": "UK"},
                                {"user_id": "user_003", "email": "***@example.com", "name": "***", "created_at": "2024-01-13T14:20:00Z", "country": "DE"},
                            ]
                        else:
                            result["sample_data"] = []
                    return ServerResponse(success=True, data=result)

            return ServerResponse(
                success=False,
                error=f"Table '{table_ref}' not found.",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def catalog_sample_queries(
        self,
        table_ref: str,
        *,
        limit: int = 5,
    ) -> ServerResponse:
        """Get sample queries for a table.

        Args:
            table_ref: Table reference (project.dataset.table)
            limit: Maximum number of queries to return

        Returns:
            ServerResponse with list of SampleQuery dicts
        """
        if self.mock_mode:
            table_ref_lower = table_ref.lower()

            for t in self._mock_data["catalog_tables"]:
                if t["name"].lower() == table_ref_lower:
                    queries = t.get("sample_queries", [])[:limit]
                    return ServerResponse(success=True, data=queries)

            return ServerResponse(
                success=False,
                error=f"Table '{table_ref}' not found.",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    # Transpile operations

    def transpile_get_rules(self) -> ServerResponse:
        """Fetch transpile rules from server.

        Returns rules for SQL transpilation including table substitutions,
        dialect conversions, and other transformation rules.

        Returns:
            ServerResponse with transpile rules data containing:
            - rules: List of transpile rule objects
            - version: Rule set version identifier
        """
        if self.mock_mode:
            mock_rules = [
                {
                    "id": "rule-001",
                    "type": "table_substitution",
                    "source": "raw.events",
                    "target": "warehouse.events_v2",
                    "enabled": True,
                    "description": "Events table migration",
                },
                {
                    "id": "rule-002",
                    "type": "table_substitution",
                    "source": "analytics.users",
                    "target": "analytics.users_v2",
                    "enabled": True,
                    "description": "Users table v2 migration",
                },
            ]
            return ServerResponse(
                success=True,
                data={"rules": mock_rules, "version": "2025-01-01-001"},
            )

        return ServerResponse(
            success=False,
            error="Not implemented",
            status_code=501,
        )

    def transpile_get_metric_sql(self, metric_name: str) -> ServerResponse:
        """Fetch metric SQL expression from server.

        Retrieves the SQL expression and metadata for a registered metric,
        which can be used for SQL transpilation and analysis.

        Args:
            metric_name: Name of the metric to fetch

        Returns:
            ServerResponse with metric SQL data containing:
            - name: Metric name
            - sql_expression: The SQL expression
            - source_table: Source table for the metric
            - description: Metric description
        """
        if self.mock_mode:
            mock_metrics = {
                "revenue": {
                    "name": "revenue",
                    "sql_expression": "SUM(amount * quantity)",
                    "source_table": "analytics.orders",
                    "description": "Total revenue",
                },
                "total_orders": {
                    "name": "total_orders",
                    "sql_expression": "COUNT(DISTINCT order_id)",
                    "source_table": "analytics.orders",
                    "description": "Total order count",
                },
            }
            if metric_name in mock_metrics:
                return ServerResponse(success=True, data=mock_metrics[metric_name])
            return ServerResponse(
                success=False,
                error=f"Metric '{metric_name}' not found",
                status_code=404,
            )

        return ServerResponse(
            success=False,
            error="Not implemented",
            status_code=501,
        )

    # Query operations

    def _get_mock_queries_by_scope(
        self,
        scope: str,
        account_keyword: str | None,
        sql_pattern: str | None,
    ) -> list[dict[str, Any]]:
        """Filter mock queries based on scope and filters.

        Args:
            scope: Query scope - "my", "system", "user", or "all"
            account_keyword: Optional keyword to filter by account name
            sql_pattern: Optional pattern to filter by SQL query text

        Returns:
            List of filtered query dicts
        """
        queries = self._mock_data.get("queries", []).copy()

        # Filter by scope
        if scope == "my":
            queries = [q for q in queries if q.get("account") == "current_user@company.com"]
        elif scope == "system":
            queries = [q for q in queries if q.get("account_type") == "system"]
        elif scope == "user":
            queries = [q for q in queries if q.get("account_type") == "personal"]
        # "all" returns everything

        # Filter by account keyword
        if account_keyword:
            keyword_lower = account_keyword.lower()
            queries = [
                q for q in queries
                if keyword_lower in q.get("account", "").lower()
            ]

        # Filter by SQL pattern
        if sql_pattern:
            pattern_lower = sql_pattern.lower()
            queries = [
                q for q in queries
                if pattern_lower in q.get("query_preview", "").lower()
            ]

        return queries

    def query_list(
        self,
        *,
        scope: str = "my",
        account_keyword: str | None = None,
        sql_pattern: str | None = None,
        state: str | None = None,
        tags: list[str] | None = None,
        engine: str | None = None,
        since: str | None = None,
        until: str | None = None,
        limit: int = 10,
        offset: int = 0,
    ) -> ServerResponse:
        """List queries with unified scope-based filtering.

        Args:
            scope: Query scope - "my", "system", "user", or "all".
            account_keyword: Filter by account name.
            sql_pattern: Filter by SQL query text content.
            state: Filter by query state.
            tags: Filter by tags (AND logic).
            engine: Filter by query engine.
            since: Start time (ISO8601 or relative).
            until: End time.
            limit: Max results.
            offset: Pagination offset.

        Returns:
            ServerResponse with query list data.
        """
        if self.mock_mode:
            # Filter mock data based on scope
            mock_queries = self._get_mock_queries_by_scope(scope, account_keyword, sql_pattern)

            # Apply additional filters
            if state:
                mock_queries = [q for q in mock_queries if q.get("state") == state]

            if engine:
                mock_queries = [q for q in mock_queries if q.get("engine") == engine]

            if tags:
                # AND logic for tags
                for tag in tags:
                    tag_lower = tag.lower()
                    mock_queries = [
                        q for q in mock_queries
                        if any(tag_lower in t.lower() for t in q.get("tags", []))
                    ]

            # Apply pagination
            total_count = len(mock_queries)
            mock_queries = mock_queries[offset:offset + limit]

            return ServerResponse(
                success=True,
                data={
                    "queries": mock_queries,
                    "total_count": total_count,
                    "has_more": total_count > offset + limit,
                },
            )

        # GET /api/v1/catalog/queries?scope={scope}&account={keyword}&sql={pattern}&...
        params: dict[str, Any] = {
            "scope": scope,
            "limit": limit,
            "offset": offset,
        }
        if account_keyword:
            params["account"] = account_keyword
        if sql_pattern:
            params["sql"] = sql_pattern
        if state:
            params["state"] = state
        if tags:
            params["tags"] = ",".join(tags)
        if engine:
            params["engine"] = engine
        if since:
            params["since"] = since
        if until:
            params["until"] = until

        # TODO: Implement actual HTTP call: self._get("/api/v1/catalog/queries", params=params)
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def query_get(
        self,
        query_id: str,
        *,
        include_full_query: bool = False,
    ) -> ServerResponse:
        """Get detailed query metadata.

        Args:
            query_id: The query ID to retrieve.
            include_full_query: Include complete query text (not truncated).

        Returns:
            ServerResponse with query detail data.
        """
        if self.mock_mode:
            # Find query in mock data
            for query in self._mock_data.get("queries", []):
                if query.get("query_id") == query_id:
                    # Build detailed response
                    detail = query.copy()

                    # Add additional detail fields for mock
                    detail.update({
                        "queue_time_seconds": 0.2,
                        "bytes_processed": 1200000000,  # 1.2 GB
                        "bytes_billed": 1200000000,
                        "slot_time_seconds": 45.0,
                        "rows_affected": 50000,
                        "tables_used": [
                            {"name": "analytics.raw_events", "operation": "read", "alias": None},
                            {"name": "analytics.users", "operation": "read", "alias": "u"},
                            {"name": "analytics.daily_metrics", "operation": "write", "alias": None},
                        ],
                    })

                    if include_full_query:
                        detail["query_text"] = (
                            "SELECT user_id, COUNT(*) as event_count\n"
                            "FROM analytics.raw_events e\n"
                            "JOIN analytics.users u ON e.user_id = u.id\n"
                            "WHERE event_date = '2026-01-01'\n"
                            "GROUP BY user_id\n"
                            "ORDER BY event_count DESC\n"
                            "LIMIT 1000"
                        )

                    return ServerResponse(success=True, data=detail)

            return ServerResponse(
                success=False,
                error=f"Query '{query_id}' not found",
                status_code=404,
            )

        # TODO: Implement actual HTTP call: GET /api/v1/catalog/queries/{query_id}
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )

    def query_cancel(
        self,
        query_id: str | None = None,
        *,
        user: str | None = None,
        dry_run: bool = False,
    ) -> ServerResponse:
        """Cancel running query(s).

        Args:
            query_id: Specific query ID to cancel.
            user: Account name to cancel all running queries for.
            dry_run: If True, return what would be cancelled without executing.

        Returns:
            ServerResponse with cancelled query details.
        """
        if self.mock_mode:
            if query_id:
                # Find specific query
                for query in self._mock_data.get("queries", []):
                    if query.get("query_id") == query_id:
                        # Check if already completed
                        if query.get("state") in ("success", "failed", "cancelled"):
                            return ServerResponse(
                                success=True,
                                data={
                                    "cancelled_count": 0,
                                    "queries": [],
                                    "warning": f"Query '{query_id}' already completed (state: {query.get('state')})",
                                },
                            )

                        # Mock cancellation
                        cancelled_query = query.copy()
                        cancelled_query["state"] = "cancelled"
                        return ServerResponse(
                            success=True,
                            data={
                                "cancelled_count": 0 if dry_run else 1,
                                "queries": [cancelled_query],
                            },
                        )

                return ServerResponse(
                    success=False,
                    error=f"Query '{query_id}' not found",
                    status_code=404,
                )

            if user:
                # Find all running queries for user
                running_states = ("pending", "running")
                mock_running = [
                    q for q in self._mock_data.get("queries", [])
                    if q.get("account") == user and q.get("state") in running_states
                ]

                if not mock_running:
                    return ServerResponse(
                        success=True,
                        data={
                            "cancelled_count": 0,
                            "queries": [],
                            "message": f"No running queries found for account '{user}'",
                        },
                    )

                # Mock cancellation
                cancelled_queries = []
                for q in mock_running:
                    cancelled = q.copy()
                    cancelled["state"] = "cancelled"
                    cancelled_queries.append(cancelled)

                return ServerResponse(
                    success=True,
                    data={
                        "cancelled_count": 0 if dry_run else len(cancelled_queries),
                        "queries": cancelled_queries,
                    },
                )

            return ServerResponse(
                success=False,
                error="Must specify either query_id or user",
                status_code=400,
            )

        # TODO: Implement actual HTTP call:
        # - POST /api/v1/catalog/queries/{query_id}/cancel (for specific query)
        # - POST /api/v1/catalog/queries/cancel?user={account} (for all user queries)
        return ServerResponse(
            success=False,
            error="Real API not implemented yet",
            status_code=501,
        )


def create_client(
    url: str | None = None,
    timeout: int = 30,
    api_key: str | None = None,
    mock_mode: bool = True,
) -> BasecampClient:
    """Create a Basecamp client.

    Args:
        url: Server URL (required unless mock_mode is True)
        timeout: Request timeout in seconds
        api_key: Optional API key for authentication
        mock_mode: If True, use mock responses

    Returns:
        BasecampClient instance
    """
    config = ServerConfig(
        url=url or "http://localhost:8081",
        timeout=timeout,
        api_key=api_key,
    )
    return BasecampClient(config, mock_mode=mock_mode)
