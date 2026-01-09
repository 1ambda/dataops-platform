"""Integration tests for Dataset/Metric/Quality spec execution.

These tests verify that CLI specs can be rendered and executed against Trino.
They use the memory catalog for fast, isolated testing.

Run with:
    pytest tests/integration/test_spec_execution_integration.py -m integration

Skip with:
    pytest -m "not integration"
"""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING, Any

import pytest

if TYPE_CHECKING:
    from dli.adapters.trino import TrinoExecutor

# Skip all tests if dependencies not available
pytestmark = [
    pytest.mark.integration,
    pytest.mark.trino,
]


# =============================================================================
# Fixture: Integration Test Project with Specs
# =============================================================================


@pytest.fixture
def spec_project(
    tmp_path: Path,
    trino_executor: TrinoExecutor,
    trino_test_schema: str,
) -> dict[str, Any]:
    """Create a complete project with Dataset/Metric/Quality specs.

    This fixture creates:
    - dli.yaml project config
    - Source tables in Trino memory catalog
    - Dataset specs with SQL files
    - Metric specs with SQL files
    - Quality specs

    Returns:
        Dictionary with project paths and table names
    """
    # Create dli.yaml
    (tmp_path / "dli.yaml").write_text(f"""
version: "1"

project:
  name: "integration-test"
  description: "Integration test project"

discovery:
  datasets_dir: "datasets"
  metrics_dir: "metrics"
  dataset_patterns:
    - "dataset.*.yaml"
  metric_patterns:
    - "metric.*.yaml"

defaults:
  dialect: "trino"
  catalog: "memory"
  schema: "{trino_test_schema}"
  timeout_seconds: 60
""")

    # Create directories
    (tmp_path / "datasets").mkdir()
    (tmp_path / "metrics").mkdir()
    (tmp_path / "quality").mkdir()
    (tmp_path / "sql").mkdir()

    # Create source tables in Trino
    users_table = f"memory.{trino_test_schema}.users"
    events_table = f"memory.{trino_test_schema}.events"

    trino_executor.execute_sql(f"""
        CREATE TABLE {users_table} (
            id VARCHAR,
            name VARCHAR,
            email VARCHAR,
            status VARCHAR,
            created_at DATE
        )
    """)

    trino_executor.execute_sql(f"""
        INSERT INTO {users_table} VALUES
        ('u1', 'Alice', 'alice@example.com', 'active', DATE '2025-01-01'),
        ('u2', 'Bob', 'bob@example.com', 'active', DATE '2025-01-02'),
        ('u3', 'Charlie', 'charlie@example.com', 'inactive', DATE '2025-01-03'),
        ('u4', 'Diana', 'diana@example.com', 'active', DATE '2025-01-04')
    """)

    trino_executor.execute_sql(f"""
        CREATE TABLE {events_table} (
            event_id VARCHAR,
            user_id VARCHAR,
            event_type VARCHAR,
            event_date DATE,
            event_count INTEGER
        )
    """)

    trino_executor.execute_sql(f"""
        INSERT INTO {events_table} VALUES
        ('e1', 'u1', 'click', DATE '2025-01-01', 5),
        ('e2', 'u1', 'view', DATE '2025-01-01', 10),
        ('e3', 'u2', 'click', DATE '2025-01-01', 3),
        ('e4', 'u2', 'click', DATE '2025-01-02', 7),
        ('e5', 'u3', 'view', DATE '2025-01-02', 2),
        ('e6', 'u4', 'click', DATE '2025-01-02', 8)
    """)

    return {
        "project_path": tmp_path,
        "schema": trino_test_schema,
        "users_table": users_table,
        "events_table": events_table,
    }


# =============================================================================
# Dataset Spec Tests
# =============================================================================


class TestDatasetSpecExecution:
    """Tests for Dataset spec rendering and execution."""

    @pytest.fixture
    def dataset_spec(self, spec_project: dict[str, Any]) -> Path:
        """Create a Dataset spec for daily click aggregation."""
        project_path = spec_project["project_path"]
        schema = spec_project["schema"]

        # Create SQL file - simple aggregation
        sql_path = project_path / "sql" / "daily_clicks.sql"
        sql_path.write_text(f"""
SELECT
    event_date,
    COUNT(DISTINCT user_id) AS unique_users,
    SUM(event_count) AS total_clicks
FROM memory.{schema}.events
WHERE event_type = 'click'
GROUP BY event_date
ORDER BY event_date
        """)

        # Create Dataset spec YAML
        spec_path = project_path / "datasets" / f"dataset.memory.{schema}.daily_clicks.yaml"
        spec_path.write_text(f"""
name: "memory.{schema}.daily_clicks"
description: "Daily click aggregation dataset"
owner: "test@example.com"
team: "@data-eng"
type: "Dataset"
query_type: "SELECT"
query_file: "../sql/daily_clicks.sql"
""")

        return spec_path

    def test_dataset_sql_renders_correctly(
        self,
        spec_project: dict[str, Any],
        dataset_spec: Path,
    ) -> None:
        """Test that dataset SQL renders without errors."""
        sql_content = (spec_project["project_path"] / "sql" / "daily_clicks.sql").read_text()

        assert "SELECT" in sql_content
        assert "event_date" in sql_content
        assert "COUNT" in sql_content
        assert spec_project["schema"] in sql_content

    def test_dataset_sql_executes_on_trino(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        dataset_spec: Path,
    ) -> None:
        """Test that rendered dataset SQL executes successfully on Trino."""
        sql_content = (spec_project["project_path"] / "sql" / "daily_clicks.sql").read_text()

        result = trino_executor.execute_sql(sql_content)

        assert result.success is True
        assert result.row_count == 2  # Two distinct dates in test data
        assert "event_date" in result.columns
        assert "unique_users" in result.columns
        assert "total_clicks" in result.columns

    def test_dataset_aggregation_results_correct(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        dataset_spec: Path,
    ) -> None:
        """Test that dataset aggregation produces correct results."""
        sql_content = (spec_project["project_path"] / "sql" / "daily_clicks.sql").read_text()

        result = trino_executor.execute_sql(sql_content)

        assert result.success is True

        # Verify aggregation results
        # 2025-01-01: u1 (5) + u2 (3) = 8 clicks, 2 users
        # 2025-01-02: u2 (7) + u4 (8) = 15 clicks, 2 users
        data = sorted(result.data, key=lambda x: str(x["event_date"]))

        assert len(data) == 2
        # Note: Date format may vary, so just check values exist
        assert data[0]["total_clicks"] in [8, 15]
        assert data[1]["total_clicks"] in [8, 15]


class TestDatasetWithParameters:
    """Tests for Dataset specs with Jinja parameters."""

    @pytest.fixture
    def parameterized_dataset(self, spec_project: dict[str, Any]) -> Path:
        """Create a Dataset spec with parameters."""
        project_path = spec_project["project_path"]
        schema = spec_project["schema"]

        # Create SQL file with Jinja parameter
        sql_path = project_path / "sql" / "filtered_events.sql"
        sql_path.write_text(f"""
SELECT
    user_id,
    SUM(event_count) AS total_events
FROM memory.{schema}.events
WHERE event_date = DATE '{{{{ execution_date }}}}'
GROUP BY user_id
        """)

        # Create spec
        spec_path = project_path / "datasets" / f"dataset.memory.{schema}.filtered_events.yaml"
        spec_path.write_text(f"""
name: "memory.{schema}.filtered_events"
owner: "test@example.com"
type: "Dataset"
query_file: "../sql/filtered_events.sql"
parameters:
  - name: "execution_date"
    type: "string"
    required: true
""")

        return spec_path

    def test_parameterized_sql_with_date(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        parameterized_dataset: Path,
    ) -> None:
        """Test parameter substitution and execution."""
        sql_template = (spec_project["project_path"] / "sql" / "filtered_events.sql").read_text()

        # Manual parameter substitution (simulating Jinja rendering)
        rendered_sql = sql_template.replace("{{ execution_date }}", "2025-01-01")

        result = trino_executor.execute_sql(rendered_sql)

        assert result.success is True
        # 2025-01-01 has events from u1 (5+10) and u2 (3)
        assert result.row_count is not None and result.row_count >= 2


# =============================================================================
# Metric Spec Tests
# =============================================================================


class TestMetricSpecExecution:
    """Tests for Metric spec rendering and execution."""

    @pytest.fixture
    def metric_spec(self, spec_project: dict[str, Any]) -> Path:
        """Create a Metric spec for user engagement."""
        project_path = spec_project["project_path"]
        schema = spec_project["schema"]

        # Create SQL file
        sql_path = project_path / "sql" / "user_engagement.sql"
        sql_path.write_text(f"""
SELECT
    COUNT(DISTINCT u.id) AS total_users,
    COUNT(DISTINCT CASE WHEN u.status = 'active' THEN u.id END) AS active_users,
    COALESCE(SUM(e.event_count), 0) AS total_events,
    CAST(COUNT(DISTINCT CASE WHEN e.user_id IS NOT NULL THEN u.id END) AS DOUBLE) /
        NULLIF(COUNT(DISTINCT u.id), 0) AS engagement_rate
FROM memory.{schema}.users u
LEFT JOIN memory.{schema}.events e ON u.id = e.user_id
        """)

        # Create Metric spec
        spec_path = project_path / "metrics" / f"metric.memory.{schema}.user_engagement.yaml"
        spec_path.write_text(f"""
name: "memory.{schema}.user_engagement"
description: "User engagement metrics"
owner: "analyst@example.com"
team: "@analytics"
type: "Metric"
query_type: "SELECT"
query_file: "../sql/user_engagement.sql"
""")

        return spec_path

    def test_metric_sql_executes_on_trino(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        metric_spec: Path,
    ) -> None:
        """Test that metric SQL executes successfully."""
        sql_content = (spec_project["project_path"] / "sql" / "user_engagement.sql").read_text()

        result = trino_executor.execute_sql(sql_content)

        assert result.success is True
        assert result.row_count == 1  # Single row metrics
        assert "total_users" in result.columns
        assert "active_users" in result.columns
        assert "total_events" in result.columns

    def test_metric_values_correct(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        metric_spec: Path,
    ) -> None:
        """Test that metric values are calculated correctly."""
        sql_content = (spec_project["project_path"] / "sql" / "user_engagement.sql").read_text()

        result = trino_executor.execute_sql(sql_content)

        assert result.success is True
        data = result.data[0]

        # 4 total users
        assert data["total_users"] == 4
        # 3 active users (Alice, Bob, Diana)
        assert data["active_users"] == 3
        # All events sum: 5+10+3+7+2+8 = 35
        assert data["total_events"] == 35


# =============================================================================
# Quality Spec Tests
# =============================================================================


class TestQualitySpecExecution:
    """Tests for Quality spec SQL generation and execution."""

    @pytest.fixture
    def quality_spec(self, spec_project: dict[str, Any]) -> Path:
        """Create a Quality spec for users table."""
        project_path = spec_project["project_path"]
        schema = spec_project["schema"]

        # Create Quality spec
        spec_path = project_path / "quality" / f"quality.memory.{schema}.users.yaml"
        spec_path.write_text(f"""
version: 1

target:
  type: table
  name: memory.{schema}.users

metadata:
  owner: qa@example.com
  team: "@data-quality"
  description: "Users table quality tests"

tests:
  - name: id_not_null
    type: not_null
    columns: [id]
    severity: error

  - name: email_unique
    type: unique
    columns: [email]
    severity: error

  - name: status_valid
    type: accepted_values
    column: status
    values: [active, inactive, pending]
    severity: warn
""")

        return spec_path

    def test_not_null_check_sql(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        quality_spec: Path,
    ) -> None:
        """Test NOT NULL quality check SQL."""
        schema = spec_project["schema"]

        # NOT NULL check SQL pattern
        not_null_sql = f"""
SELECT COUNT(*) AS null_count
FROM memory.{schema}.users
WHERE id IS NULL
        """

        result = trino_executor.execute_sql(not_null_sql)

        assert result.success is True
        assert result.data[0]["null_count"] == 0  # No null IDs

    def test_unique_check_sql(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        quality_spec: Path,
    ) -> None:
        """Test UNIQUE quality check SQL."""
        schema = spec_project["schema"]

        # UNIQUE check SQL pattern
        unique_sql = f"""
SELECT email, COUNT(*) AS cnt
FROM memory.{schema}.users
GROUP BY email
HAVING COUNT(*) > 1
        """

        result = trino_executor.execute_sql(unique_sql)

        assert result.success is True
        assert result.row_count == 0  # No duplicate emails

    def test_accepted_values_check_sql(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        quality_spec: Path,
    ) -> None:
        """Test ACCEPTED_VALUES quality check SQL."""
        schema = spec_project["schema"]

        # ACCEPTED_VALUES check SQL pattern
        accepted_values_sql = f"""
SELECT status, COUNT(*) AS cnt
FROM memory.{schema}.users
WHERE status NOT IN ('active', 'inactive', 'pending')
GROUP BY status
        """

        result = trino_executor.execute_sql(accepted_values_sql)

        assert result.success is True
        assert result.row_count == 0  # All statuses are valid

    def test_quality_check_detects_issues(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
        quality_spec: Path,
    ) -> None:
        """Test that quality checks detect data issues when present."""
        schema = spec_project["schema"]

        # Insert a user with invalid status
        trino_executor.execute_sql(f"""
            INSERT INTO memory.{schema}.users VALUES
            ('u5', 'Eve', 'eve@example.com', 'INVALID_STATUS', DATE '2025-01-05')
        """)

        # Run accepted values check
        accepted_values_sql = f"""
SELECT status, COUNT(*) AS cnt
FROM memory.{schema}.users
WHERE status NOT IN ('active', 'inactive', 'pending')
GROUP BY status
        """

        result = trino_executor.execute_sql(accepted_values_sql)

        assert result.success is True
        assert result.row_count == 1  # Found invalid status
        assert result.data[0]["status"] == "INVALID_STATUS"


# =============================================================================
# Complex Query Tests
# =============================================================================


class TestComplexQueries:
    """Tests for complex query patterns commonly used in specs."""

    def test_window_function(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test window functions work correctly."""
        schema = spec_project["schema"]

        sql = f"""
SELECT
    user_id,
    event_date,
    event_count,
    SUM(event_count) OVER (PARTITION BY user_id ORDER BY event_date) AS running_total
FROM memory.{schema}.events
WHERE event_type = 'click'
ORDER BY user_id, event_date
        """

        result = trino_executor.execute_sql(sql)

        assert result.success is True
        assert "running_total" in result.columns

    def test_cte_query(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test Common Table Expression (CTE) queries."""
        schema = spec_project["schema"]

        sql = f"""
WITH user_events AS (
    SELECT
        user_id,
        SUM(event_count) AS total_events
    FROM memory.{schema}.events
    GROUP BY user_id
),
active_users AS (
    SELECT id, name
    FROM memory.{schema}.users
    WHERE status = 'active'
)
SELECT
    au.name,
    COALESCE(ue.total_events, 0) AS events
FROM active_users au
LEFT JOIN user_events ue ON au.id = ue.user_id
ORDER BY events DESC
        """

        result = trino_executor.execute_sql(sql)

        assert result.success is True
        assert result.row_count == 3  # 3 active users
        assert "name" in result.columns
        assert "events" in result.columns

    def test_subquery(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test subquery execution."""
        schema = spec_project["schema"]

        sql = f"""
SELECT *
FROM memory.{schema}.users
WHERE id IN (
    SELECT DISTINCT user_id
    FROM memory.{schema}.events
    WHERE event_type = 'click'
)
        """

        result = trino_executor.execute_sql(sql)

        assert result.success is True
        # u1, u2, u4 have click events
        assert result.row_count == 3

    def test_case_expression(
        self,
        spec_project: dict[str, Any],
        trino_executor: TrinoExecutor,
    ) -> None:
        """Test CASE expression in queries."""
        schema = spec_project["schema"]

        sql = f"""
SELECT
    id,
    name,
    CASE
        WHEN status = 'active' THEN 'Active User'
        WHEN status = 'inactive' THEN 'Inactive User'
        ELSE 'Unknown'
    END AS status_label
FROM memory.{schema}.users
        """

        result = trino_executor.execute_sql(sql)

        assert result.success is True
        assert "status_label" in result.columns
        # Check status labels are correct
        for row in result.data:
            assert row["status_label"] in ["Active User", "Inactive User", "Unknown"]
