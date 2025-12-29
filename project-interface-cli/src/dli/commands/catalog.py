"""Catalog subcommand for DLI CLI.

Provides commands for browsing and searching the data catalog.
Uses implicit routing based on identifier part count:
- 1-part: project.* -> list tables in project
- 2-part: project.dataset -> list tables in dataset
- 3-part: project.dataset.table -> table detail
- 4-part: engine.project.dataset.table -> engine-specific table detail

Note: All metadata is fetched from Basecamp Server API.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated, Any, Literal

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    format_tags_display,
    get_client,
    get_project_path,
)
from dli.commands.utils import (
    console,
    format_datetime,
    print_error,
    print_warning,
)

# Supported query engines for 4-part identifier detection
SUPPORTED_ENGINES: frozenset[str] = frozenset({"bigquery", "trino", "hive"})

# Section options for detail view
SectionType = Literal[
    "basic", "columns", "quality", "freshness", "ownership", "impact", "queries"
]

# Create catalog subcommand app
catalog_app = typer.Typer(
    name="catalog",
    help="Browse and search the data catalog.",
    invoke_without_command=True,
)


def _format_row_count(count: int | None) -> str:
    """Format row count with K/M/B suffix.

    Args:
        count: Row count or None.

    Returns:
        Formatted string (e.g., '1.5M') or '-' if None.
    """
    if count is None:
        return "-"
    if count >= 1_000_000_000:
        return f"{count / 1_000_000_000:.1f}B"
    if count >= 1_000_000:
        return f"{count / 1_000_000:.1f}M"
    if count >= 1_000:
        return f"{count / 1_000:.1f}K"
    return str(count)


def _get_quality_style(score: int | None) -> str:
    """Return Rich style for quality score display.

    Args:
        score: Quality score (0-100) or None.

    Returns:
        Rich color style name.
    """
    if score is None:
        return "dim"
    if score >= 90:
        return "green"
    if score >= 70:
        return "yellow"
    if score >= 50:
        return "orange3"
    return "red"


def _parse_identifier(identifier: str) -> tuple[str | None, str, int]:
    """Parse identifier to detect engine and count parts.

    Args:
        identifier: User-provided identifier string.

    Returns:
        Tuple of (engine or None, table_reference, part_count).
        part_count is the number of parts after removing engine prefix.
    """
    parts = identifier.split(".")
    if parts and parts[0].lower() in SUPPORTED_ENGINES:
        # 4-part: engine.project.dataset.table
        engine = parts[0].lower()
        table_ref = ".".join(parts[1:])
        return engine, table_ref, len(parts) - 1
    # 1/2/3-part: project[.dataset[.table]]
    return None, identifier, len(parts)


def _display_table_list(
    tables: list[dict[str, Any]],
    title: str,
    format_output: ListOutputFormat,
) -> None:
    """Display list of tables in table or JSON format.

    Args:
        tables: List of TableInfo dicts.
        title: Table title.
        format_output: Output format (table or json).
    """
    if format_output == "json":
        console.print_json(json.dumps(tables, default=str))
        return

    if not tables:
        print_warning("No tables found.")
        return

    table = Table(title=f"{title} ({len(tables)})", show_header=True)
    table.add_column("Name", style="cyan", no_wrap=True)
    table.add_column("Engine", style="yellow")
    table.add_column("Owner", style="green")
    table.add_column("Rows", style="magenta", justify="right")
    table.add_column("Last Updated", style="dim")
    table.add_column("Tags", style="blue")

    for t in tables:
        table.add_row(
            t.get("name", "-"),
            t.get("engine", "-"),
            t.get("owner", "-") or "-",
            _format_row_count(t.get("row_count")),
            format_datetime(t.get("last_updated")),
            format_tags_display(t.get("tags", [])),
        )

    console.print(table)


def _display_table_detail(
    detail: dict[str, Any],
    section: SectionType | None = None,
    format_output: ListOutputFormat = "table",
) -> None:
    """Display table details.

    Args:
        detail: TableDetail dict.
        section: Optional specific section to display.
        format_output: Output format (table or json).
    """
    if format_output == "json":
        console.print_json(json.dumps(detail, default=str))
        return

    # Always show basic info header
    console.print()
    console.print(f"[bold cyan]{detail.get('name', 'Unknown')}[/bold cyan]")
    console.print(f"[dim]Engine:[/dim] {detail.get('engine', '-')}")
    console.print(f"[dim]Description:[/dim] {detail.get('description', '-') or '-'}")
    console.print(f"[dim]URL:[/dim] {detail.get('basecamp_url', '-')}")
    console.print(f"[dim]Tags:[/dim] {', '.join(detail.get('tags', [])) or '-'}")

    # If section is specified, show only that section
    sections_to_show = [section] if section else [
        "ownership", "columns", "quality", "freshness", "impact", "queries"
    ]

    # Ownership section
    if "ownership" in sections_to_show:
        ownership = detail.get("ownership", {})
        console.print("\n[bold]Ownership[/bold]")
        console.print(f"  [dim]Owner:[/dim] {ownership.get('owner', '-') or '-'}")
        console.print(f"  [dim]Team:[/dim] {ownership.get('team', '-') or '-'}")
        stewards = ownership.get("stewards", [])
        console.print(f"  [dim]Stewards:[/dim] {', '.join(stewards) or '-'}")
        consumers = ownership.get("consumers", [])
        console.print(f"  [dim]Consumers:[/dim] {', '.join(consumers) or '-'}")

    # Columns section
    if "columns" in sections_to_show:
        columns = detail.get("columns", [])
        if columns:
            console.print("\n[bold]Columns[/bold]")
            col_table = Table(show_header=True, box=None)
            col_table.add_column("Name", style="cyan")
            col_table.add_column("Type", style="yellow")
            col_table.add_column("PII", style="red")
            col_table.add_column("Fill Rate", justify="right")
            col_table.add_column("Description", style="dim")

            for col in columns:
                pii_mark = "[lock]" if col.get("is_pii") else ""
                fill_rate = col.get("fill_rate")
                fill_str = f"{fill_rate * 100:.0f}%" if fill_rate is not None else "-"
                col_table.add_row(
                    col.get("name", "-"),
                    col.get("data_type", "-"),
                    pii_mark,
                    fill_str,
                    col.get("description", "-") or "-",
                )
            console.print(col_table)

    # Quality section
    if "quality" in sections_to_show:
        quality = detail.get("quality", {})
        score = quality.get("score")
        score_style = _get_quality_style(score)
        score_str = str(score) if score is not None else "-"

        console.print("\n[bold]Quality[/bold]")
        console.print(f"  [dim]Score:[/dim] [{score_style}]{score_str}[/{score_style}]")
        console.print(
            f"  [dim]Tests:[/dim] {quality.get('passed_tests', 0)} passed, "
            f"{quality.get('failed_tests', 0)} failed, "
            f"{quality.get('warnings', 0)} warnings"
        )

        recent_tests = quality.get("recent_tests", [])
        if recent_tests:
            console.print("  [dim]Recent Tests:[/dim]")
            for test in recent_tests[:3]:
                status = test.get("status", "unknown")
                status_color = {"pass": "green", "fail": "red", "warn": "yellow"}.get(
                    status, "white"
                )
                console.print(
                    f"    - [{status_color}]{status}[/{status_color}] "
                    f"{test.get('test_name', '-')} ({test.get('test_type', '-')})"
                )

    # Freshness section
    if "freshness" in sections_to_show:
        freshness = detail.get("freshness", {})
        is_stale = freshness.get("is_stale", False)
        freshness_style = "red" if is_stale else "green"
        freshness_status = "stale" if is_stale else "fresh"

        console.print("\n[bold]Freshness[/bold]")
        console.print(
            f"  [dim]Status:[/dim] [{freshness_style}]{freshness_status}[/{freshness_style}]"
        )
        console.print(
            f"  [dim]Last Updated:[/dim] {format_datetime(freshness.get('last_updated'))}"
        )
        console.print(
            f"  [dim]Update Frequency:[/dim] {freshness.get('update_frequency', '-') or '-'}"
        )
        avg_lag = freshness.get("avg_update_lag_hours")
        lag_str = f"{avg_lag:.1f}h" if avg_lag is not None else "-"
        console.print(f"  [dim]Avg Lag:[/dim] {lag_str}")

    # Impact section
    if "impact" in sections_to_show:
        impact = detail.get("impact", {})
        console.print("\n[bold]Impact (Downstream)[/bold]")
        console.print(f"  [dim]Total:[/dim] {impact.get('total_downstream', 0)}")

        tables_impact = impact.get("tables", [])
        if tables_impact:
            console.print(f"  [dim]Tables:[/dim] {', '.join(tables_impact[:5])}")
            if len(tables_impact) > 5:
                console.print(f"    [dim]...and {len(tables_impact) - 5} more[/dim]")

        metrics_impact = impact.get("metrics", [])
        if metrics_impact:
            console.print(f"  [dim]Metrics:[/dim] {', '.join(metrics_impact[:5])}")

        dashboards = impact.get("dashboards", [])
        if dashboards:
            console.print(f"  [dim]Dashboards:[/dim] {', '.join(dashboards[:5])}")

    # Sample Queries section
    if "queries" in sections_to_show:
        queries = detail.get("sample_queries", [])
        if queries:
            console.print("\n[bold]Popular Queries[/bold]")
            for i, q in enumerate(queries[:3], 1):
                console.print(
                    f"  {i}. [cyan]{q.get('title', 'Untitled')}[/cyan] "
                    f"([dim]{q.get('run_count', 0)} runs[/dim])"
                )

    console.print()


@catalog_app.callback(invoke_without_command=True)
def catalog_callback(
    ctx: typer.Context,
    identifier: Annotated[
        str | None,
        typer.Argument(
            help="Table identifier: project, project.dataset, project.dataset.table, "
            "or engine.project.dataset.table"
        ),
    ] = None,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Maximum number of results."),
    ] = 50,
    offset: Annotated[
        int,
        typer.Option("--offset", help="Pagination offset."),
    ] = 0,
    section: Annotated[
        SectionType | None,
        typer.Option(
            "--section", "-s",
            help="Show specific section: basic, columns, quality, freshness, ownership, impact, queries.",
        ),
    ] = None,
    sample: Annotated[
        bool,
        typer.Option("--sample", help="Include sample data (PII masked by server)."),
    ] = False,
) -> None:
    """Browse data catalog with implicit routing.

    The command behavior depends on the identifier format:

    \b
    1-part (project):        dli catalog my-project
                             Lists all tables in the project.

    \b
    2-part (project.dataset): dli catalog my-project.analytics
                              Lists tables in the dataset.

    \b
    3-part (table):          dli catalog my-project.analytics.users
                             Shows table details.

    \b
    4-part (engine.table):   dli catalog bigquery.my-project.analytics.users
                             Shows table details for specific engine.

    Examples:
        dli catalog my-project
        dli catalog my-project.analytics
        dli catalog my-project.analytics.users
        dli catalog my-project.analytics.users --section columns
        dli catalog my-project.analytics.users --format json
    """
    # If a subcommand is invoked, skip the callback logic
    if ctx.invoked_subcommand is not None:
        return

    # If no identifier provided, show help
    if identifier is None:
        console.print(ctx.get_help())
        raise typer.Exit(0)

    # Use default project path for implicit routing
    project_path = get_project_path(None)
    client = get_client(project_path)

    # Parse identifier to determine behavior
    engine, table_ref, part_count = _parse_identifier(identifier)

    if part_count == 1:
        # 1-part: list tables in project
        with console.status("[bold green]Fetching tables..."):
            response = client.catalog_list(
                project=table_ref,
                limit=limit,
                offset=offset,
            )

        if not response.success:
            print_error(response.error or "Failed to list tables")
            raise typer.Exit(1)

        tables = response.data if isinstance(response.data, list) else []
        _display_table_list(tables, f"Tables in {table_ref}", format_output)

    elif part_count == 2:
        # 2-part: list tables in dataset
        parts = table_ref.split(".")
        project = parts[0]
        dataset = parts[1] if len(parts) > 1 else ""

        with console.status("[bold green]Fetching tables..."):
            response = client.catalog_list(
                project=project,
                dataset=dataset,
                limit=limit,
                offset=offset,
            )

        if not response.success:
            print_error(response.error or "Failed to list tables")
            raise typer.Exit(1)

        tables = response.data if isinstance(response.data, list) else []
        _display_table_list(tables, f"Tables in {table_ref}", format_output)

    elif part_count >= 3:
        # 3-part or 4-part: show table detail
        with console.status("[bold green]Fetching table details..."):
            response = client.catalog_get(
                table_ref=table_ref,
                include_sample=sample,
            )

        if not response.success:
            print_error(response.error or f"Table '{table_ref}' not found.")
            raise typer.Exit(1)

        detail = response.data if isinstance(response.data, dict) else {}

        # Add impact from lineage if available
        if "impact" not in detail or not detail.get("impact", {}).get("total_downstream"):
            # Try to get downstream lineage for impact
            try:
                from dli.core.lineage.client import LineageClient

                lineage_client = LineageClient(client)
                lineage = lineage_client.get_downstream(table_ref, depth=1)

                impact = {
                    "total_downstream": len(lineage.nodes),
                    "tables": [n.name for n in lineage.nodes if n.type == "Dataset"],
                    "datasets": [n.name for n in lineage.nodes if n.type == "Dataset"],
                    "metrics": [n.name for n in lineage.nodes if n.type == "Metric"],
                    "dashboards": [n.name for n in lineage.nodes if n.type == "Dashboard"],
                }
                detail["impact"] = impact
            except Exception:
                pass  # Keep existing impact or empty

        _display_table_detail(detail, section=section, format_output=format_output)

    else:
        print_error("Invalid identifier format.")
        raise typer.Exit(1)


@catalog_app.command("list")
def list_tables(
    project: Annotated[
        str | None,
        typer.Option("--project", "-p", help="Filter by project."),
    ] = None,
    dataset: Annotated[
        str | None,
        typer.Option("--dataset", "-d", help="Filter by dataset."),
    ] = None,
    owner: Annotated[
        str | None,
        typer.Option("--owner", "-o", help="Filter by owner."),
    ] = None,
    team: Annotated[
        str | None,
        typer.Option("--team", "-t", help="Filter by team."),
    ] = None,
    tag: Annotated[
        list[str] | None,
        typer.Option("--tag", help="Filter by tag (can repeat, AND condition)."),
    ] = None,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Maximum number of results."),
    ] = 50,
    offset: Annotated[
        int,
        typer.Option("--offset", help="Pagination offset."),
    ] = 0,
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """List tables with advanced filters.

    Examples:
        dli catalog list --project my-project
        dli catalog list --owner data-team@example.com
        dli catalog list --tag tier::critical --tag pii
        dli catalog list --team @data-eng --format json
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching tables..."):
        response = client.catalog_list(
            project=project,
            dataset=dataset,
            owner=owner,
            team=team,
            tags=tag,
            limit=limit,
            offset=offset,
        )

    if not response.success:
        print_error(response.error or "Failed to list tables")
        raise typer.Exit(1)

    tables = response.data if isinstance(response.data, list) else []

    if not tables:
        print_warning("No tables found matching the filters.")
        raise typer.Exit(0)

    _display_table_list(tables, "Catalog Tables", format_output)


@catalog_app.command("search")
def search_tables(
    keyword: Annotated[
        str,
        typer.Argument(help="Search keyword (searches in names, columns, descriptions, tags)."),
    ],
    project: Annotated[
        str | None,
        typer.Option("--project", "-p", help="Limit search to project."),
    ] = None,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Maximum number of results."),
    ] = 20,
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Search tables by keyword.

    Searches in table names, column names, descriptions, and tags.

    Examples:
        dli catalog search user
        dli catalog search "email" --project my-project
        dli catalog search pii --format json
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status(f"[bold green]Searching for '{keyword}'..."):
        response = client.catalog_search(
            keyword=keyword,
            project=project,
            limit=limit,
        )

    if not response.success:
        print_error(response.error or "Search failed")
        raise typer.Exit(1)

    tables = response.data if isinstance(response.data, list) else []

    if not tables:
        print_warning(f"No tables found matching '{keyword}'.")
        raise typer.Exit(0)

    _display_table_list(tables, f"Search Results for '{keyword}'", format_output)
