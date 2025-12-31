"""Quality subcommand for DLI CLI.

Provides commands for managing and executing data quality tests.
Supports both local and server-based test execution.

Commands:
    list: List quality tests registered on server
    get: Get details of a specific quality from server
    run: Execute quality tests from a Quality Spec (LOCAL/SERVER)
    validate: Validate a Quality Spec YML file
"""

from __future__ import annotations

import contextlib
import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.syntax import Syntax
from rich.table import Table
import typer

from dli.api.quality import QualityAPI
from dli.commands.base import (
    ListOutputFormat,
    get_project_path,
)
from dli.commands.utils import (
    console,
    format_datetime,
    parse_params,
    print_error,
    print_success,
    print_warning,
)
from dli.exceptions import (
    QualityNotFoundError,
    QualitySpecNotFoundError,
    QualitySpecParseError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.quality import DqStatus

# Create quality subcommand app
quality_app = typer.Typer(
    name="quality",
    help="Data quality testing commands.",
    no_args_is_help=True,
)


def _status_style(status: DqStatus | str) -> str:
    """Get Rich style for test status."""
    status_val = status.value if isinstance(status, DqStatus) else status
    styles = {
        "pass": "[green]PASS[/green]",
        "fail": "[red]FAIL[/red]",
        "warn": "[yellow]WARN[/yellow]",
        "error": "[red]ERR[/red]",
        "skipped": "[dim]SKIP[/dim]",
    }
    return styles.get(status_val, f"[dim]{status_val}[/dim]")


def _severity_style(severity: str) -> str:
    """Get Rich style for severity."""
    if severity == "error":
        return "[red]error[/red]"
    if severity == "warn":
        return "[yellow]warn[/yellow]"
    return f"[dim]{severity}[/dim]"


@quality_app.command("list")
def list_qualities(
    target_type: Annotated[
        str | None,
        typer.Option(
            "--target-type",
            help="Filter by target type (dataset or metric).",
        ),
    ] = None,
    target: Annotated[
        str | None,
        typer.Option("--target", help="Filter by target name (partial match)."),
    ] = None,
    status: Annotated[
        str | None,
        typer.Option("--status", help="Filter by status (active or inactive)."),
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
    """List quality tests registered on server.

    Examples:
        dli quality list
        dli quality list --target-type dataset
        dli quality list --target iceberg.analytics.daily_clicks
        dli quality list --status active --format json
    """
    project_path = get_project_path(path)

    try:
        # Use mock mode for now since server is not implemented
        ctx = ExecutionContext(
            project_path=project_path,
            execution_mode=ExecutionMode.MOCK,
        )
        api = QualityAPI(context=ctx)

        qualities = api.list_qualities(
            target_type=target_type,
            target_name=target,
            status=status,
        )

    except Exception as e:
        print_error(f"Failed to list qualities: {e}")
        raise typer.Exit(1)

    if not qualities:
        print_warning("No qualities found.")
        raise typer.Exit(0)

    if format_output == "json":
        data = [
            {
                "name": q.name,
                "target_urn": q.target_urn,
                "target_type": q.target_type.value,
                "target_name": q.target_name,
                "test_type": q.test_type,
                "status": q.status,
                "severity": q.severity.value,
                "description": q.description,
                "schedule": q.schedule,
                "last_run": q.last_run.isoformat() if q.last_run else None,
                "last_status": q.last_status.value if q.last_status else None,
            }
            for q in qualities
        ]
        console.print_json(json.dumps(data, default=str))
        return

    # Table output
    table = Table(
        title=f"Quality Tests ({len(qualities)})",
        show_header=True,
    )
    table.add_column("Name", style="cyan", no_wrap=True)
    table.add_column("Target", style="yellow")
    table.add_column("Type", style="magenta")
    table.add_column("Status", style="green")

    for q in qualities:
        table.add_row(
            q.name,
            q.target_urn,
            q.test_type,
            q.status,
        )

    console.print(table)


@quality_app.command("get")
def get_quality(
    quality_name: Annotated[
        str,
        typer.Argument(help="Quality name to retrieve."),
    ],
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    include_history: Annotated[
        bool,
        typer.Option("--include-history", help="Include recent execution history."),
    ] = False,
) -> None:
    """Get details of a specific quality from server.

    Examples:
        dli quality get pk_unique
        dli quality get pk_unique --format json
        dli quality get pk_unique --include-history
    """
    try:
        # Use mock mode for now
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = QualityAPI(context=ctx)

        quality = api.get(quality_name)

        if quality is None:
            print_error(f"Quality '{quality_name}' not found")
            raise typer.Exit(1)

    except QualityNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except Exception as e:
        print_error(f"Failed to get quality: {e}")
        raise typer.Exit(1)

    if format_output == "json":
        data = {
            "name": quality.name,
            "target_urn": quality.target_urn,
            "target_type": quality.target_type.value,
            "target_name": quality.target_name,
            "test_type": quality.test_type,
            "status": quality.status,
            "severity": quality.severity.value,
            "description": quality.description,
            "schedule": quality.schedule,
            "last_run": quality.last_run.isoformat() if quality.last_run else None,
            "last_status": quality.last_status.value if quality.last_status else None,
        }
        console.print_json(json.dumps(data, default=str))
        return

    # Rich output
    console.print()
    console.print(f"[bold cyan]{quality.name}[/bold cyan]")
    console.print(f"[dim]Target:[/dim] {quality.target_urn}")
    console.print(f"[dim]Type:[/dim] {quality.test_type} ({quality.target_type.value})")
    console.print(f"[dim]Severity:[/dim] {_severity_style(quality.severity.value)}")
    console.print(f"[dim]Status:[/dim] {quality.status}")

    if quality.description:
        console.print(f"[dim]Description:[/dim] {quality.description}")

    if quality.schedule:
        console.print(f"[dim]Schedule:[/dim] {quality.schedule}")

    if quality.last_run:
        console.print(f"[dim]Last Run:[/dim] {format_datetime(quality.last_run)}")
        if quality.last_status:
            console.print(
                f"[dim]Last Status:[/dim] {_status_style(quality.last_status)}"
            )

    console.print()


@quality_app.command("run")
def run_tests(
    spec_path: Annotated[
        str,
        typer.Argument(help="Path to Quality Spec YML file."),
    ],
    mode: Annotated[
        str,
        typer.Option("--mode", "-m", help="Execution mode (local or server)."),
    ] = "local",
    test: Annotated[
        list[str] | None,
        typer.Option("--test", "-t", help="Run specific test(s) by name."),
    ] = None,
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
    param: Annotated[
        list[str] | None,
        typer.Option("--param", "-P", help="Parameters (KEY=VALUE)."),
    ] = None,
) -> None:
    """Execute quality tests from a Quality Spec.

    Tests can be run in LOCAL mode (direct execution) or SERVER mode
    (via Basecamp Server with results saved to DB).

    Examples:
        dli quality run quality.iceberg.analytics.daily_clicks.yaml
        dli quality run quality.yaml --mode server
        dli quality run quality.yaml --test pk_unique --test not_null_user_id
        dli quality run quality.yaml --fail-fast
        dli quality run quality.yaml --param date=2025-01-01
    """
    project_path = get_project_path(path)

    # Parse parameters
    parameters = parse_params(param) if param else {}

    # Determine execution mode
    if mode.lower() == "server":
        execution_mode = ExecutionMode.SERVER
    else:
        execution_mode = ExecutionMode.LOCAL

    try:
        ctx = ExecutionContext(
            project_path=project_path,
            execution_mode=execution_mode,
            parameters=parameters,
        )
        api = QualityAPI(context=ctx)

        # Only show status message for table format
        if format_output != "json":
            console.print(f"\n[bold]Running quality tests ({mode} mode)...[/bold]\n")
            with console.status("[bold green]Executing tests..."):
                result = api.run(
                    spec_path,
                    tests=test,
                    parameters=parameters,
                    fail_fast=fail_fast,
                )
        else:
            result = api.run(
                spec_path,
                tests=test,
                parameters=parameters,
                fail_fast=fail_fast,
            )

    except QualitySpecNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except QualitySpecParseError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except Exception as e:
        print_error(f"Failed to run tests: {e}")
        raise typer.Exit(1)

    if format_output == "json":
        data = {
            "target_urn": result.target_urn,
            "execution_mode": result.execution_mode,
            "execution_id": result.execution_id,
            "started_at": result.started_at.isoformat(),
            "finished_at": result.finished_at.isoformat(),
            "duration_ms": result.duration_ms,
            "status": result.status.value,
            "passed_count": result.passed_count,
            "failed_count": result.failed_count,
            "test_results": result.test_results,
        }
        console.print_json(json.dumps(data, default=str))
    else:
        # Print report
        console.print(
            Panel(
                f"[bold]Quality Test Report[/bold]\n"
                f"Target: {result.target_urn}\n"
                f"Mode: {result.execution_mode.upper()}"
                + (f"\nExecution ID: {result.execution_id}" if result.execution_id else ""),
                border_style="blue",
            )
        )

        console.print()
        console.print("[bold]Tests:[/bold]")
        for test_result in result.test_results:
            status = test_result.get("status", "unknown")
            test_name = test_result.get("test_name", "unknown")
            exec_time = test_result.get("execution_time_ms", 0)
            failed_rows = test_result.get("failed_rows", 0)
            error_msg = test_result.get("error_message")

            status_str = _status_style(status)
            time_str = f"[dim][{exec_time}ms][/dim]"

            console.print(f"  {status_str} {test_name} {time_str}")

            if status in ("fail", "warn") and failed_rows > 0:
                console.print(f"      [dim]Failed rows: {failed_rows}[/dim]")

            if status == "error" and error_msg:
                console.print(f"      [red]Error: {error_msg}[/red]")

        # Summary
        console.print()
        summary_parts = []
        if result.passed_count > 0:
            summary_parts.append(f"[green]{result.passed_count} passed[/green]")
        if result.failed_count > 0:
            summary_parts.append(f"[red]{result.failed_count} failed[/red]")

        summary_str = ", ".join(summary_parts) if summary_parts else "No tests run"
        console.print(f"Summary: {summary_str}")
        console.print(f"[dim]Duration: {result.duration_ms}ms[/dim]")

        if result.status == DqStatus.PASS:
            print_success("All tests passed")
        else:
            print_error("Some tests failed")

    # Exit with error if tests failed
    if result.failed_count > 0:
        raise typer.Exit(1)


@quality_app.command("validate")
def validate_spec(
    spec_path: Annotated[
        str,
        typer.Argument(help="Path to Quality Spec YML file."),
    ],
    strict: Annotated[
        bool,
        typer.Option("--strict", help="Also validate that target exists."),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option("--format", "-f", help="Output format (table or json)."),
    ] = "table",
    test: Annotated[
        str | None,
        typer.Option("--test", "-t", help="Show details for a specific test."),
    ] = None,
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Validate a Quality Spec YML file.

    This command parses and validates the Quality Spec, showing any errors
    or warnings. Use --test to show details of a specific test definition.

    Examples:
        dli quality validate quality.iceberg.analytics.daily_clicks.yaml
        dli quality validate quality.yaml --strict
        dli quality validate quality.yaml --test pk_unique
        dli quality validate quality.yaml --format json
    """
    project_path = get_project_path(path)

    try:
        ctx = ExecutionContext(
            project_path=project_path,
            execution_mode=ExecutionMode.LOCAL,
        )
        api = QualityAPI(context=ctx)

        # Validate the spec
        result = api.validate(
            spec_path,
            strict=strict,
            tests=[test] if test else None,
        )

        # If showing specific test, also load spec
        spec = None
        if test or format_output == "table":
            with contextlib.suppress(QualitySpecNotFoundError, QualitySpecParseError):
                spec = api.get_spec(spec_path)

    except QualitySpecNotFoundError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except QualitySpecParseError as e:
        print_error(str(e))
        raise typer.Exit(1)
    except Exception as e:
        print_error(f"Failed to validate: {e}")
        raise typer.Exit(1)

    if format_output == "json":
        data = {
            "valid": result.valid,
            "errors": result.errors,
            "warnings": result.warnings,
        }
        if spec:
            data["spec"] = {
                "version": spec.version,
                "target": {
                    "type": spec.target.type.value,
                    "name": spec.target.name,
                    "urn": spec.target.urn,
                },
                "tests": [
                    {
                        "name": t.name,
                        "type": t.type.value,
                        "severity": t.severity.value,
                        "description": t.description,
                        "enabled": t.enabled,
                    }
                    for t in spec.tests
                ],
            }
        console.print_json(json.dumps(data, default=str))
        return

    # Table/Rich output
    if spec:
        console.print()
        console.print(f"[bold]Quality Spec:[/bold] {spec_path}")
        console.print(f"[dim]Version:[/dim] {spec.version}")
        console.print(f"[dim]Target:[/dim] {spec.target.urn}")
        console.print(f"[dim]Owner:[/dim] {spec.metadata.owner}")

        if spec.metadata.description:
            console.print(f"[dim]Description:[/dim] {spec.metadata.description}")

        if spec.schedule:
            console.print(f"[dim]Schedule:[/dim] {spec.schedule.cron}")

        # Show specific test if requested
        if test:
            test_def = spec.get_test(test)
            if test_def:
                console.print()
                console.print(f"[bold cyan]{test_def.name}[/bold cyan]")
                console.print(f"[dim]Type:[/dim] {test_def.type.value}")
                console.print(
                    f"[dim]Severity:[/dim] {_severity_style(test_def.severity.value)}"
                )
                console.print(f"[dim]Enabled:[/dim] {test_def.enabled}")

                if test_def.columns:
                    console.print(f"[dim]Columns:[/dim] {', '.join(test_def.columns)}")
                elif test_def.column:
                    console.print(f"[dim]Column:[/dim] {test_def.column}")

                if test_def.values:
                    console.print(f"[dim]Values:[/dim] {', '.join(test_def.values)}")

                if test_def.description:
                    console.print(f"[dim]Description:[/dim] {test_def.description}")

                if test_def.sql:
                    console.print()
                    console.print("[bold]SQL:[/bold]")
                    syntax = Syntax(test_def.sql, "sql", theme="monokai", line_numbers=True)
                    console.print(Panel(syntax, border_style="blue"))
            else:
                print_warning(f"Test '{test}' not found in spec")
        else:
            # Show all tests
            console.print()
            table = Table(title=f"Tests ({len(spec.tests)})", show_header=True)
            table.add_column("Name", style="cyan")
            table.add_column("Type", style="magenta")
            table.add_column("Severity", style="red")
            table.add_column("Enabled", style="green")

            for t in spec.tests:
                table.add_row(
                    t.name,
                    t.type.value,
                    t.severity.value,
                    "Yes" if t.enabled else "No",
                )

            console.print(table)

    # Show validation results
    console.print()
    if result.valid:
        print_success("Validation passed")
    else:
        print_error("Validation failed")

    if result.errors:
        console.print()
        console.print("[bold red]Errors:[/bold red]")
        for error in result.errors:
            console.print(f"  - {error}")

    if result.warnings:
        console.print()
        console.print("[bold yellow]Warnings:[/bold yellow]")
        for warning in result.warnings:
            console.print(f"  - {warning}")

    if not result.valid:
        raise typer.Exit(1)
