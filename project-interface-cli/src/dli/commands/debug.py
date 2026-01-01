"""Debug CLI command - Environment diagnostics and connection testing.

This module provides the `dli debug` command for comprehensive environment
diagnostics including system, configuration, server, auth, and network checks.

Example:
    $ dli debug                    # Run all checks
    $ dli debug --connection       # Test database connectivity only
    $ dli debug --auth             # Test authentication only
    $ dli debug --json             # Output in JSON format
    $ dli debug --verbose          # Show detailed output
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated

from rich.console import Console
import typer

from dli import __version__
from dli.api.debug import DebugAPI
from dli.commands.base import get_project_path
from dli.core.debug.models import CheckCategory, CheckResult, CheckStatus, DebugResult
from dli.models.common import ExecutionContext

debug_app = typer.Typer(
    name="debug",
    help="Environment diagnostics and connection testing.",
    no_args_is_help=False,
)

console = Console()


# Status symbols and colors
STATUS_SYMBOLS = {
    CheckStatus.PASS: ("[green][PASS][/green]", "green"),
    CheckStatus.FAIL: ("[red][FAIL][/red]", "red"),
    CheckStatus.WARN: ("[yellow][WARN][/yellow]", "yellow"),
    CheckStatus.SKIP: ("[dim][SKIP][/dim]", "dim"),
}

# Category display names
CATEGORY_NAMES = {
    CheckCategory.SYSTEM: "System",
    CheckCategory.CONFIG: "Configuration",
    CheckCategory.SERVER: "Server",
    CheckCategory.AUTH: "Authentication",
    CheckCategory.DATABASE: "Database",
    CheckCategory.NETWORK: "Network",
}

# Category display order
CATEGORY_ORDER = [
    CheckCategory.SYSTEM,
    CheckCategory.CONFIG,
    CheckCategory.SERVER,
    CheckCategory.AUTH,
    CheckCategory.DATABASE,
    CheckCategory.NETWORK,
]


def _print_check_result(check: CheckResult, verbose: bool = False) -> None:
    """Print a single check result.

    Args:
        check: The check result to print.
        verbose: Whether to show verbose output.
    """
    symbol, _ = STATUS_SYMBOLS.get(check.status, ("[?]", "white"))
    console.print(f"  {symbol} {check.name}: {check.message}")

    # Show details in verbose mode
    if verbose and check.details:
        for key, value in check.details.items():
            if key not in ("version", "path"):  # Skip redundant info
                console.print(f"         [dim]{key}: {value}[/dim]")

    # Show error and remediation for failures
    if check.status == CheckStatus.FAIL and check.error:
        console.print(f"         [red]Error: {check.error}[/red]")
        if check.remediation:
            console.print()
            console.print("         [yellow]Remediation:[/yellow]")
            for line in check.remediation.split("\n"):
                console.print(f"         [dim]{line}[/dim]")


def _print_debug_result(result: DebugResult, verbose: bool = False) -> None:
    """Print the complete debug result.

    Args:
        result: The debug result to print.
        verbose: Whether to show verbose output.
    """
    # Print header
    console.print(f"[bold blue]dli debug[/bold blue] v{result.version}")
    console.print()

    # Group checks by category
    by_category = result.by_category

    # Print checks by category in order
    for category in CATEGORY_ORDER:
        if category not in by_category:
            continue

        checks = by_category[category]
        category_name = CATEGORY_NAMES.get(category, category.value.title())
        console.print(f"[bold]{category_name}:[/bold]")

        for check in checks:
            _print_check_result(check, verbose)

        console.print()

    # Print summary
    if result.success:
        console.print(
            f"[green]All checks passed ({result.passed_count}/{result.total_count})[/green]"
        )
    else:
        console.print(
            f"[red]Some checks failed ({result.failed_count} failed, "
            f"{result.passed_count} passed, {result.total_count} total)[/red]"
        )


def _print_json_result(result: DebugResult) -> None:
    """Print the debug result as JSON.

    Args:
        result: The debug result to print.
    """
    console.print_json(result.model_dump_json(indent=2))


def _merge_debug_results(results: list[DebugResult]) -> DebugResult:
    """Merge multiple debug results into one.

    Args:
        results: List of debug results to merge.

    Returns:
        A single merged DebugResult.
    """
    all_checks: list[CheckResult] = []
    for r in results:
        all_checks.extend(r.checks)

    # Success if no failures
    success = all(c.status != CheckStatus.FAIL for c in all_checks)

    return DebugResult(
        version=__version__,
        success=success,
        checks=all_checks,
    )


@debug_app.callback(invoke_without_command=True)
def debug(
    ctx: typer.Context,
    connection: Annotated[
        bool,
        typer.Option(
            "--connection",
            "-c",
            help="Test database connectivity only.",
        ),
    ] = False,
    auth: Annotated[
        bool,
        typer.Option(
            "--auth",
            "-a",
            help="Test authentication only.",
        ),
    ] = False,
    network: Annotated[
        bool,
        typer.Option(
            "--network",
            "-n",
            help="Test network connectivity only.",
        ),
    ] = False,
    server: Annotated[
        bool,
        typer.Option(
            "--server",
            "-s",
            help="Test Basecamp Server connection only.",
        ),
    ] = False,
    project: Annotated[
        bool,
        typer.Option(
            "--project",
            "-p",
            help="Validate project configuration only.",
        ),
    ] = False,
    verbose: Annotated[
        bool,
        typer.Option(
            "--verbose",
            "-v",
            help="Show detailed diagnostic information.",
        ),
    ] = False,
    json_output: Annotated[
        bool,
        typer.Option(
            "--json",
            help="Output in JSON format.",
        ),
    ] = False,
    dialect: Annotated[
        str | None,
        typer.Option(
            "--dialect",
            "-d",
            help="Target dialect: bigquery, trino.",
        ),
    ] = None,
    path: Annotated[
        Path | None,
        typer.Option(
            "--path",
            help="Project path for config resolution.",
        ),
    ] = None,
    timeout: Annotated[
        int,
        typer.Option(
            "--timeout",
            "-t",
            help="Connection timeout in seconds.",
        ),
    ] = 30,
) -> None:
    """Run environment diagnostics and connection tests.

    By default, runs all diagnostic checks. Use flags to run specific checks only.

    Examples:
        # Run all checks
        $ dli debug

        # Test database connectivity only
        $ dli debug --connection

        # Test authentication only
        $ dli debug --auth

        # Show verbose output
        $ dli debug --verbose

        # Output as JSON
        $ dli debug --json
    """
    # Get project path
    project_path = get_project_path(path)

    # Create execution context
    context = ExecutionContext(
        project_path=project_path,
        timeout=timeout,
    )

    # Create API
    api = DebugAPI(context=context)

    # Determine which checks to run
    run_all = not any([connection, auth, network, server, project])

    if run_all:
        result = api.run_all(timeout=timeout)
    else:
        # Run selected checks
        results: list[DebugResult] = []

        if connection:
            results.append(api.check_connection(dialect=dialect))
        if auth:
            results.append(api.check_auth())
        if network:
            results.append(api.check_network())
        if server:
            results.append(api.check_server())
        if project:
            results.append(api.check_project())

        # Also run system checks for context
        results.insert(0, api.check_system())

        # Merge results
        result = _merge_debug_results(results)

    # Output
    if json_output:
        _print_json_result(result)
    else:
        _print_debug_result(result, verbose=verbose)

    # Exit code
    if not result.success:
        raise typer.Exit(1)


__all__ = ["debug_app"]
