"""Pytest configuration for integration tests.

This module provides pytest-docker fixtures for Trino integration testing.
Trino runs with memory catalog for fast, isolated testing.

Usage:
    # Run integration tests only
    pytest tests/integration/ -m integration

    # Run all tests including integration
    pytest -m "integration or not integration"

    # Skip integration tests (default in CI without Docker)
    pytest -m "not integration"
"""

from __future__ import annotations

from collections.abc import Generator
import os
from pathlib import Path
import time
from typing import TYPE_CHECKING, Any

import pytest

if TYPE_CHECKING:
    from dli.adapters.trino import TrinoExecutor

# =============================================================================
# Environment Detection
# =============================================================================

# Check if running in CI environment
IN_CI = os.environ.get("CI", "").lower() in ("true", "1", "yes")

# Check if Docker is available
DOCKER_AVAILABLE = False
try:
    import subprocess

    result = subprocess.run(
        ["docker", "info"],
        capture_output=True,
        timeout=10,
    )
    DOCKER_AVAILABLE = result.returncode == 0
except (FileNotFoundError, subprocess.TimeoutExpired):
    DOCKER_AVAILABLE = False

# Check if Trino package is installed
TRINO_AVAILABLE = False
try:
    import trino  # noqa: F401

    TRINO_AVAILABLE = True
except ImportError:
    TRINO_AVAILABLE = False


# =============================================================================
# Pytest Markers
# =============================================================================


def pytest_configure(config: pytest.Config) -> None:
    """Register custom markers for integration tests."""
    config.addinivalue_line(
        "markers",
        "integration: mark test as integration test (requires Docker + Trino)",
    )
    config.addinivalue_line(
        "markers",
        "trino: mark test as requiring Trino database",
    )
    config.addinivalue_line(
        "markers",
        "slow: mark test as slow running",
    )


# =============================================================================
# Docker Compose Fixtures (pytest-docker)
# =============================================================================


@pytest.fixture(scope="session")
def docker_compose_file() -> Path:
    """Return path to docker-compose file for Trino."""
    return Path(__file__).parent / "docker-compose.trino.yaml"


@pytest.fixture(scope="session")
def docker_compose_project_name() -> str:
    """Return unique project name for docker-compose."""
    return "dli-integration-test"


# =============================================================================
# Trino Connection Fixtures
# =============================================================================


def wait_for_trino(host: str, port: int, timeout: int = 60) -> bool:
    """Wait for Trino to become available.

    Args:
        host: Trino server host
        port: Trino server port
        timeout: Maximum seconds to wait

    Returns:
        True if Trino is available, False otherwise
    """
    if not TRINO_AVAILABLE:
        return False

    from trino import dbapi

    start_time = time.time()
    while time.time() - start_time < timeout:
        try:
            conn = dbapi.connect(
                host=host,
                port=port,
                user="test",
                catalog="memory",
                schema="default",
                http_scheme="http",
            )
            cursor = conn.cursor()
            cursor.execute("SELECT 1")
            cursor.fetchall()
            cursor.close()
            conn.close()
            return True
        except Exception:
            time.sleep(1)
    return False


@pytest.fixture(scope="session")
def trino_host() -> str:
    """Return Trino host."""
    return os.environ.get("TRINO_HOST", "localhost")


@pytest.fixture(scope="session")
def trino_port() -> int:
    """Return Trino port."""
    return int(os.environ.get("TRINO_PORT", "8080"))


@pytest.fixture(scope="session")
def trino_service(
    docker_compose_file: Path,
    docker_compose_project_name: str,
    trino_host: str,
    trino_port: int,
) -> Generator[dict[str, Any], None, None]:
    """Start Trino container and wait for it to be ready.

    This fixture uses pytest-docker to manage the container lifecycle.
    The container is started once per test session and stopped after all tests.

    Yields:
        Dictionary with Trino connection info: {"host", "port", "catalog", "schema"}
    """
    if not DOCKER_AVAILABLE:
        pytest.skip("Docker is not available")

    if not TRINO_AVAILABLE:
        pytest.skip("Trino package not installed. Run: uv sync --group integration")

    # Start docker-compose
    import subprocess

    try:
        subprocess.run(
            [
                "docker",
                "compose",
                "-f",
                str(docker_compose_file),
                "-p",
                docker_compose_project_name,
                "up",
                "-d",
                "--wait",
            ],
            check=True,
            capture_output=True,
            timeout=120,
        )
    except subprocess.CalledProcessError as e:
        pytest.skip(f"Failed to start Trino container: {e.stderr.decode()}")
    except subprocess.TimeoutExpired:
        pytest.skip("Timed out starting Trino container")

    # Wait for Trino to be ready
    if not wait_for_trino(trino_host, trino_port, timeout=60):
        # Cleanup on failure
        subprocess.run(
            [
                "docker",
                "compose",
                "-f",
                str(docker_compose_file),
                "-p",
                docker_compose_project_name,
                "down",
                "-v",
            ],
            capture_output=True,
        )
        pytest.skip("Trino did not become ready in time")

    connection_info = {
        "host": trino_host,
        "port": trino_port,
        "catalog": "memory",
        "schema": "default",
    }

    yield connection_info

    # Cleanup
    subprocess.run(
        [
            "docker",
            "compose",
            "-f",
            str(docker_compose_file),
            "-p",
            docker_compose_project_name,
            "down",
            "-v",
        ],
        capture_output=True,
    )


@pytest.fixture(scope="function")
def trino_executor(
    trino_service: dict[str, Any],
) -> Generator[TrinoExecutor, None, None]:
    """Create a TrinoExecutor connected to the test Trino instance.

    This fixture creates a new executor for each test function,
    ensuring test isolation.

    Yields:
        TrinoExecutor instance connected to test Trino
    """
    from dli.adapters.trino import TrinoExecutor

    executor = TrinoExecutor(
        host=trino_service["host"],
        port=trino_service["port"],
        user="test",
        catalog=trino_service["catalog"],
        schema=trino_service["schema"],
        ssl=False,
        auth_type="none",
    )

    yield executor

    executor.close()


@pytest.fixture(scope="function")
def trino_test_schema(
    trino_executor: TrinoExecutor,
) -> Generator[str, None, None]:
    """Create a unique test schema for isolation.

    Creates a new schema for each test and drops it after the test completes.
    This ensures tests don't interfere with each other.

    Yields:
        Schema name (e.g., "test_abc123")
    """
    import uuid

    schema_name = f"test_{uuid.uuid4().hex[:8]}"

    # Create schema
    trino_executor.execute_sql(f"CREATE SCHEMA memory.{schema_name}")

    yield schema_name

    # Cleanup - drop all tables and schema
    try:
        # List tables in schema
        result = trino_executor.execute_sql(
            f"SHOW TABLES FROM memory.{schema_name}"
        )
        if result.success and result.data:
            for row in result.data:
                table_name = row.get("Table", "")
                if table_name:
                    trino_executor.execute_sql(
                        f"DROP TABLE IF EXISTS memory.{schema_name}.{table_name}"
                    )
        # Drop schema
        trino_executor.execute_sql(f"DROP SCHEMA IF EXISTS memory.{schema_name}")
    except Exception:
        pass  # Best effort cleanup


# =============================================================================
# Sample Data Fixtures
# =============================================================================


@pytest.fixture
def sample_users_table(
    trino_executor: TrinoExecutor,
    trino_test_schema: str,
) -> str:
    """Create a sample users table with test data.

    Returns:
        Fully qualified table name (memory.test_xxx.users)
    """
    table_name = f"memory.{trino_test_schema}.users"

    # Create table
    trino_executor.execute_sql(f"""
        CREATE TABLE {table_name} (
            id VARCHAR,
            name VARCHAR,
            email VARCHAR,
            status VARCHAR,
            created_at TIMESTAMP
        )
    """)

    # Insert test data
    trino_executor.execute_sql(f"""
        INSERT INTO {table_name} VALUES
        ('u1', 'Alice', 'alice@example.com', 'active', TIMESTAMP '2025-01-01 00:00:00'),
        ('u2', 'Bob', 'bob@example.com', 'active', TIMESTAMP '2025-01-02 00:00:00'),
        ('u3', 'Charlie', 'charlie@example.com', 'inactive', TIMESTAMP '2025-01-03 00:00:00')
    """)

    return table_name


@pytest.fixture
def sample_events_table(
    trino_executor: TrinoExecutor,
    trino_test_schema: str,
) -> str:
    """Create a sample events table with test data.

    Returns:
        Fully qualified table name (memory.test_xxx.events)
    """
    table_name = f"memory.{trino_test_schema}.events"

    # Create table
    trino_executor.execute_sql(f"""
        CREATE TABLE {table_name} (
            event_id VARCHAR,
            user_id VARCHAR,
            event_type VARCHAR,
            event_time TIMESTAMP,
            properties VARCHAR
        )
    """)

    # Insert test data
    trino_executor.execute_sql(f"""
        INSERT INTO {table_name} VALUES
        ('e1', 'u1', 'click', TIMESTAMP '2025-01-01 10:00:00', '{{"page": "home"}}'),
        ('e2', 'u1', 'click', TIMESTAMP '2025-01-01 10:05:00', '{{"page": "products"}}'),
        ('e3', 'u2', 'view', TIMESTAMP '2025-01-01 11:00:00', '{{"page": "home"}}'),
        ('e4', 'u2', 'click', TIMESTAMP '2025-01-01 11:30:00', '{{"page": "checkout"}}'),
        ('e5', 'u3', 'view', TIMESTAMP '2025-01-01 12:00:00', '{{"page": "home"}}')
    """)

    return table_name


# =============================================================================
# Integration Test Fixtures
# =============================================================================


@pytest.fixture
def integration_project_path(tmp_path: Path) -> Path:
    """Create a temporary project directory for integration tests.

    Returns:
        Path to temporary project with dli.yaml configured for Trino
    """
    # Create dli.yaml with Trino defaults
    (tmp_path / "dli.yaml").write_text("""
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
  timeout_seconds: 60
  retry_count: 1

environments:
  test:
    connection_string: "trino://localhost:8080/memory"
""")

    # Create directories
    (tmp_path / "datasets").mkdir()
    (tmp_path / "metrics").mkdir()
    (tmp_path / "quality").mkdir()
    (tmp_path / "sql").mkdir()

    return tmp_path
