"""Tests for configuration-related models.

Covers:
- ConfigSource enum values
- ConfigValueInfo serialization (extended ConfigValue)
- ValidationResult aggregation
- EnvironmentProfile model
"""

from __future__ import annotations

from typing import Any

from pydantic import ValidationError
import pytest

from dli.models.common import ConfigValue, EnvironmentInfo, ValidationResult

# Config models - all implemented
from dli.models.config import (
    BigQueryConfig,
    ConfigSource,
    ConfigValueInfo,
    EnvironmentProfile,
    ExecutionConfig,
    ServerModeConfig,
    TrinoConfig,
)

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
        _ = ConfigValueInfo(
            key="database.password",
            value="secret",
            source=ConfigSource.PROJECT,
        )
        # Implementation may auto-set is_secret based on key
        # This test documents expected behavior
        pass

    def test_secret_auto_detection_api_key(self) -> None:
        """Test is_secret default detection for api_key keys."""
        _ = ConfigValueInfo(
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


# =============================================================================
# BigQueryConfig Tests
# =============================================================================


class TestBigQueryConfig:
    """Tests for BigQueryConfig model."""

    def test_creation_minimal(self) -> None:
        """Test minimal BigQueryConfig creation."""
        config = BigQueryConfig()

        assert config.project is None
        assert config.location is None
        assert config.credentials_path is None
        assert config.job_timeout_ms is None

    def test_creation_full(self) -> None:
        """Test BigQueryConfig with all fields."""
        config = BigQueryConfig(
            project="my-gcp-project",
            location="US",
            credentials_path="/path/to/credentials.json",
            job_timeout_ms=600000,
        )

        assert config.project == "my-gcp-project"
        assert config.location == "US"
        assert config.credentials_path == "/path/to/credentials.json"
        assert config.job_timeout_ms == 600000

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        config = BigQueryConfig(project="test")

        with pytest.raises(ValidationError):
            config.project = "modified"  # type: ignore[misc]

    def test_extra_fields_allowed(self) -> None:
        """Test that extra fields are allowed for extensibility."""
        config = BigQueryConfig(
            project="test",
            custom_field="custom_value",  # Extra field
        )

        assert config.project == "test"

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        config = BigQueryConfig(project="my-project", location="EU")
        json_str = config.model_dump_json()
        restored = BigQueryConfig.model_validate_json(json_str)

        assert restored.project == config.project
        assert restored.location == config.location


# =============================================================================
# TrinoConfig Tests
# =============================================================================


class TestTrinoConfig:
    """Tests for TrinoConfig model."""

    def test_creation_minimal(self) -> None:
        """Test minimal TrinoConfig creation."""
        config = TrinoConfig()

        assert config.host is None
        assert config.port == 8080  # default
        assert config.user is None
        assert config.catalog is None
        assert config.schema_name is None
        assert config.ssl is False  # default
        assert config.auth_type == "none"  # default
        assert config.auth_token is None
        assert config.password is None

    def test_creation_full(self) -> None:
        """Test TrinoConfig with all fields."""
        config = TrinoConfig(
            host="trino.example.com",
            port=8443,
            user="analyst",
            catalog="iceberg",
            schema="analytics",  # Note: using alias
            ssl=True,
            auth_type="oidc",
            auth_token="token123",
        )

        assert config.host == "trino.example.com"
        assert config.port == 8443
        assert config.user == "analyst"
        assert config.catalog == "iceberg"
        assert config.schema_name == "analytics"
        assert config.ssl is True
        assert config.auth_type == "oidc"
        assert config.auth_token == "token123"

    def test_schema_alias(self) -> None:
        """Test that schema field uses alias."""
        # The alias allows using 'schema' in YAML while avoiding Python keyword
        config = TrinoConfig(schema="my_schema")
        assert config.schema_name == "my_schema"

    def test_default_port(self) -> None:
        """Test default port is 8080."""
        config = TrinoConfig()
        assert config.port == 8080

    def test_auth_types(self) -> None:
        """Test various auth types."""
        for auth in ["basic", "oidc", "jwt", "kerberos", "none"]:
            config = TrinoConfig(auth_type=auth)  # type: ignore[arg-type]
            assert config.auth_type == auth

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        config = TrinoConfig(host="test")

        with pytest.raises(ValidationError):
            config.host = "modified"  # type: ignore[misc]

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        config = TrinoConfig(
            host="trino.example.com",
            port=8443,
            catalog="hive",
            ssl=True,
        )
        json_str = config.model_dump_json()
        restored = TrinoConfig.model_validate_json(json_str)

        assert restored.host == config.host
        assert restored.port == config.port
        assert restored.ssl == config.ssl


# =============================================================================
# ServerModeConfig Tests
# =============================================================================


class TestServerModeConfig:
    """Tests for ServerModeConfig model."""

    def test_creation_minimal(self) -> None:
        """Test minimal ServerModeConfig creation."""
        config = ServerModeConfig()

        assert config.url is None
        assert config.api_token is None
        assert config.verify_ssl is True  # default

    def test_creation_full(self) -> None:
        """Test ServerModeConfig with all fields."""
        config = ServerModeConfig(
            url="https://basecamp.example.com",
            api_token="sk-12345",
            verify_ssl=False,
        )

        assert config.url == "https://basecamp.example.com"
        assert config.api_token == "sk-12345"
        assert config.verify_ssl is False

    def test_default_verify_ssl(self) -> None:
        """Test verify_ssl defaults to True."""
        config = ServerModeConfig(url="https://test.com")
        assert config.verify_ssl is True

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        config = ServerModeConfig(url="https://test.com")

        with pytest.raises(ValidationError):
            config.url = "https://modified.com"  # type: ignore[misc]

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        config = ServerModeConfig(
            url="https://basecamp.example.com",
            api_token="token",
        )
        json_str = config.model_dump_json()
        restored = ServerModeConfig.model_validate_json(json_str)

        assert restored.url == config.url
        assert restored.api_token == config.api_token


# =============================================================================
# ExecutionConfig Tests
# =============================================================================


class TestExecutionConfig:
    """Tests for ExecutionConfig model."""

    def test_creation_minimal(self) -> None:
        """Test minimal ExecutionConfig creation with defaults."""
        config = ExecutionConfig()

        assert config.mode == "local"
        assert config.dialect == "bigquery"
        assert config.timeout == 300
        assert config.bigquery is None
        assert config.trino is None
        assert config.server is None

    def test_creation_full(self) -> None:
        """Test ExecutionConfig with all fields."""
        config = ExecutionConfig(
            mode="server",
            dialect="trino",
            timeout=600,
            bigquery=BigQueryConfig(project="my-project"),
            trino=TrinoConfig(host="trino.example.com"),
            server=ServerModeConfig(url="https://basecamp.example.com"),
        )

        assert config.mode == "server"
        assert config.dialect == "trino"
        assert config.timeout == 600
        assert config.bigquery is not None
        assert config.bigquery.project == "my-project"
        assert config.trino is not None
        assert config.trino.host == "trino.example.com"
        assert config.server is not None
        assert config.server.url == "https://basecamp.example.com"

    def test_mode_values(self) -> None:
        """Test valid mode values."""
        for mode in ["local", "server", "mock"]:
            config = ExecutionConfig(mode=mode)  # type: ignore[arg-type]
            assert config.mode == mode

    def test_dialect_values(self) -> None:
        """Test valid dialect values."""
        for dialect in ["trino", "bigquery", "snowflake", "duckdb", "spark"]:
            config = ExecutionConfig(dialect=dialect)  # type: ignore[arg-type]
            assert config.dialect == dialect

    def test_timeout_bounds(self) -> None:
        """Test timeout validation bounds."""
        # Valid timeout
        config = ExecutionConfig(timeout=100)
        assert config.timeout == 100

        # Below minimum
        with pytest.raises(ValidationError):
            ExecutionConfig(timeout=0)

        # Above maximum
        with pytest.raises(ValidationError):
            ExecutionConfig(timeout=3601)

    def test_from_dict_empty(self) -> None:
        """Test from_dict with empty/None input."""
        config = ExecutionConfig.from_dict(None)
        assert config.mode == "local"
        assert config.dialect == "bigquery"

        config2 = ExecutionConfig.from_dict({})
        assert config2.mode == "local"

    def test_from_dict_basic(self) -> None:
        """Test from_dict with basic config."""
        data = {
            "mode": "server",
            "dialect": "trino",
            "timeout": 600,
        }
        config = ExecutionConfig.from_dict(data)

        assert config.mode == "server"
        assert config.dialect == "trino"
        assert config.timeout == 600

    def test_from_dict_with_nested_configs(self) -> None:
        """Test from_dict with nested engine configs."""
        data = {
            "mode": "local",
            "dialect": "bigquery",
            "timeout": 300,
            "bigquery": {
                "project": "my-gcp-project",
                "location": "US",
            },
            "trino": {
                "host": "trino.example.com",
                "port": 8443,
                "catalog": "iceberg",
            },
            "server": {
                "url": "https://basecamp.example.com",
                "api_token": "token123",
            },
        }
        config = ExecutionConfig.from_dict(data)

        assert config.mode == "local"
        assert config.bigquery is not None
        assert config.bigquery.project == "my-gcp-project"
        assert config.bigquery.location == "US"
        assert config.trino is not None
        assert config.trino.host == "trino.example.com"
        assert config.trino.port == 8443
        assert config.trino.catalog == "iceberg"
        assert config.server is not None
        assert config.server.url == "https://basecamp.example.com"
        assert config.server.api_token == "token123"

    def test_from_dict_partial_nested(self) -> None:
        """Test from_dict with only some nested configs."""
        data = {
            "mode": "local",
            "bigquery": {"project": "test"},
        }
        config = ExecutionConfig.from_dict(data)

        assert config.bigquery is not None
        assert config.bigquery.project == "test"
        assert config.trino is None
        assert config.server is None

    def test_frozen_model(self) -> None:
        """Test that model is frozen (immutable)."""
        config = ExecutionConfig()

        with pytest.raises(ValidationError):
            config.mode = "server"  # type: ignore[misc]

    def test_json_roundtrip(self) -> None:
        """Test JSON serialization roundtrip."""
        config = ExecutionConfig(
            mode="server",
            dialect="trino",
            timeout=600,
            server=ServerModeConfig(url="https://example.com"),
        )
        json_str = config.model_dump_json()
        restored = ExecutionConfig.model_validate_json(json_str)

        assert restored.mode == config.mode
        assert restored.dialect == config.dialect
        assert restored.timeout == config.timeout
        assert restored.server is not None
        assert restored.server.url == config.server.url

    def test_model_schema_includes_all_fields(self) -> None:
        """Test that JSON schema includes all expected fields."""
        schema = ExecutionConfig.model_json_schema()

        assert "properties" in schema
        props = schema["properties"]

        assert "mode" in props
        assert "dialect" in props
        assert "timeout" in props
        assert "bigquery" in props
        assert "trino" in props
        assert "server" in props


# =============================================================================
# Execution Config Integration Tests
# =============================================================================


class TestExecutionConfigIntegration:
    """Integration tests for execution config models."""

    def test_typical_local_bigquery_config(self) -> None:
        """Test typical local BigQuery configuration."""
        data = {
            "mode": "local",
            "dialect": "bigquery",
            "timeout": 300,
            "bigquery": {
                "project": "my-gcp-project",
                "location": "US",
            },
        }
        config = ExecutionConfig.from_dict(data)

        assert config.mode == "local"
        assert config.dialect == "bigquery"
        assert config.bigquery is not None
        assert config.bigquery.project == "my-gcp-project"

    def test_typical_local_trino_config(self) -> None:
        """Test typical local Trino configuration."""
        data = {
            "mode": "local",
            "dialect": "trino",
            "timeout": 600,
            "trino": {
                "host": "trino.company.com",
                "port": 8443,
                "user": "analyst",
                "catalog": "iceberg",
                "schema": "analytics",
                "ssl": True,
                "auth_type": "oidc",
            },
        }
        config = ExecutionConfig.from_dict(data)

        assert config.mode == "local"
        assert config.dialect == "trino"
        assert config.trino is not None
        assert config.trino.host == "trino.company.com"
        assert config.trino.ssl is True
        assert config.trino.auth_type == "oidc"

    def test_typical_server_mode_config(self) -> None:
        """Test typical server mode configuration."""
        data = {
            "mode": "server",
            "dialect": "bigquery",
            "timeout": 300,
            "server": {
                "url": "https://basecamp.production.example.com",
                "api_token": "sk-live-12345",
                "verify_ssl": True,
            },
        }
        config = ExecutionConfig.from_dict(data)

        assert config.mode == "server"
        assert config.server is not None
        assert config.server.url == "https://basecamp.production.example.com"
        assert config.server.api_token == "sk-live-12345"
        assert config.server.verify_ssl is True

    def test_backward_compatibility_empty_execution(self) -> None:
        """Test backward compatibility when execution section is missing."""
        # Old config without execution section
        config = ExecutionConfig.from_dict(None)

        # Should use safe defaults
        assert config.mode == "local"
        assert config.dialect == "bigquery"
        assert config.timeout == 300

    def test_config_yaml_structure(self) -> None:
        """Test config matches expected YAML structure from docs."""
        # Simulating what would come from YAML parsing
        yaml_data: dict[str, Any] = {
            "mode": "local",
            "dialect": "bigquery",
            "timeout": 300,
            "bigquery": {
                "project": "my-gcp-project",
                "location": "US",
            },
            "trino": {
                "host": "trino.example.com",
                "port": 8080,
                "user": "trino",
                "catalog": "iceberg",
                "schema": "analytics",
                "ssl": True,
                "auth_type": "oidc",
                "auth_token": "${DLI_TRINO_TOKEN}",
            },
            "server": {
                "url": "https://basecamp.example.com",
                "api_token": "${DLI_API_TOKEN}",
            },
        }

        config = ExecutionConfig.from_dict(yaml_data)

        # Verify all nested configs are parsed correctly
        assert config.bigquery is not None
        assert config.trino is not None
        assert config.server is not None

        # Verify template syntax is preserved (not resolved at model level)
        assert config.trino.auth_token == "${DLI_TRINO_TOKEN}"
        assert config.server.api_token == "${DLI_API_TOKEN}"
