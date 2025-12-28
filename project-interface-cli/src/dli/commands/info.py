"""Info command for DLI CLI."""

from __future__ import annotations

import platform
import sys

from rich.console import Console
from rich.table import Table

console = Console()


def info() -> None:
    """Display CLI and environment information."""
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
