"""Slack API client interface and implementations.

Based on Slack Web API:
https://api.slack.com/methods

This module provides:
- Protocol-based interface for structural typing (duck typing)
- ABC-based interface for traditional inheritance
- Real implementation using httpx
- Mock implementation for testing
- Factory function for dependency injection
"""

from __future__ import annotations

import time
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from datetime import datetime
from typing import Any, Protocol, runtime_checkable

import httpx

from src.connect.config import get_integration_config
from src.connect.exceptions import SlackError
from src.connect.logging_config import get_logger

logger = get_logger(__name__)


@dataclass
class SlackUser:
    """Represents a Slack user."""

    id: str
    name: str
    real_name: str | None = None
    is_bot: bool = False


@dataclass
class SlackChannel:
    """Represents a Slack channel."""

    id: str
    name: str
    is_private: bool = False
    is_archived: bool = False


@dataclass
class SlackMessageData:
    """Represents a Slack message.

    Attributes:
        ts: Message timestamp (unique identifier)
        channel_id: Channel ID where message was posted
        user_id: User who posted the message
        text: Message text
        thread_ts: Parent thread timestamp (if this is a reply)
        is_bot: Whether the message was sent by a bot
        reactions: List of reactions
        attachments: List of attachments
    """

    ts: str
    channel_id: str
    user_id: str | None = None
    user_name: str | None = None
    text: str | None = None
    thread_ts: str | None = None
    is_bot: bool = False
    reactions: list[dict[str, Any]] = field(default_factory=list)
    attachments: list[dict[str, Any]] = field(default_factory=list)
    permalink: str | None = None

    @property
    def sent_at(self) -> datetime | None:
        """Convert ts to datetime."""
        try:
            return datetime.fromtimestamp(float(self.ts))
        except (ValueError, TypeError):
            return None

    @classmethod
    def from_api_response(
        cls, data: dict[str, Any], channel_id: str
    ) -> SlackMessageData:
        """Create from Slack API response."""
        return cls(
            ts=data.get("ts", ""),
            channel_id=channel_id,
            user_id=data.get("user"),
            user_name=data.get("username"),
            text=data.get("text"),
            thread_ts=data.get("thread_ts"),
            is_bot=data.get("bot_id") is not None or data.get("subtype") == "bot_message",
            reactions=data.get("reactions", []),
            attachments=data.get("attachments", []),
            permalink=data.get("permalink"),
        )


@dataclass
class PostMessageResponse:
    """Response from posting a message."""

    ok: bool
    channel: str
    ts: str
    message: dict[str, Any] | None = None
    permalink: str | None = None
    error: str | None = None


@runtime_checkable
class SlackClientProtocol(Protocol):
    """Protocol-based interface for Slack API client.

    This protocol enables structural typing (duck typing), allowing any object
    that implements the required methods to be used as a Slack client without
    explicit inheritance. Use this for type hints in function signatures.

    Example:
        def send_notification(client: SlackClientProtocol, channel: str) -> None:
            client.post_message(channel, "Hello!")
    """

    def post_message(
        self,
        channel: str,
        text: str,
        thread_ts: str | None = None,
        blocks: list[dict[str, Any]] | None = None,
    ) -> PostMessageResponse:
        """Post a message to a channel."""
        ...

    def get_channel_history(
        self,
        channel: str,
        oldest: str | None = None,
        latest: str | None = None,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get message history from a channel."""
        ...

    def get_thread_replies(
        self,
        channel: str,
        thread_ts: str,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get replies in a thread."""
        ...

    def get_permalink(self, channel: str, message_ts: str) -> str:
        """Get a permalink to a message."""
        ...

    def get_channel_info(self, channel: str) -> SlackChannel:
        """Get information about a channel."""
        ...

    def add_reaction(
        self,
        channel: str,
        timestamp: str,
        name: str,
    ) -> bool:
        """Add a reaction to a message."""
        ...


class SlackClientInterface(ABC):
    """Abstract base class for Slack API client.

    This interface defines the contract for interacting with Slack using
    traditional inheritance. Use SlackClientProtocol for structural typing.

    Note:
        Both SlackClient and MockSlackClient inherit from this ABC.
        For dependency injection, prefer using SlackClientProtocol in type hints.
    """

    @abstractmethod
    def post_message(
        self,
        channel: str,
        text: str,
        thread_ts: str | None = None,
        blocks: list[dict[str, Any]] | None = None,
    ) -> PostMessageResponse:
        """Post a message to a channel.

        Args:
            channel: Channel ID or name
            text: Message text (fallback for blocks)
            thread_ts: Optional thread timestamp to reply to
            blocks: Optional Block Kit blocks

        Returns:
            PostMessageResponse with message details

        Raises:
            SlackError: If posting fails
        """

    @abstractmethod
    def get_channel_history(
        self,
        channel: str,
        oldest: str | None = None,
        latest: str | None = None,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get message history from a channel.

        Args:
            channel: Channel ID
            oldest: Start of time range (timestamp)
            latest: End of time range (timestamp)
            limit: Maximum number of messages

        Returns:
            List of SlackMessageData objects
        """

    @abstractmethod
    def get_thread_replies(
        self,
        channel: str,
        thread_ts: str,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get replies in a thread.

        Args:
            channel: Channel ID
            thread_ts: Parent message timestamp
            limit: Maximum number of messages

        Returns:
            List of SlackMessageData objects
        """

    @abstractmethod
    def get_permalink(self, channel: str, message_ts: str) -> str:
        """Get a permalink to a message.

        Args:
            channel: Channel ID
            message_ts: Message timestamp

        Returns:
            Permalink URL
        """

    @abstractmethod
    def get_channel_info(self, channel: str) -> SlackChannel:
        """Get information about a channel.

        Args:
            channel: Channel ID

        Returns:
            SlackChannel object
        """

    @abstractmethod
    def add_reaction(
        self,
        channel: str,
        timestamp: str,
        name: str,
    ) -> bool:
        """Add a reaction to a message.

        Args:
            channel: Channel ID
            timestamp: Message timestamp
            name: Emoji name (without colons, e.g., "white_check_mark")

        Returns:
            True if reaction was added successfully
        """


class SlackClient(SlackClientInterface):
    """Real Slack API client using httpx.

    Uses Slack Web API with Bot token authentication.
    """

    BASE_URL = "https://slack.com/api"

    def __init__(self, bot_token: str | None = None) -> None:
        """Initialize the Slack client.

        Args:
            bot_token: Slack bot token (xoxb-...)
        """
        config = get_integration_config()
        self.bot_token = bot_token or config.slack_bot_token

        if not self.bot_token:
            raise SlackError("Slack bot token not configured")

        self._client = httpx.Client(
            base_url=self.BASE_URL,
            headers={
                "Authorization": f"Bearer {self.bot_token}",
                "Content-Type": "application/json; charset=utf-8",
            },
            timeout=30.0,
        )

    def _handle_response(self, response: httpx.Response) -> dict[str, Any]:
        """Handle Slack API response and check for errors."""
        response.raise_for_status()
        data = response.json()

        if not data.get("ok"):
            error = data.get("error", "Unknown error")
            logger.error(f"Slack API error: {error}")
            raise SlackError(f"Slack API error: {error}")

        return data

    def post_message(
        self,
        channel: str,
        text: str,
        thread_ts: str | None = None,
        blocks: list[dict[str, Any]] | None = None,
    ) -> PostMessageResponse:
        """Post a message to a channel."""
        try:
            payload: dict[str, Any] = {
                "channel": channel,
                "text": text,
            }
            if thread_ts:
                payload["thread_ts"] = thread_ts
            if blocks:
                payload["blocks"] = blocks

            response = self._client.post("/chat.postMessage", json=payload)
            data = self._handle_response(response)

            # Get permalink
            permalink = None
            try:
                permalink = self.get_permalink(data["channel"], data["ts"])
            except Exception:
                pass

            return PostMessageResponse(
                ok=True,
                channel=data["channel"],
                ts=data["ts"],
                message=data.get("message"),
                permalink=permalink,
            )
        except SlackError:
            raise
        except Exception as e:
            logger.error(f"Failed to post message: {e}")
            raise SlackError(f"Failed to post message: {e}")

    def get_channel_history(
        self,
        channel: str,
        oldest: str | None = None,
        latest: str | None = None,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get message history from a channel."""
        try:
            params: dict[str, Any] = {
                "channel": channel,
                "limit": min(limit, 1000),
            }
            if oldest:
                params["oldest"] = oldest
            if latest:
                params["latest"] = latest

            response = self._client.get("/conversations.history", params=params)
            data = self._handle_response(response)

            messages = []
            for msg in data.get("messages", []):
                messages.append(SlackMessageData.from_api_response(msg, channel))

            return messages
        except SlackError:
            raise
        except Exception as e:
            logger.error(f"Failed to get channel history: {e}")
            raise SlackError(f"Failed to get channel history: {e}")

    def get_thread_replies(
        self,
        channel: str,
        thread_ts: str,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get replies in a thread."""
        try:
            params: dict[str, Any] = {
                "channel": channel,
                "ts": thread_ts,
                "limit": min(limit, 1000),
            }

            response = self._client.get("/conversations.replies", params=params)
            data = self._handle_response(response)

            messages = []
            for msg in data.get("messages", []):
                messages.append(SlackMessageData.from_api_response(msg, channel))

            return messages
        except SlackError:
            raise
        except Exception as e:
            logger.error(f"Failed to get thread replies: {e}")
            raise SlackError(f"Failed to get thread replies: {e}")

    def get_permalink(self, channel: str, message_ts: str) -> str:
        """Get a permalink to a message."""
        try:
            params = {
                "channel": channel,
                "message_ts": message_ts,
            }
            response = self._client.get("/chat.getPermalink", params=params)
            data = self._handle_response(response)
            return data["permalink"]
        except SlackError:
            raise
        except Exception as e:
            logger.error(f"Failed to get permalink: {e}")
            raise SlackError(f"Failed to get permalink: {e}")

    def get_channel_info(self, channel: str) -> SlackChannel:
        """Get information about a channel."""
        try:
            params = {"channel": channel}
            response = self._client.get("/conversations.info", params=params)
            data = self._handle_response(response)

            ch = data["channel"]
            return SlackChannel(
                id=ch["id"],
                name=ch.get("name", ""),
                is_private=ch.get("is_private", False),
                is_archived=ch.get("is_archived", False),
            )
        except SlackError:
            raise
        except Exception as e:
            logger.error(f"Failed to get channel info: {e}")
            raise SlackError(f"Failed to get channel info: {e}")

    def add_reaction(
        self,
        channel: str,
        timestamp: str,
        name: str,
    ) -> bool:
        """Add a reaction to a message."""
        try:
            payload = {
                "channel": channel,
                "timestamp": timestamp,
                "name": name,
            }
            response = self._client.post("/reactions.add", json=payload)
            self._handle_response(response)
            return True
        except SlackError as e:
            # "already_reacted" is not an error for our purposes
            if "already_reacted" in str(e):
                return True
            raise
        except Exception as e:
            logger.error(f"Failed to add reaction: {e}")
            raise SlackError(f"Failed to add reaction: {e}")


class MockSlackClient(SlackClientInterface):
    """Mock Slack client for testing.

    Provides controllable responses without making real API calls.
    """

    def __init__(self) -> None:
        """Initialize the mock client."""
        self._channels: dict[str, SlackChannel] = {}
        self._messages: dict[str, list[SlackMessageData]] = {}
        self._message_counter = int(time.time() * 1000)
        self._setup_sample_data()

    def _setup_sample_data(self) -> None:
        """Set up sample channels and messages for testing."""
        # Sample channels
        self._channels = {
            "C0001": SlackChannel(id="C0001", name="general"),
            "C0002": SlackChannel(id="C0002", name="engineering"),
            "C0003": SlackChannel(id="C0003", name="jira-notifications"),
        }

        # Sample messages
        base_ts = time.time() - 3600  # 1 hour ago
        self._messages = {
            "C0001": [
                SlackMessageData(
                    ts=f"{base_ts + 100:.6f}",
                    channel_id="C0001",
                    user_id="U001",
                    user_name="alice",
                    text="Hello team!",
                ),
                SlackMessageData(
                    ts=f"{base_ts + 200:.6f}",
                    channel_id="C0001",
                    user_id="U002",
                    user_name="bob",
                    text="Hi Alice!",
                ),
            ],
            "C0002": [],
            "C0003": [],
        }

    def _generate_ts(self) -> str:
        """Generate a unique message timestamp."""
        self._message_counter += 1
        return f"{time.time():.6f}"

    def add_mock_channel(self, channel: SlackChannel) -> None:
        """Add a mock channel for testing."""
        self._channels[channel.id] = channel
        if channel.id not in self._messages:
            self._messages[channel.id] = []

    def post_message(
        self,
        channel: str,
        text: str,
        thread_ts: str | None = None,
        blocks: list[dict[str, Any]] | None = None,
    ) -> PostMessageResponse:
        """Post a message to a channel."""
        if channel not in self._channels and not channel.startswith("C"):
            # Try to find channel by name
            for ch_id, ch in self._channels.items():
                if ch.name == channel:
                    channel = ch_id
                    break
            else:
                raise SlackError(f"Channel not found: {channel}")

        if channel not in self._messages:
            self._messages[channel] = []

        ts = self._generate_ts()
        message = SlackMessageData(
            ts=ts,
            channel_id=channel,
            user_id="BOT",
            user_name="connect-bot",
            text=text,
            thread_ts=thread_ts,
            is_bot=True,
            permalink=f"https://slack.com/archives/{channel}/p{ts.replace('.', '')}",
        )
        self._messages[channel].append(message)

        return PostMessageResponse(
            ok=True,
            channel=channel,
            ts=ts,
            message={"text": text, "ts": ts},
            permalink=message.permalink,
        )

    def get_channel_history(
        self,
        channel: str,
        oldest: str | None = None,
        latest: str | None = None,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get message history from a channel."""
        if channel not in self._messages:
            raise SlackError(f"Channel not found: {channel}")

        messages = self._messages[channel]

        # Filter by time range
        if oldest:
            messages = [m for m in messages if float(m.ts) >= float(oldest)]
        if latest:
            messages = [m for m in messages if float(m.ts) <= float(latest)]

        # Filter to only top-level messages (not thread replies)
        messages = [m for m in messages if m.thread_ts is None or m.thread_ts == m.ts]

        # Sort by ts descending (newest first) and limit
        messages = sorted(messages, key=lambda m: float(m.ts), reverse=True)[:limit]

        return messages

    def get_thread_replies(
        self,
        channel: str,
        thread_ts: str,
        limit: int = 100,
    ) -> list[SlackMessageData]:
        """Get replies in a thread."""
        if channel not in self._messages:
            raise SlackError(f"Channel not found: {channel}")

        # Get all messages in the thread
        messages = [
            m
            for m in self._messages[channel]
            if m.thread_ts == thread_ts or m.ts == thread_ts
        ]

        # Sort by ts ascending (oldest first) and limit
        messages = sorted(messages, key=lambda m: float(m.ts))[:limit]

        return messages

    def get_permalink(self, channel: str, message_ts: str) -> str:
        """Get a permalink to a message."""
        # Generate a mock permalink
        ts_clean = message_ts.replace(".", "")
        return f"https://slack.com/archives/{channel}/p{ts_clean}"

    def get_channel_info(self, channel: str) -> SlackChannel:
        """Get information about a channel."""
        if channel not in self._channels:
            raise SlackError(f"Channel not found: {channel}")
        return self._channels[channel]

    def get_all_messages(self, channel: str) -> list[SlackMessageData]:
        """Get all messages including thread replies (test helper)."""
        return self._messages.get(channel, [])

    def clear_messages(self, channel: str | None = None) -> None:
        """Clear messages for testing."""
        if channel:
            self._messages[channel] = []
        else:
            for ch in self._messages:
                self._messages[ch] = []

    def add_reaction(
        self,
        channel: str,
        timestamp: str,
        name: str,
    ) -> bool:
        """Add a reaction to a message."""
        if channel not in self._messages:
            raise SlackError(f"Channel not found: {channel}")

        # Find the message
        msg = next(
            (m for m in self._messages[channel] if m.ts == timestamp), None
        )
        if not msg:
            raise SlackError(f"Message not found: {timestamp}")

        # Add reaction to the message (mock behavior)
        if not hasattr(self, "_reactions"):
            self._reactions: dict[str, set[str]] = {}

        reaction_key = f"{channel}:{timestamp}"
        if reaction_key not in self._reactions:
            self._reactions[reaction_key] = set()

        self._reactions[reaction_key].add(name)
        return True

    def get_reactions(self, channel: str, timestamp: str) -> set[str]:
        """Get reactions for a message (test helper)."""
        reaction_key = f"{channel}:{timestamp}"
        if not hasattr(self, "_reactions"):
            return set()
        return self._reactions.get(reaction_key, set())


def create_slack_client(
    use_mock: bool = False,
    bot_token: str | None = None,
) -> SlackClientProtocol:
    """Factory function to create a Slack client.

    This factory function provides a clean way to instantiate Slack clients
    with dependency injection support. It returns a client that satisfies
    the SlackClientProtocol interface.

    Args:
        use_mock: If True, returns a MockSlackClient for testing
        bot_token: Slack bot token (xoxb-...)

    Returns:
        A Slack client instance implementing SlackClientProtocol

    Example:
        # Production usage
        client = create_slack_client()

        # Testing usage
        client = create_slack_client(use_mock=True)

        # Custom configuration
        client = create_slack_client(bot_token="xoxb-your-token")
    """
    if use_mock:
        return MockSlackClient()
    return SlackClient(bot_token=bot_token)
