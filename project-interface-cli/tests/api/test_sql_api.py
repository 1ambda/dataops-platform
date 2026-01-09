"""Tests for dli.api.sql module.

Covers:
- SqlAPI initialization with context
- Mock mode operations
- List worksheets with filters
- Get worksheet details
- Update worksheets
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
    SqlTeamNotFoundError,
    SqlWorksheetNotFoundError,
    SqlUpdateFailedError,
)
from dli.models.common import ExecutionContext, ExecutionMode
from dli.models.sql import (
    SqlDialect,
    SqlListResult,
    SqlWorksheetDetail,
    SqlWorksheetInfo,
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


class TestSqlAPIListWorksheets:
    """Tests for SqlAPI.list_worksheets() method."""

    def test_list_worksheets_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets in mock mode returns valid result."""
        result = mock_api.list_worksheets()

        assert isinstance(result, SqlListResult)
        assert isinstance(result.worksheets, list)
        assert isinstance(result.total, int)
        assert result.total >= 0

    def test_list_worksheets_returns_worksheet_info(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets returns SqlWorksheetInfo items."""
        result = mock_api.list_worksheets()

        for worksheet in result.worksheets:
            assert isinstance(worksheet, SqlWorksheetInfo)
            assert isinstance(worksheet.id, int)
            assert isinstance(worksheet.name, str)
            assert isinstance(worksheet.dialect, SqlDialect)
            assert isinstance(worksheet.updated_at, datetime)

    def test_list_worksheets_with_team_filter(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets with team filter."""
        result = mock_api.list_worksheets(team="marketing")

        assert isinstance(result, SqlListResult)

    def test_list_worksheets_with_folder_filter(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets with folder filter."""
        result = mock_api.list_worksheets(folder="Analytics")

        assert isinstance(result, SqlListResult)

    def test_list_worksheets_with_starred_filter(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets with starred filter."""
        result = mock_api.list_worksheets(starred=True)

        assert isinstance(result, SqlListResult)

    def test_list_worksheets_with_pagination(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets with limit and offset."""
        result = mock_api.list_worksheets(limit=10, offset=5)

        assert isinstance(result, SqlListResult)
        assert result.limit == 10
        assert result.offset == 5

    def test_list_worksheets_combined_filters(self, mock_api: SqlAPI) -> None:
        """Test list_worksheets with multiple filters combined."""
        result = mock_api.list_worksheets(
            team="marketing",
            folder="Campaign Analytics",
            starred=True,
            limit=5,
            offset=0,
        )

        assert isinstance(result, SqlListResult)

    def test_list_worksheets_team_not_found(self, server_context: ExecutionContext) -> None:
        """Test list_worksheets raises error when team not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=False,
            error="Team not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlTeamNotFoundError) as exc_info:
            api.list_worksheets(team="nonexistent")

        assert "nonexistent" in str(exc_info.value)

    def test_list_worksheets_server_error(self, server_context: ExecutionContext) -> None:
        """Test list_worksheets raises error on server failure."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "marketing"},
        )
        mock_client.sql_list_worksheets.return_value = ServerResponse(
            success=False,
            error="Server error",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlTeamNotFoundError):
            api.list_worksheets(team="marketing")

    def test_list_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlListResult is immutable."""
        result = mock_api.list_worksheets()

        with pytest.raises(Exception):
            result.total = 100  # type: ignore[misc]


class TestSqlAPIGet:
    """Tests for SqlAPI.get() method."""

    def test_get_worksheet_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test get returns SqlWorksheetDetail in mock mode."""
        result = mock_api.get(worksheet_id=1)

        assert isinstance(result, SqlWorksheetDetail)
        assert result.id == 1
        assert isinstance(result.sql, str)
        assert len(result.sql) > 0

    def test_get_worksheet_returns_full_detail(self, mock_api: SqlAPI) -> None:
        """Test get returns complete worksheet details."""
        result = mock_api.get(worksheet_id=1)

        assert isinstance(result, SqlWorksheetDetail)
        assert isinstance(result.id, int)
        assert isinstance(result.name, str)
        assert isinstance(result.team, str)
        assert isinstance(result.dialect, SqlDialect)
        assert isinstance(result.sql, str)
        assert isinstance(result.created_at, datetime)
        assert isinstance(result.updated_at, datetime)
        assert isinstance(result.created_by, str)
        assert isinstance(result.updated_by, str)

    def test_get_worksheet_with_team(self, mock_api: SqlAPI) -> None:
        """Test get with team option."""
        result = mock_api.get(worksheet_id=1, team="default")

        assert isinstance(result, SqlWorksheetDetail)

    def test_get_worksheet_not_found(self, server_context: ExecutionContext) -> None:
        """Test get raises error when worksheet not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_get_worksheet.return_value = ServerResponse(
            success=False,
            error="Worksheet not found",
            status_code=404,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlWorksheetNotFoundError) as exc_info:
            api.get(worksheet_id=999, team="default")

        assert exc_info.value.worksheet_id == 999

    def test_get_worksheet_access_denied(self, server_context: ExecutionContext) -> None:
        """Test get raises error when access denied."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_get_worksheet.return_value = ServerResponse(
            success=False,
            error="Access denied",
            status_code=403,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlAccessDeniedError) as exc_info:
            api.get(worksheet_id=1, team="default")

        assert exc_info.value.worksheet_id == 1

    def test_get_worksheet_team_not_found(self, server_context: ExecutionContext) -> None:
        """Test get raises error when team not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=False,
            error="Team not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlTeamNotFoundError):
            api.get(worksheet_id=1, team="nonexistent")

    def test_get_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlWorksheetDetail is immutable."""
        result = mock_api.get(worksheet_id=1)

        with pytest.raises(Exception):
            result.sql = "modified"  # type: ignore[misc]


class TestSqlAPIPut:
    """Tests for SqlAPI.put() method."""

    def test_put_worksheet_mock_mode(self, mock_api: SqlAPI) -> None:
        """Test put returns SqlUpdateResult in mock mode."""
        result = mock_api.put(worksheet_id=1, sql="SELECT * FROM users")

        assert isinstance(result, SqlUpdateResult)
        assert result.id == 1
        assert isinstance(result.updated_at, datetime)

    def test_put_worksheet_returns_update_result(self, mock_api: SqlAPI) -> None:
        """Test put returns complete update result."""
        result = mock_api.put(worksheet_id=1, sql="SELECT 1")

        assert isinstance(result, SqlUpdateResult)
        assert isinstance(result.id, int)
        assert isinstance(result.name, str)
        assert isinstance(result.updated_at, datetime)
        assert isinstance(result.updated_by, str)

    def test_put_worksheet_with_team(self, mock_api: SqlAPI) -> None:
        """Test put with team option."""
        result = mock_api.put(worksheet_id=1, sql="SELECT 1", team="default")

        assert isinstance(result, SqlUpdateResult)

    def test_put_worksheet_not_found(self, server_context: ExecutionContext) -> None:
        """Test put raises error when worksheet not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_worksheet.return_value = ServerResponse(
            success=False,
            error="Worksheet not found",
            status_code=404,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlWorksheetNotFoundError) as exc_info:
            api.put(worksheet_id=999, sql="SELECT 1", team="default")

        assert exc_info.value.worksheet_id == 999

    def test_put_worksheet_access_denied(self, server_context: ExecutionContext) -> None:
        """Test put raises error when access denied."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_worksheet.return_value = ServerResponse(
            success=False,
            error="Access denied",
            status_code=403,
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlAccessDeniedError) as exc_info:
            api.put(worksheet_id=1, sql="SELECT 1", team="default")

        assert exc_info.value.worksheet_id == 1

    def test_put_worksheet_update_failed(self, server_context: ExecutionContext) -> None:
        """Test put raises error when update fails."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 1, "name": "default"},
        )
        mock_client.sql_update_worksheet.return_value = ServerResponse(
            success=False,
            error="Database error",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlUpdateFailedError) as exc_info:
            api.put(worksheet_id=1, sql="SELECT 1", team="default")

        assert exc_info.value.worksheet_id == 1

    def test_put_worksheet_team_not_found(self, server_context: ExecutionContext) -> None:
        """Test put raises error when team not found."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=False,
            error="Team not found",
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlTeamNotFoundError):
            api.put(worksheet_id=1, sql="SELECT 1", team="nonexistent")

    def test_put_result_is_frozen(self, mock_api: SqlAPI) -> None:
        """Test that SqlUpdateResult is immutable."""
        result = mock_api.put(worksheet_id=1, sql="SELECT 1")

        with pytest.raises(Exception):
            result.id = 999  # type: ignore[misc]


class TestSqlAPITeamResolution:
    """Tests for SqlAPI team resolution logic."""

    def test_resolve_team_mock_mode_default(self, mock_api: SqlAPI) -> None:
        """Test team resolution uses default in mock mode."""
        # Should work without specifying team
        result = mock_api.list_worksheets()
        assert isinstance(result, SqlListResult)

    def test_resolve_team_mock_mode_explicit(self, mock_api: SqlAPI) -> None:
        """Test team resolution with explicit team in mock mode."""
        result = mock_api.list_worksheets(team="custom_team")
        assert isinstance(result, SqlListResult)

    def test_resolve_team_server_success(self, server_context: ExecutionContext) -> None:
        """Test team resolution succeeds when team exists."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_get_by_name.return_value = ServerResponse(
            success=True,
            data={"id": 42, "name": "marketing"},
        )
        mock_client.sql_list_worksheets.return_value = ServerResponse(
            success=True,
            data={"worksheets": [], "total": 0},
        )

        api = SqlAPI(context=server_context, client=mock_client)
        result = api.list_worksheets(team="marketing")

        assert isinstance(result, SqlListResult)
        mock_client.team_get_by_name.assert_called_with("marketing")

    def test_resolve_team_no_default(self, server_context: ExecutionContext) -> None:
        """Test team resolution fails when no team specified and no default."""
        mock_client = MagicMock(spec=BasecampClient)
        mock_client.mock_mode = False
        mock_client.team_list.return_value = ServerResponse(
            success=True,
            data={"teams": []},  # No teams available
        )

        api = SqlAPI(context=server_context, client=mock_client)

        with pytest.raises(SqlTeamNotFoundError):
            api.list_worksheets()


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


class TestSqlWorksheetInfoModel:
    """Tests for SqlWorksheetInfo model."""

    def test_worksheet_info_creation(self) -> None:
        """Test SqlWorksheetInfo model creation."""
        now = datetime.now(timezone.utc)
        info = SqlWorksheetInfo(
            id=1,
            name="daily_revenue",
            team="analytics",
            folder="Reports",
            dialect=SqlDialect.BIGQUERY,
            starred=True,
            updated_at=now,
            updated_by="alice",
        )

        assert info.id == 1
        assert info.name == "daily_revenue"
        assert info.team == "analytics"
        assert info.folder == "Reports"
        assert info.dialect == SqlDialect.BIGQUERY
        assert info.starred is True
        assert info.updated_at == now
        assert info.updated_by == "alice"

    def test_worksheet_info_optional_folder(self) -> None:
        """Test SqlWorksheetInfo without folder."""
        now = datetime.now(timezone.utc)
        info = SqlWorksheetInfo(
            id=1,
            name="query",
            team="default",
            dialect=SqlDialect.TRINO,
            updated_at=now,
            updated_by="bob",
        )

        assert info.folder is None

    def test_worksheet_info_frozen(self) -> None:
        """Test SqlWorksheetInfo is frozen (immutable)."""
        now = datetime.now(timezone.utc)
        info = SqlWorksheetInfo(
            id=1,
            name="query",
            team="default",
            dialect=SqlDialect.TRINO,
            updated_at=now,
            updated_by="bob",
        )

        with pytest.raises(Exception):
            info.name = "modified"  # type: ignore[misc]


class TestSqlWorksheetDetailModel:
    """Tests for SqlWorksheetDetail model."""

    def test_worksheet_detail_creation(self) -> None:
        """Test SqlWorksheetDetail model creation."""
        now = datetime.now(timezone.utc)
        detail = SqlWorksheetDetail(
            id=1,
            name="daily_revenue",
            team="analytics",
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
        assert detail.team == "analytics"
        assert detail.folder == "Reports"
        assert detail.dialect == SqlDialect.BIGQUERY
        assert detail.sql == "SELECT * FROM users"
        assert detail.starred is True
        assert detail.created_at == now
        assert detail.updated_at == now
        assert detail.created_by == "alice"
        assert detail.updated_by == "bob"

    def test_worksheet_detail_frozen(self) -> None:
        """Test SqlWorksheetDetail is frozen (immutable)."""
        now = datetime.now(timezone.utc)
        detail = SqlWorksheetDetail(
            id=1,
            name="query",
            team="default",
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
            worksheets=[],
            total=0,
            offset=0,
            limit=20,
        )

        assert result.worksheets == []
        assert result.total == 0
        assert result.offset == 0
        assert result.limit == 20

    def test_list_result_with_worksheets(self) -> None:
        """Test SqlListResult with worksheets."""
        now = datetime.now(timezone.utc)
        worksheets = [
            SqlWorksheetInfo(
                id=1,
                name="query1",
                team="default",
                dialect=SqlDialect.BIGQUERY,
                updated_at=now,
                updated_by="alice",
            ),
            SqlWorksheetInfo(
                id=2,
                name="query2",
                team="default",
                dialect=SqlDialect.TRINO,
                updated_at=now,
                updated_by="bob",
            ),
        ]
        result = SqlListResult(
            worksheets=worksheets,
            total=2,
            offset=0,
            limit=20,
        )

        assert len(result.worksheets) == 2
        assert result.total == 2

    def test_list_result_frozen(self) -> None:
        """Test SqlListResult is frozen (immutable)."""
        result = SqlListResult(
            worksheets=[],
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
