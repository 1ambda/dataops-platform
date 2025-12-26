"""DataOps CLI main application."""

from __future__ import annotations

import asyncio
import json
import os
import sys
from pathlib import Path
from typing import Any

import httpx
import sqlglot
import typer
from dotenv import load_dotenv
from pydantic import BaseModel, ValidationError
from rich.console import Console
from rich.panel import Panel
from rich.table import Table

from .config import CliConfigManager, get_config_manager, get_cli_config
from .exceptions import APIConnectionError, ConfigurationError, SQLProcessingError
from .logging_config import get_logger, setup_logging

# Load environment variables
load_dotenv()

# Setup logging
setup_logging()

# Initialize console and logger
console = Console()
logger = get_logger(__name__)

app = typer.Typer(
    name="dli",
    help="DataOps CLI - Command-line interface for DataOps platform operations",
    rich_markup_mode="rich",
    no_args_is_help=True,
)


class PipelineInfo(BaseModel):
    """Pipeline information model."""

    id: int
    name: str
    description: str
    status: str


@app.command()
def version() -> None:
    """Show version information."""
    try:
        from dataops_cli import __version__
        console.print(Panel(f"DataOps CLI version: [bold green]{__version__}[/bold green]"))
    except ImportError:
        logger.error("Could not determine version information")
        console.print(Panel("[bold red]Version information unavailable[/bold red]", border_style="red"))
        raise typer.Exit(1)


@app.command()
def health(
    base_url: str = typer.Option(
        None, "--url", "-u", help="Base URL of the DataOps server (defaults to config)"
    ),
) -> None:
    """Check health status of the DataOps server."""
    try:
        config_manager = get_config_manager()
        api_config = config_manager.get_api_config()
        
        # Use provided URL or fall back to config
        target_url = base_url or api_config.base_url
        
        logger.info(f"Checking health of server at {target_url}")
        asyncio.run(check_health(target_url, api_config.timeout))
    except Exception as e:
        logger.error(f"Health check failed: {e}")
        console.print(
            Panel(
                f"[bold red]✗[/bold red] Health check failed: {e!s}",
                title="Health Check Error",
                border_style="red",
            )
        )
        raise typer.Exit(1)


async def check_health(base_url: str, timeout: int) -> None:
    """Check health status asynchronously.
    
    Args:
        base_url: Base URL of the server
        timeout: Request timeout in seconds
        
    Raises:
        APIConnectionError: If connection fails
    """
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            with console.status("[bold green]Checking server health..."):
                response = await client.get(f"{base_url}/api/health")
                response.raise_for_status()

            if response.status_code == 200:
                data = response.json()
                service_name = data.get("service", "unknown")
                console.print(
                    Panel(
                        f"[bold green]✓[/bold green] Server is healthy\nService: {service_name}",
                        title="Health Check",
                        border_style="green",
                    )
                )
                logger.info(f"Health check successful for {service_name}")
            else:
                console.print(
                    Panel(
                        f"[bold yellow]⚠[/bold yellow] Server returned status: {response.status_code}",
                        title="Health Check",
                        border_style="yellow",
                    )
                )
                logger.warning(f"Health check returned status {response.status_code}")

    except httpx.RequestError as e:
        error_msg = f"Failed to connect to {base_url}: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="Health Check",
                border_style="red",
            )
        )
        raise APIConnectionError(error_msg, base_url)
    except Exception as e:
        error_msg = f"Unexpected error during health check: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="Health Check",
                border_style="red",
            )
        )
        raise


@app.command()
def pipelines(
    base_url: str = typer.Option(
        None, "--url", "-u", help="Base URL of the DataOps server (defaults to config)"
    ),
) -> None:
    """List all pipelines."""
    try:
        config_manager = get_config_manager()
        api_config = config_manager.get_api_config()
        
        # Use provided URL or fall back to config
        target_url = base_url or api_config.base_url
        
        logger.info(f"Fetching pipelines from {target_url}")
        asyncio.run(list_pipelines(target_url, api_config.timeout))
    except Exception as e:
        logger.error(f"Pipeline listing failed: {e}")
        console.print(
            Panel(
                f"[bold red]✗[/bold red] Failed to list pipelines: {e!s}",
                title="Pipeline List Error",
                border_style="red",
            )
        )
        raise typer.Exit(1)


async def list_pipelines(base_url: str, timeout: int) -> None:
    """List pipelines asynchronously.
    
    Args:
        base_url: Base URL of the server
        timeout: Request timeout in seconds
        
    Raises:
        APIConnectionError: If connection fails
    """
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            with console.status("[bold blue]Fetching pipelines..."):
                response = await client.get(f"{base_url}/api/pipelines")
                response.raise_for_status()

            data: list[dict[str, Any]] = response.json()

            if not data:
                console.print("[yellow]No pipelines found.[/yellow]")
                logger.info("No pipelines returned from server")
                return

            table = Table(title="DataOps Pipelines")
            table.add_column("ID", style="cyan", no_wrap=True)
            table.add_column("Name", style="magenta")
            table.add_column("Description")
            table.add_column("Status", style="green")

            processed_count = 0
            for pipeline_data in data:
                try:
                    pipeline = PipelineInfo(**pipeline_data)
                    table.add_row(
                        str(pipeline.id),
                        pipeline.name,
                        pipeline.description,
                        pipeline.status,
                    )
                    processed_count += 1
                except ValidationError as e:
                    # Fallback for unexpected data format
                    logger.warning(f"Invalid pipeline data format: {e}")
                    table.add_row(
                        str(pipeline_data.get("id", "N/A")),
                        pipeline_data.get("name", "N/A"),
                        pipeline_data.get("description", "N/A"),
                        pipeline_data.get("status", "N/A"),
                    )
                    processed_count += 1

            console.print(table)
            logger.info(f"Listed {processed_count} pipelines")

    except httpx.RequestError as e:
        error_msg = f"Failed to connect to {base_url}: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="Pipeline List",
                border_style="red",
            )
        )
        raise APIConnectionError(error_msg, base_url)
    except Exception as e:
        error_msg = f"Unexpected error while listing pipelines: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="Pipeline List",
                border_style="red",
            )
        )
        raise


@app.command()
def sql_parse(
    query: str = typer.Argument(..., help="SQL query to parse"),
    dialect: str = typer.Option("", "--dialect", "-d", help="SQL dialect (optional)"),
    output_format: str = typer.Option("pretty", "--format", "-f", help="Output format (pretty, compact)"),
) -> None:
    """Parse and format SQL query using SQLGlot."""
    if not query.strip():
        logger.error("SQL query cannot be empty")
        console.print(
            Panel(
                "[bold red]✗[/bold red] SQL query cannot be empty",
                title="SQL Parser Error",
                border_style="red",
            )
        )
        raise typer.Exit(1)
    
    try:
        logger.info(f"Parsing SQL query with dialect: {dialect or 'auto-detect'}")
        
        # Parse the SQL
        if dialect:
            parsed = sqlglot.parse_one(query, dialect=dialect)
        else:
            parsed = sqlglot.parse_one(query)

        if not parsed:
            raise SQLProcessingError("Failed to parse SQL - no valid statements found", query)

        # Format the SQL based on output format
        if output_format.lower() == "compact":
            formatted = parsed.sql()
        else:  # pretty (default)
            formatted = parsed.sql(pretty=True)

        # Display results
        console.print(
            Panel(
                f"[bold cyan]Original:[/bold cyan]\n{query}\n\n[bold green]Formatted:[/bold green]\n{formatted}",
                title=f"SQL Parser ({dialect or 'auto-detect'})",
                border_style="blue",
            )
        )
        logger.info("SQL parsing completed successfully")

    except sqlglot.ParseError as e:
        error_msg = f"SQL syntax error: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="SQL Parser",
                border_style="red",
            )
        )
        raise typer.Exit(4)
    except Exception as e:
        error_msg = f"Failed to parse SQL: {e!s}"
        logger.error(error_msg)
        console.print(
            Panel(
                f"[bold red]✗[/bold red] {error_msg}",
                title="SQL Parser",
                border_style="red",
            )
        )
        raise typer.Exit(4)


@app.command()
def config(
    show: bool = typer.Option(False, "--show", "-s", help="Show current configuration"),
    set_url: str | None = typer.Option(None, "--set-url", help="Set base URL"),
    set_timeout: int | None = typer.Option(None, "--set-timeout", help="Set request timeout"),
    reset: bool = typer.Option(False, "--reset", help="Reset to default configuration"),
    config_file: str = typer.Option(
        None, "--config", "-c", help="Configuration file path (default: ~/.dli/config.json)"
    ),
) -> None:
    """Manage CLI configuration."""
    try:
        # Initialize config manager with custom file path if provided
        config_manager = get_config_manager(config_file)
        
        # Handle reset option
        if reset:
            logger.info("Resetting configuration to defaults")
            if config_manager.config_path.exists():
                config_manager.config_path.unlink()
                console.print("[green]✓[/green] Configuration reset to defaults")
            else:
                console.print("[yellow]No configuration file to reset[/yellow]")
            return

        # Handle show option
        if show:
            logger.debug("Displaying current configuration")
            if config_manager.config_path.exists():
                try:
                    config_data = config_manager.load_config()
                    api_config = config_manager.get_api_config()
                    
                    display_data = {
                        "config_file": str(config_manager.config_path),
                        "base_url": api_config.base_url,
                        "timeout": api_config.timeout,
                        "retries": api_config.retries,
                    }
                    
                    console.print(
                        Panel(
                            json.dumps(display_data, indent=2),
                            title="Current Configuration",
                            border_style="blue",
                        )
                    )
                except (OSError, json.JSONDecodeError) as e:
                    logger.error(f"Error reading config: {e}")
                    console.print(f"[red]Error reading config: {e}[/red]")
                    raise typer.Exit(2)
            else:
                console.print(
                    Panel(
                        f"No configuration file found at: {config_manager.config_path}\nUsing default values",
                        title="Configuration",
                        border_style="yellow",
                    )
                )
            return

        # Handle setting new values
        updates_made = False
        
        if set_url:
            logger.info(f"Setting base URL to: {set_url}")
            config_manager.update_api_config(base_url=set_url)
            console.print(f"[green]✓[/green] Base URL set to: {set_url}")
            updates_made = True

        if set_timeout:
            if set_timeout <= 0:
                logger.error("Timeout must be positive")
                console.print("[red]Error: Timeout must be positive[/red]")
                raise typer.Exit(2)
            
            logger.info(f"Setting timeout to: {set_timeout}")
            config_manager.update_api_config(timeout=set_timeout)
            console.print(f"[green]✓[/green] Timeout set to: {set_timeout} seconds")
            updates_made = True

        if not updates_made and not show and not reset:
            console.print("[yellow]No changes made. Use --show to view current config or --help for options.[/yellow]")

    except Exception as e:
        logger.error(f"Configuration error: {e}")
        console.print(f"[red]Configuration error: {e}[/red]")
        raise typer.Exit(2)


def main() -> None:
    """Main entry point for the CLI."""
    try:
        app()
    except KeyboardInterrupt:
        logger.info("Interrupted by user")
        console.print("\n[yellow]Interrupted by user[/yellow]")
        sys.exit(130)
    except Exception as e:
        logger.error(f"Unexpected error: {e}", exc_info=True)
        console.print(f"\n[red]Unexpected error: {e}[/red]")
        sys.exit(1)


if __name__ == "__main__":
    main()
