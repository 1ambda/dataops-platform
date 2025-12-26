"""Tests for Slack message storage service."""

import pytest

from src.connect.clients.slack import MockSlackClient, SlackMessageData
from src.connect.models.slack import SlackMessage, SlackThread
from src.connect.services.slack_message import SlackMessageService


class TestSlackMessageService:
    """Tests for SlackMessageService."""

    @pytest.fixture
    def slack_client(self):
        """Create a mock Slack client."""
        return MockSlackClient()

    @pytest.fixture
    def service(self, db_session, slack_client):
        """Create a SlackMessageService."""
        return SlackMessageService(db_session, slack_client)

    def test_create_thread(self, service, db_session):
        """Test creating a thread."""
        thread = service.create_thread(
            channel_id="C0001",
            thread_ts="1705320000.000100",
            channel_name="general",
            permalink="https://slack.com/test",
            created_by_bot=True,
        )

        assert thread is not None
        assert thread.id is not None
        assert thread.channel_id == "C0001"
        assert thread.thread_ts == "1705320000.000100"
        assert thread.channel_name == "general"
        assert thread.permalink == "https://slack.com/test"

        # Verify it's in the database
        saved = (
            db_session.query(SlackThread)
            .filter_by(thread_ts="1705320000.000100")
            .first()
        )
        assert saved is not None

    def test_create_thread_duplicate(self, service, db_session):
        """Test that creating duplicate thread returns existing."""
        thread1 = service.create_thread(
            channel_id="C0001",
            thread_ts="1705320000.000200",
        )

        thread2 = service.create_thread(
            channel_id="C0001",
            thread_ts="1705320000.000200",
        )

        assert thread1.id == thread2.id

    def test_get_thread(self, service, db_session):
        """Test getting a thread."""
        # Create thread
        service.create_thread(
            channel_id="C0001",
            thread_ts="1705320000.000300",
        )

        thread = service.get_thread("C0001", "1705320000.000300")

        assert thread is not None
        assert thread.thread_ts == "1705320000.000300"

    def test_get_thread_not_found(self, service):
        """Test getting non-existent thread."""
        thread = service.get_thread("C0001", "nonexistent")

        assert thread is None

    def test_store_message(self, service, db_session):
        """Test storing a message."""
        message_data = SlackMessageData(
            ts="1705320000.000400",
            channel_id="C0001",
            user_id="U123",
            user_name="testuser",
            text="Test message",
        )

        message = service.store_message(message_data)

        assert message is not None
        assert message.id is not None
        assert message.message_ts == "1705320000.000400"
        assert message.text == "Test message"

        # Verify it's in the database
        saved = (
            db_session.query(SlackMessage)
            .filter_by(message_ts="1705320000.000400")
            .first()
        )
        assert saved is not None

    def test_store_message_duplicate(self, service, db_session):
        """Test that storing duplicate message returns existing."""
        message_data = SlackMessageData(
            ts="1705320000.000500",
            channel_id="C0001",
            text="Test",
        )

        msg1 = service.store_message(message_data)
        msg2 = service.store_message(message_data)

        assert msg1.id == msg2.id

    def test_store_message_with_thread(self, service, db_session):
        """Test storing a message with thread association."""
        thread = service.create_thread("C0001", "1705320000.000600")

        message_data = SlackMessageData(
            ts="1705320000.000601",
            channel_id="C0001",
            text="Thread reply",
            thread_ts="1705320000.000600",
        )

        message = service.store_message(message_data, thread_id=thread.id)

        assert message.thread_id == thread.id
        assert message.thread_ts == "1705320000.000600"

    def test_get_channel_messages(self, service, db_session):
        """Test getting channel messages."""
        # Store some messages
        for i in range(3):
            message_data = SlackMessageData(
                ts=f"1705320000.00070{i}",
                channel_id="C0002",
                text=f"Message {i}",
            )
            service.store_message(message_data)

        messages = service.get_channel_messages("C0002")

        assert len(messages) == 3

    def test_get_channel_messages_with_limit(self, service, db_session):
        """Test getting limited channel messages."""
        for i in range(5):
            message_data = SlackMessageData(
                ts=f"1705320000.00080{i}",
                channel_id="C0003",
                text=f"Message {i}",
            )
            service.store_message(message_data)

        messages = service.get_channel_messages("C0003", limit=3)

        assert len(messages) == 3

    def test_get_thread_messages(self, service, db_session):
        """Test getting thread messages."""
        thread_ts = "1705320000.000900"

        # Store thread messages
        for i in range(3):
            message_data = SlackMessageData(
                ts=f"1705320000.00090{i + 1}",
                channel_id="C0004",
                text=f"Reply {i}",
                thread_ts=thread_ts,
            )
            service.store_message(message_data)

        messages = service.get_thread_messages("C0004", thread_ts)

        assert len(messages) == 3
        assert all(m.thread_ts == thread_ts for m in messages)

    def test_sync_channel_history(self, service, slack_client, db_session):
        """Test syncing channel history from Slack API."""
        # Mock client has some messages in C0001
        messages = service.sync_channel_history("C0001", limit=10)

        assert len(messages) >= 2  # Sample data

    def test_sync_thread_replies(self, service, slack_client, db_session):
        """Test syncing thread replies from Slack API."""
        # First post a message and replies
        parent = slack_client.post_message("C0001", "Parent")
        slack_client.post_message("C0001", "Reply", thread_ts=parent.ts)

        # Create local thread
        thread = service.create_thread("C0001", parent.ts)

        # Sync replies
        messages = service.sync_thread_replies("C0001", parent.ts, thread.id)

        assert len(messages) >= 1
