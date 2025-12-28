"""Render command for DLI CLI."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from rich.console import Console
import typer

console = Console()
error_console = Console(stderr=True)


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
