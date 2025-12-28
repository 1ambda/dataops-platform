"""Version command for DLI CLI."""

from __future__ import annotations

from rich.console import Console
from rich.panel import Panel
import typer

console = Console()


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
