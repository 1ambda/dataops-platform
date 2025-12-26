"""Slack reply sync database model for tracking synced replies to Jira comments."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, Integer, String, Text, UniqueConstraint

from src.connect.models.base import Base


class SyncStatus(str):
    """Sync status values."""

    PENDING = "pending"
    SYNCED = "synced"
    FAILED = "failed"
    SKIPPED = "skipped"


class SlackReplySync(Base):
    """Slack thread reply synced to Jira as a comment.

    Represents a Slack thread reply that has been synced to a Jira ticket as a comment.
    This model tracks which Slack replies have been synced to prevent duplicates.

    Direction: Slack thread reply -> Jira comment
    (Jira is the Single Source of Truth)

    Attributes:
        id: Local database primary key
        jira_ticket_id: Reference to the jira_tickets table (no FK constraint)
        slack_thread_id: Reference to the slack_threads table (no FK constraint)
        slack_message_ts: Slack message timestamp of the reply (unique identifier)
        slack_user_id: Slack user ID who posted the reply
        slack_user_name: Display name of Slack user
        body: Reply text content
        jira_comment_id: Jira comment ID after syncing (null until synced)
        sync_status: Current sync status (pending, synced, failed, skipped)
        synced_at: When the reply was synced to Jira
        sent_at_slack: When the reply was sent in Slack
        created_at: When the record was created locally
        updated_at: When the record was last updated locally
    """

    __tablename__ = "slack_reply_syncs"

    id = Column(Integer, primary_key=True, autoincrement=True)
    jira_ticket_id = Column(Integer, nullable=False, index=True)
    slack_thread_id = Column(Integer, nullable=False, index=True)
    slack_message_ts = Column(String(50), nullable=False, index=True)
    slack_user_id = Column(String(50), nullable=True)
    slack_user_name = Column(String(255), nullable=True)
    body = Column(Text, nullable=True)
    jira_comment_id = Column(String(50), nullable=True, index=True)
    sync_status = Column(
        String(20), nullable=False, default=SyncStatus.PENDING, index=True
    )
    synced_at = Column(DateTime, nullable=True)
    sent_at_slack = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Ensure unique combination of ticket and Slack message timestamp
    __table_args__ = (
        UniqueConstraint(
            "jira_ticket_id", "slack_message_ts", name="uq_slack_reply_sync"
        ),
    )

    def __repr__(self) -> str:
        body_preview = self.body[:30] if self.body else ""
        return (
            f"<SlackReplySync(id={self.id}, slack_ts={self.slack_message_ts}, "
            f"ticket_id={self.jira_ticket_id}, body='{body_preview}...')>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "jira_ticket_id": self.jira_ticket_id,
            "slack_thread_id": self.slack_thread_id,
            "slack_message_ts": self.slack_message_ts,
            "slack_user_id": self.slack_user_id,
            "slack_user_name": self.slack_user_name,
            "body": self.body,
            "jira_comment_id": self.jira_comment_id,
            "sync_status": self.sync_status,
            "synced_at": self.synced_at.isoformat() if self.synced_at else None,
            "sent_at_slack": (
                self.sent_at_slack.isoformat() if self.sent_at_slack else None
            ),
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }


# Keep old name as alias for backward compatibility during migration
JiraComment = SlackReplySync
