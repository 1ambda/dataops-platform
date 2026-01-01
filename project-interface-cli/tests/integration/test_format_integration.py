"""Integration tests for FORMAT feature.

End-to-end tests that verify the complete format workflow:
- File creation, formatting, and verification
- SQL and YAML formatting together
- Jinja template preservation
- Key ordering in YAML files
"""

from __future__ import annotations

from pathlib import Path

import pytest

# Check if FORMAT feature is implemented
try:
    from dli import DatasetAPI, ExecutionContext, ExecutionMode, MetricAPI
    from dli.models.format import FileFormatStatus, FormatResult, FormatStatus

    FORMAT_IMPLEMENTED = True
except ImportError:
    FORMAT_IMPLEMENTED = False
    DatasetAPI = None  # type: ignore[misc, assignment]
    MetricAPI = None  # type: ignore[misc, assignment]
    ExecutionContext = None  # type: ignore[misc, assignment]
    ExecutionMode = None  # type: ignore[misc, assignment]
    FormatStatus = None  # type: ignore[misc, assignment]
    FileFormatStatus = None  # type: ignore[misc, assignment]
    FormatResult = None  # type: ignore[misc, assignment]

# Check if format dependencies are installed
try:
    import sqlfluff  # noqa: F401
    import ruamel.yaml  # noqa: F401

    FORMAT_DEPS_AVAILABLE = True
except ImportError:
    FORMAT_DEPS_AVAILABLE = False


@pytest.fixture
def sample_project(tmp_path: Path) -> Path:
    """Create a complete sample project for testing."""
    # Create project config
    (tmp_path / "dli.yaml").write_text("""
project:
  name: test_project
  version: "1.0.0"
defaults:
  dialect: bigquery
  catalog: iceberg
  schema: analytics
""")

    # Create datasets directory
    datasets_dir = tmp_path / "datasets"
    datasets_dir.mkdir()

    # Create sql directory
    sql_dir = tmp_path / "sql"
    sql_dir.mkdir()

    return tmp_path


@pytest.fixture
def unformatted_dataset(sample_project: Path) -> Path:
    """Create a dataset with unformatted SQL and YAML."""
    datasets_dir = sample_project / "datasets"

    # Create unformatted YAML (keys out of order) in datasets directory
    yaml_path = datasets_dir / "dataset.iceberg.analytics.daily_clicks.yaml"
    yaml_path.write_text("""
# Dataset configuration
tags:
  - daily
  - analytics
query_file: sql/daily_clicks.sql
name: iceberg.analytics.daily_clicks
owner: engineer@example.com
team: "@data-eng"
type: Dataset
description: "Daily click aggregation"
parameters:
  - name: execution_date
    type: string
    required: true
""")

    # Create unformatted SQL (lowercase keywords, poor formatting)
    sql_path = sample_project / "sql" / "daily_clicks.sql"
    sql_path.write_text("""
select user_id,COUNT(*) as click_count,dt
from {{ ref('raw_clicks') }}
where dt = '{{ execution_date }}'
and status = 'active'
group by user_id,dt
having COUNT(*) > 0
order by click_count desc
""")

    return sample_project


@pytest.fixture
def unformatted_metric(sample_project: Path) -> Path:
    """Create a metric with unformatted SQL and YAML."""
    # Create metrics directory
    metrics_dir = sample_project / "metrics"
    metrics_dir.mkdir(exist_ok=True)

    # Create unformatted YAML in metrics directory
    yaml_path = metrics_dir / "metric.iceberg.analytics.user_engagement.yaml"
    yaml_path.write_text("""
tags:
  - kpi
  - weekly
query_file: sql/user_engagement.sql
name: iceberg.analytics.user_engagement
owner: analyst@example.com
team: "@analytics"
type: Metric
""")

    # Create unformatted SQL
    sql_path = sample_project / "sql" / "user_engagement.sql"
    sql_path.write_text("""
select date,count(distinct user_id) dau,sum(events) total_events
from {{ ref('user_events') }}
where dt between '{{ start_date }}' and '{{ end_date }}'
group by date
""")

    return sample_project


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFullFormatFlow:
    """End-to-end format workflow tests."""

    def test_full_format_flow_dataset(self, unformatted_dataset: Path) -> None:
        """Test complete format workflow for dataset."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        # Step 1: Check first (should report changes needed)
        check_result = api.format("iceberg.analytics.daily_clicks", check_only=True)

        assert isinstance(check_result, FormatResult)
        assert check_result.status in [FormatStatus.CHANGED, FormatStatus.SUCCESS]

        # Step 2: Apply format
        format_result = api.format("iceberg.analytics.daily_clicks")

        assert format_result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

        # Step 3: Verify formatting applied to SQL
        sql_path = unformatted_dataset / "sql" / "daily_clicks.sql"
        sql_content = sql_path.read_text()

        # Keywords should be uppercase
        assert "SELECT" in sql_content or "select" in sql_content
        # Jinja should be preserved
        assert "{{ ref('raw_clicks') }}" in sql_content
        assert "{{ execution_date }}" in sql_content

        # Step 4: Verify formatting applied to YAML
        yaml_path = unformatted_dataset / "dataset.iceberg.analytics.daily_clicks.yaml"
        yaml_content = yaml_path.read_text()

        # name should come before tags (DLI standard order)
        name_pos = yaml_content.find("name:")
        tags_pos = yaml_content.find("tags:")
        assert name_pos < tags_pos, "name should come before tags after formatting"

        # Step 5: Check again (should report no changes)
        recheck_result = api.format("iceberg.analytics.daily_clicks", check_only=True)
        assert recheck_result.status == FormatStatus.SUCCESS

    def test_full_format_flow_metric(self, unformatted_metric: Path) -> None:
        """Test complete format workflow for metric."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_metric,
        )
        api = MetricAPI(context=ctx)

        # Check first
        check_result = api.format("iceberg.analytics.user_engagement", check_only=True)
        assert isinstance(check_result, FormatResult)

        # Apply format
        format_result = api.format("iceberg.analytics.user_engagement")
        assert format_result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

        # Verify SQL formatting
        sql_path = unformatted_metric / "sql" / "user_engagement.sql"
        sql_content = sql_path.read_text()

        # Jinja preserved
        assert "{{ ref('user_events') }}" in sql_content
        assert "{{ start_date }}" in sql_content
        assert "{{ end_date }}" in sql_content


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatJinjaPreservation:
    """Tests for Jinja template preservation during formatting."""

    def test_jinja_ref_function_preserved(self, unformatted_dataset: Path) -> None:
        """Test that ref() function is preserved."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        api.format("iceberg.analytics.daily_clicks")

        sql_path = unformatted_dataset / "sql" / "daily_clicks.sql"
        content = sql_path.read_text()

        assert "{{ ref('raw_clicks') }}" in content

    def test_jinja_variables_preserved(self, unformatted_dataset: Path) -> None:
        """Test that Jinja variables are preserved."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        api.format("iceberg.analytics.daily_clicks")

        sql_path = unformatted_dataset / "sql" / "daily_clicks.sql"
        content = sql_path.read_text()

        assert "{{ execution_date }}" in content

    def test_complex_jinja_preserved(self, sample_project: Path) -> None:
        """Test that complex Jinja constructs are preserved."""
        # Create SQL with complex Jinja in datasets directory
        datasets_dir = sample_project / "datasets"
        yaml_path = datasets_dir / "dataset.test.complex.yaml"
        yaml_path.write_text("""
name: test.complex
owner: test@example.com
type: Dataset
query_file: sql/complex.sql
""")

        sql_path = sample_project / "sql" / "complex.sql"
        sql_path.write_text("""
{% set columns = ['a', 'b', 'c'] %}
select
    {% for col in columns %}
    {{ col }}{% if not loop.last %},{% endif %}
    {% endfor %}
from table
{% if condition %}
where status = 'active'
{% endif %}
""")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_project,
        )
        api = DatasetAPI(context=ctx)

        api.format("test.complex")

        content = sql_path.read_text()

        # All Jinja constructs should be preserved
        assert "{% set columns" in content or "{%" in content
        assert "{% for col" in content or "for" in content
        assert "{% if condition %}" in content or "{% if" in content
        assert "{% endif %}" in content


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatYamlKeyOrder:
    """Tests for YAML key ordering."""

    def test_yaml_key_order_name_first(self, unformatted_dataset: Path) -> None:
        """Test that 'name' key comes first."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        api.format("iceberg.analytics.daily_clicks", yaml_only=True)

        yaml_path = unformatted_dataset / "dataset.iceberg.analytics.daily_clicks.yaml"
        content = yaml_path.read_text()

        # Find first non-comment, non-empty line
        lines = content.split("\n")
        content_lines = [l for l in lines if l.strip() and not l.strip().startswith("#")]

        if content_lines:
            first_key_line = content_lines[0]
            assert first_key_line.strip().startswith("name:")

    def test_yaml_key_order_owner_before_tags(
        self, unformatted_dataset: Path
    ) -> None:
        """Test that 'owner' comes before 'tags'."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        api.format("iceberg.analytics.daily_clicks", yaml_only=True)

        yaml_path = unformatted_dataset / "dataset.iceberg.analytics.daily_clicks.yaml"
        content = yaml_path.read_text()

        owner_pos = content.find("owner:")
        tags_pos = content.find("tags:")

        # Both should exist
        assert owner_pos >= 0, "owner: should be in YAML"
        assert tags_pos >= 0, "tags: should be in YAML"

        # owner should come before tags
        assert owner_pos < tags_pos


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatSqlKeywords:
    """Tests for SQL keyword formatting."""

    def test_sql_keywords_uppercase(self, unformatted_dataset: Path) -> None:
        """Test that SQL keywords are uppercased."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        api.format("iceberg.analytics.daily_clicks", sql_only=True)

        sql_path = unformatted_dataset / "sql" / "daily_clicks.sql"
        content = sql_path.read_text()

        # Check uppercase keywords
        assert "SELECT" in content or "SELECT" in content.upper()
        assert "FROM" in content or "FROM" in content.upper()
        assert "WHERE" in content or "WHERE" in content.upper()
        assert "GROUP BY" in content or "GROUP BY" in content.upper()

    def test_sql_adds_as_keyword(self, sample_project: Path) -> None:
        """Test that AS keyword is added for aliases."""
        datasets_dir = sample_project / "datasets"
        yaml_path = datasets_dir / "dataset.test.alias.yaml"
        yaml_path.write_text("""
name: test.alias
owner: test@example.com
type: Dataset
query_file: sql/alias.sql
""")

        sql_path = sample_project / "sql" / "alias.sql"
        sql_path.write_text("SELECT COUNT(*) cnt FROM table")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_project,
        )
        api = DatasetAPI(context=ctx)

        api.format("test.alias", sql_only=True)

        content = sql_path.read_text()

        # Should have explicit AS keyword
        assert "AS" in content or "as" in content.lower()


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatIdempotency:
    """Tests for format idempotency (running twice produces same result)."""

    def test_format_idempotent(self, unformatted_dataset: Path) -> None:
        """Test that formatting twice produces same result."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        # First format
        api.format("iceberg.analytics.daily_clicks")

        sql_path = unformatted_dataset / "sql" / "daily_clicks.sql"
        yaml_path = unformatted_dataset / "dataset.iceberg.analytics.daily_clicks.yaml"

        content_after_first_format_sql = sql_path.read_text()
        content_after_first_format_yaml = yaml_path.read_text()

        # Second format
        api.format("iceberg.analytics.daily_clicks")

        content_after_second_format_sql = sql_path.read_text()
        content_after_second_format_yaml = yaml_path.read_text()

        # Content should be identical
        assert content_after_first_format_sql == content_after_second_format_sql
        assert content_after_first_format_yaml == content_after_second_format_yaml

    def test_check_after_format_returns_success(
        self, unformatted_dataset: Path
    ) -> None:
        """Test that check after format returns SUCCESS."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=unformatted_dataset,
        )
        api = DatasetAPI(context=ctx)

        # Format first
        api.format("iceberg.analytics.daily_clicks")

        # Then check - should be SUCCESS (no changes needed)
        result = api.format("iceberg.analytics.daily_clicks", check_only=True)

        assert result.status == FormatStatus.SUCCESS
        assert result.changed_count == 0


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatCommentPreservation:
    """Tests for comment preservation during formatting."""

    def test_yaml_comments_preserved(self, sample_project: Path) -> None:
        """Test that YAML comments are preserved."""
        datasets_dir = sample_project / "datasets"
        yaml_path = datasets_dir / "dataset.test.comments.yaml"
        yaml_path.write_text("""
# This is a dataset for testing
name: test.comments
# Owner information
owner: test@example.com  # inline comment
type: Dataset
query_file: sql/comments.sql
""")

        sql_path = sample_project / "sql" / "comments.sql"
        sql_path.write_text("SELECT 1")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_project,
        )
        api = DatasetAPI(context=ctx)

        api.format("test.comments", yaml_only=True)

        content = yaml_path.read_text()

        # Comments should be preserved
        assert "# This is a dataset" in content or "testing" in content
        assert "# Owner information" in content or "Owner" in content

    def test_sql_comments_preserved(self, sample_project: Path) -> None:
        """Test that SQL comments are preserved."""
        datasets_dir = sample_project / "datasets"
        yaml_path = datasets_dir / "dataset.test.sqlcomments.yaml"
        yaml_path.write_text("""
name: test.sqlcomments
owner: test@example.com
type: Dataset
query_file: sql/sqlcomments.sql
""")

        sql_path = sample_project / "sql" / "sqlcomments.sql"
        sql_path.write_text("""
-- Get user data
select user_id, -- user identifier
       count(*) as cnt
from users
/* Filter active users */
where status = 'active'
""")

        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=sample_project,
        )
        api = DatasetAPI(context=ctx)

        api.format("test.sqlcomments", sql_only=True)

        content = sql_path.read_text()

        # Comments should be preserved
        assert "--" in content or "Get user data" in content
        assert "/*" in content or "Filter active" in content


@pytest.mark.skipif(
    not FORMAT_IMPLEMENTED or not FORMAT_DEPS_AVAILABLE,
    reason="FORMAT feature not implemented or dependencies missing (sqlfluff, ruamel.yaml)",
)
class TestFormatDifferentDialects:
    """Tests for formatting with different SQL dialects."""

    @pytest.fixture
    def dialect_project(self, sample_project: Path) -> Path:
        """Create project with SQL for dialect testing."""
        datasets_dir = sample_project / "datasets"
        yaml_path = datasets_dir / "dataset.test.dialect.yaml"
        yaml_path.write_text("""
name: test.dialect
owner: test@example.com
type: Dataset
query_file: sql/dialect.sql
""")

        sql_path = sample_project / "sql" / "dialect.sql"
        sql_path.write_text("select a,b from t where x=1")

        return sample_project

    def test_format_bigquery_dialect(self, dialect_project: Path) -> None:
        """Test formatting with BigQuery dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=dialect_project,
        )
        api = DatasetAPI(context=ctx)

        result = api.format("test.dialect", dialect="bigquery")

        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

    def test_format_trino_dialect(self, dialect_project: Path) -> None:
        """Test formatting with Trino dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=dialect_project,
        )
        api = DatasetAPI(context=ctx)

        result = api.format("test.dialect", dialect="trino")

        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]

    def test_format_snowflake_dialect(self, dialect_project: Path) -> None:
        """Test formatting with Snowflake dialect."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.LOCAL,
            project_path=dialect_project,
        )
        api = DatasetAPI(context=ctx)

        result = api.format("test.dialect", dialect="snowflake")

        assert result.status in [FormatStatus.SUCCESS, FormatStatus.CHANGED]
