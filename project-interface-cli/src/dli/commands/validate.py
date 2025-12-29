"""Validate command for DLI CLI.

Provides LOCAL ONLY validation capabilities:
- SQL syntax validation (SQLGlot, dialect-aware)
- YAML Spec validation (Pydantic schemas)
- Project-wide validation (--all flag)
- Local dependency checking (--check-deps flag)
- Strict mode (--strict flag, warnings become errors)

Examples:
    dli validate query.sql --dialect trino
    dli validate spec.iceberg.analytics.daily_clicks.yaml
    dli validate iceberg.analytics.daily_clicks --var execution_date=2025-01-01
    dli validate --all
    dli validate --all --strict
    dli validate --all --type metric
    dli validate --all --check-deps
"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING, Annotated, Literal

from rich.console import Console
import typer

if TYPE_CHECKING:
    from dli.core.validation.dep_validator import ProjectDepSummary
    from dli.core.validation.spec_validator import ValidationSummary

console = Console()
error_console = Console(stderr=True)


def _parse_variables(var_list: list[str] | None) -> dict[str, str]:
    """Parse variable arguments into a dictionary.

    Args:
        var_list: List of "key=value" strings

    Returns:
        Dictionary of variable name to value
    """
    if not var_list:
        return {}

    variables: dict[str, str] = {}
    for var in var_list:
        if "=" not in var:
            error_console.print(f"[yellow]Warning:[/yellow] Invalid variable format: {var} (expected key=value)")
            continue
        key, value = var.split("=", 1)
        variables[key.strip()] = value.strip()

    return variables


def _display_validation_summary(
    summary: ValidationSummary,
    check_deps: bool,
    dep_summary: ProjectDepSummary | None = None,
) -> None:
    """Display validation summary with Rich formatting.

    Args:
        summary: ValidationSummary from SpecValidator
        check_deps: Whether dependency checking was enabled
        dep_summary: Optional ProjectDepSummary from DepValidator
    """
    # Group results by type
    metric_results = [r for r in summary.results if r.spec_type == "metric"]
    dataset_results = [r for r in summary.results if r.spec_type == "dataset"]

    # Display metrics section
    if metric_results:
        console.print(f"\n[bold]Metrics ({len(metric_results)} files)[/bold]")
        for result in metric_results:
            _display_result(result)

    # Display datasets section
    if dataset_results:
        console.print(f"\n[bold]Datasets ({len(dataset_results)} files)[/bold]")
        for result in dataset_results:
            _display_result(result)

    # Display dependency summary if enabled
    if check_deps and dep_summary and dep_summary.missing_dependencies > 0:
        console.print(f"\n[bold]Dependency Issues ({dep_summary.specs_with_missing_deps} specs)[/bold]")
        for result in dep_summary.failed_results:
            console.print(f"  [red]![/red] {result.spec_name}")
            for error in result.errors:
                console.print(f"      [red]{error}[/red]")

    # Display summary line
    console.print("\n" + "=" * 60)

    status_parts = []
    if summary.passed > 0:
        status_parts.append(f"[green]{summary.passed} passed[/green]")
    if summary.failed > 0:
        status_parts.append(f"[red]{summary.failed} failed[/red]")
    if summary.warnings > 0:
        status_parts.append(f"[yellow]{summary.warnings} warnings[/yellow]")

    if check_deps and dep_summary and dep_summary.missing_dependencies > 0:
        status_parts.append(f"[red]{dep_summary.missing_dependencies} missing deps[/red]")

    console.print(f"Summary: {', '.join(status_parts)}")


def _display_result(result) -> None:
    """Display a single validation result.

    Args:
        result: SpecValidationResult
    """
    name = result.spec_name or result.spec_path.name

    if result.is_valid:
        if result.has_warnings:
            console.print(f"  [yellow]![/yellow] {name}")
            for warning in result.warnings:
                console.print(f"      [yellow]{warning}[/yellow]")
        else:
            console.print(f"  [green]v[/green] {name}")
    else:
        console.print(f"  [red]x[/red] {name}")
        for error in result.errors:
            console.print(f"      [red]{error}[/red]")


def validate(
    path: Annotated[
        Path | None,
        typer.Argument(
            help="Path to SQL/spec file, or resource name (catalog.schema.table). "
            "Omit for project-wide validation with --all.",
        ),
    ] = None,
    dialect: Annotated[
        str,
        typer.Option(
            "--dialect",
            "-d",
            help="SQL dialect for validation (e.g., trino, bigquery, postgres).",
        ),
    ] = "trino",
    strict: Annotated[
        bool,
        typer.Option(
            "--strict",
            "-s",
            help="Enable strict validation mode (fail on warnings).",
        ),
    ] = False,
    all_specs: Annotated[
        bool,
        typer.Option(
            "--all",
            "-a",
            help="Validate all specs in the project.",
        ),
    ] = False,
    spec_type: Annotated[
        str | None,
        typer.Option(
            "--type",
            "-t",
            help="Filter by spec type: metric, dataset, or all.",
        ),
    ] = None,
    check_deps: Annotated[
        bool,
        typer.Option(
            "--check-deps",
            help="Check that depends_on references exist locally.",
        ),
    ] = False,
    var: Annotated[
        list[str] | None,
        typer.Option(
            "--var",
            "-v",
            help="Variable substitution in format key=value. Can be repeated.",
        ),
    ] = None,
    project: Annotated[
        Path | None,
        typer.Option(
            "--project",
            "-p",
            help="Project directory path. Defaults to current directory.",
        ),
    ] = None,
) -> None:
    """Validate SQL files, spec files, or entire project (LOCAL ONLY).

    This command performs local-only validation without server interaction.

    Validation includes:
    - YAML schema validation using Pydantic
    - SQL syntax validation using SQLGlot
    - Local dependency checking (with --check-deps)

    \b
    Examples:
        # Validate a SQL file
        dli validate query.sql --dialect trino

        # Validate a spec file
        dli validate spec.iceberg.analytics.daily_clicks.yaml

        # Validate by resource name
        dli validate iceberg.analytics.daily_clicks

        # Validate with variable substitution
        dli validate iceberg.analytics.daily_clicks --var execution_date=2025-01-01

        # Validate all specs in project
        dli validate --all

        # Validate with strict mode (warnings are errors)
        dli validate --all --strict

        # Validate only metrics
        dli validate --all --type metric

        # Validate with dependency checking
        dli validate --all --check-deps
    """
    from dli.commands.base import get_project_path  # noqa: PLC0415

    # Parse variables
    variables = _parse_variables(var)

    # Determine project path
    project_path = get_project_path(project)

    # Validate spec_type parameter
    validated_spec_type: Literal["metric", "dataset", "all"] = "all"
    if spec_type:
        spec_type_lower = spec_type.lower()
        if spec_type_lower not in ("metric", "dataset", "all"):
            error_console.print(f"[red]Invalid --type value:[/red] {spec_type}")
            error_console.print("Valid options: metric, dataset, all")
            raise typer.Exit(1)
        validated_spec_type = spec_type_lower  # type: ignore[assignment]

    # Project-wide validation
    if all_specs:
        _validate_project(
            project_path=project_path,
            dialect=dialect,
            strict=strict,
            spec_type=validated_spec_type,
            check_deps=check_deps,
            variables=variables,
        )
        return

    # Single file/resource validation
    if path is None:
        error_console.print("[red]Error:[/red] Either provide a path/name or use --all flag")
        error_console.print("\nExamples:")
        error_console.print("  dli validate query.sql")
        error_console.print("  dli validate iceberg.analytics.daily_clicks")
        error_console.print("  dli validate --all")
        raise typer.Exit(1)

    # Check if path is a file or a resource name
    if path.exists():
        _validate_file(
            path=path,
            dialect=dialect,
            strict=strict,
            check_deps=check_deps,
            variables=variables,
            project_path=project_path,
        )
    elif "." in str(path) and not str(path).endswith((".sql", ".yaml", ".yml")):
        # Looks like a resource name (catalog.schema.table)
        _validate_resource(
            resource_name=str(path),
            project_path=project_path,
            dialect=dialect,
            strict=strict,
            check_deps=check_deps,
            variables=variables,
        )
    else:
        error_console.print(f"[red]Error:[/red] File not found: {path}")
        raise typer.Exit(1)


def _validate_file(
    path: Path,
    dialect: str,
    strict: bool,
    check_deps: bool,
    variables: dict[str, str],
    project_path: Path,
) -> None:
    """Validate a single file (SQL or YAML spec).

    Args:
        path: Path to the file
        dialect: SQL dialect
        strict: Strict mode flag
        check_deps: Check dependencies flag
        variables: Variable substitutions
        project_path: Project path for dependency checking
    """
    from dli.core.validation import SpecValidator  # noqa: PLC0415
    from dli.core.validator import SQLValidator  # noqa: PLC0415

    # Handle YAML spec files
    if path.suffix in (".yaml", ".yml"):
        validator = SpecValidator(dialect=dialect, strict=strict)
        result = validator.validate_file(path, variables=variables)

        if result.is_valid:
            console.print(f"[green]Valid spec:[/green] {result.spec_name or path.name}")
            if result.warnings:
                console.print(f"[yellow]Warnings ({len(result.warnings)}):[/yellow]")
                for warning in result.warnings:
                    console.print(f"  - {warning}")

            # Check dependencies if requested
            if check_deps and result.spec_name:
                _check_dependencies_for_file(path, project_path)
        else:
            error_console.print(f"[red]Invalid spec:[/red] {path.name}")
            for error in result.errors:
                error_console.print(f"  - {error}")
            raise typer.Exit(1)
        return

    # Handle SQL files
    if path.suffix == ".sql":
        try:
            content = path.read_text(encoding="utf-8")
        except Exception as e:
            error_console.print(f"[red]Error reading file:[/red] {e}")
            raise typer.Exit(1)

        validator = SQLValidator(dialect=dialect)
        result = validator.validate(content)

        if result.is_valid:
            console.print(f"[green]Valid SQL:[/green] {path}")
            if result.warnings:
                console.print(f"[yellow]Warnings ({len(result.warnings)}):[/yellow]")
                for warning in result.warnings:
                    console.print(f"  - {warning}")
                if strict:
                    error_console.print("[red]Strict mode: failing due to warnings[/red]")
                    raise typer.Exit(1)
        else:
            error_console.print(f"[red]Invalid SQL:[/red] {path}")
            for error in result.errors:
                error_console.print(f"  - {error}")
            raise typer.Exit(1)
        return

    # Unknown file type
    error_console.print(f"[yellow]Unknown file type:[/yellow] {path.suffix}")
    error_console.print("Supported: .sql, .yaml, .yml")
    raise typer.Exit(1)


def _validate_resource(
    resource_name: str,
    project_path: Path,
    dialect: str,
    strict: bool,
    check_deps: bool,
    variables: dict[str, str],
) -> None:
    """Validate a resource by its fully qualified name.

    Args:
        resource_name: Fully qualified name (catalog.schema.table)
        project_path: Project directory path
        dialect: SQL dialect
        strict: Strict mode flag
        check_deps: Check dependencies flag
        variables: Variable substitutions
    """
    from dli.core.validation import SpecValidator  # noqa: PLC0415

    console.print(f"Validating resource: {resource_name}")

    validator = SpecValidator(dialect=dialect, strict=strict)
    result = validator.validate_by_name(resource_name, project_path, variables=variables)

    if result.is_valid:
        console.print(f"[green]Valid:[/green] {result.spec_name}")
        if result.warnings:
            console.print(f"[yellow]Warnings ({len(result.warnings)}):[/yellow]")
            for warning in result.warnings:
                console.print(f"  - {warning}")

        # Check dependencies if requested
        if check_deps:
            _check_dependencies_for_resource(resource_name, project_path)
    else:
        error_console.print(f"[red]Invalid:[/red] {resource_name}")
        for error in result.errors:
            error_console.print(f"  - {error}")
        raise typer.Exit(1)


def _validate_project(
    project_path: Path,
    dialect: str,
    strict: bool,
    spec_type: Literal["metric", "dataset", "all"],
    check_deps: bool,
    variables: dict[str, str],
) -> None:
    """Validate all specs in a project.

    Args:
        project_path: Project directory path
        dialect: SQL dialect
        strict: Strict mode flag
        spec_type: Type filter
        check_deps: Check dependencies flag
        variables: Variable substitutions
    """
    from dli.core.config import load_project  # noqa: PLC0415
    from dli.core.validation import DepValidator, SpecValidator  # noqa: PLC0415

    console.print(f"Validating project: [bold]{project_path.name}[/bold] (local only)")
    console.print("=" * 60)

    # Validate specs
    validator = SpecValidator(dialect=dialect, strict=strict)
    summary = validator.validate_all(
        project_path,
        spec_type=spec_type,
        variables=variables,
    )

    # Validate dependencies if requested
    dep_summary = None
    if check_deps:
        try:
            config = load_project(project_path)
            dep_validator = DepValidator.from_project(project_path)
            dep_summary = dep_validator.validate_all(config)
        except FileNotFoundError as e:
            error_console.print(f"[yellow]Warning:[/yellow] Could not check dependencies: {e}")

    # Display results
    _display_validation_summary(summary, check_deps, dep_summary)

    # Exit with error if any validation failed
    has_failures = summary.failed > 0
    has_dep_failures = check_deps and dep_summary and not dep_summary.all_valid

    if has_failures or has_dep_failures:
        raise typer.Exit(1)


def _check_dependencies_for_file(path: Path, project_path: Path) -> None:
    """Check dependencies for a spec file.

    Args:
        path: Path to the spec file
        project_path: Project directory path
    """
    from dli.core.config import load_project  # noqa: PLC0415
    from dli.core.discovery import SpecDiscovery  # noqa: PLC0415
    from dli.core.validation import DepValidator  # noqa: PLC0415

    try:
        config = load_project(project_path)
        discovery = SpecDiscovery(config)

        # Load spec from file
        spec = None
        for s in discovery.discover_all():
            if s.spec_path == path:
                spec = s
                break

        if spec is None:
            console.print("[yellow]Warning:[/yellow] Could not load spec for dependency check")
            return

        dep_validator = DepValidator.from_project(project_path)
        result = dep_validator.validate(spec)

        if not result.is_valid:
            console.print("[yellow]Dependency issues:[/yellow]")
            for error in result.errors:
                console.print(f"  - {error}")
        elif result.found_dependencies:
            console.print(f"[green]Dependencies OK:[/green] {len(result.found_dependencies)} found")

    except FileNotFoundError as e:
        console.print(f"[yellow]Warning:[/yellow] Could not check dependencies: {e}")


def _check_dependencies_for_resource(resource_name: str, project_path: Path) -> None:
    """Check dependencies for a resource by name.

    Args:
        resource_name: Fully qualified resource name
        project_path: Project directory path
    """
    from dli.core.config import load_project  # noqa: PLC0415
    from dli.core.discovery import SpecDiscovery  # noqa: PLC0415
    from dli.core.validation import DepValidator  # noqa: PLC0415

    try:
        config = load_project(project_path)
        discovery = SpecDiscovery(config)
        spec = discovery.find_spec(resource_name)

        if spec is None:
            console.print("[yellow]Warning:[/yellow] Could not find spec for dependency check")
            return

        dep_validator = DepValidator.from_project(project_path)
        result = dep_validator.validate(spec)

        if not result.is_valid:
            console.print("[yellow]Dependency issues:[/yellow]")
            for error in result.errors:
                console.print(f"  - {error}")
        elif result.found_dependencies:
            console.print(f"[green]Dependencies OK:[/green] {len(result.found_dependencies)} found")

    except FileNotFoundError as e:
        console.print(f"[yellow]Warning:[/yellow] Could not check dependencies: {e}")
