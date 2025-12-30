"""Shared fixtures for transpile tests."""

from __future__ import annotations

import pytest

from dli.core.transpile.client import MockTranspileClient
from dli.core.transpile.engine import TranspileEngine
from dli.core.transpile.models import TranspileConfig


@pytest.fixture
def mock_client() -> MockTranspileClient:
    """Create a mock transpile client with default rules and metrics."""
    return MockTranspileClient()


@pytest.fixture
def empty_client() -> MockTranspileClient:
    """Create an empty mock client (no rules/metrics)."""
    client = MockTranspileClient()
    client.clear()
    return client


@pytest.fixture
def default_config() -> TranspileConfig:
    """Create default transpile config."""
    return TranspileConfig()


@pytest.fixture
def strict_config() -> TranspileConfig:
    """Create strict mode transpile config."""
    return TranspileConfig(strict_mode=True)


@pytest.fixture
def default_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create transpile engine with default config and mock client."""
    return TranspileEngine(client=mock_client)


@pytest.fixture
def strict_engine(mock_client: MockTranspileClient) -> TranspileEngine:
    """Create transpile engine with strict mode and mock client."""
    config = TranspileConfig(strict_mode=True)
    return TranspileEngine(client=mock_client, config=config)
