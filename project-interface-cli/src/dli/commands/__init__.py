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
"""

from dli.commands.dataset import dataset_app
from dli.commands.info import info
from dli.commands.metric import metric_app
from dli.commands.render import render
from dli.commands.server import server_app
from dli.commands.validate import validate
from dli.commands.version import version

__all__ = [
    "dataset_app",
    "info",
    "metric_app",
    "render",
    "server_app",
    "validate",
    "version",
]
