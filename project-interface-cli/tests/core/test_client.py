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


class TestBasecampClientNonMock:
    """Tests for BasecampClient in non-mock mode (real API calls)."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a non-mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=False)

    def test_health_check_non_mock(self, client: BasecampClient) -> None:
        """Test health check returns 501 in non-mock mode."""
        response = client.health_check()
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_list_metrics_non_mock(self, client: BasecampClient) -> None:
        """Test list metrics returns 501 in non-mock mode."""
        response = client.list_metrics()
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_get_metric_non_mock(self, client: BasecampClient) -> None:
        """Test get metric returns 501 in non-mock mode."""
        response = client.get_metric("some.metric.name")
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_register_metric_non_mock(self, client: BasecampClient) -> None:
        """Test register metric returns 501 in non-mock mode."""
        response = client.register_metric({"name": "test.metric"})
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_list_datasets_non_mock(self, client: BasecampClient) -> None:
        """Test list datasets returns 501 in non-mock mode."""
        response = client.list_datasets()
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_get_dataset_non_mock(self, client: BasecampClient) -> None:
        """Test get dataset returns 501 in non-mock mode."""
        response = client.get_dataset("some.dataset.name")
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_register_dataset_non_mock(self, client: BasecampClient) -> None:
        """Test register dataset returns 501 in non-mock mode."""
        response = client.register_dataset({"name": "test.dataset"})
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_get_lineage_non_mock(self, client: BasecampClient) -> None:
        """Test get lineage returns 501 in non-mock mode."""
        response = client.get_lineage("iceberg.analytics.daily_clicks")
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    def test_execute_quality_test_non_mock(self, client: BasecampClient) -> None:
        """Test execute quality test returns 501 in non-mock mode."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="not_null_test",
            test_type="not_null",
        )
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"


class TestBasecampClientLineage:
    """Tests for lineage functionality in mock mode."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    # get_lineage tests

    def test_get_lineage_existing_dataset(self, client: BasecampClient) -> None:
        """Test getting lineage for existing dataset."""
        response = client.get_lineage("iceberg.analytics.daily_clicks")
        assert response.success is True
        assert response.data is not None
        assert response.data["root"]["name"] == "iceberg.analytics.daily_clicks"
        assert "nodes" in response.data
        assert "edges" in response.data
        assert "total_upstream" in response.data
        assert "total_downstream" in response.data

    def test_get_lineage_existing_metric(self, client: BasecampClient) -> None:
        """Test getting lineage for existing metric."""
        response = client.get_lineage("iceberg.reporting.user_summary")
        assert response.success is True
        assert response.data is not None
        assert response.data["root"]["name"] == "iceberg.reporting.user_summary"
        assert response.data["root"]["type"] == "Metric"

    def test_get_lineage_not_found(self, client: BasecampClient) -> None:
        """Test getting lineage for non-existent resource."""
        response = client.get_lineage("nonexistent.resource.name")
        assert response.success is False
        assert response.status_code == 404
        assert "not found" in response.error.lower()

    def test_get_lineage_direction_upstream(self, client: BasecampClient) -> None:
        """Test getting only upstream lineage."""
        response = client.get_lineage(
            "iceberg.analytics.daily_clicks", direction="upstream"
        )
        assert response.success is True
        # total_upstream and total_downstream report available counts
        # but nodes only contains upstream nodes for this direction
        assert response.data["total_upstream"] >= 0
        # Nodes should only contain upstream nodes (count should match total_upstream)
        assert len(response.data["nodes"]) == response.data["total_upstream"]

    def test_get_lineage_direction_downstream(self, client: BasecampClient) -> None:
        """Test getting only downstream lineage."""
        response = client.get_lineage(
            "iceberg.analytics.daily_clicks", direction="downstream"
        )
        assert response.success is True
        # total_upstream and total_downstream report available counts
        # but nodes only contains downstream nodes for this direction
        assert response.data["total_downstream"] >= 0
        # Nodes should only contain downstream nodes (count should match total_downstream)
        assert len(response.data["nodes"]) == response.data["total_downstream"]

    def test_get_lineage_direction_both(self, client: BasecampClient) -> None:
        """Test getting both upstream and downstream lineage."""
        response = client.get_lineage(
            "iceberg.analytics.daily_clicks", direction="both"
        )
        assert response.success is True
        # Should have both upstream and downstream
        total_nodes = len(response.data["nodes"])
        assert total_nodes == response.data["total_upstream"] + response.data["total_downstream"]

    def test_get_lineage_depth_limited(self, client: BasecampClient) -> None:
        """Test getting lineage with limited depth."""
        response = client.get_lineage(
            "iceberg.analytics.daily_clicks", depth=1
        )
        assert response.success is True
        # With depth=1, should have fewer nodes than unlimited
        response_unlimited = client.get_lineage(
            "iceberg.analytics.daily_clicks", depth=-1
        )
        assert len(response.data["nodes"]) <= len(response_unlimited.data["nodes"])

    def test_get_lineage_depth_zero(self, client: BasecampClient) -> None:
        """Test getting lineage with depth=0 still works."""
        response = client.get_lineage(
            "iceberg.analytics.daily_clicks", depth=0
        )
        # Even with depth=0, the mock generates some nodes based on naming convention
        assert response.success is True
        assert response.data is not None

    def test_get_lineage_warehouse_dataset(self, client: BasecampClient) -> None:
        """Test lineage for warehouse schema dataset."""
        response = client.get_lineage("iceberg.warehouse.user_events")
        assert response.success is True
        assert response.data["root"]["name"] == "iceberg.warehouse.user_events"
        # Warehouse schema should also generate lineage
        assert "nodes" in response.data


class TestBasecampClientMockUpstreamDownstream:
    """Tests for mock upstream and downstream generation."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    # _generate_mock_upstream tests

    def test_generate_mock_upstream_analytics_schema(self, client: BasecampClient) -> None:
        """Test upstream generation for analytics schema."""
        result = client._generate_mock_upstream("iceberg.analytics.daily_clicks", depth=-1)
        assert "nodes" in result
        assert "edges" in result
        # Analytics schema should generate raw source dependency
        assert len(result["nodes"]) >= 1
        # Check raw source naming convention
        raw_nodes = [n for n in result["nodes"] if "raw" in n["name"]]
        assert len(raw_nodes) >= 1

    def test_generate_mock_upstream_reporting_schema(self, client: BasecampClient) -> None:
        """Test upstream generation for reporting schema."""
        result = client._generate_mock_upstream("iceberg.reporting.user_summary", depth=-1)
        assert "nodes" in result
        # Reporting schema should also generate dependencies
        assert len(result["nodes"]) >= 1

    def test_generate_mock_upstream_warehouse_schema(self, client: BasecampClient) -> None:
        """Test upstream generation for warehouse schema."""
        result = client._generate_mock_upstream("iceberg.warehouse.user_events", depth=-1)
        assert "nodes" in result
        # Warehouse schema should also generate dependencies
        assert len(result["nodes"]) >= 1

    def test_generate_mock_upstream_unknown_schema(self, client: BasecampClient) -> None:
        """Test upstream generation for unknown schema - no dependencies."""
        result = client._generate_mock_upstream("iceberg.unknown_schema.table", depth=-1)
        assert "nodes" in result
        # Unknown schema should not generate dependencies
        assert len(result["nodes"]) == 0
        assert len(result["edges"]) == 0

    def test_generate_mock_upstream_depth_one(self, client: BasecampClient) -> None:
        """Test upstream generation with depth=1."""
        result = client._generate_mock_upstream("iceberg.analytics.daily_clicks", depth=1)
        # With depth=1, should only have first level
        assert len(result["nodes"]) >= 1
        # Should not have second level (external source)
        external_nodes = [n for n in result["nodes"] if "external" in n["name"]]
        assert len(external_nodes) == 0

    def test_generate_mock_upstream_depth_two(self, client: BasecampClient) -> None:
        """Test upstream generation with depth>=2 includes external source."""
        result = client._generate_mock_upstream("iceberg.analytics.daily_clicks", depth=2)
        # With depth>=2, should have external source
        external_nodes = [n for n in result["nodes"] if "external" in n["name"]]
        assert len(external_nodes) >= 1

    def test_generate_mock_upstream_invalid_name_format(self, client: BasecampClient) -> None:
        """Test upstream generation with invalid name format."""
        result = client._generate_mock_upstream("invalid", depth=-1)
        assert "nodes" in result
        # Invalid name format should not generate dependencies
        assert len(result["nodes"]) == 0

    def test_generate_mock_upstream_two_part_name(self, client: BasecampClient) -> None:
        """Test upstream generation with two-part name."""
        result = client._generate_mock_upstream("catalog.table", depth=-1)
        assert "nodes" in result
        # Two-part name should not generate dependencies
        assert len(result["nodes"]) == 0

    # _generate_mock_downstream tests

    def test_generate_mock_downstream_raw_schema(self, client: BasecampClient) -> None:
        """Test downstream generation for raw schema."""
        result = client._generate_mock_downstream("iceberg.raw.clicks", depth=-1)
        assert "nodes" in result
        assert "edges" in result
        # Raw schema should generate reporting dependency
        assert len(result["nodes"]) >= 1
        reporting_nodes = [n for n in result["nodes"] if "reporting" in n["name"]]
        assert len(reporting_nodes) >= 1

    def test_generate_mock_downstream_analytics_schema(self, client: BasecampClient) -> None:
        """Test downstream generation for analytics schema."""
        result = client._generate_mock_downstream("iceberg.analytics.daily_clicks", depth=-1)
        assert "nodes" in result
        # Analytics schema should generate reporting dependency
        assert len(result["nodes"]) >= 1

    def test_generate_mock_downstream_warehouse_schema(self, client: BasecampClient) -> None:
        """Test downstream generation for warehouse schema."""
        result = client._generate_mock_downstream("iceberg.warehouse.user_events", depth=-1)
        assert "nodes" in result
        # Warehouse schema should generate reporting dependency
        assert len(result["nodes"]) >= 1

    def test_generate_mock_downstream_unknown_schema(self, client: BasecampClient) -> None:
        """Test downstream generation for unknown schema."""
        result = client._generate_mock_downstream("iceberg.unknown.table", depth=-1)
        assert "nodes" in result
        # Unknown schema should not generate dependents
        assert len(result["nodes"]) == 0

    def test_generate_mock_downstream_depth_one(self, client: BasecampClient) -> None:
        """Test downstream generation with depth=1."""
        result = client._generate_mock_downstream("iceberg.analytics.daily_clicks", depth=1)
        # With depth=1, should only have first level
        assert len(result["nodes"]) >= 1
        # Should not have metric dependency (depth=2)
        metric_nodes = [n for n in result["nodes"] if "metric" in n["name"]]
        assert len(metric_nodes) == 0

    def test_generate_mock_downstream_depth_two(self, client: BasecampClient) -> None:
        """Test downstream generation with depth>=2 includes metric."""
        result = client._generate_mock_downstream("iceberg.analytics.daily_clicks", depth=2)
        # With depth>=2, should have metric dependency
        metric_nodes = [n for n in result["nodes"] if "metric" in n["name"]]
        assert len(metric_nodes) >= 1

    def test_generate_mock_downstream_invalid_name_format(self, client: BasecampClient) -> None:
        """Test downstream generation with invalid name format."""
        result = client._generate_mock_downstream("invalid", depth=-1)
        assert "nodes" in result
        # Invalid name format should not generate dependents
        assert len(result["nodes"]) == 0

    def test_generate_mock_downstream_edge_structure(self, client: BasecampClient) -> None:
        """Test downstream edges have correct structure."""
        result = client._generate_mock_downstream("iceberg.analytics.daily_clicks", depth=-1)
        for edge in result["edges"]:
            assert "source" in edge
            assert "target" in edge
            assert "edge_type" in edge
            assert edge["edge_type"] == "direct"


class TestBasecampClientQualityTest:
    """Tests for quality test functionality."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    def test_execute_quality_test_basic(self, client: BasecampClient) -> None:
        """Test basic quality test execution."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="not_null_test",
            test_type="not_null",
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["status"] == "pass"
        assert response.data["failed_rows"] == 0
        assert isinstance(response.data["failed_samples"], list)
        assert response.data["execution_time_ms"] > 0

    def test_execute_quality_test_with_columns(self, client: BasecampClient) -> None:
        """Test quality test execution with columns."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="not_null_id",
            test_type="not_null",
            columns=["id", "user_id"],
        )
        assert response.success is True
        assert response.data["status"] == "pass"

    def test_execute_quality_test_with_params(self, client: BasecampClient) -> None:
        """Test quality test execution with params."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="value_range_test",
            test_type="accepted_values",
            params={"values": ["active", "inactive", "pending"]},
        )
        assert response.success is True
        assert response.data["status"] == "pass"

    def test_execute_quality_test_unique_type(self, client: BasecampClient) -> None:
        """Test unique type quality test."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="unique_id",
            test_type="unique",
            columns=["id"],
        )
        assert response.success is True
        assert response.data["status"] == "pass"

    def test_execute_quality_test_severity_warn(self, client: BasecampClient) -> None:
        """Test quality test with warn severity."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="freshness_test",
            test_type="freshness",
            severity="warn",
        )
        assert response.success is True
        assert response.data is not None

    def test_execute_quality_test_rendered_sql(self, client: BasecampClient) -> None:
        """Test quality test includes rendered SQL."""
        response = client.execute_quality_test(
            resource_name="iceberg.analytics.daily_clicks",
            test_name="null_check",
            test_type="not_null",
        )
        assert response.success is True
        assert "rendered_sql" in response.data
        assert "not_null" in response.data["rendered_sql"]
        assert "iceberg.analytics.daily_clicks" in response.data["rendered_sql"]

    def test_execute_quality_test_all_params(self, client: BasecampClient) -> None:
        """Test quality test with all parameters."""
        response = client.execute_quality_test(
            resource_name="iceberg.warehouse.user_events",
            test_name="comprehensive_test",
            test_type="custom",
            columns=["event_type", "user_id", "timestamp"],
            params={"min_rows": 1000, "max_null_pct": 0.01},
            severity="error",
        )
        assert response.success is True
        assert response.data["status"] == "pass"


class TestBasecampClientFilterOperations:
    """Tests for filter operations in list methods."""

    @pytest.fixture
    def client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    # Owner filter tests

    def test_list_metrics_with_owner_filter(self, client: BasecampClient) -> None:
        """Test listing metrics with owner filter."""
        response = client.list_metrics(owner="analyst")
        assert response.success is True
        assert isinstance(response.data, list)
        # Should match the owner containing 'analyst'
        for metric in response.data:
            assert "analyst" in metric.get("owner", "")

    def test_list_metrics_with_owner_no_match(self, client: BasecampClient) -> None:
        """Test listing metrics with owner filter no match."""
        response = client.list_metrics(owner="nonexistent_owner")
        assert response.success is True
        assert isinstance(response.data, list)
        assert len(response.data) == 0

    def test_list_datasets_with_owner_filter(self, client: BasecampClient) -> None:
        """Test listing datasets with owner filter."""
        response = client.list_datasets(owner="engineer")
        assert response.success is True
        assert isinstance(response.data, list)
        for dataset in response.data:
            assert "engineer" in dataset.get("owner", "")

    def test_list_datasets_with_search(self, client: BasecampClient) -> None:
        """Test listing datasets with search."""
        response = client.list_datasets(search="click")
        assert response.success is True
        assert isinstance(response.data, list)
        # Should find datasets with 'click' in name or description
        for dataset in response.data:
            name_lower = dataset.get("name", "").lower()
            desc_lower = dataset.get("description", "").lower()
            assert "click" in name_lower or "click" in desc_lower

    def test_list_metrics_combined_filters(self, client: BasecampClient) -> None:
        """Test listing metrics with combined filters."""
        response = client.list_metrics(tag="reporting", search="user")
        assert response.success is True
        assert isinstance(response.data, list)

    def test_list_datasets_combined_filters(self, client: BasecampClient) -> None:
        """Test listing datasets with combined filters."""
        response = client.list_datasets(tag="feed", owner="engineer")
        assert response.success is True
        assert isinstance(response.data, list)


class TestBasecampClientExecutionAPIs:
    """Tests for server-side execution API methods."""

    @pytest.fixture
    def mock_client(self) -> BasecampClient:
        """Create a mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=True)

    @pytest.fixture
    def real_client(self) -> BasecampClient:
        """Create a non-mock client."""
        config = ServerConfig(url="http://localhost:8081")
        return BasecampClient(config, mock_mode=False)

    # execute_rendered_dataset tests

    def test_execute_rendered_dataset_mock(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_dataset in mock mode."""
        response = mock_client.execute_rendered_dataset(
            rendered_sql="SELECT * FROM my_table LIMIT 10",
            resource_name="my_dataset",
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["execution_id"] == "exec-mock-dataset-001"
        assert response.data["status"] == "COMPLETED"
        assert isinstance(response.data["rows"], list)
        assert response.data["row_count"] == 2
        assert response.data["duration_seconds"] > 0
        assert response.data["rendered_sql"] == "SELECT * FROM my_table LIMIT 10"

    def test_execute_rendered_dataset_with_all_params(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_dataset with all parameters."""
        response = mock_client.execute_rendered_dataset(
            rendered_sql="SELECT id, name FROM users WHERE created_at > '2025-01-01'",
            resource_name="user_report",
            parameters={"date": "2025-01-01"},
            execution_timeout=600,
            execution_limit=1000,
            transpile_source_dialect="trino",
            transpile_target_dialect="bigquery",
            transpile_used_server_policy=True,
            original_spec={"name": "user_report", "type": "Dataset"},
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["status"] == "COMPLETED"

    def test_execute_rendered_dataset_non_mock(self, real_client: BasecampClient) -> None:
        """Test execute_rendered_dataset returns 501 in non-mock mode."""
        response = real_client.execute_rendered_dataset(
            rendered_sql="SELECT 1",
        )
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    # execute_rendered_metric tests

    def test_execute_rendered_metric_mock(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_metric in mock mode."""
        response = mock_client.execute_rendered_metric(
            rendered_sql="SELECT date, COUNT(*) as dau FROM users GROUP BY date",
            resource_name="daily_active_users",
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["execution_id"] == "exec-mock-metric-001"
        assert response.data["status"] == "COMPLETED"
        assert isinstance(response.data["rows"], list)
        assert response.data["row_count"] == 2
        assert response.data["duration_seconds"] > 0

    def test_execute_rendered_metric_with_all_params(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_metric with all parameters."""
        response = mock_client.execute_rendered_metric(
            rendered_sql="SELECT SUM(revenue) FROM orders",
            resource_name="total_revenue",
            parameters={"start_date": "2025-01-01", "end_date": "2025-01-31"},
            execution_timeout=300,
            execution_limit=100,
            transpile_source_dialect="trino",
            transpile_target_dialect="bigquery",
            transpile_used_server_policy=False,
            original_spec={"name": "total_revenue", "type": "Metric"},
        )
        assert response.success is True
        assert response.data["status"] == "COMPLETED"

    def test_execute_rendered_metric_non_mock(self, real_client: BasecampClient) -> None:
        """Test execute_rendered_metric returns 501 in non-mock mode."""
        response = real_client.execute_rendered_metric(
            rendered_sql="SELECT 1",
        )
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    # execute_rendered_quality tests

    def test_execute_rendered_quality_mock(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_quality in mock mode."""
        tests = [
            {"name": "not_null_id", "type": "not_null", "rendered_sql": "SELECT COUNT(*) FROM t WHERE id IS NULL"},
            {"name": "unique_email", "type": "unique", "rendered_sql": "SELECT email, COUNT(*) FROM t GROUP BY email HAVING COUNT(*) > 1"},
        ]
        response = mock_client.execute_rendered_quality(
            resource_name="user_events",
            tests=tests,
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["execution_id"] == "exec-mock-quality-001"
        assert response.data["status"] == "COMPLETED"
        assert response.data["total_tests"] == 2
        assert response.data["passed_tests"] == 2
        assert response.data["failed_tests"] == 0
        assert len(response.data["results"]) == 2

        # Check individual test results
        for result in response.data["results"]:
            assert result["passed"] is True
            assert result["failed_count"] == 0
            assert result["duration_ms"] > 0

    def test_execute_rendered_quality_with_transpile(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_quality with transpilation options."""
        tests = [
            {"name": "freshness_check", "type": "freshness", "rendered_sql": "SELECT MAX(updated_at) FROM events"},
        ]
        response = mock_client.execute_rendered_quality(
            resource_name="events_table",
            tests=tests,
            execution_timeout=600,
            transpile_source_dialect="trino",
            transpile_target_dialect="bigquery",
        )
        assert response.success is True
        assert response.data["total_tests"] == 1
        assert response.data["results"][0]["test_name"] == "freshness_check"

    def test_execute_rendered_quality_empty_tests(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_quality with empty tests list."""
        response = mock_client.execute_rendered_quality(
            resource_name="my_table",
            tests=[],
        )
        assert response.success is True
        assert response.data["total_tests"] == 0
        assert response.data["passed_tests"] == 0
        assert response.data["failed_tests"] == 0
        assert response.data["results"] == []

    def test_execute_rendered_quality_non_mock(self, real_client: BasecampClient) -> None:
        """Test execute_rendered_quality returns 501 in non-mock mode."""
        response = real_client.execute_rendered_quality(
            resource_name="my_table",
            tests=[{"name": "test1", "type": "not_null", "rendered_sql": "SELECT 1"}],
        )
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"

    # execute_rendered_sql tests

    def test_execute_rendered_sql_mock(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_sql in mock mode."""
        response = mock_client.execute_rendered_sql(
            sql="SELECT * FROM catalog.schema.table LIMIT 100",
        )
        assert response.success is True
        assert response.data is not None
        assert response.data["execution_id"] == "exec-mock-sql-001"
        assert response.data["status"] == "COMPLETED"
        assert isinstance(response.data["rows"], list)
        assert response.data["row_count"] == 2
        assert response.data["duration_seconds"] > 0
        assert response.data["rendered_sql"] == "SELECT * FROM catalog.schema.table LIMIT 100"

    def test_execute_rendered_sql_with_all_params(self, mock_client: BasecampClient) -> None:
        """Test execute_rendered_sql with all parameters."""
        response = mock_client.execute_rendered_sql(
            sql="SELECT id, name FROM users WHERE status = :status",
            parameters={"status": "active"},
            execution_timeout=120,
            execution_limit=500,
            target_dialect="bigquery",
        )
        assert response.success is True
        assert response.data["status"] == "COMPLETED"

    def test_execute_rendered_sql_non_mock(self, real_client: BasecampClient) -> None:
        """Test execute_rendered_sql returns 501 in non-mock mode."""
        response = real_client.execute_rendered_sql(
            sql="SELECT 1",
        )
        assert response.success is False
        assert response.status_code == 501
        assert response.error == "Real API not implemented yet"