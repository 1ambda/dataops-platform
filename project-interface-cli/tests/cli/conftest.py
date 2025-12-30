"""Shared fixtures and helpers for CLI tests."""

from __future__ import annotations

from click.testing import Result
import pytest
from typer.testing import CliRunner

# Shared CliRunner instance
runner = CliRunner()


def get_output(result: Result) -> str:
    """Get combined output from CLI result.

    Typer's CliRunner mixes stdout/stderr by default, so we check
    multiple attributes to get the complete output.

    Args:
        result: CliRunner invoke result.

    Returns:
        Combined output string from the CLI result.
    """
    return result.output or result.stdout or ""


@pytest.fixture
def cli_runner() -> CliRunner:
    """Provide CliRunner for CLI tests.

    Returns:
        Shared CliRunner instance.
    """
    return runner
