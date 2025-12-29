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
