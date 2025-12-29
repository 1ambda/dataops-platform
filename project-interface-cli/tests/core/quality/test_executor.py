"""Tests for QualityExecutor.

This module tests the quality test execution capabilities,
including local and server execution modes.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any
from unittest.mock import Mock, patch

import pytest

from dli.core.executor import MockExecutor
from dli.core.quality.executor import QualityExecutor, create_executor
from dli.core.quality.models import (
    DqSeverity,
    DqStatus,
    DqTestConfig,
    DqTestDefinition,
    DqTestResult,
    DqTestType,
    QualityReport,
)


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def mock_sql_executor() -> MockExecutor:
    """Create a mock SQL executor that returns no failing rows."""
    return MockExecutor(mock_data=[])


@pytest.fixture
def mock_sql_executor_with_failures() -> MockExecutor:
    """Create a mock SQL executor that returns failing rows."""
    return MockExecutor(
        mock_data=[
            {"user_id": None, "email": "test@example.com"},
            {"user_id": None, "email": "test2@example.com"},
        ]
    )


@pytest.fixture
def mock_sql_executor_error() -> MockExecutor:
    """Create a mock SQL executor that always fails."""
    return MockExecutor(should_fail=True, error_message="Connection failed")


@pytest.fixture
def mock_client() -> Mock:
    """Create a mock BasecampClient."""
    client = Mock()
    return client


@pytest.fixture
def sample_not_null_test() -> DqTestDefinition:
    """Create a sample NOT NULL test definition."""
    return DqTestDefinition(
        name="test_user_id_not_null",
        test_type=DqTestType.NOT_NULL,
        resource_name="iceberg.analytics.users",
        columns=["user_id"],
    )


@pytest.fixture
def sample_unique_test() -> DqTestDefinition:
    """Create a sample UNIQUE test definition."""
    return DqTestDefinition(
        name="test_email_unique",
        test_type=DqTestType.UNIQUE,
        resource_name="iceberg.analytics.users",
        columns=["email"],
    )


@pytest.fixture
def sample_disabled_test() -> DqTestDefinition:
    """Create a disabled test definition."""
    return DqTestDefinition(
        name="disabled_test",
        test_type=DqTestType.NOT_NULL,
        resource_name="iceberg.analytics.users",
        columns=["optional_field"],
        enabled=False,
    )


@pytest.fixture
def sample_singular_test(tmp_path: Path) -> DqTestDefinition:
    """Create a SINGULAR test with SQL file."""
    sql_file = tmp_path / "custom_test.sql"
    sql_file.write_text("SELECT * FROM users WHERE created_at > NOW()")

    return DqTestDefinition(
        name="custom_freshness_test",
        test_type=DqTestType.SINGULAR,
        resource_name="iceberg.analytics.users",
        file=str(sql_file),
    )


@pytest.fixture
def sample_singular_test_inline() -> DqTestDefinition:
    """Create a SINGULAR test with inline SQL."""
    return DqTestDefinition(
        name="inline_freshness_test",
        test_type=DqTestType.SINGULAR,
        resource_name="iceberg.analytics.users",
        sql="SELECT * FROM users WHERE created_at < DATE_SUB(NOW(), INTERVAL 1 DAY)",
    )


# =============================================================================
# Executor Initialization Tests
# =============================================================================


class TestExecutorInitialization:
    """Tests for QualityExecutor initialization."""

    def test_init_with_defaults(self) -> None:
        """Executor should initialize with default config."""
        executor = QualityExecutor()
        assert executor.client is None
        assert executor.sql_executor is None
        assert executor.config is not None
        assert isinstance(executor.config, DqTestConfig)

    def test_init_with_client(self, mock_client: Mock) -> None:
        """Executor should accept a client."""
        executor = QualityExecutor(client=mock_client)
        assert executor.client is mock_client

    def test_init_with_sql_executor(self, mock_sql_executor: MockExecutor) -> None:
        """Executor should accept a SQL executor."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        assert executor.sql_executor is mock_sql_executor

    def test_init_with_custom_config(self) -> None:
        """Executor should accept custom config."""
        config = DqTestConfig(fail_fast=True, limit=50)
        executor = QualityExecutor(config=config)
        assert executor.config.fail_fast is True
        assert executor.config.limit == 50

    def test_create_executor_factory(self) -> None:
        """Factory function should create executor correctly."""
        executor = create_executor()
        assert isinstance(executor, QualityExecutor)


# =============================================================================
# Local Execution Tests
# =============================================================================


class TestLocalExecution:
    """Tests for local test execution."""

    def test_run_passing_test(
        self,
        mock_sql_executor: MockExecutor,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """Test that passes when no failing rows are returned."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(sample_not_null_test)

        assert result.status == DqStatus.PASS
        assert result.failed_rows == 0
        assert result.executed_on == "local"
        assert result.rendered_sql is not None
        assert "IS NULL" in result.rendered_sql

    def test_run_failing_test(
        self,
        mock_sql_executor_with_failures: MockExecutor,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """Test that fails when failing rows are returned."""
        executor = QualityExecutor(sql_executor=mock_sql_executor_with_failures)
        result = executor.run(sample_not_null_test)

        assert result.status == DqStatus.FAIL
        assert result.failed_rows == 2
        assert len(result.failed_samples) == 2

    def test_run_warning_test(
        self,
        mock_sql_executor_with_failures: MockExecutor,
    ) -> None:
        """Test with WARN severity should return WARN status on failure."""
        test = DqTestDefinition(
            name="warning_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["optional_field"],
            severity=DqSeverity.WARN,
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor_with_failures)
        result = executor.run(test)

        assert result.status == DqStatus.WARN
        assert result.failed_rows == 2

    def test_run_disabled_test(
        self,
        mock_sql_executor: MockExecutor,
        sample_disabled_test: DqTestDefinition,
    ) -> None:
        """Disabled tests should be skipped."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(sample_disabled_test)

        assert result.status == DqStatus.SKIPPED
        assert "disabled" in result.error_message.lower()

    def test_run_without_executor_returns_error(
        self,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """Running without a SQL executor should return error."""
        executor = QualityExecutor()  # No SQL executor
        result = executor.run(sample_not_null_test)

        assert result.status == DqStatus.ERROR
        assert "not configured" in result.error_message.lower()

    def test_run_with_executor_error(
        self,
        mock_sql_executor_error: MockExecutor,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """Executor errors should be handled gracefully."""
        executor = QualityExecutor(sql_executor=mock_sql_executor_error)
        result = executor.run(sample_not_null_test)

        assert result.status == DqStatus.ERROR
        assert "Connection failed" in result.error_message

    def test_run_records_execution_time(
        self,
        mock_sql_executor: MockExecutor,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """Execution time should be recorded."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(sample_not_null_test)

        assert result.execution_time_ms >= 0

    def test_run_singular_test_with_file(
        self,
        mock_sql_executor: MockExecutor,
        sample_singular_test: DqTestDefinition,
    ) -> None:
        """SINGULAR test should load SQL from file."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(sample_singular_test)

        assert result.status == DqStatus.PASS
        assert "created_at > NOW()" in result.rendered_sql

    def test_run_singular_test_with_inline_sql(
        self,
        mock_sql_executor: MockExecutor,
        sample_singular_test_inline: DqTestDefinition,
    ) -> None:
        """SINGULAR test should use inline SQL."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(sample_singular_test_inline)

        assert result.status == DqStatus.PASS
        assert "DATE_SUB" in result.rendered_sql

    def test_run_singular_test_missing_file_returns_error(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """SINGULAR test with missing file should return error."""
        test = DqTestDefinition(
            name="missing_file_test",
            test_type=DqTestType.SINGULAR,
            resource_name="users",
            file="/nonexistent/path/test.sql",
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(test)

        assert result.status == DqStatus.ERROR
        assert "not found" in result.error_message.lower()

    def test_run_singular_test_no_sql_or_file_returns_error(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """SINGULAR test with no SQL or file should return error."""
        test = DqTestDefinition(
            name="empty_singular_test",
            test_type=DqTestType.SINGULAR,
            resource_name="users",
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(test)

        assert result.status == DqStatus.ERROR
        assert "no sql or file" in result.error_message.lower()


# =============================================================================
# Server Execution Tests
# =============================================================================


class TestServerExecution:
    """Tests for server-side test execution."""

    def test_run_on_server_passing(self, mock_client: Mock) -> None:
        """Test passing on server execution."""
        mock_response = Mock()
        mock_response.success = True
        mock_response.data = {
            "status": "pass",
            "failed_rows": 0,
            "failed_samples": [],
            "execution_time_ms": 150,
        }
        mock_client.execute_quality_test.return_value = mock_response

        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor(client=mock_client)
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.PASS
        assert result.failed_rows == 0
        assert result.executed_on == "server"
        mock_client.execute_quality_test.assert_called_once()

    def test_run_on_server_failing(self, mock_client: Mock) -> None:
        """Test failing on server execution."""
        mock_response = Mock()
        mock_response.success = True
        mock_response.data = {
            "status": "fail",
            "failed_rows": 5,
            "failed_samples": [{"id": 1}, {"id": 2}],
            "execution_time_ms": 200,
        }
        mock_client.execute_quality_test.return_value = mock_response

        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor(client=mock_client)
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.FAIL
        assert result.failed_rows == 5

    def test_run_on_server_request_failure(self, mock_client: Mock) -> None:
        """Server request failure should return error status."""
        mock_response = Mock()
        mock_response.success = False
        mock_response.error = "Server unavailable"
        mock_client.execute_quality_test.return_value = mock_response

        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor(client=mock_client)
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.ERROR
        assert "unavailable" in result.error_message.lower()

    def test_run_on_server_without_client(self) -> None:
        """Running on server without client should return error."""
        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor()  # No client
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.ERROR
        assert "not configured" in result.error_message.lower()

    def test_run_on_server_exception_handling(self, mock_client: Mock) -> None:
        """Exceptions during server execution should be caught."""
        mock_client.execute_quality_test.side_effect = Exception("Network error")

        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor(client=mock_client)
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.ERROR
        assert "Network error" in result.error_message

    def test_run_on_server_invalid_response_format(self, mock_client: Mock) -> None:
        """Invalid response format should return error."""
        mock_response = Mock()
        mock_response.success = True
        mock_response.data = "invalid string instead of dict"
        mock_client.execute_quality_test.return_value = mock_response

        test = DqTestDefinition(
            name="server_test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        executor = QualityExecutor(client=mock_client)
        result = executor.run(test, on_server=True)

        assert result.status == DqStatus.ERROR
        assert "Invalid" in result.error_message


# =============================================================================
# Run All Tests
# =============================================================================


class TestRunAll:
    """Tests for run_all method."""

    def test_run_all_returns_report(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """run_all should return a QualityReport."""
        tests = [
            DqTestDefinition(
                name="test1",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
            DqTestDefinition(
                name="test2",
                test_type=DqTestType.UNIQUE,
                resource_name="users",
                columns=["email"],
            ),
        ]
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        report = executor.run_all(tests)

        assert isinstance(report, QualityReport)
        assert report.total_tests == 2
        assert report.passed == 2
        assert len(report.results) == 2

    def test_run_all_aggregates_statistics(
        self,
        mock_sql_executor_with_failures: MockExecutor,
    ) -> None:
        """run_all should aggregate pass/fail statistics."""
        tests = [
            DqTestDefinition(
                name="pass_test",
                test_type=DqTestType.ROW_COUNT,  # No failures from mock
                resource_name="users",
            ),
            DqTestDefinition(
                name="fail_test",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
        ]
        # Create executor that returns failures only for NOT_NULL
        executor = QualityExecutor(sql_executor=mock_sql_executor_with_failures)
        report = executor.run_all(tests)

        # Both tests return failures from mock
        assert report.total_tests == 2
        assert report.failed == 2

    def test_run_all_fail_fast(
        self,
        mock_sql_executor_with_failures: MockExecutor,
    ) -> None:
        """fail_fast should stop on first failure."""
        tests = [
            DqTestDefinition(
                name="first_test",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
            DqTestDefinition(
                name="second_test",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["name"],
            ),
            DqTestDefinition(
                name="third_test",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["id"],
            ),
        ]
        config = DqTestConfig(fail_fast=True)
        executor = QualityExecutor(
            sql_executor=mock_sql_executor_with_failures,
            config=config,
        )
        report = executor.run_all(tests)

        assert report.total_tests == 3
        assert report.failed == 1
        assert report.skipped == 2

        # Verify first failed, rest skipped
        assert report.results[0].status == DqStatus.FAIL
        assert report.results[1].status == DqStatus.SKIPPED
        assert report.results[2].status == DqStatus.SKIPPED

    def test_run_all_continues_without_fail_fast(
        self,
        mock_sql_executor_with_failures: MockExecutor,
    ) -> None:
        """Without fail_fast, all tests should run."""
        tests = [
            DqTestDefinition(
                name="test1",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
            DqTestDefinition(
                name="test2",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["name"],
            ),
        ]
        config = DqTestConfig(fail_fast=False)
        executor = QualityExecutor(
            sql_executor=mock_sql_executor_with_failures,
            config=config,
        )
        report = executor.run_all(tests)

        assert report.total_tests == 2
        assert report.failed == 2
        assert report.skipped == 0

    def test_run_all_multiple_resources(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """run_all with multiple resources should report 'multiple_resources'."""
        tests = [
            DqTestDefinition(
                name="test1",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
            DqTestDefinition(
                name="test2",
                test_type=DqTestType.NOT_NULL,
                resource_name="orders",
                columns=["order_id"],
            ),
        ]
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        report = executor.run_all(tests)

        assert report.resource_name == "multiple_resources"

    def test_run_all_single_resource(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """run_all with single resource should report that resource."""
        tests = [
            DqTestDefinition(
                name="test1",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
            DqTestDefinition(
                name="test2",
                test_type=DqTestType.UNIQUE,
                resource_name="users",
                columns=["email"],
            ),
        ]
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        report = executor.run_all(tests)

        assert report.resource_name == "users"

    def test_run_all_empty_list(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """run_all with empty list should return empty report."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        report = executor.run_all([])

        assert report.total_tests == 0
        assert report.success is True

    def test_run_all_on_server(self, mock_client: Mock) -> None:
        """run_all should support server execution."""
        mock_response = Mock()
        mock_response.success = True
        mock_response.data = {
            "status": "pass",
            "failed_rows": 0,
            "failed_samples": [],
            "execution_time_ms": 100,
        }
        mock_client.execute_quality_test.return_value = mock_response

        tests = [
            DqTestDefinition(
                name="test1",
                test_type=DqTestType.NOT_NULL,
                resource_name="users",
                columns=["email"],
            ),
        ]
        executor = QualityExecutor(client=mock_client)
        report = executor.run_all(tests, on_server=True)

        assert report.executed_on == "server"
        assert report.passed == 1


# =============================================================================
# SQL Generation Tests
# =============================================================================


class TestSqlGeneration:
    """Tests for SQL generation from test definitions."""

    def test_generate_not_null_sql(
        self,
        mock_sql_executor: MockExecutor,
        sample_not_null_test: DqTestDefinition,
    ) -> None:
        """NOT NULL test should generate correct SQL."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(sample_not_null_test)

        assert len(mock_sql_executor.executed_sqls) == 1
        sql = mock_sql_executor.executed_sqls[0]
        assert "user_id IS NULL" in sql
        assert "FROM iceberg.analytics.users" in sql

    def test_generate_unique_sql(
        self,
        mock_sql_executor: MockExecutor,
        sample_unique_test: DqTestDefinition,
    ) -> None:
        """UNIQUE test should generate correct SQL."""
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(sample_unique_test)

        sql = mock_sql_executor.executed_sqls[0]
        assert "GROUP BY email" in sql
        assert "HAVING COUNT(*) > 1" in sql

    def test_generate_accepted_values_sql(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """ACCEPTED_VALUES test should generate correct SQL."""
        test = DqTestDefinition(
            name="status_check",
            test_type=DqTestType.ACCEPTED_VALUES,
            resource_name="users",
            columns=["status"],
            params={"values": ["active", "inactive"]},
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(test)

        sql = mock_sql_executor.executed_sqls[0]
        assert "status NOT IN" in sql
        assert "'active'" in sql

    def test_generate_relationships_sql(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """RELATIONSHIPS test should generate correct SQL."""
        test = DqTestDefinition(
            name="fk_check",
            test_type=DqTestType.RELATIONSHIPS,
            resource_name="orders",
            columns=["user_id"],
            params={"to": "users", "to_column": "id"},
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(test)

        sql = mock_sql_executor.executed_sqls[0]
        assert "LEFT JOIN users" in sql
        assert "a.user_id = b.id" in sql

    def test_generate_range_check_sql(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """RANGE_CHECK test should generate correct SQL."""
        test = DqTestDefinition(
            name="amount_range",
            test_type=DqTestType.RANGE_CHECK,
            resource_name="orders",
            columns=["amount"],
            params={"min": 0, "max": 10000},
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(test)

        sql = mock_sql_executor.executed_sqls[0]
        assert "amount < 0" in sql
        assert "amount > 10000" in sql

    def test_generate_row_count_sql(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """ROW_COUNT test should generate correct SQL."""
        test = DqTestDefinition(
            name="min_rows",
            test_type=DqTestType.ROW_COUNT,
            resource_name="users",
            params={"min": 100},
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        executor.run(test)

        sql = mock_sql_executor.executed_sqls[0]
        assert "COUNT(*)" in sql
        assert "cnt < 100" in sql

    def test_missing_required_params_returns_error(
        self,
        mock_sql_executor: MockExecutor,
    ) -> None:
        """Tests with missing required params should return error."""
        test = DqTestDefinition(
            name="bad_relationships",
            test_type=DqTestType.RELATIONSHIPS,
            resource_name="orders",
            columns=["user_id"],
            # Missing 'to' and 'to_column' params
        )
        executor = QualityExecutor(sql_executor=mock_sql_executor)
        result = executor.run(test)

        assert result.status == DqStatus.ERROR
        assert "requires" in result.error_message.lower()


# =============================================================================
# Configuration Tests
# =============================================================================


class TestConfiguration:
    """Tests for TestConfig behavior."""

    def test_limit_applies_to_samples(
        self,
        mock_sql_executor_with_failures: MockExecutor,
    ) -> None:
        """Config limit should restrict failed_samples."""
        # Add more mock data
        mock_sql_executor_with_failures.mock_data = [
            {"id": i} for i in range(10)
        ]

        test = DqTestDefinition(
            name="test",
            test_type=DqTestType.NOT_NULL,
            resource_name="users",
            columns=["email"],
        )
        config = DqTestConfig(limit=3)
        executor = QualityExecutor(
            sql_executor=mock_sql_executor_with_failures,
            config=config,
        )
        result = executor.run(test)

        assert result.failed_rows == 10
        assert len(result.failed_samples) == 3  # Limited to 3

    def test_store_failures_config(self) -> None:
        """store_failures config should be respected."""
        config = DqTestConfig(store_failures=False)
        assert config.store_failures is False

        config = DqTestConfig(store_failures=True)
        assert config.store_failures is True

    def test_default_severity(self) -> None:
        """Default severity should be ERROR."""
        config = DqTestConfig()
        assert config.severity == DqSeverity.ERROR
