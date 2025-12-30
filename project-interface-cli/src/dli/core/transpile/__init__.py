"""
SQL Transpile module for DLI CLI.

This module provides SQL transformation capabilities including:
- Table substitution based on server-defined rules
- METRIC() function expansion to SQL expressions
- SQL pattern analysis and warnings

Usage:
    from dli.core.transpile import TranspileEngine, TranspileConfig

    engine = TranspileEngine(config=TranspileConfig())
    result = engine.transpile("SELECT * FROM users")
"""

from dli.core.transpile.client import (
    MockTranspileClient,
    TranspileRuleClient,
)
from dli.core.transpile.engine import TranspileEngine
from dli.core.transpile.exceptions import (
    MetricNotFoundError,
    RuleFetchError,
    SqlParseError,
    TranspileError,
)
from dli.core.transpile.metrics import (
    METRIC_PATTERN,
    expand_metrics,
    find_metric_functions,
)
from dli.core.transpile.models import (
    DIALECT_MAP,
    Dialect,
    MetricDefinition,
    MetricMatch,
    RuleType,
    TranspileConfig,
    TranspileMetadata,
    TranspileResult,
    TranspileRule,
    TranspileWarning,
    WarningType,
)
from dli.core.transpile.rules import apply_table_substitutions
from dli.core.transpile.warnings import detect_warnings

__all__ = [
    "DIALECT_MAP",
    "METRIC_PATTERN",
    "Dialect",
    "MetricDefinition",
    "MetricMatch",
    "MetricNotFoundError",
    "MockTranspileClient",
    "RuleFetchError",
    "RuleType",
    "SqlParseError",
    "TranspileConfig",
    "TranspileEngine",
    "TranspileError",
    "TranspileMetadata",
    "TranspileResult",
    "TranspileRule",
    "TranspileRuleClient",
    "TranspileWarning",
    "WarningType",
    "apply_table_substitutions",
    "detect_warnings",
    "expand_metrics",
    "find_metric_functions",
]
