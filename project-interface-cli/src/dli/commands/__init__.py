"""DLI CLI Commands.

This package contains individual command implementations for the DLI CLI.
Each command is defined in its own module for better organization.

Commands:
- version: Display CLI version information
- info: Display CLI and environment information
- metric: Metric management and execution subcommand (list, get, run, validate, register)
- dataset: Dataset management and execution subcommand (list, get, run, validate, register)
- config: Configuration management subcommand (show, status)
- lineage: Data lineage commands (table-level, server-based)
- quality: Data quality testing subcommand
- workflow: Workflow execution and management (server-based via Airflow)
- catalog: Data catalog browsing and search (server-based)
- query: Query execution metadata (list, show, cancel)
- transpile: SQL transpile operations (table substitution, METRIC expansion)
"""

from dli.commands.catalog import catalog_app
from dli.commands.config import config_app
from dli.commands.dataset import dataset_app
from dli.commands.info import info
from dli.commands.lineage import lineage_app
from dli.commands.metric import metric_app
from dli.commands.quality import quality_app
from dli.commands.query import query_app
from dli.commands.transpile import transpile_app
from dli.commands.version import version
from dli.commands.workflow import workflow_app

__all__ = [
    "catalog_app",
    "config_app",
    "dataset_app",
    "info",
    "lineage_app",
    "metric_app",
    "quality_app",
    "query_app",
    "transpile_app",
    "version",
    "workflow_app",
]
