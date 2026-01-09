"""Query subcommand for DLI CLI.

Provides commands for browsing and managing query execution metadata.
Query metadata is fetched from Basecamp Server's catalog API.

Note: This command retrieves query metadata (state, timing, tables used),
not query results.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    get_client,
    get_project_path,
    with_trace,
)
from dli.commands.utils import (
    console,
    format_datetime,
    print_error,
    print_success,
    print_warning,
)
from dli.core.query.models import (
    QueryScope,
    QueryState,
)

# Status style constants for Rich output
_STATUS_STYLES: dict[str, str] = {
    "pending": "blue",
    "running": "cyan",
    "success": "green",
    "failed": "red",
    "cancelled": "magenta",
}

# Account type style constants
_ACCOUNT_TYPE_STYLES: dict[str, str] = {
    "personal": "green",
    "system": "yellow",
}


# Create query subcommand app
query_app = typer.Typer(
    name="query",
    help="Browse and manage query execution metadata.",
    no_args_is_help=True,
)


def _get_status_style(status: str) -> str:
    """Return Rich style for status display.

    Args:
        status: Status string (query state).

    Returns:
        Rich color style name for the status.
    """
    return _STATUS_STYLES.get(status.lower(), "white")


def _get_account_type_style(account_type: str) -> str:
    """Return Rich style for account type display.

    Args:
        account_type: Account type string (personal or system).

    Returns:
        Rich color style name for the account type.
    """
    return _ACCOUNT_TYPE_STYLES.get(account_type.lower(), "white")


def _format_duration(seconds: float | None) -> str:
    """Format duration in seconds to human-readable format.

    Args:
        seconds: Duration in seconds or None.

    Returns:
        Formatted duration string (e.g., '12.5s', '2m 30s') or '-' if None.
    """
    if seconds is None:
        return "-"
    if seconds < 60:
        return f"{seconds:.1f}s"
    minutes = int(seconds // 60)
    remaining_seconds = seconds % 60
    return f"{minutes}m {remaining_seconds:.0f}s"


def _format_bytes(bytes_value: int | None) -> str:
    """Format bytes to human-readable format.

    Args:
        bytes_value: Bytes count or None.

    Returns:
        Formatted string (e.g., '1.5 GB') or '-' if None.
    """
    if bytes_value is None:
        return "-"
    if bytes_value >= 1_000_000_000_000:
        return f"{bytes_value / 1_000_000_000_000:.2f} TB"
    if bytes_value >= 1_000_000_000:
        return f"{bytes_value / 1_000_000_000:.2f} GB"
    if bytes_value >= 1_000_000:
        return f"{bytes_value / 1_000_000:.2f} MB"
    if bytes_value >= 1_000:
        return f"{bytes_value / 1_000:.2f} KB"
    return f"{bytes_value} B"


@query_app.command("list")
@with_trace("query list")
def list_queries(
    account_keyword: Annotated[
        str | None,
        typer.Argument(
            help="Filter by account name (searches account names only).",
        ),
    ] = None,
    scope: Annotated[
        QueryScope,
        typer.Option(
            "--scope",
            help="Query scope: my (default), system, user, or all.",
        ),
    ] = QueryScope.MY,
    sql: Annotated[
        str | None,
        typer.Option("--sql", help="Filter by SQL query text content."),
    ] = None,
    status: Annotated[
        QueryState | None,
        typer.Option(
            "--status",
            "-S",
            help="Filter by state: pending, running, success, failed, cancelled.",
        ),
    ] = None,
    tag: Annotated[
        list[str] | None,
        typer.Option("--tag", "-t", help="Filter by tag (repeatable, AND logic)."),
    ] = None,
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Maximum number of results."),
    ] = 10,
    offset: Annotated[
        int,
        typer.Option("--offset", help="Pagination offset."),
    ] = 0,
    since: Annotated[
        str,
        typer.Option("--since", help="Start time (ISO8601 or relative: 1h, 7d)."),
    ] = "24h",
    until: Annotated[
        str | None,
        typer.Option("--until", help="End time (ISO8601 or relative)."),
    ] = None,
    engine: Annotated[
        str | None,
        typer.Option("--engine", help="Filter by engine: bigquery, trino."),
    ] = None,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """List queries with scope-based filtering.

    The optional ACCOUNT_KEYWORD searches account names only.
    Use --sql to search query text content.

    \b
    Scope values:
      my      - Queries by current authenticated user (default)
      system  - Queries from system/service accounts
      user    - Queries from personal (non-system) accounts
      all     - All accessible queries

    Examples:
        dli query list
        dli query list --status failed
        dli query list --scope system
        dli query list airflow --scope system
        dli query list --scope all --sql "SELECT * FROM users"
        dli query list --tag team::analytics --since 7d
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching queries..."):
        response = client.query_list(
            scope=scope.value,
            account_keyword=account_keyword,
            sql_pattern=sql,
            state=status.value if status else None,
            tags=tag,
            engine=engine,
            since=since,
            until=until,
            limit=limit,
            offset=offset,
        )

    if not response.success:
        print_error(response.error or "Failed to list queries")
        raise typer.Exit(1)

    data = response.data if isinstance(response.data, dict) else {}
    queries = data.get("queries", [])
    total_count = data.get("total_count", len(queries))
    has_more = data.get("has_more", False)

    if not queries:
        print_warning("No queries found matching the filters.")
        raise typer.Exit(0)

    if format_output == "json":
        console.print_json(json.dumps(data, default=str))
        return

    # Build table based on scope
    show_account = scope != QueryScope.MY
    table = Table(title=f"Queries ({len(queries)})", show_header=True)
    table.add_column("QUERY_ID", style="cyan", no_wrap=True, max_width=35)

    if show_account:
        table.add_column("ACCOUNT", style="white", max_width=25)
        table.add_column("TYPE", style="dim")

    table.add_column("ENGINE", style="yellow")
    table.add_column("STATE", style="green")
    table.add_column("STARTED", style="dim")
    table.add_column("DURATION", style="magenta", justify="right")
    table.add_column("TABLES", style="blue", justify="right")

    for q in queries:
        query_state = q.get("state", "-")
        account_type = q.get("account_type", "-")

        row = [q.get("query_id", "-")]

        if show_account:
            row.append(q.get("account", "-"))
            row.append(
                f"[{_get_account_type_style(account_type)}]{account_type}[/]"
            )

        row.extend(
            [
                q.get("engine", "-"),
                f"[{_get_status_style(query_state)}]{query_state}[/]",
                format_datetime(q.get("started_at"), include_seconds=True),
                _format_duration(q.get("duration_seconds")),
                str(q.get("tables_used_count", 0)),
            ]
        )

        table.add_row(*row)

    console.print(table)

    # Show pagination info
    if has_more or total_count > len(queries):
        console.print(f"\n[dim]Showing {len(queries)} of {total_count} queries[/dim]")


@query_app.command("show")
@with_trace("query show")
def show_query(
    query_id: Annotated[
        str,
        typer.Argument(help="Query ID to show details for."),
    ],
    full_query: Annotated[
        bool,
        typer.Option("--full-query", help="Show complete query text (not truncated)."),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Show detailed metadata for a specific query.

    Examples:
        dli query show bq_job_abc123
        dli query show bq_job_abc123 --full-query
        dli query show bq_job_abc123 --format json
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching query details..."):
        response = client.query_get(
            query_id=query_id,
            include_full_query=full_query,
        )

    if not response.success:
        if response.status_code == 404:
            print_error(f"Query '{query_id}' not found")
        elif response.status_code == 403:
            print_error(f"Access denied to query '{query_id}'")
        else:
            print_error(response.error or "Failed to get query details")
        raise typer.Exit(1)

    detail = response.data if isinstance(response.data, dict) else {}

    if format_output == "json":
        console.print_json(json.dumps(detail, default=str))
        return

    # Display query details
    query_state = detail.get("state", "-")
    account_type = detail.get("account_type", "-")

    console.print("\n[bold]Query Details:[/bold]")
    console.print(f"  [dim]Query ID:[/dim]     [cyan]{detail.get('query_id', '-')}[/cyan]")
    console.print(f"  [dim]Engine:[/dim]       {detail.get('engine', '-')}")
    console.print(
        f"  [dim]State:[/dim]        [{_get_status_style(query_state)}]{query_state}[/]"
    )
    console.print(f"  [dim]Account:[/dim]      {detail.get('account', '-')}")
    console.print(
        f"  [dim]Account Type:[/dim] [{_get_account_type_style(account_type)}]{account_type}[/]"
    )

    # Timing section
    console.print("\n[bold]Timing:[/bold]")
    console.print(
        f"  [dim]Started:[/dim]      {format_datetime(detail.get('started_at'), include_seconds=True)}"
    )
    console.print(
        f"  [dim]Finished:[/dim]     {format_datetime(detail.get('finished_at'), include_seconds=True)}"
    )
    console.print(
        f"  [dim]Duration:[/dim]     {_format_duration(detail.get('duration_seconds'))}"
    )
    console.print(
        f"  [dim]Queue Time:[/dim]   {_format_duration(detail.get('queue_time_seconds'))}"
    )

    # Resources section
    console.print("\n[bold]Resources:[/bold]")
    console.print(
        f"  [dim]Bytes Processed:[/dim]  {_format_bytes(detail.get('bytes_processed'))}"
    )
    console.print(
        f"  [dim]Bytes Billed:[/dim]     {_format_bytes(detail.get('bytes_billed'))}"
    )
    console.print(
        f"  [dim]Slot Time:[/dim]        {_format_duration(detail.get('slot_time_seconds'))}"
    )

    rows_affected = detail.get("rows_affected")
    console.print(
        f"  [dim]Rows Affected:[/dim]    {rows_affected if rows_affected is not None else '-'}"
    )

    # Tables used section
    tables_used = detail.get("tables_used", [])
    if tables_used:
        console.print("\n[bold]Tables Used:[/bold]")
        for t in tables_used:
            operation = t.get("operation", "read")
            op_style = "green" if operation == "read" else "yellow"
            console.print(f"  - {t.get('name', '-')} ([{op_style}]{operation}[/])")

    # Tags section
    tags = detail.get("tags", [])
    if tags:
        console.print("\n[bold]Tags:[/bold]")
        for tag_value in tags:
            console.print(f"  - {tag_value}")

    # Error section (if failed)
    if query_state == "failed":
        console.print("\n[bold red]Error:[/bold red]")
        console.print(f"  [dim]Code:[/dim]    {detail.get('error_code', '-')}")
        console.print(f"  [dim]Message:[/dim] {detail.get('error_message', '-')}")

    # Query text section
    query_text = detail.get("query_text") if full_query else detail.get("query_preview")
    if query_text:
        console.print("\n[bold]Query Preview:[/bold]")
        # Truncate for display if not full
        if not full_query and len(query_text) > 500:
            query_text = query_text[:500] + f"...truncated ({len(detail.get('query_text', query_text))} chars)"
        console.print(f"  {query_text}")

    console.print()


@query_app.command("cancel")
@with_trace("query cancel")
def cancel_query(
    query_id: Annotated[
        str | None,
        typer.Argument(
            help="Query ID to cancel (mutually exclusive with --user).",
        ),
    ] = None,
    user: Annotated[
        str | None,
        typer.Option("--user", help="Cancel all running queries for this account."),
    ] = None,
    dry_run: Annotated[
        bool,
        typer.Option("--dry-run", help="Show what would be cancelled without executing."),
    ] = False,
    force: Annotated[
        bool,
        typer.Option("--force", help="Skip confirmation prompt."),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Cancel running query(s).

    Cancel a specific query by ID, or cancel all running queries for an account
    using --user.

    Examples:
        dli query cancel bq_job_abc123
        dli query cancel --user airflow-prod
        dli query cancel --user airflow-prod --dry-run
        dli query cancel --user alice@company.com --force
    """
    # Validate mutually exclusive arguments
    if query_id and user:
        print_error("Cannot specify both QUERY_ID and --user")
        raise typer.Exit(1)

    if not query_id and not user:
        print_error("Must specify either QUERY_ID or --user")
        raise typer.Exit(1)

    project_path = get_project_path(path)
    client = get_client(project_path)

    if query_id:
        # Cancel specific query
        with console.status("[bold yellow]Cancelling query..."):
            response = client.query_cancel(
                query_id=query_id,
                dry_run=dry_run,
            )

        if not response.success:
            if response.status_code == 404:
                print_error(f"Query '{query_id}' not found")
            elif response.status_code == 403:
                print_error(f"Access denied to cancel query '{query_id}'")
            else:
                print_error(response.error or "Failed to cancel query")
            raise typer.Exit(1)

        result = response.data if isinstance(response.data, dict) else {}

        if format_output == "json":
            console.print_json(json.dumps(result, default=str))
            return

        if dry_run:
            console.print(f"[dim][DRY RUN][/dim] Would cancel query: [cyan]{query_id}[/cyan]")
        else:
            print_success(f"Query cancelled: {query_id}")

    else:
        # Cancel all queries for user
        # First, do a dry-run to get list of queries
        with console.status("[bold green]Fetching running queries..."):
            response = client.query_cancel(
                user=user,
                dry_run=True,
            )

        if not response.success:
            print_error(response.error or "Failed to fetch running queries")
            raise typer.Exit(1)

        result = response.data if isinstance(response.data, dict) else {}
        queries = result.get("queries", [])

        if not queries:
            print_warning(f"No running queries found for account '{user}'")
            raise typer.Exit(0)

        # Show queries to be cancelled
        if dry_run:
            console.print(
                f"[dim][DRY RUN][/dim] Would cancel {len(queries)} running queries for account '[cyan]{user}[/cyan]':"
            )
        else:
            console.print(
                f"Found {len(queries)} running queries for account '[cyan]{user}[/cyan]':"
            )

        for q in queries:
            duration = _format_duration(q.get("duration_seconds"))
            console.print(f"  - [cyan]{q.get('query_id', '-')}[/cyan] (running for {duration})")

        if dry_run:
            if format_output == "json":
                console.print_json(json.dumps(result, default=str))
            return

        # Confirm cancellation
        if not force:
            confirm = typer.confirm(f"\nCancel all {len(queries)} queries?")
            if not confirm:
                console.print("[dim]Cancelled[/dim]")
                raise typer.Exit(0)

        # Execute cancellation
        with console.status("[bold yellow]Cancelling queries..."):
            response = client.query_cancel(
                user=user,
                dry_run=False,
            )

        if not response.success:
            print_error(response.error or "Failed to cancel queries")
            raise typer.Exit(1)

        result = response.data if isinstance(response.data, dict) else {}
        cancelled_count = result.get("cancelled_count", 0)

        if format_output == "json":
            console.print_json(json.dumps(result, default=str))
            return

        print_success(f"Cancelled {cancelled_count} queries for account '{user}'")
