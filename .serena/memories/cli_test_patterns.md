# DLI CLI Test Patterns (Quick Reference)

> 상세 내용: `project-interface-cli/docs/PATTERNS.md` → Section 4

## CLI Test Template

```python
"""Tests for {feature} CLI commands."""

import json
import pytest
from typer.testing import CliRunner

from dli.main import app

runner = CliRunner()


class Test{Feature}List:
    """Tests for `dli {feature} list` command."""

    def test_list_default(self) -> None:
        """Test listing with default options."""
        result = runner.invoke(app, ["{feature}", "list"])
        assert result.exit_code == 0
        assert "Items" in result.output or "{Feature}" in result.output

    def test_list_json_format(self) -> None:
        """Test listing with JSON output."""
        result = runner.invoke(app, ["{feature}", "list", "--format", "json"])
        assert result.exit_code == 0
        json.loads(result.output)  # Should be valid JSON

    def test_list_with_filter(self) -> None:
        """Test listing with filter option."""
        result = runner.invoke(app, ["{feature}", "list", "--status", "active"])
        assert result.exit_code == 0


class Test{Feature}Help:
    """Tests for help output."""

    def test_help_flag(self) -> None:
        result = runner.invoke(app, ["{feature}", "--help"])
        assert result.exit_code == 0
        assert "{feature}" in result.output.lower()


class Test{Feature}ErrorHandling:
    """Tests for error scenarios."""

    def test_not_found(self) -> None:
        result = runner.invoke(app, ["{feature}", "get", "nonexistent"])
        assert result.exit_code == 1
        assert "not found" in result.output.lower() or "error" in result.output.lower()
```

## Model Test Template

```python
"""Tests for {feature} models."""

import pytest
from pydantic import ValidationError

from dli.core.{feature}.models import {Feature}Info, {Feature}Status


class Test{Feature}Status:
    """Tests for {Feature}Status enum."""

    def test_values(self) -> None:
        assert {Feature}Status.ACTIVE.value == "active"

    def test_string_conversion(self) -> None:
        assert str({Feature}Status.ACTIVE) == "active"


class Test{Feature}Info:
    """Tests for {Feature}Info model."""

    def test_minimal_creation(self) -> None:
        info = {Feature}Info(name="test")
        assert info.name == "test"

    def test_json_roundtrip(self) -> None:
        original = {Feature}Info(name="test")
        json_str = original.model_dump_json()
        restored = {Feature}Info.model_validate_json(json_str)
        assert restored == original

    def test_validation_error(self) -> None:
        with pytest.raises(ValidationError):
            {Feature}Info()  # Missing required field
```

## Client Test Template

```python
"""Tests for {feature} client methods."""

import pytest

from dli.core.client import BasecampClient, ServerConfig


@pytest.fixture
def mock_client() -> BasecampClient:
    config = ServerConfig(url="http://test:8080")
    return BasecampClient(config, mock_mode=True)


class Test{Feature}Client:
    def test_list(self, mock_client: BasecampClient) -> None:
        response = mock_client.{feature}_list()
        assert response.success is True
        assert isinstance(response.data, list)

    def test_get(self, mock_client: BasecampClient) -> None:
        response = mock_client.{feature}_get("test_name")
        assert response.success is True
```

## Test Commands

```bash
# Run specific tests
uv run pytest tests/cli/test_{feature}_cmd.py -v
uv run pytest tests/core/{feature}/test_models.py -v

# Run with coverage
uv run pytest tests/cli/test_{feature}_cmd.py --cov=dli.commands.{feature}

# Run matching pattern
uv run pytest -k "{feature}" -v
```
