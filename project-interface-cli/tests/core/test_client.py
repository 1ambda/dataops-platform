"""Tests for the Basecamp client module."""

from __future__ import annotations

import pytest

from dli.core.client import BasecampClient, ServerConfig, ServerResponse, create_client


class TestServerConfig:
    """Tests for ServerConfig."""

    def test_create_config(self) -> None:
        """Test creating a server configuration."""
        config = ServerConfig(url="http://localhost:8081")
        assert config.url == "http://localhost:8081"
        assert config.timeout == 30  # default
        assert config.api_key is None

    def test_create_config_with_all_options(self) -> None:
        """Test creating a configuration with all options."""
        config = ServerConfig(
            url="http://example.com:8080",
            timeout=60,
            api_key="secret-key",
        )
        assert config.url == "http://example.com:8080"
        assert config.timeout == 60
        assert config.api_key == "secret-key"


class TestServerResponse:
    """Tests for ServerResponse."""

    def test_success_response(self) -> None:
        """Test creating a success response."""
        response = ServerResponse(success=True, data={"key": "value"})
        assert response.success is True
        assert response.data == {"key": "value"}
        assert response.error is None

    def test_error_response(self) -> None:
        """Test creating an error response."""
        response = ServerResponse(
            success=False, error="Something went wrong", status_code=500
        )
        assert response.success is False
        assert response.error == "Something went wrong"
        assert response.status_code == 500


class TestBasecampClientMock:
    """Tests for BasecampClient in mock mode."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_health_check(self, client: BasecampClient) -> None:
        """Test health check in mock mode."""
        response = client.health_check()
        assert response.success is True
        assert response.data is not None
        assert response.data["status"] == "healthy"

    # Metric tests

    def test_list_metrics(self, client: BasecampClient) -> None:
        """Test listing metrics."""
        response = client.list_metrics()
        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) > 0

    def test_list_metrics_with_tag_filter(self, client: BasecampClient) -> None:
        """Test listing metrics with tag filter."""
        response = client.list_metrics(tag="reporting")
        assert response.success is True
        assert isinstance(response.data, list)

    def test_list_metrics_with_search(self, client: BasecampClient) -> None:
        """Test listing metrics with search."""
        response = client.list_metrics(search="user")
        assert response.success is True
        assert isinstance(response.data, list)

    def test_get_metric_found(self, client: BasecampClient) -> None:
        """Test getting an existing metric."""
        response = client.get_metric("iceberg.reporting.user_summary")
        assert response.success is True
        assert response.data is not None
        assert response.data["name"] == "iceberg.reporting.user_summary"

    def test_get_metric_not_found(self, client: BasecampClient) -> None:
        """Test getting a non-existent metric."""
        response = client.get_metric("nonexistent.metric")
        assert response.success is False
        assert response.status_code == 404

    def test_register_metric(self, client: BasecampClient) -> None:
        """Test registering a new metric."""
        spec_data = {
            "name": "iceberg.test.new_metric",
            "type": "Metric",
            "owner": "test@example.com",
            "team": "@test",
        }
        response = client.register_metric(spec_data)
        assert response.success is True

    def test_register_metric_duplicate(self, client: BasecampClient) -> None:
        """Test registering a duplicate metric."""
        spec_data = {
            "name": "iceberg.reporting.user_summary",
            "type": "Metric",
            "owner": "test@example.com",
            "team": "@test",
        }
        response = client.register_metric(spec_data)
        assert response.success is False
        assert response.status_code == 409

    # Dataset tests

    def test_list_datasets(self, client: BasecampClient) -> None:
        """Test listing datasets."""
        response = client.list_datasets()
        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) > 0

    def test_list_datasets_with_tag_filter(self, client: BasecampClient) -> None:
        """Test listing datasets with tag filter."""
        response = client.list_datasets(tag="feed")
        assert response.success is True
        assert isinstance(response.data, list)

    def test_get_dataset_found(self, client: BasecampClient) -> None:
        """Test getting an existing dataset."""
        response = client.get_dataset("iceberg.analytics.daily_clicks")
        assert response.success is True
        assert response.data is not None
        assert response.data["name"] == "iceberg.analytics.daily_clicks"

    def test_get_dataset_not_found(self, client: BasecampClient) -> None:
        """Test getting a non-existent dataset."""
        response = client.get_dataset("nonexistent.dataset")
        assert response.success is False
        assert response.status_code == 404

    def test_register_dataset(self, client: BasecampClient) -> None:
        """Test registering a new dataset."""
        spec_data = {
            "name": "iceberg.test.new_dataset",
            "type": "Dataset",
            "owner": "test@example.com",
            "team": "@test",
        }
        response = client.register_dataset(spec_data)
        assert response.success is True

    def test_register_dataset_duplicate(self, client: BasecampClient) -> None:
        """Test registering a duplicate dataset."""
        spec_data = {
            "name": "iceberg.analytics.daily_clicks",
            "type": "Dataset",
            "owner": "test@example.com",
            "team": "@test",
        }
        response = client.register_dataset(spec_data)
        assert response.success is False
        assert response.status_code == 409


class TestCreateClient:
    """Tests for create_client helper function."""

    def test_create_mock_client(self) -> None:
        """Test creating a mock client."""
        client = create_client(mock_mode=True)
        assert client.mock_mode is True
        assert client.config.url == "http://localhost:8081"

    def test_create_client_with_url(self) -> None:
        """Test creating a client with custom URL."""
        client = create_client(
            url="http://custom.server:9000", timeout=60, api_key="key123"
        )
        assert client.config.url == "http://custom.server:9000"
        assert client.config.timeout == 60
        assert client.config.api_key == "key123"
