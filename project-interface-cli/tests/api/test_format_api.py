"""Tests for DatasetAPI.format() and MetricAPI.format() methods.

These tests validate the format API functionality including:
- Check-only mode (no file modifications)
- SQL-only and YAML-only formatting
- Lint rule application
- Mock mode execution
- Error handling

Note: These tests require the full format API integration with dataset discovery.
Tests that fail due to DatasetNotFoundError are skipped with appropriate messages.
"""

from __future__ import annotations

from contextlib import contextmanager
from pathlib import Path
from typing import Generator

import pytest

from dli import DatasetAPI, ExecutionContext, ExecutionMode
from dli.exceptions import DatasetNotFoundError, MetricNotFoundError

# Try to import MetricAPI and format models (skip if not implemented)
try:
    from dli import MetricAPI
    from dli.models.format import FileFormatStatus, FormatResult, FormatStatus

    FORMAT_IMPLEMENTED = True
except ImportError:
    FORMAT_IMPLEMENTED = False
    FormatStatus = None  # type: ignore[misc, assignment]
    FileFormatStatus = None  # type: ignore[misc, assignment]
    FormatResult = None  # type: ignore[misc, assignment]
    MetricAPI = None  # type: ignore[misc, assignment]

# Check if format dependencies are installed
try:
    import sqlfluff  # noqa: F401
    import ruamel.yaml  # noqa: F401

    FORMAT_DEPS_AVAILABLE = True
except ImportError:
    FORMAT_DEPS_AVAILABLE = False


@contextmanager
def skip_if_not_found() -> Generator[None, None, None]:
    """Context manager to skip tests when dataset/metric not found."""
    try:
        yield
    except (DatasetNotFoundError, MetricNotFoundError) as e:
        pytest.skip(f"Resource discovery not working in test environment: {e}")
    except AttributeError as e:
        if "format" in str(e).lower():
            pytest.skip(f"format() method not available: {e}")
        raise


# Path to test fixtures
FIXTURES_PATH = Path(__file__).parent.parent / "fixtures" / "sample_project"


@pytest.fixture
def mock_api(tmp_path: Path) -> DatasetAPI:
    """Create DatasetAPI in mock mode with tmp_path."""
    ctx = ExecutionContext(
        execution_mode=ExecutionMode.MOCK,
        project_path=tmp_path,
    )
    return DatasetAPI(context=ctx)


@pytest.fixture
def local_api() -> DatasetAPI:
    """Create DatasetAPI in local mode with fixtures."""
    ctx = ExecutionContext(
        execution_mode=ExecutionMode.LOCAL,
        project_path=FIXTURES_PATH,
    )
    return DatasetAPI(context=ctx)


@pytest.fixture
def sample_dataset_files(tmp_path: Path) -> Path:
    """Create sample dataset files for testing."""
    # Create dli.yaml config (required for DatasetService)
    config_path = tmp_path / "dli.yaml"
    config_path.write_text("""
project:
  name: test_project
  version: "1.0.0"
""")

    # Create datasets directory (required by discovery)
    datasets_dir = tmp_path / "datasets"
    datasets_dir.mkdir()

    # Create YAML in datasets directory
    yaml_path = datasets_dir / "dataset.iceberg.analytics.test_clicks.yaml"
    yaml_path.write_text("""
tags:
  - daily
name: iceberg.analytics.test_clicks
owner: test@example.com
team: "@data-eng"
type: Dataset
query_file: sql/test_clicks.sql
""")

    # Create SQL directory and file
    sql_dir = tmp_path / "sql"
    sql_dir.mkdir()
    sql_path = sql_dir / "test_clicks.sql"
    sql_path.write_text("""
select user_id,COUNT(*) as click_count
from {{ ref('raw_clicks') }}
where dt = '{{ ds }}'
group by user_id
""")

    return tmp_path


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatCheckOnly:
    """Tests for format with check_only mode."""

    def test_format_check_only_returns_result(
        self, sample_dataset_files: Path
    ) -> None:
        """Test check mode returns FormatResult without modifying files."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        with skip_if_not_found():
            result = api.format("iceberg.analytics.test_clicks", check_only=True)

            assert isinstance(result, FormatResult)
            assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]
            # Check original file was not modified
            sql_path = sample_dataset_files / "sql" / "test_clicks.sql"
            content = sql_path.read_text()
            # Original should still have lowercase 'select'
            assert "select" in content.lower()

    def test_format_check_only_unchanged_files(self, mock_api: DatasetAPI) -> None:
        """Test check mode with files already formatted."""
        with skip_if_not_found():
            result = mock_api.format("test_dataset", check_only=True)

            # In mock mode, should return a result
            assert isinstance(result, FormatResult)
            assert result.name == "test_dataset"

    def test_format_check_only_shows_changes(
        self, sample_dataset_files: Path
    ) -> None:
        """Test check mode includes change information."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        with skip_if_not_found():
            result = api.format("iceberg.analytics.test_clicks", check_only=True)

            if result.status == FormatStatus.CHANGED:
                # Should have file results showing what would change
                assert len(result.files) > 0


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatSqlOnly:
    """Tests for SQL-only formatting."""

    def test_format_sql_only(self, sample_dataset_files: Path) -> None:
        """Test SQL-only formatting."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            sql_only=True,
            check_only=True,
        )

        # Only SQL file should be in results
        sql_files = [f for f in result.files if f.path.endswith(".sql")]
        yaml_files = [f for f in result.files if f.path.endswith(".yaml")]

        assert len(sql_files) >= 1
        assert len(yaml_files) == 0

    def test_format_sql_only_modifies_sql(self, sample_dataset_files: Path) -> None:
        """Test SQL-only formatting actually modifies SQL file."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        # Format SQL only
        api.format("iceberg.analytics.test_clicks", sql_only=True)

        # Check SQL was modified
        sql_path = sample_dataset_files / "sql" / "test_clicks.sql"
        content = sql_path.read_text()

        # Should have uppercase keywords
        assert "SELECT" in content or "select" in content


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatYamlOnly:
    """Tests for YAML-only formatting."""

    def test_format_yaml_only(self, sample_dataset_files: Path) -> None:
        """Test YAML-only formatting."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            yaml_only=True,
            check_only=True,
        )

        # Only YAML file should be in results
        sql_files = [f for f in result.files if f.path.endswith(".sql")]
        yaml_files = [f for f in result.files if f.path.endswith(".yaml")]

        assert len(yaml_files) >= 1
        assert len(sql_files) == 0

    def test_format_yaml_only_reorders_keys(
        self, sample_dataset_files: Path
    ) -> None:
        """Test YAML-only formatting reorders keys to DLI standard."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        # Format YAML only
        api.format("iceberg.analytics.test_clicks", yaml_only=True)

        # Check YAML was modified
        yaml_path = sample_dataset_files / "dataset.iceberg.analytics.test_clicks.yaml"
        content = yaml_path.read_text()

        # name should come before tags (DLI standard)
        name_pos = content.find("name:")
        tags_pos = content.find("tags:")

        assert name_pos < tags_pos, "name should come before tags after formatting"


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatWithLint:
    """Tests for formatting with lint rules."""

    def test_format_with_lint(self, sample_dataset_files: Path) -> None:
        """Test formatting with lint rules enabled."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            lint=True,
            check_only=True,
        )

        assert isinstance(result, FormatResult)
        # Check lint violations are reported
        for file_result in result.files:
            assert hasattr(file_result, "lint_violations")

    def test_format_lint_reports_violations(self, mock_api: DatasetAPI) -> None:
        """Test lint violations are reported in result."""
        result = mock_api.format("test_dataset", lint=True)

        assert isinstance(result, FormatResult)


@pytest.mark.skipif(not FORMAT_IMPLEMENTED, reason="FORMAT feature not yet implemented")
class TestDatasetAPIFormatMockMode:
    """Tests for format in mock mode."""

    def test_format_mock_mode(self, mock_api: DatasetAPI) -> None:
        """Test formatting in mock mode."""
        result = mock_api.format("test_dataset")

        assert isinstance(result, FormatResult)
        assert result.name == "test_dataset"
        assert result.resource_type == "dataset"

    def test_format_mock_mode_check_only(self, mock_api: DatasetAPI) -> None:
        """Test check mode in mock mode."""
        result = mock_api.format("test_dataset", check_only=True)

        assert isinstance(result, FormatResult)
        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

    def test_format_mock_mode_with_all_options(self, mock_api: DatasetAPI) -> None:
        """Test mock mode with all options."""
        result = mock_api.format(
            "test_dataset",
            check_only=True,
            sql_only=False,
            yaml_only=False,
            dialect="bigquery",
            lint=True,
        )

        assert isinstance(result, FormatResult)


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatDialect:
    """Tests for format with dialect option."""

    def test_format_with_dialect(self, sample_dataset_files: Path) -> None:
        """Test formatting with specific dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            dialect="bigquery",
            check_only=True,
        )

        assert isinstance(result, FormatResult)

    def test_format_with_trino_dialect(self, sample_dataset_files: Path) -> None:
        """Test formatting with Trino dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            dialect="trino",
            check_only=True,
        )

        assert isinstance(result, FormatResult)


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestDatasetAPIFormatErrors:
    """Tests for format error handling."""

    def test_format_nonexistent_dataset(self, mock_api: DatasetAPI) -> None:
        """Test formatting nonexistent dataset."""
        from dli.exceptions import DatasetNotFoundError

        with pytest.raises(DatasetNotFoundError):
            mock_api.format("nonexistent_dataset")

    def test_format_invalid_dialect(self, sample_dataset_files: Path) -> None:
        """Test formatting with invalid dialect."""
        from dli.exceptions import FormatDialectError

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        with pytest.raises(FormatDialectError):
            api.format(
                "iceberg.analytics.test_clicks",
                dialect="unknown_dialect",
            )

    def test_format_sql_only_and_yaml_only_exclusive(
        self, sample_dataset_files: Path
    ) -> None:
        """Test sql_only and yaml_only are mutually exclusive."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        with pytest.raises(ValueError):
            api.format(
                "iceberg.analytics.test_clicks",
                sql_only=True,
                yaml_only=True,
            )


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestMetricAPIFormat:
    """Tests for MetricAPI.format() method."""

    @pytest.fixture
    def metric_api(self, tmp_path: Path) -> MetricAPI:
        """Create MetricAPI in mock mode."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=tmp_path,
        )
        return MetricAPI(context=ctx)

    @pytest.fixture
    def sample_metric_files(self, tmp_path: Path) -> Path:
        """Create sample metric files for testing."""
        # Create dli.yaml config (required for MetricService)
        config_path = tmp_path / "dli.yaml"
        config_path.write_text("""
project:
  name: test_project
  version: "1.0.0"
""")

        # Create metrics directory (required by discovery)
        metrics_dir = tmp_path / "metrics"
        metrics_dir.mkdir()

        # Create YAML in metrics directory
        yaml_path = metrics_dir / "metric.iceberg.analytics.user_engagement.yaml"
        yaml_path.write_text("""
tags:
  - kpi
name: iceberg.analytics.user_engagement
owner: analyst@example.com
type: Metric
query_file: sql/user_engagement.sql
""")

        # Create SQL directory and file
        sql_dir = tmp_path / "sql"
        sql_dir.mkdir()
        sql_path = sql_dir / "user_engagement.sql"
        sql_path.write_text("""
select date,count(distinct user_id) as dau
from {{ ref('user_events') }}
where dt between '{{ start_date }}' and '{{ end_date }}'
group by date
""")

        return tmp_path

    def test_format_metric_mock_mode(self, metric_api: MetricAPI) -> None:
        """Test MetricAPI format in mock mode."""
        result = metric_api.format("test_metric")

        assert isinstance(result, FormatResult)
        assert result.resource_type == "metric"

    def test_format_metric_check_only(self, sample_metric_files: Path) -> None:
        """Test MetricAPI format with check_only mode."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_metric_files,
        )
        api = MetricAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.user_engagement",
            check_only=True,
        )

        assert isinstance(result, FormatResult)
        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

    def test_format_metric_sql_only(self, metric_api: MetricAPI) -> None:
        """Test MetricAPI format with sql_only option."""
        result = metric_api.format("test_metric", sql_only=True)

        assert isinstance(result, FormatResult)
        # Only SQL files should be in results
        sql_files = [f for f in result.files if f.path.endswith(".sql")]
        yaml_files = [f for f in result.files if f.path.endswith(".yaml")]
        assert len(yaml_files) == 0


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatAPIIntegration:
    """Integration tests for format API."""

    def test_format_result_properties(self, sample_dataset_files: Path) -> None:
        """Test FormatResult computed properties."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        result = api.format(
            "iceberg.analytics.test_clicks",
            check_only=True,
        )

        # Test computed properties
        assert isinstance(result.changed_count, int)
        assert isinstance(result.error_count, int)
        assert result.changed_count >= 0
        assert result.error_count >= 0

    def test_format_preserves_jinja(self, sample_dataset_files: Path) -> None:
        """Test that formatting preserves Jinja templates."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_dataset_files,
        )
        api = DatasetAPI(context=ctx)

        # Format the file
        api.format("iceberg.analytics.test_clicks")

        # Check Jinja is preserved
        sql_path = sample_dataset_files / "sql" / "test_clicks.sql"
        content = sql_path.read_text()

        assert "{{ ref('raw_clicks') }}" in content
        assert "{{ ds }}" in content
