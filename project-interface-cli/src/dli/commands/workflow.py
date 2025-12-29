"""Workflow subcommand for DLI CLI.

Provides commands for managing and executing server-based workflows.
Workflows represent scheduled or adhoc execution of datasets via Airflow.

Note: Unlike `dli dataset run` which executes locally, `dli workflow run`
triggers execution on the server (Airflow).
"""

from __future__ import annotations

from datetime import datetime
import json
from pathlib import Path
from typing import Annotated, Literal

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    get_client,
    get_project_path,
)
from dli.commands.utils import (
    console,
    parse_params,
    print_error,
    print_success,
    print_warning,
)

# Workflow-specific type definitions for CLI options
WorkflowSourceType = Literal["code", "manual", "all"]
"""Valid source type options for workflow filtering."""

WorkflowStatusFilter = Literal["PENDING", "RUNNING", "COMPLETED", "FAILED", "KILLED"]
"""Valid status options for workflow history filtering."""

# Status style constants for Rich output
_STATUS_STYLES: dict[str, str] = {
    "active": "green",
    "paused": "yellow",
    "overridden": "dim",
    "disabled": "dim",
    "PENDING": "blue",
    "RUNNING": "cyan",
    "COMPLETED": "green",
    "FAILED": "red",
    "KILLED": "magenta",
}


# Create workflow subcommand app
workflow_app = typer.Typer(
    name="workflow",
    help="Workflow management and execution commands (server-based via Airflow).",
    no_args_is_help=True,
)


def _format_datetime(dt: datetime | str | None) -> str:
    """Format datetime for display.

    Args:
        dt: A datetime object, ISO format string, or None.

    Returns:
        Formatted date string (YYYY-MM-DD HH:MM:SS) or "-" if None.
    """
    if dt is None:
        return "-"
    if isinstance(dt, str):
        try:
            dt = datetime.fromisoformat(dt.replace("Z", "+00:00"))
        except ValueError:
            return dt
    return dt.strftime("%Y-%m-%d %H:%M:%S")


def _get_status_style(status: str) -> str:
    """Return Rich style for status display.

    Args:
        status: Status string (workflow or run status).

    Returns:
        Rich color style name for the status.
    """
    return _STATUS_STYLES.get(status, "white")


def _get_source_style(source: str) -> str:
    """Return Rich style for source type display.

    Args:
        source: Source type string (code or manual).

    Returns:
        Rich color style name for the source.
    """
    return "cyan" if source.lower() == "code" else "yellow"


@workflow_app.command("run")
def run_workflow(
    dataset_name: Annotated[
        str,
        typer.Argument(help="Dataset name to run."),
    ],
    params: Annotated[
        list[str] | None,
        typer.Option("--param", "-p", help="Parameter in key=value format."),
    ] = None,
    dry_run: Annotated[
        bool,
        typer.Option("--dry-run", help="Validate only, don't execute."),
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
    """Trigger adhoc workflow execution on server (Airflow).

    Unlike `dli dataset run` which runs locally, this command triggers
    execution on the server via Airflow.

    Examples:
        dli workflow run iceberg.analytics.daily_clicks -p execution_date=2024-01-15
        dli workflow run iceberg.analytics.daily_clicks --dry-run
    """
    project_path = get_project_path(path)
    params = params or []

    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)

    client = get_client(project_path)

    with console.status("[bold green]Triggering workflow run..."):
        response = client.workflow_run(
            dataset_name=dataset_name,
            params=param_dict,
            dry_run=dry_run,
        )

    if not response.success:
        print_error(response.error or "Failed to trigger workflow run")
        raise typer.Exit(1)

    result = response.data if isinstance(response.data, dict) else {}

    if format_output == "json":
        console.print_json(json.dumps(result, default=str))
        return

    if dry_run:
        print_success("Dry-run validation passed (no execution)")
    else:
        run_id = result.get("run_id", "unknown")
        console.print(f"Run started: [cyan]{run_id}[/cyan]")


@workflow_app.command("backfill")
def backfill_workflow(
    dataset_name: Annotated[
        str,
        typer.Argument(help="Dataset name to backfill."),
    ],
    start_date: Annotated[
        str,
        typer.Option("--start", "-s", help="Start date (YYYY-MM-DD)."),
    ],
    end_date: Annotated[
        str,
        typer.Option("--end", "-e", help="End date (YYYY-MM-DD)."),
    ],
    params: Annotated[
        list[str] | None,
        typer.Option(
            "--param", "-p", help="Additional parameters in key=value format."
        ),
    ] = None,
    dry_run: Annotated[
        bool,
        typer.Option("--dry-run", help="Validate date range only, don't execute."),
    ] = False,
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Run backfill for a date range.

    Executes the workflow sequentially from start to end date.
    Stops on first failure.

    Examples:
        dli workflow backfill iceberg.analytics.daily_clicks -s 2024-01-01 -e 2024-01-07
        dli workflow backfill iceberg.analytics.daily_clicks -s 2024-01-01 -e 2024-01-07 --dry-run
    """
    project_path = get_project_path(path)
    params = params or []

    try:
        param_dict = parse_params(params)
    except ValueError as e:
        print_error(str(e))
        raise typer.Exit(1)

    # Validate date format
    try:
        datetime.strptime(start_date, "%Y-%m-%d")
        datetime.strptime(end_date, "%Y-%m-%d")
    except ValueError:
        print_error("Invalid date format. Use YYYY-MM-DD.")
        raise typer.Exit(1)

    if start_date > end_date:
        print_error("Start date must be before or equal to end date.")
        raise typer.Exit(1)

    if dry_run:
        # Calculate number of dates
        start = datetime.strptime(start_date, "%Y-%m-%d")
        end = datetime.strptime(end_date, "%Y-%m-%d")
        dates_count = (end - start).days + 1
        print_success("Dry-run validation passed (no execution)")
        console.print(f"  [dim]Date range:[/dim] {start_date} to {end_date}")
        console.print(f"  [dim]Total dates:[/dim] {dates_count}")
        return

    client = get_client(project_path)

    with console.status("[bold green]Starting backfill..."):
        response = client.workflow_backfill(
            dataset_name=dataset_name,
            start_date=start_date,
            end_date=end_date,
            params=param_dict,
        )

    if not response.success:
        print_error(response.error or "Failed to start backfill")
        raise typer.Exit(1)

    result = response.data if isinstance(response.data, dict) else {}
    total_runs = result.get("total_runs", 0)
    console.print(f"Backfill started for [cyan]{dataset_name}[/cyan]")
    console.print(f"  [dim]Date range:[/dim] {start_date} to {end_date}")
    console.print(f"  [dim]Total runs:[/dim] {total_runs}")


@workflow_app.command("stop")
def stop_workflow(
    run_id: Annotated[
        str,
        typer.Argument(help="Run ID to stop."),
    ],
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Stop a running workflow execution.

    Examples:
        dli workflow stop iceberg.analytics.daily_clicks_20240115_093045
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold yellow]Stopping workflow..."):
        response = client.workflow_stop(run_id=run_id)

    if not response.success:
        print_error(response.error or "Failed to stop workflow")
        raise typer.Exit(1)

    print_success(f"Workflow stopped: {run_id}")


@workflow_app.command("status")
def status_workflow(
    run_id: Annotated[
        str,
        typer.Argument(help="Run ID to check status."),
    ],
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Get workflow run status.

    Examples:
        dli workflow status iceberg.analytics.daily_clicks_20240115_093045
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching status..."):
        response = client.workflow_status(run_id=run_id)

    if not response.success:
        print_error(response.error or "Failed to get workflow status")
        raise typer.Exit(1)

    result = response.data if isinstance(response.data, dict) else {}

    if format_output == "json":
        console.print_json(json.dumps(result, default=str))
        return

    status = result.get("status", "UNKNOWN")
    status_style = _get_status_style(status)

    console.print(f"\n[bold]Run ID:[/bold] [cyan]{run_id}[/cyan]")
    console.print(f"[bold]Status:[/bold] [{status_style}]{status}[/{status_style}]")
    console.print(f"[dim]Dataset:[/dim] {result.get('dataset_name', '-')}")
    console.print(f"[dim]Source:[/dim] {result.get('source', '-')}")
    console.print(f"[dim]Triggered By:[/dim] {result.get('triggered_by', '-')}")
    console.print(
        f"[dim]Started At:[/dim] {_format_datetime(result.get('started_at'))}"
    )
    console.print(f"[dim]Ended At:[/dim] {_format_datetime(result.get('ended_at'))}")

    if result.get("duration_seconds"):
        console.print(f"[dim]Duration:[/dim] {result['duration_seconds']}s")

    if result.get("error_message"):
        console.print(f"[red]Error:[/red] {result['error_message']}")


@workflow_app.command("list")
def list_workflows(
    source: Annotated[
        WorkflowSourceType,
        typer.Option("--source", help="Source type filter: code, manual, or all."),
    ] = "all",
    running: Annotated[
        bool,
        typer.Option("--running", help="Show only running workflows."),
    ] = False,
    enabled_only: Annotated[
        bool,
        typer.Option("--enabled-only", help="Show only enabled schedules."),
    ] = False,
    dataset: Annotated[
        str | None,
        typer.Option("--dataset", "-d", help="Filter by dataset name."),
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
    """List registered workflows.

    Examples:
        dli workflow list
        dli workflow list --source code
        dli workflow list --running
        dli workflow list --dataset iceberg.analytics.daily_clicks
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching workflows..."):
        response = client.workflow_list(
            source=source if source != "all" else None,
            running_only=running,
            enabled_only=enabled_only,
            dataset_filter=dataset,
        )

    if not response.success:
        print_error(response.error or "Failed to list workflows")
        raise typer.Exit(1)

    workflows = response.data if isinstance(response.data, list) else []

    if not workflows:
        print_warning("No workflows found.")
        raise typer.Exit(0)

    if format_output == "json":
        console.print_json(json.dumps(workflows, default=str))
        return

    table = Table(title=f"Workflows ({len(workflows)})", show_header=True)
    table.add_column("DATASET", style="cyan", no_wrap=True)
    table.add_column("SOURCE", style="yellow")
    table.add_column("STATUS", style="green")
    table.add_column("SCHEDULE", style="magenta")
    table.add_column("NEXT RUN", style="dim")

    for wf in workflows:
        # Determine status from paused/enabled fields
        is_paused = wf.get("paused", False)
        is_enabled = wf.get("enabled", True)

        if is_paused:
            wf_status = "paused"
        elif not is_enabled:
            wf_status = "disabled"
        else:
            wf_status = "active"

        source_type = wf.get("source", "-")
        next_run = wf.get("next_run_at")
        schedule = wf.get("schedule", "-")

        # Format next run display
        if is_paused or not is_enabled:
            next_run_display = "-"
        else:
            next_run_display = _format_datetime(next_run)

        table.add_row(
            wf.get("dataset_name", "-"),
            f"[{_get_source_style(source_type)}]{source_type}[/]",
            f"[{_get_status_style(wf_status)}]{wf_status}[/]",
            schedule or "-",
            next_run_display,
        )

    console.print(table)


@workflow_app.command("history")
def history_workflow(
    dataset: Annotated[
        str | None,
        typer.Option("--dataset", "-d", help="Filter by dataset name."),
    ] = None,
    source: Annotated[
        WorkflowSourceType,
        typer.Option("--source", help="Source type filter: code, manual, or all."),
    ] = "all",
    limit: Annotated[
        int,
        typer.Option("--limit", "-n", help="Number of records to show."),
    ] = 20,
    status: Annotated[
        WorkflowStatusFilter | None,
        typer.Option("--status", "-s", help="Filter by run status."),
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
    """Show workflow execution history.

    Examples:
        dli workflow history
        dli workflow history -d iceberg.analytics.daily_clicks
        dli workflow history --status FAILED -n 50
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Fetching history..."):
        response = client.workflow_history(
            dataset_filter=dataset,
            source=source if source != "all" else None,
            limit=limit,
            status_filter=status,
        )

    if not response.success:
        print_error(response.error or "Failed to get workflow history")
        raise typer.Exit(1)

    runs = response.data if isinstance(response.data, list) else []

    if not runs:
        print_warning("No execution history found.")
        raise typer.Exit(0)

    if format_output == "json":
        console.print_json(json.dumps(runs, default=str))
        return

    table = Table(title=f"Workflow History ({len(runs)})", show_header=True)
    table.add_column("RUN ID", style="cyan", no_wrap=True, max_width=40)
    table.add_column("DATASET", style="white")
    table.add_column("SOURCE", style="yellow")
    table.add_column("STATUS", style="green")
    table.add_column("STARTED", style="dim")
    table.add_column("ENDED", style="dim")

    for run in runs:
        run_status = run.get("status", "-")
        table.add_row(
            run.get("run_id", "-"),
            run.get("dataset_name", "-"),
            run.get("source", "-"),
            f"[{_get_status_style(run_status)}]{run_status}[/]",
            _format_datetime(run.get("started_at")),
            _format_datetime(run.get("ended_at")),
        )

    console.print(table)


@workflow_app.command("pause")
def pause_workflow(
    dataset_name: Annotated[
        str,
        typer.Argument(help="Dataset name to pause schedule."),
    ],
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Pause a workflow schedule.

    Works for both Code and Manual registered workflows.

    Examples:
        dli workflow pause iceberg.analytics.daily_clicks
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold yellow]Pausing workflow..."):
        response = client.workflow_pause(dataset_name=dataset_name)

    if not response.success:
        print_error(response.error or "Failed to pause workflow")
        raise typer.Exit(1)

    print_success(f"Workflow paused: {dataset_name}")


@workflow_app.command("unpause")
def unpause_workflow(
    dataset_name: Annotated[
        str,
        typer.Argument(help="Dataset name to unpause schedule."),
    ],
    path: Annotated[
        Path | None,
        typer.Option("--path", help="Project path."),
    ] = None,
) -> None:
    """Unpause (resume) a workflow schedule.

    Works for both Code and Manual registered workflows.

    Examples:
        dli workflow unpause iceberg.analytics.daily_clicks
    """
    project_path = get_project_path(path)
    client = get_client(project_path)

    with console.status("[bold green]Unpausing workflow..."):
        response = client.workflow_unpause(dataset_name=dataset_name)

    if not response.success:
        print_error(response.error or "Failed to unpause workflow")
        raise typer.Exit(1)

    print_success(f"Workflow unpaused: {dataset_name}")
