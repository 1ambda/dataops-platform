"""Lineage subcommand for DLI CLI.

Provides commands for querying data lineage information from the server.
This module supports table-level lineage only (no column-level lineage).

All lineage operations are SERVER-BASED ONLY, querying registered datasets
from the Basecamp server. No local SQLGlot processing is performed.

Commands:
    show: Display full lineage (upstream and downstream)
    upstream: Show upstream dependencies
    downstream: Show downstream dependents
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.table import Table
from rich.tree import Tree
import typer

from dli.commands.base import (
    ListOutputFormat,
    get_client,
    get_project_path,
    with_trace,
)
from dli.commands.utils import (
    console,
    print_error,
    print_warning,
)
from dli.core.lineage import (
    LineageNode,
    LineageResult,
)
from dli.core.lineage.client import LineageClient, LineageClientError

# Create lineage subcommand app
lineage_app = typer.Typer(
    name="lineage",
    help="Data lineage commands (table-level, server-based).",
    no_args_is_help=True,
)


def _get_lineage_client(project_path: Path) -> LineageClient:
    """Create a LineageClient from project configuration.

    Args:
        project_path: Path to the project directory

    Returns:
        Configured LineageClient instance
    """
    basecamp_client = get_client(project_path)
    return LineageClient(basecamp_client)


def _format_node_name(node: LineageNode, show_type: bool = True) -> str:
    """Format a node name for display.

    Args:
        node: LineageNode to format
        show_type: Whether to include the type in brackets

    Returns:
        Formatted node name string
    """
    if show_type:
        type_color = {
            "Dataset": "cyan",
            "Metric": "magenta",
            "External": "yellow",
        }.get(node.type, "white")
        return f"[{type_color}]{node.name}[/{type_color}] [dim]({node.type})[/dim]"
    return f"[cyan]{node.name}[/cyan]"


def _build_upstream_tree(
    tree: Tree,
    result: LineageResult,
    current_node: str,
    visited: set[str],
) -> None:
    """Recursively build upstream tree visualization.

    Args:
        tree: Rich Tree to add branches to
        result: LineageResult containing all nodes and edges
        current_node: Name of the current node
        visited: Set of visited node names to prevent cycles
    """
    if current_node in visited:
        return
    visited.add(current_node)

    # Find edges where current_node is the target (upstream sources)
    upstream_edges = [e for e in result.edges if e.target == current_node]

    for edge in upstream_edges:
        source_name = edge.source
        source_node = next((n for n in result.nodes if n.name == source_name), None)
        if source_node:
            branch = tree.add(_format_node_name(source_node))
            _build_upstream_tree(branch, result, source_name, visited)


def _build_downstream_tree(
    tree: Tree,
    result: LineageResult,
    current_node: str,
    visited: set[str],
) -> None:
    """Recursively build downstream tree visualization.

    Args:
        tree: Rich Tree to add branches to
        result: LineageResult containing all nodes and edges
        current_node: Name of the current node
        visited: Set of visited node names to prevent cycles
    """
    if current_node in visited:
        return
    visited.add(current_node)

    # Find edges where current_node is the source (downstream targets)
    downstream_edges = [e for e in result.edges if e.source == current_node]

    for edge in downstream_edges:
        target_name = edge.target
        target_node = next((n for n in result.nodes if n.name == target_name), None)
        if target_node:
            branch = tree.add(_format_node_name(target_node))
            _build_downstream_tree(branch, result, target_name, visited)


def _display_lineage_tree(result: LineageResult, direction: str) -> None:
    """Display lineage as a tree visualization.

    Args:
        result: LineageResult to display
        direction: Direction of lineage ('upstream', 'downstream', 'both')
    """
    root_label = (
        f"[bold green]{result.root.name}[/bold green] [dim]({result.root.type})[/dim]"
    )

    if direction == "upstream":
        tree = Tree(f"[bold]Upstream Dependencies[/bold]\n{root_label}")
        _build_upstream_tree(tree, result, result.root.name, set())
        console.print(tree)
    elif direction == "downstream":
        tree = Tree(f"[bold]Downstream Dependents[/bold]\n{root_label}")
        _build_downstream_tree(tree, result, result.root.name, set())
        console.print(tree)
    else:
        # Both directions
        console.print()

        # Upstream tree
        up_tree = Tree("[bold blue]Upstream[/bold blue] (depends on)")
        _build_upstream_tree(up_tree, result, result.root.name, set())

        # Downstream tree
        down_tree = Tree("[bold yellow]Downstream[/bold yellow] (depended by)")
        _build_downstream_tree(down_tree, result, result.root.name, set())

        # Display summary panel
        console.print(
            Panel(
                f"[cyan]{result.root.name}[/cyan]\n"
                f"[dim]Type:[/dim] {result.root.type}\n"
                f"[dim]Owner:[/dim] {result.root.owner or '-'}\n"
                f"[dim]Team:[/dim] {result.root.team or '-'}\n"
                f"[dim]Description:[/dim] {result.root.description or '-'}",
                title="[bold]Resource[/bold]",
                border_style="green",
            )
        )

        if result.total_upstream > 0:
            console.print(up_tree)
        else:
            console.print("[dim]No upstream dependencies found.[/dim]")

        console.print()

        if result.total_downstream > 0:
            console.print(down_tree)
        else:
            console.print("[dim]No downstream dependents found.[/dim]")


def _display_lineage_table(result: LineageResult) -> None:
    """Display lineage as a table.

    Args:
        result: LineageResult to display
    """
    # Nodes table
    if result.nodes:
        table = Table(
            title=f"Lineage for {result.root.name}",
            show_header=True,
        )
        table.add_column("Name", style="cyan", no_wrap=True)
        table.add_column("Type", style="yellow")
        table.add_column("Direction", style="green")
        table.add_column("Depth", style="magenta", justify="right")
        table.add_column("Owner", style="blue")

        for node in sorted(result.nodes, key=lambda n: n.depth):
            direction = "upstream" if node.depth < 0 else "downstream"
            table.add_row(
                node.name,
                node.type,
                direction,
                str(abs(node.depth)),
                node.owner or "-",
            )

        console.print(table)
    else:
        print_warning("No lineage found for this resource.")


def _lineage_to_dict(result: LineageResult) -> dict:
    """Convert LineageResult to a dictionary for JSON output.

    Args:
        result: LineageResult to convert

    Returns:
        Dictionary representation of the lineage
    """
    return {
        "root": {
            "name": result.root.name,
            "type": result.root.type,
            "owner": result.root.owner,
            "team": result.root.team,
            "description": result.root.description,
            "tags": result.root.tags,
        },
        "nodes": [
            {
                "name": n.name,
                "type": n.type,
                "owner": n.owner,
                "team": n.team,
                "description": n.description,
                "tags": n.tags,
                "depth": n.depth,
            }
            for n in result.nodes
        ],
        "edges": [
            {
                "source": e.source,
                "target": e.target,
                "edge_type": e.edge_type,
            }
            for e in result.edges
        ],
        "summary": {
            "direction": result.direction.value,
            "max_depth": result.max_depth,
            "total_upstream": result.total_upstream,
            "total_downstream": result.total_downstream,
        },
    }


@lineage_app.command("show")
@with_trace("lineage show")
def show_lineage(
    resource: Annotated[
        str,
        typer.Argument(help="Resource name (e.g., iceberg.analytics.daily_clicks)."),
    ],
    depth: Annotated[
        int,
        typer.Option(
            "--depth", "-d", help="Maximum traversal depth (-1 for unlimited)."
        ),
    ] = -1,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Show full lineage for a resource (upstream and downstream).

    Displays both what the resource depends on (upstream) and
    what depends on the resource (downstream).

    Examples:
        dli lineage show iceberg.analytics.daily_clicks
        dli lineage show iceberg.analytics.daily_clicks --depth 3
        dli lineage show iceberg.analytics.daily_clicks --format json
    """
    project_path = get_project_path(path)

    try:
        client = _get_lineage_client(project_path)
        result = client.get_lineage(
            resource_name=resource,
            direction="both",
            depth=depth,
        )
    except LineageClientError as e:
        print_error(e.message)
        raise typer.Exit(1)

    if format_output == "json":
        console.print_json(json.dumps(_lineage_to_dict(result), default=str))
        return

    _display_lineage_tree(result, "both")

    # Summary
    console.print()
    console.print(
        f"[dim]Summary: {result.total_upstream} upstream, "
        f"{result.total_downstream} downstream[/dim]"
    )


@lineage_app.command("upstream")
@with_trace("lineage upstream")
def show_upstream(
    resource: Annotated[
        str,
        typer.Argument(help="Resource name (e.g., iceberg.analytics.daily_clicks)."),
    ],
    depth: Annotated[
        int,
        typer.Option(
            "--depth", "-d", help="Maximum traversal depth (-1 for unlimited)."
        ),
    ] = -1,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Show upstream dependencies for a resource.

    Displays what the resource depends on (data sources that feed into it).

    Examples:
        dli lineage upstream iceberg.analytics.daily_clicks
        dli lineage upstream iceberg.analytics.daily_clicks --depth 3
    """
    project_path = get_project_path(path)

    try:
        client = _get_lineage_client(project_path)
        result = client.get_upstream(
            resource_name=resource,
            depth=depth,
        )
    except LineageClientError as e:
        print_error(e.message)
        raise typer.Exit(1)

    if format_output == "json":
        console.print_json(json.dumps(_lineage_to_dict(result), default=str))
        return

    if not result.nodes:
        print_warning(f"No upstream dependencies found for '{resource}'.")
        raise typer.Exit(0)

    _display_lineage_tree(result, "upstream")

    console.print()
    console.print(f"[dim]Total upstream dependencies: {result.total_upstream}[/dim]")


@lineage_app.command("downstream")
@with_trace("lineage downstream")
def show_downstream(
    resource: Annotated[
        str,
        typer.Argument(help="Resource name (e.g., iceberg.analytics.daily_clicks)."),
    ],
    depth: Annotated[
        int,
        typer.Option(
            "--depth", "-d", help="Maximum traversal depth (-1 for unlimited)."
        ),
    ] = -1,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Show downstream dependents for a resource.

    Displays what depends on the resource (consumers of this data).

    Examples:
        dli lineage downstream iceberg.analytics.daily_clicks
        dli lineage downstream iceberg.analytics.daily_clicks --depth 2
    """
    project_path = get_project_path(path)

    try:
        client = _get_lineage_client(project_path)
        result = client.get_downstream(
            resource_name=resource,
            depth=depth,
        )
    except LineageClientError as e:
        print_error(e.message)
        raise typer.Exit(1)

    if format_output == "json":
        console.print_json(json.dumps(_lineage_to_dict(result), default=str))
        return

    if not result.nodes:
        print_warning(f"No downstream dependents found for '{resource}'.")
        raise typer.Exit(0)

    _display_lineage_tree(result, "downstream")

    console.print()
    console.print(f"[dim]Total downstream dependents: {result.total_downstream}[/dim]")
