"""Custom exceptions for the Connect service."""

from __future__ import annotations


class ConnectError(Exception):
    """Base exception for Connect service errors."""

    def __init__(self, message: str, code: str | None = None) -> None:
        super().__init__(message)
        self.message = message
        self.code = code or "CONNECT_ERROR"


class ValidationError(ConnectError):
    """Raised when input validation fails."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="VALIDATION_ERROR")


class IntegrationError(ConnectError):
    """Raised when external service integration fails."""

    def __init__(self, service: str, message: str) -> None:
        super().__init__(f"{service}: {message}", code="INTEGRATION_ERROR")
        self.service = service


class GitHubError(IntegrationError):
    """Raised when GitHub API call fails."""

    def __init__(self, message: str) -> None:
        super().__init__("GitHub", message)


class JiraError(IntegrationError):
    """Raised when Jira API call fails."""

    def __init__(self, message: str) -> None:
        super().__init__("Jira", message)


class SlackError(IntegrationError):
    """Raised when Slack API call fails."""

    def __init__(self, message: str) -> None:
        super().__init__("Slack", message)


class DatabaseError(ConnectError):
    """Raised when database operation fails."""

    def __init__(self, message: str) -> None:
        super().__init__(message, code="DATABASE_ERROR")


class NotFoundError(ConnectError):
    """Raised when a requested resource is not found."""

    def __init__(self, resource: str, identifier: str) -> None:
        super().__init__(f"{resource} not found: {identifier}", code="NOT_FOUND")
        self.resource = resource
        self.identifier = identifier
