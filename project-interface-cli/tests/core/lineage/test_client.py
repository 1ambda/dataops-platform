"""Tests for the LineageClient.

This module provides comprehensive tests for:
- LineageClient: get_lineage, get_upstream, get_downstream
- LineageNode and LineageResult dataclasses
- LineageClientError exception handling
"""

from __future__ import annotations

from unittest.mock import Mock

import pytest

from dli.core.client import ServerResponse
from dli.core.lineage import (
    LineageDirection,
    LineageEdge,
    LineageNode,
    LineageResult,
)
from dli.core.lineage.client import LineageClient, LineageClientError


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def mock_basecamp_client() -> Mock:
    """Create a mock BasecampClient."""
    client = Mock()
    client.mock_mode = True
    client.base_url = "http://localhost:8081"
    return client


@pytest.fixture
def lineage_client(mock_basecamp_client: Mock) -> LineageClient:
    """Create a LineageClient with mock BasecampClient."""
    return LineageClient(mock_basecamp_client)


@pytest.fixture
def mock_lineage_response_success() -> dict:
    """Mock successful lineage response data."""
    return {
        "root": {
            "name": "iceberg.analytics.daily_clicks",
            "type": "Dataset",
            "owner": "engineer@example.com",
            "team": "@data-eng",
            "description": "Daily click aggregations",
            "tags": ["feed", "daily"],
        },
        "nodes": [
            {
                "name": "iceberg.raw.clicks",
                "type": "Dataset",
                "owner": "ingestion@example.com",
                "team": "@data-platform",
                "description": "Raw click data",
                "tags": ["raw", "source"],
                "depth": -1,
            },
            {
                "name": "iceberg.reporting.clicks_report",
                "type": "Dataset",
                "owner": "analyst@example.com",
                "team": "@analytics",
                "description": "Click reporting view",
                "tags": ["reporting", "bi"],
                "depth": 1,
            },
        ],
        "edges": [
            {
                "source": "iceberg.raw.clicks",
                "target": "iceberg.analytics.daily_clicks",
                "edge_type": "direct",
            },
            {
                "source": "iceberg.analytics.daily_clicks",
                "target": "iceberg.reporting.clicks_report",
                "edge_type": "direct",
            },
        ],
        "total_upstream": 1,
        "total_downstream": 1,
    }


@pytest.fixture
def mock_lineage_response_with_depth() -> dict:
    """Mock lineage response with multiple depth levels."""
    return {
        "root": {
            "name": "iceberg.analytics.daily_clicks",
            "type": "Dataset",
            "owner": "engineer@example.com",
            "team": "@data-eng",
            "description": "Daily click aggregations",
            "tags": ["feed", "daily"],
        },
        "nodes": [
            {
                "name": "iceberg.raw.clicks",
                "type": "Dataset",
                "owner": "ingestion@example.com",
                "team": "@data-platform",
                "depth": -1,
            },
            {
                "name": "external.kafka.clicks_events",
                "type": "External",
                "owner": "streaming@example.com",
                "team": "@streaming",
                "depth": -2,
            },
            {
                "name": "iceberg.reporting.clicks_report",
                "type": "Dataset",
                "depth": 1,
            },
            {
                "name": "iceberg.metrics.clicks_kpi",
                "type": "Metric",
                "depth": 2,
            },
        ],
        "edges": [
            {"source": "external.kafka.clicks_events", "target": "iceberg.raw.clicks"},
            {"source": "iceberg.raw.clicks", "target": "iceberg.analytics.daily_clicks"},
            {"source": "iceberg.analytics.daily_clicks", "target": "iceberg.reporting.clicks_report"},
            {"source": "iceberg.reporting.clicks_report", "target": "iceberg.metrics.clicks_kpi"},
        ],
        "total_upstream": 2,
        "total_downstream": 2,
    }


# =============================================================================
# LineageNode Tests
# =============================================================================


class TestLineageNode:
    """Tests for LineageNode dataclass."""

    def test_create_node_with_required_fields(self) -> None:
        """Test creating a node with only required fields."""
        node = LineageNode(name="iceberg.analytics.daily_clicks")

        assert node.name == "iceberg.analytics.daily_clicks"
        assert node.type == "Dataset"  # default
        assert node.owner is None
        assert node.team is None
        assert node.description is None
        assert node.tags == []
        assert node.depth == 0

    def test_create_node_with_all_fields(self) -> None:
        """Test creating a node with all fields."""
        node = LineageNode(
            name="iceberg.analytics.daily_clicks",
            type="Dataset",
            owner="engineer@example.com",
            team="@data-eng",
            description="Daily click aggregations",
            tags=["feed", "daily"],
            depth=-1,
        )

        assert node.name == "iceberg.analytics.daily_clicks"
        assert node.type == "Dataset"
        assert node.owner == "engineer@example.com"
        assert node.team == "@data-eng"
        assert node.description == "Daily click aggregations"
        assert node.tags == ["feed", "daily"]
        assert node.depth == -1

    def test_node_type_variations(self) -> None:
        """Test different node types."""
        dataset_node = LineageNode(name="test.dataset", type="Dataset")
        metric_node = LineageNode(name="test.metric", type="Metric")
        external_node = LineageNode(name="test.external", type="External")

        assert dataset_node.type == "Dataset"
        assert metric_node.type == "Metric"
        assert external_node.type == "External"

    def test_node_depth_values(self) -> None:
        """Test various depth values (negative for upstream, positive for downstream)."""
        upstream_node = LineageNode(name="upstream", depth=-2)
        root_node = LineageNode(name="root", depth=0)
        downstream_node = LineageNode(name="downstream", depth=2)

        assert upstream_node.depth == -2
        assert root_node.depth == 0
        assert downstream_node.depth == 2


# =============================================================================
# LineageEdge Tests
# =============================================================================


class TestLineageEdge:
    """Tests for LineageEdge dataclass."""

    def test_create_edge_with_required_fields(self) -> None:
        """Test creating an edge with only required fields."""
        edge = LineageEdge(
            source="iceberg.raw.clicks",
            target="iceberg.analytics.daily_clicks",
        )

        assert edge.source == "iceberg.raw.clicks"
        assert edge.target == "iceberg.analytics.daily_clicks"
        assert edge.edge_type == "direct"  # default

    def test_create_edge_with_all_fields(self) -> None:
        """Test creating an edge with all fields."""
        edge = LineageEdge(
            source="iceberg.raw.clicks",
            target="iceberg.analytics.daily_clicks",
            edge_type="indirect",
        )

        assert edge.source == "iceberg.raw.clicks"
        assert edge.target == "iceberg.analytics.daily_clicks"
        assert edge.edge_type == "indirect"

    def test_edge_type_variations(self) -> None:
        """Test different edge types."""
        direct_edge = LineageEdge(source="a", target="b", edge_type="direct")
        indirect_edge = LineageEdge(source="a", target="b", edge_type="indirect")

        assert direct_edge.edge_type == "direct"
        assert indirect_edge.edge_type == "indirect"


# =============================================================================
# LineageResult Tests
# =============================================================================


class TestLineageResult:
    """Tests for LineageResult dataclass."""

    def test_create_result_with_required_fields(self) -> None:
        """Test creating a result with only required fields."""
        root = LineageNode(name="iceberg.analytics.daily_clicks")
        result = LineageResult(root=root)

        assert result.root == root
        assert result.nodes == []
        assert result.edges == []
        assert result.direction == LineageDirection.BOTH
        assert result.max_depth == -1
        assert result.total_upstream == 0
        assert result.total_downstream == 0

    def test_create_result_with_all_fields(self) -> None:
        """Test creating a result with all fields."""
        root = LineageNode(name="iceberg.analytics.daily_clicks")
        nodes = [
            LineageNode(name="iceberg.raw.clicks", depth=-1),
            LineageNode(name="iceberg.reporting.clicks_report", depth=1),
        ]
        edges = [
            LineageEdge(source="iceberg.raw.clicks", target="iceberg.analytics.daily_clicks"),
            LineageEdge(source="iceberg.analytics.daily_clicks", target="iceberg.reporting.clicks_report"),
        ]

        result = LineageResult(
            root=root,
            nodes=nodes,
            edges=edges,
            direction=LineageDirection.BOTH,
            max_depth=3,
            total_upstream=1,
            total_downstream=1,
        )

        assert result.root == root
        assert len(result.nodes) == 2
        assert len(result.edges) == 2
        assert result.direction == LineageDirection.BOTH
        assert result.max_depth == 3
        assert result.total_upstream == 1
        assert result.total_downstream == 1

    def test_upstream_nodes_property(self) -> None:
        """Test the upstream_nodes property."""
        root = LineageNode(name="iceberg.analytics.daily_clicks")
        upstream_node = LineageNode(name="iceberg.raw.clicks", depth=-1)
        downstream_node = LineageNode(name="iceberg.reporting.clicks_report", depth=1)
        nodes = [upstream_node, downstream_node]
        edges = [
            LineageEdge(source="iceberg.raw.clicks", target="iceberg.analytics.daily_clicks"),
            LineageEdge(source="iceberg.analytics.daily_clicks", target="iceberg.reporting.clicks_report"),
        ]

        result = LineageResult(root=root, nodes=nodes, edges=edges)

        upstream_nodes = result.upstream_nodes
        assert len(upstream_nodes) == 1
        assert upstream_nodes[0].name == "iceberg.raw.clicks"

    def test_downstream_nodes_property(self) -> None:
        """Test the downstream_nodes property."""
        root = LineageNode(name="iceberg.analytics.daily_clicks")
        upstream_node = LineageNode(name="iceberg.raw.clicks", depth=-1)
        downstream_node = LineageNode(name="iceberg.reporting.clicks_report", depth=1)
        nodes = [upstream_node, downstream_node]
        edges = [
            LineageEdge(source="iceberg.raw.clicks", target="iceberg.analytics.daily_clicks"),
            LineageEdge(source="iceberg.analytics.daily_clicks", target="iceberg.reporting.clicks_report"),
        ]

        result = LineageResult(root=root, nodes=nodes, edges=edges)

        downstream_nodes = result.downstream_nodes
        assert len(downstream_nodes) == 1
        assert downstream_nodes[0].name == "iceberg.reporting.clicks_report"


# =============================================================================
# LineageDirection Tests
# =============================================================================


class TestLineageDirection:
    """Tests for LineageDirection enum."""

    def test_direction_values(self) -> None:
        """Test direction enum values."""
        assert LineageDirection.UPSTREAM.value == "upstream"
        assert LineageDirection.DOWNSTREAM.value == "downstream"
        assert LineageDirection.BOTH.value == "both"

    def test_direction_from_string(self) -> None:
        """Test creating direction from string."""
        assert LineageDirection("upstream") == LineageDirection.UPSTREAM
        assert LineageDirection("downstream") == LineageDirection.DOWNSTREAM
        assert LineageDirection("both") == LineageDirection.BOTH


# =============================================================================
# LineageClientError Tests
# =============================================================================


class TestLineageClientError:
    """Tests for LineageClientError exception."""

    def test_error_with_message_only(self) -> None:
        """Test creating an error with only a message."""
        error = LineageClientError("Failed to get lineage")

        assert str(error) == "Failed to get lineage"
        assert error.message == "Failed to get lineage"
        assert error.status_code == 500  # default

    def test_error_with_status_code(self) -> None:
        """Test creating an error with a status code."""
        error = LineageClientError("Resource not found", status_code=404)

        assert str(error) == "Resource not found"
        assert error.message == "Resource not found"
        assert error.status_code == 404

    def test_error_can_be_raised(self) -> None:
        """Test that the error can be raised and caught."""
        with pytest.raises(LineageClientError) as exc_info:
            raise LineageClientError("Test error", status_code=500)

        assert exc_info.value.message == "Test error"
        assert exc_info.value.status_code == 500


# =============================================================================
# LineageClient Tests
# =============================================================================


class TestLineageClient:
    """Tests for LineageClient."""

    def test_init(self, mock_basecamp_client: Mock) -> None:
        """Test LineageClient initialization."""
        client = LineageClient(mock_basecamp_client)

        assert client.client == mock_basecamp_client

    def test_get_lineage_success(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
        mock_lineage_response_success: dict,
    ) -> None:
        """Test successful lineage retrieval."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data=mock_lineage_response_success,
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        # Verify the client was called correctly
        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="both",
            depth=-1,
        )

        # Verify the result
        assert isinstance(result, LineageResult)
        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.root.type == "Dataset"
        assert result.root.owner == "engineer@example.com"
        assert result.direction == LineageDirection.BOTH
        assert result.max_depth == -1
        assert result.total_upstream == 1
        assert result.total_downstream == 1

        # Verify nodes
        assert len(result.nodes) == 2
        node_names = [n.name for n in result.nodes]
        assert "iceberg.raw.clicks" in node_names
        assert "iceberg.reporting.clicks_report" in node_names

        # Verify edges
        assert len(result.edges) == 2

    def test_get_lineage_with_direction_upstream(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval with upstream direction."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.raw.clicks", "depth": -1}],
                "edges": [{"source": "iceberg.raw.clicks", "target": "iceberg.analytics.daily_clicks"}],
                "total_upstream": 1,
                "total_downstream": 0,
            },
            status_code=200,
        )

        result = lineage_client.get_lineage(
            "iceberg.analytics.daily_clicks",
            direction="upstream",
        )

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="upstream",
            depth=-1,
        )

        assert result.direction == LineageDirection.UPSTREAM
        assert result.total_upstream == 1
        assert result.total_downstream == 0

    def test_get_lineage_with_direction_downstream(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval with downstream direction."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.reporting.clicks_report", "depth": 1}],
                "edges": [{"source": "iceberg.analytics.daily_clicks", "target": "iceberg.reporting.clicks_report"}],
                "total_upstream": 0,
                "total_downstream": 1,
            },
            status_code=200,
        )

        result = lineage_client.get_lineage(
            "iceberg.analytics.daily_clicks",
            direction="downstream",
        )

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="downstream",
            depth=-1,
        )

        assert result.direction == LineageDirection.DOWNSTREAM
        assert result.total_upstream == 0
        assert result.total_downstream == 1

    def test_get_lineage_with_depth_limit(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval with depth limit."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.raw.clicks", "depth": -1}],
                "edges": [{"source": "iceberg.raw.clicks", "target": "iceberg.analytics.daily_clicks"}],
                "total_upstream": 1,
                "total_downstream": 0,
            },
            status_code=200,
        )

        result = lineage_client.get_lineage(
            "iceberg.analytics.daily_clicks",
            depth=1,
        )

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="both",
            depth=1,
        )

        assert result.max_depth == 1

    def test_get_lineage_with_unlimited_depth(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
        mock_lineage_response_with_depth: dict,
    ) -> None:
        """Test lineage retrieval with unlimited depth (-1)."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data=mock_lineage_response_with_depth,
            status_code=200,
        )

        result = lineage_client.get_lineage(
            "iceberg.analytics.daily_clicks",
            depth=-1,
        )

        assert result.max_depth == -1
        assert len(result.nodes) == 4  # 2 upstream + 2 downstream
        assert result.total_upstream == 2
        assert result.total_downstream == 2

    def test_get_lineage_failure_resource_not_found(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval failure when resource not found."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=False,
            error="Resource 'iceberg.nonexistent.table' not found",
            status_code=404,
        )

        with pytest.raises(LineageClientError) as exc_info:
            lineage_client.get_lineage("iceberg.nonexistent.table")

        assert "not found" in exc_info.value.message
        assert exc_info.value.status_code == 404

    def test_get_lineage_failure_server_error(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval failure on server error."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=False,
            error="Internal server error",
            status_code=500,
        )

        with pytest.raises(LineageClientError) as exc_info:
            lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert exc_info.value.message == "Internal server error"
        assert exc_info.value.status_code == 500

    def test_get_lineage_failure_no_error_message(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test lineage retrieval failure with no error message."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=False,
            error=None,
            status_code=500,
        )

        with pytest.raises(LineageClientError) as exc_info:
            lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert "Failed to get lineage for 'iceberg.analytics.daily_clicks'" in exc_info.value.message

    def test_get_upstream_success(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test get_upstream convenience method."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.raw.clicks", "depth": -1}],
                "edges": [{"source": "iceberg.raw.clicks", "target": "iceberg.analytics.daily_clicks"}],
                "total_upstream": 1,
                "total_downstream": 0,
            },
            status_code=200,
        )

        result = lineage_client.get_upstream("iceberg.analytics.daily_clicks")

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="upstream",
            depth=-1,
        )

        assert result.direction == LineageDirection.UPSTREAM

    def test_get_upstream_with_depth(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test get_upstream with depth parameter."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [],
                "edges": [],
            },
            status_code=200,
        )

        lineage_client.get_upstream("iceberg.analytics.daily_clicks", depth=2)

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="upstream",
            depth=2,
        )

    def test_get_downstream_success(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test get_downstream convenience method."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.reporting.clicks_report", "depth": 1}],
                "edges": [{"source": "iceberg.analytics.daily_clicks", "target": "iceberg.reporting.clicks_report"}],
                "total_upstream": 0,
                "total_downstream": 1,
            },
            status_code=200,
        )

        result = lineage_client.get_downstream("iceberg.analytics.daily_clicks")

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="downstream",
            depth=-1,
        )

        assert result.direction == LineageDirection.DOWNSTREAM

    def test_get_downstream_with_depth(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test get_downstream with depth parameter."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [],
                "edges": [],
            },
            status_code=200,
        )

        lineage_client.get_downstream("iceberg.analytics.daily_clicks", depth=3)

        mock_basecamp_client.get_lineage.assert_called_once_with(
            resource_name="iceberg.analytics.daily_clicks",
            direction="downstream",
            depth=3,
        )


# =============================================================================
# LineageClient Parse Response Tests
# =============================================================================


class TestLineageClientParseResponse:
    """Tests for LineageClient._parse_lineage_response method."""

    def test_parse_response_with_none_data(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test parsing response when data is None."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data=None,
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.nodes == []
        assert result.edges == []

    def test_parse_response_with_list_data(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test parsing response when data is a list (nodes only)."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data=[
                {"name": "iceberg.raw.clicks", "type": "Dataset", "depth": -1},
                {"name": "iceberg.reporting.clicks_report", "type": "Dataset", "depth": 1},
            ],
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert len(result.nodes) == 2
        assert result.edges == []

    def test_parse_response_with_minimal_node_data(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test parsing response with minimal node data (missing optional fields)."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [{"name": "iceberg.raw.clicks"}],
                "edges": [{"source": "iceberg.raw.clicks", "target": "iceberg.analytics.daily_clicks"}],
            },
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.root.type == "Dataset"  # default
        assert result.root.owner is None
        assert result.root.tags == []

        assert len(result.nodes) == 1
        assert result.nodes[0].name == "iceberg.raw.clicks"
        assert result.nodes[0].type == "Dataset"  # default
        assert result.nodes[0].depth == 0  # default

    def test_parse_response_with_empty_nodes_and_edges(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test parsing response with no nodes or edges."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.isolated_table"},
            },
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.isolated_table")

        assert result.root.name == "iceberg.analytics.isolated_table"
        assert result.nodes == []
        assert result.edges == []
        assert result.total_upstream == 0
        assert result.total_downstream == 0

    def test_parse_response_preserves_all_node_fields(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
        mock_lineage_response_success: dict,
    ) -> None:
        """Test that all node fields are correctly parsed."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data=mock_lineage_response_success,
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        # Check root node
        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.root.type == "Dataset"
        assert result.root.owner == "engineer@example.com"
        assert result.root.team == "@data-eng"
        assert result.root.description == "Daily click aggregations"
        assert result.root.tags == ["feed", "daily"]
        assert result.root.depth == 0

        # Check upstream node
        upstream_node = next(n for n in result.nodes if n.name == "iceberg.raw.clicks")
        assert upstream_node.type == "Dataset"
        assert upstream_node.owner == "ingestion@example.com"
        assert upstream_node.team == "@data-platform"
        assert upstream_node.depth == -1

    def test_parse_response_preserves_all_edge_fields(
        self,
        lineage_client: LineageClient,
        mock_basecamp_client: Mock,
    ) -> None:
        """Test that all edge fields are correctly parsed."""
        mock_basecamp_client.get_lineage.return_value = ServerResponse(
            success=True,
            data={
                "root": {"name": "iceberg.analytics.daily_clicks"},
                "nodes": [],
                "edges": [
                    {
                        "source": "iceberg.raw.clicks",
                        "target": "iceberg.analytics.daily_clicks",
                        "edge_type": "indirect",
                    }
                ],
            },
            status_code=200,
        )

        result = lineage_client.get_lineage("iceberg.analytics.daily_clicks")

        assert len(result.edges) == 1
        assert result.edges[0].source == "iceberg.raw.clicks"
        assert result.edges[0].target == "iceberg.analytics.daily_clicks"
        assert result.edges[0].edge_type == "indirect"
