"""Ticket closure notification database model."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, Integer, String, UniqueConstraint

from src.connect.models.base import Base


class TicketClosureNotification(Base):
    """Ticket closure notification sent to Slack.

    Represents a notification sent to a Slack thread when a linked Jira ticket
    reaches a terminal status (e.g., Done, Closed).

    This model prevents duplicate notifications for the same ticket closure.

    Attributes:
        id: Local database primary key
        jira_ticket_id: Reference to the jira_tickets table (no FK constraint)
        slack_thread_id: Reference to the slack_threads table (no FK constraint)
        jira_status: The terminal status that triggered the notification
        notification_message_ts: Slack message timestamp of the notification
        reaction_added: Whether emoji reaction was added to the original message
        reaction_emoji: The emoji name that was added (e.g., "white_check_mark")
        notified_at: When the notification was sent
        created_at: When the record was created locally
        updated_at: When the record was last updated locally
    """

    __tablename__ = "ticket_closure_notifications"

    id = Column(Integer, primary_key=True, autoincrement=True)
    jira_ticket_id = Column(Integer, nullable=False, index=True)
    slack_thread_id = Column(Integer, nullable=False, index=True)
    jira_status = Column(String(50), nullable=False, index=True)
    notification_message_ts = Column(String(50), nullable=True)
    reaction_added = Column(Integer, nullable=False, default=0)
    reaction_emoji = Column(String(50), nullable=True)
    notified_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Ensure unique combination of ticket and thread
    __table_args__ = (
        UniqueConstraint(
            "jira_ticket_id", "slack_thread_id", name="uq_ticket_closure"
        ),
    )

    def __repr__(self) -> str:
        return (
            f"<TicketClosureNotification(id={self.id}, "
            f"ticket_id={self.jira_ticket_id}, status={self.jira_status})>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "jira_ticket_id": self.jira_ticket_id,
            "slack_thread_id": self.slack_thread_id,
            "jira_status": self.jira_status,
            "notification_message_ts": self.notification_message_ts,
            "reaction_added": bool(self.reaction_added),
            "reaction_emoji": self.reaction_emoji,
            "notified_at": self.notified_at.isoformat() if self.notified_at else None,
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }
