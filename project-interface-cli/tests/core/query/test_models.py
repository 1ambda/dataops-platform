"""Tests for Query Models.

This module tests the core query models including
AccountType, QueryScope, QueryState enums, and model classes
TableReference, QueryResources, QueryInfo, and QueryDetail.
"""

from __future__ import annotations

from datetime import UTC, datetime
from typing import Any

from pydantic import ValidationError
import pytest

from dli.core.query.models import (
    AccountType,
    QueryDetail,
    QueryInfo,
    QueryResources,
    QueryScope,
    QueryState,
    TableReference,
)

# =============================================================================
# AccountType Enum Tests
# =============================================================================


class TestAccountType:
    """Tests for AccountType enum."""

    def test_values(self) -> None:
        """AccountType should have expected values."""
        assert AccountType.PERSONAL.value == "personal"
        assert AccountType.SYSTEM.value == "system"

    def test_all_types_defined(self) -> None:
        """All expected account types should be defined."""
        expected_types = {"PERSONAL", "SYSTEM"}
        actual_types = {t.name for t in AccountType}
        assert actual_types == expected_types

    def test_string_conversion(self) -> None:
        """AccountType value should be the string representation."""
        # String enum values are used in serialization
        assert AccountType.PERSONAL.value == "personal"
        assert AccountType.SYSTEM.value == "system"
        # str() returns the enum representation, not value
        assert "PERSONAL" in str(AccountType.PERSONAL)

    def test_is_string_enum(self) -> None:
        """AccountType should be a string enum."""
        assert isinstance(AccountType.PERSONAL.value, str)
        # String enum comparison
        assert AccountType.PERSONAL == "personal"
        assert AccountType.SYSTEM == "system"

    def test_from_string(self) -> None:
        """AccountType should be creatable from string."""
        assert AccountType("personal") == AccountType.PERSONAL
        assert AccountType("system") == AccountType.SYSTEM

    def test_invalid_type_raises_error(self) -> None:
        """Invalid account type should raise ValueError."""
        with pytest.raises(ValueError):
            AccountType("invalid")


# =============================================================================
# QueryScope Enum Tests
# =============================================================================


class TestQueryScope:
    """Tests for QueryScope enum."""

    def test_values(self) -> None:
        """QueryScope should have expected values."""
        assert QueryScope.MY.value == "my"
        assert QueryScope.SYSTEM.value == "system"
        assert QueryScope.USER.value == "user"
        assert QueryScope.ALL.value == "all"

    def test_all_scopes_defined(self) -> None:
        """All expected query scopes should be defined."""
        expected_scopes = {"MY", "SYSTEM", "USER", "ALL"}
        actual_scopes = {s.name for s in QueryScope}
        assert actual_scopes == expected_scopes

    def test_string_conversion(self) -> None:
        """QueryScope value should be the string representation."""
        # String enum values are used in serialization
        assert QueryScope.MY.value == "my"
        assert QueryScope.SYSTEM.value == "system"
        assert QueryScope.USER.value == "user"
        assert QueryScope.ALL.value == "all"
        # str() returns the enum representation, not value
        assert "MY" in str(QueryScope.MY)

    def test_is_string_enum(self) -> None:
        """QueryScope should be a string enum."""
        assert isinstance(QueryScope.MY.value, str)
        # String enum comparison
        assert QueryScope.MY == "my"
        assert QueryScope.ALL == "all"

    def test_from_string(self) -> None:
        """QueryScope should be creatable from string."""
        assert QueryScope("my") == QueryScope.MY
        assert QueryScope("system") == QueryScope.SYSTEM
        assert QueryScope("user") == QueryScope.USER
        assert QueryScope("all") == QueryScope.ALL

    def test_invalid_scope_raises_error(self) -> None:
        """Invalid query scope should raise ValueError."""
        with pytest.raises(ValueError):
            QueryScope("invalid")


# =============================================================================
# QueryState Enum Tests
# =============================================================================


class TestQueryState:
    """Tests for QueryState enum."""

    def test_values(self) -> None:
        """QueryState should have expected values."""
        assert QueryState.PENDING.value == "pending"
        assert QueryState.RUNNING.value == "running"
        assert QueryState.SUCCESS.value == "success"
        assert QueryState.FAILED.value == "failed"
        assert QueryState.CANCELLED.value == "cancelled"

    def test_all_states_defined(self) -> None:
        """All expected query states should be defined."""
        expected_states = {"PENDING", "RUNNING", "SUCCESS", "FAILED", "CANCELLED"}
        actual_states = {s.name for s in QueryState}
        assert actual_states == expected_states

    def test_string_conversion(self) -> None:
        """QueryState value should be the string representation."""
        # String enum values are used in serialization
        assert QueryState.PENDING.value == "pending"
        assert QueryState.RUNNING.value == "running"
        assert QueryState.SUCCESS.value == "success"
        assert QueryState.FAILED.value == "failed"
        assert QueryState.CANCELLED.value == "cancelled"
        # str() returns the enum representation, not value
        assert "PENDING" in str(QueryState.PENDING)

    def test_is_string_enum(self) -> None:
        """QueryState should be a string enum."""
        assert isinstance(QueryState.SUCCESS.value, str)
        # String enum comparison
        assert QueryState.SUCCESS == "success"
        assert QueryState.FAILED == "failed"

    def test_from_string(self) -> None:
        """QueryState should be creatable from string."""
        assert QueryState("pending") == QueryState.PENDING
        assert QueryState("running") == QueryState.RUNNING
        assert QueryState("success") == QueryState.SUCCESS
        assert QueryState("failed") == QueryState.FAILED
        assert QueryState("cancelled") == QueryState.CANCELLED

    def test_invalid_state_raises_error(self) -> None:
        """Invalid query state should raise ValueError."""
        with pytest.raises(ValueError):
            QueryState("invalid")


# =============================================================================
# QueryResources Tests
# =============================================================================


class TestQueryResources:
    """Tests for QueryResources model."""

    def test_default_values(self) -> None:
        """QueryResources should have None defaults for all fields."""
        resources = QueryResources()
        assert resources.bytes_processed is None
        assert resources.bytes_billed is None
        assert resources.slot_time_seconds is None
        assert resources.rows_affected is None

    def test_full_creation(self) -> None:
        """Create QueryResources with all fields."""
        resources = QueryResources(
            bytes_processed=1073741824,  # 1 GB
            bytes_billed=1073741824,
            slot_time_seconds=150.0,
            rows_affected=1000000,
        )
        assert resources.bytes_processed == 1073741824
        assert resources.bytes_billed == 1073741824
        assert resources.slot_time_seconds == 150.0
        assert resources.rows_affected == 1000000

    def test_partial_creation(self) -> None:
        """Create QueryResources with partial fields."""
        resources = QueryResources(
            bytes_processed=500000,
            rows_affected=100,
        )
        assert resources.bytes_processed == 500000
        assert resources.bytes_billed is None
        assert resources.slot_time_seconds is None
        assert resources.rows_affected == 100

    def test_frozen(self) -> None:
        """QueryResources should be immutable (frozen)."""
        resources = QueryResources(bytes_processed=1000)
        with pytest.raises(ValidationError):
            resources.bytes_processed = 2000  # type: ignore[misc]


class TestQueryResourcesSerialization:
    """Tests for QueryResources JSON serialization."""

    def test_to_json(self) -> None:
        """QueryResources should serialize to JSON dict."""
        resources = QueryResources(
            bytes_processed=1073741824,
            bytes_billed=1073741824,
            slot_time_seconds=150.0,
            rows_affected=1000,
        )
        data = resources.model_dump()
        assert data["bytes_processed"] == 1073741824
        assert data["bytes_billed"] == 1073741824
        assert data["slot_time_seconds"] == 150.0
        assert data["rows_affected"] == 1000

    def test_from_json(self) -> None:
        """QueryResources should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "bytes_processed": 500000,
            "bytes_billed": 500000,
            "slot_time_seconds": 25.5,
            "rows_affected": 50,
        }
        resources = QueryResources.model_validate(data)
        assert resources.bytes_processed == 500000
        assert resources.slot_time_seconds == 25.5

    def test_json_roundtrip(self) -> None:
        """QueryResources should survive JSON roundtrip."""
        original = QueryResources(
            bytes_processed=1073741824,
            bytes_billed=1073741824,
            slot_time_seconds=150.0,
            rows_affected=1000000,
        )
        json_str = original.model_dump_json()
        restored = QueryResources.model_validate_json(json_str)
        assert restored == original


# =============================================================================
# TableReference Tests
# =============================================================================


class TestTableReference:
    """Tests for TableReference model."""

    def test_creation(self) -> None:
        """Create TableReference with required fields."""
        ref = TableReference(
            name="project.dataset.table",
            operation="read",
        )
        assert ref.name == "project.dataset.table"
        assert ref.operation == "read"
        assert ref.alias is None

    def test_read_operation(self) -> None:
        """Create TableReference with read operation."""
        ref = TableReference(name="project.dataset.source", operation="read")
        assert ref.operation == "read"

    def test_write_operation(self) -> None:
        """Create TableReference with write operation."""
        ref = TableReference(name="project.dataset.target", operation="write")
        assert ref.operation == "write"

    def test_with_alias(self) -> None:
        """Create TableReference with table alias."""
        ref = TableReference(
            name="project.dataset.users",
            operation="read",
            alias="u",
        )
        assert ref.name == "project.dataset.users"
        assert ref.alias == "u"

    def test_frozen(self) -> None:
        """TableReference should be immutable (frozen)."""
        ref = TableReference(name="test.table", operation="read")
        with pytest.raises(ValidationError):
            ref.name = "other.table"  # type: ignore[misc]


class TestTableReferenceSerialization:
    """Tests for TableReference JSON serialization."""

    def test_to_json(self) -> None:
        """TableReference should serialize to JSON dict."""
        ref = TableReference(
            name="project.dataset.table",
            operation="read",
            alias="t",
        )
        data = ref.model_dump()
        assert data["name"] == "project.dataset.table"
        assert data["operation"] == "read"
        assert data["alias"] == "t"

    def test_from_json(self) -> None:
        """TableReference should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "name": "project.dataset.table",
            "operation": "write",
            "alias": None,
        }
        ref = TableReference.model_validate(data)
        assert ref.name == "project.dataset.table"
        assert ref.operation == "write"
        assert ref.alias is None

    def test_json_roundtrip(self) -> None:
        """TableReference should survive JSON roundtrip."""
        original = TableReference(
            name="project.dataset.table",
            operation="read",
            alias="tbl",
        )
        json_str = original.model_dump_json()
        restored = TableReference.model_validate_json(json_str)
        assert restored == original


class TestTableReferenceValidation:
    """Tests for TableReference validation."""

    def test_operation_must_be_read_or_write(self) -> None:
        """operation must be 'read' or 'write'."""
        with pytest.raises(ValidationError):
            TableReference(name="test.table", operation="delete")  # type: ignore[arg-type]

    def test_name_required(self) -> None:
        """name field is required."""
        with pytest.raises(ValidationError):
            TableReference(operation="read")  # type: ignore[call-arg]


# =============================================================================
# QueryInfo Tests
# =============================================================================


class TestQueryInfo:
    """Tests for QueryInfo model."""

    def test_minimal_creation(self) -> None:
        """Create QueryInfo with required fields only."""
        info = QueryInfo(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
        )
        assert info.query_id == "bq_job_123"
        assert info.engine == "bigquery"
        assert info.state == QueryState.SUCCESS
        assert info.account == "user@example.com"
        assert info.account_type == AccountType.PERSONAL
        assert info.finished_at is None
        assert info.duration_seconds is None
        assert info.tables_used_count == 0
        assert info.error_message is None
        assert info.tags == []

    def test_full_creation(self) -> None:
        """Create QueryInfo with all fields."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        info = QueryInfo(
            query_id="bq_job_456",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            finished_at=finished,
            duration_seconds=300.0,
            tables_used_count=5,
            error_message=None,
            tags=["analytics", "daily"],
        )
        assert info.query_id == "bq_job_456"
        assert info.finished_at == finished
        assert info.duration_seconds == 300.0
        assert info.tables_used_count == 5
        assert len(info.tags) == 2

    def test_trino_engine(self) -> None:
        """Create QueryInfo with trino engine."""
        info = QueryInfo(
            query_id="trino_query_789",
            engine="trino",
            state=QueryState.RUNNING,
            account="airflow-prod",
            account_type=AccountType.SYSTEM,
            started_at=datetime.now(UTC),
        )
        assert info.engine == "trino"

    def test_failed_query_with_error(self) -> None:
        """Create QueryInfo for a failed query."""
        info = QueryInfo(
            query_id="bq_job_failed",
            engine="bigquery",
            state=QueryState.FAILED,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            error_message="Table not found: project.dataset.missing_table",
        )
        assert info.state == QueryState.FAILED
        assert info.error_message is not None
        assert "Table not found" in info.error_message


class TestQueryInfoProperties:
    """Tests for QueryInfo convenience properties."""

    def test_is_running_true(self) -> None:
        """is_running should be True for running states."""
        pending_info = QueryInfo(
            query_id="test1",
            engine="bigquery",
            state=QueryState.PENDING,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        running_info = QueryInfo(
            query_id="test2",
            engine="bigquery",
            state=QueryState.RUNNING,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        assert pending_info.is_running is True
        assert running_info.is_running is True

    def test_is_running_false(self) -> None:
        """is_running should be False for terminal states."""
        success_info = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        failed_info = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.FAILED,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        cancelled_info = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.CANCELLED,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        assert success_info.is_running is False
        assert failed_info.is_running is False
        assert cancelled_info.is_running is False

    def test_is_terminal_true(self) -> None:
        """is_terminal should be True for terminal states."""
        for state in [QueryState.SUCCESS, QueryState.FAILED, QueryState.CANCELLED]:
            info = QueryInfo(
                query_id="test",
                engine="bigquery",
                state=state,
                account="user@test.com",
                account_type=AccountType.PERSONAL,
                started_at=datetime.now(UTC),
            )
            assert info.is_terminal is True

    def test_is_terminal_false(self) -> None:
        """is_terminal should be False for non-terminal states."""
        for state in [QueryState.PENDING, QueryState.RUNNING]:
            info = QueryInfo(
                query_id="test",
                engine="bigquery",
                state=state,
                account="user@test.com",
                account_type=AccountType.PERSONAL,
                started_at=datetime.now(UTC),
            )
            assert info.is_terminal is False

    def test_frozen(self) -> None:
        """QueryInfo should be immutable (frozen)."""
        info = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        with pytest.raises(ValidationError):
            info.query_id = "other"  # type: ignore[misc]


class TestQueryInfoSerialization:
    """Tests for QueryInfo JSON serialization."""

    def test_to_json(self) -> None:
        """QueryInfo should serialize to JSON dict."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        info = QueryInfo(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            duration_seconds=12.5,
            tags=["test"],
        )
        data = info.model_dump()
        assert data["query_id"] == "bq_job_123"
        assert data["engine"] == "bigquery"
        assert data["state"] == "success"
        assert data["account_type"] == "personal"
        assert data["duration_seconds"] == 12.5
        assert "test" in data["tags"]

    def test_from_json(self) -> None:
        """QueryInfo should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "query_id": "bq_job_123",
            "engine": "bigquery",
            "state": "success",
            "account": "user@example.com",
            "account_type": "personal",
            "started_at": "2024-01-15T09:00:00Z",
            "finished_at": "2024-01-15T09:05:00Z",
            "duration_seconds": 300.0,
            "tables_used_count": 3,
            "error_message": None,
            "tags": ["analytics"],
        }
        info = QueryInfo.model_validate(data)
        assert info.query_id == "bq_job_123"
        assert info.state == QueryState.SUCCESS
        assert info.account_type == AccountType.PERSONAL
        assert info.duration_seconds == 300.0

    def test_json_roundtrip(self) -> None:
        """QueryInfo should survive JSON roundtrip."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        original = QueryInfo(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            finished_at=finished,
            duration_seconds=300.0,
            tables_used_count=5,
            tags=["analytics", "daily"],
        )
        json_str = original.model_dump_json()
        restored = QueryInfo.model_validate_json(json_str)
        assert restored.query_id == original.query_id
        assert restored.state == original.state
        assert restored.started_at == original.started_at
        assert restored.finished_at == original.finished_at
        assert restored.duration_seconds == original.duration_seconds


# =============================================================================
# QueryDetail Tests
# =============================================================================


class TestQueryDetail:
    """Tests for QueryDetail model."""

    def test_minimal_creation(self) -> None:
        """Create QueryDetail with required fields only."""
        detail = QueryDetail(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC),
        )
        assert detail.query_id == "bq_job_123"
        assert detail.engine == "bigquery"
        assert detail.state == QueryState.SUCCESS
        assert detail.finished_at is None
        assert detail.duration_seconds is None
        assert detail.queue_time_seconds is None
        assert detail.bytes_processed is None
        assert detail.bytes_billed is None
        assert detail.slot_time_seconds is None
        assert detail.rows_affected is None
        assert detail.tables_used == []
        assert detail.tags == []
        assert detail.error_message is None
        assert detail.error_code is None
        assert detail.query_preview is None
        assert detail.query_text is None

    def test_full_creation(self) -> None:
        """Create QueryDetail with all fields."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        detail = QueryDetail(
            query_id="bq_job_456",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            finished_at=finished,
            duration_seconds=300.0,
            queue_time_seconds=2.5,
            bytes_processed=1073741824,  # 1 GB
            bytes_billed=1073741824,
            slot_time_seconds=150.0,
            rows_affected=1000000,
            tables_used=[
                TableReference(name="project.dataset.source", operation="read"),
                TableReference(name="project.dataset.target", operation="write"),
            ],
            tags=["etl", "daily"],
            error_message=None,
            error_code=None,
            query_preview="SELECT * FROM source...",
            query_text="SELECT * FROM source WHERE date = '2024-01-15'",
        )
        assert detail.duration_seconds == 300.0
        assert detail.bytes_processed == 1073741824
        assert detail.slot_time_seconds == 150.0
        assert len(detail.tables_used) == 2
        assert detail.query_preview is not None
        assert detail.query_text is not None

    def test_failed_query_with_error_details(self) -> None:
        """Create QueryDetail for a failed query."""
        detail = QueryDetail(
            query_id="bq_job_failed",
            engine="bigquery",
            state=QueryState.FAILED,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            error_message="Table not found: project.dataset.missing_table",
            error_code="NOT_FOUND",
        )
        assert detail.state == QueryState.FAILED
        assert detail.error_message is not None
        assert detail.error_code == "NOT_FOUND"


class TestQueryDetailTableFiltering:
    """Tests for QueryDetail table filtering properties."""

    def test_tables_read(self) -> None:
        """tables_read should return only read operations."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[
                TableReference(name="source1", operation="read"),
                TableReference(name="source2", operation="read"),
                TableReference(name="target", operation="write"),
            ],
        )
        tables_read = detail.tables_read
        assert len(tables_read) == 2
        assert all(t.operation == "read" for t in tables_read)
        assert {t.name for t in tables_read} == {"source1", "source2"}

    def test_tables_written(self) -> None:
        """tables_written should return only write operations."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[
                TableReference(name="source", operation="read"),
                TableReference(name="target1", operation="write"),
                TableReference(name="target2", operation="write"),
            ],
        )
        tables_written = detail.tables_written
        assert len(tables_written) == 2
        assert all(t.operation == "write" for t in tables_written)
        assert {t.name for t in tables_written} == {"target1", "target2"}

    def test_empty_tables(self) -> None:
        """Table filtering should handle empty tables_used."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[],
        )
        assert detail.tables_read == []
        assert detail.tables_written == []

    def test_only_read_tables(self) -> None:
        """tables_written should be empty when only read operations."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[
                TableReference(name="source1", operation="read"),
                TableReference(name="source2", operation="read"),
            ],
        )
        assert len(detail.tables_read) == 2
        assert detail.tables_written == []

    def test_only_write_tables(self) -> None:
        """tables_read should be empty when only write operations."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[
                TableReference(name="target", operation="write"),
            ],
        )
        assert detail.tables_read == []
        assert len(detail.tables_written) == 1


class TestQueryDetailProperties:
    """Tests for QueryDetail convenience properties."""

    def test_is_running_true(self) -> None:
        """is_running should be True for running states."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.RUNNING,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        assert detail.is_running is True
        assert detail.is_terminal is False

    def test_is_terminal_true(self) -> None:
        """is_terminal should be True for terminal states."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        assert detail.is_terminal is True
        assert detail.is_running is False

    def test_frozen(self) -> None:
        """QueryDetail should be immutable (frozen)."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
        )
        with pytest.raises(ValidationError):
            detail.query_id = "other"  # type: ignore[misc]


class TestQueryDetailSerialization:
    """Tests for QueryDetail JSON serialization."""

    def test_to_json(self) -> None:
        """QueryDetail should serialize to JSON dict."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        detail = QueryDetail(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            duration_seconds=12.5,
            bytes_processed=1000000,
            tables_used=[
                TableReference(name="project.dataset.table", operation="read"),
            ],
            tags=["test"],
            query_preview="SELECT * FROM...",
        )
        data = detail.model_dump()
        assert data["query_id"] == "bq_job_123"
        assert data["engine"] == "bigquery"
        assert data["state"] == "success"
        assert data["bytes_processed"] == 1000000
        assert len(data["tables_used"]) == 1
        assert data["tables_used"][0]["name"] == "project.dataset.table"

    def test_from_json(self) -> None:
        """QueryDetail should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "query_id": "bq_job_123",
            "engine": "bigquery",
            "state": "success",
            "account": "user@example.com",
            "account_type": "personal",
            "started_at": "2024-01-15T09:00:00Z",
            "finished_at": "2024-01-15T09:05:00Z",
            "duration_seconds": 300.0,
            "queue_time_seconds": 2.0,
            "bytes_processed": 1073741824,
            "bytes_billed": 1073741824,
            "slot_time_seconds": 150.0,
            "rows_affected": 1000,
            "tables_used": [
                {"name": "project.dataset.source", "operation": "read", "alias": "s"},
                {"name": "project.dataset.target", "operation": "write", "alias": None},
            ],
            "tags": ["etl"],
            "error_message": None,
            "error_code": None,
            "query_preview": "SELECT...",
            "query_text": "SELECT * FROM source",
        }
        detail = QueryDetail.model_validate(data)
        assert detail.query_id == "bq_job_123"
        assert detail.state == QueryState.SUCCESS
        assert detail.bytes_processed == 1073741824
        assert len(detail.tables_used) == 2
        assert detail.tables_used[0].alias == "s"

    def test_json_roundtrip(self) -> None:
        """QueryDetail should survive JSON roundtrip."""
        started = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)
        finished = datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC)
        original = QueryDetail(
            query_id="bq_job_123",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@example.com",
            account_type=AccountType.PERSONAL,
            started_at=started,
            finished_at=finished,
            duration_seconds=300.0,
            queue_time_seconds=2.5,
            bytes_processed=1073741824,
            bytes_billed=1073741824,
            slot_time_seconds=150.0,
            rows_affected=1000000,
            tables_used=[
                TableReference(name="source", operation="read", alias="s"),
                TableReference(name="target", operation="write"),
            ],
            tags=["etl", "daily"],
            query_preview="SELECT...",
            query_text="SELECT * FROM source",
        )
        json_str = original.model_dump_json()
        restored = QueryDetail.model_validate_json(json_str)
        assert restored.query_id == original.query_id
        assert restored.state == original.state
        assert restored.bytes_processed == original.bytes_processed
        assert len(restored.tables_used) == len(original.tables_used)
        assert restored.tables_used[0].alias == original.tables_used[0].alias


# =============================================================================
# Integration Tests
# =============================================================================


class TestModelIntegration:
    """Integration tests for query model interactions."""

    def test_query_detail_contains_table_references(self) -> None:
        """QueryDetail should properly contain TableReference instances."""
        detail = QueryDetail(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=datetime.now(UTC),
            tables_used=[
                TableReference(
                    name="project.analytics.source",
                    operation="read",
                    alias="src",
                ),
                TableReference(
                    name="project.analytics.target",
                    operation="write",
                ),
            ],
        )

        # Verify table references are accessible
        assert len(detail.tables_used) == 2
        assert detail.tables_used[0].alias == "src"
        assert detail.tables_used[1].alias is None

        # Verify filtering works
        assert len(detail.tables_read) == 1
        assert len(detail.tables_written) == 1
        assert detail.tables_read[0].name == "project.analytics.source"
        assert detail.tables_written[0].name == "project.analytics.target"

    def test_all_state_combinations_with_account_types(self) -> None:
        """All QueryState and AccountType combinations should be valid."""
        for state in QueryState:
            for account_type in AccountType:
                info = QueryInfo(
                    query_id=f"test-{state.value}-{account_type.value}",
                    engine="bigquery",
                    state=state,
                    account="test@example.com",
                    account_type=account_type,
                    started_at=datetime.now(UTC),
                )
                assert info.state == state
                assert info.account_type == account_type

    def test_query_lifecycle_states(self) -> None:
        """Test query through its lifecycle states."""
        base_time = datetime(2024, 1, 15, 9, 0, 0, tzinfo=UTC)

        # Pending
        pending = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.PENDING,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=base_time,
        )
        assert pending.is_running is True
        assert pending.is_terminal is False

        # Running
        running = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.RUNNING,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=base_time,
        )
        assert running.is_running is True
        assert running.is_terminal is False

        # Success
        success = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.SUCCESS,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=base_time,
            finished_at=datetime(2024, 1, 15, 9, 5, 0, tzinfo=UTC),
            duration_seconds=300.0,
        )
        assert success.is_running is False
        assert success.is_terminal is True

        # Failed
        failed = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.FAILED,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=base_time,
            error_message="Query failed",
        )
        assert failed.is_running is False
        assert failed.is_terminal is True

        # Cancelled
        cancelled = QueryInfo(
            query_id="test",
            engine="bigquery",
            state=QueryState.CANCELLED,
            account="user@test.com",
            account_type=AccountType.PERSONAL,
            started_at=base_time,
        )
        assert cancelled.is_running is False
        assert cancelled.is_terminal is True

    def test_all_engine_types(self) -> None:
        """Both engine types should be valid."""
        for engine in ["bigquery", "trino"]:
            info = QueryInfo(
                query_id="test",
                engine=engine,  # type: ignore[arg-type]
                state=QueryState.SUCCESS,
                account="user@test.com",
                account_type=AccountType.PERSONAL,
                started_at=datetime.now(UTC),
            )
            assert info.engine == engine

    def test_all_models_have_proper_exports(self) -> None:
        """All models should be properly exported from the module."""
        from dli.core.query import (
            AccountType,
            QueryDetail,
            QueryInfo,
            QueryResources,
            QueryScope,
            QueryState,
            TableReference,
        )

        # Verify imports work
        assert AccountType is not None
        assert QueryScope is not None
        assert QueryState is not None
        assert TableReference is not None
        assert QueryResources is not None
        assert QueryInfo is not None
        assert QueryDetail is not None
