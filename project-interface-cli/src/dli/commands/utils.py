"""CLI utilities for parameter parsing and output formatting."""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any

from rich.console import Console
from rich.markup import escape as rich_escape
from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table

from dli.core.trace import get_current_trace
from dli.models.common import TraceMode

if TYPE_CHECKING:
    from dli.exceptions import DLIError

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


def print_error(
    message: str,
    *,
    error_code: str | None = None,
    trace_mode: TraceMode = TraceMode.ERROR_ONLY,
) -> None:
    """Print error message to stderr with optional trace ID.

    Args:
        message: Error message to display.
        error_code: Optional error code (e.g., "DLI-501").
        trace_mode: Controls trace ID display (default: ERROR_ONLY shows trace on errors).

    Example:
        >>> print_error("Connection failed", error_code="DLI-501")
        [red]x Error: [DLI-501] [trace:550e8400] Connection failed[/red]
    """
    trace = get_current_trace()
    parts: list[str] = []

    if error_code:
        # Escape brackets for Rich markup using rich.markup.escape
        parts.append(rich_escape(f"[{error_code}]"))

    if trace and trace_mode != TraceMode.NEVER:
        # Escape brackets for Rich markup using rich.markup.escape
        parts.append(rich_escape(f"[trace:{trace.short_id}]"))

    parts.append(message)
    error_console.print(f"[red]x Error: {' '.join(parts)}[/red]")


def display_error(
    error: DLIError,
    trace_mode: TraceMode = TraceMode.ERROR_ONLY,
) -> None:
    """Display DLI error with trace ID.

    Convenience wrapper for displaying DLIError exceptions with their
    error codes and trace information.

    Args:
        error: DLIError exception to display.
        trace_mode: Controls trace ID display (default: ERROR_ONLY).

    Example:
        >>> from dli.exceptions import ServerError, ErrorCode
        >>> err = ServerError(message="Connection timeout", code=ErrorCode.SERVER_UNREACHABLE)
        >>> display_error(err)
        [red]x Error: [DLI-501] [trace:550e8400] Connection timeout[/red]
    """
    print_error(error.message, error_code=error.code.value, trace_mode=trace_mode)


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


def get_effective_trace_mode(trace_flag: bool | None) -> TraceMode:
    """Get effective trace mode from CLI flag or config.

    Determines the trace mode to use based on CLI flag override or config setting.
    CLI flags take precedence over configuration.

    Args:
        trace_flag: CLI --trace/--no-trace flag value
            - True: force show trace (TraceMode.ALWAYS)
            - False: force hide trace (TraceMode.NEVER)
            - None: use config setting

    Returns:
        Effective TraceMode to use.

    Example:
        >>> # CLI flag overrides config
        >>> get_effective_trace_mode(True)  # Returns TraceMode.ALWAYS
        >>> get_effective_trace_mode(False)  # Returns TraceMode.NEVER
        >>> get_effective_trace_mode(None)  # Returns config setting
    """
    if trace_flag is True:
        return TraceMode.ALWAYS
    elif trace_flag is False:
        return TraceMode.NEVER
    else:
        from dli.api.config import ConfigAPI

        config_api = ConfigAPI()
        return config_api.get_trace_mode()


def format_datetime(
    dt: datetime | str | None,
    *,
    include_seconds: bool = False,
) -> str:
    """Format datetime for CLI display.

    Args:
        dt: A datetime object, ISO format string, or None.
        include_seconds: Whether to include seconds in the output.

    Returns:
        Formatted date string or "-" if None.

    Examples:
        >>> format_datetime(datetime(2024, 1, 15, 10, 30, 45))
        "2024-01-15 10:30"
        >>> format_datetime("2024-01-15T10:30:45Z")
        "2024-01-15 10:30"
        >>> format_datetime(None)
        "-"
    """
    if dt is None:
        return "-"
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(dt.replace("Z", "+00:00"))
        except ValueError:
            return dt
    fmt = "%Y-%m-%d %H:%M:%S" if include_seconds else "%Y-%m-%d %H:%M"
    return dt.strftime(fmt)
