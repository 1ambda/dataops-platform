"""Tests for the lineage subcommand.

This module tests the lineage subcommand for querying data lineage information.
Tests cover:
- show: Display full lineage (upstream and downstream)
- upstream: Show upstream dependencies
- downstream: Show downstream dependents

All lineage operations are SERVER-BASED, so tests use mock mode.
"""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest
from typer.testing import CliRunner

from dli.core.lineage import (
    LineageDirection,
    LineageEdge,
    LineageNode,
    LineageResult,
)
from dli.core.lineage.client import LineageClientError
from dli.main import app
from tests.cli.conftest import get_output
from tests.conftest import strip_ansi

runner = CliRunner()


@pytest.fixture
def mock_lineage_result() -> LineageResult:
    """Create a mock LineageResult for testing."""
    root = LineageNode(
        name="iceberg.analytics.daily_clicks",
        type="Dataset",
        owner="engineer@example.com",
        team="@data-eng",
        description="Daily user click aggregations",
        depth=0,
    )

    upstream_node = LineageNode(
        name="iceberg.raw.user_events",
        type="Dataset",
        owner="source@example.com",
        team="@data-platform",
        depth=-1,
    )

    downstream_node = LineageNode(
        name="iceberg.reporting.user_summary",
        type="Metric",
        owner="analyst@example.com",
        team="@analytics",
        depth=1,
    )

    edges = [
        LineageEdge(
            source="iceberg.raw.user_events",
            target="iceberg.analytics.daily_clicks",
            edge_type="direct",
        ),
        LineageEdge(
            source="iceberg.analytics.daily_clicks",
            target="iceberg.reporting.user_summary",
            edge_type="direct",
        ),
    ]

    return LineageResult(
        root=root,
        nodes=[upstream_node, downstream_node],
        edges=edges,
        direction=LineageDirection.BOTH,
        max_depth=-1,
        total_upstream=1,
        total_downstream=1,
    )


@pytest.fixture
def mock_upstream_result() -> LineageResult:
    """Create a mock LineageResult for upstream only."""
    root = LineageNode(
        name="iceberg.analytics.daily_clicks",
        type="Dataset",
        depth=0,
    )

    upstream_node = LineageNode(
        name="iceberg.raw.user_events",
        type="Dataset",
        depth=-1,
    )

    edges = [
        LineageEdge(
            source="iceberg.raw.user_events",
            target="iceberg.analytics.daily_clicks",
        ),
    ]

    return LineageResult(
        root=root,
        nodes=[upstream_node],
        edges=edges,
        direction=LineageDirection.UPSTREAM,
        total_upstream=1,
        total_downstream=0,
    )


@pytest.fixture
def mock_downstream_result() -> LineageResult:
    """Create a mock LineageResult for downstream only."""
    root = LineageNode(
        name="iceberg.analytics.daily_clicks",
        type="Dataset",
        depth=0,
    )

    downstream_node = LineageNode(
        name="iceberg.reporting.user_summary",
        type="Metric",
        depth=1,
    )

    edges = [
        LineageEdge(
            source="iceberg.analytics.daily_clicks",
            target="iceberg.reporting.user_summary",
        ),
    ]

    return LineageResult(
        root=root,
        nodes=[downstream_node],
        edges=edges,
        direction=LineageDirection.DOWNSTREAM,
        total_upstream=0,
        total_downstream=1,
    )


@pytest.fixture
def mock_empty_result() -> LineageResult:
    """Create an empty LineageResult."""
    root = LineageNode(
        name="iceberg.analytics.isolated",
        type="Dataset",
        depth=0,
    )

    return LineageResult(
        root=root,
        nodes=[],
        edges=[],
        direction=LineageDirection.BOTH,
        total_upstream=0,
        total_downstream=0,
    )


class TestLineageHelp:
    """Tests for lineage command help."""

    def test_lineage_help(self) -> None:
        """Test 'dli lineage --help' shows command help."""
        result = runner.invoke(app, ["lineage", "--help"])
        assert result.exit_code == 0
        assert "lineage" in result.stdout.lower()
        assert "show" in result.stdout
        assert "upstream" in result.stdout
        assert "downstream" in result.stdout

    def test_lineage_show_help(self) -> None:
        """Test 'dli lineage show --help' shows command help."""
        result = runner.invoke(app, ["lineage", "show", "--help"])
        assert result.exit_code == 0
        output = strip_ansi(result.stdout)
        assert "--depth" in output
        assert "--format" in output

    def test_lineage_upstream_help(self) -> None:
        """Test 'dli lineage upstream --help' shows command help."""
        result = runner.invoke(app, ["lineage", "upstream", "--help"])
        assert result.exit_code == 0
        assert "--depth" in strip_ansi(result.stdout)

    def test_lineage_downstream_help(self) -> None:
        """Test 'dli lineage downstream --help' shows command help."""
        result = runner.invoke(app, ["lineage", "downstream", "--help"])
        assert result.exit_code == 0
        assert "--depth" in strip_ansi(result.stdout)


class TestLineageShow:
    """Tests for lineage show command."""

    def test_lineage_show_table_format(
        self, sample_project_path: Path, mock_lineage_result: LineageResult
    ) -> None:
        """Test lineage show with table format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_lineage.return_value = mock_lineage_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "iceberg.analytics.daily_clicks" in output

    def test_lineage_show_json_format(
        self, sample_project_path: Path, mock_lineage_result: LineageResult
    ) -> None:
        """Test lineage show with JSON format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_lineage.return_value = mock_lineage_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                    "--format",
                    "json",
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            assert "iceberg.analytics.daily_clicks" in output
            # JSON output should contain expected fields
            assert "root" in output or "nodes" in output

    def test_lineage_show_with_depth(
        self, sample_project_path: Path, mock_lineage_result: LineageResult
    ) -> None:
        """Test lineage show with depth limit."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_lineage.return_value = mock_lineage_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "show",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                    "--depth",
                    "3",
                ],
            )

            assert result.exit_code == 0
            # Verify depth was passed to client
            mock_client.get_lineage.assert_called_once()
            call_kwargs = mock_client.get_lineage.call_args[1]
            assert call_kwargs["depth"] == 3

    def test_lineage_show_error_handling(
        self, sample_project_path: Path
    ) -> None:
        """Test lineage show handles server errors gracefully."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_lineage.side_effect = LineageClientError(
                message="Resource not found", status_code=404
            )
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "show",
                    "nonexistent.resource",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 1
            assert "not found" in get_output(result).lower() or "error" in get_output(
                result
            ).lower()


class TestLineageUpstream:
    """Tests for lineage upstream command."""

    def test_lineage_upstream_table_format(
        self, sample_project_path: Path, mock_upstream_result: LineageResult
    ) -> None:
        """Test lineage upstream with table format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_upstream.return_value = mock_upstream_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "upstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # Should show upstream dependencies
            assert "Upstream" in output or "upstream" in output.lower()

    def test_lineage_upstream_json_format(
        self, sample_project_path: Path, mock_upstream_result: LineageResult
    ) -> None:
        """Test lineage upstream with JSON format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_upstream.return_value = mock_upstream_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "upstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                    "--format",
                    "json",
                ],
            )

            assert result.exit_code == 0

    def test_lineage_upstream_empty_result(
        self, sample_project_path: Path, mock_empty_result: LineageResult
    ) -> None:
        """Test lineage upstream with no dependencies found."""
        # Create empty upstream result
        mock_empty_result.direction = LineageDirection.UPSTREAM
        mock_empty_result.nodes = []

        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_upstream.return_value = mock_empty_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "upstream",
                    "iceberg.analytics.isolated",
                    "--path",
                    str(sample_project_path),
                ],
            )

            # Should exit 0 even with no results
            assert result.exit_code == 0
            output = get_output(result)
            assert "no upstream" in output.lower() or "not found" in output.lower()

    def test_lineage_upstream_with_depth(
        self, sample_project_path: Path, mock_upstream_result: LineageResult
    ) -> None:
        """Test lineage upstream with depth limit."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_upstream.return_value = mock_upstream_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "upstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                    "--depth",
                    "2",
                ],
            )

            assert result.exit_code == 0
            mock_client.get_upstream.assert_called_once()
            call_kwargs = mock_client.get_upstream.call_args[1]
            assert call_kwargs["depth"] == 2


class TestLineageDownstream:
    """Tests for lineage downstream command."""

    def test_lineage_downstream_table_format(
        self, sample_project_path: Path, mock_downstream_result: LineageResult
    ) -> None:
        """Test lineage downstream with table format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_downstream.return_value = mock_downstream_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "downstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 0
            output = get_output(result)
            # Should show downstream dependents
            assert "Downstream" in output or "downstream" in output.lower()

    def test_lineage_downstream_json_format(
        self, sample_project_path: Path, mock_downstream_result: LineageResult
    ) -> None:
        """Test lineage downstream with JSON format output."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_downstream.return_value = mock_downstream_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "downstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                    "--format",
                    "json",
                ],
            )

            assert result.exit_code == 0

    def test_lineage_downstream_empty_result(
        self, sample_project_path: Path, mock_empty_result: LineageResult
    ) -> None:
        """Test lineage downstream with no dependents found."""
        mock_empty_result.direction = LineageDirection.DOWNSTREAM
        mock_empty_result.nodes = []

        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_downstream.return_value = mock_empty_result
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "downstream",
                    "iceberg.analytics.isolated",
                    "--path",
                    str(sample_project_path),
                ],
            )

            # Should exit 0 even with no results
            assert result.exit_code == 0
            output = get_output(result)
            assert "no downstream" in output.lower() or "not found" in output.lower()

    def test_lineage_downstream_error_handling(
        self, sample_project_path: Path
    ) -> None:
        """Test lineage downstream handles server errors gracefully."""
        with patch(
            "dli.commands.lineage._get_lineage_client"
        ) as mock_get_client:
            mock_client = MagicMock()
            mock_client.get_downstream.side_effect = LineageClientError(
                message="Server unavailable", status_code=503
            )
            mock_get_client.return_value = mock_client

            result = runner.invoke(
                app,
                [
                    "lineage",
                    "downstream",
                    "iceberg.analytics.daily_clicks",
                    "--path",
                    str(sample_project_path),
                ],
            )

            assert result.exit_code == 1


class TestLineageNoArgsIsHelp:
    """Tests for lineage command with no args."""

    def test_lineage_no_args_shows_help(self) -> None:
        """Test running 'dli lineage' with no args shows help."""
        result = runner.invoke(app, ["lineage"])
        # no_args_is_help=True should show help
        assert result.exit_code in [0, 2]
        output = get_output(result)
        assert "show" in output or "upstream" in output or "Usage" in output
