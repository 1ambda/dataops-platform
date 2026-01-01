"""DataOps CLI - Command-line interface for DataOps platform operations.

This module provides the main CLI application using Typer, following 2025 best practices
for CLI design with rich terminal output.

Commands:
    version: Display CLI version information
    info: Display CLI and environment information
    metric: Metric management (list, get, run, validate, register)
    dataset: Dataset management (list, get, run, validate, register)
    config: Configuration management (show, status)
    debug: Environment diagnostics and connection testing
    lineage: Data lineage queries (upstream, downstream, table-level)
    quality: Data quality testing (list, run, show)
    workflow: Workflow execution and management (run, backfill, stop, status, list, history, pause, unpause)
    catalog: Data catalog browsing and search
    query: Query execution metadata (list, show, cancel)
    run: Ad-hoc SQL execution with result download
    transpile: SQL transpile operations (table substitution, METRIC expansion, warnings)

Example:
    $ dli --help
    $ dli version
    $ dli metric list
    $ dli metric validate iceberg.reporting.user_summary -p date=2024-01-01
    $ dli dataset run iceberg.analytics.daily_clicks -p date=2024-01-01
    $ dli dataset validate iceberg.analytics.daily_clicks -p date=2024-01-01
    $ dli lineage show iceberg.analytics.daily_clicks
    $ dli quality run iceberg.analytics.daily_clicks --server
    $ dli workflow run iceberg.analytics.daily_clicks -p execution_date=2024-01-15
    $ dli catalog my-project.analytics.users
    $ dli query list --scope my
    $ dli run --sql query.sql --output results.csv
    $ dli transpile "SELECT * FROM analytics.users"
"""

from __future__ import annotations

from typing import Annotated

from rich.console import Console
from rich.panel import Panel
import typer

# Import command implementations
from dli.commands import catalog_app
from dli.commands import config_app
from dli.commands import dataset_app
from dli.commands import debug_app
from dli.commands import info as info_cmd
from dli.commands import lineage_app
from dli.commands import metric_app
from dli.commands import quality_app
from dli.commands import query_app
from dli.commands import run_app
from dli.commands import version as version_cmd
from dli.commands import workflow_app

# Create the main Typer app
app = typer.Typer(
    name="dli",
    help="DataOps CLI - Command-line interface for DataOps platform operations.",
    add_completion=False,
    no_args_is_help=True,
    rich_markup_mode="rich",
)

# Console for rich output
console = Console()


def version_callback(value: bool) -> None:
    """Display version and exit."""
    if value:
        from dli import __version__

        console.print(
            Panel(
                f"[bold blue]dli[/bold blue] version [green]{__version__}[/green]",
                title="DataOps CLI",
                border_style="blue",
            )
        )
        raise typer.Exit()


@app.callback()
def main(
    version: Annotated[
        bool,
        typer.Option(
            "--version",
            "-v",
            help="Show version and exit.",
            callback=version_callback,
            is_eager=True,
        ),
    ] = False,
) -> None:
    """DataOps CLI - Command-line interface for DataOps platform operations.

    Use 'dli COMMAND --help' for more information on a specific command.
    """
    pass


# Register commands
app.command()(version_cmd)
app.command()(info_cmd)

# Register subcommand apps
app.add_typer(catalog_app, name="catalog")
app.add_typer(config_app, name="config")
app.add_typer(dataset_app, name="dataset")
app.add_typer(debug_app, name="debug")
app.add_typer(lineage_app, name="lineage")
app.add_typer(metric_app, name="metric")
app.add_typer(quality_app, name="quality")
app.add_typer(query_app, name="query")
app.add_typer(run_app, name="run")
app.add_typer(workflow_app, name="workflow")


# Entry point for the CLI
if __name__ == "__main__":
    app()
