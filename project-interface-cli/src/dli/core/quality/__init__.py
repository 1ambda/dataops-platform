"""Data Quality Module for the DLI Core Engine.

This package provides data quality testing capabilities following dbt-style
patterns for generic and singular tests.

Modules:
    models: Core data models (DqTestType, DqTestDefinition, DqTestResult, etc.)
    builtin_tests: SQL generators for built-in generic tests
    executor: Test execution engine (local and server)

Usage:
    >>> from dli.core.quality import QualityExecutor
    >>> from dli.core.quality import DqTestDefinition, DqTestType, DqStatus

    # Execute tests
    >>> executor = QualityExecutor(client=client)
    >>> report = executor.run_all(tests, on_server=True)

Test Philosophy:
    "A test is a SELECT query that returns rows that fail the test."
    "If any rows are returned, the test fails."

Note:
    Model classes use "Dq" prefix to avoid pytest collection warnings
    (pytest treats classes starting with "Test" as test classes).

References:
    - dbt Data Tests: https://docs.getdbt.com/docs/build/data-tests
    - SQLMesh Audits: https://sqlmesh.readthedocs.io/en/latest/concepts/audits/
"""

from dli.core.quality.builtin_tests import BuiltinTests
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

__all__ = [
    # Models (Dq prefix to avoid pytest collection warnings)
    "DqTestType",
    "DqSeverity",
    "DqStatus",
    "DqTestDefinition",
    "DqTestResult",
    "DqTestConfig",
    "QualityReport",
    # Builtin Tests
    "BuiltinTests",
    # Executor
    "QualityExecutor",
    "create_executor",
]
