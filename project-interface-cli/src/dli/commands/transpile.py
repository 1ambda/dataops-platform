"""Transpile subcommand for DLI CLI.

Provides commands for SQL transpilation operations including:
- Table substitution based on server-defined rules
- METRIC() function expansion to SQL expressions
- SQL pattern analysis and warnings

Example:
    $ dli transpile "SELECT * FROM analytics.users"
    $ dli transpile -f query.sql --strict
    $ dli transpile "SELECT * FROM analytics.users" --show-rules
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table
import typer

from dli.commands.base import ListOutputFormat
from dli.commands.utils import (
    console,
    print_error,
    print_success,
    print_warning,
)
from dli.core.transpile import (
    Dialect,
    MockTranspileClient,
    TranspileConfig,
    TranspileEngine,
)

# Create transpile subcommand app
transpile_app = typer.Typer(
    name="transpile",
    help="SQL transpile operations (table substitution, METRIC expansion, warnings).",
    invoke_without_command=True,
)


def _read_sql_from_file(file_path: Path) -> str:
    """Read SQL content from a file.

    Args:
        file_path: Path to the SQL file.

    Returns:
        SQL content as string.

    Raises:
        typer.Exit: If file cannot be read.
    """
    if not file_path.exists():
        print_error(f"File not found: {file_path}")
        raise typer.Exit(1)

    try:
        return file_path.read_text(encoding="utf-8").strip()
    except OSError as e:
        print_error(f"Failed to read file: {e}")
        raise typer.Exit(1) from e


def _display_table_result(
    original_sql: str,
    result,
    show_rules: bool = False,
) -> None:
    """Display transpile result in table format.

    Args:
        original_sql: Original SQL before transpilation.
        result: TranspileResult object.
        show_rules: Whether to show applied rules detail.
    """
    # Create the main result panel
    table = Table(show_header=False, box=None, padding=(0, 1))
    table.add_column("Label", style="dim", width=18)
    table.add_column("Value")

    # Status
    status = "[green]Success[/green]" if result.success else "[red]Failed[/red]"
    table.add_row("Status:", status)

    # Dialect
    table.add_row("Dialect:", result.metadata.dialect.value)

    # Duration
    table.add_row("Duration:", f"{result.metadata.duration_ms}ms")

    # Applied rules count
    table.add_row("Applied Rules:", str(len(result.applied_rules)))

    # Warnings count
    warning_style = "yellow" if result.warnings else "dim"
    table.add_row(
        "Warnings:", f"[{warning_style}]{len(result.warnings)}[/{warning_style}]"
    )

    # Error if present
    if result.error:
        table.add_row("Error:", f"[red]{result.error}[/red]")

    console.print()
    console.print(Panel(table, title="Transpile Result", border_style="blue"))

    # Show original SQL
    console.print()
    console.print("[bold]Original SQL:[/bold]")
    original_syntax = Syntax(original_sql, "sql", theme="monokai", line_numbers=True)
    console.print(Panel(original_syntax, border_style="dim"))

    # Show transpiled SQL
    if result.sql != original_sql:
        console.print("[bold]Transpiled SQL:[/bold]")
        transpiled_syntax = Syntax(
            result.sql, "sql", theme="monokai", line_numbers=True
        )
        console.print(Panel(transpiled_syntax, border_style="green"))
    else:
        console.print("[dim]No changes made to SQL.[/dim]")

    # Show applied rules detail if requested
    if show_rules and result.applied_rules:
        console.print()
        console.print("[bold]Applied Rules:[/bold]")
        rules_table = Table(show_header=True, header_style="bold cyan")
        rules_table.add_column("ID", style="cyan", no_wrap=True)
        rules_table.add_column("Type", style="yellow")
        rules_table.add_column("Source", style="red")
        rules_table.add_column("Target", style="green")
        rules_table.add_column("Description", style="dim")

        for rule in result.applied_rules:
            rules_table.add_row(
                rule.id,
                rule.type.value,
                rule.source,
                rule.target,
                rule.description or "-",
            )

        console.print(rules_table)

    # Show warnings if present
    if result.warnings:
        console.print()
        console.print("[bold yellow]Warnings:[/bold yellow]")
        for warning in result.warnings:
            location = ""
            if warning.line:
                location = f" (line {warning.line}"
                if warning.column:
                    location += f", col {warning.column}"
                location += ")"
            console.print(
                f"  [yellow]-[/yellow] [{warning.type.value}]{location} {warning.message}"
            )

    console.print()


def _display_json_result(result) -> None:
    """Display transpile result in JSON format.

    Args:
        result: TranspileResult object.
    """
    # Convert to dict for JSON output
    output = {
        "success": result.success,
        "sql": result.sql,
        "applied_rules": [
            {
                "id": r.id,
                "type": r.type.value,
                "source": r.source,
                "target": r.target,
                "description": r.description,
            }
            for r in result.applied_rules
        ],
        "warnings": [
            {
                "type": w.type.value,
                "message": w.message,
                "line": w.line,
                "column": w.column,
            }
            for w in result.warnings
        ],
        "metadata": {
            "original_sql": result.metadata.original_sql,
            "transpiled_at": result.metadata.transpiled_at.isoformat(),
            "dialect": result.metadata.dialect.value,
            "duration_ms": result.metadata.duration_ms,
        },
        "error": result.error,
    }

    console.print_json(json.dumps(output))


@transpile_app.callback(invoke_without_command=True)
def transpile_sql(
    ctx: typer.Context,
    sql: Annotated[
        str | None,
        typer.Argument(
            help="Inline SQL to transpile. If not provided, use --file option."
        ),
    ] = None,
    file: Annotated[
        Path | None,
        typer.Option(
            "--file",
            "-f",
            help="Path to SQL file to transpile.",
        ),
    ] = None,
    strict: Annotated[
        bool,
        typer.Option(
            "--strict",
            help="Fail on any error (default: graceful degradation with warnings).",
        ),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option(
            "--format",
            help="Output format (table or json).",
        ),
    ] = "table",
    show_rules: Annotated[
        bool,
        typer.Option(
            "--show-rules",
            help="Show detailed information about applied rules.",
        ),
    ] = False,
    validate: Annotated[
        bool,
        typer.Option(
            "--validate",
            help="Perform SQL syntax validation.",
        ),
    ] = False,
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            "-d",
            help="Input SQL dialect (trino, bigquery).",
        ),
    ] = "trino",
) -> None:
    """Transpile SQL with table substitution and METRIC expansion.

    This command performs SQL transpilation including:
    - Table substitution based on server-defined rules
    - METRIC() function expansion to SQL expressions
    - SQL pattern analysis and warnings

    The SQL can be provided either as an inline argument or from a file.

    \b
    Examples:
        # Inline SQL transpilation
        dli transpile "SELECT * FROM analytics.users"

        # Transpile from file
        dli transpile -f query.sql

        # Strict mode (fail on any error)
        dli transpile "SELECT * FROM analytics.users" --strict

        # Show applied rules detail
        dli transpile "SELECT * FROM raw.events" --show-rules

        # JSON output
        dli transpile "SELECT * FROM analytics.users" --format json

        # Validate SQL syntax
        dli transpile "SELECT * FROM users" --validate

        # Specify dialect
        dli transpile "SELECT * FROM users" --dialect bigquery
    """
    # If a subcommand is invoked, skip the callback logic
    if ctx.invoked_subcommand is not None:
        return

    # Validate input: either sql argument or --file must be provided
    if sql is None and file is None:
        console.print(ctx.get_help())
        raise typer.Exit(0)

    if sql is not None and file is not None:
        print_error("Cannot specify both inline SQL and --file option. Choose one.")
        raise typer.Exit(1)

    # Read SQL from file if provided
    if file is not None:
        sql = _read_sql_from_file(file)

    if not sql or not sql.strip():
        print_error("SQL cannot be empty.")
        raise typer.Exit(1)

    # Parse and validate dialect
    try:
        parsed_dialect = Dialect(dialect.lower())
    except ValueError:
        print_error(f"Invalid dialect '{dialect}'. Supported: trino, bigquery")
        raise typer.Exit(1)

    # Create engine configuration
    config = TranspileConfig(
        dialect=parsed_dialect,
        strict_mode=strict,
        validate_syntax=validate,
    )

    # Create engine with mock client
    client = MockTranspileClient()
    engine = TranspileEngine(client=client, config=config)

    # Validate syntax if requested
    if validate:
        with console.status("[bold green]Validating SQL syntax..."):
            validation_errors = engine.validate_sql(sql)

        if validation_errors:
            print_error("SQL validation failed:")
            for err in validation_errors:
                console.print(f"  [red]-[/red] {err}")
            raise typer.Exit(1)

        print_success("SQL syntax is valid.")

    # Perform transpilation
    try:
        with console.status("[bold green]Transpiling SQL..."):
            result = engine.transpile(sql)
    except Exception as e:
        print_error(f"Transpilation failed: {e}")
        raise typer.Exit(1) from e

    # Handle strict mode failures
    if not result.success and strict:
        print_error(result.error or "Transpilation failed in strict mode.")
        raise typer.Exit(1)

    # Display result
    if format_output == "json":
        _display_json_result(result)
    else:
        _display_table_result(sql, result, show_rules=show_rules)

    # Show summary
    if format_output == "table":
        if result.success:
            if result.applied_rules:
                print_success(
                    f"Transpilation complete: {len(result.applied_rules)} rule(s) applied, "
                    f"{len(result.warnings)} warning(s)."
                )
            else:
                print_warning("No transpile rules were applied.")
        else:
            print_warning("Transpilation completed with errors (graceful degradation).")

    # Exit with error code if not successful
    if not result.success:
        raise typer.Exit(1)
