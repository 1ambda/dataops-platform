"""Tests for API dependency injection support.

Covers:
- DatasetAPI with injected executor
- MetricAPI with injected executor
"""

from __future__ import annotations

from pathlib import Path

import pytest

from dli.api.dataset import DatasetAPI
from dli.api.metric import MetricAPI
from dli.core.executor import MockExecutor
from dli.models.common import ExecutionContext, ExecutionMode, ResultStatus


class TestDatasetAPIDI:
    """Tests for DatasetAPI dependency injection."""

    def test_init_with_executor(self) -> None:
        """Test DatasetAPI accepts executor parameter."""
        mock_executor = MockExecutor(mock_data=[{"id": 1}])
        ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)

        api = DatasetAPI(context=ctx, executor=mock_executor)

        assert api._executor is mock_executor
        assert api.context is ctx

    def test_init_without_executor(self) -> None:
        """Test DatasetAPI works without executor parameter."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        api = DatasetAPI(context=ctx)

        assert api._executor is None

    def test_mock_mode_returns_success(self) -> None:
        """Test that mock mode returns success without real execution."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = DatasetAPI(context=ctx)

        result = api.run("test.schema.dataset")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "test.schema.dataset"

    def test_is_mock_mode_property(self) -> None:
        """Test _is_mock_mode property returns correct value."""
        ctx_mock = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        ctx_local = ExecutionContext(execution_mode=ExecutionMode.LOCAL)
        ctx_server = ExecutionContext(execution_mode=ExecutionMode.SERVER)

        api_mock = DatasetAPI(context=ctx_mock)
        api_local = DatasetAPI(context=ctx_local)
        api_server = DatasetAPI(context=ctx_server)

        assert api_mock._is_mock_mode is True
        assert api_local._is_mock_mode is False
        assert api_server._is_mock_mode is False


class TestMetricAPIDI:
    """Tests for MetricAPI dependency injection."""

    def test_init_with_executor(self) -> None:
        """Test MetricAPI accepts executor parameter."""
        mock_executor = MockExecutor(mock_data=[{"value": 42}])
        ctx = ExecutionContext(execution_mode=ExecutionMode.LOCAL)

        api = MetricAPI(context=ctx, executor=mock_executor)

        assert api._executor is mock_executor
        assert api.context is ctx

    def test_init_without_executor(self) -> None:
        """Test MetricAPI works without executor parameter."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)

        api = MetricAPI(context=ctx)

        assert api._executor is None

    def test_mock_mode_returns_success(self) -> None:
        """Test that mock mode returns success without real execution."""
        ctx = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        api = MetricAPI(context=ctx)

        result = api.run("test.schema.metric")

        assert result.status == ResultStatus.SUCCESS
        assert result.name == "test.schema.metric"
        assert result.data == []
        assert result.row_count == 0

    def test_is_mock_mode_property(self) -> None:
        """Test _is_mock_mode property returns correct value."""
        ctx_mock = ExecutionContext(execution_mode=ExecutionMode.MOCK)
        ctx_local = ExecutionContext(execution_mode=ExecutionMode.LOCAL)
        ctx_server = ExecutionContext(execution_mode=ExecutionMode.SERVER)

        api_mock = MetricAPI(context=ctx_mock)
        api_local = MetricAPI(context=ctx_local)
        api_server = MetricAPI(context=ctx_server)

        assert api_mock._is_mock_mode is True
        assert api_local._is_mock_mode is False
        assert api_server._is_mock_mode is False


class TestBackwardCompatibility:
    """Tests for backward compatibility with old mock_mode API."""

    def test_dataset_api_with_legacy_mock_mode(self) -> None:
        """Test DatasetAPI works with legacy mock_mode=True."""
        # Using mock_mode=True should migrate to execution_mode=MOCK
        ctx = ExecutionContext(mock_mode=True)  # type: ignore[call-arg]
        api = DatasetAPI(context=ctx)

        # Should work in mock mode
        result = api.run("test.schema.dataset")
        assert result.status == ResultStatus.SUCCESS

    def test_metric_api_with_legacy_mock_mode(self) -> None:
        """Test MetricAPI works with legacy mock_mode=True."""
        # Using mock_mode=True should migrate to execution_mode=MOCK
        ctx = ExecutionContext(mock_mode=True)  # type: ignore[call-arg]
        api = MetricAPI(context=ctx)

        # Should work in mock mode
        result = api.run("test.schema.metric")
        assert result.status == ResultStatus.SUCCESS
