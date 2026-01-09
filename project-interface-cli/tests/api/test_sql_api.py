"""Tests for dli.api.sql module.

Covers:
- SqlAPI initialization with context
- Mock mode operations
- List snippets with filters
- Get snippet details
- Update snippets
- Error handling for various failure cases
"""

from __future__ import annotations

from datetime import datetime, timezone
from unittest.mock import MagicMock

import pytest

from dli.api.sql import SqlAPI
from dli.core.client import BasecampClient, ServerResponse
from dli.exceptions import (
    SqlAccessDeniedError,
    SqlProjectNotFoundError,
    SqlSnippetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.sql import (
    SqlDialect,
    SqlListResult,
    SqlSnippetDetail,
    SqlSnippetInfo,
    SqlUpdateResult,
)


# === Fixtures ===


@pytest.fixture
def mock_context() -> ExecutionContext:
    """Create mock mode context."""
    return ExecutionContext(execution_mode=ExecutionMode.MOCK)


@pytest.fixture
def server_context() -> ExecutionContext:
    """Create server mode context."""
    return ExecutionContext(
        execution_mode=ExecutionMode.SERVER,
        server_url="http://localhost:8081",
    )


@pytest.fixture
def mock_api(mock_context: ExecutionContext) -> SqlAPI:
    """Create SqlAPI in mock mode."""
    return SqlAPI(context=mock_context)


# === Test Classes ===


class TestSqlAPIInit:
    """Tests for SqlAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = SqlAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = SqlAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_server_context(self) -> None:
        """Test initialization with server context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8081",
        )
        api = SqlAPI(context=ctx)

        assert api.context.execution_mode == ExecutionMode.SERVER
        assert api.context.server_url == "http://localhost:8081"

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(
            server_url="https://test.com",
            execution_mode=ExecutionMode.MOCK,
        )
        api = SqlAPI(context=ctx)

        result = repr(api)

        assert "SqlAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = SqlAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _client should be None before any operation
        assert api._client is None


class TestSqlAPIListSnippets:
    """Tests for SqlAPI.list_snippets() method."""

    def test_list_snippets_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test list_snippets in mock mode returns valid result."""
        result = mock_api.list_snippets()

        assert isinstance(result, SqlListResult)
        assert isinstance(result.snippets, list)
        assert isinstance(result.total, int)
        assert result.total >= 0

    def test_list_snippets_returns_snippet_info(self, mock_api: SqlAPI) -> None:
        """Test list_snippets returns SqlSnippetInfo items."""
        result = mock_api.list_snippets()

        for snippet in result.snippets:
            assert isinstance(snippet, SqlSnippetInfo)
            assert isinstance(snippet.id, int)
            assert isinstance(snippet.name, str)
            assert isinstance(snippet.dialect, SqlDialect)
            assert isinstance(snippet.updated_at, datetime)

    def test_list_snippets_with_project_filter(self, mock_api: SqlAPI) -> None:
        """Test list_snippets with project filter."""
        result = mock_api.list_snippets(project="marketing")

        assert isinstance(result, SqlListResult)

    def test_list_snippets_with_folder_filter(self, mock_api: SqlAPI) -> None:
        """Test list_snippets with folder filter."""
        result = mock_api.list_snippets(folder="Analytics")

        assert isinstance(result, SqlListResult)

    def test_list_snippets_with_starred_filter(self, mock_api: SqlAPI) -> None:
        """Test list_snippets with starred filter."""
        result = mock_api.list_snippets(starred=True)

        assert isinstance(result, SqlListResult)

    def test_list_snippets_with_pagination(self, mock_api: SqlAPI) -> None:
        """Test list_snippets with limit and offset."""
        result = mock_api.list_snippets(limit=10, offset=5)

        assert isinstance(result, SqlListResult)
        assert result.limit == 10
        assert result.offset == 5

    def test_list_snippets_combined_filters(self, mock_api: SqlAPI) -> None:
        """Test list_snippets with multiple filters combined."""
        result = mock_api.list_snippets(
            project="marketing",
            folder="Campaign Analytics",
            starred=True,
            limit=5,
            offset=0,
        )

        assert isinstance(result, SqlListResult)

    def test_list_snippets_project_not_found(self, server_context: ExecutionContext) -> None:
        """Test list_snippets raises error when project not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=False,
            error="Project not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlProjectNotFoundError) as exc_info:
            api.list_snippets(project="nonexistent")

        assert "nonexistent" in str(exc_info.value)

    def test_list_snippets_server_error(self, server_context: ExecutionContext) -> None:
        """Test list_snippets raises error on server failure."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "marketing"},
        )
        mock_client.sql_list_snippets.return_value = ServerResponse(
            success=False,
            error="Server error",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlProjectNotFoundError):
            api.list_snippets(project="marketing")

    def test_list_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlListResult is immutable."""
        result = mock_api.list_snippets()

        with pytest.raises(Exception):
            result.total = 100  # type: ignore[misc]


class TestSqlAPIGet:
    """Tests for SqlAPI.get() method."""

    def test_get_snippet_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test get returns SqlSnippetDetail in mock mode."""
        result = mock_api.get(snippet_id=1)

        assert isinstance(result, SqlSnippetDetail)
        assert result.id == 1
        assert isinstance(result.sql, str)
        assert len(result.sql) > 0

    def test_get_snippet_returns_full_detail(self, mock_api: SqlAPI) -> None:
        """Test get returns complete snippet details."""
        result = mock_api.get(snippet_id=1)

        assert isinstance(result, SqlSnippetDetail)
        assert isinstance(result.id, int)
        assert isinstance(result.name, str)
        assert isinstance(result.project, str)
        assert isinstance(result.dialect, SqlDialect)
        assert isinstance(result.sql, str)
        assert isinstance(result.created_at, datetime)
        assert isinstance(result.updated_at, datetime)
        assert isinstance(result.created_by, str)
        assert isinstance(result.updated_by, str)

    def test_get_snippet_with_project(self, mock_api: SqlAPI) -> None:
        """Test get with project option."""
        result = mock_api.get(snippet_id=1, project="default")

        assert isinstance(result, SqlSnippetDetail)

    def test_get_snippet_not_found(self, server_context: ExecutionContext) -> None:
        """Test get raises error when snippet not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_get_snippet.return_value = ServerResponse(
            success=False,
            error="Snippet not found",
            status_code=404,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlSnippetNotFoundError) as exc_info:
            api.get(snippet_id=999, project="default")

        assert exc_info.value.snippet_id == 999

    def test_get_snippet_access_denied(self, server_context: ExecutionContext) -> None:
        """Test get raises error when access denied."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_get_snippet.return_value = ServerResponse(
            success=False,
            error="Access denied",
            status_code=403,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlAccessDeniedError) as exc_info:
            api.get(snippet_id=1, project="default")

        assert exc_info.value.snippet_id == 1

    def test_get_snippet_project_not_found(self, server_context: ExecutionContext) -> None:
        """Test get raises error when project not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=False,
            error="Project not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlProjectNotFoundError):
            api.get(snippet_id=1, project="nonexistent")

    def test_get_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlSnippetDetail is immutable."""
        result = mock_api.get(snippet_id=1)

        with pytest.raises(Exception):
            result.sql = "modified"  # type: ignore[misc]


class TestSqlAPIPut:
    """Tests for SqlAPI.put() method."""

    def test_put_snippet_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test put returns SqlUpdateResult in mock mode."""
        result = mock_api.put(snippet_id=1, sql="SELECT * FROM users")

        assert isinstance(result, SqlUpdateResult)
        assert result.id == 1
        assert isinstance(result.updated_at, datetime)

    def test_put_snippet_returns_update_result(self, mock_api: SqlAPI) -> None:
        """Test put returns complete update result."""
        result = mock_api.put(snippet_id=1, sql="SELECT 1")

        assert isinstance(result, SqlUpdateResult)
        assert isinstance(result.id, int)
        assert isinstance(result.name, str)
        assert isinstance(result.updated_at, datetime)
        assert isinstance(result.updated_by, str)

    def test_put_snippet_with_project(self, mock_api: SqlAPI) -> None:
        """Test put with project option."""
        result = mock_api.put(snippet_id=1, sql="SELECT 1", project="default")

        assert isinstance(result, SqlUpdateResult)

    def test_put_snippet_not_found(self, server_context: ExecutionContext) -> None:
        """Test put raises error when snippet not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_snippet.return_value = ServerResponse(
            success=False,
            error="Snippet not found",
            status_code=404,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlSnippetNotFoundError) as exc_info:
            api.put(snippet_id=999, sql="SELECT 1", project="default")

        assert exc_info.value.snippet_id == 999

    def test_put_snippet_access_denied(self, server_context: ExecutionContext) -> None:
        """Test put raises error when access denied."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_snippet.return_value = ServerResponse(
            success=False,
            error="Access denied",
            status_code=403,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlAccessDeniedError) as exc_info:
            api.put(snippet_id=1, sql="SELECT 1", project="default")

        assert exc_info.value.snippet_id == 1

    def test_put_snippet_update_failed(self, server_context: ExecutionContext) -> None:
        """Test put raises error when update fails."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_snippet.return_value = ServerResponse(
            success=False,
            error="Database error",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlUpdateFailedError) as exc_info:
            api.put(snippet_id=1, sql="SELECT 1", project="default")

        assert exc_info.value.snippet_id == 1

    def test_put_snippet_project_not_found(self, server_context: ExecutionContext) -> None:
        """Test put raises error when project not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=False,
            error="Project not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlProjectNotFoundError):
            api.put(snippet_id=1, sql="SELECT 1", project="nonexistent")

    def test_put_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlUpdateResult is immutable."""
        result = mock_api.put(snippet_id=1, sql="SELECT 1")

        with pytest.raises(Exception):
            result.id = 999  # type: ignore[misc]


class TestSqlAPIProjectResolution:
    """Tests for SqlAPI project resolution logic."""

    def test_resolve_project_mock_mode_default(self, mock_api: SqlAPI) -> None:
        """Test project resolution uses default in mock mode."""
        # Should work without specifying project
        result = mock_api.list_snippets()
        assert isinstance(result, SqlListResult)

    def test_resolve_project_mock_mode_explicit(self, mock_api: SqlAPI) -> None:
        """Test project resolution with explicit project in mock mode."""
        result = mock_api.list_snippets(project="custom_project")
        assert isinstance(result, SqlListResult)

    def test_resolve_project_server_success(self, server_context: ExecutionContext) -> None:
        """Test project resolution succeeds when project exists."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 42, "name": "marketing"},
        )
        mock_client.sql_list_snippets.return_value = ServerResponse(
            success=True,
            data={"snippets": [], "total": 0},
        )

        api = SqlAPI(context=server_context, client=mock_client)
        result = api.list_snippets(project="marketing")

        assert isinstance(result, SqlListResult)
        mock_client.project_get_by_name.assert_called_with("marketing")

    def test_resolve_project_no_default(self, server_context: ExecutionContext) -> None:
        """Test project resolution fails when no project specified and no default."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.project_list.return_value = ServerResponse(
            success=True,
            data={"projects": []},  # No projects available
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlProjectNotFoundError):
            api.list_snippets()


class TestSqlAPIClientInit:
    """Tests for SqlAPI client initialization."""

    def test_client_uses_context_server_url(self) -> None:
        """Test that client uses server_url from context."""
        ctx = ExecutionContext(
            server_url="https://custom.server.com",
            execution_mode=ExecutionMode.MOCK,
        )
        api = SqlAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "https://custom.server.com"

    def test_client_default_server_url(self) -> None:
        """Test that client uses default URL when not specified."""
        ctx = ExecutionContext(server_url=None, execution_mode=ExecutionMode.MOCK)
        api = SqlAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "http://localhost:8081"

    def test_client_mock_mode_propagated(self) -> None:
        """Test that mock_mode is propagated to client."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = SqlAPI(context=ctx)

        client = api._get_client()

        assert client.mock_mode is True


class TestSqlAPIDependencyInjection:
    """Tests for SqlAPI with injected client."""

    def test_uses_injected_client(self) -> None:
        """Test that injected client is used."""
        mock_client = MagicMock(spec=BasecampClient)
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = SqlAPI(context=ctx, client=mock_client)

        client = api._get_client()

        assert client is mock_client


class TestSqlDialectEnum:
    """Tests for SqlDialect enum."""

    def test_sql_dialect_values(self) -> None:
        """Test SqlDialect enum values."""
        assert SqlDialect.BIGQUERY.value == "bigquery"
        assert SqlDialect.TRINO.value == "trino"
        assert SqlDialect.SPARK.value == "spark"

    def test_sql_dialect_from_string(self) -> None:
        """Test creating SqlDialect from string."""
        assert SqlDialect("bigquery") == SqlDialect.BIGQUERY
        assert SqlDialect("trino") == SqlDialect.TRINO
        assert SqlDialect("spark") == SqlDialect.SPARK


class TestSqlSnippetInfoModel:
    """Tests for SqlSnippetInfo model."""

    def test_snippet_info_creation(self) -> None:
        """Test SqlSnippetInfo model creation."""
        now = datetime.now(timezone.utc)
        info = SqlSnippetInfo(
            id=1,
            name="daily_revenue",
            project="analytics",
            folder="Reports",
            dialect=SqlDialect.BIGQUERY,
            starred=True,
            updated_at=now,
            updated_by="alice",
        )

        assert info.id == 1
        assert info.name == "daily_revenue"
        assert info.project == "analytics"
        assert info.folder == "Reports"
        assert info.dialect == SqlDialect.BIGQUERY
        assert info.starred is True
        assert info.updated_at == now
        assert info.updated_by == "alice"

    def test_snippet_info_optional_folder(self) -> None:
        """Test SqlSnippetInfo without folder."""
        now = datetime.now(timezone.utc)
        info = SqlSnippetInfo(
            id=1,
            name="query",
            project="default",
            dialect=SqlDialect.TRINO,
            updated_at=now,
            updated_by="bob",
        )

        assert info.folder is None

    def test_snippet_info_frozen(self) -> None:
        """Test SqlSnippetInfo is frozen (immutable)."""
        now = datetime.now(timezone.utc)
        info = SqlSnippetInfo(
            id=1,
            name="query",
            project="default",
            dialect=SqlDialect.TRINO,
            updated_at=now,
            updated_by="bob",
        )

        with pytest.raises(Exception):
            info.name = "modified"  # type: ignore[misc]


class TestSqlSnippetDetailModel:
    """Tests for SqlSnippetDetail model."""

    def test_snippet_detail_creation(self) -> None:
        """Test SqlSnippetDetail model creation."""
        now = datetime.now(timezone.utc)
        detail = SqlSnippetDetail(
            id=1,
            name="daily_revenue",
            project="analytics",
            folder="Reports",
            dialect=SqlDialect.BIGQUERY,
            sql="SELECT * FROM users",
            starred=True,
            created_at=now,
            updated_at=now,
            created_by="alice",
            updated_by="bob",
        )

        assert detail.id == 1
        assert detail.name == "daily_revenue"
        assert detail.project == "analytics"
        assert detail.folder == "Reports"
        assert detail.dialect == SqlDialect.BIGQUERY
        assert detail.sql == "SELECT * FROM users"
        assert detail.starred is True
        assert detail.created_at == now
        assert detail.updated_at == now
        assert detail.created_by == "alice"
        assert detail.updated_by == "bob"

    def test_snippet_detail_frozen(self) -> None:
        """Test SqlSnippetDetail is frozen (immutable)."""
        now = datetime.now(timezone.utc)
        detail = SqlSnippetDetail(
            id=1,
            name="query",
            project="default",
            dialect=SqlDialect.TRINO,
            sql="SELECT 1",
            created_at=now,
            updated_at=now,
            created_by="alice",
            updated_by="alice",
        )

        with pytest.raises(Exception):
            detail.sql = "SELECT 2"  # type: ignore[misc]


class TestSqlListResultModel:
    """Tests for SqlListResult model."""

    def test_list_result_creation(self) -> None:
        """Test SqlListResult model creation."""
        result = SqlListResult(
            snippets=[],
            total=0,
            offset=0,
            limit=20,
        )

        assert result.snippets == []
        assert result.total == 0
        assert result.offset == 0
        assert result.limit == 20

    def test_list_result_with_snippets(self) -> None:
        """Test SqlListResult with snippets."""
        now = datetime.now(timezone.utc)
        snippets = [
            SqlSnippetInfo(
                id=1,
                name="query1",
                project="default",
                dialect=SqlDialect.BIGQUERY,
                updated_at=now,
                updated_by="alice",
            ),
            SqlSnippetInfo(
                id=2,
                name="query2",
                project="default",
                dialect=SqlDialect.TRINO,
                updated_at=now,
                updated_by="bob",
            ),
        ]
        result = SqlListResult(
            snippets=snippets,
            total=2,
            offset=0,
            limit=20,
        )

        assert len(result.snippets) == 2
        assert result.total == 2

    def test_list_result_frozen(self) -> None:
        """Test SqlListResult is frozen (immutable)."""
        result = SqlListResult(
            snippets=[],
            total=0,
        )

        with pytest.raises(Exception):
            result.total = 100  # type: ignore[misc]


class TestSqlUpdateResultModel:
    """Tests for SqlUpdateResult model."""

    def test_update_result_creation(self) -> None:
        """Test SqlUpdateResult model creation."""
        now = datetime.now(timezone.utc)
        result = SqlUpdateResult(
            id=1,
            name="daily_revenue",
            updated_at=now,
            updated_by="alice",
        )

        assert result.id == 1
        assert result.name == "daily_revenue"
        assert result.updated_at == now
        assert result.updated_by == "alice"

    def test_update_result_frozen(self) -> None:
        """Test SqlUpdateResult is frozen (immutable)."""
        now = datetime.now(timezone.utc)
        result = SqlUpdateResult(
            id=1,
            name="query",
            updated_at=now,
            updated_by="alice",
        )

        with pytest.raises(Exception):
            result.id = 2  # type: ignore[misc]
