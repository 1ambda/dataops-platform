"""Tests for RunAPI Library API."""

import csv
import json
from pathlib import Path

import pytest

from dli import ExecutionContext, ExecutionMode
from dli.api.run import RunAPI
from dli.exceptions import (
    ConfigurationError,
    RunFileNotFoundError,
)
from dli.models.common import ResultStatus
from dli.models.run import ExecutionPlan, OutputFormat, RunResult


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def mock_context() -> ExecutionContext:
    """Create mock mode context."""
    return ExecutionContext(execution_mode=ExecutionMode.MOCK)


@pytest.fixture
def server_context() -> ExecutionContext:
    """Create server mode context."""
    return ExecutionContext(
        execution_mode=ExecutionMode.SERVER,
        server_url="http://localhost:8081",
    )


@pytest.fixture
def mock_api(mock_context: ExecutionContext) -> RunAPI:
    """Create RunAPI in mock mode."""
    return RunAPI(context=mock_context)


# =============================================================================
# TestRunAPIInit
# =============================================================================


class TestRunAPIInit:
    """Tests for RunAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = RunAPI()
        assert api.context is not None
        assert api.context.execution_mode == ExecutionMode.LOCAL  # default

    def test_init_with_mock_context(self, mock_context: ExecutionContext) -> None:
        """Test initialization with mock context."""
        api = RunAPI(context=mock_context)
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_server_context(self, server_context: ExecutionContext) -> None:
        """Test initialization with server context."""
        api = RunAPI(context=server_context)
        assert api.context.execution_mode == ExecutionMode.SERVER
        assert api.context.server_url == "http://localhost:8081"

    def test_repr(self, mock_api: RunAPI) -> None:
        """Test string representation."""
        repr_str = repr(mock_api)
        assert "RunAPI" in repr_str
        assert "context=" in repr_str


# =============================================================================
# TestRunAPIRun
# =============================================================================


class TestRunAPIRun:
    """Tests for RunAPI.run() method."""

    def test_run_mock_mode(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test running SQL in mock mode."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM users")
        output_file = tmp_path / "output.csv"

        result = mock_api.run(sql_path=sql_file, output_path=output_file)

        assert isinstance(result, RunResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.is_success is True
        assert result.sql_path == sql_file
        assert result.output_path == output_file
        assert result.output_format == OutputFormat.CSV
        assert result.row_count > 0
        assert result.duration_seconds >= 0.0
        assert result.execution_mode == ExecutionMode.MOCK
        assert output_file.exists()

    def test_run_creates_output_csv(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test that run creates CSV output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        mock_api.run(sql_path=sql_file, output_path=output_file)

        assert output_file.exists()
        content = output_file.read_text()
        # Mock mode creates output with id, name, value columns
        assert "id" in content or "name" in content

    def test_run_creates_output_json(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test that run creates JSON output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.json"

        mock_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.JSON,
        )

        assert output_file.exists()
        # JSON Lines format - each line is valid JSON
        lines = output_file.read_text().strip().split("\n")
        for line in lines:
            if line:
                json.loads(line)  # Should not raise

    def test_run_creates_output_tsv(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test that run creates TSV output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.tsv"

        mock_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.TSV,
        )

        assert output_file.exists()
        content = output_file.read_text()
        # TSV uses tab delimiters
        assert "\t" in content

    def test_run_with_parameters(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test running SQL with parameter substitution."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{ table }} WHERE date = '{{ date }}'")
        output_file = tmp_path / "output.csv"

        result = mock_api.run(
            sql_path=sql_file,
            output_path=output_file,
            parameters={"table": "users", "date": "2026-01-01"},
        )

        assert result.is_success
        # Rendered SQL should have substituted parameters
        assert "{{ table }}" not in result.rendered_sql
        assert "users" in result.rendered_sql
        assert "2026-01-01" in result.rendered_sql

    def test_run_file_not_found(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test error when SQL file does not exist."""
        sql_file = tmp_path / "nonexistent.sql"
        output_file = tmp_path / "output.csv"

        with pytest.raises(RunFileNotFoundError) as exc_info:
            mock_api.run(sql_path=sql_file, output_path=output_file)

        assert "DLI-410" in str(exc_info.value)
        assert "nonexistent.sql" in str(exc_info.value)

    def test_run_both_prefer_options_error(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test error when both prefer_local and prefer_server are True."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        with pytest.raises(ConfigurationError):
            mock_api.run(
                sql_path=sql_file,
                output_path=output_file,
                prefer_local=True,
                prefer_server=True,
            )

    def test_run_creates_parent_directories(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test that run creates parent directories for output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "nested" / "dir" / "output.csv"

        result = mock_api.run(sql_path=sql_file, output_path=output_file)

        assert result.is_success
        assert output_file.exists()

    def test_run_result_is_frozen(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test that RunResult is frozen (immutable)."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        result = mock_api.run(sql_path=sql_file, output_path=output_file)

        with pytest.raises(Exception):
            result.row_count = 999  # type: ignore[misc]


# =============================================================================
# TestRunAPIDryRun
# =============================================================================


class TestRunAPIDryRun:
    """Tests for RunAPI.dry_run() method."""

    def test_dry_run_returns_plan(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test dry_run returns ExecutionPlan."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        plan = mock_api.dry_run(sql_path=sql_file, output_path=output_file)

        assert isinstance(plan, ExecutionPlan)
        assert plan.sql_path == sql_file
        assert plan.output_path == output_file
        assert plan.output_format == OutputFormat.CSV
        assert plan.dialect == "bigquery"  # default
        assert plan.is_valid is True

    def test_dry_run_does_not_create_output(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test dry_run does not create output file."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        mock_api.dry_run(sql_path=sql_file, output_path=output_file)

        assert not output_file.exists()

    def test_dry_run_with_parameters(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test dry_run shows rendered SQL with parameters."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{ table }}")
        output_file = tmp_path / "output.csv"

        plan = mock_api.dry_run(
            sql_path=sql_file,
            output_path=output_file,
            parameters={"table": "users"},
        )

        assert plan.parameters == {"table": "users"}
        assert "users" in plan.rendered_sql
        assert "{{ table }}" not in plan.rendered_sql

    def test_dry_run_with_different_dialect(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test dry_run with different SQL dialect."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        plan = mock_api.dry_run(
            sql_path=sql_file,
            output_path=output_file,
            dialect="trino",
        )

        assert plan.dialect == "trino"

    def test_dry_run_file_not_found(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test dry_run error when SQL file does not exist."""
        sql_file = tmp_path / "nonexistent.sql"
        output_file = tmp_path / "output.csv"

        with pytest.raises(RunFileNotFoundError):
            mock_api.dry_run(sql_path=sql_file, output_path=output_file)

    def test_dry_run_both_prefer_options_error(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test dry_run error when both prefer_local and prefer_server are True."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        with pytest.raises(ConfigurationError):
            mock_api.dry_run(
                sql_path=sql_file,
                output_path=output_file,
                prefer_local=True,
                prefer_server=True,
            )

    def test_dry_run_result_is_frozen(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test that ExecutionPlan is frozen (immutable)."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        plan = mock_api.dry_run(sql_path=sql_file, output_path=output_file)

        with pytest.raises(Exception):
            plan.is_valid = False  # type: ignore[misc]


# =============================================================================
# TestRunAPIRenderSQL
# =============================================================================


class TestRunAPIRenderSQL:
    """Tests for RunAPI.render_sql() method."""

    def test_render_sql_no_parameters(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test render_sql without parameters."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        rendered = mock_api.render_sql(sql_path=sql_file)

        assert rendered == "SELECT 1"

    def test_render_sql_with_parameters(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test render_sql with parameter substitution."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text(
            "SELECT * FROM {{ table }} WHERE date = '{{ date }}'"
        )

        rendered = mock_api.render_sql(
            sql_path=sql_file,
            parameters={"table": "users", "date": "2026-01-01"},
        )

        assert "users" in rendered
        assert "2026-01-01" in rendered
        assert "{{ table }}" not in rendered
        assert "{{ date }}" not in rendered

    def test_render_sql_with_compact_syntax(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test render_sql with compact {{param}} syntax."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{table}}")

        rendered = mock_api.render_sql(
            sql_path=sql_file,
            parameters={"table": "orders"},
        )

        assert "orders" in rendered
        assert "{{table}}" not in rendered

    def test_render_sql_file_not_found(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test render_sql error when SQL file does not exist."""
        sql_file = tmp_path / "nonexistent.sql"

        with pytest.raises(RunFileNotFoundError):
            mock_api.render_sql(sql_path=sql_file)

    def test_render_sql_preserves_unreplaced_params(
        self, mock_api: RunAPI, tmp_path: Path
    ) -> None:
        """Test that unreplaced parameters remain in output."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT * FROM {{ table }} WHERE id = {{ id }}")

        rendered = mock_api.render_sql(
            sql_path=sql_file,
            parameters={"table": "users"},  # id not provided
        )

        assert "users" in rendered
        assert "{{ id }}" in rendered  # Unreplaced parameter remains


# =============================================================================
# TestRunAPIServerMode
# =============================================================================


class TestRunAPIServerMode:
    """Tests for RunAPI server mode behavior."""

    def test_mock_mode_flag(self, mock_api: RunAPI) -> None:
        """Test _is_mock_mode property in mock mode."""
        assert mock_api._is_mock_mode is True

    def test_server_mode_flag(self, server_context: ExecutionContext) -> None:
        """Test _is_mock_mode property in server mode."""
        api = RunAPI(context=server_context)
        assert api._is_mock_mode is False

    def test_client_creation(self, mock_api: RunAPI) -> None:
        """Test client is created lazily."""
        assert mock_api._client is None

        client = mock_api._get_client()

        assert client is not None
        assert mock_api._client is not None


# =============================================================================
# TestRunAPIDependencyInjection
# =============================================================================


class TestRunAPIDependencyInjection:
    """Tests for RunAPI dependency injection."""

    def test_uses_injected_client(self, mock_context: ExecutionContext) -> None:
        """Test that injected client is used."""
        from dli.core.client import BasecampClient, ServerConfig

        config = ServerConfig(url="http://test:8080")
        mock_client = BasecampClient(config=config, mock_mode=True)

        api = RunAPI(context=mock_context, client=mock_client)

        # Should use the injected client
        assert api._get_client() is mock_client


# =============================================================================
# TestRunAPIOutputWriting
# =============================================================================


class TestRunAPIOutputWriting:
    """Tests for RunAPI output file writing."""

    def test_csv_output_format(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test CSV output is properly formatted."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.csv"

        mock_api.run(sql_path=sql_file, output_path=output_file)

        # Read and verify CSV format
        with output_file.open() as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            assert len(rows) > 0

    def test_tsv_output_format(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test TSV output uses tab delimiter."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.tsv"

        mock_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.TSV,
        )

        # Read and verify TSV format
        with output_file.open() as f:
            reader = csv.DictReader(f, delimiter="\t")
            rows = list(reader)
            assert len(rows) > 0

    def test_json_output_format(self, mock_api: RunAPI, tmp_path: Path) -> None:
        """Test JSON output is JSON Lines format."""
        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")
        output_file = tmp_path / "output.json"

        mock_api.run(
            sql_path=sql_file,
            output_path=output_file,
            output_format=OutputFormat.JSON,
        )

        # Read and verify JSON Lines format
        lines = output_file.read_text().strip().split("\n")
        for line in lines:
            if line:
                data = json.loads(line)
                assert isinstance(data, dict)


# =============================================================================
# TestRunAPIServerPolicy
# =============================================================================


class TestRunAPIServerPolicy:
    """Tests for server policy enforcement (No Fallback)."""

    def test_local_denied_by_policy(self, tmp_path: Path) -> None:
        """Test DLI-411 when server denies local execution.

        Verifies that when server policy returns allow_local=False,
        requesting prefer_local=True raises RunLocalDeniedError.
        """
        from unittest.mock import Mock

        from dli.core.client import ServerResponse
        from dli.exceptions import RunLocalDeniedError

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=True,
            data={
                "allow_local": False,
                "server_available": True,
                "default_mode": "server",
            },
        )

        # Use SERVER mode context (not MOCK) to trigger policy check
        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        with pytest.raises(RunLocalDeniedError) as exc_info:
            api.run(
                sql_path=sql_file,
                output_path=tmp_path / "out.csv",
                prefer_local=True,
            )

        assert exc_info.value.code.value == "DLI-411"
        assert "local execution" in str(exc_info.value).lower()

    def test_server_unavailable(self, tmp_path: Path) -> None:
        """Test DLI-412 when server execution unavailable.

        Verifies that when server policy returns server_available=False,
        requesting prefer_server=True raises RunServerUnavailableError.
        """
        from unittest.mock import Mock

        from dli.core.client import ServerResponse
        from dli.exceptions import RunServerUnavailableError

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=True,
            data={
                "allow_local": True,
                "server_available": False,
                "default_mode": "local",
            },
        )

        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        with pytest.raises(RunServerUnavailableError) as exc_info:
            api.run(
                sql_path=sql_file,
                output_path=tmp_path / "out.csv",
                prefer_server=True,
            )

        assert exc_info.value.code.value == "DLI-412"
        assert "server" in str(exc_info.value).lower()

    def test_dry_run_local_denied_returns_invalid_plan(self, tmp_path: Path) -> None:
        """Test dry_run returns invalid plan with DLI-411 when local denied.

        Note: dry_run captures policy errors in validation_error rather than raising.
        This allows users to see what would happen without failing.
        """
        from unittest.mock import Mock

        from dli.core.client import ServerResponse

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=True,
            data={"allow_local": False, "server_available": True, "default_mode": "server"},
        )

        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        # dry_run captures error instead of raising
        plan = api.dry_run(
            sql_path=sql_file,
            output_path=tmp_path / "out.csv",
            prefer_local=True,
        )

        assert plan.is_valid is False
        assert plan.validation_error is not None
        assert "DLI-411" in plan.validation_error
        assert "local" in plan.validation_error.lower()

    def test_dry_run_server_unavailable_returns_invalid_plan(
        self, tmp_path: Path
    ) -> None:
        """Test dry_run returns invalid plan with DLI-412 when server unavailable.

        Note: dry_run captures policy errors in validation_error rather than raising.
        """
        from unittest.mock import Mock

        from dli.core.client import ServerResponse

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=True,
            data={"allow_local": True, "server_available": False, "default_mode": "local"},
        )

        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        # dry_run captures error instead of raising
        plan = api.dry_run(
            sql_path=sql_file,
            output_path=tmp_path / "out.csv",
            prefer_server=True,
        )

        assert plan.is_valid is False
        assert plan.validation_error is not None
        assert "DLI-412" in plan.validation_error
        assert "server" in plan.validation_error.lower()

    def test_policy_check_fallback_to_server_on_failure(
        self, tmp_path: Path
    ) -> None:
        """Test that failed policy check defaults to SERVER mode."""
        from unittest.mock import Mock

        from dli.core.client import ServerResponse

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=False,
            data=None,
            error="Policy service unavailable",
        )
        # Mock the run_execute to avoid actual server call
        mock_client.run_execute.return_value = ServerResponse(
            success=True,
            data={"rows": [], "row_count": 0, "columns": []},
        )

        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        sql_file = tmp_path / "query.sql"
        sql_file.write_text("SELECT 1")

        # Should not raise - defaults to SERVER mode
        result = api.run(
            sql_path=sql_file,
            output_path=tmp_path / "out.csv",
        )

        assert result.execution_mode == ExecutionMode.SERVER

    def test_default_mode_from_policy(self, tmp_path: Path) -> None:
        """Test that default_mode from policy is respected."""
        from unittest.mock import Mock

        from dli.core.client import ServerResponse

        mock_client = Mock()
        mock_client.run_get_policy.return_value = ServerResponse(
            success=True,
            data={
                "allow_local": True,
                "server_available": True,
                "default_mode": "local",  # Policy suggests local
            },
        )

        ctx = ExecutionContext(execution_mode=ExecutionMode.SERVER)
        api = RunAPI(context=ctx, client=mock_client)

        # Resolve mode without preferences - should use policy default
        mode = api._resolve_execution_mode(prefer_local=False, prefer_server=False)
        assert mode == ExecutionMode.LOCAL
