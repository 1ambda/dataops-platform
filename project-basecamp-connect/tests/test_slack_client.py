"""Tests for Slack API client."""

import pytest

from src.connect.clients.slack import (
    MockSlackClient,
    SlackChannel,
    SlackMessageData,
)
from src.connect.exceptions import SlackError


class TestSlackMessageData:
    """Tests for SlackMessageData data class."""

    def test_from_api_response_basic(self):
        """Test parsing a basic Slack API response."""
        data = {
            "ts": "1705320000.000100",
            "user": "U123",
            "username": "testuser",
            "text": "Hello world!",
            "thread_ts": None,
        }

        message = SlackMessageData.from_api_response(data, "C001")

        assert message.ts == "1705320000.000100"
        assert message.channel_id == "C001"
        assert message.user_id == "U123"
        assert message.user_name == "testuser"
        assert message.text == "Hello world!"
        assert message.is_bot is False

    def test_from_api_response_bot_message(self):
        """Test parsing a bot message."""
        data = {
            "ts": "1705320000.000200",
            "bot_id": "B123",
            "text": "Bot message",
        }

        message = SlackMessageData.from_api_response(data, "C001")

        assert message.is_bot is True

    def test_sent_at_property(self):
        """Test sent_at timestamp conversion."""
        message = SlackMessageData(
            ts="1705320000.000100",
            channel_id="C001",
        )

        assert message.sent_at is not None
        assert message.sent_at.year >= 2024


class TestMockSlackClient:
    """Tests for MockSlackClient."""

    def test_post_message_basic(self):
        """Test posting a basic message."""
        client = MockSlackClient()

        response = client.post_message(
            channel="C0001",
            text="Test message",
        )

        assert response.ok is True
        assert response.channel == "C0001"
        assert response.ts is not None
        assert response.permalink is not None

    def test_post_message_to_thread(self):
        """Test posting a reply to a thread."""
        client = MockSlackClient()

        # Post parent message
        parent = client.post_message("C0001", "Parent message")

        # Post reply
        reply = client.post_message(
            channel="C0001",
            text="Reply message",
            thread_ts=parent.ts,
        )

        assert reply.ok is True
        assert reply.ts != parent.ts

    def test_post_message_channel_not_found(self):
        """Test posting to non-existent channel."""
        client = MockSlackClient()

        with pytest.raises(SlackError) as exc_info:
            client.post_message("nonexistent", "Test")

        assert "not found" in str(exc_info.value)

    def test_post_message_by_channel_name(self):
        """Test posting by channel name."""
        client = MockSlackClient()

        # Add a channel with a name
        client.add_mock_channel(SlackChannel(id="C9999", name="test-channel"))

        response = client.post_message("test-channel", "Test message")

        assert response.ok is True
        assert response.channel == "C9999"

    def test_get_channel_history(self):
        """Test getting channel history."""
        client = MockSlackClient()

        messages = client.get_channel_history("C0001")

        assert len(messages) >= 2  # Sample data has messages

    def test_get_channel_history_with_limit(self):
        """Test getting limited channel history."""
        client = MockSlackClient()

        # Post several messages
        for i in range(5):
            client.post_message("C0001", f"Message {i}")

        messages = client.get_channel_history("C0001", limit=3)

        assert len(messages) <= 3

    def test_get_channel_history_not_found(self):
        """Test getting history from non-existent channel."""
        client = MockSlackClient()

        with pytest.raises(SlackError):
            client.get_channel_history("NONEXISTENT")

    def test_get_thread_replies(self):
        """Test getting thread replies."""
        client = MockSlackClient()

        # Create a thread
        parent = client.post_message("C0001", "Parent")
        client.post_message("C0001", "Reply 1", thread_ts=parent.ts)
        client.post_message("C0001", "Reply 2", thread_ts=parent.ts)

        replies = client.get_thread_replies("C0001", parent.ts)

        assert len(replies) >= 2

    def test_get_permalink(self):
        """Test getting message permalink."""
        client = MockSlackClient()

        permalink = client.get_permalink("C0001", "1705320000.000100")

        assert "slack.com" in permalink
        assert "C0001" in permalink

    def test_get_channel_info(self):
        """Test getting channel info."""
        client = MockSlackClient()

        info = client.get_channel_info("C0001")

        assert info.id == "C0001"
        assert info.name == "general"

    def test_get_channel_info_not_found(self):
        """Test getting info for non-existent channel."""
        client = MockSlackClient()

        with pytest.raises(SlackError):
            client.get_channel_info("NONEXISTENT")

    def test_clear_messages(self):
        """Test clearing messages."""
        client = MockSlackClient()

        # Post some messages
        client.post_message("C0001", "Test")

        # Clear
        client.clear_messages("C0001")

        messages = client.get_all_messages("C0001")
        assert len(messages) == 0

    def test_add_mock_channel(self):
        """Test adding a mock channel."""
        client = MockSlackClient()

        client.add_mock_channel(
            SlackChannel(id="CNEW", name="new-channel", is_private=True)
        )

        info = client.get_channel_info("CNEW")

        assert info.id == "CNEW"
        assert info.name == "new-channel"
        assert info.is_private is True
