"""CLI utilities for parameter parsing and output formatting."""

from __future__ import annotations

from typing import Any

from rich.console import Console
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table

console = Console()
error_console = Console(stderr=True)


def parse_params(params: list[str]) -> dict[str, Any]:
    """Parse CLI parameters from key=value format.

    Args:
        params: List of key=value strings

    Returns:
        Dictionary of parsed parameters with type inference

    Raises:
        ValueError: If parameter format is invalid

    Examples:
        >>> parse_params(["date=2024-01-01"])
        {"date": "2024-01-01"}
        >>> parse_params(["count=100"])
        {"count": 100}
        >>> parse_params(["tags=a,b,c"])
        {"tags": ["a", "b", "c"]}
    """
    result: dict[str, Any] = {}

    for param in params:
        if "=" not in param:
            msg = f"Invalid format: '{param}'. Use key=value"
            raise ValueError(msg)

        key, value = param.split("=", 1)
        key, value = key.strip(), value.strip()

        if not key:
            msg = f"Empty key in parameter: '{param}'"
            raise ValueError(msg)

        # Type inference
        if "," in value:
            # List type
            result[key] = [v.strip() for v in value.split(",")]
        elif value.isdigit():
            # Integer type
            result[key] = int(value)
        elif value.replace(".", "", 1).isdigit() and value.count(".") == 1:
            # Float type
            result[key] = float(value)
        elif value.lower() in ("true", "false"):
            # Boolean type
            result[key] = value.lower() == "true"
        else:
            # String type (default)
            result[key] = value

    return result


def print_error(message: str) -> None:
    """Print error message to stderr."""
    error_console.print(f"[red]✗ Error: {message}[/red]")


def print_success(message: str) -> None:
    """Print success message."""
    console.print(f"[green]✓ {message}[/green]")


def print_warning(message: str) -> None:
    """Print warning message."""
    console.print(f"[yellow]⚠ {message}[/yellow]")


def print_sql(sql: str, title: str = "Rendered SQL") -> None:
    """Print SQL with syntax highlighting."""
    syntax = Syntax(sql, "sql", theme="monokai", line_numbers=True)
    console.print(Panel(syntax, title=title, border_style="blue"))


def print_data_table(
    columns: list[str],
    data: list[dict[str, Any]],
    title: str = "Results",
) -> None:
    """Print data as a formatted table."""
    table = Table(title=title, show_header=True, header_style="bold cyan")

    for col in columns:
        table.add_column(col)

    for row in data:
        table.add_row(*[str(row.get(col, "")) for col in columns])

    console.print(table)


def print_validation_result(
    is_valid: bool,
    errors: list[str],
    warnings: list[str] | None = None,
) -> None:
    """Print validation result with errors and warnings."""
    if is_valid:
        print_success("Validation passed")
    else:
        print_error("Validation failed")
        for e in errors:
            console.print(f"  • {e}")

    if warnings:
        console.print("\n[yellow]Warnings:[/yellow]")
        for w in warnings:
            console.print(f"  • {w}")
