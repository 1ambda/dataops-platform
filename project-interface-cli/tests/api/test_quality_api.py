"""Tests for QualityAPI."""

from datetime import UTC, datetime
from pathlib import Path

import pytest

from dli import QualityAPI, ExecutionContext, ExecutionMode
from dli.exceptions import QualitySpecNotFoundError, QualitySpecParseError
from dli.models.quality import DqQualityResult, DqStatus, QualityInfo, QualityTargetType


# Fixtures
FIXTURES_PATH = Path(__file__).parent.parent / "fixtures" / "sample_project"


@pytest.fixture
def mock_api() -> QualityAPI:
    """Create QualityAPI in mock mode."""
    ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
    return QualityAPI(context=ctx)


@pytest.fixture
def local_api() -> QualityAPI:
    """Create QualityAPI in local mode with fixtures."""
    ctx = ExecutionContext(
        execution_mode=ExecutionMode.LOCAL,
        project_path=FIXTURES_PATH,
    )
    return QualityAPI(context=ctx)


class TestQualityAPIListQualities:
    """Tests for list_qualities method."""

    def test_list_all_mock(self, mock_api: QualityAPI) -> None:
        """Test listing all qualities in mock mode."""
        qualities = mock_api.list_qualities()

        assert len(qualities) > 0
        assert all(isinstance(q, QualityInfo) for q in qualities)

    def test_list_filter_by_target_type(self, mock_api: QualityAPI) -> None:
        """Test filtering by target type."""
        qualities = mock_api.list_qualities(target_type="dataset")

        assert len(qualities) > 0
        assert all(q.target_type == QualityTargetType.DATASET for q in qualities)

    def test_list_filter_by_target_name(self, mock_api: QualityAPI) -> None:
        """Test filtering by target name."""
        qualities = mock_api.list_qualities(target_name="daily_clicks")

        assert len(qualities) > 0
        assert all("daily_clicks" in q.target_name for q in qualities)

    def test_list_filter_by_status(self, mock_api: QualityAPI) -> None:
        """Test filtering by status."""
        qualities = mock_api.list_qualities(status="active")

        assert len(qualities) > 0
        assert all(q.status == "active" for q in qualities)


class TestQualityAPIGet:
    """Tests for get method."""

    def test_get_existing(self, mock_api: QualityAPI) -> None:
        """Test getting an existing quality."""
        quality = mock_api.get("pk_unique")

        assert quality is not None
        assert quality.name == "pk_unique"
        assert quality.target_type == QualityTargetType.DATASET

    def test_get_nonexistent(self, mock_api: QualityAPI) -> None:
        """Test getting a nonexistent quality."""
        quality = mock_api.get("nonexistent_quality")

        assert quality is None


class TestQualityAPIValidate:
    """Tests for validate method."""

    def test_validate_valid_spec(self, local_api: QualityAPI) -> None:
        """Test validating a valid Quality Spec."""
        result = local_api.validate("quality.iceberg.analytics.daily_clicks.yaml")

        assert result.valid is True
        assert len(result.errors) == 0

    def test_validate_nonexistent_spec(self, local_api: QualityAPI) -> None:
        """Test validating a nonexistent spec."""
        result = local_api.validate("nonexistent.yaml")

        assert result.valid is False
        assert len(result.errors) > 0

    def test_validate_specific_test(self, local_api: QualityAPI) -> None:
        """Test validating with specific test filter."""
        result = local_api.validate(
            "quality.iceberg.analytics.daily_clicks.yaml",
            tests=["pk_unique"],
        )

        assert result.valid is True

    def test_validate_nonexistent_test(self, local_api: QualityAPI) -> None:
        """Test validating with nonexistent test name."""
        result = local_api.validate(
            "quality.iceberg.analytics.daily_clicks.yaml",
            tests=["nonexistent_test"],
        )

        assert result.valid is False
        assert any("not found" in e.lower() for e in result.errors)


class TestQualityAPIRun:
    """Tests for run method."""

    def test_run_mock_mode(self, mock_api: QualityAPI) -> None:
        """Test running in mock mode."""
        # Need a real spec file for run
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=FIXTURES_PATH,
        )
        api = QualityAPI(context=ctx)

        result = api.run("quality.iceberg.analytics.daily_clicks.yaml")

        assert isinstance(result, DqQualityResult)
        assert result.status == DqStatus.PASS
        assert result.execution_mode == "mock"
        assert result.passed_count > 0

    def test_run_specific_tests(self, mock_api: QualityAPI) -> None:
        """Test running specific tests only."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=FIXTURES_PATH,
        )
        api = QualityAPI(context=ctx)

        result = api.run(
            "quality.iceberg.analytics.daily_clicks.yaml",
            tests=["pk_unique"],
        )

        assert result.passed_count == 1

    def test_run_nonexistent_spec(self, mock_api: QualityAPI) -> None:
        """Test running nonexistent spec."""
        ctx = ExecutionContext(
            execution_mode=ExecutionMode.MOCK,
            project_path=FIXTURES_PATH,
        )
        api = QualityAPI(context=ctx)

        with pytest.raises(QualitySpecNotFoundError):
            api.run("nonexistent.yaml")


class TestQualityAPIGetSpec:
    """Tests for get_spec method."""

    def test_get_spec_valid(self, local_api: QualityAPI) -> None:
        """Test loading a valid spec."""
        spec = local_api.get_spec("quality.iceberg.analytics.daily_clicks.yaml")

        assert spec.version == 1
        assert spec.target.name == "iceberg.analytics.daily_clicks"
        assert spec.target.type == QualityTargetType.DATASET
        assert len(spec.tests) > 0
        assert spec.metadata.owner == "analyst@example.com"

    def test_get_spec_nonexistent(self, local_api: QualityAPI) -> None:
        """Test loading a nonexistent spec."""
        with pytest.raises(QualitySpecNotFoundError):
            local_api.get_spec("nonexistent.yaml")

    def test_get_spec_with_schedule(self, local_api: QualityAPI) -> None:
        """Test loading a spec with schedule."""
        spec = local_api.get_spec("quality.iceberg.analytics.daily_clicks.yaml")

        assert spec.schedule is not None
        assert spec.schedule.cron == "0 6 * * *"
        assert spec.schedule.timezone == "UTC"

    def test_get_spec_with_notifications(self, local_api: QualityAPI) -> None:
        """Test loading a spec with notifications."""
        spec = local_api.get_spec("quality.iceberg.core.users.yaml")

        assert spec.notifications is not None
        assert spec.notifications.slack is not None
        assert spec.notifications.email is not None


class TestQualityAPIHelpers:
    """Tests for helper methods and properties."""

    def test_is_mock_mode(self) -> None:
        """Test _is_mock_mode property."""
        mock_api = QualityAPI(context=ExecutionContext(execution_mode=ExecutionMode.MOCK))
        local_api = QualityAPI(context=ExecutionContext(execution_mode=ExecutionMode.LOCAL))

        assert mock_api._is_mock_mode is True
        assert local_api._is_mock_mode is False

    def test_repr(self) -> None:
        """Test __repr__ method."""
        api = QualityAPI()
        repr_str = repr(api)

        assert "QualityAPI" in repr_str
        assert "context" in repr_str
