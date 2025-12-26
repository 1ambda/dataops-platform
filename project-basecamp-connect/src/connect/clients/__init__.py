"""API clients for external services (Jira, Slack, GitHub).

This module provides client interfaces and implementations for external service
integrations. Both Protocol-based (structural typing) and ABC-based (nominal typing)
interfaces are provided for maximum flexibility.

Usage:
    # Import interfaces for type hints
    from src.connect.clients import JiraClientProtocol, SlackClientProtocol

    # Import factory functions for dependency injection
    from src.connect.clients import create_jira_client, create_slack_client

    # Import concrete implementations if needed
    from src.connect.clients import JiraClient, MockJiraClient
"""

from src.connect.clients.jira import (
    JiraClient,
    JiraClientInterface,
    JiraClientProtocol,
    JiraIssue,
    JiraWebhookEvent,
    MockJiraClient,
    create_jira_client,
)
from src.connect.clients.slack import (
    MockSlackClient,
    PostMessageResponse,
    SlackChannel,
    SlackClient,
    SlackClientInterface,
    SlackClientProtocol,
    SlackMessageData,
    SlackUser,
    create_slack_client,
)

__all__ = [
    # Jira Protocol interface (preferred for type hints)
    "JiraClientProtocol",
    # Jira ABC interface (for inheritance)
    "JiraClientInterface",
    # Jira implementations
    "JiraClient",
    "MockJiraClient",
    # Jira factory function (preferred for DI)
    "create_jira_client",
    # Jira data classes
    "JiraIssue",
    "JiraWebhookEvent",
    # Slack Protocol interface (preferred for type hints)
    "SlackClientProtocol",
    # Slack ABC interface (for inheritance)
    "SlackClientInterface",
    # Slack implementations
    "SlackClient",
    "MockSlackClient",
    # Slack factory function (preferred for DI)
    "create_slack_client",
    # Slack data classes
    "SlackChannel",
    "SlackMessageData",
    "SlackUser",
    "PostMessageResponse",
]
