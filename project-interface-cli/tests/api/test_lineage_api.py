"""Tests for dli.api.lineage module.

Covers:
- LineageAPI initialization with context
- Mock mode operations
- Lineage query operations (get_lineage, get_upstream, get_downstream)
- Error handling (not found, timeout)
- Result model properties and validation
"""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from dli.api import LineageAPI
from dli.core.client import BasecampClient
from dli.core.lineage import (
    LineageDirection,
    LineageEdge,
    LineageNode,
    LineageResult,
)
from dli.exceptions import (
    ConfigurationError,
    LineageError,
    LineageNotFoundError,
    LineageTimeoutError,
)
from dli.models.common import ExecutionContext, ExecutionMode

# === Fixtures ===


@pytest.fixture
def mock_context() -> ExecutionContext:
    """Create mock mode context."""
    return ExecutionContext(execution_mode=ExecutionMode.MOCK)


@pytest.fixture
def server_context() -> ExecutionContext:
    """Create server mode context."""
    return ExecutionContext(
        execution_mode=ExecutionMode.SERVER,
        server_url="http://localhost:8080",
    )


@pytest.fixture
def mock_api(mock_context: ExecutionContext) -> LineageAPI:
    """Create LineageAPI in mock mode."""
    return LineageAPI(context=mock_context)


# === Test Classes ===


class TestLineageAPIInit:
    """Tests for LineageAPI initialization."""

    def test_init_with_default_context(self) -> None:
        """Test initialization with default context."""
        api = LineageAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_custom_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = LineageAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_injected_client(self) -> None:
        """Test initialization with injected client."""
        mock_client = MagicMock(spec=BasecampClient)
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = LineageAPI(context=ctx, client=mock_client)

        # Access private method to get client
        client = api._get_client()

        assert client is mock_client

    def test_init_with_server_context(self) -> None:
        """Test initialization with server context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx)

        assert api.context.execution_mode == ExecutionMode.SERVER
        assert api.context.server_url == "http://localhost:8080"

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(
            server_url="https://test.com", execution_mode=ExecutionMode.MOCK
        )
        api = LineageAPI(context=ctx)

        result = repr(api)

        assert "LineageAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = LineageAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _client should be None before any operation
        assert api._client is None


class TestLineageAPIGetLineage:
    """Tests for LineageAPI.get_lineage() method."""

    def test_get_lineage_mock_mode(self, mock_api: LineageAPI) -> None:
        """Test get_lineage returns LineageResult in mock mode."""
        result = mock_api.get_lineage("iceberg.analytics.daily_clicks")

        assert result is not None
        assert isinstance(result, LineageResult)
        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.direction == LineageDirection.BOTH
        assert result.total_upstream >= 0
        assert result.total_downstream >= 0

    def test_get_lineage_both_directions(self, mock_api: LineageAPI) -> None:
        """Test get_lineage with both directions (default)."""
        result = mock_api.get_lineage(
            "iceberg.analytics.daily_clicks",
            direction="both",
        )

        assert result.direction == LineageDirection.BOTH
        # Both directions should have data in mock
        assert result.total_upstream >= 0
        assert result.total_downstream >= 0
        # Should have both upstream and downstream nodes
        assert len(result.nodes) >= 0

    def test_get_lineage_with_depth(self, mock_api: LineageAPI) -> None:
        """Test get_lineage with depth limit."""
        result = mock_api.get_lineage(
            "iceberg.analytics.daily_clicks",
            depth=2,
        )

        assert result is not None
        assert result.max_depth == 2

    def test_get_lineage_not_found_raises_error(self) -> None:
        """Test get_lineage raises error when lineage not found."""
        # Create mock client that returns 404
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = False
        mock_response.status_code = 404
        mock_response.error = "Not found"
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        with pytest.raises(LineageNotFoundError) as exc_info:
            api.get_lineage("non_existent.table")

        assert "non_existent.table" in exc_info.value.message
        assert exc_info.value.resource_name == "non_existent.table"

    def test_get_lineage_upstream_only(self, mock_api: LineageAPI) -> None:
        """Test get_lineage with upstream direction only."""
        result = mock_api.get_lineage(
            "iceberg.analytics.daily_clicks",
            direction="upstream",
        )

        assert result.direction == LineageDirection.UPSTREAM
        assert result.total_upstream >= 0

    def test_get_lineage_downstream_only(self, mock_api: LineageAPI) -> None:
        """Test get_lineage with downstream direction only."""
        result = mock_api.get_lineage(
            "iceberg.analytics.daily_clicks",
            direction="downstream",
        )

        assert result.direction == LineageDirection.DOWNSTREAM
        assert result.total_downstream >= 0

    def test_get_lineage_returns_root_node(self, mock_api: LineageAPI) -> None:
        """Test get_lineage returns proper root node."""
        result = mock_api.get_lineage("iceberg.analytics.daily_clicks")

        assert result.root is not None
        assert result.root.name == "iceberg.analytics.daily_clicks"
        assert result.root.type == "Dataset"
        assert result.root.depth == 0


class TestLineageAPIGetUpstream:
    """Tests for LineageAPI.get_upstream() method."""

    def test_get_upstream_mock_mode(self, mock_api: LineageAPI) -> None:
        """Test get_upstream returns LineageResult in mock mode."""
        result = mock_api.get_upstream("iceberg.analytics.daily_clicks")

        assert result is not None
        assert isinstance(result, LineageResult)
        assert result.direction == LineageDirection.UPSTREAM
        assert result.root.name == "iceberg.analytics.daily_clicks"

    def test_get_upstream_with_depth(self, mock_api: LineageAPI) -> None:
        """Test get_upstream with depth limit."""
        result = mock_api.get_upstream(
            "iceberg.analytics.daily_clicks",
            depth=3,
        )

        assert result.max_depth == 3
        assert result.direction == LineageDirection.UPSTREAM

    def test_get_upstream_empty_result(self) -> None:
        """Test get_upstream with no upstream dependencies."""
        # Create mock client that returns empty result
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "root": {"name": "raw.source_table"},
            "nodes": [],
            "edges": [],
            "total_upstream": 0,
            "total_downstream": 0,
        }
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        result = api.get_upstream("raw.source_table")

        assert result.total_upstream == 0
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_get_upstream_returns_upstream_nodes(self, mock_api: LineageAPI) -> None:
        """Test that get_upstream returns only upstream nodes."""
        result = mock_api.get_upstream("iceberg.analytics.daily_clicks")

        # All nodes should have depth < 0 or be upstream dependencies
        for node in result.nodes:
            # In mock mode, upstream nodes have depth -1
            assert node.depth <= 0


class TestLineageAPIGetDownstream:
    """Tests for LineageAPI.get_downstream() method."""

    def test_get_downstream_mock_mode(self, mock_api: LineageAPI) -> None:
        """Test get_downstream returns LineageResult in mock mode."""
        result = mock_api.get_downstream("iceberg.analytics.daily_clicks")

        assert result is not None
        assert isinstance(result, LineageResult)
        assert result.direction == LineageDirection.DOWNSTREAM
        assert result.root.name == "iceberg.analytics.daily_clicks"

    def test_get_downstream_with_depth(self, mock_api: LineageAPI) -> None:
        """Test get_downstream with depth limit."""
        result = mock_api.get_downstream(
            "iceberg.analytics.daily_clicks",
            depth=5,
        )

        assert result.max_depth == 5
        assert result.direction == LineageDirection.DOWNSTREAM

    def test_get_downstream_empty_result(self) -> None:
        """Test get_downstream with no downstream dependencies."""
        # Create mock client that returns empty result
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "root": {"name": "final.report_table"},
            "nodes": [],
            "edges": [],
            "total_upstream": 0,
            "total_downstream": 0,
        }
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        result = api.get_downstream("final.report_table")

        assert result.total_downstream == 0
        assert len(result.nodes) == 0
        assert len(result.edges) == 0

    def test_get_downstream_returns_downstream_nodes(
        self, mock_api: LineageAPI
    ) -> None:
        """Test that get_downstream returns only downstream nodes."""
        result = mock_api.get_downstream("iceberg.analytics.daily_clicks")

        # All nodes should have depth > 0 or be downstream dependencies
        for node in result.nodes:
            # In mock mode, downstream nodes have depth 1
            assert node.depth >= 0


class TestLineageAPIExceptions:
    """Tests for LineageAPI exception handling."""

    def test_lineage_not_found_error(self) -> None:
        """Test LineageNotFoundError is raised when resource not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = False
        mock_response.status_code = 404
        mock_response.error = "Resource not found"
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        with pytest.raises(LineageNotFoundError) as exc_info:
            api.get_lineage("missing.resource")

        assert exc_info.value.resource_name == "missing.resource"
        assert "missing.resource" in exc_info.value.message

    def test_lineage_timeout_error(self) -> None:
        """Test LineageTimeoutError is raised on timeout."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = False
        mock_response.status_code = 408
        mock_response.error = "Request timeout"
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        with pytest.raises(LineageTimeoutError) as exc_info:
            api.get_lineage("large.complex_graph")

        assert "timed out" in exc_info.value.message.lower()
        assert exc_info.value.resource_name == "large.complex_graph"

    def test_lineage_error_base(self) -> None:
        """Test LineageError is raised for general lineage failures."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = False
        mock_response.status_code = 500
        mock_response.error = "Internal server error"
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        with pytest.raises(LineageError) as exc_info:
            api.get_lineage("some.resource")

        assert exc_info.value.resource_name == "some.resource"

    def test_configuration_error_missing_server_url(self) -> None:
        """Test ConfigurationError when server_url is missing in SERVER mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER, server_url=None)
        api = LineageAPI(context=ctx)

        with pytest.raises(ConfigurationError) as exc_info:
            api._get_client()

        assert "server_url required" in exc_info.value.message


class TestLineageAPIServerMode:
    """Tests for LineageAPI in server mode."""

    def test_requires_server_url(self) -> None:
        """Test that server mode requires server_url."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER, server_url=None)
        api = LineageAPI(context=ctx)

        with pytest.raises(ConfigurationError) as exc_info:
            api._get_client()

        assert "server_url required" in exc_info.value.message

    def test_mock_mode_does_not_require_server_url(self) -> None:
        """Test that mock mode doesn't require server_url."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK, server_url=None)
        api = LineageAPI(context=ctx)

        # Should not raise
        client = api._get_client()
        assert client is not None
        assert client.mock_mode is True

    def test_server_mode_creates_client_with_url(self) -> None:
        """Test that server mode creates client with provided URL."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
            api_token="test-token",
        )
        api = LineageAPI(context=ctx)

        client = api._get_client()

        assert client is not None
        assert client.config.url == "http://localhost:8080"


class TestLineageAPIDependencyInjection:
    """Tests for LineageAPI with injected client."""

    def test_uses_injected_client(self) -> None:
        """Test that injected client is used."""
        mock_client = MagicMock()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = LineageAPI(context=ctx, client=mock_client)

        # Access private method to get client
        client = api._get_client()

        assert client is mock_client

    def test_injected_client_used_for_operations(self) -> None:
        """Test that injected client is used for lineage operations."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "root": {"name": "test.dataset"},
            "nodes": [],
            "edges": [],
            "total_upstream": 0,
            "total_downstream": 0,
        }
        mock_client.get_lineage.return_value = mock_response

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = LineageAPI(context=ctx, client=mock_client)

        result = api.get_lineage("test.dataset")

        mock_client.get_lineage.assert_called_once_with(
            resource_name="test.dataset",
            direction="both",
            depth=-1,
        )
        assert result.root.name == "test.dataset"


class TestLineageResultModel:
    """Tests for LineageResult model properties."""

    def test_lineage_result_root_node(self) -> None:
        """Test LineageResult root node."""
        root = LineageNode(
            name="test.dataset",
            type="Dataset",
            depth=0,
        )
        result = LineageResult(
            root=root,
            nodes=[],
            edges=[],
        )

        assert result.root.name == "test.dataset"
        assert result.root.depth == 0

    def test_lineage_result_upstream_nodes_property(self) -> None:
        """Test LineageResult.upstream_nodes property."""
        root = LineageNode(name="mid.table", depth=0)
        upstream = LineageNode(name="raw.source", depth=-1)
        downstream = LineageNode(name="final.report", depth=1)
        edge_up = LineageEdge(source="raw.source", target="mid.table")
        edge_down = LineageEdge(source="mid.table", target="final.report")

        result = LineageResult(
            root=root,
            nodes=[upstream, downstream],
            edges=[edge_up, edge_down],
        )

        upstream_nodes = result.upstream_nodes
        assert len(upstream_nodes) == 1
        assert upstream_nodes[0].name == "raw.source"

    def test_lineage_result_downstream_nodes_property(self) -> None:
        """Test LineageResult.downstream_nodes property."""
        root = LineageNode(name="mid.table", depth=0)
        upstream = LineageNode(name="raw.source", depth=-1)
        downstream = LineageNode(name="final.report", depth=1)
        edge_up = LineageEdge(source="raw.source", target="mid.table")
        edge_down = LineageEdge(source="mid.table", target="final.report")

        result = LineageResult(
            root=root,
            nodes=[upstream, downstream],
            edges=[edge_up, edge_down],
        )

        downstream_nodes = result.downstream_nodes
        assert len(downstream_nodes) == 1
        assert downstream_nodes[0].name == "final.report"

    def test_lineage_result_direction(self) -> None:
        """Test LineageResult direction field."""
        root = LineageNode(name="test.table", depth=0)

        # Test different directions
        for direction in [
            LineageDirection.UPSTREAM,
            LineageDirection.DOWNSTREAM,
            LineageDirection.BOTH,
        ]:
            result = LineageResult(root=root, direction=direction)
            assert result.direction == direction

    def test_lineage_result_max_depth(self) -> None:
        """Test LineageResult max_depth field."""
        root = LineageNode(name="test.table", depth=0)

        result = LineageResult(root=root, max_depth=5)
        assert result.max_depth == 5

        result_unlimited = LineageResult(root=root, max_depth=-1)
        assert result_unlimited.max_depth == -1

    def test_lineage_result_counts(self) -> None:
        """Test LineageResult upstream/downstream counts."""
        root = LineageNode(name="test.table", depth=0)

        result = LineageResult(
            root=root,
            total_upstream=3,
            total_downstream=5,
        )

        assert result.total_upstream == 3
        assert result.total_downstream == 5


class TestLineageNodeModel:
    """Tests for LineageNode model."""

    def test_lineage_node_creation(self) -> None:
        """Test LineageNode creation with all fields."""
        node = LineageNode(
            name="iceberg.analytics.daily_clicks",
            type="Dataset",
            owner="data-team@example.com",
            team="@analytics",
            description="Daily click aggregation",
            tags=["pii", "core"],
            depth=2,
        )

        assert node.name == "iceberg.analytics.daily_clicks"
        assert node.type == "Dataset"
        assert node.owner == "data-team@example.com"
        assert node.team == "@analytics"
        assert node.description == "Daily click aggregation"
        assert node.tags == ["pii", "core"]
        assert node.depth == 2

    def test_lineage_node_defaults(self) -> None:
        """Test LineageNode default values."""
        node = LineageNode(name="test.table")

        assert node.type == "Dataset"
        assert node.owner is None
        assert node.team is None
        assert node.description is None
        assert node.tags == []
        assert node.depth == 0


class TestLineageEdgeModel:
    """Tests for LineageEdge model."""

    def test_lineage_edge_creation(self) -> None:
        """Test LineageEdge creation."""
        edge = LineageEdge(
            source="raw.events",
            target="analytics.daily_aggregates",
            edge_type="direct",
        )

        assert edge.source == "raw.events"
        assert edge.target == "analytics.daily_aggregates"
        assert edge.edge_type == "direct"

    def test_lineage_edge_defaults(self) -> None:
        """Test LineageEdge default values."""
        edge = LineageEdge(
            source="a",
            target="b",
        )

        assert edge.edge_type == "direct"

    def test_lineage_edge_indirect_type(self) -> None:
        """Test LineageEdge with indirect type."""
        edge = LineageEdge(
            source="raw.events",
            target="analytics.report",
            edge_type="indirect",
        )

        assert edge.edge_type == "indirect"


class TestLineageDirectionEnum:
    """Tests for LineageDirection enum."""

    def test_lineage_direction_values(self) -> None:
        """Test LineageDirection enum values."""
        assert LineageDirection.UPSTREAM.value == "upstream"
        assert LineageDirection.DOWNSTREAM.value == "downstream"
        assert LineageDirection.BOTH.value == "both"

    def test_lineage_direction_from_string(self) -> None:
        """Test creating LineageDirection from string."""
        assert LineageDirection("upstream") == LineageDirection.UPSTREAM
        assert LineageDirection("downstream") == LineageDirection.DOWNSTREAM
        assert LineageDirection("both") == LineageDirection.BOTH
