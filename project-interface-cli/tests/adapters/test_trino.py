"""Tests for TrinoExecutor adapter.

This module tests the Trino executor with mocked trino client.
"""

from __future__ import annotations

from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import pytest

if TYPE_CHECKING:
    from dli.adapters.trino import TrinoExecutor


class TestTrinoAvailability:
    """Tests for optional Trino dependency handling."""

    def test_trino_available_flag(self) -> None:
        """TRINO_AVAILABLE should indicate import status."""
        from dli.adapters import trino

        # Flag should be boolean
        assert isinstance(trino.TRINO_AVAILABLE, bool)

    def test_import_error_when_dependency_missing(self) -> None:
        """Should raise ImportError when trino not installed."""
        with (
            patch.dict("sys.modules", {"trino": None}),
            patch("dli.adapters.trino.TRINO_AVAILABLE", False),
        ):
            from dli.adapters.trino import TrinoExecutor

            with pytest.raises(ImportError) as exc_info:
                TrinoExecutor(host="localhost")

            assert "trino" in str(exc_info.value)
            assert "uv add trino" in str(exc_info.value)


@pytest.fixture
def mock_trino_connection() -> MagicMock:
    """Create a mocked Trino connection."""
    return MagicMock()


@pytest.fixture
def mock_trino_cursor() -> MagicMock:
    """Create a mocked Trino cursor."""
    return MagicMock()


@pytest.fixture
def mock_trino_dbapi(
    mock_trino_connection: MagicMock,
    mock_trino_cursor: MagicMock,
) -> MagicMock:
    """Create a mocked trino.dbapi module."""
    dbapi = MagicMock()
    dbapi.connect.return_value = mock_trino_connection
    mock_trino_connection.cursor.return_value = mock_trino_cursor
    return dbapi


@pytest.fixture
def mock_trino_auth() -> MagicMock:
    """Create a mocked trino.auth module."""
    auth = MagicMock()
    auth.BasicAuthentication = MagicMock()
    auth.JWTAuthentication = MagicMock()
    auth.OAuth2Authentication = MagicMock()
    return auth


@pytest.fixture
def trino_executor(
    mock_trino_dbapi: MagicMock,
    mock_trino_auth: MagicMock,
    mock_trino_connection: MagicMock,
) -> TrinoExecutor:
    """Create a TrinoExecutor with mocked dependencies."""
    with (
        patch("dli.adapters.trino.TRINO_AVAILABLE", True),
        patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
        patch("dli.adapters.trino._trino_auth", mock_trino_auth),
    ):
        from dli.adapters.trino import TrinoExecutor

        executor = TrinoExecutor(
            host="localhost",
            port=8080,
            user="test_user",
            catalog="test_catalog",
            schema="test_schema",
            ssl=False,
        )
        # Replace connection with mock after initialization
        executor.connection = mock_trino_connection
        return executor


class TestTrinoExecutorInit:
    """Tests for TrinoExecutor initialization."""

    def test_init_with_defaults(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should initialize with default values."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            executor = TrinoExecutor(host="trino.example.com")

            assert executor.host == "trino.example.com"
            assert executor.port == 8080
            assert executor.user == "trino"
            assert executor.ssl is True
            mock_trino_dbapi.connect.assert_called_once_with(
                host="trino.example.com",
                port=8080,
                user="trino",
                catalog=None,
                schema=None,
                http_scheme="https",
                verify=True,
                auth=None,
            )

    def test_init_with_custom_values(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should initialize with custom values."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            executor = TrinoExecutor(
                host="trino.example.com",
                port=443,
                user="admin",
                catalog="hive",
                schema="default",
                ssl=True,
                ssl_verify=True,
            )

            assert executor.host == "trino.example.com"
            assert executor.port == 443
            assert executor.user == "admin"
            assert executor.catalog == "hive"
            assert executor.schema == "default"

    def test_init_with_basic_auth(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should initialize with basic authentication."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            TrinoExecutor(
                host="trino.example.com",
                user="admin",
                auth_type="basic",
                password="secret123",
            )

            mock_trino_auth.BasicAuthentication.assert_called_once_with(
                "admin",
                "secret123",
            )

    def test_init_with_jwt_auth(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should initialize with JWT authentication."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            TrinoExecutor(
                host="trino.example.com",
                auth_type="jwt",
                auth_token="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            )

            mock_trino_auth.JWTAuthentication.assert_called_once_with(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
            )

    def test_init_basic_auth_missing_password(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should raise ValueError when basic auth is missing password."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            with pytest.raises(ValueError) as exc_info:
                TrinoExecutor(
                    host="trino.example.com",
                    auth_type="basic",
                    # password missing
                )

            assert "Password is required" in str(exc_info.value)

    def test_init_jwt_auth_missing_token(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should raise ValueError when JWT auth is missing token."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            with pytest.raises(ValueError) as exc_info:
                TrinoExecutor(
                    host="trino.example.com",
                    auth_type="jwt",
                    # auth_token missing
                )

            assert "auth_token is required" in str(exc_info.value)

    def test_init_unsupported_auth_type(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
    ) -> None:
        """Should raise ValueError for unsupported auth type."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            with pytest.raises(ValueError) as exc_info:
                TrinoExecutor(
                    host="trino.example.com",
                    auth_type="kerberos",  # not supported
                )

            assert "Unsupported auth_type" in str(exc_info.value)


class TestTrinoExecutorExecuteSql:
    """Tests for TrinoExecutor.execute_sql() method."""

    def test_execute_sql_success(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return successful ExecutionResult with data."""
        # Setup mock cursor
        mock_trino_cursor.description = [("id",), ("name",)]
        mock_trino_cursor.fetchall.return_value = [
            (1, "Alice"),
            (2, "Bob"),
        ]

        # Execute
        result = trino_executor.execute_sql("SELECT * FROM users")

        # Verify
        assert result.success is True
        assert result.row_count == 2
        assert result.columns == ["id", "name"]
        assert result.data == [
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
        ]
        assert result.rendered_sql == "SELECT * FROM users"
        assert result.execution_time_ms >= 0
        mock_trino_cursor.execute.assert_called_once_with("SELECT * FROM users")

    def test_execute_sql_empty_result(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should handle empty result set."""
        mock_trino_cursor.description = []
        mock_trino_cursor.fetchall.return_value = []

        result = trino_executor.execute_sql("SELECT * FROM empty_table")

        assert result.success is True
        assert result.row_count == 0
        assert result.columns == []
        assert result.data == []

    def test_execute_sql_error(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should handle Trino errors."""
        mock_trino_cursor.execute.side_effect = Exception("Table not found: users")

        result = trino_executor.execute_sql("SELECT * FROM missing_table")

        assert result.success is False
        assert "Table not found" in result.error_message

    def test_execute_sql_timeout_error(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should handle timeout errors gracefully."""
        mock_trino_cursor.execute.side_effect = Exception(
            "Query exceeded timeout threshold"
        )

        result = trino_executor.execute_sql("SELECT * FROM slow_query", timeout=10)

        assert result.success is False
        assert "timed out" in result.error_message.lower()
        assert "10" in result.error_message


class TestTrinoExecutorDryRun:
    """Tests for TrinoExecutor.dry_run() method."""

    def test_dry_run_valid_query(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return plan for valid query."""
        mock_trino_cursor.fetchall.return_value = [
            ("- Output[id, name]",),
            ("    - TableScan[users]",),
        ]

        result = trino_executor.dry_run("SELECT id, name FROM users")

        assert result["valid"] is True
        assert "plan" in result
        assert "Output" in result["plan"]
        mock_trino_cursor.execute.assert_called_once_with(
            "EXPLAIN SELECT id, name FROM users"
        )

    def test_dry_run_invalid_query(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return error for invalid SQL."""
        mock_trino_cursor.execute.side_effect = Exception(
            "Syntax error at position 10"
        )

        result = trino_executor.dry_run("SELECT * FORM users")

        assert result["valid"] is False
        assert "Syntax error" in result["error"]


class TestTrinoExecutorTestConnection:
    """Tests for TrinoExecutor.test_connection() method."""

    def test_connection_success(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return True on successful connection."""
        mock_trino_cursor.fetchall.return_value = [(1,)]

        result = trino_executor.test_connection()

        assert result is True
        mock_trino_cursor.execute.assert_called_once_with("SELECT 1")

    def test_connection_failure(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return False on connection error."""
        mock_trino_cursor.execute.side_effect = Exception("Connection refused")

        result = trino_executor.test_connection()

        assert result is False


class TestTrinoExecutorGetTableSchema:
    """Tests for TrinoExecutor.get_table_schema() method."""

    def test_get_schema_fully_qualified(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return schema for fully qualified table name."""
        # DESCRIBE returns: Column, Type, Extra, Comment
        mock_trino_cursor.fetchall.return_value = [
            ("id", "bigint", "", ""),
            ("name", "varchar", "", "User name"),
            ("created_at", "timestamp", "", ""),
        ]

        result = trino_executor.get_table_schema("hive.default.users")

        assert result == [
            {"name": "id", "type": "bigint"},
            {"name": "name", "type": "varchar"},
            {"name": "created_at", "type": "timestamp"},
        ]
        mock_trino_cursor.execute.assert_called_once_with(
            'DESCRIBE "hive"."default"."users"'
        )

    def test_get_schema_schema_qualified(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return schema for schema-qualified table name."""
        mock_trino_cursor.fetchall.return_value = [
            ("id", "bigint", "", ""),
        ]

        result = trino_executor.get_table_schema("default.users")

        assert result == [{"name": "id", "type": "bigint"}]
        mock_trino_cursor.execute.assert_called_once_with(
            'DESCRIBE "default"."users"'
        )

    def test_get_schema_table_only(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return schema for unqualified table name."""
        mock_trino_cursor.fetchall.return_value = [
            ("id", "bigint", "", ""),
        ]

        result = trino_executor.get_table_schema("users")

        assert result == [{"name": "id", "type": "bigint"}]
        mock_trino_cursor.execute.assert_called_once_with('DESCRIBE "users"')

    def test_get_schema_table_not_found(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Should return empty list for non-existent table."""
        mock_trino_cursor.execute.side_effect = Exception("Table not found")

        result = trino_executor.get_table_schema("hive.default.missing_table")

        assert result == []


class TestTrinoExecutorContextManager:
    """Tests for TrinoExecutor context manager support."""

    def test_context_manager(
        self,
        mock_trino_dbapi: MagicMock,
        mock_trino_auth: MagicMock,
        mock_trino_connection: MagicMock,
    ) -> None:
        """Should support context manager protocol."""
        with (
            patch("dli.adapters.trino.TRINO_AVAILABLE", True),
            patch("dli.adapters.trino._trino_dbapi", mock_trino_dbapi),
            patch("dli.adapters.trino._trino_auth", mock_trino_auth),
        ):
            from dli.adapters.trino import TrinoExecutor

            with TrinoExecutor(host="localhost", ssl=False) as executor:
                assert executor is not None

            # Connection should be closed after exiting context
            mock_trino_connection.close.assert_called_once()


class TestTrinoExecutorIntegration:
    """Integration-like tests verifying executor behavior patterns."""

    def test_execute_preserves_sql_in_result(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Executed SQL should be preserved in result for debugging."""
        mock_trino_cursor.description = []
        mock_trino_cursor.fetchall.return_value = []

        original_sql = "SELECT id, name FROM users WHERE status = 'active'"
        result = trino_executor.execute_sql(original_sql)

        assert result.rendered_sql == original_sql

    def test_execution_time_is_measured(
        self,
        trino_executor: TrinoExecutor,
        mock_trino_cursor: MagicMock,
    ) -> None:
        """Execution time should be measured and reported."""
        mock_trino_cursor.description = []
        mock_trino_cursor.fetchall.return_value = []

        result = trino_executor.execute_sql("SELECT 1")

        # Execution time should be non-negative
        assert result.execution_time_ms >= 0
