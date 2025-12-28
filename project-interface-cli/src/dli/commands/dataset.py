"""Dataset subcommand for DLI CLI.

Provides commands for managing and executing datasets.
Supports both local datasets and server-based operations.
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.table import Table
import typer

from dli.commands.base import (
    ListOutputFormat,
    SourceType,
    format_tags_display,
    get_client,
    get_project_path,
    load_dataset_service,
    spec_to_dict,
    spec_to_list_dict,
    spec_to_register_dict,
)
from dli.commands.utils import (
    console,
    parse_params,
    print_error,
    print_sql,
    print_success,
    print_validation_result,
    print_warning,
)

# Create dataset subcommand app
dataset_app = typer.Typer(
    name="dataset",
    help="Dataset management and execution commands.",
    no_args_is_help=True,
)


@dataset_app.command("list")
def list_datasets(
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
    """List datasets from local project or server.

    Examples:
        dli dataset list
        dli dataset list --source server
        dli dataset list --tag daily --format json
    """
    project_path = get_project_path(path)

    if source == "server":
        client = get_client(project_path)
        response = client.list_datasets(tag=tag, owner=owner, search=search)

        if not response.success:
            print_error(response.error or "Failed to list datasets from server")
            raise typer.Exit(1)

        datasets = response.data or []
    else:
        try:
            service = load_dataset_service(project_path)
            local_datasets = service.list_datasets(tag=tag, owner=owner)
            datasets = [spec_to_list_dict(d) for d in local_datasets]
        except Exception as e:
            print_error(f"Failed to list local datasets: {e}")
            raise typer.Exit(1)

    if not datasets:
        print_warning("No datasets found.")
        raise typer.Exit(0)

    if format_output == "json":
        console.print_json(json.dumps(datasets, default=str))
        return

    table = Table(
        title=f"Datasets ({len(datasets)}) - Source: {source}", show_header=True
    )
    table.add_column("Name", style="cyan", no_wrap=True)
    table.add_column("Owner", style="green")
    table.add_column("Team", style="yellow")
    table.add_column("Tags", style="magenta")

    for d in datasets:
        if isinstance(d, dict):
            tags = d.get("tags", [])
            table.add_row(
                d.get("name", ""),
                d.get("owner", "-"),
                d.get("team", "-"),
                format_tags_display(tags),
            )

    console.print(table)


@dataset_app.command("get")
def get_dataset(
    name: Annotated[str, typer.Argument(help="Dataset name.")],
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
    """Get dataset details.

    Examples:
        dli dataset get iceberg.analytics.daily_clicks
        dli dataset get iceberg.analytics.daily_clicks --source server
    """
    project_path = get_project_path(path)

    if source == "server":
        client = get_client(project_path)
        response = client.get_dataset(name)

        if not response.success:
            print_error(response.error or f"Dataset '{name}' not found on server")
            raise typer.Exit(1)

        if not isinstance(response.data, dict):
            print_error(f"Invalid response data for dataset '{name}'")
            raise typer.Exit(1)

        dataset_data: dict = response.data
    else:
        try:
            service = load_dataset_service(project_path)
            dataset = service.get_dataset(name)
            if not dataset:
                print_error(f"Dataset '{name}' not found locally")
                raise typer.Exit(1)

            dataset_data = spec_to_dict(dataset, include_parameters=True)
        except Exception as e:
            print_error(f"Failed to get dataset: {e}")
            raise typer.Exit(1)

    if format_output == "json":
        console.print_json(json.dumps(dataset_data, default=str))
        return

    console.print(f"\n[bold cyan]{dataset_data.get('name')}[/bold cyan]")
    console.print(f"[dim]Type:[/dim] {dataset_data.get('type', 'Dataset')}")
    console.print(f"[dim]Owner:[/dim] {dataset_data.get('owner', '-')}")
    console.print(f"[dim]Team:[/dim] {dataset_data.get('team', '-')}")
    console.print(f"[dim]Description:[/dim] {dataset_data.get('description', '-')}")
    console.print(f"[dim]Tags:[/dim] {', '.join(dataset_data.get('tags', [])) or '-'}")

    params = dataset_data.get("parameters", [])
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


@dataset_app.command("run")
def run_dataset(
    name: Annotated[str, typer.Argument(help="Dataset name to run.")],
    params: Annotated[
        list[str] | None,
        typer.Option("--param", "-p", help="Parameter in key=value format."),
    ] = None,
    dry_run: Annotated[
        bool,
        typer.Option("--dry-run", help="Only validate, don't execute."),
    ] = False,
    skip_pre: Annotated[
        bool,
        typer.Option("--skip-pre", help="Skip pre-statements."),
    ] = False,
    skip_post: Annotated[
        bool,
        typer.Option("--skip-post", help="Skip post-statements."),
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
    """Execute a dataset (DML operations).

    Examples:
        dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01
        dli dataset run iceberg.analytics.daily_clicks -p execution_date=2024-01-01 --dry-run
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
        service = load_dataset_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    dataset = service.get_dataset(name)
    if not dataset:
        print_error(f"Dataset '{name}' not found")
        raise typer.Exit(1)

    with console.status("[bold green]Executing dataset..."):
        result = service.execute(
            name,
            param_dict,
            dry_run=dry_run,
            skip_pre=skip_pre,
            skip_post=skip_post,
        )

    if not result.success:
        print_error(result.error_message or "Execution failed")
        raise typer.Exit(1)

    if dry_run:
        print_success("Dry-run completed (no execution)")
    else:
        print_success("Dataset executed successfully")
        if result.main_result:
            console.print(f"  [dim]Rows affected:[/dim] {result.main_result.row_count}")

        # Show execution summary
        if result.pre_results:
            console.print(
                f"  [dim]Pre-statements:[/dim] {len(result.pre_results)} executed"
            )
        if result.post_results:
            console.print(
                f"  [dim]Post-statements:[/dim] {len(result.post_results)} executed"
            )

    if show_sql and result.main_result and result.main_result.rendered_sql:
        console.print()
        print_sql(result.main_result.rendered_sql)


@dataset_app.command("validate")
def validate_dataset(
    name: Annotated[str, typer.Argument(help="Dataset name to validate.")],
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
    """Validate a dataset query.

    Examples:
        dli dataset validate iceberg.analytics.daily_clicks -p execution_date=2024-01-01
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
        service = load_dataset_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    dataset = service.get_dataset(name)
    if not dataset:
        print_error(f"Dataset '{name}' not found")
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
        rendered = service.render_sql(name, param_dict)
        if rendered and "main" in rendered:
            main_sql = rendered["main"]
            if isinstance(main_sql, list):
                main_sql = main_sql[0]
            console.print()
            print_sql(main_sql)

    if not all_valid:
        raise typer.Exit(1)


@dataset_app.command("register")
def register_dataset(
    name: Annotated[str, typer.Argument(help="Dataset name to register.")],
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    force: Annotated[
        bool,
        typer.Option("--force", "-f", help="Force overwrite if exists."),
    ] = False,
) -> None:
    """Register a local dataset to the server.

    Examples:
        dli dataset register iceberg.analytics.daily_clicks
        dli dataset register iceberg.analytics.daily_clicks --force
    """
    project_path = get_project_path(path)

    try:
        service = load_dataset_service(project_path)
    except Exception as e:
        print_error(f"Failed to initialize: {e}")
        raise typer.Exit(1)

    dataset = service.get_dataset(name)
    if not dataset:
        print_error(f"Dataset '{name}' not found locally")
        raise typer.Exit(1)

    client = get_client(project_path)

    if not force:
        existing = client.get_dataset(name)
        if existing.success:
            print_error(
                f"Dataset '{name}' already exists on server. Use --force to overwrite."
            )
            raise typer.Exit(1)

    spec_data = spec_to_register_dict(dataset)

    with console.status("[bold green]Registering dataset..."):
        response = client.register_dataset(spec_data)

    if response.success:
        print_success(f"Dataset '{name}' registered successfully")
    else:
        print_error(response.error or "Registration failed")
        raise typer.Exit(1)
