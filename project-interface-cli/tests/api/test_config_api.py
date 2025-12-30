"""Tests for dli.api.config module.

Covers:
- ConfigAPI initialization with context
- Mock mode operations
- Get configuration values
- List environments
- Get current environment
- Server status check
"""

from __future__ import annotations

import os

import pytest

from dli import ConfigAPI, ExecutionContext
from dli.models.common import ConfigValue, EnvironmentInfo


class TestConfigAPIInit:
    """Tests for ConfigAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = ConfigAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(mock_mode=True, server_url="https://test.com")
        api = ConfigAPI(context=ctx)

        assert api.context is ctx
        assert api.context.mock_mode is True

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", mock_mode=True)
        api = ConfigAPI(context=ctx)

        result = repr(api)

        assert "ConfigAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = ConfigAPI(context=ExecutionContext(mock_mode=True))

        # _client should be None before server operations
        assert api._client is None


class TestConfigAPIMockMode:
    """Tests for ConfigAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> ConfigAPI:
        """Create ConfigAPI in mock mode."""
        ctx = ExecutionContext(mock_mode=True)
        return ConfigAPI(context=ctx)

    def test_list_environments_returns_mock(self, mock_api: ConfigAPI) -> None:
        """Test list_environments returns mock environments."""
        result = mock_api.list_environments()

        assert len(result) == 3
        assert all(isinstance(env, EnvironmentInfo) for env in result)

        # Check mock environment names
        names = [env.name for env in result]
        assert "dev" in names
        assert "staging" in names
        assert "prod" in names

    def test_list_environments_has_active(self, mock_api: ConfigAPI) -> None:
        """Test that one environment is marked as active."""
        result = mock_api.list_environments()

        active_count = sum(1 for env in result if env.is_active)
        assert active_count == 1

        # dev should be active in mock mode
        dev_env = next(env for env in result if env.name == "dev")
        assert dev_env.is_active is True


class TestConfigAPIGet:
    """Tests for ConfigAPI.get method."""

    @pytest.fixture
    def api_with_context(self) -> ConfigAPI:
        """Create ConfigAPI with context values."""
        ctx = ExecutionContext(
            server_url="https://context.example.com",
            dialect="bigquery",
        )
        return ConfigAPI(context=ctx)

    def test_get_server_url_from_context(self, api_with_context: ConfigAPI) -> None:
        """Test getting server.url returns context value."""
        result = api_with_context.get("server.url")

        assert result is not None
        assert isinstance(result, ConfigValue)
        assert result.key == "server.url"
        assert result.value == "https://context.example.com"
        assert result.source == "context"

    def test_get_dialect_from_context(self, api_with_context: ConfigAPI) -> None:
        """Test getting defaults.dialect returns context value."""
        result = api_with_context.get("defaults.dialect")

        assert result is not None
        assert result.key == "defaults.dialect"
        assert result.value == "bigquery"
        assert result.source == "context"

    def test_get_unknown_key_returns_none(self, api_with_context: ConfigAPI) -> None:
        """Test getting unknown key returns None."""
        result = api_with_context.get("unknown.key")

        assert result is None

    def test_get_without_project_config(self) -> None:
        """Test get without project config uses context."""
        ctx = ExecutionContext(
            server_url="https://test.com",
            project_path=None,
        )
        api = ConfigAPI(context=ctx)

        result = api.get("server.url")

        assert result is not None
        assert result.value == "https://test.com"


class TestConfigAPIGetCurrentEnvironment:
    """Tests for ConfigAPI.get_current_environment method."""

    def test_default_environment(self) -> None:
        """Test default environment is 'dev'."""
        api = ConfigAPI()

        # Clear any DLI_ENV that might be set
        env_backup = os.environ.pop("DLI_ENV", None)
        try:
            result = api.get_current_environment()
            assert result == "dev"
        finally:
            if env_backup:
                os.environ["DLI_ENV"] = env_backup

    def test_environment_from_env_var(self, monkeypatch: pytest.MonkeyPatch) -> None:
        """Test environment from DLI_ENV environment variable."""
        monkeypatch.setenv("DLI_ENV", "production")

        api = ConfigAPI()
        result = api.get_current_environment()

        assert result == "production"


class TestConfigAPIListEnvironments:
    """Tests for ConfigAPI.list_environments method."""

    @pytest.fixture
    def mock_api(self) -> ConfigAPI:
        """Create ConfigAPI in mock mode."""
        return ConfigAPI(context=ExecutionContext(mock_mode=True))

    def test_list_returns_environment_info(self, mock_api: ConfigAPI) -> None:
        """Test that list returns EnvironmentInfo objects."""
        result = mock_api.list_environments()

        assert isinstance(result, list)
        for env in result:
            assert isinstance(env, EnvironmentInfo)
            assert hasattr(env, "name")
            assert hasattr(env, "is_active")

    def test_list_without_project_path(self) -> None:
        """Test list_environments without project path returns empty in non-mock."""
        ctx = ExecutionContext(mock_mode=False, project_path=None)
        api = ConfigAPI(context=ctx)

        result = api.list_environments()

        assert result == []


class TestConfigAPIServerStatus:
    """Tests for ConfigAPI.get_server_status method."""

    @pytest.fixture
    def mock_api(self) -> ConfigAPI:
        """Create ConfigAPI in mock mode."""
        return ConfigAPI(context=ExecutionContext(mock_mode=True))

    def test_get_server_status_returns_dict(self, mock_api: ConfigAPI) -> None:
        """Test that get_server_status returns a dictionary."""
        result = mock_api.get_server_status()

        assert isinstance(result, dict)

    def test_get_server_status_has_url(self, mock_api: ConfigAPI) -> None:
        """Test that result includes url field."""
        result = mock_api.get_server_status()

        assert "url" in result

    def test_get_server_status_has_healthy(self, mock_api: ConfigAPI) -> None:
        """Test that result includes healthy field."""
        result = mock_api.get_server_status()

        assert "healthy" in result
        assert isinstance(result["healthy"], bool)


class TestConfigAPIClientInit:
    """Tests for ConfigAPI client initialization."""

    def test_client_uses_context_server_url(self) -> None:
        """Test that client uses server_url from context."""
        ctx = ExecutionContext(
            server_url="https://custom.server.com",
            mock_mode=True,
        )
        api = ConfigAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "https://custom.server.com"

    def test_client_default_server_url(self) -> None:
        """Test that client uses default URL when not specified."""
        ctx = ExecutionContext(server_url=None, mock_mode=True)
        api = ConfigAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "http://localhost:8081"


class TestConfigValueModel:
    """Tests for ConfigValue model."""

    def test_config_value_creation(self) -> None:
        """Test ConfigValue model creation."""
        cv = ConfigValue(key="test.key", value="test_value")

        assert cv.key == "test.key"
        assert cv.value == "test_value"
        assert cv.source == "config"  # default

    def test_config_value_with_source(self) -> None:
        """Test ConfigValue with explicit source."""
        cv = ConfigValue(
            key="server.url",
            value="https://example.com",
            source="environment",
        )

        assert cv.source == "environment"

    def test_config_value_any_type(self) -> None:
        """Test that value can be any type."""
        # String
        cv1 = ConfigValue(key="str", value="text")
        assert cv1.value == "text"

        # Integer
        cv2 = ConfigValue(key="int", value=42)
        assert cv2.value == 42

        # Boolean
        cv3 = ConfigValue(key="bool", value=True)
        assert cv3.value is True

        # List
        cv4 = ConfigValue(key="list", value=[1, 2, 3])
        assert cv4.value == [1, 2, 3]


class TestEnvironmentInfoModel:
    """Tests for EnvironmentInfo model."""

    def test_environment_info_creation(self) -> None:
        """Test EnvironmentInfo model creation."""
        env = EnvironmentInfo(name="dev")

        assert env.name == "dev"
        assert env.connection_string is None
        assert env.is_active is False

    def test_environment_info_full(self) -> None:
        """Test EnvironmentInfo with all fields."""
        env = EnvironmentInfo(
            name="prod",
            connection_string="postgres://...",
            is_active=True,
        )

        assert env.name == "prod"
        assert env.connection_string == "postgres://..."
        assert env.is_active is True

    def test_environment_info_frozen(self) -> None:
        """Test that model is frozen (immutable)."""
        from pydantic import ValidationError

        env = EnvironmentInfo(name="dev")

        with pytest.raises(ValidationError):
            env.name = "prod"  # type: ignore[misc]


class TestConfigAPIReadOnly:
    """Tests documenting ConfigAPI is read-only."""

    def test_no_set_method(self) -> None:
        """Test that ConfigAPI has no set method."""
        api = ConfigAPI()

        assert not hasattr(api, "set")

    def test_no_update_method(self) -> None:
        """Test that ConfigAPI has no update method."""
        api = ConfigAPI()

        assert not hasattr(api, "update")

    def test_documented_as_read_only(self) -> None:
        """Test that class docstring mentions read-only."""
        doc = ConfigAPI.__doc__
        assert doc is not None
        assert "read-only" in doc.lower()
