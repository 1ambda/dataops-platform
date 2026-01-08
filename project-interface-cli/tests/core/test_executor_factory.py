"""Tests for ExecutorFactory and related classes.

Covers:
- ExecutorFactory: Factory for creating executors based on ExecutionMode
- QueryExecutor: Protocol for query executors
- ServerExecutor: Server-side execution stub
"""

from __future__ import annotations

import pytest

from dli.core.executor import (
    BaseExecutor,
    ExecutorFactory,
    MockExecutor,
    QueryExecutor,
    ServerExecutor,
)
from dli.models.common import ExecutionContext, ExecutionMode


class TestQueryExecutorProtocol:
    """Tests for QueryExecutor protocol."""

    def test_mock_executor_is_base_executor(self) -> None:
        """Test that MockExecutor is a BaseExecutor.

        Note: MockExecutor uses execute_sql (BaseExecutor interface) rather
        than execute (QueryExecutor interface). The two interfaces serve
        different purposes in the architecture.
        """
        executor = MockExecutor()
        assert isinstance(executor, BaseExecutor)

    def test_server_executor_implements_protocol(self) -> None:
        """Test that ServerExecutor satisfies QueryExecutor protocol."""
        executor = ServerExecutor()
        assert isinstance(executor, QueryExecutor)


class TestServerExecutor:
    """Tests for ServerExecutor stub."""

    def test_init_with_defaults(self) -> None:
        """Test ServerExecutor with default values."""
        executor = ServerExecutor()
        assert executor.server_url is None
        assert executor.api_token is None

    def test_init_with_values(self) -> None:
        """Test ServerExecutor with explicit values."""
        executor = ServerExecutor(
            server_url="https://example.com",
            api_token="secret-token",
        )
        assert executor.server_url == "https://example.com"
        assert executor.api_token == "secret-token"

    def test_execute_returns_failed_result_without_server(self) -> None:
        """Test that execute returns failed result when no server is configured."""
        executor = ServerExecutor()
        result = executor.execute("SELECT 1")
        # Without server_url, execute should return a failed result
        assert result.success is False
        assert "server" in result.error_message.lower() or "connection" in result.error_message.lower()

    def test_test_connection_returns_false_when_no_url(self) -> None:
        """Test test_connection returns False when server_url is not set."""
        executor = ServerExecutor()
        assert executor.test_connection() is False


class TestExecutorFactory:
    """Tests for ExecutorFactory."""

    def test_create_mock_executor(self) -> None:
        """Test creating MockExecutor for MOCK mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        executor = ExecutorFactory.create(ExecutionMode.MOCK, ctx)

        assert isinstance(executor, MockExecutor)

    def test_create_server_executor(self) -> None:
        """Test creating ServerExecutor for SERVER mode."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="https://example.com",
            api_token="secret-token",
        )
        executor = ExecutorFactory.create(ExecutionMode.SERVER, ctx)

        assert isinstance(executor, ServerExecutor)
        assert executor.server_url == "https://example.com"
        assert executor.api_token == "secret-token"

    def test_create_local_executor_unsupported_engine_raises(self) -> None:
        """Test that unsupported engine raises ValueError for LOCAL mode."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            dialect="snowflake",  # Not implemented
        )

        with pytest.raises(ValueError, match="Unsupported engine for LOCAL mode"):
            ExecutorFactory.create(ExecutionMode.LOCAL, ctx)

    def test_create_local_trino_executor(self) -> None:
        """Test creating TrinoExecutor for LOCAL mode with trino dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            dialect="trino",
            parameters={"host": "trino.example.com", "port": 8080},
        )

        # This will fail if trino package is not installed, which is expected
        # in CI environments. We just verify the factory logic.
        try:
            from dli.adapters.trino import TRINO_AVAILABLE
            if not TRINO_AVAILABLE:
                pytest.skip("trino package not installed")
            executor = ExecutorFactory.create(ExecutionMode.LOCAL, ctx)
            from dli.adapters.trino import TrinoExecutor
            assert isinstance(executor, TrinoExecutor)
        except ImportError:
            pytest.skip("trino package not installed")

    def test_create_unknown_mode_raises(self) -> None:
        """Test that unknown mode raises ValueError."""
        ctx = ExecutionContext()

        # This shouldn't happen in normal usage, but test the match case
        with pytest.raises(ValueError, match="Unknown execution mode"):
            ExecutorFactory.create("invalid_mode", ctx)  # type: ignore[arg-type]
