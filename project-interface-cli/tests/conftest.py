"""Pytest configuration and shared fixtures for dli tests."""
from __future__ import annotations

import re
from pathlib import Path
from typing import TYPE_CHECKING
from unittest.mock import Mock

import pytest

if TYPE_CHECKING:
    from dli.core.client import BasecampClient


# =============================================================================
# ANSI Code Utilities
# =============================================================================

# Regex pattern to match ANSI escape codes
ANSI_ESCAPE_PATTERN = re.compile(r"\x1b\[[0-9;]*m")


def strip_ansi(text: str) -> str:
    """Remove ANSI escape codes from text.

    This is useful for comparing CLI output in tests where
    Rich may output formatting codes depending on terminal settings.

    Args:
        text: Text potentially containing ANSI escape codes.

    Returns:
        Text with all ANSI codes removed.
    """
    return ANSI_ESCAPE_PATTERN.sub("", text)


# =============================================================================
# Path Fixtures
# =============================================================================


@pytest.fixture
def fixtures_path() -> Path:
    """Return path to test fixtures directory."""
    return Path(__file__).parent / "fixtures"


@pytest.fixture
def sample_project_path(fixtures_path: Path) -> Path:
    """Return path to sample project directory."""
    return fixtures_path / "sample_project"


@pytest.fixture
def tmp_project_path(tmp_path: Path) -> Path:
    """Create a temporary project directory with minimal structure."""
    project_dir = tmp_path / "test_project"
    project_dir.mkdir()

    # Create minimal dli.yaml
    (project_dir / "dli.yaml").write_text(
        """project_name: test_project
defaults:
  dialect: trino
  catalog: iceberg
  schema: analytics
"""
    )

    # Create directories
    (project_dir / "datasets").mkdir()
    (project_dir / "metrics").mkdir()

    return project_dir


# =============================================================================
# Mock Client Fixtures
# =============================================================================


@pytest.fixture
def mock_client() -> Mock:
    """Create a mock BasecampClient."""
    client = Mock()
    client.mock_mode = True
    client.base_url = "http://localhost:8081"
    return client


@pytest.fixture
def mock_server_response_success() -> dict:
    """Standard successful server response."""
    return {
        "success": True,
        "data": {},
        "error": None,
    }


@pytest.fixture
def mock_server_response_failure() -> dict:
    """Standard failed server response."""
    return {
        "success": False,
        "data": None,
        "error": "Resource not found",
    }


# =============================================================================
# Lineage Fixtures
# =============================================================================


@pytest.fixture
def mock_lineage_response() -> dict:
    """Mock lineage server response."""
    return {
        "success": True,
        "data": {
            "upstream": [
                {"name": "iceberg.raw.user_events", "type": "dataset"},
                {"name": "iceberg.dim.users", "type": "dataset"},
            ],
            "downstream": [
                {"name": "iceberg.reporting.user_summary", "type": "metric"},
            ],
        },
        "error": None,
    }


# =============================================================================
# Quality Test Fixtures
# =============================================================================


@pytest.fixture
def mock_quality_test_response_pass() -> dict:
    """Mock quality test pass response."""
    return {
        "success": True,
        "data": {
            "status": "pass",
            "failed_rows": 0,
            "failed_samples": [],
            "execution_time_ms": 150,
        },
        "error": None,
    }


@pytest.fixture
def mock_quality_test_response_fail() -> dict:
    """Mock quality test fail response."""
    return {
        "success": True,
        "data": {
            "status": "fail",
            "failed_rows": 3,
            "failed_samples": [
                {"user_id": None, "click_count": 10},
                {"user_id": None, "click_count": 5},
                {"user_id": None, "click_count": 2},
            ],
            "execution_time_ms": 200,
        },
        "error": None,
    }


# =============================================================================
# Sample YAML Fixtures
# =============================================================================


@pytest.fixture
def sample_dataset_yaml() -> str:
    """Sample dataset YAML specification."""
    return """name: iceberg.analytics.daily_clicks
owner: engineer@example.com
team: "@data-eng"
type: Dataset
query_type: DML

query_file: daily_clicks.sql

parameters:
  - name: execution_date
    type: string
    required: true
    description: "Execution date in YYYY-MM-DD format"

tests:
  - type: not_null
    columns: [user_id, click_count, dt]
  - type: unique
    columns: [user_id, dt]
  - type: accepted_values
    column: device_type
    values: ["mobile", "desktop", "tablet"]
"""


@pytest.fixture
def sample_metric_yaml() -> str:
    """Sample metric YAML specification."""
    return """name: iceberg.analytics.user_engagement
owner: analyst@example.com
team: "@data-analytics"
type: Metric
query_type: SELECT

query_file: user_engagement.sql

parameters:
  - name: start_date
    type: string
    required: true
  - name: end_date
    type: string
    required: true
"""


@pytest.fixture
def sample_sql_valid() -> str:
    """Sample valid SQL query."""
    return """
SELECT
    user_id,
    COUNT(*) as click_count,
    dt
FROM iceberg.raw.user_events
WHERE dt = '{{ execution_date }}'
GROUP BY user_id, dt
"""


@pytest.fixture
def sample_sql_invalid() -> str:
    """Sample invalid SQL query (syntax error)."""
    return """
SELEC user_id
FORM iceberg.raw.user_events
"""


@pytest.fixture
def sample_sql_with_warning() -> str:
    """Sample SQL with warnings (SELECT *)."""
    return """
SELECT *
FROM iceberg.raw.user_events
"""
