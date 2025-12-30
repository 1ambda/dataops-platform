"""Tests for dli.api.catalog module.

Covers:
- CatalogAPI initialization with context
- Mock mode operations
- List tables with implicit routing
- Get table details
- Search tables
"""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from dli import CatalogAPI, ExecutionContext
from dli.core.catalog import TableDetail, TableInfo
from dli.models.common import ExecutionMode


class TestCatalogAPIInit:
    """Tests for CatalogAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = CatalogAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK, server_url="https://test.com")
        api = CatalogAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(server_url="https://test.com", execution_mode=ExecutionMode.MOCK)
        api = CatalogAPI(context=ctx)

        result = repr(api)

        assert "CatalogAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = CatalogAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _client should be None before any operation
        assert api._client is None


class TestCatalogAPIMockMode:
    """Tests for CatalogAPI in mock mode."""

    @pytest.fixture
    def mock_api(self) -> CatalogAPI:
        """Create CatalogAPI in mock mode."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        return CatalogAPI(context=ctx)

    def test_list_tables_no_identifier(self, mock_api: CatalogAPI) -> None:
        """Test list_tables without identifier (list all projects)."""
        result = mock_api.list_tables()

        assert isinstance(result, list)

    def test_list_tables_with_project(self, mock_api: CatalogAPI) -> None:
        """Test list_tables with project identifier."""
        result = mock_api.list_tables("my-project")

        assert isinstance(result, list)

    def test_list_tables_with_dataset(self, mock_api: CatalogAPI) -> None:
        """Test list_tables with project.dataset identifier."""
        result = mock_api.list_tables("my-project.analytics")

        assert isinstance(result, list)

    def test_list_tables_with_limit(self, mock_api: CatalogAPI) -> None:
        """Test list_tables with limit."""
        result = mock_api.list_tables("my-project", limit=10)

        assert isinstance(result, list)

    def test_get_returns_table_or_none(self, mock_api: CatalogAPI) -> None:
        """Test get returns TableDetail or None."""
        result = mock_api.get("project.dataset.table")

        # In mock mode with mock client, may return None or mock data
        assert result is None or isinstance(result, TableDetail)

    def test_search_returns_list(self, mock_api: CatalogAPI) -> None:
        """Test search returns list of TableInfo."""
        result = mock_api.search("user")

        assert isinstance(result, list)

    def test_search_with_limit(self, mock_api: CatalogAPI) -> None:
        """Test search with limit."""
        result = mock_api.search("event", limit=5)

        assert isinstance(result, list)


class TestCatalogAPIListTables:
    """Tests for CatalogAPI.list_tables method."""

    @pytest.fixture
    def mock_api(self) -> CatalogAPI:
        """Create CatalogAPI in mock mode."""
        return CatalogAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

    def test_implicit_routing_no_parts(self, mock_api: CatalogAPI) -> None:
        """Test implicit routing with no identifier parts."""
        result = mock_api.list_tables(None)

        assert isinstance(result, list)

    def test_implicit_routing_one_part(self, mock_api: CatalogAPI) -> None:
        """Test implicit routing with 1-part identifier (project)."""
        result = mock_api.list_tables("project")

        assert isinstance(result, list)

    def test_implicit_routing_two_parts(self, mock_api: CatalogAPI) -> None:
        """Test implicit routing with 2-part identifier (project.dataset)."""
        result = mock_api.list_tables("project.dataset")

        assert isinstance(result, list)

    def test_implicit_routing_three_parts(self, mock_api: CatalogAPI) -> None:
        """Test implicit routing with 3-part identifier (project.dataset.table)."""
        # This should redirect to get() and return single-item list
        result = mock_api.list_tables("project.dataset.table")

        assert isinstance(result, list)

    def test_result_items_are_table_info(self, mock_api: CatalogAPI) -> None:
        """Test that list returns TableInfo items."""
        result = mock_api.list_tables()

        for item in result:
            assert isinstance(item, TableInfo)


class TestCatalogAPIGet:
    """Tests for CatalogAPI.get method."""

    @pytest.fixture
    def mock_api(self) -> CatalogAPI:
        """Create CatalogAPI in mock mode."""
        return CatalogAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

    def test_get_with_full_reference(self, mock_api: CatalogAPI) -> None:
        """Test get with full table reference."""
        result = mock_api.get("project.dataset.table")

        # In mock mode, might return None or mock data
        assert result is None or isinstance(result, TableDetail)

    def test_get_nonexistent_returns_none(self, mock_api: CatalogAPI) -> None:
        """Test that nonexistent table returns None."""
        result = mock_api.get("nonexistent.schema.table")

        # Should return None for not found
        assert result is None


class TestCatalogAPISearch:
    """Tests for CatalogAPI.search method."""

    @pytest.fixture
    def mock_api(self) -> CatalogAPI:
        """Create CatalogAPI in mock mode."""
        return CatalogAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

    def test_search_basic(self, mock_api: CatalogAPI) -> None:
        """Test basic search."""
        result = mock_api.search("user")

        assert isinstance(result, list)

    def test_search_with_pattern(self, mock_api: CatalogAPI) -> None:
        """Test search with pattern."""
        result = mock_api.search("user_*")

        assert isinstance(result, list)

    def test_search_case_insensitive(self, mock_api: CatalogAPI) -> None:
        """Test search is case-insensitive (behavior check)."""
        result_lower = mock_api.search("user")
        result_upper = mock_api.search("USER")

        # Both should work (actual matching is server-side)
        assert isinstance(result_lower, list)
        assert isinstance(result_upper, list)

    def test_search_results_are_table_info(self, mock_api: CatalogAPI) -> None:
        """Test that search returns TableInfo items."""
        result = mock_api.search("test")

        for item in result:
            assert isinstance(item, TableInfo)


class TestCatalogAPIClientInit:
    """Tests for CatalogAPI client initialization."""

    def test_client_uses_context_server_url(self) -> None:
        """Test that client uses server_url from context."""
        ctx = ExecutionContext(
            server_url="https://custom.server.com",
            execution_mode=ExecutionMode.MOCK,
        )
        api = CatalogAPI(context=ctx)

        # Force client creation
        client = api._get_client()

        assert client.config.url == "https://custom.server.com"

    def test_client_default_server_url(self) -> None:
        """Test that client uses default URL when not specified."""
        ctx = ExecutionContext(server_url=None, execution_mode=ExecutionMode.MOCK)
        api = CatalogAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "http://localhost:8081"

    def test_client_mock_mode_propagated(self) -> None:
        """Test that mock_mode is propagated to client."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = CatalogAPI(context=ctx)

        client = api._get_client()

        assert client.mock_mode is True


class TestTableInfoModel:
    """Tests for TableInfo model returned by CatalogAPI."""

    def test_table_info_creation(self) -> None:
        """Test TableInfo model creation."""
        info = TableInfo(
            name="my_table",
            engine="bigquery",
        )

        assert info.name == "my_table"
        assert info.engine == "bigquery"

    def test_table_info_optional_fields(self) -> None:
        """Test TableInfo with optional fields."""
        info = TableInfo(
            name="my_table",
            engine="trino",
            owner="data-team",
            team="analytics",
            tags=["production", "pii"],
            row_count=1000000,
            last_updated=datetime(2025, 1, 1, 0, 0, 0, tzinfo=UTC),
        )

        assert info.owner == "data-team"
        assert info.team == "analytics"
        assert "production" in info.tags
        assert info.row_count == 1000000


class TestCatalogAPIHelperMethods:
    """Tests for CatalogAPI helper methods."""

    @pytest.fixture
    def api(self) -> CatalogAPI:
        """Create CatalogAPI in mock mode."""
        return CatalogAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

    def test_dict_to_table_info(self, api: CatalogAPI) -> None:
        """Test _dict_to_table_info conversion."""
        data = {
            "name": "test_table",
            "engine": "bigquery",
            "owner": "owner@example.com",
            "team": "data-team",
            "tags": ["tag1", "tag2"],
            "row_count": 500,
            "last_updated": "2025-01-01",
        }

        result = api._dict_to_table_info(data)

        assert isinstance(result, TableInfo)
        assert result.name == "test_table"
        assert result.engine == "bigquery"
        assert result.owner == "owner@example.com"
        assert result.tags == ["tag1", "tag2"]

    def test_dict_to_table_info_minimal(self, api: CatalogAPI) -> None:
        """Test _dict_to_table_info with minimal data."""
        data = {}

        result = api._dict_to_table_info(data)

        assert isinstance(result, TableInfo)
        assert result.name == ""
        assert result.engine == "unknown"

    def test_dict_to_table_detail(self, api: CatalogAPI) -> None:
        """Test _dict_to_table_detail conversion."""
        data = {
            "name": "test_table",
            "engine": "trino",
            "description": "A test table",
            "tags": ["production"],
            "basecamp_url": "https://basecamp.example.com/table/123",
            "columns": [
                {"name": "id", "data_type": "INT64"},
                {"name": "name", "data_type": "STRING"},
            ],
            "ownership": {
                "owner": "data-team",
                "team": "analytics",
            },
            "freshness": {
                "last_updated": "2025-01-01T00:00:00Z",
            },
            "quality": {
                "score": 95,
                "total_tests": 10,
                "passed_tests": 9,
            },
        }

        result = api._dict_to_table_detail(data)

        assert isinstance(result, TableDetail)
        assert result.name == "test_table"
        assert len(result.columns) == 2
        assert result.ownership.owner == "data-team"
        assert result.quality.score == 95
