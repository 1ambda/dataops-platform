"""DataOps CLI - Command-line interface for DataOps platform operations.

This module provides the main CLI application using Typer, following 2025 best practices
for CLI design with rich terminal output.

Commands:
    version: Display CLI version information
    validate: Validate SQL files or spec files
    list: List datasets and specs in the project
    render: Render SQL templates with parameters
    config: Manage CLI configuration

Example:
    $ dli --help
    $ dli version
    $ dli validate path/to/spec.yaml
    $ dli list --type dataset
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from rich.console import Console
from rich.panel import Panel
from rich.table import Table
import typer

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
error_console = Console(stderr=True)


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


@app.command()
def version() -> None:
    """Display CLI version information."""
    from dli import __version__

    console.print(
        Panel(
            f"[bold blue]dli[/bold blue] version [green]{__version__}[/green]",
            title="DataOps CLI",
            border_style="blue",
        )
    )


@app.command()
def validate(
    path: Annotated[
        Path,
        typer.Argument(
            help="Path to SQL file or spec file to validate.",
            exists=True,
            readable=True,
        ),
    ],
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            "-d",
            help="SQL dialect for validation (e.g., trino, bigquery, postgres).",
        ),
    ] = "trino",
    strict: Annotated[
        bool,
        typer.Option(
            "--strict",
            "-s",
            help="Enable strict validation mode (fail on warnings).",
        ),
    ] = False,
) -> None:
    """Validate SQL files or spec files.

    Validates SQL syntax and provides warnings for potential issues
    like SELECT * usage or missing LIMIT clauses.

    Examples:
        dli validate query.sql
        dli validate spec.yaml --dialect bigquery
        dli validate query.sql --strict
    """
    from dli.core.validator import SQLValidator

    validator = SQLValidator(dialect=dialect)

    try:
        content = path.read_text(encoding="utf-8")
    except Exception as e:
        error_console.print(f"[red]Error reading file:[/red] {e}")
        raise typer.Exit(1)

    # Handle YAML spec files
    if path.suffix in (".yaml", ".yml"):
        console.print(f"[yellow]Spec file validation not yet implemented:[/yellow] {path}")
        raise typer.Exit(0)

    # Validate SQL
    result = validator.validate(content)

    if result.is_valid:
        console.print(f"[green]Valid SQL:[/green] {path}")
        if result.warnings:
            console.print(f"[yellow]Warnings ({len(result.warnings)}):[/yellow]")
            for warning in result.warnings:
                console.print(f"  - {warning}")
            if strict:
                error_console.print("[red]Strict mode: failing due to warnings[/red]")
                raise typer.Exit(1)
    else:
        error_console.print(f"[red]Invalid SQL:[/red] {path}")
        for error in result.errors:
            error_console.print(f"  - {error}")
        raise typer.Exit(1)


@app.command(name="list")
def list_specs(
    spec_type: Annotated[
        str | None,
        typer.Option(
            "--type",
            "-t",
            help="Filter by spec type (dataset or metric).",
        ),
    ] = None,
    path: Annotated[
        Path | None,
        typer.Option(
            "--path",
            "-p",
            help="Path to search for specs (defaults to current directory).",
            exists=True,
            dir_okay=True,
            file_okay=False,
        ),
    ] = None,
    format_output: Annotated[
        str,
        typer.Option(
            "--format",
            "-f",
            help="Output format (table or json).",
        ),
    ] = "table",
) -> None:
    """List datasets and specs in the project.

    Discovers and displays all spec files in the specified directory.
    Requires a dli.yaml configuration file in the search path.

    Examples:
        dli list
        dli list --type dataset
        dli list --path ./project --format json
    """
    from dli.core.discovery import SpecDiscovery, load_project
    from dli.core.models import SpecType

    search_path = path or Path.cwd()

    # Try to find dli.yaml configuration
    config_path = search_path / "dli.yaml"
    if not config_path.exists():
        # Try parent directories
        for parent in search_path.parents:
            candidate = parent / "dli.yaml"
            if candidate.exists():
                config_path = candidate
                break
        else:
            console.print(
                "[yellow]No dli.yaml configuration found.[/yellow]\n"
                "Create a dli.yaml file to define your project structure."
            )
            raise typer.Exit(0)

    try:
        project_config = load_project(config_path)
        discovery = SpecDiscovery(project_config)
        specs = list(discovery.discover_all())
    except Exception as e:
        error_console.print(f"[red]Error discovering specs:[/red] {e}")
        raise typer.Exit(1)

    # Filter by type if specified
    if spec_type:
        spec_type_upper = spec_type.upper()
        if spec_type_upper == "DATASET":
            specs = [s for s in specs if s.type == SpecType.DATASET]
        elif spec_type_upper == "METRIC":
            specs = [s for s in specs if s.type == SpecType.METRIC]
        else:
            error_console.print(f"[red]Unknown spec type:[/red] {spec_type}. Use 'dataset' or 'metric'.")
            raise typer.Exit(1)

    if not specs:
        console.print("[yellow]No specs found.[/yellow]")
        raise typer.Exit(0)

    if format_output == "json":
        output = [
            {
                "name": spec.name,
                "type": spec.type.value,
                "owner": spec.owner,
                "team": spec.team,
            }
            for spec in specs
        ]
        console.print_json(data=output)
    else:
        table = Table(title=f"Specs in {search_path}")
        table.add_column("Name", style="cyan", no_wrap=True)
        table.add_column("Type", style="magenta")
        table.add_column("Owner", style="green")
        table.add_column("Team", style="yellow")

        for spec in specs:
            table.add_row(
                spec.name,
                spec.type.value,
                spec.owner,
                spec.team,
            )

        console.print(table)

    console.print(f"\n[dim]Found {len(specs)} spec(s)[/dim]")


@app.command()
def render(
    path: Annotated[
        Path,
        typer.Argument(
            help="Path to SQL template file to render.",
            exists=True,
            readable=True,
        ),
    ],
    params: Annotated[
        list[str] | None,
        typer.Option(
            "--param",
            "-p",
            help="Parameter in key=value format. Can be used multiple times.",
        ),
    ] = None,
    execution_date: Annotated[
        str | None,
        typer.Option(
            "--date",
            "-d",
            help="Execution date in YYYY-MM-DD format.",
        ),
    ] = None,
    output: Annotated[
        Path | None,
        typer.Option(
            "--output",
            "-o",
            help="Output file path (defaults to stdout).",
        ),
    ] = None,
) -> None:
    """Render SQL templates with parameters.

    Renders Jinja2 SQL templates with the provided parameters and
    execution context (dates, variables).

    Examples:
        dli render query.sql
        dli render query.sql --param dt=2025-01-01
        dli render query.sql --date 2025-01-01 --output rendered.sql
    """
    from datetime import date

    from dli.core.renderer import SQLRenderer

    try:
        template_str = path.read_text(encoding="utf-8")
    except Exception as e:
        error_console.print(f"[red]Error reading template:[/red] {e}")
        raise typer.Exit(1)

    # Parse parameters
    param_dict: dict[str, str] = {}
    if params:
        for param in params:
            if "=" not in param:
                error_console.print(f"[red]Invalid parameter format:[/red] {param}. Use key=value format.")
                raise typer.Exit(1)
            key, value = param.split("=", 1)
            param_dict[key] = value

    # Parse execution date
    exec_date = None
    if execution_date:
        try:
            exec_date = date.fromisoformat(execution_date)
        except ValueError as e:
            error_console.print(f"[red]Invalid date format:[/red] {e}. Use YYYY-MM-DD format.")
            raise typer.Exit(1)

    try:
        renderer = SQLRenderer(templates_dir=path.parent)
        rendered = renderer.render_with_template_context(
            template_str,
            execution_date=exec_date,
            extra_params=param_dict,
        )
    except Exception as e:
        error_console.print(f"[red]Error rendering template:[/red] {e}")
        raise typer.Exit(1)

    if output:
        try:
            output.write_text(rendered, encoding="utf-8")
            console.print(f"[green]Rendered SQL written to:[/green] {output}")
        except Exception as e:
            error_console.print(f"[red]Error writing output:[/red] {e}")
            raise typer.Exit(1)
    else:
        console.print(rendered)


@app.command()
def info() -> None:
    """Display CLI and environment information."""
    import platform
    import sys

    from dli import __version__

    table = Table(title="DLI Environment Information")
    table.add_column("Property", style="cyan")
    table.add_column("Value", style="green")

    table.add_row("CLI Version", __version__)
    table.add_row("Python Version", sys.version.split()[0])
    table.add_row("Platform", platform.platform())
    table.add_row("Python Path", sys.executable)

    # Check for optional dependencies
    optional_deps = [
        ("sqlglot", "SQL parsing"),
        ("pydantic", "Data validation"),
        ("jinja2", "Template rendering"),
        ("httpx", "HTTP client"),
        ("rich", "Terminal output"),
    ]

    for module, description in optional_deps:
        try:
            mod = __import__(module)
            version = getattr(mod, "__version__", "installed")
            table.add_row(f"{description} ({module})", version)
        except ImportError:
            table.add_row(f"{description} ({module})", "[red]not installed[/red]")

    console.print(table)


# Entry point for the CLI
if __name__ == "__main__":
    app()
