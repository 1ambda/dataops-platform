"""DLI CLI Commands.

This package contains individual command implementations for the DLI CLI.
Each command is defined in its own module for better organization.

Commands:
- version: Display CLI version information
- validate: Validate SQL files or spec files
- render: Render SQL templates with parameters
- info: Display CLI and environment information
- metric: Metric management and execution subcommand
- dataset: Dataset management and execution subcommand
- server: Server management subcommand
- lineage: Data lineage commands (table-level, server-based)
- quality: Data quality testing subcommand
- workflow: Workflow execution and management (server-based via Airflow)
- catalog: Data catalog browsing and search (server-based)
- transpile: SQL transpile operations (table substitution, METRIC expansion)
"""

from dli.commands.catalog import catalog_app
from dli.commands.dataset import dataset_app
from dli.commands.info import info
from dli.commands.lineage import lineage_app
from dli.commands.metric import metric_app
from dli.commands.quality import quality_app
from dli.commands.render import render
from dli.commands.server import server_app
from dli.commands.transpile import transpile_app
from dli.commands.validate import validate
from dli.commands.version import version
from dli.commands.workflow import workflow_app

__all__ = [
    "catalog_app",
    "dataset_app",
    "info",
    "lineage_app",
    "metric_app",
    "quality_app",
    "render",
    "server_app",
    "transpile_app",
    "validate",
    "version",
    "workflow_app",
]
