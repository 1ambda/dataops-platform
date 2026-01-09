"""SQL Snippet CLI commands.

Provides commands for managing saved SQL snippets on Basecamp Server.
Snippets can be listed, downloaded, and uploaded.

Commands:
    list: List SQL snippets with optional filters
    get: Download snippet content to file or stdout
    put: Upload SQL file to update a snippet
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.prompt import Confirm
from rich.table import Table
import typer

from dli.api.sql import SqlAPI
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
    SqlProjectNotFoundError,
    SqlSnippetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode

# Output format literal type
ListOutputFormat = str

sql_app = typer.Typer(
    name="sql",
    help="Manage saved SQL snippets on Basecamp Server.",
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
def list_snippets(
    project: Annotated[
        str | None,
        typer.Option("--project", "-p", help="Filter by project name."),
    ] = None,
    folder: Annotated[
        str | None,
        typer.Option("--folder", help="Filter by folder name."),
    ] = None,
    starred: Annotated[
        bool,
        typer.Option("--starred", help="Show only starred snippets."),
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
    """List saved SQL snippets.

    \b
    Examples:
        dli sql list
        dli sql list --project marketing
        dli sql list --project marketing --folder "Campaign Analytics"
        dli sql list --starred
        dli sql list --format json
    """
    try:
        ctx = _get_context(mock)
        api = SqlAPI(context=ctx)

        with console.status("[bold green]Fetching snippets..."):
            result = api.list_snippets(
                project=project,
                folder=folder,
                starred=starred,
                limit=limit,
                offset=offset,
            )

        if output_format == "json":
            console.print_json(json.dumps(result.model_dump(mode="json"), default=str))
            return

        if not result.snippets:
            print_warning("No snippets found.")
            raise typer.Exit(0)

        table = Table(title="SQL Snippets", show_header=True)
        table.add_column("ID", style="cyan", no_wrap=True)
        table.add_column("NAME", style="white")
        table.add_column("FOLDER", style="dim")
        table.add_column("DIALECT", style="yellow")
        table.add_column("UPDATED", style="dim")

        for snippet in result.snippets:
            table.add_row(
                str(snippet.id),
                snippet.name,
                snippet.folder or "-",
                snippet.dialect.value,
                format_datetime(snippet.updated_at),
            )

        console.print(table)
        console.print(f"\n[dim]Showing {len(result.snippets)} of {result.total} snippets[/dim]")

    except SqlProjectNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e


@sql_app.command("get")
def get_snippet(
    snippet_id: Annotated[
        int,
        typer.Argument(help="Snippet ID to download."),
    ],
    file: Annotated[
        Path | None,
        typer.Option("--file", "-f", help="Output file path (stdout if omitted)."),
    ] = None,
    overwrite: Annotated[
        bool,
        typer.Option("--overwrite", help="Overwrite existing file without prompt."),
    ] = False,
    project: Annotated[
        str | None,
        typer.Option("--project", "-p", help="Project name."),
    ] = None,
    mock: Annotated[
        bool,
        typer.Option("--mock", hidden=True, help="Use mock mode for testing."),
    ] = False,
) -> None:
    """Download SQL snippet content.

    \b
    Examples:
        dli sql get 43
        dli sql get 43 -f ./insight.sql
        dli sql get 43 --file ./queries/insight.sql --overwrite
    """
    try:
        ctx = _get_context(mock)
        api = SqlAPI(context=ctx)

        with console.status(f"[bold green]Fetching snippet {snippet_id}..."):
            snippet = api.get(snippet_id, project=project)

        if file is None:
            # Print to stdout
            console.print(snippet.sql)
            return

        # Check if file exists
        if file.exists() and not overwrite and not Confirm.ask(f"File {file} exists. Overwrite?"):
            console.print("[dim]Cancelled.[/dim]")
            raise typer.Exit(0)

        # Create parent directories if needed
        file.parent.mkdir(parents=True, exist_ok=True)

        # Write SQL content
        file.write_text(snippet.sql)
        print_success(f"Downloaded snippet {snippet_id} to {file}")

    except SqlSnippetNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlAccessDeniedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlProjectNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e


@sql_app.command("put")
def put_snippet(
    snippet_id: Annotated[
        int,
        typer.Argument(help="Snippet ID to update."),
    ],
    file: Annotated[
        Path,
        typer.Option("--file", "-f", help="SQL file to upload."),
    ],
    force: Annotated[
        bool,
        typer.Option("--force", help="Skip confirmation prompt."),
    ] = False,
    project: Annotated[
        str | None,
        typer.Option("--project", "-p", help="Project name."),
    ] = None,
    mock: Annotated[
        bool,
        typer.Option("--mock", hidden=True, help="Use mock mode for testing."),
    ] = False,
) -> None:
    """Upload SQL file to update a snippet.

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

        # Get current snippet info for confirmation
        if not force:
            with console.status(f"[bold green]Fetching snippet {snippet_id}..."):
                snippet = api.get(snippet_id, project=project)

            if not Confirm.ask(
                f"Upload {file} to snippet {snippet_id} ({snippet.name})?\n"
                "This will overwrite the existing SQL content."
            ):
                console.print("[dim]Cancelled.[/dim]")
                raise typer.Exit(0)

        # Read and upload SQL
        sql = file.read_text()

        with console.status(f"[bold green]Uploading to snippet {snippet_id}..."):
            result = api.put(snippet_id, sql, project=project)

        print_success(f"Uploaded {file} to snippet {snippet_id}")
        console.print(f"[dim]Updated at:[/dim] {format_datetime(result.updated_at)}")

    except SqlFileNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlSnippetNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlAccessDeniedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlUpdateFailedError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
    except SqlProjectNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1) from e
