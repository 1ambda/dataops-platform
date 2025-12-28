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
from typing import Any
import logging

logger = logging.getLogger(__name__)


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
