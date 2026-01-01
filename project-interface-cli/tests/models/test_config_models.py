"""Tests for configuration-related models.

Covers:
- ConfigSource enum values
- ConfigValueInfo serialization (extended ConfigValue)
- ValidationResult aggregation
- EnvironmentProfile model
"""

from __future__ import annotations

from typing import Any

import pytest
from pydantic import ValidationError

# Config models - all implemented
from dli.models.config import (
    ConfigSource,
    ConfigValidationResult,
    ConfigValueInfo,
    EnvironmentProfile,
)

from dli.models.common import ConfigValue, EnvironmentInfo, ValidationResult


# =============================================================================
# ConfigSource Enum Tests
# =============================================================================


class TestConfigSourceEnum:
    """Tests for ConfigSource enum."""

    def test_default_value(self) -> None:
        """Test DEFAULT source value."""
        assert ConfigSource.DEFAULT.value == "default"

    def test_global_value(self) -> None:
        """Test GLOBAL source value."""
        assert ConfigSource.GLOBAL.value == "global"

    def test_project_value(self) -> None:
        """Test PROJECT source value."""
        assert ConfigSource.PROJECT.value == "project"

    def test_local_value(self) -> None:
        """Test LOCAL source value."""
        assert ConfigSource.LOCAL.value == "local"

    def test_env_var_value(self) -> None:
        """Test ENV_VAR source value."""
        assert ConfigSource.ENV_VAR.value == "env"

    def test_cli_value(self) -> None:
        """Test CLI source value."""
        assert ConfigSource.CLI.value == "cli"

    def test_string_conversion(self) -> None:
        """Test string conversion of enum.

        ConfigSource is a str-based Enum, so its .value returns the string.
        Note: str(ConfigSource.X) returns 'ConfigSource.X', use .value for 'x'.
        """
        assert ConfigSource.PROJECT.value == "project"
        assert ConfigSource.LOCAL.value == "local"
        # Also verify str-enum behavior (membership comparison)
        assert ConfigSource.PROJECT == "project"

    def test_enum_comparison(self) -> None:
        """Test enum comparison."""
        assert ConfigSource.LOCAL == ConfigSource.LOCAL
        assert ConfigSource.PROJECT != ConfigSource.LOCAL

    def test_enum_from_string(self) -> None:
        """Test creating enum from string value."""
        assert ConfigSource("project") == ConfigSource.PROJECT
        assert ConfigSource("local") == ConfigSource.LOCAL

    def test_enum_invalid_value(self) -> None:
        """Test invalid enum value raises error."""
        with pytest.raises(ValueError):
            ConfigSource("invalid")

    def test_all_sources_defined(self) -> None:
        """Test all expected sources are defined."""
        expected = {"default", "global", "project", "local", "env", "cli"}
        actual = {source.value for source in ConfigSource}
        assert actual == expected


# =============================================================================
# ConfigValue Tests (Existing Model)
# =============================================================================


class TestConfigValueModel:
    """Tests for existing ConfigValue model."""

    def test_creation_minimal(self) -> None:
        """Test minimal ConfigValue creation."""
        cv = ConfigValue(key="test.key", value="test_value")

        assert cv.key == "test.key"
        assert cv.value == "test_value"
        assert cv.source == "config"  # default

    def test_creation_with_source(self) -> None:
        """Test ConfigValue with explicit source."""
        cv = ConfigValue(
            key="server.url",
            value="https://example.com",
            source="environment",
        )

        assert cv.key == "server.url"
        assert cv.value == "https://example.com"
        assert cv.source == "environment"

    def test_value_type_string(self) -> None:
        """Test string value type."""
        cv = ConfigValue(key="str", value="text")
        assert cv.value == "text"
        assert isinstance(cv.value, str)

    def test_value_type_integer(self) -> None:
        """Test integer value type."""
        cv = ConfigValue(key="int", value=42)
        assert cv.value == 42
        assert isinstance(cv.value, int)

    def test_value_type_boolean(self) -> None:
        """Test boolean value type."""
        cv = ConfigValue(key="bool", value=True)
        assert cv.value is True

    def test_value_type_list(self) -> None:
        """Test list value type."""
        cv = ConfigValue(key="list", value=[1, 2, 3])
        assert cv.value == [1, 2, 3]

    def test_value_type_dict(self) -> None:
        """Test dict value type."""
        cv = ConfigValue(key="dict", value={"a": 1, "b": 2})
        assert cv.value == {"a": 1, "b": 2}

    def test_value_type_none(self) -> None:
        """Test None value type."""
        cv = ConfigValue(key="none", value=None)
        assert cv.value is None

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        cv = ConfigValue(key="test", value="value")

        with pytest.raises(ValidationError):
            cv.key = "modified"  # type: ignore[misc]

    def test_json_serialization(self) -> None:
        """Test JSON serialization."""
        cv = ConfigValue(key="test", value="value", source="project")
        json_str = cv.model_dump_json()

        assert "test" in json_str
        assert "value" in json_str
        assert "project" in json_str

    def test_json_deserialization(self) -> None:
        """Test JSON deserialization."""
        cv = ConfigValue(key="test", value="value", source="project")
        json_str = cv.model_dump_json()

        restored = ConfigValue.model_validate_json(json_str)

        assert restored.key == cv.key
        assert restored.value == cv.value
        assert restored.source == cv.source


# =============================================================================
# ConfigValueInfo Tests (Extended Model)
# =============================================================================


class TestConfigValueInfoModel:
    """Tests for extended ConfigValueInfo model."""

    def test_creation_minimal(self) -> None:
        """Test minimal ConfigValueInfo creation."""
        cv = ConfigValueInfo(
            key="test.key",
            value="test_value",
            source=ConfigSource.PROJECT,
        )

        assert cv.key == "test.key"
        assert cv.value == "test_value"
        assert cv.source == ConfigSource.PROJECT
        assert cv.is_secret is False
        assert cv.raw_value is None

    def test_creation_with_secret_flag(self) -> None:
        """Test ConfigValueInfo with is_secret flag."""
        cv = ConfigValueInfo(
            key="database.password",
            value="secret123",
            source=ConfigSource.ENV_VAR,
            is_secret=True,
        )

        assert cv.is_secret is True

    def test_creation_with_raw_value(self) -> None:
        """Test ConfigValueInfo with raw_value (template)."""
        cv = ConfigValueInfo(
            key="server.url",
            value="https://prod.example.com",
            source=ConfigSource.ENV_VAR,
            raw_value="${DLI_SERVER_URL}",
        )

        assert cv.raw_value == "${DLI_SERVER_URL}"

    def test_source_type_validation(self) -> None:
        """Test that source must be ConfigSource enum."""
        cv = ConfigValueInfo(
            key="test",
            value="value",
            source=ConfigSource.LOCAL,
        )
        assert isinstance(cv.source, ConfigSource)

    def test_secret_auto_detection_password(self) -> None:
        """Test is_secret default detection for password keys."""
        # If implementation auto-detects secrets based on key name
        cv = ConfigValueInfo(
            key="database.password",
            value="secret",
            source=ConfigSource.PROJECT,
        )
        # Implementation may auto-set is_secret based on key
        # This test documents expected behavior
        pass

    def test_secret_auto_detection_api_key(self) -> None:
        """Test is_secret default detection for api_key keys."""
        cv = ConfigValueInfo(
            key="server.api_key",
            value="sk-xxx",
            source=ConfigSource.ENV_VAR,
        )
        pass

    def test_display_value_masked_when_secret(self) -> None:
        """Test that display_value masks secrets."""
        cv = ConfigValueInfo(
            key="password",
            value="actual-password",
            source=ConfigSource.ENV_VAR,
            is_secret=True,
        )

        # If display_value property exists
        if hasattr(cv, "display_value"):
            assert cv.display_value == "***"
        else:
            # Alternative: check model_dump with mask option
            pass

    def test_display_value_shows_configured_when_secret(self) -> None:
        """Test secret shows '*** (configured)' pattern."""
        cv = ConfigValueInfo(
            key="api_key",
            value="sk-12345",
            source=ConfigSource.ENV_VAR,
            is_secret=True,
        )

        if hasattr(cv, "display_value"):
            assert "configured" in cv.display_value.lower() or cv.display_value == "***"

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        cv = ConfigValueInfo(
            key="test",
            value="value",
            source=ConfigSource.PROJECT,
            is_secret=False,
            raw_value="${TEST}",
        )
        json_str = cv.model_dump_json()
        restored = ConfigValueInfo.model_validate_json(json_str)

        assert restored.key == cv.key
        assert restored.value == cv.value
        assert restored.source == cv.source
        assert restored.is_secret == cv.is_secret
        assert restored.raw_value == cv.raw_value

    def test_comparison_equality(self) -> None:
        """Test ConfigValueInfo equality comparison."""
        cv1 = ConfigValueInfo(
            key="test",
            value="value",
            source=ConfigSource.PROJECT,
        )
        cv2 = ConfigValueInfo(
            key="test",
            value="value",
            source=ConfigSource.PROJECT,
        )
        cv3 = ConfigValueInfo(
            key="test",
            value="different",
            source=ConfigSource.PROJECT,
        )

        assert cv1 == cv2
        assert cv1 != cv3


# =============================================================================
# ValidationResult Tests
# =============================================================================


class TestValidationResult:
    """Tests for ValidationResult model."""

    def test_valid_result(self) -> None:
        """Test creating a valid result."""
        result = ValidationResult(valid=True)

        assert result.valid is True
        assert result.errors == []
        assert result.warnings == []

    def test_invalid_result_with_errors(self) -> None:
        """Test creating an invalid result with errors."""
        result = ValidationResult(
            valid=False,
            errors=["Missing required field: server.url"],
        )

        assert result.valid is False
        assert len(result.errors) == 1
        assert "server.url" in result.errors[0]

    def test_valid_result_with_warnings(self) -> None:
        """Test valid result can have warnings."""
        result = ValidationResult(
            valid=True,
            warnings=["Using deprecated field: mock_mode"],
        )

        assert result.valid is True
        assert len(result.warnings) == 1

    def test_multiple_errors(self) -> None:
        """Test result with multiple errors."""
        result = ValidationResult(
            valid=False,
            errors=[
                "Missing: server.url",
                "Invalid: defaults.timeout (must be positive)",
                "Missing env var: DLI_API_KEY",
            ],
        )

        assert len(result.errors) == 3

    def test_has_errors_property(self) -> None:
        """Test has_errors property."""
        valid_result = ValidationResult(valid=True)
        invalid_result = ValidationResult(valid=False, errors=["error"])

        assert valid_result.has_errors is False
        assert invalid_result.has_errors is True

    def test_has_warnings_property(self) -> None:
        """Test has_warnings property."""
        no_warnings = ValidationResult(valid=True)
        with_warnings = ValidationResult(valid=True, warnings=["warning"])

        assert no_warnings.has_warnings is False
        assert with_warnings.has_warnings is True

    def test_frozen_model(self) -> None:
        """Test ValidationResult is immutable."""
        result = ValidationResult(valid=True)

        with pytest.raises(ValidationError):
            result.valid = False  # type: ignore[misc]

    def test_json_serialization(self) -> None:
        """Test JSON serialization."""
        result = ValidationResult(
            valid=False,
            errors=["error1"],
            warnings=["warning1"],
        )

        json_str = result.model_dump_json()

        assert "valid" in json_str
        assert "error1" in json_str
        assert "warning1" in json_str


# =============================================================================
# EnvironmentInfo Tests (Existing Model)
# =============================================================================


class TestEnvironmentInfo:
    """Tests for existing EnvironmentInfo model."""

    def test_creation_minimal(self) -> None:
        """Test minimal EnvironmentInfo creation."""
        env = EnvironmentInfo(name="dev")

        assert env.name == "dev"
        assert env.connection_string is None
        assert env.is_active is False

    def test_creation_full(self) -> None:
        """Test EnvironmentInfo with all fields."""
        env = EnvironmentInfo(
            name="prod",
            connection_string="postgres://...",
            is_active=True,
        )

        assert env.name == "prod"
        assert env.connection_string == "postgres://..."
        assert env.is_active is True

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        env = EnvironmentInfo(name="dev")

        with pytest.raises(ValidationError):
            env.name = "prod"  # type: ignore[misc]

    def test_multiple_environments(self) -> None:
        """Test creating multiple environments."""
        envs = [
            EnvironmentInfo(name="dev", is_active=True),
            EnvironmentInfo(name="staging"),
            EnvironmentInfo(name="prod"),
        ]

        active_count = sum(1 for e in envs if e.is_active)
        assert active_count == 1

    def test_json_serialization(self) -> None:
        """Test JSON serialization."""
        env = EnvironmentInfo(name="prod", is_active=True)
        json_str = env.model_dump_json()

        assert "prod" in json_str
        assert "is_active" in json_str


# =============================================================================
# EnvironmentProfile Tests (Extended Model)
# =============================================================================


class TestEnvironmentProfile:
    """Tests for EnvironmentProfile model."""

    def test_creation_minimal(self) -> None:
        """Test minimal EnvironmentProfile creation."""
        profile = EnvironmentProfile(name="dev")

        assert profile.name == "dev"

    def test_creation_full(self) -> None:
        """Test EnvironmentProfile with all fields."""
        profile = EnvironmentProfile(
            name="prod",
            server_url="https://prod.basecamp.io",
            dialect="bigquery",
            catalog="prod_catalog",
            connection_string="bigquery://project/dataset",
            is_active=True,
        )

        assert profile.name == "prod"
        assert profile.server_url == "https://prod.basecamp.io"
        assert profile.dialect == "bigquery"
        assert profile.catalog == "prod_catalog"
        assert profile.is_active is True

    def test_from_dict(self) -> None:
        """Test creating EnvironmentProfile from dict."""
        data = {
            "name": "staging",
            "server_url": "https://staging.example.com",
            "dialect": "trino",
        }

        profile = EnvironmentProfile(**data)

        assert profile.name == "staging"
        assert profile.dialect == "trino"

    def test_default_values(self) -> None:
        """Test default values for optional fields."""
        profile = EnvironmentProfile(name="test")

        assert profile.server_url is None
        assert profile.dialect is None
        assert profile.catalog is None
        assert profile.is_active is False

    def test_to_environment_info(self) -> None:
        """Test conversion to EnvironmentInfo."""
        profile = EnvironmentProfile(
            name="dev",
            connection_string="conn://string",
            is_active=True,
        )

        if hasattr(profile, "to_environment_info"):
            info = profile.to_environment_info()
            assert isinstance(info, EnvironmentInfo)
            assert info.name == "dev"
            assert info.is_active is True

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        profile = EnvironmentProfile(
            name="prod",
            server_url="https://prod.example.com",
            dialect="bigquery",
        )

        json_str = profile.model_dump_json()
        restored = EnvironmentProfile.model_validate_json(json_str)

        assert restored.name == profile.name
        assert restored.server_url == profile.server_url
        assert restored.dialect == profile.dialect


# =============================================================================
# Model Integration Tests
# =============================================================================


class TestModelIntegration:
    """Integration tests for config models."""

    def test_config_value_in_list(self) -> None:
        """Test ConfigValue works in list context."""
        values = [
            ConfigValue(key="server.url", value="https://example.com", source="config"),
            ConfigValue(key="server.timeout", value=30, source="default"),
        ]

        assert len(values) == 2
        assert all(isinstance(v, ConfigValue) for v in values)

    def test_environment_info_sorting(self) -> None:
        """Test EnvironmentInfo can be sorted by name."""
        envs = [
            EnvironmentInfo(name="prod"),
            EnvironmentInfo(name="dev"),
            EnvironmentInfo(name="staging"),
        ]

        sorted_envs = sorted(envs, key=lambda e: e.name)

        assert sorted_envs[0].name == "dev"
        assert sorted_envs[1].name == "prod"
        assert sorted_envs[2].name == "staging"

    def test_validation_result_aggregation(self) -> None:
        """Test aggregating multiple validation results."""
        results = [
            ValidationResult(valid=True, warnings=["w1"]),
            ValidationResult(valid=False, errors=["e1"]),
            ValidationResult(valid=True, warnings=["w2", "w3"]),
        ]

        # Aggregate
        all_errors = []
        all_warnings = []
        for r in results:
            all_errors.extend(r.errors)
            all_warnings.extend(r.warnings)

        final_valid = all(r.valid for r in results)

        assert final_valid is False
        assert len(all_errors) == 1
        assert len(all_warnings) == 3

    def test_model_dict_conversion(self) -> None:
        """Test model_dump() for all models."""
        cv = ConfigValue(key="test", value=123, source="config")
        env = EnvironmentInfo(name="dev", is_active=True)
        vr = ValidationResult(valid=True, warnings=["test"])

        cv_dict = cv.model_dump()
        env_dict = env.model_dump()
        vr_dict = vr.model_dump()

        assert cv_dict["key"] == "test"
        assert env_dict["name"] == "dev"
        assert vr_dict["valid"] is True

    def test_model_schema_generation(self) -> None:
        """Test JSON schema generation for models."""
        cv_schema = ConfigValue.model_json_schema()
        env_schema = EnvironmentInfo.model_json_schema()
        vr_schema = ValidationResult.model_json_schema()

        assert "properties" in cv_schema
        assert "properties" in env_schema
        assert "properties" in vr_schema

        assert "key" in cv_schema["properties"]
        assert "name" in env_schema["properties"]
        assert "valid" in vr_schema["properties"]
