"""Format module for SQL and YAML formatting.

This module provides formatting capabilities for DLI specs:
- SQL formatting using sqlfluff with Jinja template preservation
- YAML formatting with DLI standard key ordering and comment preservation

Example:
    >>> from dli.core.format import SqlFormatter, YamlFormatter
    >>> sql_formatter = SqlFormatter(dialect="bigquery")
    >>> result = sql_formatter.format("select a,b from t")
    >>> print(result.formatted)
"""

from dli.core.format.config import (
    DEFAULT_DIALECT,
    DLI_YAML_KEY_ORDER,
    SUPPORTED_DIALECTS,
    BackupConfig,
    FormatConfig,
    SqlFormatConfig,
    YamlFormatConfig,
    get_dialect_from_config,
    load_format_config,
)
from dli.core.format.sql_formatter import SqlFormatResult, SqlFormatter
from dli.core.format.yaml_formatter import (
    YamlFormatResult,
    YamlFormatter,
    get_key_order_position,
)

__all__ = [  # noqa: RUF022 - Grouped by category
    # Config
    "BackupConfig",
    "DEFAULT_DIALECT",
    "DLI_YAML_KEY_ORDER",
    "FormatConfig",
    "SUPPORTED_DIALECTS",
    "SqlFormatConfig",
    "YamlFormatConfig",
    "get_dialect_from_config",
    "load_format_config",
    # SQL Formatter
    "SqlFormatResult",
    "SqlFormatter",
    # YAML Formatter
    "YamlFormatResult",
    "YamlFormatter",
    "get_key_order_position",
]
