"""DLI CLI Commands.

This package contains individual command implementations for the DLI CLI.
Each command is defined in its own module for better organization.

Commands:
- version: Display CLI version information
- info: Display CLI and environment information
- metric: Metric management and execution subcommand (list, get, run, validate, register, transpile)
- dataset: Dataset management and execution subcommand (list, get, run, validate, register, transpile)
- config: Configuration management subcommand (show, status)
- debug: Environment diagnostics and connection testing
- lineage: Data lineage commands (table-level, server-based)
- quality: Data quality testing subcommand
- workflow: Workflow execution and management (server-based via Airflow)
- catalog: Data catalog browsing and search (server-based)
- query: Query execution metadata (list, show, cancel)
- run: Ad-hoc SQL execution with result download
- sql: SQL snippet management (list, get, put)
"""

from dli.commands.catalog import catalog_app
from dli.commands.config import config_app
from dli.commands.dataset import dataset_app
from dli.commands.debug import debug_app
from dli.commands.info import info
from dli.commands.lineage import lineage_app
from dli.commands.metric import metric_app
from dli.commands.quality import quality_app
from dli.commands.query import query_app
from dli.commands.run import run_app
from dli.commands.sql import sql_app
from dli.commands.version import version
from dli.commands.workflow import workflow_app

__all__ = [
    "catalog_app",
    "config_app",
    "dataset_app",
    "debug_app",
    "info",
    "lineage_app",
    "metric_app",
    "quality_app",
    "query_app",
    "run_app",
    "sql_app",
    "version",
    "workflow_app",
]
