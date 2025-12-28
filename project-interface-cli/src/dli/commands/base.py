"""Base utilities for CLI commands.

This module provides shared utilities, type definitions, and helper functions
used across multiple CLI command modules (metric, dataset, etc.).

Goals:
- Eliminate code duplication between command modules
- Provide type-safe enums and Literal types for CLI options
- Centralize common patterns like project path resolution and client creation
"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING, Literal

if TYPE_CHECKING:
    from dli.core.client import BasecampClient


# Type definitions for CLI options
SourceType = Literal["local", "server"]
"""Valid source options for commands that support local/server data."""

OutputFormat = Literal["table", "json", "csv"]
"""Valid output format options."""

ListOutputFormat = Literal["table", "json"]
"""Valid output format options for list commands (no csv)."""


# Constants
MAX_TAGS_DISPLAY = 3
"""Maximum number of tags to display in table output before truncating."""


def get_project_path(path: Path | None) -> Path:
    """Get project path by searching for dli.yaml configuration.

    Searches from the given path (or current directory if None) upwards
    through parent directories until a dli.yaml file is found.

    Args:
        path: Optional starting path. Uses current directory if None.

    Returns:
        Path to the project directory containing dli.yaml,
        or the search path if no config file is found.

    Examples:
        >>> get_project_path(None)  # Searches from cwd
        PosixPath('/home/user/my-dli-project')
        >>> get_project_path(Path('/home/user/my-dli-project/metrics'))
        PosixPath('/home/user/my-dli-project')
    """
    search_path = path or Path.cwd()

    # Check the given path first
    config_path = search_path / "dli.yaml"
    if config_path.exists():
        return search_path

    # Search parent directories
    for parent in search_path.parents:
        if (parent / "dli.yaml").exists():
            return parent

    # Return original path if no config found
    return search_path


def get_client(project_path: Path, mock_mode: bool = True) -> BasecampClient:
    """Create a Basecamp client using project configuration.

    Loads server connection settings from the project's dli.yaml config
    and creates a client instance. Falls back to default settings if
    config file is not found.

    Args:
        project_path: Path to the project directory containing dli.yaml.
        mock_mode: If True, client will use mock responses instead of
            making actual HTTP requests. Defaults to True for safety.

    Returns:
        Configured BasecampClient instance.

    Examples:
        >>> client = get_client(Path('/my-project'))
        >>> client.health_check()
        ServerResponse(success=True, ...)
    """
    from dli.core.client import create_client
    from dli.core.config import load_project

    try:
        config = load_project(project_path)
        return create_client(
            url=config.server_url,
            timeout=config.server_timeout,
            api_key=config.server_api_key,
            mock_mode=mock_mode,
        )
    except FileNotFoundError:
        return create_client(mock_mode=mock_mode)


def load_metric_service(project_path: Path):
    """Load MetricService for the given project.

    Args:
        project_path: Path to the project directory.

    Returns:
        Initialized MetricService instance.

    Note:
        Import is done inside the function to avoid circular imports
        and to defer loading until actually needed.
    """
    from dli.core import MetricService

    return MetricService(project_path=project_path)


def load_dataset_service(project_path: Path):
    """Load DatasetService for the given project.

    Args:
        project_path: Path to the project directory.

    Returns:
        Initialized DatasetService instance.

    Note:
        Import is done inside the function to avoid circular imports
        and to defer loading until actually needed.
    """
    from dli.core import DatasetService

    return DatasetService(project_path=project_path)


def format_tags_display(tags: list[str], max_display: int = MAX_TAGS_DISPLAY) -> str:
    """Format a list of tags for display, truncating if necessary.

    Args:
        tags: List of tag strings.
        max_display: Maximum number of tags to show before truncating.

    Returns:
        Comma-separated string of tags, with "..." appended if truncated,
        or "-" if the list is empty.

    Examples:
        >>> format_tags_display(["a", "b"])
        'a, b'
        >>> format_tags_display(["a", "b", "c", "d"])
        'a, b, c...'
        >>> format_tags_display([])
        '-'
    """
    if not tags:
        return "-"

    display = ", ".join(tags[:max_display])
    if len(tags) > max_display:
        display += "..."

    return display


def spec_to_dict(spec, include_parameters: bool = False) -> dict:
    """Convert a spec object (Metric/Dataset) to a dictionary for display.

    Args:
        spec: A Metric or Dataset spec object with standard attributes.
        include_parameters: If True, include parameter details.

    Returns:
        Dictionary with spec details suitable for JSON output or display.
    """
    result = {
        "name": spec.name,
        "type": spec.type.value,
        "owner": spec.owner,
        "team": spec.team,
        "description": spec.description or "",
        "tags": spec.tags,
        "domains": spec.domains,
    }

    if include_parameters and hasattr(spec, "parameters"):
        result["parameters"] = [
            {
                "name": p.name,
                "type": p.type.value,
                "required": p.required,
                "default": p.default,
                "description": p.description,
            }
            for p in spec.parameters
        ]

    return result


def spec_to_list_dict(spec) -> dict:
    """Convert a spec object to a dictionary for list display.

    This is a lighter version of spec_to_dict, containing only
    the fields shown in list commands.

    Args:
        spec: A Metric or Dataset spec object.

    Returns:
        Dictionary with minimal spec details for list output.
    """
    return {
        "name": spec.name,
        "type": spec.type.value,
        "owner": spec.owner,
        "team": spec.team,
        "description": spec.description or "",
        "tags": spec.tags,
    }


def spec_to_register_dict(spec) -> dict:
    """Convert a spec object to a dictionary for server registration.

    Args:
        spec: A Metric or Dataset spec object.

    Returns:
        Dictionary with spec data for registration API call.
    """
    return {
        "name": spec.name,
        "type": spec.type.value,
        "owner": spec.owner,
        "team": spec.team,
        "description": spec.description or "",
        "tags": spec.tags,
        "domains": spec.domains,
    }
