"""Basecamp Server client package.

This package provides the client interface for communicating with
the Basecamp Server API.

Public API:
    - BasecampClient: Main client class for server communication
    - ServerConfig: Configuration for server connection
    - ServerResponse: Response wrapper for API calls
    - WorkflowSource: Enum for workflow source types
    - RunStatus: Enum for workflow run status
"""

from dli.core.client.baseclient import BasecampClient, create_client
from dli.core.client.config import ServerConfig, ServerResponse
from dli.core.client.enums import RunStatus, WorkflowSource

__all__ = [
    "BasecampClient",
    "ServerConfig",
    "ServerResponse",
    "WorkflowSource",
    "RunStatus",
    "create_client",
]
