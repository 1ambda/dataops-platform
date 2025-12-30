"""Config subcommand for DLI CLI.

Provides commands for managing DLI configuration and server connection.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.table import Table
import typer

from dli.commands.utils import console, print_error, print_success, print_warning

# Create config subcommand app
config_app = typer.Typer(
    name="config",
    help="Configuration management commands.",
    no_args_is_help=True,
)


def _get_project_config(path: Path | None) -> "ProjectConfig | None":
    """Load project configuration."""
    from dli.core.config import load_project

    search_path = path or Path.cwd()

    # Check if dli.yaml exists
    config_path = search_path / "dli.yaml"
    if config_path.exists():
        return load_project(search_path)

    # Try parent directories
    for parent in search_path.parents:
        if (parent / "dli.yaml").exists():
            return load_project(parent)

    return None


@config_app.command("show")
def show_config(
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    format_output: Annotated[
        str,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
) -> None:
    """Show current configuration.

    Examples:
        dli config show
        dli config show --format json
    """
    config = _get_project_config(path)

    if config is None:
        print_warning("No dli.yaml configuration found.")
        raise typer.Exit(0)

    server_url = config.server_url
    server_timeout = config.server_timeout
    server_api_key = config.server_api_key

    if format_output == "json":
        data = {
            "server": {
                "url": server_url,
                "timeout": server_timeout,
                "api_key_configured": server_api_key is not None,
            },
        }
        console.print_json(json.dumps(data))
        return

    # Table output
    table = Table(title="DLI Configuration", show_header=True)
    table.add_column("Setting", style="cyan")
    table.add_column("Value", style="green")

    table.add_row("Server URL", server_url or "[dim]Not configured[/dim]")
    table.add_row("Server Timeout", f"{server_timeout}s")
    table.add_row("API Key", "[green]✓ Configured[/green]" if server_api_key else "[dim]Not set[/dim]")

    console.print(table)

    if not server_url:
        console.print("\n[yellow]Tip: Add server configuration to dli.yaml:[/yellow]")
        console.print(
            Panel(
                "server:\n  url: \"http://localhost:8081\"\n  timeout: 30\n  # api_key: \"your-key\"",
                title="dli.yaml",
                border_style="dim",
            )
        )


@config_app.command("status")
def check_status(
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Check server connection status.

    Examples:
        dli config status
    """
    from dli.core.client import create_client

    config = _get_project_config(path)

    if config is None:
        print_warning("No dli.yaml configuration found.")
        raise typer.Exit(0)

    server_url = config.server_url
    if not server_url:
        print_error("Server URL not configured in dli.yaml")
        raise typer.Exit(1)

    console.print(f"[dim]Checking server at {server_url}...[/dim]")

    # Use mock mode for now
    client = create_client(
        url=server_url,
        timeout=config.server_timeout,
        api_key=config.server_api_key,
        mock_mode=True,
    )

    response = client.health_check()

    if response.success:
        print_success("Server is healthy")
        if response.data:
            console.print(f"  [dim]Version:[/dim] {response.data.get('version', 'unknown')}")
    else:
        print_error(f"Server check failed: {response.error}")
        raise typer.Exit(1)
