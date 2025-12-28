"""Tests for CLI base utilities."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from dli.commands.base import (
    MAX_TAGS_DISPLAY,
    format_tags_display,
    get_client,
    get_project_path,
    load_dataset_service,
    load_metric_service,
    spec_to_dict,
    spec_to_list_dict,
    spec_to_register_dict,
)


class TestGetProjectPath:
    """Tests for get_project_path function."""

    def test_returns_path_when_dli_yaml_exists(self, tmp_path: Path) -> None:
        """Test returns current path when dli.yaml exists."""
        (tmp_path / "dli.yaml").touch()
        result = get_project_path(tmp_path)
        assert result == tmp_path

    def test_searches_parent_directories(self, tmp_path: Path) -> None:
        """Test searches parent directories for dli.yaml."""
        (tmp_path / "dli.yaml").touch()
        subdir = tmp_path / "metrics" / "nested"
        subdir.mkdir(parents=True)

        result = get_project_path(subdir)
        assert result == tmp_path

    def test_returns_original_path_when_no_config(self, tmp_path: Path) -> None:
        """Test returns original path when no dli.yaml found."""
        subdir = tmp_path / "no_config"
        subdir.mkdir()

        result = get_project_path(subdir)
        assert result == subdir

    def test_uses_cwd_when_path_is_none(self) -> None:
        """Test uses current working directory when path is None."""
        result = get_project_path(None)
        assert result == Path.cwd() or result.exists()


class TestFormatTagsDisplay:
    """Tests for format_tags_display function."""

    def test_empty_tags_returns_dash(self) -> None:
        """Test empty list returns dash."""
        assert format_tags_display([]) == "-"

    def test_single_tag(self) -> None:
        """Test single tag is displayed correctly."""
        assert format_tags_display(["daily"]) == "daily"

    def test_multiple_tags_within_limit(self) -> None:
        """Test multiple tags within limit."""
        assert format_tags_display(["a", "b"]) == "a, b"

    def test_tags_at_limit(self) -> None:
        """Test tags at the display limit."""
        tags = ["a", "b", "c"]
        assert format_tags_display(tags) == "a, b, c"

    def test_tags_exceeding_limit_shows_ellipsis(self) -> None:
        """Test tags exceeding limit show ellipsis."""
        tags = ["a", "b", "c", "d", "e"]
        result = format_tags_display(tags)
        assert result == "a, b, c..."

    def test_custom_max_display(self) -> None:
        """Test custom max_display parameter."""
        tags = ["a", "b", "c", "d"]
        assert format_tags_display(tags, max_display=2) == "a, b..."

    def test_max_tags_display_constant(self) -> None:
        """Test MAX_TAGS_DISPLAY constant value."""
        assert MAX_TAGS_DISPLAY == 3


class TestSpecToDict:
    """Tests for spec_to_dict function."""

    def test_basic_spec_conversion(self) -> None:
        """Test basic spec conversion without parameters."""
        spec = MagicMock()
        spec.name = "test.metric"
        spec.type.value = "Metric"
        spec.owner = "owner@test.com"
        spec.team = "@team"
        spec.description = "Test description"
        spec.tags = ["tag1", "tag2"]
        spec.domains = ["domain1"]

        result = spec_to_dict(spec)

        assert result == {
            "name": "test.metric",
            "type": "Metric",
            "owner": "owner@test.com",
            "team": "@team",
            "description": "Test description",
            "tags": ["tag1", "tag2"],
            "domains": ["domain1"],
        }

    def test_spec_with_parameters(self) -> None:
        """Test spec conversion with parameters included."""
        spec = MagicMock()
        spec.name = "test.metric"
        spec.type.value = "Metric"
        spec.owner = "owner@test.com"
        spec.team = "@team"
        spec.description = "Test description"
        spec.tags = []
        spec.domains = []

        param = MagicMock()
        param.name = "date"
        param.type.value = "string"
        param.required = True
        param.default = None
        param.description = "Execution date"
        spec.parameters = [param]

        result = spec_to_dict(spec, include_parameters=True)

        assert "parameters" in result
        assert len(result["parameters"]) == 1
        assert result["parameters"][0]["name"] == "date"
        assert result["parameters"][0]["type"] == "string"
        assert result["parameters"][0]["required"] is True

    def test_spec_with_none_description(self) -> None:
        """Test spec conversion with None description."""
        spec = MagicMock()
        spec.name = "test.metric"
        spec.type.value = "Metric"
        spec.owner = "owner"
        spec.team = "@team"
        spec.description = None
        spec.tags = []
        spec.domains = []

        result = spec_to_dict(spec)

        assert result["description"] == ""


class TestSpecToListDict:
    """Tests for spec_to_list_dict function."""

    def test_list_dict_has_minimal_fields(self) -> None:
        """Test list dict contains only fields needed for list display."""
        spec = MagicMock()
        spec.name = "test.metric"
        spec.type.value = "Metric"
        spec.owner = "owner@test.com"
        spec.team = "@team"
        spec.description = "Description"
        spec.tags = ["tag1"]
        spec.domains = ["domain1"]  # Should not be in result

        result = spec_to_list_dict(spec)

        assert "domains" not in result
        assert "parameters" not in result
        assert result["name"] == "test.metric"
        assert result["type"] == "Metric"
        assert result["owner"] == "owner@test.com"


class TestSpecToRegisterDict:
    """Tests for spec_to_register_dict function."""

    def test_register_dict_includes_domains(self) -> None:
        """Test register dict includes domains field."""
        spec = MagicMock()
        spec.name = "test.metric"
        spec.type.value = "Metric"
        spec.owner = "owner@test.com"
        spec.team = "@team"
        spec.description = "Description"
        spec.tags = ["tag1"]
        spec.domains = ["domain1", "domain2"]

        result = spec_to_register_dict(spec)

        assert "domains" in result
        assert result["domains"] == ["domain1", "domain2"]


class TestLoadMetricService:
    """Tests for load_metric_service function."""

    def test_loads_metric_service(self) -> None:
        """Test MetricService is loaded correctly."""
        with patch("dli.core.MetricService") as mock_service:
            mock_instance = MagicMock()
            mock_service.return_value = mock_instance

            result = load_metric_service(Path("/test/project"))

            mock_service.assert_called_once_with(project_path=Path("/test/project"))
            assert result == mock_instance


class TestLoadDatasetService:
    """Tests for load_dataset_service function."""

    def test_loads_dataset_service(self) -> None:
        """Test DatasetService is loaded correctly."""
        with patch("dli.core.DatasetService") as mock_service:
            mock_instance = MagicMock()
            mock_service.return_value = mock_instance

            result = load_dataset_service(Path("/test/project"))

            mock_service.assert_called_once_with(project_path=Path("/test/project"))
            assert result == mock_instance


class TestGetClient:
    """Tests for get_client function."""

    def test_creates_client_with_config(self, tmp_path: Path) -> None:
        """Test client is created with project config."""
        # Create a mock config
        mock_config = MagicMock()
        mock_config.server_url = "http://test.server"
        mock_config.server_timeout = 60
        mock_config.server_api_key = "test-key"

        with (
            patch("dli.core.config.load_project", return_value=mock_config),
            patch("dli.core.client.create_client") as mock_create,
        ):
            mock_client = MagicMock()
            mock_create.return_value = mock_client

            result = get_client(tmp_path)

            mock_create.assert_called_once_with(
                url="http://test.server",
                timeout=60,
                api_key="test-key",
                mock_mode=True,
            )
            assert result == mock_client

    def test_creates_default_client_when_config_not_found(self, tmp_path: Path) -> None:
        """Test default client is created when config not found."""
        with (
            patch("dli.core.config.load_project", side_effect=FileNotFoundError),
            patch("dli.core.client.create_client") as mock_create,
        ):
            mock_client = MagicMock()
            mock_create.return_value = mock_client

            result = get_client(tmp_path)

            mock_create.assert_called_once_with(mock_mode=True)
            assert result == mock_client

    def test_respects_mock_mode_parameter(self, tmp_path: Path) -> None:
        """Test mock_mode parameter is passed through."""
        with (
            patch("dli.core.config.load_project", side_effect=FileNotFoundError),
            patch("dli.core.client.create_client") as mock_create,
        ):
            get_client(tmp_path, mock_mode=False)
            mock_create.assert_called_once_with(mock_mode=False)
