"""Format configuration loading.

This module handles loading format configuration from .sqlfluff and .dli-format.yaml files.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

import yaml

# Supported SQL dialects for formatting
SUPPORTED_DIALECTS: list[str] = [
    "bigquery",
    "trino",
    "snowflake",
    "sparksql",
    "hive",
    "postgres",
    "mysql",
    "duckdb",
    "sqlite",
    "redshift",
    "tsql",
]

# Default dialect if none specified
DEFAULT_DIALECT = "bigquery"

# DLI standard key order for YAML files
DLI_YAML_KEY_ORDER: list[str] = [
    # 1. Required fields
    "name",
    "owner",
    "team",
    "type",
    "query_type",
    # 2. Description
    "description",
    # 3. Classification
    "domains",
    "tags",
    # 4. SQL definition
    "query_file",
    "query_statement",
    # 5. Parameters
    "parameters",
    # 6. Execution settings
    "execution",
    # 7. Dependencies
    "depends_on",
    # 8. Schema
    "schema",
    # 9. Dataset-specific
    "pre_statements",
    "post_statements",
    # 10. Versions
    "versions",
]


@dataclass
class YamlFormatConfig:
    """YAML formatting configuration.

    Attributes:
        indent: Indentation spaces (default: 2).
        line_width: Maximum line width (default: 120).
        preserve_quotes: Whether to preserve quote styles (default: True).
        key_order: Key ordering strategy (dli_standard or none).
    """

    indent: int = 2
    line_width: int = 120
    preserve_quotes: bool = True
    key_order: str = "dli_standard"


@dataclass
class SqlFormatConfig:
    """SQL formatting configuration.

    Attributes:
        dialect: SQL dialect (bigquery, trino, snowflake, etc.).
        use_project_sqlfluff: Whether to use project .sqlfluff file.
        indent_unit: Indentation unit (space or tab).
        tab_space_size: Number of spaces per tab.
        max_line_length: Maximum line length.
    """

    dialect: str = DEFAULT_DIALECT
    use_project_sqlfluff: bool = True
    indent_unit: str = "space"
    tab_space_size: int = 4
    max_line_length: int = 120


@dataclass
class BackupConfig:
    """Backup configuration.

    Attributes:
        enabled: Whether to create backups before modifying.
        suffix: Backup file suffix.
    """

    enabled: bool = True
    suffix: str = ".bak"


@dataclass
class FormatConfig:
    """Complete format configuration.

    Attributes:
        yaml: YAML formatting settings.
        sql: SQL formatting settings.
        backup: Backup settings.
        project_path: Path to the project root.
        sqlfluff_path: Path to .sqlfluff file (if found).
        dli_format_path: Path to .dli-format.yaml file (if found).
    """

    yaml: YamlFormatConfig = field(default_factory=YamlFormatConfig)
    sql: SqlFormatConfig = field(default_factory=SqlFormatConfig)
    backup: BackupConfig = field(default_factory=BackupConfig)
    project_path: Path | None = None
    sqlfluff_path: Path | None = None
    dli_format_path: Path | None = None


def load_format_config(project_path: Path | None = None) -> FormatConfig:
    """Load format configuration from project files.

    Configuration is loaded from:
    1. .sqlfluff file (if present)
    2. .dli-format.yaml file (if present)

    Args:
        project_path: Path to the project root. If None, uses current directory.

    Returns:
        FormatConfig with merged settings.
    """
    config = FormatConfig()

    if project_path is None:
        project_path = Path.cwd()

    config.project_path = project_path

    # Try to load .sqlfluff
    sqlfluff_path = project_path / ".sqlfluff"
    if sqlfluff_path.exists():
        config.sqlfluff_path = sqlfluff_path
        _load_sqlfluff_config(config, sqlfluff_path)

    # Try to load .dli-format.yaml
    dli_format_path = project_path / ".dli-format.yaml"
    if dli_format_path.exists():
        config.dli_format_path = dli_format_path
        _load_dli_format_config(config, dli_format_path)

    return config


def _load_sqlfluff_config(config: FormatConfig, path: Path) -> None:
    """Load settings from .sqlfluff file.

    Args:
        config: FormatConfig to update.
        path: Path to .sqlfluff file.
    """
    try:
        import configparser

        parser = configparser.ConfigParser()
        parser.read(path)

        if "sqlfluff" in parser:
            section = parser["sqlfluff"]
            if "dialect" in section:
                config.sql.dialect = section["dialect"]
            if "max_line_length" in section:
                config.sql.max_line_length = int(section["max_line_length"])

        if "sqlfluff:indentation" in parser:
            section = parser["sqlfluff:indentation"]
            if "indent_unit" in section:
                config.sql.indent_unit = section["indent_unit"]
            if "tab_space_size" in section:
                config.sql.tab_space_size = int(section["tab_space_size"])

    except Exception:
        # If parsing fails, use defaults
        pass


def _load_dli_format_config(config: FormatConfig, path: Path) -> None:
    """Load settings from .dli-format.yaml file.

    Args:
        config: FormatConfig to update.
        path: Path to .dli-format.yaml file.
    """
    try:
        with open(path) as f:
            data: dict[str, Any] = yaml.safe_load(f) or {}

        format_section = data.get("format", {})

        # Load YAML settings
        yaml_settings = format_section.get("yaml", {})
        if "indent" in yaml_settings:
            config.yaml.indent = int(yaml_settings["indent"])
        if "line_width" in yaml_settings:
            config.yaml.line_width = int(yaml_settings["line_width"])
        if "preserve_quotes" in yaml_settings:
            config.yaml.preserve_quotes = bool(yaml_settings["preserve_quotes"])
        if "key_order" in yaml_settings:
            config.yaml.key_order = yaml_settings["key_order"]

        # Load SQL settings (lower priority than .sqlfluff)
        sql_settings = format_section.get("sql", {})
        if "dialect" in sql_settings and config.sqlfluff_path is None:
            config.sql.dialect = sql_settings["dialect"]
        if "use_project_sqlfluff" in sql_settings:
            config.sql.use_project_sqlfluff = bool(sql_settings["use_project_sqlfluff"])

        # Load backup settings
        backup_settings = format_section.get("backup", {})
        if "enabled" in backup_settings:
            config.backup.enabled = bool(backup_settings["enabled"])
        if "suffix" in backup_settings:
            config.backup.suffix = backup_settings["suffix"]

    except Exception:
        # If parsing fails, use defaults
        pass


def get_dialect_from_config(
    dialect: str | None,
    config: FormatConfig | None = None,
) -> str:
    """Get the effective dialect to use.

    Priority:
    1. Explicit dialect parameter
    2. .sqlfluff file
    3. .dli-format.yaml file
    4. Default (bigquery)

    Args:
        dialect: Explicitly specified dialect (or None).
        config: Loaded format configuration (or None).

    Returns:
        The dialect to use for formatting.
    """
    if dialect is not None:
        return dialect

    if config is not None:
        return config.sql.dialect

    return DEFAULT_DIALECT


__all__ = [
    "DEFAULT_DIALECT",
    "DLI_YAML_KEY_ORDER",
    "SUPPORTED_DIALECTS",
    "BackupConfig",
    "FormatConfig",
    "SqlFormatConfig",
    "YamlFormatConfig",
    "get_dialect_from_config",
    "load_format_config",
]
