"""Tests for dli.api.query module.

Covers:
- QueryAPI initialization with context
- Mock mode operations
- List queries with scope-based filtering
- Get query details
- Cancel queries
- Result model properties and validation
"""

from __future__ import annotations

from datetime import datetime, timedelta
from unittest.mock import MagicMock

import pytest

from dli import ExecutionContext, QueryAPI
from dli.core.query.models import (
    AccountType,
    QueryDetail,
    QueryInfo,
    QueryScope,
    QueryState,
    TableReference,
)
from dli.exceptions import (
    ConfigurationError,
    QueryAccessDeniedError,
    QueryCancelError,
    QueryInvalidFilterError,
    QueryNotFoundError,
)
from dli.models.common import ExecutionMode, ResultStatus
from dli.models.query import (
    QueryCancelResult,
    QueryDetailResult,
    QueryListResult,
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
        server_url="http://localhost:8080",
    )


@pytest.fixture
def mock_api(mock_context: ExecutionContext) -> QueryAPI:
    """Create QueryAPI in mock mode."""
    return QueryAPI(context=mock_context)


# === Test Classes ===


class TestQueryAPIInit:
    """Tests for QueryAPI initialization."""

    def test_init_default_context(self) -> None:
        """Test initialization with default context."""
        api = QueryAPI()

        assert api.context is not None
        assert isinstance(api.context, ExecutionContext)

    def test_init_with_context(self) -> None:
        """Test initialization with explicit context."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = QueryAPI(context=ctx)

        assert api.context is ctx
        assert api.context.execution_mode == ExecutionMode.MOCK

    def test_init_with_server_context(self) -> None:
        """Test initialization with server context."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = QueryAPI(context=ctx)

        assert api.context.execution_mode == ExecutionMode.SERVER
        assert api.context.server_url == "http://localhost:8080"

    def test_repr(self) -> None:
        """Test __repr__ returns descriptive string."""
        ctx = ExecutionContext(
            server_url="https://test.com",
            execution_mode=ExecutionMode.MOCK,
        )
        api = QueryAPI(context=ctx)

        result = repr(api)

        assert "QueryAPI" in result
        assert "ExecutionContext" in result

    def test_lazy_client_init(self) -> None:
        """Test that client is not created until needed."""
        api = QueryAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))

        # _client should be None before any operation
        assert api._client is None


class TestQueryAPIListQueries:
    """Tests for QueryAPI.list_queries() method."""

    def test_list_queries_default(self, mock_api: QueryAPI) -> None:
        """Test list_queries with default scope (MY)."""
        result = mock_api.list_queries()

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.queries, list)
        # Default scope is MY, should return current user's queries
        for q in result.queries:
            assert isinstance(q, QueryInfo)
            assert q.account == "current_user@company.com"

    def test_list_queries_system_scope(self, mock_api: QueryAPI) -> None:
        """Test list_queries with SYSTEM scope."""
        result = mock_api.list_queries(scope=QueryScope.SYSTEM)

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        # System scope returns system account queries
        for q in result.queries:
            assert q.account_type == AccountType.SYSTEM

    def test_list_queries_user_scope(self, mock_api: QueryAPI) -> None:
        """Test list_queries with USER scope."""
        result = mock_api.list_queries(scope=QueryScope.USER)

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        # User scope returns personal account queries
        for q in result.queries:
            assert q.account_type == AccountType.PERSONAL

    def test_list_queries_all_scope(self, mock_api: QueryAPI) -> None:
        """Test list_queries with ALL scope."""
        result = mock_api.list_queries(scope=QueryScope.ALL)

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        # All scope should return more queries than MY scope
        assert len(result.queries) >= 0
        assert isinstance(result.total_count, int)

    def test_list_queries_with_account_keyword(self, mock_api: QueryAPI) -> None:
        """Test list_queries with account_keyword filter."""
        result = mock_api.list_queries(
            scope=QueryScope.SYSTEM,
            account_keyword="airflow",
        )

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        for q in result.queries:
            assert "airflow" in q.account.lower()

    def test_list_queries_with_status_filter(self, mock_api: QueryAPI) -> None:
        """Test list_queries with status filter."""
        result = mock_api.list_queries(
            scope=QueryScope.ALL,
            status=QueryState.RUNNING,
        )

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        for q in result.queries:
            assert q.state == QueryState.RUNNING

    def test_list_queries_with_tags(self, mock_api: QueryAPI) -> None:
        """Test list_queries with tag filter."""
        result = mock_api.list_queries(
            scope=QueryScope.ALL,
            tags=["pipeline"],
        )

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        # All queries should have matching tags
        for q in result.queries:
            # Check that at least one tag contains the filter
            assert any("pipeline" in tag.lower() for tag in q.tags)

    def test_list_queries_with_engine_filter(self, mock_api: QueryAPI) -> None:
        """Test list_queries with engine filter."""
        result = mock_api.list_queries(
            scope=QueryScope.ALL,
            engine="bigquery",
        )

        assert isinstance(result, QueryListResult)
        assert result.status == ResultStatus.SUCCESS
        for q in result.queries:
            assert q.engine == "bigquery"

    def test_list_queries_with_pagination(self, mock_api: QueryAPI) -> None:
        """Test list_queries with limit and offset."""
        # First page
        result1 = mock_api.list_queries(scope=QueryScope.ALL, limit=2, offset=0)

        assert isinstance(result1, QueryListResult)
        assert result1.status == ResultStatus.SUCCESS
        assert len(result1.queries) <= 2
        assert isinstance(result1.has_more, bool)

        # Second page
        result2 = mock_api.list_queries(scope=QueryScope.ALL, limit=2, offset=2)

        assert isinstance(result2, QueryListResult)
        assert result2.status == ResultStatus.SUCCESS

    def test_list_queries_mock_mode(self, mock_api: QueryAPI) -> None:
        """Test list_queries returns mock data correctly."""
        result = mock_api.list_queries(scope=QueryScope.ALL)

        assert result.status == ResultStatus.SUCCESS
        assert len(result.queries) > 0
        assert result.total_count > 0

        # Verify mock data structure
        first_query = result.queries[0]
        assert first_query.query_id is not None
        assert first_query.engine in ("bigquery", "trino")
        assert isinstance(first_query.state, QueryState)
        assert isinstance(first_query.account_type, AccountType)

    def test_list_queries_items_are_query_info(self, mock_api: QueryAPI) -> None:
        """Test that list returns QueryInfo items."""
        result = mock_api.list_queries(scope=QueryScope.ALL)

        for query in result.queries:
            assert isinstance(query, QueryInfo)
            assert query.query_id is not None
            assert isinstance(query.started_at, datetime)

    def test_list_result_is_frozen(self, mock_api: QueryAPI) -> None:
        """Test that QueryListResult is immutable."""
        result = mock_api.list_queries()

        with pytest.raises(Exception):
            result.total_count = 100  # type: ignore[misc]


class TestQueryAPIGet:
    """Tests for QueryAPI.get() method."""

    def test_get_existing_query(self, mock_api: QueryAPI) -> None:
        """Test get returns QueryDetail for existing query."""
        result = mock_api.get("bq_job_abc123")

        assert result is not None
        assert isinstance(result, QueryDetail)
        assert result.query_id == "bq_job_abc123"
        assert result.engine == "bigquery"
        assert isinstance(result.state, QueryState)
        assert result.account == "current_user@company.com"
        assert result.account_type == AccountType.PERSONAL

    def test_get_not_found(self, mock_api: QueryAPI) -> None:
        """Test get raises QueryNotFoundError for missing query."""
        with pytest.raises(QueryNotFoundError) as exc_info:
            mock_api.get("nonexistent_query_id")

        assert "nonexistent_query_id" in str(exc_info.value)
        assert exc_info.value.query_id == "nonexistent_query_id"

    def test_get_with_full_query(self, mock_api: QueryAPI) -> None:
        """Test get with include_full_query=True returns query text."""
        result = mock_api.get("bq_job_abc123", include_full_query=True)

        assert result is not None
        assert isinstance(result, QueryDetail)
        assert result.query_text is not None
        assert len(result.query_text) > 0
        assert "SELECT" in result.query_text

    def test_get_returns_query_detail_properties(self, mock_api: QueryAPI) -> None:
        """Test that returned QueryDetail has expected properties."""
        result = mock_api.get("bq_job_abc123")

        assert result is not None
        # Timing properties
        assert result.started_at is not None
        assert isinstance(result.started_at, datetime)

        # Resource properties
        assert result.bytes_processed is not None or result.bytes_processed is None
        assert result.bytes_billed is not None or result.bytes_billed is None

        # Table references
        assert isinstance(result.tables_used, list)

    def test_get_result_existing(self, mock_api: QueryAPI) -> None:
        """Test get_result returns success for existing query."""
        result = mock_api.get_result("bq_job_abc123")

        assert isinstance(result, QueryDetailResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.query is not None
        assert isinstance(result.query, QueryDetail)
        assert result.query.query_id == "bq_job_abc123"

    def test_get_result_not_found(self, mock_api: QueryAPI) -> None:
        """Test get_result returns failure for missing query."""
        result = mock_api.get_result("nonexistent_query_id")

        assert isinstance(result, QueryDetailResult)
        assert result.status == ResultStatus.FAILURE
        assert result.query is None
        assert result.error_message is not None
        assert "not found" in result.error_message.lower()

    def test_get_result_is_frozen(self, mock_api: QueryAPI) -> None:
        """Test that QueryDetailResult is immutable."""
        result = mock_api.get_result("bq_job_abc123")

        with pytest.raises(Exception):
            result.status = ResultStatus.FAILURE  # type: ignore[misc]


class TestQueryAPICancel:
    """Tests for QueryAPI.cancel() method."""

    def test_cancel_by_query_id(self, mock_api: QueryAPI) -> None:
        """Test cancel with specific query_id."""
        result = mock_api.cancel(query_id="airflow_job_001")

        assert isinstance(result, QueryCancelResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.cancelled_count >= 0
        assert isinstance(result.queries, list)

    def test_cancel_by_user(self, mock_api: QueryAPI) -> None:
        """Test cancel with user account name."""
        result = mock_api.cancel(user="airflow-prod")

        assert isinstance(result, QueryCancelResult)
        assert result.status == ResultStatus.SUCCESS
        assert isinstance(result.cancelled_count, int)

    def test_cancel_dry_run(self, mock_api: QueryAPI) -> None:
        """Test cancel with dry_run=True doesn't actually cancel."""
        result = mock_api.cancel(query_id="airflow_job_001", dry_run=True)

        assert isinstance(result, QueryCancelResult)
        assert result.status == ResultStatus.SUCCESS
        assert result.dry_run is True
        # In dry run, cancelled_count should be 0 (nothing actually cancelled)
        assert result.cancelled_count == 0
        # But should still return what would be cancelled
        assert isinstance(result.queries, list)

    def test_cancel_both_params_error(self, mock_api: QueryAPI) -> None:
        """Test cancel raises error when both query_id and user provided."""
        with pytest.raises(QueryInvalidFilterError) as exc_info:
            mock_api.cancel(query_id="some_id", user="some_user")

        assert "Cannot specify both" in str(exc_info.value)

    def test_cancel_neither_params_error(self, mock_api: QueryAPI) -> None:
        """Test cancel raises error when neither query_id nor user provided."""
        with pytest.raises(QueryInvalidFilterError) as exc_info:
            mock_api.cancel()

        assert "Must specify either" in str(exc_info.value)

    def test_cancel_not_found(self, mock_api: QueryAPI) -> None:
        """Test cancel raises error for non-existent query."""
        with pytest.raises(QueryNotFoundError) as exc_info:
            mock_api.cancel(query_id="nonexistent_query_id")

        assert "nonexistent_query_id" in str(exc_info.value)

    def test_cancel_result_is_frozen(self, mock_api: QueryAPI) -> None:
        """Test that QueryCancelResult is immutable."""
        result = mock_api.cancel(user="airflow-prod", dry_run=True)

        with pytest.raises(Exception):
            result.cancelled_count = 100  # type: ignore[misc]


class TestQueryAPIServerMode:
    """Tests for QueryAPI in server mode."""

    def test_mock_mode_does_not_require_server_url(self) -> None:
        """Test that mock mode doesn't require server_url."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK, server_url=None)
        api = QueryAPI(context=ctx)

        # Should not raise
        client = api._get_client()
        assert client is not None
        assert client.mock_mode is True

    def test_server_mode_creates_client_with_url(self) -> None:
        """Test that server mode creates client with provided URL."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.SERVER,
            server_url="http://localhost:8080",
        )
        api = QueryAPI(context=ctx)

        client = api._get_client()

        assert client is not None
        assert client.config.url == "http://localhost:8080"


class TestQueryAPIDependencyInjection:
    """Tests for QueryAPI with injected client."""

    def test_uses_injected_client(self) -> None:
        """Test that injected client is used."""
        mock_client = MagicMock()
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = QueryAPI(context=ctx, client=mock_client)

        # Access private method to get client
        client = api._get_client()

        assert client is mock_client


class TestQueryResultModels:
    """Tests for query result model properties and validation."""

    def test_query_list_result_frozen(self) -> None:
        """Test QueryListResult is frozen (immutable)."""
        result = QueryListResult(
            queries=[],
            total_count=0,
            has_more=False,
            status=ResultStatus.SUCCESS,
        )

        with pytest.raises(Exception):
            result.queries = []  # type: ignore[misc]

    def test_query_detail_result_frozen(self) -> None:
        """Test QueryDetailResult is frozen."""
        result = QueryDetailResult(
            query=None,
            status=ResultStatus.FAILURE,
            error_message="Not found",
        )

        with pytest.raises(Exception):
            result.status = ResultStatus.SUCCESS  # type: ignore[misc]

    def test_query_cancel_result_frozen(self) -> None:
        """Test QueryCancelResult is frozen."""
        result = QueryCancelResult(
            cancelled_count=0,
            queries=[],
            dry_run=False,
            status=ResultStatus.SUCCESS,
        )

        with pytest.raises(Exception):
            result.cancelled_count = 10  # type: ignore[misc]


class TestQueryInfoModel:
    """Tests for QueryInfo model properties."""

    def test_query_info_is_running(self) -> None:
        """Test QueryInfo.is_running property for RUNNING state."""
        info = QueryInfo(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.RUNNING,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )

        assert info.is_running is True
        assert info.is_terminal is False

    def test_query_info_is_running_pending(self) -> None:
        """Test QueryInfo.is_running property for PENDING state."""
        info = QueryInfo(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.PENDING,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )

        assert info.is_running is True
        assert info.is_terminal is False

    def test_query_info_is_terminal_success(self) -> None:
        """Test QueryInfo.is_terminal property for SUCCESS state."""
        info = QueryInfo(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )

        assert info.is_terminal is True
        assert info.is_running is False

    def test_query_info_is_terminal_failed(self) -> None:
        """Test QueryInfo.is_terminal property for FAILED state."""
        info = QueryInfo(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.FAILED,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
            error_message="Quota exceeded",
        )

        assert info.is_terminal is True
        assert info.is_running is False
        assert info.error_message is not None

    def test_query_info_is_terminal_cancelled(self) -> None:
        """Test QueryInfo.is_terminal property for CANCELLED state."""
        info = QueryInfo(
            query_id="test_001",
            engine="trino",
            state=QueryState.CANCELLED,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )

        assert info.is_terminal is True
        assert info.is_running is False


class TestQueryDetailModel:
    """Tests for QueryDetail model properties."""

    def test_query_detail_tables_read(self) -> None:
        """Test QueryDetail.tables_read property."""
        detail = QueryDetail(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
            tables_used=[
                TableReference(name="analytics.users", operation="read"),
                TableReference(name="analytics.events", operation="read", alias="e"),
                TableReference(name="analytics.output", operation="write"),
            ],
        )

        tables_read = detail.tables_read
        assert len(tables_read) == 2
        assert all(t.operation == "read" for t in tables_read)
        assert tables_read[0].name == "analytics.users"
        assert tables_read[1].alias == "e"

    def test_query_detail_tables_written(self) -> None:
        """Test QueryDetail.tables_written property."""
        detail = QueryDetail(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
            tables_used=[
                TableReference(name="analytics.users", operation="read"),
                TableReference(name="analytics.output", operation="write"),
                TableReference(name="analytics.backup", operation="write"),
            ],
        )

        tables_written = detail.tables_written
        assert len(tables_written) == 2
        assert all(t.operation == "write" for t in tables_written)
        assert tables_written[0].name == "analytics.output"
        assert tables_written[1].name == "analytics.backup"

    def test_query_detail_is_running(self) -> None:
        """Test QueryDetail.is_running property."""
        running = QueryDetail(
            query_id="test_001",
            engine="bigquery",
            state=QueryState.RUNNING,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )
        assert running.is_running is True
        assert running.is_terminal is False

        completed = QueryDetail(
            query_id="test_002",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@company.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(),
        )
        assert completed.is_running is False
        assert completed.is_terminal is True


class TestTableReferenceModel:
    """Tests for TableReference model."""

    def test_table_reference_creation(self) -> None:
        """Test TableReference model creation."""
        ref = TableReference(
            name="project.dataset.table",
            operation="read",
            alias="t",
        )

        assert ref.name == "project.dataset.table"
        assert ref.operation == "read"
        assert ref.alias == "t"

    def test_table_reference_without_alias(self) -> None:
        """Test TableReference without alias."""
        ref = TableReference(
            name="analytics.users",
            operation="write",
        )

        assert ref.name == "analytics.users"
        assert ref.operation == "write"
        assert ref.alias is None

    def test_table_reference_frozen(self) -> None:
        """Test TableReference is frozen (immutable)."""
        ref = TableReference(
            name="analytics.users",
            operation="read",
        )

        with pytest.raises(Exception):
            ref.name = "other.table"  # type: ignore[misc]


class TestQueryAPIClientInit:
    """Tests for QueryAPI client initialization."""

    def test_client_uses_context_server_url(self) -> None:
        """Test that client uses server_url from context."""
        ctx = ExecutionContext(
            server_url="https://custom.server.com",
            execution_mode=ExecutionMode.MOCK,
        )
        api = QueryAPI(context=ctx)

        # Force client creation
        client = api._get_client()

        assert client.config.url == "https://custom.server.com"

    def test_client_default_server_url(self) -> None:
        """Test that client uses default URL when not specified."""
        ctx = ExecutionContext(server_url=None, execution_mode=ExecutionMode.MOCK)
        api = QueryAPI(context=ctx)

        client = api._get_client()

        assert client.config.url == "http://localhost:8081"

    def test_client_mock_mode_propagated(self) -> None:
        """Test that mock_mode is propagated to client."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = QueryAPI(context=ctx)

        client = api._get_client()

        assert client.mock_mode is True


class TestQueryScopeEnum:
    """Tests for QueryScope enum."""

    def test_query_scope_values(self) -> None:
        """Test QueryScope enum values."""
        assert QueryScope.MY.value == "my"
        assert QueryScope.SYSTEM.value == "system"
        assert QueryScope.USER.value == "user"
        assert QueryScope.ALL.value == "all"


class TestQueryStateEnum:
    """Tests for QueryState enum."""

    def test_query_state_values(self) -> None:
        """Test QueryState enum values."""
        assert QueryState.PENDING.value == "pending"
        assert QueryState.RUNNING.value == "running"
        assert QueryState.SUCCESS.value == "success"
        assert QueryState.FAILED.value == "failed"
        assert QueryState.CANCELLED.value == "cancelled"


class TestAccountTypeEnum:
    """Tests for AccountType enum."""

    def test_account_type_values(self) -> None:
        """Test AccountType enum values."""
        assert AccountType.PERSONAL.value == "personal"
        assert AccountType.SYSTEM.value == "system"
