"""Quality subcommand for DLI CLI.

Provides commands for managing and executing data quality tests.
Supports both local and server-based test execution.

Commands:
    list: List quality tests for a resource or all resources
    run: Execute quality tests locally or on server
    show: Show details of a specific test result
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    get_client,
    get_project_path,
)
from dli.commands.utils import (
    console,
    print_error,
    print_success,
    print_warning,
)
from dli.core.quality import (
    DqStatus,
    DqTestConfig,
    DqTestResult,
    QualityExecutor,
    QualityRegistry,
    QualityReport,
)

# Create quality subcommand app
quality_app = typer.Typer(
    name="quality",
    help="Data quality testing commands.",
    no_args_is_help=True,
)


def _status_icon(status: DqStatus) -> str:
    """Get display icon for test status."""
    icons = {
        DqStatus.PASS: "[green]PASS[/green]",
        DqStatus.FAIL: "[red]FAIL[/red]",
        DqStatus.WARN: "[yellow]WARN[/yellow]",
        DqStatus.ERROR: "[red]ERR[/red]",
        DqStatus.SKIPPED: "[dim]SKIP[/dim]",
    }
    return icons.get(status, "[dim]?[/dim]")


def _print_test_result(result: DqTestResult) -> None:
    """Print a single test result with formatting."""
    icon = _status_icon(result.status)
    time_str = f"[dim][{result.execution_time_ms}ms][/dim]"

    console.print(f"  {icon} {result.test_name} {time_str}")

    if result.status == DqStatus.FAIL or result.status == DqStatus.WARN:
        if result.failed_rows > 0:
            console.print(f"      [dim]Failed rows: {result.failed_rows}[/dim]")

        # Show sample of failing rows
        for sample in result.failed_samples[:3]:
            sample_str = ", ".join(f"{k}={v}" for k, v in list(sample.items())[:4])
            if len(sample) > 4:
                sample_str += ", ..."
            console.print(f"      [dim]- {sample_str}[/dim]")

        if len(result.failed_samples) > 3:
            remaining = result.failed_rows - 3
            console.print(f"      [dim]... and {remaining} more[/dim]")

    if result.status == DqStatus.ERROR and result.error_message:
        console.print(f"      [red]Error: {result.error_message}[/red]")


def _print_report(report: QualityReport) -> None:
    """Print a quality report summary."""
    console.print()
    console.print(
        Panel(
            f"[bold]Quality Tests: {report.resource_name}[/bold]\n"
            f"Executed on: {report.executed_on} | "
            f"Total time: {report.total_execution_time_ms}ms",
            border_style="blue",
        )
    )

    # Print individual results
    console.print()
    for result in report.results:
        _print_test_result(result)

    # Print summary
    console.print()
    summary_parts = []
    if report.passed > 0:
        summary_parts.append(f"[green]{report.passed} passed[/green]")
    if report.failed > 0:
        summary_parts.append(f"[red]{report.failed} failed[/red]")
    if report.warned > 0:
        summary_parts.append(f"[yellow]{report.warned} warned[/yellow]")
    if report.errors > 0:
        summary_parts.append(f"[red]{report.errors} errors[/red]")
    if report.skipped > 0:
        summary_parts.append(f"[dim]{report.skipped} skipped[/dim]")

    summary_str = ", ".join(summary_parts) if summary_parts else "No tests run"
    console.print(f"Summary: {summary_str}")

    if report.success:
        print_success("All tests passed")
    else:
        print_error("Some tests failed")


@quality_app.command("list")
def list_tests(
    resource: Annotated[
        str | None,
        typer.Option("--resource", "-r", help="Filter by resource name."),
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
    """List quality tests for a resource or all resources.

    Examples:
        dli quality list
        dli quality list --resource iceberg.analytics.daily_clicks
        dli quality list --format json
    """
    project_path = get_project_path(path)

    try:
        registry = QualityRegistry(project_path=project_path)
        registry.load_all()
    except Exception as e:
        print_error(f"Failed to load tests: {e}")
        raise typer.Exit(1)

    tests = registry.get_tests(resource_name=resource)

    if not tests:
        if resource:
            print_warning(f"No tests found for resource: {resource}")
        else:
            print_warning("No tests found in project.")
        raise typer.Exit(0)

    if format_output == "json":
        tests_data = [
            {
                "name": t.name,
                "type": t.test_type.value,
                "resource": t.resource_name,
                "columns": t.columns,
                "severity": t.severity.value,
                "enabled": t.enabled,
            }
            for t in tests
        ]
        console.print_json(json.dumps(tests_data, default=str))
        return

    # Table output
    table = Table(
        title=f"Quality Tests ({len(tests)})",
        show_header=True,
    )
    table.add_column("Resource", style="cyan", no_wrap=True)
    table.add_column("Test Name", style="yellow")
    table.add_column("Type", style="magenta")
    table.add_column("Columns", style="green")
    table.add_column("Severity", style="red")

    for test in tests:
        cols = ", ".join(test.columns[:3]) if test.columns else "-"
        if test.columns and len(test.columns) > 3:
            cols += "..."

        table.add_row(
            test.resource_name,
            test.name,
            test.test_type.value,
            cols,
            test.severity.value,
        )

    console.print(table)


@quality_app.command("run")
def run_tests(
    resource: Annotated[
        str | None,
        typer.Argument(help="Resource name to test (optional if --all is used)."),
    ] = None,
    run_all: Annotated[
        bool,
        typer.Option("--all", "-a", help="Run all tests in the project."),
    ] = False,
    test_name: Annotated[
        str | None,
        typer.Option("--test", "-t", help="Run a specific test by name."),
    ] = None,
    server: Annotated[
        bool,
        typer.Option("--server", "-s", help="Execute tests on server."),
    ] = False,
    fail_fast: Annotated[
        bool,
        typer.Option("--fail-fast", help="Stop on first failure."),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Execute quality tests for a resource or all resources.

    Tests can be executed locally (requires SQL executor) or on the server.

    Examples:
        dli quality run iceberg.analytics.daily_clicks
        dli quality run iceberg.analytics.daily_clicks --server
        dli quality run --all
        dli quality run --all --server
        dli quality run iceberg.analytics.daily_clicks --test unique_user_id
    """
    project_path = get_project_path(path)

    # Validate arguments
    if not resource and not run_all:
        print_error("Either provide a resource name or use --all flag")
        raise typer.Exit(1)

    try:
        registry = QualityRegistry(project_path=project_path)
        registry.load_all()
    except Exception as e:
        print_error(f"Failed to load tests: {e}")
        raise typer.Exit(1)

    # Get tests to run
    tests = registry.get_tests(
        resource_name=resource if not run_all else None,
        test_name=test_name,
    )

    if not tests:
        if resource:
            print_warning(f"No tests found for resource: {resource}")
        else:
            print_warning("No tests found in project.")
        raise typer.Exit(0)

    # Create executor
    config = DqTestConfig(fail_fast=fail_fast)
    client = get_client(project_path) if server else None
    executor = QualityExecutor(client=client, config=config)

    # Execute tests
    execution_mode = "server" if server else "local"
    console.print(f"\n[bold]Running {len(tests)} tests ({execution_mode} mode)...[/bold]\n")

    with console.status("[bold green]Executing tests..."):
        report = executor.run_all(tests, on_server=server)

    if format_output == "json":
        report_data = {
            "resource": report.resource_name,
            "total": report.total_tests,
            "passed": report.passed,
            "failed": report.failed,
            "warned": report.warned,
            "errors": report.errors,
            "skipped": report.skipped,
            "success": report.success,
            "executed_on": report.executed_on,
            "total_time_ms": report.total_execution_time_ms,
            "results": [
                {
                    "test_name": r.test_name,
                    "resource": r.resource_name,
                    "status": r.status.value,
                    "failed_rows": r.failed_rows,
                    "execution_time_ms": r.execution_time_ms,
                    "error_message": r.error_message,
                }
                for r in report.results
            ],
        }
        console.print_json(json.dumps(report_data, default=str))
    else:
        _print_report(report)

    # Exit with error code if tests failed
    if not report.success:
        raise typer.Exit(1)


@quality_app.command("show")
def show_test(
    resource: Annotated[
        str,
        typer.Argument(help="Resource name."),
    ],
    test_name: Annotated[
        str,
        typer.Option("--test", "-t", help="Test name to show."),
    ],
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Show details of a specific test definition.

    Examples:
        dli quality show iceberg.analytics.daily_clicks --test not_null_user_id
    """
    project_path = get_project_path(path)

    try:
        registry = QualityRegistry(project_path=project_path)
        registry.load_all()
    except Exception as e:
        print_error(f"Failed to load tests: {e}")
        raise typer.Exit(1)

    test = registry.get_test(resource, test_name)
    if not test:
        print_error(f"Test '{test_name}' not found for resource '{resource}'")
        raise typer.Exit(1)

    if format_output == "json":
        test_data = {
            "name": test.name,
            "type": test.test_type.value,
            "resource": test.resource_name,
            "columns": test.columns,
            "params": test.params,
            "severity": test.severity.value,
            "description": test.description,
            "enabled": test.enabled,
            "sql": test.sql,
            "file": test.file,
        }
        console.print_json(json.dumps(test_data, default=str))
        return

    # Rich output
    console.print()
    console.print(f"[bold cyan]{test.name}[/bold cyan]")
    console.print(f"[dim]Type:[/dim] {test.test_type.value}")
    console.print(f"[dim]Resource:[/dim] {test.resource_name}")
    console.print(f"[dim]Severity:[/dim] {test.severity.value}")
    console.print(f"[dim]Enabled:[/dim] {test.enabled}")

    if test.columns:
        console.print(f"[dim]Columns:[/dim] {', '.join(test.columns)}")

    if test.params:
        console.print(f"[dim]Parameters:[/dim]")
        for k, v in test.params.items():
            console.print(f"  {k}: {v}")

    if test.description:
        console.print(f"[dim]Description:[/dim] {test.description}")

    if test.sql:
        from rich.syntax import Syntax

        console.print()
        console.print("[bold]SQL:[/bold]")
        syntax = Syntax(test.sql, "sql", theme="monokai", line_numbers=True)
        console.print(Panel(syntax, border_style="blue"))
