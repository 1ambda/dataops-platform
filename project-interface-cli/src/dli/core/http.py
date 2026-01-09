"""HTTP client with trace ID support.

This module provides a low-level HTTP transport with automatic
trace header injection. Used by BasecampClient for server communication.

Example:
    >>> from dli.core.http import TracedHttpClient
    >>> from dli.core.trace import TraceContext
    >>> client = TracedHttpClient("https://api.example.com")
    >>> trace = TraceContext.create("run")
    >>> trace.set_current()
    >>> response = client.get("/health")  # Headers include X-Trace-Id
"""

from __future__ import annotations

from typing import Any

import httpx

from dli.core.trace import TraceContext

__all__ = ["TracedHttpClient"]


class TracedHttpClient:
    """HTTP client that automatically adds trace headers.

    Responsibilities:
    - Low-level HTTP transport (GET, POST, PUT, DELETE)
    - Automatic X-Trace-Id header injection from current trace context
    - User-Agent header with CLI metadata

    Note:
        BasecampClient uses this for actual API calls when not in mock mode.
        Each request creates a new httpx.Client to ensure proper connection handling.

    Attributes:
        base_url: Base URL for all requests.
        timeout: Request timeout in seconds.

    Example:
        >>> client = TracedHttpClient("https://basecamp.example.com", timeout=30)
        >>> response = client.get("/api/v1/health")
    """

    def __init__(self, base_url: str, timeout: int = 30) -> None:
        """Initialize the traced HTTP client.

        Args:
            base_url: Base URL for all requests (e.g., "https://api.example.com").
            timeout: Request timeout in seconds. Defaults to 30.
        """
        self.base_url = base_url
        self.timeout = timeout

    def __repr__(self) -> str:
        """Return representation for debugging."""
        return f"TracedHttpClient(base_url={self.base_url!r})"

    def _get_headers(self) -> dict[str, str]:
        """Get headers including trace ID if available.

        Returns:
            Dictionary of headers with X-Trace-Id and User-Agent if trace context exists.
        """
        headers: dict[str, str] = {}
        trace = TraceContext.get_current()
        if trace:
            headers["X-Trace-Id"] = trace.trace_id
            headers["User-Agent"] = trace.user_agent
        return headers

    def get(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make GET request with trace headers.

        Args:
            path: URL path relative to base_url.
            **kwargs: Additional arguments passed to httpx.Client.get().

        Returns:
            httpx.Response object.
        """
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.get(path, headers=headers, **kwargs)

    def post(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make POST request with trace headers.

        Args:
            path: URL path relative to base_url.
            **kwargs: Additional arguments passed to httpx.Client.post().

        Returns:
            httpx.Response object.
        """
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.post(path, headers=headers, **kwargs)

    def put(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make PUT request with trace headers.

        Args:
            path: URL path relative to base_url.
            **kwargs: Additional arguments passed to httpx.Client.put().

        Returns:
            httpx.Response object.
        """
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.put(path, headers=headers, **kwargs)

    def delete(self, path: str, **kwargs: Any) -> httpx.Response:
        """Make DELETE request with trace headers.

        Args:
            path: URL path relative to base_url.
            **kwargs: Additional arguments passed to httpx.Client.delete().

        Returns:
            httpx.Response object.
        """
        headers = {**self._get_headers(), **kwargs.pop("headers", {})}
        with httpx.Client(base_url=self.base_url, timeout=self.timeout) as client:
            return client.delete(path, headers=headers, **kwargs)
