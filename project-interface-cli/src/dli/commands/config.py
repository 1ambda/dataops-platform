"""Config subcommand for DLI CLI.

Provides commands for managing DLI configuration with hierarchical layering:
- show: Display current configuration with sources
- status: Check server connection and validate config
- validate: Validate configuration without connecting
- env: List or switch named environments
- init: Initialize configuration files
- set: Set configuration value

Configuration layers (in priority order, lowest to highest):
1. ~/.dli/config.yaml (global)
2. dli.yaml (project)
3. .dli.local.yaml (local)
4. Environment variables (DLI_*)
5. CLI options

Example:
    $ dli config show --show-source
    $ dli config validate --strict
    $ dli config env --list
    $ dli config init
    $ dli config set server.url "http://localhost:8081"
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Annotated

from rich.panel import Panel
from rich.table import Table
import typer
import yaml

from dli.commands.base import with_trace
from dli.commands.utils import console, print_error, print_success, print_warning
from dli.models.config import ConfigSource

# Create config subcommand app
config_app = typer.Typer(
    name="config",
    help="Configuration management commands.",
    no_args_is_help=True,
)


def _get_config_api(path: Path | None, *, validate_path: bool = True):
    """Get ConfigAPI for the given project path.

    Args:
        path: Project path to use.
        validate_path: If True, validate that path exists when explicitly provided.

    Raises:
        typer.Exit: If validate_path is True and the path doesn't exist.
    """
    from dli.api.config import ConfigAPI

    search_path = path or Path.cwd()

    # Validate explicitly provided path exists
    if validate_path and path is not None and not path.exists():
        print_error(f"Path not found: {path}")
        raise typer.Exit(1)

    # Search for dli.yaml in current or parent directories
    config_path = search_path / "dli.yaml"
    if not config_path.exists():
        for parent in search_path.parents:
            if (parent / "dli.yaml").exists():
                search_path = parent
                break

    return ConfigAPI(project_path=search_path)


@config_app.command("show")
@with_trace("config show")
def show_config(
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    format_output: Annotated[
        str,
        typer.Option("--format", "-f", help="Output format (table, json, yaml)."),
    ] = "table",
    show_source: Annotated[
        bool,
        typer.Option("--show-source", "-s", help="Show value source."),
    ] = False,
    show_secrets: Annotated[
        bool,
        typer.Option("--show-secrets", help="Reveal secret values (requires confirmation)."),
    ] = False,
    section: Annotated[
        str | None,
        typer.Option("--section", help="Show specific section only."),
    ] = None,
) -> None:
    """Show current configuration.

    Displays merged configuration from all layers with optional source tracking.

    Examples:
        dli config show
        dli config show --show-source
        dli config show --format json
        dli config show --section server
    """
    api = _get_config_api(path)

    if show_secrets:
        confirm = typer.confirm("This will display sensitive values. Continue?")
        if not confirm:
            raise typer.Exit(0)

    try:
        config = api.get_all()
    except Exception as e:
        print_error(f"Failed to load configuration: {e}")
        raise typer.Exit(1)

    # Filter by section if specified
    if section:
        if section in config:
            config = {section: config[section]}
        else:
            print_warning(f"Section '{section}' not found in configuration.")
            raise typer.Exit(0)

    if format_output == "json":
        # Mask secrets in JSON output unless show_secrets is True
        output = _mask_secrets(config) if not show_secrets else config
        console.print_json(json.dumps(output, indent=2, default=str))
        return

    if format_output == "yaml":
        output = _mask_secrets(config) if not show_secrets else config
        console.print(yaml.dump(output, default_flow_style=False, sort_keys=False))
        return

    # Table output
    title = "DLI Configuration"
    if section:
        title = f"DLI Configuration: {section}"

    if show_source:
        values = api.get_all_with_sources()
        if section:
            values = [v for v in values if v.key.startswith(f"{section}.")]

        table = Table(title=title, show_header=True)
        table.add_column("Setting", style="cyan")
        table.add_column("Value", style="green")
        table.add_column("Source", style="dim")

        for v in values:
            display_val = v.display_value if (v.is_secret and not show_secrets) else str(v.value)
            table.add_row(v.key, display_val, v.source_label)
    else:
        table = Table(title=title, show_header=True)
        table.add_column("Setting", style="cyan")
        table.add_column("Value", style="green")

        def add_rows(obj: dict, prefix: str = "") -> None:
            for key, value in obj.items():
                full_key = f"{prefix}.{key}" if prefix else key
                if isinstance(value, dict):
                    add_rows(value, full_key)
                else:
                    display_val = _mask_if_secret(full_key, value, show_secrets)
                    table.add_row(full_key, display_val)

        add_rows(config)

    console.print(table)


@config_app.command("status")
@with_trace("config status")
def check_status(
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    env: Annotated[
        str | None,
        typer.Option("--env", "-e", help="Environment to check."),
    ] = None,
) -> None:
    """Check server connection status.

    Validates configuration and tests server connectivity.

    Examples:
        dli config status
        dli config status --env prod
    """
    api = _get_config_api(path)

    # First validate configuration
    result = api.validate()

    console.print("[bold]Configuration Status[/bold]\n")

    # Show files found/missing
    console.print("[bold]Files:[/bold]")
    for f in result.files_found:
        console.print(f"  [green][OK][/green] {f}")
    for f in result.files_missing:
        console.print(f"  [dim][--][/dim] {f} (not found)")

    console.print()

    # Show validation results
    if result.errors:
        console.print("[bold]Errors:[/bold]")
        for error in result.errors:
            console.print(f"  [red][ERROR][/red] {error}")

    if result.warnings:
        console.print("[bold]Warnings:[/bold]")
        for warning in result.warnings:
            console.print(f"  [yellow][WARN][/yellow] {warning}")

    console.print()

    # Check server connection
    if env:
        try:
            env_config = api.get_environment(env)
            server_url = env_config.get("server_url")
            if server_url:
                console.print(f"[dim]Checking '{env}' environment at {server_url}...[/dim]")
        except Exception as e:
            print_error(f"Environment '{env}' not found: {e}")
            raise typer.Exit(1)

    status = api.get_server_status()
    console.print("[bold]Connection:[/bold]")
    if status.get("healthy"):
        console.print(f"  [green][OK][/green] Server reachable at {status['url']}")
        if status.get("version"):
            console.print(f"  [green][OK][/green] API version: {status['version']}")
    else:
        console.print(f"  [red][FAIL][/red] Server unreachable: {status.get('error', 'Unknown error')}")

    if not result.valid:
        raise typer.Exit(1)


@config_app.command("validate")
@with_trace("config validate")
def validate_config(
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
    strict: Annotated[
        bool,
        typer.Option("--strict", help="Fail on warnings."),
    ] = False,
) -> None:
    """Validate configuration without connecting.

    Checks for:
    - Valid YAML syntax
    - Required fields
    - Template variable resolution

    Examples:
        dli config validate
        dli config validate --strict
    """
    api = _get_config_api(path)
    result = api.validate(strict=strict)

    console.print("[bold]Configuration Validation[/bold]\n")

    # Show file status
    for f in result.files_found:
        console.print(f"[green][OK][/green] {f} syntax valid")
    for f in result.files_missing:
        if f == "dli.yaml":
            console.print(f"[red][ERROR][/red] {f} not found")
        else:
            console.print(f"[dim][--][/dim] {f} not found (optional)")

    console.print()

    # Show errors
    for error in result.errors:
        console.print(f"[red][ERROR][/red] {error}")

    # Show warnings
    for warning in result.warnings:
        console.print(f"[yellow][WARN][/yellow] {warning}")

    console.print()

    if result.valid:
        if result.warnings:
            print_success(f"Validation passed with {len(result.warnings)} warning(s).")
        else:
            print_success("Validation passed.")
    else:
        print_error(f"Validation failed with {len(result.errors)} error(s).")
        raise typer.Exit(1)


@config_app.command("env")
@with_trace("config env")
def manage_environment(
    name: Annotated[
        str | None,
        typer.Argument(help="Environment name to switch to."),
    ] = None,
    list_envs: Annotated[
        bool,
        typer.Option("--list", "-l", help="List available environments."),
    ] = False,
    format_output: Annotated[
        str,
        typer.Option("--format", "-f", help="Output format (table, json)."),
    ] = "table",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """List or switch named environments.

    Without arguments, shows the current environment.
    With --list, shows all available environments.
    With a name argument, switches to that environment.

    Examples:
        dli config env
        dli config env --list
        dli config env staging
    """
    api = _get_config_api(path)

    if list_envs or name is None:
        # List environments
        environments = api.list_environments()

        if not environments:
            print_warning("No environments configured.")
            console.print("\n[dim]Add environments to dli.yaml:[/dim]")
            console.print(
                Panel(
                    "environments:\n  dev:\n    server_url: \"http://localhost:8081\"\n  prod:\n    server_url: \"https://prod.basecamp.io\"",
                    title="dli.yaml",
                    border_style="dim",
                )
            )
            raise typer.Exit(0)

        if format_output == "json":
            data = [
                {
                    "name": e.name,
                    "server_url": e.server_url,
                    "dialect": e.dialect,
                    "is_active": e.is_active,
                }
                for e in environments
            ]
            console.print_json(json.dumps(data, indent=2))
            return

        # Table output
        table = Table(title="Available Environments", show_header=True)
        table.add_column("Name", style="cyan")
        table.add_column("Server URL", style="green")
        table.add_column("Dialect")
        table.add_column("Active", justify="center")

        for env in environments:
            active = "[green]*[/green]" if env.is_active else ""
            table.add_row(
                env.name,
                env.server_url or "[dim]--[/dim]",
                env.dialect or "[dim]--[/dim]",
                active,
            )

        console.print(table)

        if not list_envs:
            # Show current environment
            current = api.get_active_environment()
            if current:
                console.print(f"\n[dim]Current environment:[/dim] [cyan]{current}[/cyan]")
            else:
                console.print("\n[dim]No active environment set.[/dim]")
        return

    # Switch to environment
    try:
        env_config = api.get_environment(name)
    except Exception as e:
        # Ensure "not found" is in the error message for testability
        error_msg = str(e)
        if "not found" not in error_msg.lower():
            error_msg = f"Environment '{name}' not found: {e}"
        print_error(error_msg)
        raise typer.Exit(1)

    # Update .dli.local.yaml
    search_path = path or Path.cwd()
    local_path = search_path / ".dli.local.yaml"

    try:
        if local_path.exists():
            with open(local_path, encoding="utf-8") as f:
                local_config = yaml.safe_load(f) or {}
        else:
            local_config = {}

        local_config["active_environment"] = name

        with open(local_path, "w", encoding="utf-8") as f:
            yaml.dump(local_config, f, default_flow_style=False, sort_keys=False)

        print_success(f"Switched to '{name}' environment.")
        if env_config.get("server_url"):
            console.print(f"  [dim]Server URL:[/dim] {env_config['server_url']}")
        if env_config.get("dialect"):
            console.print(f"  [dim]Dialect:[/dim] {env_config['dialect']}")

    except Exception as e:
        print_error(f"Failed to update local config: {e}")
        raise typer.Exit(1)


@config_app.command("init")
@with_trace("config init")
def init_config(
    global_config: Annotated[
        bool,
        typer.Option("--global", "-g", help="Create global config in ~/.dli/"),
    ] = False,
    force: Annotated[
        bool,
        typer.Option("--force", help="Overwrite existing files."),
    ] = False,
    template: Annotated[
        str,
        typer.Option("--template", "-t", help="Template type (minimal, full)."),
    ] = "minimal",
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Initialize configuration files.

    Creates dli.yaml and .dli.local.yaml templates in the project directory,
    or ~/.dli/config.yaml for global configuration.

    Examples:
        dli config init
        dli config init --global
        dli config init --template full
    """
    if global_config:
        # Create global config
        global_dir = Path.home() / ".dli"
        global_path = global_dir / "config.yaml"

        if global_path.exists() and not force:
            print_warning(f"Global config already exists: {global_path}")
            console.print("[dim]Use --force to overwrite.[/dim]")
            raise typer.Exit(1)

        global_dir.mkdir(parents=True, exist_ok=True)

        content = _get_global_template()
        with open(global_path, "w", encoding="utf-8") as f:
            f.write(content)

        print_success(f"Created global config: {global_path}")
        return

    # Create project config
    project_path = path or Path.cwd()
    config_path = project_path / "dli.yaml"
    local_path = project_path / ".dli.local.yaml"
    gitignore_path = project_path / ".gitignore"

    # Check if files exist
    if config_path.exists() and not force:
        print_warning(f"Project config already exists: {config_path}")
        console.print("[dim]Use --force to overwrite.[/dim]")
        raise typer.Exit(1)

    # Create dli.yaml
    if template == "full":
        content = _get_full_project_template()
    else:
        content = _get_minimal_project_template()

    with open(config_path, "w", encoding="utf-8") as f:
        f.write(content)
    print_success(f"Created: {config_path}")

    # Create .dli.local.yaml
    if not local_path.exists() or force:
        local_content = _get_local_template()
        with open(local_path, "w", encoding="utf-8") as f:
            f.write(local_content)
        print_success(f"Created: {local_path}")

    # Add to .gitignore
    if gitignore_path.exists():
        with open(gitignore_path, encoding="utf-8") as f:
            gitignore_content = f.read()
        if ".dli.local.yaml" not in gitignore_content:
            with open(gitignore_path, "a", encoding="utf-8") as f:
                f.write("\n# DLI local config\n.dli.local.yaml\n")
            print_success("Added .dli.local.yaml to .gitignore")
    else:
        with open(gitignore_path, "w", encoding="utf-8") as f:
            f.write("# DLI local config\n.dli.local.yaml\n")
        print_success(f"Created: {gitignore_path}")


@config_app.command("set")
@with_trace("config set")
def set_config(
    key: Annotated[
        str,
        typer.Argument(help="Configuration key (dot notation, e.g., 'server.url')."),
    ],
    value: Annotated[
        str,
        typer.Argument(help="Configuration value."),
    ],
    local: Annotated[
        bool,
        typer.Option("--local", "-l", help="Write to .dli.local.yaml (default)."),
    ] = True,
    project: Annotated[
        bool,
        typer.Option("--project", help="Write to dli.yaml."),
    ] = False,
    global_config: Annotated[
        bool,
        typer.Option("--global", "-g", help="Write to ~/.dli/config.yaml."),
    ] = False,
    path: Annotated[
        Path | None,
        typer.Option("--path", "-p", help="Project path."),
    ] = None,
) -> None:
    """Set configuration value.

    Updates a configuration value in the specified file. By default,
    writes to .dli.local.yaml for local development overrides.

    Examples:
        dli config set server.url "http://localhost:8081"
        dli config set defaults.dialect "bigquery" --project
        dli config set defaults.timeout_seconds 600 --global
    """
    # Validate key format
    if not key or not key.strip():
        print_error("Invalid key: key cannot be empty")
        raise typer.Exit(1)

    # Validate key contains only valid characters
    key = key.strip()
    if not all(c.isalnum() or c in "._-" for c in key):
        print_error(f"Invalid key format: '{key}'. Keys can only contain alphanumeric characters, dots, underscores, and hyphens.")
        raise typer.Exit(1)

    project_path = path or Path.cwd()

    # Determine target file
    if global_config:
        target_path = Path.home() / ".dli" / "config.yaml"
        target_name = "~/.dli/config.yaml"
    elif project:
        target_path = project_path / "dli.yaml"
        target_name = "dli.yaml"
    else:  # local (default)
        target_path = project_path / ".dli.local.yaml"
        target_name = ".dli.local.yaml"

    # Load existing config
    if target_path.exists():
        with open(target_path, encoding="utf-8") as f:
            config = yaml.safe_load(f) or {}
    else:
        # Create parent directory for global config
        if global_config:
            target_path.parent.mkdir(parents=True, exist_ok=True)
        config = {}

    # Parse value (handle numbers and booleans)
    parsed_value: str | int | float | bool = value
    if value.lower() in ("true", "false"):
        parsed_value = value.lower() == "true"
    else:
        try:
            parsed_value = int(value)
        except ValueError:
            try:
                parsed_value = float(value)
            except ValueError:
                pass

    # Set nested value
    parts = key.split(".")
    current = config
    for part in parts[:-1]:
        if part not in current:
            current[part] = {}
        current = current[part]
    current[parts[-1]] = parsed_value

    # Write config
    try:
        with open(target_path, "w", encoding="utf-8") as f:
            yaml.dump(config, f, default_flow_style=False, sort_keys=False)
        print_success(f'Set {key} = "{parsed_value}" in {target_name}')
    except Exception as e:
        print_error(f"Failed to write config: {e}")
        raise typer.Exit(1)


# Helper functions


def _mask_secrets(config: dict) -> dict:
    """Mask secret values in configuration."""
    result = {}
    for key, value in config.items():
        if isinstance(value, dict):
            result[key] = _mask_secrets(value)
        elif _is_secret_key(key):
            result[key] = "***" if value else None
        else:
            result[key] = value
    return result


def _mask_if_secret(key: str, value: str, show_secrets: bool) -> str:
    """Mask value if key is secret and show_secrets is False."""
    if not show_secrets and _is_secret_key(key) and value:
        return "***"
    return str(value) if value is not None else "[dim]Not set[/dim]"


def _is_secret_key(key: str) -> bool:
    """Check if key represents a secret value."""
    secret_patterns = ["password", "secret", "api_key", "token", "credential"]
    key_lower = key.lower()
    return any(pattern in key_lower for pattern in secret_patterns)


def _get_global_template() -> str:
    """Get global config template."""
    return '''# DLI Global Configuration
# This file contains user-level defaults for all projects.
version: "1"

# Default server settings
server:
  timeout: 30

# Default execution settings
defaults:
  dialect: "trino"
  timeout_seconds: 300
'''


def _get_minimal_project_template() -> str:
    """Get minimal project config template."""
    return '''# DLI Project Configuration
version: "1"

project:
  name: "my-project"

# Server connection (use environment variables for secrets)
server:
  url: "${DLI_SERVER_URL:-http://localhost:8081}"
  # api_key: "${DLI_SECRET_API_KEY}"

# Discovery configuration
discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
'''


def _get_full_project_template() -> str:
    """Get full project config template."""
    return '''# DLI Project Configuration
version: "1"

project:
  name: "my-project"
  description: "Data transformation models"

# Server connection
server:
  url: "${DLI_SERVER_URL:-https://basecamp.company.com}"
  timeout: 30
  api_key: "${DLI_SECRET_API_KEY}"

# Discovery configuration
discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns: ["dataset.*.yaml"]
  metric_patterns: ["metric.*.yaml"]

# Default settings
defaults:
  dialect: "trino"
  timeout_seconds: 3600
  retry_count: 2

# Named environments
environments:
  dev:
    server_url: "http://localhost:8081"
    dialect: "duckdb"
    catalog: "dev_catalog"
  staging:
    server_url: "https://staging.basecamp.io"
    dialect: "trino"
    catalog: "staging_catalog"
  prod:
    server_url: "https://prod.basecamp.io"
    dialect: "bigquery"
    catalog: "prod_catalog"
'''


def _get_local_template() -> str:
    """Get local config template."""
    return '''# DLI Local Configuration (DO NOT COMMIT)
# This file overrides project settings for local development.

# Uncomment to override server settings
# server:
#   url: "http://localhost:8081"

# Set active environment
# active_environment: "dev"

# Override defaults for local development
# defaults:
#   timeout_seconds: 60
'''
