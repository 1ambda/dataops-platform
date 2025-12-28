"""Metric subcommand for DLI CLI.

Provides commands for managing and executing metrics.
Supports both local metrics and server-based operations.
"""

from __future__ import annotations

import csv as csv_module
import json
from pathlib import Path
import sys
from typing import Annotated

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    OutputFormat,
    SourceType,
    format_tags_display,
    get_client,
    get_project_path,
    load_metric_service,
    spec_to_dict,
    spec_to_list_dict,
    spec_to_register_dict,
)
from dli.commands.utils import (
    console,
    parse_params,
    print_data_table,
    print_error,
    print_sql,
    print_success,
    print_validation_result,
    print_warning,
)

# Create metric subcommand app
metric_app = typer.Typer(
    name="metric",
    help="Metric management and execution commands.",
    no_args_is_help=True,
)


@metric_app.command("list")
def list_metrics(
    source: Annotated[
        SourceType,
        typer.Option("--source", "-s", help="Source: local or server."),
    ] = "local",
    tag: Annotated[
        str | None,
        typer.Option("--tag", "-t", help="Filter by tag."),
    ] = None,
    owner: Annotated[
        str | None,
        typer.Option("--owner", "-o", help="Filter by owner."),
    ] = None,
    search: Annotated[
        str | None,
        typer.Option("--search", help="Search in name/description."),
    ] = None,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """List metrics from local project or server.

    Examples:
        dli metric list
        dli metric list --source server
        dli metric list --tag daily --format json
    """
    project_path = get_project_path(path)

    if source == "server":
        # List from server
        client = get_client(project_path)
        response = client.list_metrics(tag=tag, owner=owner, search=search)

        if not response.success:
            print_error(response.error or "Failed to list metrics from server")
            raise typer.Exit(1)

        metrics = response.data or []
    else:
        # List from local
        try:
            service = load_metric_service(project_path)
            local_metrics = service.list_metrics(tag=tag, owner=owner)
            metrics = [spec_to_list_dict(m) for m in local_metrics]
        except Exception as e:
            print_error(f"Failed to list local metrics: {e}")
            raise typer.Exit(1)

    if not metrics:
        print_warning("No metrics found.")
        raise typer.Exit(0)

    if format_output == "json":
        console.print_json(json.dumps(metrics, default=str))
        return

    # Table output
    table = Table(
        title=f"Metrics ({len(metrics)}) - Source: {source}", show_header=True
    )
    table.add_column("Name", style="cyan", no_wrap=True)
    table.add_column("Owner", style="green")
    table.add_column("Team", style="yellow")
    table.add_column("Tags", style="magenta")

    for m in metrics:
        if isinstance(m, dict):
            tags = m.get("tags", [])
            table.add_row(
                m.get("name", ""),
                m.get("owner", "-"),
                m.get("team", "-"),
                format_tags_display(tags),
            )

    console.print(table)


@metric_app.command("get")
def get_metric(
    name: Annotated[str, typer.Argument(help="Metric name.")],
    source: Annotated[
        SourceType,
        typer.Option("--source", "-s", help="Source: local or server."),
    ] = "local",
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Get metric details.

    Examples:
        dli metric get iceberg.reporting.user_summary
        dli metric get iceberg.reporting.user_summary --source server
    """
    project_path = get_project_path(path)

    if source == "server":
        client = get_client(project_path)
        response = client.get_metric(name)

        if not response.success:
            print_error(response.error or f"Metric '{name}' not found on server")
            raise typer.Exit(1)

        if not isinstance(response.data, dict):
            print_error(f"Invalid response data for metric '{name}'")
            raise typer.Exit(1)

        metric_data: dict = response.data
    else:
        try:
            service = load_metric_service(project_path)
            metric = service.get_metric(name)
            if not metric:
                print_error(f"Metric '{name}' not found locally")
                raise typer.Exit(1)

            metric_data = spec_to_dict(metric, include_parameters=True)
        except Exception as e:
            print_error(f"Failed to get metric: {e}")
            raise typer.Exit(1)

    if format_output == "json":
        console.print_json(json.dumps(metric_data, default=str))
        return

    # Table output for details
    console.print(f"\n[bold cyan]{metric_data.get('name')}[/bold cyan]")
    console.print(f"[dim]Type:[/dim] {metric_data.get('type', 'Metric')}")
    console.print(f"[dim]Owner:[/dim] {metric_data.get('owner', '-')}")
    console.print(f"[dim]Team:[/dim] {metric_data.get('team', '-')}")
    console.print(f"[dim]Description:[/dim] {metric_data.get('description', '-')}")
    console.print(f"[dim]Tags:[/dim] {', '.join(metric_data.get('tags', [])) or '-'}")

    params = metric_data.get("parameters", [])
    if params:
        console.print("\n[bold]Parameters:[/bold]")
        param_table = Table(show_header=True)
        param_table.add_column("Name", style="cyan")
        param_table.add_column("Type", style="yellow")
        param_table.add_column("Required", style="red")
        param_table.add_column("Default", style="green")

        for p in params:
            param_table.add_row(
                p.get("name", ""),
                p.get("type", ""),
                "Y" if p.get("required") else "",
                str(p.get("default")) if p.get("default") is not None else "-",
            )
        console.print(param_table)


@metric_app.command("run")
def run_metric(
    name: Annotated[str, typer.Argument(help="Metric name to run.")],
    params: Annotated[
        list[str] | None,
        typer.Option("--param", "-p", help="Parameter in key=value format."),
    ] = None,
    output: Annotated[
        OutputFormat,
        typer.Option("--output", "-o", help="Output format (table, json, csv)."),
    ] = "table",
    limit: Annotated[
        int | None,
        typer.Option("--limit", "-l", help="Limit output rows."),
    ] = None,
    dry_run: Annotated[
        bool,
        typer.Option("--dry-run", help="Only validate, don't execute."),
    ] = False,
    show_sql: Annotated[
        bool,
        typer.Option("--show-sql", help="Show executed SQL."),
    ] = False,
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Execute a metric query (SELECT).

    Examples:
        dli metric run iceberg.reporting.user_summary -p date=2024-01-01
        dli metric run iceberg.reporting.user_summary -p date=2024-01-01 -o json
    """
    project_path = get_project_path(path)

    # Fix: Handle None default for mutable list argument
    params = params or []

    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)

    try:
        service = load_metric_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    metric = service.get_metric(name)
    if not metric:
        print_error(f"Metric '{name}' not found")
        raise typer.Exit(1)

    with console.status("[bold green]Executing metric..."):
        result = service.execute(name, param_dict, dry_run=dry_run)

    if not result.success:
        print_error(result.error_message or "Execution failed")
        raise typer.Exit(1)

    if dry_run:
        print_success("Dry-run completed (no execution)")
    else:
        print_success(f"Query executed: {result.row_count} rows")
        if result.execution_time_ms:
            console.print(f"  [dim]Time:[/dim] {result.execution_time_ms:.1f}ms")

    if show_sql and result.rendered_sql:
        console.print()
        print_sql(result.rendered_sql)

    if not dry_run and result.rows:
        data = result.rows
        if limit:
            data = data[:limit]

        console.print()

        if output == "json":
            console.print_json(json.dumps(data, default=str, indent=2))
        elif output == "csv":
            if result.columns:
                writer = csv_module.DictWriter(
                    sys.stdout,
                    fieldnames=result.columns,
                    extrasaction="ignore",
                )
                writer.writeheader()
                writer.writerows(data)
        elif result.columns:
            print_data_table(
                result.columns,
                data,
                title=f"Results ({len(data)} rows)",
            )

            if limit and len(result.rows) > limit:
                console.print(f"[dim]Showing {limit} of {result.row_count} rows.[/dim]")


@metric_app.command("validate")
def validate_metric(
    name: Annotated[str, typer.Argument(help="Metric name to validate.")],
    params: Annotated[
        list[str] | None,
        typer.Option("--param", "-p", help="Parameter in key=value format."),
    ] = None,
    show_sql: Annotated[
        bool,
        typer.Option("--show-sql/--no-sql", help="Show rendered SQL."),
    ] = True,
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Validate a metric query.

    Examples:
        dli metric validate iceberg.reporting.user_summary -p date=2024-01-01
    """
    project_path = get_project_path(path)

    # Fix: Handle None default for mutable list argument
    params = params or []

    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)

    try:
        service = load_metric_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    metric = service.get_metric(name)
    if not metric:
        print_error(f"Metric '{name}' not found")
        raise typer.Exit(1)

    with console.status("[bold green]Validating..."):
        results = service.validate(name, param_dict)

    all_valid = all(r.is_valid for r in results)
    all_errors = []
    all_warnings = []

    for result in results:
        all_errors.extend(result.errors)
        all_warnings.extend(result.warnings)

    print_validation_result(all_valid, all_errors, all_warnings)

    if show_sql and all_valid:
        rendered_sql = service.render_sql(name, param_dict)
        if rendered_sql:
            console.print()
            print_sql(rendered_sql)

    if not all_valid:
        raise typer.Exit(1)


@metric_app.command("register")
def register_metric(
    name: Annotated[str, typer.Argument(help="Metric name to register.")],
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", "-f", help="Force overwrite if exists."),
    ] = False,
) -> None:
    """Register a local metric to the server.

    Examples:
        dli metric register iceberg.reporting.user_summary
        dli metric register iceberg.reporting.user_summary --force
    """
    project_path = get_project_path(path)

    try:
        service = load_metric_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    metric = service.get_metric(name)
    if not metric:
        print_error(f"Metric '{name}' not found locally")
        raise typer.Exit(1)

    client = get_client(project_path)

    # Check if exists
    if not force:
        existing = client.get_metric(name)
        if existing.success:
            print_error(
                f"Metric '{name}' already exists on server. Use --force to overwrite."
            )
            raise typer.Exit(1)

    spec_data = spec_to_register_dict(metric)

    with console.status("[bold green]Registering metric..."):
        response = client.register_metric(spec_data)

    if response.success:
        print_success(f"Metric '{name}' registered successfully")
    else:
        print_error(response.error or "Registration failed")
        raise typer.Exit(1)
