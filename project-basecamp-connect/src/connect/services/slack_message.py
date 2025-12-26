"""Slack message storage service.

Handles storing and retrieving Slack messages and threads.
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING

from sqlalchemy.orm import Session

from src.connect.clients.slack import SlackClientInterface, SlackMessageData
from src.connect.logging_config import get_logger
from src.connect.models.slack import SlackMessage, SlackThread

if TYPE_CHECKING:
    pass

logger = get_logger(__name__)


class SlackMessageService:
    """Service for storing and managing Slack messages.

    This service handles:
    - Storing messages from Slack channels
    - Managing thread metadata
    - Syncing messages from Slack API
    """

    def __init__(
        self,
        session: Session,
        slack_client: SlackClientInterface,
    ) -> None:
        """Initialize the Slack message service.

        Args:
            session: SQLAlchemy database session
            slack_client: Slack API client (real or mock)
        """
        self._session = session
        self._slack_client = slack_client

    def create_thread(
        self,
        channel_id: str,
        thread_ts: str,
        channel_name: str | None = None,
        permalink: str | None = None,
        created_by_bot: bool = True,
    ) -> SlackThread:
        """Create a new thread record.

        Args:
            channel_id: Slack channel ID
            thread_ts: Thread timestamp (parent message ts)
            channel_name: Optional human-readable channel name
            permalink: Optional permalink to the thread
            created_by_bot: Whether this thread was created by our bot

        Returns:
            Created SlackThread
        """
        logger.info(f"Creating thread record: channel={channel_id}, ts={thread_ts}")

        # Check if thread already exists
        existing = self.get_thread(channel_id, thread_ts)
        if existing:
            logger.debug(f"Thread already exists: id={existing.id}")
            return existing

        # Try to get channel name if not provided
        if not channel_name:
            try:
                channel_info = self._slack_client.get_channel_info(channel_id)
                channel_name = channel_info.name
            except Exception as e:
                logger.warning(f"Could not fetch channel name: {e}")

        # Try to get permalink if not provided
        if not permalink:
            try:
                permalink = self._slack_client.get_permalink(channel_id, thread_ts)
            except Exception as e:
                logger.warning(f"Could not fetch permalink: {e}")

        thread = SlackThread(
            channel_id=channel_id,
            channel_name=channel_name,
            thread_ts=thread_ts,
            parent_message_ts=thread_ts,
            permalink=permalink,
            created_by_bot=1 if created_by_bot else 0,
        )

        self._session.add(thread)
        self._session.commit()
        self._session.refresh(thread)

        logger.info(f"Created thread: id={thread.id}")
        return thread

    def get_thread(self, channel_id: str, thread_ts: str) -> SlackThread | None:
        """Get a thread by channel and timestamp.

        Args:
            channel_id: Slack channel ID
            thread_ts: Thread timestamp

        Returns:
            SlackThread or None if not found
        """
        return (
            self._session.query(SlackThread)
            .filter(
                SlackThread.channel_id == channel_id,
                SlackThread.thread_ts == thread_ts,
            )
            .first()
        )

    def get_thread_by_id(self, thread_id: int) -> SlackThread | None:
        """Get a thread by its local database ID.

        Args:
            thread_id: Local database ID

        Returns:
            SlackThread or None if not found
        """
        return (
            self._session.query(SlackThread)
            .filter(SlackThread.id == thread_id)
            .first()
        )

    def store_message(
        self,
        message: SlackMessageData,
        thread_id: int | None = None,
    ) -> SlackMessage:
        """Store a single message.

        Args:
            message: SlackMessageData from the API
            thread_id: Optional local thread ID

        Returns:
            Created SlackMessage
        """
        logger.debug(f"Storing message: channel={message.channel_id}, ts={message.ts}")

        # Check if message already exists
        existing = self.get_message(message.channel_id, message.ts)
        if existing:
            logger.debug(f"Message already exists: id={existing.id}")
            return existing

        db_message = SlackMessage(
            channel_id=message.channel_id,
            message_ts=message.ts,
            thread_id=thread_id,
            thread_ts=message.thread_ts,
            user_id=message.user_id,
            user_name=message.user_name,
            text=message.text,
            message_type="bot_message" if message.is_bot else "message",
            is_bot_message=1 if message.is_bot else 0,
            reactions=str(message.reactions) if message.reactions else None,
            attachments=str(message.attachments) if message.attachments else None,
            sent_at=message.sent_at,
        )

        self._session.add(db_message)
        self._session.commit()
        self._session.refresh(db_message)

        logger.debug(f"Stored message: id={db_message.id}")
        return db_message

    def get_message(self, channel_id: str, message_ts: str) -> SlackMessage | None:
        """Get a message by channel and timestamp.

        Args:
            channel_id: Slack channel ID
            message_ts: Message timestamp

        Returns:
            SlackMessage or None if not found
        """
        return (
            self._session.query(SlackMessage)
            .filter(
                SlackMessage.channel_id == channel_id,
                SlackMessage.message_ts == message_ts,
            )
            .first()
        )

    def get_channel_messages(
        self,
        channel_id: str,
        limit: int = 100,
        include_thread_replies: bool = False,
    ) -> list[SlackMessage]:
        """Get messages from a channel.

        Args:
            channel_id: Slack channel ID
            limit: Maximum number of messages
            include_thread_replies: Whether to include thread replies

        Returns:
            List of SlackMessage entities
        """
        query = self._session.query(SlackMessage).filter(
            SlackMessage.channel_id == channel_id
        )

        if not include_thread_replies:
            # Only get top-level messages (no thread_ts or thread_ts == message_ts)
            query = query.filter(
                (SlackMessage.thread_ts == None)  # noqa: E711
                | (SlackMessage.thread_ts == SlackMessage.message_ts)
            )

        return (
            query.order_by(SlackMessage.sent_at.desc())
            .limit(limit)
            .all()
        )

    def get_thread_messages(
        self, channel_id: str, thread_ts: str
    ) -> list[SlackMessage]:
        """Get all messages in a thread.

        Args:
            channel_id: Slack channel ID
            thread_ts: Thread timestamp

        Returns:
            List of SlackMessage entities
        """
        return (
            self._session.query(SlackMessage)
            .filter(
                SlackMessage.channel_id == channel_id,
                SlackMessage.thread_ts == thread_ts,
            )
            .order_by(SlackMessage.sent_at.asc())
            .all()
        )

    def sync_channel_history(
        self,
        channel_id: str,
        oldest: str | None = None,
        latest: str | None = None,
        limit: int = 100,
    ) -> list[SlackMessage]:
        """Sync message history from Slack API.

        Args:
            channel_id: Slack channel ID
            oldest: Start of time range
            latest: End of time range
            limit: Maximum number of messages

        Returns:
            List of stored SlackMessage entities
        """
        logger.info(f"Syncing channel history: channel={channel_id}")

        messages = self._slack_client.get_channel_history(
            channel=channel_id,
            oldest=oldest,
            latest=latest,
            limit=limit,
        )

        stored = []
        for msg in messages:
            db_message = self.store_message(msg)
            stored.append(db_message)

        self._session.commit()
        logger.info(f"Synced {len(stored)} messages")
        return stored

    def sync_thread_replies(
        self,
        channel_id: str,
        thread_ts: str,
        thread_id: int | None = None,
    ) -> list[SlackMessage]:
        """Sync thread replies from Slack API.

        Args:
            channel_id: Slack channel ID
            thread_ts: Thread timestamp
            thread_id: Optional local thread ID to associate

        Returns:
            List of stored SlackMessage entities
        """
        logger.info(f"Syncing thread replies: channel={channel_id}, ts={thread_ts}")

        messages = self._slack_client.get_thread_replies(
            channel=channel_id,
            thread_ts=thread_ts,
        )

        stored = []
        for msg in messages:
            db_message = self.store_message(msg, thread_id=thread_id)
            stored.append(db_message)

        self._session.commit()
        logger.info(f"Synced {len(stored)} thread replies")
        return stored
