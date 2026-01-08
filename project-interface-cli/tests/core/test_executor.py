"""Tests for the DLI Core Engine executor module."""

from unittest.mock import MagicMock, patch

import pytest

from dli.core.executor import (
    BaseExecutor,
    DatasetExecutor,
    FailingMockExecutor,
    MockExecutor,
    ServerExecutor,
)
from dli.core.models import (
    DatasetSpec,
    ExecutionConfig,
    QueryType,
    StatementDefinition,
)


class TestBaseExecutor:
    """Tests for BaseExecutor abstract class."""

    def test_cannot_instantiate(self):
        """Test that abstract class cannot be instantiated."""
        with pytest.raises(TypeError):
            BaseExecutor()


class TestMockExecutor:
    """Tests for MockExecutor class."""

    def test_execute_empty_data(self):
        """Test executing with empty mock data."""
        executor = MockExecutor()
        result = executor.execute_sql("SELECT * FROM users")

        assert result.success is True
        assert result.row_count == 0
        assert result.columns == []
        assert result.data == []
        assert result.rendered_sql == "SELECT * FROM users"
        assert result.execution_time_ms >= 0

    def test_execute_with_mock_data(self):
        """Test returning mock data."""
        mock_data = [
            {"id": 1, "name": "Alice"},
            {"id": 2, "name": "Bob"},
        ]
        executor = MockExecutor(mock_data=mock_data)
        result = executor.execute_sql("SELECT * FROM users")

        assert result.success is True
        assert result.row_count == 2
        assert result.columns == ["id", "name"]
        assert result.data == mock_data

    def test_execute_tracks_queries(self):
        """Test tracking executed queries."""
        executor = MockExecutor()
        executor.execute_sql("SELECT 1")
        executor.execute_sql("SELECT 2")

        assert len(executor.executed_sqls) == 2
        assert "SELECT 1" in executor.executed_sqls
        assert "SELECT 2" in executor.executed_sqls

    def test_execute_failure(self):
        """Test simulating execution failure."""
        executor = MockExecutor(should_fail=True, error_message="Connection failed")
        result = executor.execute_sql("SELECT 1")

        assert result.success is False
        assert result.error_message == "Connection failed"

    def test_dry_run(self):
        """Test mock dry run result."""
        executor = MockExecutor()
        result = executor.dry_run("SELECT * FROM users")

        assert result["valid"] is True
        assert "bytes_processed" in result
        assert "estimated_cost_usd" in result

    def test_dry_run_tracks_queries(self):
        """Test tracking dry run queries."""
        executor = MockExecutor()
        executor.dry_run("SELECT * FROM users")

        assert len(executor.executed_sqls) == 1
        assert "DRY_RUN" in executor.executed_sqls[0]

    def test_test_connection(self):
        """Test connection test always returns True."""
        executor = MockExecutor()
        assert executor.test_connection() is True

    def test_reset(self):
        """Test resetting executor state."""
        executor = MockExecutor()
        executor.execute_sql("SELECT 1")
        executor.execute_sql("SELECT 2")
        assert len(executor.executed_sqls) == 2

        executor.reset()
        assert len(executor.executed_sqls) == 0


class TestFailingMockExecutor:
    """Tests for FailingMockExecutor class."""

    def test_fail_on_specific_statement(self):
        """Test failing on specific statement index."""
        executor = FailingMockExecutor(
            fail_on_statements=[1],
            error_message="Statement 1 failed",
        )

        result0 = executor.execute_sql("SELECT 0")
        assert result0.success is True

        result1 = executor.execute_sql("SELECT 1")
        assert result1.success is False
        assert result1.error_message == "Statement 1 failed"

        result2 = executor.execute_sql("SELECT 2")
        assert result2.success is True

    def test_fail_on_multiple_statements(self):
        """Test failing on multiple statement indices."""
        executor = FailingMockExecutor(
            fail_on_statements=[0, 2],
            error_message="Failed",
        )

        result0 = executor.execute_sql("SELECT 0")
        assert result0.success is False

        result1 = executor.execute_sql("SELECT 1")
        assert result1.success is True

        result2 = executor.execute_sql("SELECT 2")
        assert result2.success is False

    def test_reset(self):
        """Test resetting execution count."""
        executor = FailingMockExecutor(fail_on_statements=[1])

        executor.execute_sql("SELECT 0")
        executor.execute_sql("SELECT 1")  # Fails

        executor.reset()

        # After reset, statement indices restart
        result = executor.execute_sql("SELECT 0")
        assert result.success is True

        result = executor.execute_sql("SELECT 1")
        assert result.success is False


class TestDatasetExecutor:
    """Tests for DatasetExecutor class."""

    @pytest.fixture
    def simple_spec(self):
        """Create a simple dataset spec."""
        return DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.DML,
            query_statement="INSERT INTO t SELECT 1",
            execution=ExecutionConfig(timeout_seconds=300),
        )

    @pytest.fixture
    def spec_with_pre_post(self):
        """Create a dataset spec with pre and post statements."""
        return DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.DML,
            query_statement="INSERT INTO t SELECT 1",
            pre_statements=[
                StatementDefinition(name="delete_partition", sql="DELETE FROM t WHERE dt = '2024-01-01'"),
            ],
            post_statements=[
                StatementDefinition(name="optimize", sql="ALTER TABLE t EXECUTE optimize"),
            ],
            execution=ExecutionConfig(timeout_seconds=300),
        )

    def test_execute_main_only(self, simple_spec):
        """Test executing main query only."""
        mock_executor = MockExecutor()
        executor = DatasetExecutor(mock_executor)

        rendered_sqls = {"main": "INSERT INTO t SELECT 1"}
        result = executor.execute(simple_spec, rendered_sqls)

        assert result.success is True
        assert result.main_result is not None
        assert result.main_result.phase == "main"
        assert len(result.pre_results) == 0
        assert len(result.post_results) == 0
        assert len(mock_executor.executed_sqls) == 1

    def test_execute_with_pre_post(self, spec_with_pre_post):
        """Test executing pre, main, and post statements."""
        mock_executor = MockExecutor()
        executor = DatasetExecutor(mock_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t WHERE dt = '2024-01-01'"],
            "main": "INSERT INTO t SELECT 1",
            "post": ["ALTER TABLE t EXECUTE optimize"],
        }
        result = executor.execute(spec_with_pre_post, rendered_sqls)

        assert result.success is True
        assert len(result.pre_results) == 1
        assert result.pre_results[0].phase == "pre"
        assert result.pre_results[0].statement_name == "delete_partition"
        assert result.main_result is not None
        assert len(result.post_results) == 1
        assert result.post_results[0].phase == "post"
        assert result.post_results[0].statement_name == "optimize"
        assert len(mock_executor.executed_sqls) == 3

    def test_execute_skip_pre(self, spec_with_pre_post):
        """Test skipping pre statements."""
        mock_executor = MockExecutor()
        executor = DatasetExecutor(mock_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t WHERE dt = '2024-01-01'"],
            "main": "INSERT INTO t SELECT 1",
            "post": ["ALTER TABLE t EXECUTE optimize"],
        }
        result = executor.execute(spec_with_pre_post, rendered_sqls, skip_pre=True)

        assert result.success is True
        assert len(result.pre_results) == 0
        assert result.main_result is not None
        assert len(result.post_results) == 1
        assert len(mock_executor.executed_sqls) == 2

    def test_execute_skip_post(self, spec_with_pre_post):
        """Test skipping post statements."""
        mock_executor = MockExecutor()
        executor = DatasetExecutor(mock_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t WHERE dt = '2024-01-01'"],
            "main": "INSERT INTO t SELECT 1",
            "post": ["ALTER TABLE t EXECUTE optimize"],
        }
        result = executor.execute(spec_with_pre_post, rendered_sqls, skip_post=True)

        assert result.success is True
        assert len(result.pre_results) == 1
        assert result.main_result is not None
        assert len(result.post_results) == 0
        assert len(mock_executor.executed_sqls) == 2

    def test_pre_failure_stops_execution(self, spec_with_pre_post):
        """Test that pre statement failure stops execution."""
        failing_executor = FailingMockExecutor(
            fail_on_statements=[0],
            error_message="Pre statement failed",
        )
        executor = DatasetExecutor(failing_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t WHERE dt = '2024-01-01'"],
            "main": "INSERT INTO t SELECT 1",
            "post": ["ALTER TABLE t EXECUTE optimize"],
        }
        result = executor.execute(spec_with_pre_post, rendered_sqls)

        assert result.success is False
        assert "Pre statement" in result.error_message
        assert len(result.pre_results) == 1
        assert result.main_result is None
        assert len(result.post_results) == 0
        # Only pre statement was executed
        assert len(failing_executor.executed_sqls) == 1

    def test_main_failure_stops_post(self, spec_with_pre_post):
        """Test that main query failure stops post statements."""
        failing_executor = FailingMockExecutor(
            fail_on_statements=[1],  # Main is at index 1
            error_message="Main query failed",
        )
        executor = DatasetExecutor(failing_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t WHERE dt = '2024-01-01'"],
            "main": "INSERT INTO t SELECT 1",
            "post": ["ALTER TABLE t EXECUTE optimize"],
        }
        result = executor.execute(spec_with_pre_post, rendered_sqls)

        assert result.success is False
        assert "Main query" in result.error_message
        assert len(result.pre_results) == 1
        assert result.main_result is not None
        assert result.main_result.success is False
        assert len(result.post_results) == 0
        assert len(failing_executor.executed_sqls) == 2

    def test_continue_on_error(self):
        """Test continue_on_error for pre statements."""
        spec = DatasetSpec(
            name="test.test.test",
            owner="owner@example.com",
            team="@team",
            query_type=QueryType.DML,
            query_statement="INSERT INTO t SELECT 1",
            pre_statements=[
                StatementDefinition(
                    name="cleanup",
                    sql="DELETE FROM t",
                    continue_on_error=True,
                ),
            ],
            execution=ExecutionConfig(timeout_seconds=300),
        )

        failing_executor = FailingMockExecutor(
            fail_on_statements=[0],
            error_message="Cleanup failed",
        )
        executor = DatasetExecutor(failing_executor)

        rendered_sqls = {
            "pre": ["DELETE FROM t"],
            "main": "INSERT INTO t SELECT 1",
        }
        result = executor.execute(spec, rendered_sqls)

        # Despite pre statement failure, execution continues
        assert result.success is True
        assert len(result.pre_results) == 1
        assert result.pre_results[0].success is False
        assert result.main_result is not None
        assert result.main_result.success is True

    def test_total_execution_time(self, simple_spec):
        """Test that total execution time is calculated."""
        mock_executor = MockExecutor()
        executor = DatasetExecutor(mock_executor)

        rendered_sqls = {"main": "INSERT INTO t SELECT 1"}
        result = executor.execute(simple_spec, rendered_sqls)

        assert result.total_execution_time_ms >= 0


class TestServerExecutor:
    """Tests for ServerExecutor class."""

    def test_init_default_values(self):
        """Test initialization with default values."""
        executor = ServerExecutor()

        assert executor.server_url is None
        assert executor.api_token is None
        assert executor.timeout == 300
        assert executor._client is None

    def test_init_custom_values(self):
        """Test initialization with custom values."""
        executor = ServerExecutor(
            server_url="http://localhost:8081",
            api_token="test-token",
            timeout=600,
        )

        assert executor.server_url == "http://localhost:8081"
        assert executor.api_token == "test-token"
        assert executor.timeout == 600

    def test_client_lazy_initialization(self):
        """Test that client is lazily initialized."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        # Client should not be created yet
        assert executor._client is None

        # Mock create_client to avoid actual client creation
        with patch("dli.core.executor.ServerExecutor.client", new_callable=lambda: property(lambda self: MagicMock())):
            pass

    @patch("dli.core.client.create_client")
    def test_client_creation(self, mock_create_client):
        """Test that client is created with correct parameters."""
        mock_client = MagicMock()
        mock_create_client.return_value = mock_client

        executor = ServerExecutor(
            server_url="http://localhost:8081",
            api_token="test-token",
            timeout=600,
        )

        # Access client property to trigger creation
        _ = executor.client

        mock_create_client.assert_called_once_with(
            url="http://localhost:8081",
            timeout=600,
            api_key="test-token",
            mock_mode=False,
        )

    def test_execute_success(self):
        """Test successful query execution."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        # Create a mock response
        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "rows": [{"id": 1, "name": "test"}, {"id": 2, "name": "test2"}],
            "row_count": 2,
            "rendered_sql": "SELECT * FROM users",
        }

        # Mock the client
        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute("SELECT * FROM users")

        assert result.success is True
        assert result.row_count == 2
        assert result.columns == ["id", "name"]
        assert result.data == [{"id": 1, "name": "test"}, {"id": 2, "name": "test2"}]
        assert result.rendered_sql == "SELECT * FROM users"
        assert result.error_message is None

    def test_execute_with_params(self):
        """Test query execution with parameters."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "rows": [{"count": 42}],
            "row_count": 1,
            "rendered_sql": "SELECT COUNT(*) FROM users WHERE id = 1",
        }

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute(
            "SELECT COUNT(*) FROM users WHERE id = :id",
            params={"id": 1},
        )

        assert result.success is True
        mock_client.execute_rendered_sql.assert_called_once_with(
            sql="SELECT COUNT(*) FROM users WHERE id = :id",
            parameters={"id": 1},
            execution_timeout=300,
        )

    def test_execute_server_error(self):
        """Test handling of server error response."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = False
        mock_response.error = "Query syntax error"
        mock_response.data = None

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute("SELECT * FROM nonexistent_table")

        assert result.success is False
        assert result.row_count == 0
        assert result.columns == []
        assert result.data == []
        assert "Server execution failed: Query syntax error" in result.error_message

    def test_execute_connection_error(self):
        """Test handling of connection error."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.side_effect = ConnectionError("Connection refused")
        executor._client = mock_client

        result = executor.execute("SELECT 1")

        assert result.success is False
        assert "Server connection error" in result.error_message
        assert "Connection refused" in result.error_message

    def test_execute_unexpected_response_format(self):
        """Test handling of unexpected response format (list instead of dict)."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = [{"id": 1}]  # List instead of dict

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute("SELECT 1")

        assert result.success is False
        assert "Unexpected server response format" in result.error_message

    def test_execute_empty_rows(self):
        """Test execution with empty result set."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "rows": [],
            "row_count": 0,
            "rendered_sql": "SELECT * FROM users WHERE 1=0",
        }

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute("SELECT * FROM users WHERE 1=0")

        assert result.success is True
        assert result.row_count == 0
        assert result.columns == []
        assert result.data == []

    def test_execute_sql_with_timeout(self):
        """Test execute_sql method with explicit timeout."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = True
        mock_response.data = {
            "rows": [{"result": 1}],
            "row_count": 1,
            "rendered_sql": "SELECT 1",
        }

        mock_client = MagicMock()
        mock_client.execute_rendered_sql.return_value = mock_response
        executor._client = mock_client

        result = executor.execute_sql("SELECT 1", timeout=60)

        assert result.success is True
        # Verify timeout was applied
        mock_client.execute_rendered_sql.assert_called_once_with(
            sql="SELECT 1",
            parameters=None,
            execution_timeout=60,
        )
        # Verify timeout is restored
        assert executor.timeout == 300

    def test_test_connection_success(self):
        """Test successful connection test."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = True

        mock_client = MagicMock()
        mock_client.health_check.return_value = mock_response
        executor._client = mock_client

        assert executor.test_connection() is True
        mock_client.health_check.assert_called_once()

    def test_test_connection_failure(self):
        """Test failed connection test."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_response = MagicMock()
        mock_response.success = False

        mock_client = MagicMock()
        mock_client.health_check.return_value = mock_response
        executor._client = mock_client

        assert executor.test_connection() is False

    def test_test_connection_exception(self):
        """Test connection test with exception."""
        executor = ServerExecutor(server_url="http://localhost:8081")

        mock_client = MagicMock()
        mock_client.health_check.side_effect = Exception("Network error")
        executor._client = mock_client

        assert executor.test_connection() is False

    def test_test_connection_no_url(self):
        """Test connection test with no server URL."""
        executor = ServerExecutor()

        assert executor.test_connection() is False

    def test_implements_query_executor_protocol(self):
        """Test that ServerExecutor implements QueryExecutor protocol."""
        from dli.core.executor import QueryExecutor

        executor = ServerExecutor(server_url="http://localhost:8081")

        # Check that it satisfies the protocol
        assert isinstance(executor, QueryExecutor)
        assert hasattr(executor, "execute")
        assert hasattr(executor, "test_connection")
