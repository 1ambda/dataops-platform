"""YAML formatter using ruamel.yaml.

This module provides YAML formatting capabilities with:
- DLI standard key ordering
- Comment preservation
- Consistent indentation
"""

from __future__ import annotations

from dataclasses import dataclass
import difflib
from io import StringIO
from pathlib import Path
from typing import Any

from dli.exceptions import FormatYamlError

from .config import DLI_YAML_KEY_ORDER, FormatConfig, YamlFormatConfig


@dataclass
class YamlFormatResult:
    """Result of YAML formatting.

    Attributes:
        original: Original YAML content.
        formatted: Formatted YAML content.
        changed: Whether the content was changed.
        error: Error message if formatting failed.
    """

    original: str
    formatted: str
    changed: bool = False
    error: str | None = None

    def get_diff(self) -> list[str]:
        """Get diff between original and formatted content.

        Returns:
            List of diff lines.
        """
        if not self.changed:
            return []

        original_lines = self.original.splitlines(keepends=True)
        formatted_lines = self.formatted.splitlines(keepends=True)

        diff = difflib.unified_diff(
            original_lines,
            formatted_lines,
            fromfile="original",
            tofile="formatted",
            lineterm="",
        )
        return list(diff)


class YamlFormatter:
    """YAML formatter with DLI standard formatting.

    Provides YAML formatting with:
    - DLI standard key ordering
    - Comment preservation
    - Consistent 2-space indentation
    - Preserved quote styles

    Example:
        >>> formatter = YamlFormatter()
        >>> result = formatter.format('''
        ... tags: [daily]
        ... name: my_dataset
        ... owner: test@example.com
        ... ''')
        >>> print(result.formatted)
        name: my_dataset
        owner: test@example.com
        tags:
          - daily
    """

    def __init__(
        self,
        config: FormatConfig | YamlFormatConfig | None = None,
    ) -> None:
        """Initialize YAML formatter.

        Args:
            config: Optional format configuration.
        """
        if config is None:
            self.yaml_config = YamlFormatConfig()
        elif isinstance(config, FormatConfig):
            self.yaml_config = config.yaml
        else:
            self.yaml_config = config

        self._yaml = None  # Lazy initialization

    def _get_yaml(self) -> YAML:  # type: ignore[name-defined]  # noqa: F821
        """Get or create the ruamel.yaml YAML instance.

        Returns:
            Configured YAML instance.

        Raises:
            FormatYamlError: If ruamel.yaml is not available.
        """
        if self._yaml is not None:
            return self._yaml

        try:
            from ruamel.yaml import YAML
        except ImportError as e:
            raise FormatYamlError(
                message="ruamel.yaml is not installed. Install with: uv pip install ruamel.yaml",
            ) from e

        # Configure YAML with round-trip mode (preserves comments)
        yaml = YAML(typ="rt")
        yaml.indent(
            mapping=self.yaml_config.indent,
            sequence=self.yaml_config.indent + 2,
            offset=self.yaml_config.indent,
        )
        yaml.preserve_quotes = self.yaml_config.preserve_quotes
        yaml.width = self.yaml_config.line_width
        yaml.default_flow_style = False

        self._yaml = yaml
        return self._yaml

    def format(
        self,
        content: str,
        *,
        file_path: str | None = None,
        reorder_keys: bool = True,
    ) -> YamlFormatResult:
        """Format YAML content.

        Args:
            content: YAML content to format.
            file_path: Optional file path for error context.
            reorder_keys: Whether to reorder keys to DLI standard order.

        Returns:
            YamlFormatResult with formatted content and metadata.
        """
        if not content.strip():
            return YamlFormatResult(original=content, formatted=content, changed=False)

        try:
            yaml = self._get_yaml()

            # Parse YAML
            data = yaml.load(content)

            if data is None:
                return YamlFormatResult(original=content, formatted=content, changed=False)

            # Reorder keys if requested
            if reorder_keys and isinstance(data, dict):
                data = self._reorder_keys(data)

            # Format to string
            stream = StringIO()
            yaml.dump(data, stream)
            formatted = stream.getvalue()

            # Check if content changed
            changed = formatted != content

            return YamlFormatResult(
                original=content,
                formatted=formatted,
                changed=changed,
            )

        except ImportError:
            # ruamel.yaml not installed, return original
            return YamlFormatResult(
                original=content,
                formatted=content,
                changed=False,
                error="ruamel.yaml not installed",
            )
        except Exception as e:
            # Return error result instead of raising
            return YamlFormatResult(
                original=content,
                formatted=content,
                changed=False,
                error=str(e),
            )

    def _reorder_keys(self, data: dict[str, Any]) -> dict[str, Any]:
        """Reorder dictionary keys according to DLI standard order.

        Args:
            data: Dictionary to reorder.

        Returns:
            New dictionary with keys in DLI standard order.
        """
        if self.yaml_config.key_order != "dli_standard":
            return data

        try:
            # Use CommentedMap to preserve comments
            from ruamel.yaml.comments import CommentedMap

            result = CommentedMap()

            # Add keys in standard order
            for key in DLI_YAML_KEY_ORDER:
                if key in data:
                    value = data[key]
                    # Recursively reorder nested dicts
                    if isinstance(value, dict):
                        value = self._reorder_keys(value)
                    result[key] = value

            # Add any remaining keys not in standard order
            for key, value in data.items():
                if key not in result:
                    if isinstance(value, dict):
                        value = self._reorder_keys(value)
                    result[key] = value

            # Preserve comments from original
            if hasattr(data, "ca"):
                result.ca = data.ca  # type: ignore[attr-defined]

            return result

        except ImportError:
            # Fall back to regular dict if ruamel.yaml not available
            result: dict[str, Any] = {}

            for key in DLI_YAML_KEY_ORDER:
                if key in data:
                    result[key] = data[key]

            for key, value in data.items():
                if key not in result:
                    result[key] = value

            return result

    def format_file(
        self,
        file_path: Path,
        *,
        check_only: bool = False,
        reorder_keys: bool = True,
    ) -> YamlFormatResult:
        """Format a YAML file.

        Args:
            file_path: Path to the YAML file.
            check_only: If True, don't modify the file.
            reorder_keys: Whether to reorder keys to DLI standard order.

        Returns:
            YamlFormatResult with formatting result.

        Raises:
            FormatYamlError: If file cannot be read.
        """
        try:
            content = file_path.read_text(encoding="utf-8")
        except Exception as e:
            raise FormatYamlError(
                message=f"Cannot read YAML file: {e}",
                file_path=str(file_path),
            ) from e

        result = self.format(content, file_path=str(file_path), reorder_keys=reorder_keys)

        # Write formatted content if not check_only and content changed
        if not check_only and result.changed and result.error is None:
            try:
                file_path.write_text(result.formatted, encoding="utf-8")
            except Exception as e:
                raise FormatYamlError(
                    message=f"Cannot write YAML file: {e}",
                    file_path=str(file_path),
                ) from e

        return result


def get_key_order_position(key: str) -> int:
    """Get the position of a key in DLI standard order.

    Args:
        key: The key to look up.

    Returns:
        Position index, or a high number if not in standard order.
    """
    try:
        return DLI_YAML_KEY_ORDER.index(key)
    except ValueError:
        return len(DLI_YAML_KEY_ORDER) + ord(key[0]) if key else len(DLI_YAML_KEY_ORDER)


__all__ = [
    "YamlFormatResult",
    "YamlFormatter",
    "get_key_order_position",
]
