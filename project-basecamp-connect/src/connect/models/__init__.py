"""Database models for the Connect service."""

from src.connect.models.base import Base
from src.connect.models.closure import TicketClosureNotification
from src.connect.models.comment import JiraComment, SlackReplySync
from src.connect.models.integration import IntegrationLog, ServiceMapping
from src.connect.models.jira import JiraTicket
from src.connect.models.linking import JiraSlackLink
from src.connect.models.slack import SlackMessage, SlackThread

__all__ = [
    "Base",
    "IntegrationLog",
    "JiraComment",  # Backward compatibility alias
    "JiraSlackLink",
    "JiraTicket",
    "ServiceMapping",
    "SlackMessage",
    "SlackReplySync",
    "SlackThread",
    "TicketClosureNotification",
]
