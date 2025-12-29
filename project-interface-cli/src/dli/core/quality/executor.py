"""Quality Test Executor for the DLI Core Engine.

This module provides test execution capabilities for data quality tests,
supporting both local execution (via SQL executor) and server delegation.

Classes:
    QualityExecutor: Main executor that handles local and server test execution

Architecture:
    Local Execution:
        - Requires a configured SQL executor (Trino, BigQuery, etc.)
        - Tests are run directly against the data warehouse
        - Results are returned immediately

    Server Execution:
        - Requires a configured Basecamp client
        - Tests are sent to the server for execution
        - Server has access to production data
        - Useful when local access is restricted
"""

from __future__ import annotations

import logging
import time
from pathlib import Path
from typing import TYPE_CHECKING, Any

from dli.core.quality.builtin_tests import BuiltinTests
from dli.core.quality.models import (
    DqSeverity,
    DqStatus,
    DqTestConfig,
    DqTestDefinition,
    DqTestResult,
    DqTestType,
    QualityReport,
)

if TYPE_CHECKING:
    from dli.core.client import BasecampClient
    from dli.core.executor import BaseExecutor

logger = logging.getLogger(__name__)


class QualityExecutor:
    """Test executor supporting local and server execution.

    This executor manages the execution of data quality tests, either
    locally using a SQL executor or by delegating to a remote server.

    Attributes:
        client: Optional Basecamp client for server execution
        sql_executor: Optional SQL executor for local execution
        config: Test execution configuration
    """

    def __init__(
        self,
        client: BasecampClient | None = None,
        sql_executor: BaseExecutor | None = None,
        config: DqTestConfig | None = None,
    ) -> None:
        """Initialize the quality executor.

        Args:
            client: Basecamp client for server-side test execution
            sql_executor: SQL executor for local test execution
            config: Test execution configuration (defaults provided)
        """
        self.client = client
        self.sql_executor = sql_executor
        self.config = config or DqTestConfig()

    def run(
        self,
        test: DqTestDefinition,
        *,
        on_server: bool = False,
    ) -> DqTestResult:
        """Execute a single test.

        Args:
            test: Test definition to execute
            on_server: If True, delegate to server; if False, run locally

        Returns:
            DqTestResult with execution outcome
        """
        if not test.enabled:
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.SKIPPED,
                error_message="Test is disabled",
                executed_on="local" if not on_server else "server",
            )

        if on_server:
            return self._run_on_server(test)
        return self._run_local(test)

    def run_all(
        self,
        tests: list[DqTestDefinition],
        *,
        on_server: bool = False,
    ) -> QualityReport:
        """Execute multiple tests and return aggregated report.

        Args:
            tests: List of test definitions to execute
            on_server: If True, delegate to server; if False, run locally

        Returns:
            QualityReport with all test results
        """
        results: list[DqTestResult] = []
        resource_names = set()

        for test in tests:
            resource_names.add(test.resource_name)
            result = self.run(test, on_server=on_server)
            results.append(result)

            # Check fail_fast
            if self.config.fail_fast and result.status == DqStatus.FAIL:
                logger.info(f"Fail-fast triggered on test: {test.name}")
                # Mark remaining tests as skipped
                for remaining in tests[tests.index(test) + 1 :]:
                    results.append(
                        DqTestResult(
                            test_name=remaining.name,
                            resource_name=remaining.resource_name,
                            status=DqStatus.SKIPPED,
                            error_message="Skipped due to fail-fast",
                            executed_on="local" if not on_server else "server",
                        )
                    )
                break

        # Determine resource name for report
        if len(resource_names) == 1:
            report_resource = resource_names.pop()
        else:
            report_resource = "multiple_resources"

        return QualityReport.from_results(
            resource_name=report_resource,
            results=results,
            executed_on="server" if on_server else "local",
        )

    def _run_local(self, test: DqTestDefinition) -> DqTestResult:
        """Execute test locally using SQL executor.

        Args:
            test: Test definition to execute

        Returns:
            DqTestResult with execution outcome
        """
        if self.sql_executor is None:
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.ERROR,
                error_message="Local executor not configured. Use --server flag or configure an executor.",
                executed_on="local",
            )

        try:
            sql = self._generate_test_sql(test)
        except ValueError as e:
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.ERROR,
                error_message=f"Failed to generate test SQL: {e}",
                executed_on="local",
            )

        start_time = time.time()

        try:
            result = self.sql_executor.execute_sql(sql)
            execution_time_ms = int((time.time() - start_time) * 1000)

            # Check if execution succeeded
            if not result.success:
                return DqTestResult(
                    test_name=test.name,
                    resource_name=test.resource_name,
                    status=DqStatus.ERROR,
                    error_message=result.error_message or "SQL execution failed",
                    execution_time_ms=execution_time_ms,
                    rendered_sql=sql,
                    executed_on="local",
                )

            # Test passes if no rows returned
            failed_rows = result.row_count or 0

            if failed_rows == 0:
                status = DqStatus.PASS
            elif test.severity == DqSeverity.WARN:
                status = DqStatus.WARN
            else:
                status = DqStatus.FAIL

            # Get sample of failing rows
            failed_samples = (
                result.data[: self.config.limit] if result.data else []
            )

            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=status,
                failed_rows=failed_rows,
                failed_samples=failed_samples,
                execution_time_ms=execution_time_ms,
                rendered_sql=sql,
                executed_on="local",
            )

        except Exception as e:
            execution_time_ms = int((time.time() - start_time) * 1000)
            logger.exception(f"Error executing test {test.name}")
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.ERROR,
                error_message=str(e),
                execution_time_ms=execution_time_ms,
                rendered_sql=sql,
                executed_on="local",
            )

    def _run_on_server(self, test: DqTestDefinition) -> DqTestResult:
        """Execute test on remote server.

        Args:
            test: Test definition to execute

        Returns:
            DqTestResult with execution outcome
        """
        if self.client is None:
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.ERROR,
                error_message="Server client not configured. Configure a Basecamp client or use local execution.",
                executed_on="server",
            )

        start_time = time.time()

        try:
            response = self.client.execute_quality_test(
                resource_name=test.resource_name,
                test_name=test.name,
                test_type=test.test_type.value,
                columns=test.columns,
                params=test.params,
                severity=test.severity.value,
            )

            execution_time_ms = int((time.time() - start_time) * 1000)

            if not response.success:
                return DqTestResult(
                    test_name=test.name,
                    resource_name=test.resource_name,
                    status=DqStatus.ERROR,
                    error_message=response.error or "Server request failed",
                    execution_time_ms=execution_time_ms,
                    executed_on="server",
                )

            # Parse server response
            data = response.data
            if not isinstance(data, dict):
                return DqTestResult(
                    test_name=test.name,
                    resource_name=test.resource_name,
                    status=DqStatus.ERROR,
                    error_message="Invalid server response format",
                    execution_time_ms=execution_time_ms,
                    executed_on="server",
                )

            # Parse status from server
            status_str = data.get("status", "error")
            try:
                status = DqStatus(status_str)
            except ValueError:
                status = DqStatus.ERROR

            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=status,
                failed_rows=data.get("failed_rows", 0),
                failed_samples=data.get("failed_samples", []),
                execution_time_ms=data.get("execution_time_ms", execution_time_ms),
                rendered_sql=data.get("rendered_sql"),
                executed_on="server",
            )

        except Exception as e:
            execution_time_ms = int((time.time() - start_time) * 1000)
            logger.exception(f"Error executing test {test.name} on server")
            return DqTestResult(
                test_name=test.name,
                resource_name=test.resource_name,
                status=DqStatus.ERROR,
                error_message=str(e),
                execution_time_ms=execution_time_ms,
                executed_on="server",
            )

    def _generate_test_sql(self, test: DqTestDefinition) -> str:
        """Generate SQL for a test definition.

        Args:
            test: Test definition to generate SQL for

        Returns:
            SQL query string

        Raises:
            ValueError: If test type is unknown or required fields are missing
        """
        # Handle singular tests (custom SQL)
        if test.test_type == DqTestType.SINGULAR:
            if test.sql:
                return test.sql
            if test.file:
                # Load SQL from file
                file_path = Path(test.file)
                if not file_path.exists():
                    raise ValueError(f"Test SQL file not found: {test.file}")
                return file_path.read_text(encoding="utf-8")
            raise ValueError(f"Singular test '{test.name}' has no SQL or file specified")

        # Generate SQL for built-in test types
        return self._generate_builtin_sql(test)

    def _generate_builtin_sql(self, test: DqTestDefinition) -> str:
        """Generate SQL for a built-in test type.

        Args:
            test: Test definition with built-in type

        Returns:
            Generated SQL query

        Raises:
            ValueError: If required parameters are missing
        """
        table = test.resource_name
        columns = test.columns or []
        params = test.params or {}

        match test.test_type:
            case DqTestType.NOT_NULL:
                if not columns:
                    raise ValueError("not_null test requires at least one column")
                return BuiltinTests.not_null(table, columns)

            case DqTestType.UNIQUE:
                if not columns:
                    raise ValueError("unique test requires at least one column")
                return BuiltinTests.unique(table, columns)

            case DqTestType.ACCEPTED_VALUES:
                column = columns[0] if columns else params.get("column")
                values = params.get("values", [])
                if not column:
                    raise ValueError("accepted_values test requires a column")
                if not values:
                    raise ValueError("accepted_values test requires a values list")
                return BuiltinTests.accepted_values(table, column, values)

            case DqTestType.RELATIONSHIPS:
                column = columns[0] if columns else params.get("column")
                to_table = params.get("to")
                to_column = params.get("to_column")
                if not column:
                    raise ValueError("relationships test requires a column")
                if not to_table:
                    raise ValueError("relationships test requires a 'to' table")
                if not to_column:
                    raise ValueError("relationships test requires a 'to_column'")
                return BuiltinTests.relationships(table, column, to_table, to_column)

            case DqTestType.RANGE_CHECK:
                column = columns[0] if columns else params.get("column")
                min_val = params.get("min")
                max_val = params.get("max")
                if not column:
                    raise ValueError("range_check test requires a column")
                return BuiltinTests.range_check(table, column, min_val, max_val)

            case DqTestType.ROW_COUNT:
                min_count = params.get("min")
                max_count = params.get("max")
                return BuiltinTests.row_count(table, min_count, max_count)

            case _:
                raise ValueError(f"Unknown built-in test type: {test.test_type}")


def create_executor(
    client: BasecampClient | None = None,
    sql_executor: BaseExecutor | None = None,
    config: DqTestConfig | None = None,
) -> QualityExecutor:
    """Factory function to create a QualityExecutor.

    Args:
        client: Optional Basecamp client for server execution
        sql_executor: Optional SQL executor for local execution
        config: Optional test configuration

    Returns:
        Configured QualityExecutor instance
    """
    return QualityExecutor(
        client=client,
        sql_executor=sql_executor,
        config=config,
    )
