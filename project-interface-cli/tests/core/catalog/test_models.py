"""Tests for Catalog Models.

This module tests the core catalog models including
ColumnInfo, OwnershipInfo, FreshnessInfo, QualityInfo,
ImpactSummary, SampleQuery, TableInfo, and TableDetail.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import Any

import pytest
from pydantic import ValidationError

from dli.core.catalog.models import (
    ColumnInfo,
    FreshnessInfo,
    ImpactSummary,
    OwnershipInfo,
    QualityInfo,
    QualityTestResult,
    SampleQuery,
    TableDetail,
    TableInfo,
)


# =============================================================================
# ColumnInfo Tests
# =============================================================================


class TestColumnInfoCreation:
    """Tests for ColumnInfo creation."""

    def test_minimal_column(self) -> None:
        """Create ColumnInfo with required fields only."""
        col = ColumnInfo(name="user_id", data_type="STRING")
        assert col.name == "user_id"
        assert col.data_type == "STRING"
        assert col.description is None
        assert col.is_pii is False
        assert col.fill_rate is None
        assert col.distinct_count is None

    def test_full_column(self) -> None:
        """Create ColumnInfo with all fields."""
        col = ColumnInfo(
            name="email",
            data_type="STRING",
            description="User email address",
            is_pii=True,
            fill_rate=0.98,
            distinct_count=1500000,
        )
        assert col.name == "email"
        assert col.data_type == "STRING"
        assert col.description == "User email address"
        assert col.is_pii is True
        assert col.fill_rate == 0.98
        assert col.distinct_count == 1500000


class TestColumnInfoProperties:
    """Tests for ColumnInfo properties."""

    def test_pii_indicator_when_pii(self) -> None:
        """pii_indicator should return [lock] when is_pii is True."""
        col = ColumnInfo(name="email", data_type="STRING", is_pii=True)
        assert col.pii_indicator == "[lock]"

    def test_pii_indicator_when_not_pii(self) -> None:
        """pii_indicator should return empty string when is_pii is False."""
        col = ColumnInfo(name="user_id", data_type="STRING", is_pii=False)
        assert col.pii_indicator == ""

    def test_fill_rate_percent_with_value(self) -> None:
        """fill_rate_percent should format as percentage."""
        col = ColumnInfo(name="test", data_type="STRING", fill_rate=0.95)
        assert col.fill_rate_percent == "95.0%"

    def test_fill_rate_percent_when_none(self) -> None:
        """fill_rate_percent should return '-' when fill_rate is None."""
        col = ColumnInfo(name="test", data_type="STRING")
        assert col.fill_rate_percent == "-"

    def test_fill_rate_percent_at_boundaries(self) -> None:
        """fill_rate_percent should handle boundary values."""
        col_zero = ColumnInfo(name="test", data_type="STRING", fill_rate=0.0)
        col_one = ColumnInfo(name="test", data_type="STRING", fill_rate=1.0)
        assert col_zero.fill_rate_percent == "0.0%"
        assert col_one.fill_rate_percent == "100.0%"


class TestColumnInfoValidation:
    """Tests for ColumnInfo validation."""

    def test_fill_rate_must_be_between_0_and_1(self) -> None:
        """fill_rate must be between 0 and 1."""
        with pytest.raises(ValidationError):
            ColumnInfo(name="test", data_type="STRING", fill_rate=1.5)

        with pytest.raises(ValidationError):
            ColumnInfo(name="test", data_type="STRING", fill_rate=-0.1)

    def test_distinct_count_must_be_non_negative(self) -> None:
        """distinct_count must be >= 0."""
        with pytest.raises(ValidationError):
            ColumnInfo(name="test", data_type="STRING", distinct_count=-1)


# =============================================================================
# OwnershipInfo Tests
# =============================================================================


class TestOwnershipInfoCreation:
    """Tests for OwnershipInfo creation."""

    def test_default_values(self) -> None:
        """OwnershipInfo should have sensible defaults."""
        info = OwnershipInfo()
        assert info.owner is None
        assert info.team is None
        assert info.stewards == []
        assert info.consumers == []

    def test_full_ownership(self) -> None:
        """Create OwnershipInfo with all fields."""
        info = OwnershipInfo(
            owner="data-team@example.com",
            team="@data-eng",
            stewards=["alice@example.com", "bob@example.com"],
            consumers=["@analytics", "@marketing"],
        )
        assert info.owner == "data-team@example.com"
        assert info.team == "@data-eng"
        assert len(info.stewards) == 2
        assert len(info.consumers) == 2


# =============================================================================
# FreshnessInfo Tests
# =============================================================================


class TestFreshnessInfoCreation:
    """Tests for FreshnessInfo creation."""

    def test_default_values(self) -> None:
        """FreshnessInfo should have sensible defaults."""
        info = FreshnessInfo()
        assert info.last_updated is None
        assert info.avg_update_lag_hours is None
        assert info.update_frequency is None
        assert info.is_stale is False
        assert info.stale_threshold_hours is None

    def test_full_freshness(self) -> None:
        """Create FreshnessInfo with all fields."""
        now = datetime.now(UTC)
        info = FreshnessInfo(
            last_updated=now,
            avg_update_lag_hours=1.5,
            update_frequency="hourly",
            is_stale=False,
            stale_threshold_hours=6,
        )
        assert info.last_updated == now
        assert info.avg_update_lag_hours == 1.5
        assert info.update_frequency == "hourly"
        assert info.is_stale is False
        assert info.stale_threshold_hours == 6


class TestFreshnessInfoProperties:
    """Tests for FreshnessInfo properties."""

    def test_freshness_status_when_fresh(self) -> None:
        """freshness_status should be 'fresh' when not stale."""
        info = FreshnessInfo(is_stale=False)
        assert info.freshness_status == "fresh"

    def test_freshness_status_when_stale(self) -> None:
        """freshness_status should be 'stale' when stale."""
        info = FreshnessInfo(is_stale=True)
        assert info.freshness_status == "stale"


class TestFreshnessInfoValidation:
    """Tests for FreshnessInfo validation."""

    def test_avg_lag_must_be_non_negative(self) -> None:
        """avg_update_lag_hours must be >= 0."""
        with pytest.raises(ValidationError):
            FreshnessInfo(avg_update_lag_hours=-1.0)

    def test_stale_threshold_must_be_non_negative(self) -> None:
        """stale_threshold_hours must be >= 0."""
        with pytest.raises(ValidationError):
            FreshnessInfo(stale_threshold_hours=-1)


# =============================================================================
# QualityTestResult Tests
# =============================================================================


class TestQualityTestResultCreation:
    """Tests for QualityTestResult creation."""

    def test_passing_test(self) -> None:
        """Create a passing QualityTestResult."""
        result = QualityTestResult(
            test_name="not_null_user_id",
            test_type="not_null",
            status="pass",
            failed_rows=0,
        )
        assert result.test_name == "not_null_user_id"
        assert result.test_type == "not_null"
        assert result.status == "pass"
        assert result.failed_rows == 0

    def test_failing_test(self) -> None:
        """Create a failing QualityTestResult."""
        result = QualityTestResult(
            test_name="valid_email",
            test_type="regex",
            status="fail",
            failed_rows=42,
        )
        assert result.status == "fail"
        assert result.failed_rows == 42

    def test_warning_test(self) -> None:
        """Create a warning QualityTestResult."""
        result = QualityTestResult(
            test_name="freshness_check",
            test_type="freshness",
            status="warn",
            failed_rows=0,
        )
        assert result.status == "warn"


# =============================================================================
# QualityInfo Tests
# =============================================================================


class TestQualityInfoCreation:
    """Tests for QualityInfo creation."""

    def test_default_values(self) -> None:
        """QualityInfo should have sensible defaults."""
        info = QualityInfo()
        assert info.score is None
        assert info.total_tests == 0
        assert info.passed_tests == 0
        assert info.failed_tests == 0
        assert info.warnings == 0
        assert info.recent_tests == []

    def test_full_quality(self) -> None:
        """Create QualityInfo with all fields."""
        info = QualityInfo(
            score=92,
            total_tests=15,
            passed_tests=14,
            failed_tests=1,
            warnings=0,
            recent_tests=[
                QualityTestResult(
                    test_name="test1", test_type="not_null", status="pass"
                ),
            ],
        )
        assert info.score == 92
        assert info.total_tests == 15
        assert len(info.recent_tests) == 1


class TestQualityInfoProperties:
    """Tests for QualityInfo properties."""

    def test_score_status_excellent(self) -> None:
        """score_status should be 'excellent' for scores >= 90."""
        info = QualityInfo(score=95)
        assert info.score_status == "excellent"

    def test_score_status_good(self) -> None:
        """score_status should be 'good' for scores 70-89."""
        info = QualityInfo(score=75)
        assert info.score_status == "good"

    def test_score_status_fair(self) -> None:
        """score_status should be 'fair' for scores 50-69."""
        info = QualityInfo(score=60)
        assert info.score_status == "fair"

    def test_score_status_poor(self) -> None:
        """score_status should be 'poor' for scores < 50."""
        info = QualityInfo(score=30)
        assert info.score_status == "poor"

    def test_score_status_unknown(self) -> None:
        """score_status should be 'unknown' when score is None."""
        info = QualityInfo()
        assert info.score_status == "unknown"


class TestQualityInfoValidation:
    """Tests for QualityInfo validation."""

    def test_score_must_be_0_to_100(self) -> None:
        """score must be between 0 and 100."""
        with pytest.raises(ValidationError):
            QualityInfo(score=101)

        with pytest.raises(ValidationError):
            QualityInfo(score=-1)


# =============================================================================
# ImpactSummary Tests
# =============================================================================


class TestImpactSummaryCreation:
    """Tests for ImpactSummary creation."""

    def test_default_values(self) -> None:
        """ImpactSummary should have sensible defaults."""
        info = ImpactSummary()
        assert info.total_downstream == 0
        assert info.tables == []
        assert info.datasets == []
        assert info.metrics == []
        assert info.dashboards == []

    def test_full_impact(self) -> None:
        """Create ImpactSummary with all fields."""
        info = ImpactSummary(
            total_downstream=5,
            tables=["table1", "table2"],
            datasets=["dataset1"],
            metrics=["metric1", "metric2"],
            dashboards=["dashboard1"],
        )
        assert info.total_downstream == 5
        assert len(info.tables) == 2
        assert len(info.metrics) == 2


# =============================================================================
# SampleQuery Tests
# =============================================================================


class TestSampleQueryCreation:
    """Tests for SampleQuery creation."""

    def test_minimal_query(self) -> None:
        """Create SampleQuery with required fields only."""
        query = SampleQuery(title="Get users", sql="SELECT * FROM users")
        assert query.title == "Get users"
        assert query.sql == "SELECT * FROM users"
        assert query.author is None
        assert query.run_count == 0
        assert query.last_run is None

    def test_full_query(self) -> None:
        """Create SampleQuery with all fields."""
        now = datetime.now(UTC)
        query = SampleQuery(
            title="Active users by country",
            sql="SELECT country, COUNT(*) FROM users GROUP BY 1",
            author="analyst@example.com",
            run_count=156,
            last_run=now,
        )
        assert query.title == "Active users by country"
        assert query.author == "analyst@example.com"
        assert query.run_count == 156
        assert query.last_run == now


# =============================================================================
# TableInfo Tests
# =============================================================================


class TestTableInfoCreation:
    """Tests for TableInfo creation."""

    def test_minimal_table_info(self) -> None:
        """Create TableInfo with required fields only."""
        info = TableInfo(name="project.dataset.table", engine="bigquery")
        assert info.name == "project.dataset.table"
        assert info.engine == "bigquery"
        assert info.owner is None
        assert info.team is None
        assert info.tags == []
        assert info.row_count is None
        assert info.last_updated is None

    def test_full_table_info(self) -> None:
        """Create TableInfo with all fields."""
        now = datetime.now(UTC)
        info = TableInfo(
            name="my-project.analytics.users",
            engine="bigquery",
            owner="data-team@example.com",
            team="@data-eng",
            tags=["tier::critical", "pii"],
            row_count=1500000,
            last_updated=now,
        )
        assert info.name == "my-project.analytics.users"
        assert info.engine == "bigquery"
        assert info.owner == "data-team@example.com"
        assert info.row_count == 1500000


class TestTableInfoProperties:
    """Tests for TableInfo properties."""

    def test_project_extraction(self) -> None:
        """project property should extract first part of name."""
        info = TableInfo(name="my-project.analytics.users", engine="bigquery")
        assert info.project == "my-project"

    def test_dataset_extraction(self) -> None:
        """dataset property should extract second part of name."""
        info = TableInfo(name="my-project.analytics.users", engine="bigquery")
        assert info.dataset == "analytics"

    def test_table_extraction(self) -> None:
        """table property should extract third part of name."""
        info = TableInfo(name="my-project.analytics.users", engine="bigquery")
        assert info.table == "users"

    def test_partial_name_handling(self) -> None:
        """Properties should handle partial names gracefully."""
        info = TableInfo(name="project", engine="bigquery")
        assert info.project == "project"
        assert info.dataset == ""
        assert info.table == ""


# =============================================================================
# TableDetail Tests
# =============================================================================


class TestTableDetailCreation:
    """Tests for TableDetail creation."""

    def test_minimal_table_detail(self) -> None:
        """Create TableDetail with required fields only."""
        detail = TableDetail(
            name="my-project.analytics.users",
            engine="bigquery",
            basecamp_url="https://basecamp.example.com/catalog/users",
        )
        assert detail.name == "my-project.analytics.users"
        assert detail.engine == "bigquery"
        assert detail.description is None
        assert detail.tags == []
        assert detail.ownership is not None  # Default factory
        assert detail.columns == []
        assert detail.sample_data is None

    def test_full_table_detail(self) -> None:
        """Create TableDetail with all fields."""
        detail = TableDetail(
            name="my-project.analytics.users",
            engine="bigquery",
            description="User dimension table",
            tags=["tier::critical", "pii"],
            basecamp_url="https://basecamp.example.com/catalog/users",
            ownership=OwnershipInfo(owner="data@example.com"),
            columns=[
                ColumnInfo(name="user_id", data_type="STRING"),
                ColumnInfo(name="email", data_type="STRING", is_pii=True),
            ],
            freshness=FreshnessInfo(update_frequency="hourly"),
            quality=QualityInfo(score=92),
            impact=ImpactSummary(total_downstream=5),
            sample_queries=[SampleQuery(title="Test", sql="SELECT 1")],
            sample_data=[{"user_id": "1", "email": "***"}],
        )
        assert detail.description == "User dimension table"
        assert len(detail.columns) == 2
        assert detail.quality.score == 92


class TestTableDetailProperties:
    """Tests for TableDetail properties."""

    def test_name_extraction_same_as_table_info(self) -> None:
        """Name extraction properties should work like TableInfo."""
        detail = TableDetail(
            name="my-project.analytics.users",
            engine="bigquery",
            basecamp_url="https://example.com",
        )
        assert detail.project == "my-project"
        assert detail.dataset == "analytics"
        assert detail.table == "users"

    def test_pii_column_count(self) -> None:
        """pii_column_count should count PII columns."""
        detail = TableDetail(
            name="test",
            engine="bigquery",
            basecamp_url="https://example.com",
            columns=[
                ColumnInfo(name="user_id", data_type="STRING", is_pii=False),
                ColumnInfo(name="email", data_type="STRING", is_pii=True),
                ColumnInfo(name="name", data_type="STRING", is_pii=True),
                ColumnInfo(name="country", data_type="STRING", is_pii=False),
            ],
        )
        assert detail.pii_column_count == 2

    def test_pii_column_count_when_no_pii(self) -> None:
        """pii_column_count should be 0 when no PII columns."""
        detail = TableDetail(
            name="test",
            engine="bigquery",
            basecamp_url="https://example.com",
            columns=[
                ColumnInfo(name="id", data_type="INT64"),
                ColumnInfo(name="value", data_type="FLOAT64"),
            ],
        )
        assert detail.pii_column_count == 0


# =============================================================================
# JSON Serialization Tests
# =============================================================================


class TestColumnInfoSerialization:
    """Tests for ColumnInfo JSON serialization."""

    def test_to_json(self) -> None:
        """ColumnInfo should serialize to JSON dict."""
        col = ColumnInfo(
            name="email", data_type="STRING", is_pii=True, fill_rate=0.98
        )
        data = col.model_dump()
        assert data["name"] == "email"
        assert data["is_pii"] is True
        assert data["fill_rate"] == 0.98

    def test_from_json(self) -> None:
        """ColumnInfo should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "name": "user_id",
            "data_type": "STRING",
            "description": "Unique identifier",
            "is_pii": False,
            "fill_rate": 1.0,
            "distinct_count": 1000000,
        }
        col = ColumnInfo.model_validate(data)
        assert col.name == "user_id"
        assert col.fill_rate == 1.0

    def test_json_roundtrip(self) -> None:
        """ColumnInfo should survive JSON roundtrip."""
        original = ColumnInfo(
            name="test",
            data_type="INT64",
            description="Test column",
            is_pii=False,
            fill_rate=0.99,
            distinct_count=500,
        )
        json_str = original.model_dump_json()
        restored = ColumnInfo.model_validate_json(json_str)
        assert restored == original


class TestTableInfoSerialization:
    """Tests for TableInfo JSON serialization."""

    def test_to_json(self) -> None:
        """TableInfo should serialize to JSON dict."""
        info = TableInfo(
            name="project.dataset.table",
            engine="bigquery",
            owner="owner@example.com",
            tags=["critical"],
        )
        data = info.model_dump()
        assert data["name"] == "project.dataset.table"
        assert data["engine"] == "bigquery"
        assert "critical" in data["tags"]

    def test_from_json(self) -> None:
        """TableInfo should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "name": "project.dataset.table",
            "engine": "trino",
            "owner": "owner@example.com",
            "team": "@team",
            "tags": ["a", "b"],
            "row_count": 1000,
            "last_updated": "2024-01-15T10:00:00Z",
        }
        info = TableInfo.model_validate(data)
        assert info.name == "project.dataset.table"
        assert info.engine == "trino"
        assert info.row_count == 1000

    def test_json_roundtrip(self) -> None:
        """TableInfo should survive JSON roundtrip."""
        original = TableInfo(
            name="project.dataset.table",
            engine="bigquery",
            owner="owner@example.com",
            team="@team",
            tags=["critical", "pii"],
            row_count=1500000,
            last_updated=datetime(2024, 1, 15, 10, 0, 0, tzinfo=UTC),
        )
        json_str = original.model_dump_json()
        restored = TableInfo.model_validate_json(json_str)
        assert restored.name == original.name
        assert restored.engine == original.engine
        assert restored.row_count == original.row_count


class TestTableDetailSerialization:
    """Tests for TableDetail JSON serialization."""

    def test_to_json(self) -> None:
        """TableDetail should serialize to JSON dict."""
        detail = TableDetail(
            name="project.dataset.table",
            engine="bigquery",
            description="Test table",
            tags=["critical"],
            basecamp_url="https://example.com",
            ownership=OwnershipInfo(owner="owner@example.com"),
            columns=[ColumnInfo(name="id", data_type="INT64")],
            quality=QualityInfo(score=90),
        )
        data = detail.model_dump()
        assert data["name"] == "project.dataset.table"
        assert data["quality"]["score"] == 90
        assert len(data["columns"]) == 1

    def test_from_json(self) -> None:
        """TableDetail should deserialize from JSON dict."""
        data: dict[str, Any] = {
            "name": "project.dataset.table",
            "engine": "bigquery",
            "description": "Test",
            "tags": [],
            "basecamp_url": "https://example.com",
            "ownership": {"owner": "owner@example.com"},
            "columns": [{"name": "id", "data_type": "INT64"}],
            "freshness": {},
            "quality": {"score": 85},
            "impact": {},
            "sample_queries": [],
            "sample_data": None,
        }
        detail = TableDetail.model_validate(data)
        assert detail.name == "project.dataset.table"
        assert detail.quality.score == 85

    def test_json_roundtrip(self) -> None:
        """TableDetail should survive JSON roundtrip."""
        original = TableDetail(
            name="project.dataset.table",
            engine="bigquery",
            description="Test table",
            tags=["critical", "pii"],
            basecamp_url="https://example.com",
            ownership=OwnershipInfo(
                owner="owner@example.com",
                team="@team",
                stewards=["steward@example.com"],
            ),
            columns=[
                ColumnInfo(name="id", data_type="INT64"),
                ColumnInfo(name="email", data_type="STRING", is_pii=True),
            ],
            freshness=FreshnessInfo(update_frequency="hourly"),
            quality=QualityInfo(score=92, total_tests=10, passed_tests=9),
            impact=ImpactSummary(total_downstream=5, tables=["other.table"]),
            sample_queries=[SampleQuery(title="Query 1", sql="SELECT 1")],
        )
        json_str = original.model_dump_json()
        restored = TableDetail.model_validate_json(json_str)
        assert restored.name == original.name
        assert restored.quality.score == original.quality.score
        assert len(restored.columns) == len(original.columns)


# =============================================================================
# Integration Tests
# =============================================================================


class TestModelIntegration:
    """Integration tests for catalog model interactions."""

    def test_table_detail_contains_all_components(self) -> None:
        """TableDetail should properly contain all sub-models."""
        detail = TableDetail(
            name="project.dataset.table",
            engine="bigquery",
            description="Integration test table",
            tags=["tier::critical", "domain::analytics"],
            basecamp_url="https://basecamp.example.com/catalog/table",
            ownership=OwnershipInfo(
                owner="data@example.com",
                team="@data-eng",
                stewards=["alice@example.com"],
                consumers=["@analytics"],
            ),
            columns=[
                ColumnInfo(name="user_id", data_type="STRING", fill_rate=1.0),
                ColumnInfo(
                    name="email", data_type="STRING", is_pii=True, fill_rate=0.98
                ),
            ],
            freshness=FreshnessInfo(
                last_updated=datetime.now(UTC),
                update_frequency="hourly",
                is_stale=False,
            ),
            quality=QualityInfo(
                score=95,
                total_tests=15,
                passed_tests=15,
                recent_tests=[
                    QualityTestResult(
                        test_name="not_null",
                        test_type="not_null",
                        status="pass",
                    )
                ],
            ),
            impact=ImpactSummary(
                total_downstream=3,
                tables=["table1", "table2"],
                metrics=["metric1"],
            ),
            sample_queries=[
                SampleQuery(
                    title="Active users",
                    sql="SELECT * FROM users WHERE active = true",
                    run_count=50,
                )
            ],
        )

        # Verify all components are accessible
        assert detail.ownership.owner == "data@example.com"
        assert len(detail.columns) == 2
        assert detail.columns[1].is_pii is True
        assert detail.freshness.is_stale is False
        assert detail.quality.score == 95
        assert detail.impact.total_downstream == 3
        assert len(detail.sample_queries) == 1

        # Verify computed properties
        assert detail.pii_column_count == 1
        assert detail.project == "project"
        assert detail.dataset == "dataset"
        assert detail.table == "table"

    def test_all_models_have_proper_exports(self) -> None:
        """All models should be properly exported from the module."""
        from dli.core.catalog import (
            ColumnInfo,
            FreshnessInfo,
            ImpactSummary,
            OwnershipInfo,
            QualityInfo,
            QualityTestResult,
            SampleQuery,
            TableDetail,
            TableInfo,
        )

        # Just verify imports work - if they don't, test will fail
        assert ColumnInfo is not None
        assert FreshnessInfo is not None
        assert ImpactSummary is not None
        assert OwnershipInfo is not None
        assert QualityInfo is not None
        assert QualityTestResult is not None
        assert SampleQuery is not None
        assert TableDetail is not None
        assert TableInfo is not None
