"""Tests for dli.core.http module.

This module tests TracedHttpClient with mocked httpx responses.
"""

from __future__ import annotations

from typing import TYPE_CHECKING
from unittest.mock import MagicMock, patch

import httpx
import pytest

from dli.core.http import TracedHttpClient
from dli.core.trace import TraceContext

if TYPE_CHECKING:
    from collections.abc import Generator


# =============================================================================
# Fixtures
# =============================================================================


@pytest.fixture
def trace_context() -> TraceContext:
    """Create test trace context with fixed values."""
    return TraceContext(
        trace_id="550e8400-e29b-41d4-a716-446655440000",
        command="workflow backfill",
        cli_version="0.9.0",
        os_name="darwin",
        python_version="3.12.1",
    )


@pytest.fixture
def with_trace_context(trace_context: TraceContext) -> Generator[TraceContext, None, None]:
    """Set trace context for test, cleanup after."""
    trace_context.set_current()
    yield trace_context
    TraceContext.clear_current()


@pytest.fixture
def http_client() -> TracedHttpClient:
    """Create a TracedHttpClient instance."""
    return TracedHttpClient("https://api.example.com", timeout=30)


@pytest.fixture
def mock_response() -> httpx.Response:
    """Create a mock HTTP response."""
    response = MagicMock(spec=httpx.Response)
    response.status_code = 200
    response.json.return_value = {"success": True}
    return response


# =============================================================================
# Basic Tests
# =============================================================================


class TestTracedHttpClientInit:
    """Tests for TracedHttpClient initialization."""

    def test_traced_http_client_init_sets_base_url(self) -> None:
        """TracedHttpClient should store base_url."""
        client = TracedHttpClient("https://api.example.com")
        assert client.base_url == "https://api.example.com"

    def test_traced_http_client_init_sets_timeout(self) -> None:
        """TracedHttpClient should store timeout."""
        client = TracedHttpClient("https://api.example.com", timeout=60)
        assert client.timeout == 60

    def test_traced_http_client_init_default_timeout(self) -> None:
        """TracedHttpClient should use default timeout of 30."""
        client = TracedHttpClient("https://api.example.com")
        assert client.timeout == 30


class TestTracedHttpClientRepr:
    """Tests for TracedHttpClient.__repr__ method."""

    def test_traced_http_client_repr(self) -> None:
        """__repr__ should include base_url."""
        client = TracedHttpClient("https://api.example.com")
        repr_str = repr(client)
        assert repr_str == "TracedHttpClient(base_url='https://api.example.com')"

    def test_traced_http_client_repr_with_special_chars(self) -> None:
        """__repr__ should handle URLs with special characters."""
        client = TracedHttpClient("https://api.example.com:8080/v1")
        repr_str = repr(client)
        assert "https://api.example.com:8080/v1" in repr_str


# =============================================================================
# Header Tests
# =============================================================================


class TestTracedHttpClientHeaders:
    """Tests for header injection."""

    def test_traced_http_client_headers_without_trace(
        self, http_client: TracedHttpClient
    ) -> None:
        """Headers should be empty when no trace context is set."""
        # Ensure no trace context
        TraceContext.clear_current()

        headers = http_client._get_headers()
        assert headers == {}

    def test_traced_http_client_headers_with_trace(
        self, http_client: TracedHttpClient, with_trace_context: TraceContext
    ) -> None:
        """Headers should include X-Trace-Id and User-Agent when trace context is set."""
        headers = http_client._get_headers()

        assert "X-Trace-Id" in headers
        assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"

        assert "User-Agent" in headers
        expected_ua = "dli/0.9.0 (darwin; Python/3.12.1) command/workflow-backfill"
        assert headers["User-Agent"] == expected_ua


# =============================================================================
# HTTP Method Tests (with mocked httpx)
# =============================================================================


class TestTracedHttpClientGet:
    """Tests for GET requests."""

    def test_traced_http_client_get_without_trace(
        self, http_client: TracedHttpClient, mock_response: MagicMock
    ) -> None:
        """GET request without trace context should work."""
        TraceContext.clear_current()

        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            response = http_client.get("/health")

            mock_client_instance.get.assert_called_once_with("/health", headers={})
            assert response == mock_response

    def test_traced_http_client_get_with_trace(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """GET request with trace context should include trace headers."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            response = http_client.get("/api/v1/status")

            # Verify headers were passed
            call_kwargs = mock_client_instance.get.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"
            assert "User-Agent" in headers
            assert response == mock_response


class TestTracedHttpClientPost:
    """Tests for POST requests."""

    def test_traced_http_client_post_with_trace(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """POST request should include trace headers."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.post.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            response = http_client.post("/api/v1/run", json={"query": "SELECT 1"})

            call_kwargs = mock_client_instance.post.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"
            assert response == mock_response


class TestTracedHttpClientPut:
    """Tests for PUT requests."""

    def test_traced_http_client_put_with_trace(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """PUT request should include trace headers."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.put.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            response = http_client.put("/api/v1/config", json={"setting": "value"})

            call_kwargs = mock_client_instance.put.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"
            assert response == mock_response


class TestTracedHttpClientDelete:
    """Tests for DELETE requests."""

    def test_traced_http_client_delete_with_trace(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """DELETE request should include trace headers."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.delete.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            response = http_client.delete("/api/v1/resource/123")

            call_kwargs = mock_client_instance.delete.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"
            assert response == mock_response


class TestTracedHttpClientMergesHeaders:
    """Tests for custom header merging."""

    def test_traced_http_client_merges_custom_headers(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """Custom headers should be merged with trace headers."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            custom_headers = {"Authorization": "Bearer token123", "Accept": "application/json"}
            http_client.get("/api/v1/protected", headers=custom_headers)

            call_kwargs = mock_client_instance.get.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            # Should have trace headers
            assert headers["X-Trace-Id"] == "550e8400-e29b-41d4-a716-446655440000"
            assert "User-Agent" in headers

            # Should have custom headers
            assert headers["Authorization"] == "Bearer token123"
            assert headers["Accept"] == "application/json"

    def test_traced_http_client_custom_headers_override_trace_headers(
        self,
        http_client: TracedHttpClient,
        mock_response: MagicMock,
        with_trace_context: TraceContext,
    ) -> None:
        """Custom headers should override trace headers if same key."""
        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            # Custom User-Agent should override trace User-Agent
            custom_headers = {"User-Agent": "CustomAgent/1.0"}
            http_client.get("/api/v1/test", headers=custom_headers)

            call_kwargs = mock_client_instance.get.call_args
            headers = call_kwargs.kwargs.get("headers", call_kwargs[1].get("headers", {}))

            # Custom User-Agent should win
            assert headers["User-Agent"] == "CustomAgent/1.0"


class TestTracedHttpClientClientCreation:
    """Tests for httpx.Client creation."""

    def test_traced_http_client_uses_correct_base_url(
        self, mock_response: MagicMock
    ) -> None:
        """Client should be created with correct base_url."""
        TraceContext.clear_current()
        client = TracedHttpClient("https://custom.api.com", timeout=45)

        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            client.get("/test")

            MockClient.assert_called_once_with(
                base_url="https://custom.api.com",
                timeout=45,
            )

    def test_traced_http_client_passes_kwargs(
        self, http_client: TracedHttpClient, mock_response: MagicMock
    ) -> None:
        """Additional kwargs should be passed to httpx method."""
        TraceContext.clear_current()

        with patch("httpx.Client") as MockClient:
            mock_client_instance = MagicMock()
            mock_client_instance.get.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            http_client.get("/test", params={"key": "value"}, follow_redirects=True)

            call_kwargs = mock_client_instance.get.call_args
            assert call_kwargs.kwargs.get("params") == {"key": "value"}
            assert call_kwargs.kwargs.get("follow_redirects") is True


class TestTracedHttpClientIntegration:
    """Integration-style tests (still mocked but testing full flow)."""

    def test_full_request_flow_with_trace(
        self, with_trace_context: TraceContext
    ) -> None:
        """Test complete request flow with trace context."""
        client = TracedHttpClient("https://api.example.com", timeout=30)

        with patch("httpx.Client") as MockClient:
            # Setup mock
            mock_response = MagicMock(spec=httpx.Response)
            mock_response.status_code = 200
            mock_response.json.return_value = {"data": "result"}

            mock_client_instance = MagicMock()
            mock_client_instance.post.return_value = mock_response
            MockClient.return_value.__enter__.return_value = mock_client_instance

            # Make request
            response = client.post(
                "/api/v1/execute",
                json={"sql": "SELECT 1"},
                headers={"Content-Type": "application/json"},
            )

            # Verify response
            assert response.status_code == 200
            assert response.json() == {"data": "result"}

            # Verify httpx.Client was called correctly
            MockClient.assert_called_once_with(
                base_url="https://api.example.com",
                timeout=30,
            )

            # Verify headers included trace info
            call_kwargs = mock_client_instance.post.call_args
            headers = call_kwargs.kwargs.get("headers", {})
            assert "X-Trace-Id" in headers
            assert "User-Agent" in headers
            assert "Content-Type" in headers
