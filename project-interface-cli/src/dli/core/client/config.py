"""Configuration classes for Basecamp Server client.

This module contains configuration and response data classes
used by the BasecampClient.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class ServerConfig:
    """Server connection configuration."""

    url: str
    timeout: int = 30
    api_key: str | None = None


@dataclass
class ServerResponse:
    """Response from server API calls."""

    success: bool
    data: dict[str, Any] | list[dict[str, Any]] | None = None
    error: str | None = None
    status_code: int = 200
