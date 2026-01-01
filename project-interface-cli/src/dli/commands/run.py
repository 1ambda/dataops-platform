"""Run subcommand for DLI CLI.

Provides commands for ad-hoc SQL execution with result download.
Executes SQL files against query engines (BigQuery/Trino) and
saves results to local files.

Unlike `dli dataset run` or `dli metric run`, which execute registered specs,
this command runs arbitrary SQL files without registration.
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.syntax import Syntax
import typer

from dli.api.run import RunAPI
from dli.commands.base import get_project_path
from dli.commands.utils import (
    console,
    print_error,
    print_success,
)
from dli.exceptions import (
    ConfigurationError,
    RunExecutionError,
    RunFileNotFoundError,
    RunLocalDeniedError,
    RunOutputError,
    RunServerUnavailableError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.run import OutputFormat

# Create run subcommand app
run_app = typer.Typer(
    name="run",
    help="Execute ad-hoc SQL files and download results.",
    no_args_is_help=True,
)


def _parse_parameters(params: list[str] | None) -> dict[str, str]:
    """Parse key=value parameter strings into a dictionary.

    Args:
        params: List of "key=value" strings.

    Returns:
        Dictionary of parsed parameters.

    Raises:
        typer.BadParameter: If parameter format is invalid.
    """
    if not params:
        return {}

    result: dict[str, str] = {}
    for param in params:
        if "=" not in param:
            raise typer.BadParameter(
                f"Invalid parameter format. Expected 'key=value', got '{param}'"
            )
        key, value = param.split("=", 1)
        if not key.strip():
            raise typer.BadParameter(
                f"Invalid parameter format. Key cannot be empty in '{param}'"
            )
        result[key.strip()] = value
    return result


def _format_bytes(bytes_value: int | None) -> str:
    """Format bytes to human-readable format.

    Args:
        bytes_value: Bytes count or None.

    Returns:
        Formatted string (e.g., '1.5 GB') or '-' if None.
    """
    if bytes_value is None:
        return "-"
    if bytes_value >= 1_000_000_000_000:
        return f"{bytes_value / 1_000_000_000_000:.2f} TB"
    if bytes_value >= 1_000_000_000:
        return f"{bytes_value / 1_000_000_000:.2f} GB"
    if bytes_value >= 1_000_000:
        return f"{bytes_value / 1_000_000:.2f} MB"
    if bytes_value >= 1_000:
        return f"{bytes_value / 1_000:.2f} KB"
    return f"{bytes_value} B"


def _format_duration(seconds: float) -> str:
    """Format duration in seconds to human-readable format.

    Args:
        seconds: Duration in seconds.

    Returns:
        Formatted duration string (e.g., '12.5s', '2m 30s').
    """
    if seconds < 60:
        return f"{seconds:.1f}s"
    minutes = int(seconds // 60)
    remaining_seconds = seconds % 60
    return f"{minutes}m {remaining_seconds:.0f}s"


@run_app.command(name="sql")
def run_sql(
    sql: Annotated[
        Path,
        typer.Option(
            "--sql",
            help="Path to SQL file to execute.",
            exists=True,
            readable=True,
            resolve_path=True,
        ),
    ],
    output: Annotated[
        Path,
        typer.Option(
            "--output",
            "-o",
            help="Output file path for results.",
            resolve_path=True,
        ),
    ],
    output_format: Annotated[
        OutputFormat,
        typer.Option(
            "--format",
            "-f",
            help="Output format: csv (default), tsv, or json.",
        ),
    ] = OutputFormat.CSV,
    local: Annotated[
        bool,
        typer.Option(
            "--local",
            help="Request local execution (server policy may override).",
        ),
    ] = False,
    server: Annotated[
        bool,
        typer.Option(
            "--server",
            help="Request server execution (server policy may override).",
        ),
    ] = False,
    param: Annotated[
        list[str] | None,
        typer.Option(
            "--param",
            "-p",
            help="Parameter: key=value (repeatable).",
        ),
    ] = None,
    limit: Annotated[
        int | None,
        typer.Option(
            "--limit",
            "-n",
            help="Maximum rows to return.",
            min=1,
        ),
    ] = None,
    timeout: Annotated[
        int,
        typer.Option(
            "--timeout",
            "-t",
            help="Query timeout in seconds (1-3600).",
            min=1,
            max=3600,
        ),
    ] = 300,
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            help="SQL dialect: bigquery (default) or trino.",
        ),
    ] = "bigquery",
    dry_run: Annotated[
        bool,
        typer.Option(
            "--dry-run",
            help="Validate and show plan without executing.",
        ),
    ] = False,
    show_sql: Annotated[
        bool,
        typer.Option(
            "--show-sql",
            help="Display rendered SQL before execution.",
        ),
    ] = False,
    quiet: Annotated[
        bool,
        typer.Option(
            "--quiet",
            "-q",
            help="Suppress progress output.",
        ),
    ] = False,
    path: Annotated[
        Path | None,
        typer.Option(
            "--path",
            help="Project path for config resolution.",
        ),
    ] = None,
) -> None:
    """Execute SQL file and save results to output file.

    Runs ad-hoc SQL files against query engines (BigQuery/Trino) and
    downloads results to local CSV, TSV, or JSON files.

    \b
    Examples:
        dli run sql --sql query.sql --output results.csv
        dli run sql --sql report.sql -o report.json -f json
        dli run sql --sql daily.sql -o out.csv -p date=2026-01-01
        dli run sql --sql query.sql -o results.csv --local
        dli run sql --sql query.sql -o results.csv --dry-run
        dli run sql --sql query.sql -o results.csv --show-sql
        dli run sql --sql query.sql -o results.csv -n 100 --timeout 60
    """
    # Validate mutually exclusive options
    if local and server:
        print_error("Cannot specify both --local and --server")
        raise typer.Exit(1)

    # Validate dialect
    if dialect not in ("bigquery", "trino"):
        print_error(f"Invalid dialect '{dialect}'. Supported: bigquery, trino")
        raise typer.Exit(1)

    # Parse parameters
    try:
        parameters = _parse_parameters(param)
    except typer.BadParameter as e:
        print_error(str(e))
        raise typer.Exit(1)

    # Setup context and API
    project_path = get_project_path(path)
    ctx = ExecutionContext(
        project_path=project_path,
        execution_mode=ExecutionMode.MOCK,  # Use mock for now
    )
    api = RunAPI(context=ctx)

    # Handle dry-run
    if dry_run:
        try:
            plan = api.dry_run(
                sql_path=sql,
                output_path=output,
                output_format=output_format,
                parameters=parameters,
                dialect=dialect,  # type: ignore[arg-type]
                prefer_local=local,
                prefer_server=server,
            )
        except RunFileNotFoundError as e:
            print_error(str(e))
            raise typer.Exit(1)
        except ConfigurationError as e:
            print_error(str(e))
            raise typer.Exit(1)

        console.print("[dim][DRY RUN][/dim] Would execute:")
        console.print(f"  [dim]SQL file:[/dim]   {sql}")
        console.print(f"  [dim]Dialect:[/dim]    {dialect}")
        console.print(f"  [dim]Mode:[/dim]       {plan.execution_mode.value}")
        console.print(f"  [dim]Output:[/dim]     {output} ({output_format.value})")
        if parameters:
            console.print(f"  [dim]Parameters:[/dim] {parameters}")
        else:
            console.print("  [dim]Parameters:[/dim] none")

        if not plan.is_valid:
            console.print(
                f"\n[bold red]Validation Error:[/bold red] {plan.validation_error}"
            )
            raise typer.Exit(1)

        console.print("\n[bold]SQL Preview:[/bold]")
        syntax = Syntax(
            plan.rendered_sql.strip(),
            "sql",
            theme="monokai",
            line_numbers=True,
            word_wrap=True,
        )
        console.print(Panel(syntax, border_style="dim"))
        return

    # Show rendered SQL if requested
    if show_sql:
        try:
            rendered = api.render_sql(sql, parameters)
        except RunFileNotFoundError as e:
            print_error(str(e))
            raise typer.Exit(1)

        console.print("[bold]Rendered SQL:[/bold]")
        syntax = Syntax(
            rendered.strip(),
            "sql",
            theme="monokai",
            line_numbers=True,
            word_wrap=True,
        )
        console.print(Panel(syntax, border_style="dim"))
        console.print()

    # Execute query
    try:
        mode_str = "local" if local else "server"

        if not quiet:
            with console.status(
                f"[bold green]Executing {sql.name} ({mode_str} mode)..."
            ):
                result = api.run(
                    sql_path=sql,
                    output_path=output,
                    output_format=output_format,
                    parameters=parameters,
                    limit=limit,
                    timeout=timeout,
                    dialect=dialect,  # type: ignore[arg-type]
                    prefer_local=local,
                    prefer_server=server,
                )
        else:
            result = api.run(
                sql_path=sql,
                output_path=output,
                output_format=output_format,
                parameters=parameters,
                limit=limit,
                timeout=timeout,
                dialect=dialect,  # type: ignore[arg-type]
                prefer_local=local,
                prefer_server=server,
            )

    except RunFileNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except RunLocalDeniedError as e:
        print_error(str(e))
        console.print("[dim]Server requires: --server mode for this operation.[/dim]")
        raise typer.Exit(1)
    except RunServerUnavailableError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except RunExecutionError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except RunOutputError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except ConfigurationError as e:
        print_error(str(e))
        raise typer.Exit(1)

    # Display results
    if quiet:
        # Quiet mode - just output path
        console.print(str(result.output_path))
    else:
        # Normal output
        duration = _format_duration(result.duration_seconds)
        row_info = f"{result.row_count:,}"
        if limit and result.row_count >= limit:
            row_info += " (limited)"

        print_success(f"Query completed in {duration}")
        console.print(f"[dim]Rows returned:[/dim] {row_info}")
        console.print(f"[dim]Mode:[/dim]         {result.execution_mode.value}")

        if result.bytes_processed:
            console.print(
                f"[dim]Bytes processed:[/dim] {_format_bytes(result.bytes_processed)}"
            )

        console.print(f"[dim]Saved to:[/dim]     [cyan]{result.output_path}[/cyan]")


# Default command - alias for 'sql' subcommand
@run_app.callback(invoke_without_command=True)
def run_default(
    ctx: typer.Context,
    sql: Annotated[
        Path | None,
        typer.Option(
            "--sql",
            help="Path to SQL file to execute.",
            exists=True,
            readable=True,
            resolve_path=True,
        ),
    ] = None,
    output: Annotated[
        Path | None,
        typer.Option(
            "--output",
            "-o",
            help="Output file path for results.",
            resolve_path=True,
        ),
    ] = None,
    output_format: Annotated[
        OutputFormat,
        typer.Option(
            "--format",
            "-f",
            help="Output format: csv (default), tsv, or json.",
        ),
    ] = OutputFormat.CSV,
    local: Annotated[
        bool,
        typer.Option(
            "--local",
            help="Request local execution.",
        ),
    ] = False,
    server: Annotated[
        bool,
        typer.Option(
            "--server",
            help="Request server execution.",
        ),
    ] = False,
    param: Annotated[
        list[str] | None,
        typer.Option(
            "--param",
            "-p",
            help="Parameter: key=value (repeatable).",
        ),
    ] = None,
    limit: Annotated[
        int | None,
        typer.Option(
            "--limit",
            "-n",
            help="Maximum rows to return.",
            min=1,
        ),
    ] = None,
    timeout: Annotated[
        int,
        typer.Option(
            "--timeout",
            "-t",
            help="Query timeout in seconds.",
            min=1,
            max=3600,
        ),
    ] = 300,
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            help="SQL dialect: bigquery or trino.",
        ),
    ] = "bigquery",
    dry_run: Annotated[
        bool,
        typer.Option(
            "--dry-run",
            help="Validate and show plan without executing.",
        ),
    ] = False,
    show_sql: Annotated[
        bool,
        typer.Option(
            "--show-sql",
            help="Display rendered SQL before execution.",
        ),
    ] = False,
    quiet: Annotated[
        bool,
        typer.Option(
            "--quiet",
            "-q",
            help="Suppress progress output.",
        ),
    ] = False,
    path: Annotated[
        Path | None,
        typer.Option(
            "--path",
            help="Project path for config resolution.",
        ),
    ] = None,
) -> None:
    """Execute ad-hoc SQL files and download results.

    \b
    Examples:
        dli run --sql query.sql --output results.csv
        dli run --sql report.sql -o report.json -f json
        dli run --sql daily.sql -o out.csv -p date=2026-01-01

    Use 'dli run sql --help' for more options.
    """
    # If no subcommand and sql/output provided, run the query
    if ctx.invoked_subcommand is None:
        if sql is None or output is None:
            # Show help if required options missing
            if sql is None and output is None:
                # Just show help
                raise typer.Exit(0)
            if sql is None:
                print_error("Missing option '--sql'.")
                raise typer.Exit(1)
            if output is None:
                print_error("Missing option '--output' / '-o'.")
                raise typer.Exit(1)

        # Forward to sql command
        run_sql(
            sql=sql,
            output=output,
            output_format=output_format,
            local=local,
            server=server,
            param=param,
            limit=limit,
            timeout=timeout,
            dialect=dialect,
            dry_run=dry_run,
            show_sql=show_sql,
            quiet=quiet,
            path=path,
        )
