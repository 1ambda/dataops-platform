"""SQL Worksheet CLI commands.

Provides commands for managing saved SQL worksheets on Basecamp Server.
Worksheets can be listed, downloaded, and uploaded.

Commands:
    list: List SQL worksheets with optional filters
    get: Download worksheet content to file or stdout
    put: Upload SQL file to update a worksheet
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.prompt import Confirm
from rich.table import Table
import typer

from dli.api.sql import SqlAPI
from dli.commands.base import with_trace
from dli.commands.utils import (
    console,
    format_datetime,
    print_error,
    print_success,
    print_warning,
)
from dli.exceptions import (
    SqlAccessDeniedError,
    SqlFileNotFoundError,
    SqlTeamNotFoundError,
    SqlWorksheetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode

# Output format literal type
ListOutputFormat = str

sql_app = typer.Typer(
    name="sql",
    help="Manage saved SQL worksheets on Basecamp Server.",
    no_args_is_help=True,
)


def _get_context(mock: bool = False) -> ExecutionContext:
    """Create execution context.

    Args:
        mock: Whether to use mock mode.

    Returns:
        ExecutionContext configured for server or mock mode.
    """
    return ExecutionContext(
        execution_mode=ExecutionMode.MOCK if mock else ExecutionMode.SERVER,
    )


@sql_app.command("list")
@with_trace("sql list")
def list_worksheets(
    team: Annotated[
        str | None,
        typer.Option("--team", "-t", help="Filter by team name."),
    ] = None,
    folder: Annotated[
        str | None,
        typer.Option("--folder", help="Filter by folder name."),
    ] = None,
    starred: Annotated[
        bool,
        typer.Option("--starred", help="Show only starred worksheets."),
    ] = False,
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Maximum number of results."),
    ] = 20,
    offset: Annotated[
        int,
        typer.Option("--offset", help="Pagination offset."),
    ] = 0,
    output_format: Annotated[
        str,
        typer.Option("--format", "-f", help="Output format: table or json."),
    ] = "table",
    mock: Annotated[
        bool,
        typer.Option("--mock", hidden=True, help="Use mock mode for testing."),
    ] = False,
) -> None:
    """List saved SQL worksheets.

    \b
    Examples:
        dli sql list
        dli sql list --team marketing
        dli sql list --team marketing --folder "Campaign Analytics"
        dli sql list --starred
        dli sql list --format json
    """
    try:
        ctx = _get_context(mock)
        api = SqlAPI(context=ctx)

        with console.status("[bold green]Fetching worksheets..."):
            result = api.list_worksheets(
                team=team,
                folder=folder,
                starred=starred,
                limit=limit,
                offset=offset,
            )

        if output_format == "json":
            console.print_json(json.dumps(result.model_dump(mode="json"), default=str))
            return

        if not result.worksheets:
            print_warning("No worksheets found.")
            raise typer.Exit(0)

        table = Table(title="SQL Worksheets", show_header=True)
        table.add_column("ID", style="cyan", no_wrap=True)
        table.add_column("NAME", style="white")
        table.add_column("FOLDER", style="dim")
        table.add_column("DIALECT", style="yellow")
        table.add_column("UPDATED", style="dim")

        for worksheet in result.worksheets:
            table.add_row(
                str(worksheet.id),
                worksheet.name,
                worksheet.folder or "-",
                worksheet.dialect.value,
                format_datetime(worksheet.updated_at),
            )

        console.print(table)
        console.print(f"\n[dim]Showing {len(result.worksheets)} of {result.total} worksheets[/dim]")

    except SqlTeamNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e


@sql_app.command("get")
@with_trace("sql get")
def get_worksheet(
    worksheet_id: Annotated[
        int,
        typer.Argument(help="Worksheet ID to download."),
    ],
    file: Annotated[
        Path | None,
        typer.Option("--file", "-f", help="Output file path (stdout if omitted)."),
    ] = None,
    overwrite: Annotated[
        bool,
        typer.Option("--overwrite", help="Overwrite existing file without prompt."),
    ] = False,
    team: Annotated[
        str | None,
        typer.Option("--team", "-t", help="Team name."),
    ] = None,
    mock: Annotated[
        bool,
        typer.Option("--mock", hidden=True, help="Use mock mode for testing."),
    ] = False,
) -> None:
    """Download SQL worksheet content.

    \b
    Examples:
        dli sql get 43
        dli sql get 43 -f ./insight.sql
        dli sql get 43 --file ./worksheets/insight.sql --overwrite
    """
    try:
        ctx = _get_context(mock)
        api = SqlAPI(context=ctx)

        with console.status(f"[bold green]Fetching worksheet {worksheet_id}..."):
            worksheet = api.get(worksheet_id, team=team)

        if file is None:
            # Print to stdout
            console.print(worksheet.sql)
            return

        # Check if file exists
        if file.exists() and not overwrite and not Confirm.ask(f"File {file} exists. Overwrite?"):
            console.print("[dim]Cancelled.[/dim]")
            raise typer.Exit(0)

        # Create parent directories if needed
        file.parent.mkdir(parents=True, exist_ok=True)

        # Write SQL content
        file.write_text(worksheet.sql)
        print_success(f"Downloaded worksheet {worksheet_id} to {file}")

    except SqlWorksheetNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlAccessDeniedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlTeamNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e


@sql_app.command("put")
@with_trace("sql put")
def put_worksheet(
    worksheet_id: Annotated[
        int,
        typer.Argument(help="Worksheet ID to update."),
    ],
    file: Annotated[
        Path,
        typer.Option("--file", "-f", help="SQL file to upload."),
    ],
    force: Annotated[
        bool,
        typer.Option("--force", help="Skip confirmation prompt."),
    ] = False,
    team: Annotated[
        str | None,
        typer.Option("--team", "-t", help="Team name."),
    ] = None,
    mock: Annotated[
        bool,
        typer.Option("--mock", hidden=True, help="Use mock mode for testing."),
    ] = False,
) -> None:
    """Upload SQL file to update a worksheet.

    \b
    Examples:
        dli sql put 43 -f ./insight.sql
        dli sql put 43 --file ./insight.sql --force
    """
    try:
        # Check if file exists
        if not file.exists():
            raise SqlFileNotFoundError(
                message=f"File not found: {file}",
                path=str(file),
            )
        ctx = _get_context(mock)
        api = SqlAPI(context=ctx)

        # Get current worksheet info for confirmation
        if not force:
            with console.status(f"[bold green]Fetching worksheet {worksheet_id}..."):
                worksheet = api.get(worksheet_id, team=team)

            if not Confirm.ask(
                f"Upload {file} to worksheet {worksheet_id} ({worksheet.name})?\n"
                "This will overwrite the existing SQL content."
            ):
                console.print("[dim]Cancelled.[/dim]")
                raise typer.Exit(0)

        # Read and upload SQL
        sql = file.read_text()

        with console.status(f"[bold green]Uploading to worksheet {worksheet_id}..."):
            result = api.put(worksheet_id, sql, team=team)

        print_success(f"Uploaded {file} to worksheet {worksheet_id}")
        console.print(f"[dim]Updated at:[/dim] {format_datetime(result.updated_at)}")

    except SqlFileNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlWorksheetNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlAccessDeniedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlUpdateFailedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlTeamNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
