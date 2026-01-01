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


@metric_app.command("format")
def format_metric(
    name: Annotated[str, typer.Argument(help="Metric name to format.")],
    check: Annotated[
        bool,
        typer.Option("--check", help="Check only, don't modify files (CI mode)."),
    ] = False,
    sql_only: Annotated[
        bool,
        typer.Option("--sql-only", help="Format SQL file only."),
    ] = False,
    yaml_only: Annotated[
        bool,
        typer.Option("--yaml-only", help="Format YAML file only."),
    ] = False,
    dialect: Annotated[
        str | None,
        typer.Option("--dialect", "-d", help="SQL dialect (bigquery, trino, snowflake, etc.)."),
    ] = None,
    lint: Annotated[
        bool,
        typer.Option("--lint", help="Apply lint rules."),
    ] = False,
    fix: Annotated[
        bool,
        typer.Option("--fix", help="Auto-fix lint violations (requires --lint)."),
    ] = False,
    diff: Annotated[
        bool,
        typer.Option("--diff", help="Show diff of changes."),
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
    """Format metric SQL and YAML files.

    Uses sqlfluff for SQL formatting (with Jinja template preservation)
    and ruamel.yaml for YAML formatting (with DLI standard key ordering).

    Examples:
        dli metric format iceberg.reporting.user_summary
        dli metric format iceberg.reporting.user_summary --check
        dli metric format iceberg.reporting.user_summary --sql-only
        dli metric format iceberg.reporting.user_summary --dialect trino
        dli metric format iceberg.reporting.user_summary --lint
        dli metric format iceberg.reporting.user_summary --check --diff
    """
    from dli.api.metric import MetricAPI
    from dli.models.common import ExecutionContext

    # Validate options
    if sql_only and yaml_only:
        print_error("Cannot use both --sql-only and --yaml-only")
        raise typer.Exit(1)

    if fix and not lint:
        print_error("--fix requires --lint to be enabled")
        raise typer.Exit(1)

    project_path = get_project_path(path)

    try:
        ctx = ExecutionContext(project_path=project_path)
        api = MetricAPI(context=ctx)

        with console.status("[bold green]Formatting metric..."):
            result = api.format(
                name,
                check_only=check,
                sql_only=sql_only,
                yaml_only=yaml_only,
                dialect=dialect,
                lint=lint,
                fix=fix,
            )

    except Exception as e:
        print_error(f"Format failed: {e}")
        raise typer.Exit(1)

    # Handle JSON output
    if format_output == "json":
        console.print_json(result.model_dump_json())
        if check and result.has_changes:
            raise typer.Exit(1)
        return

    # Table output
    mode_str = "check" if check else "format"
    console.print("\n[bold cyan]Format Result[/bold cyan]")
    console.print(f"[dim]Metric:[/dim] {result.name}")
    console.print(f"[dim]Mode:[/dim] {mode_str}")
    if lint:
        console.print("[dim]Lint:[/dim] enabled")

    if not result.files:
        print_warning("No files found to format")
        return

    # Show files table
    console.print("\n[bold]Files:[/bold]")
    file_table = Table(show_header=True)
    file_table.add_column("Mode", style="cyan", width=8)
    file_table.add_column("File", style="green")
    file_table.add_column("Status", style="yellow")
    file_table.add_column("Violations", style="red")

    for file_result in result.files:
        status_color = {
            "unchanged": "green",
            "changed": "yellow",
            "error": "red",
        }.get(file_result.status.value, "white")

        violation_count = file_result.lint_violation_count
        violation_str = str(violation_count) if violation_count > 0 else "-"

        file_table.add_row(
            mode_str,
            file_result.path,
            f"[{status_color}]{file_result.status.value.upper()}[/{status_color}]",
            violation_str,
        )

    console.print(file_table)

    # Show diff if requested
    if diff:
        for file_result in result.files:
            if file_result.changes:
                console.print(f"\n[bold]Diff: {file_result.path}[/bold]")
                for line in file_result.changes:
                    if line.startswith("+") and not line.startswith("+++"):
                        console.print(f"[green]{line}[/green]", end="")
                    elif line.startswith("-") and not line.startswith("---"):
                        console.print(f"[red]{line}[/red]", end="")
                    else:
                        console.print(line, end="")

    # Show lint violations if any
    if lint:
        total_violations = result.total_lint_violations
        if total_violations > 0:
            console.print(f"\n[bold]Lint Violations ({total_violations}):[/bold]")
            for file_result in result.files:
                for violation in file_result.lint_violations:
                    console.print(
                        f"  {file_result.path}:{violation.line}:{violation.column}  "
                        f"[yellow]{violation.rule}[/yellow]  {violation.description}"
                    )

    # Summary
    console.print()
    if result.has_errors:
        print_error(f"Format failed for {result.error_count} file(s)")
        raise typer.Exit(2)
    if result.has_changes:
        if check:
            print_warning(f"{result.changed_count} file(s) would be changed")
            console.print("Run without --check to apply changes.")
            raise typer.Exit(1)
        print_success(f"{result.changed_count} file(s) formatted")
    else:
        print_success("All files already formatted")



@metric_app.command("transpile")
def transpile_metric(
    name: Annotated[str, typer.Argument(help="Metric name to transpile.")],
    file: Annotated[
        Path | None,
        typer.Option(
            "--file",
            "-f",
            help="Path to SQL file to transpile (overrides metric's SQL).",
        ),
    ] = None,
    strict: Annotated[
        bool,
        typer.Option(
            "--strict",
            help="Fail on any error (default: graceful degradation with warnings).",
        ),
    ] = False,
    format_output: Annotated[
        ListOutputFormat,
        typer.Option(
            "--format",
            help="Output format (table or json).",
        ),
    ] = "table",
    show_rules: Annotated[
        bool,
        typer.Option(
            "--show-rules",
            help="Show detailed information about applied rules.",
        ),
    ] = False,
    validate: Annotated[
        bool,
        typer.Option(
            "--validate",
            help="Perform SQL syntax validation.",
        ),
    ] = False,
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            "-d",
            help="Input SQL dialect (trino, bigquery).",
        ),
    ] = "trino",
    retry: Annotated[
        int,
        typer.Option(
            "--transpile-retry",
            help="Number of retries for rule fetching (0-5).",
            min=0,
            max=5,
        ),
    ] = 1,
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Transpile metric SQL with table substitution and METRIC expansion.

    This command performs SQL transpilation on a metric's SQL file including:
    - Table substitution based on server-defined rules
    - METRIC() function expansion to SQL expressions
    - SQL pattern analysis and warnings

    The SQL can be taken from the metric's SQL file or from a custom file via --file.

    Examples:
        dli metric transpile iceberg.reporting.user_summary
        dli metric transpile iceberg.reporting.user_summary --file custom.sql
        dli metric transpile iceberg.reporting.user_summary --strict
        dli metric transpile iceberg.reporting.user_summary --show-rules
        dli metric transpile iceberg.reporting.user_summary --validate
        dli metric transpile iceberg.reporting.user_summary --dialect bigquery
        dli metric transpile iceberg.reporting.user_summary --transpile-retry 3
    """
    from dli.api.transpile import TranspileAPI
    from dli.models.common import ExecutionContext

    project_path = get_project_path(path)

    # Get the SQL to transpile
    if file is not None:
        # Read from custom file
        if not file.exists():
            print_error(f"File not found: {file}")
            raise typer.Exit(1)
        try:
            sql = file.read_text(encoding="utf-8").strip()
        except OSError as e:
            print_error(f"Failed to read file: {e}")
            raise typer.Exit(1) from e
    else:
        # Read from metric's SQL file
        try:
            from dli.commands.base import load_metric_service

            service = load_metric_service(project_path)
            metric = service.get_metric(name)
            if not metric:
                print_error(f"Metric '{name}' not found locally")
                raise typer.Exit(1)

            # Construct SQL file path from query_file and base_dir
            if not metric.query_file:
                print_error(f"Metric '{name}' has no query_file specified")
                raise typer.Exit(1)
            
            sql_path = metric.base_dir / metric.query_file if metric.base_dir else Path(metric.query_file)
            if not sql_path.exists():
                print_error(f"SQL file not found for metric '{name}': {sql_path}")
                raise typer.Exit(1)

            sql = sql_path.read_text(encoding="utf-8").strip()
        except Exception as e:
            print_error(f"Failed to load metric: {e}")
            raise typer.Exit(1) from e

    if not sql or not sql.strip():
        print_error("SQL cannot be empty.")
        raise typer.Exit(1)

    # Create API context
    try:
        ctx = ExecutionContext(project_path=project_path)
        api = TranspileAPI(context=ctx)

        # Perform transpilation
        with console.status("[bold green]Transpiling SQL..."):
            result = api.transpile(
                sql,
                source_dialect=dialect,
                target_dialect=dialect,
                strict=strict,
            )

    except Exception as e:
        print_error(f"Transpilation failed: {e}")
        raise typer.Exit(1) from e

    # Handle strict mode failures
    if not result.success and strict:
        print_error("Transpilation failed in strict mode.")
        raise typer.Exit(1)

    # Display result
    if format_output == "json":
        console.print_json(result.model_dump_json())
    else:
        # Table output
        console.print("\n[bold cyan]Transpile Result[/bold cyan]")
        console.print(f"[dim]Metric:[/dim] {name}")
        console.print(f"[dim]Dialect:[/dim] {dialect}")
        console.print(
            f"[dim]Status:[/dim] {'[green]Success[/green]' if result.success else '[red]Failed[/red]'}"
        )
        console.print(f"[dim]Duration:[/dim] {result.duration_ms}ms")
        console.print(f"[dim]Applied Rules:[/dim] {len(result.applied_rules)}")
        console.print(
            f"[dim]Warnings:[/dim] [{'yellow' if result.warnings else 'dim'}]{len(result.warnings)}[/{'yellow' if result.warnings else 'dim'}]"
        )

        # Show original SQL
        console.print("\n[bold]Original SQL:[/bold]")
        from rich.syntax import Syntax

        original_syntax = Syntax(sql, "sql", theme="monokai", line_numbers=True)
        from rich.panel import Panel

        console.print(Panel(original_syntax, border_style="dim"))

        # Show transpiled SQL
        if result.transpiled_sql != sql:
            console.print("[bold]Transpiled SQL:[/bold]")
            transpiled_syntax = Syntax(
                result.transpiled_sql, "sql", theme="monokai", line_numbers=True
            )
            console.print(Panel(transpiled_syntax, border_style="green"))
        else:
            console.print("[dim]No changes made to SQL.[/dim]")

        # Show applied rules if requested
        if show_rules and result.applied_rules:
            console.print("\n[bold]Applied Rules:[/bold]")
            rules_table = Table(show_header=True, header_style="bold cyan")
            rules_table.add_column("Source", style="red")
            rules_table.add_column("Target", style="green")
            rules_table.add_column("Priority", style="yellow")

            for rule in result.applied_rules:
                rules_table.add_row(
                    rule.source_table,
                    rule.target_table,
                    str(rule.priority),
                )

            console.print(rules_table)

        # Show warnings if present
        if result.warnings:
            console.print("\n[bold yellow]Warnings:[/bold yellow]")
            for warning in result.warnings:
                location = ""
                if warning.line:
                    location = f" (line {warning.line}"
                    if warning.column:
                        location += f", col {warning.column}"
                    location += ")"
                console.print(f"  [yellow]-[/yellow] {location} {warning.message}")

        console.print()

    # Show summary
    if format_output == "table":
        if result.success:
            if result.applied_rules:
                print_success(
                    f"Transpilation complete: {len(result.applied_rules)} rule(s) applied, "
                    f"{len(result.warnings)} warning(s)."
                )
            else:
                print_warning("No transpile rules were applied.")
        else:
            print_warning("Transpilation completed with errors (graceful degradation).")
