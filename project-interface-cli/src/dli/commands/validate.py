"""Validate command for DLI CLI."""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from rich.console import Console
import typer

console = Console()
error_console = Console(stderr=True)


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
