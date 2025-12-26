"""Slack message and thread database models."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, ForeignKey, Integer, String, Text
from sqlalchemy.orm import relationship

from src.connect.models.base import Base


class SlackThread(Base):
    """Slack thread metadata.

    Represents a Slack thread that was created in response to a Jira ticket
    or other integration event.

    Attributes:
        id: Local database primary key
        channel_id: Slack channel ID (e.g., "C0123456789")
        channel_name: Human-readable channel name
        thread_ts: Thread timestamp (parent message ts)
        parent_message_ts: Same as thread_ts (Slack API compatibility)
        permalink: Full URL to the thread
        created_by_bot: Whether thread was created by our bot
        created_at: When the record was created locally
        updated_at: When the record was last updated locally
    """

    __tablename__ = "slack_threads"

    id = Column(Integer, primary_key=True, autoincrement=True)
    channel_id = Column(String(20), nullable=False, index=True)
    channel_name = Column(String(100), nullable=True)
    thread_ts = Column(String(50), nullable=False, index=True)
    parent_message_ts = Column(String(50), nullable=False)
    permalink = Column(String(500), nullable=True)
    created_by_bot = Column(Integer, default=1)  # SQLite boolean compatibility
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)
    updated_at = Column(
        DateTime, default=datetime.utcnow, onupdate=datetime.utcnow, nullable=False
    )

    # Relationships
    messages = relationship(
        "SlackMessage", back_populates="thread", cascade="all, delete-orphan"
    )
    jira_links = relationship(
        "JiraSlackLink", back_populates="slack_thread", cascade="all, delete-orphan"
    )

    def __repr__(self) -> str:
        return (
            f"<SlackThread(id={self.id}, channel={self.channel_id}, "
            f"thread_ts={self.thread_ts})>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "channel_id": self.channel_id,
            "channel_name": self.channel_name,
            "thread_ts": self.thread_ts,
            "parent_message_ts": self.parent_message_ts,
            "permalink": self.permalink,
            "created_by_bot": bool(self.created_by_bot),
            "created_at": self.created_at.isoformat() if self.created_at else None,
            "updated_at": self.updated_at.isoformat() if self.updated_at else None,
        }


class SlackMessage(Base):
    """Slack channel message.

    Stores individual messages from Slack channels, optionally associated
    with a thread.

    Attributes:
        id: Local database primary key
        channel_id: Slack channel ID
        message_ts: Message timestamp (unique identifier in Slack)
        thread_id: Optional reference to parent thread
        user_id: Slack user ID who sent the message
        user_name: Display name of the user
        text: Message text content
        message_type: Type of message (e.g., "message", "bot_message")
        is_bot_message: Whether message was sent by a bot
        reactions: JSON string of reactions
        attachments: JSON string of attachments metadata
        sent_at: When the message was sent (derived from ts)
        created_at: When the record was created locally
    """

    __tablename__ = "slack_messages"

    id = Column(Integer, primary_key=True, autoincrement=True)
    channel_id = Column(String(20), nullable=False, index=True)
    message_ts = Column(String(50), nullable=False, index=True)
    thread_id = Column(Integer, ForeignKey("slack_threads.id"), nullable=True)
    thread_ts = Column(String(50), nullable=True, index=True)  # For thread replies
    user_id = Column(String(20), nullable=True)
    user_name = Column(String(255), nullable=True)
    text = Column(Text, nullable=True)
    message_type = Column(String(50), nullable=False, default="message")
    is_bot_message = Column(Integer, default=0)  # SQLite boolean compatibility
    reactions = Column(Text, nullable=True)  # JSON string
    attachments = Column(Text, nullable=True)  # JSON string
    sent_at = Column(DateTime, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # Relationship to thread
    thread = relationship("SlackThread", back_populates="messages")

    def __repr__(self) -> str:
        text_preview = self.text[:30] if self.text else ""
        return (
            f"<SlackMessage(id={self.id}, channel={self.channel_id}, "
            f"ts={self.message_ts}, text='{text_preview}...')>"
        )

    def to_dict(self) -> dict:
        """Convert to dictionary representation."""
        return {
            "id": self.id,
            "channel_id": self.channel_id,
            "message_ts": self.message_ts,
            "thread_id": self.thread_id,
            "thread_ts": self.thread_ts,
            "user_id": self.user_id,
            "user_name": self.user_name,
            "text": self.text,
            "message_type": self.message_type,
            "is_bot_message": bool(self.is_bot_message),
            "reactions": self.reactions,
            "attachments": self.attachments,
            "sent_at": self.sent_at.isoformat() if self.sent_at else None,
            "created_at": self.created_at.isoformat() if self.created_at else None,
        }
