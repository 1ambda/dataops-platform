"""Jira-Slack integration service.

Handles the main integration logic:
- Creating Slack threads for new Jira tickets
- Linking Jira tickets to Slack threads
- Syncing Slack thread replies TO Jira as comments (Jira is SOT)
- Sending closure notifications when tickets reach terminal status
"""

from __future__ import annotations

from datetime import datetime
from typing import TYPE_CHECKING, Any

from sqlalchemy.orm import Session

from src.connect.clients.jira import JiraClientInterface, JiraCommentData
from src.connect.clients.slack import SlackClientInterface, SlackMessageData
from src.connect.config import get_integration_config
from src.connect.logging_config import get_logger
from src.connect.models.closure import TicketClosureNotification
from src.connect.models.comment import SlackReplySync, SyncStatus
from src.connect.models.jira import JiraTicket
from src.connect.models.linking import JiraSlackLink, LinkType
from src.connect.models.slack import SlackThread
from src.connect.services.jira_monitor import JiraMonitorService
from src.connect.services.slack_message import SlackMessageService

# Backward compatibility alias
JiraComment = SlackReplySync

if TYPE_CHECKING:
    pass

logger = get_logger(__name__)


class JiraSlackIntegrationService:
    """Main integration service for Jira-Slack workflows.

    This service orchestrates:
    - Creating Slack threads when Jira tickets are created
    - Posting updates to Slack when Jira tickets are updated
    - Managing bidirectional links between systems
    """

    def __init__(
        self,
        session: Session,
        jira_client: JiraClientInterface,
        slack_client: SlackClientInterface,
        notification_channel: str = "jira-notifications",
    ) -> None:
        """Initialize the integration service.

        Args:
            session: SQLAlchemy database session
            jira_client: Jira API client
            slack_client: Slack API client
            notification_channel: Default channel for Jira notifications
        """
        self._session = session
        self._jira_client = jira_client
        self._slack_client = slack_client
        self._notification_channel = notification_channel
        self._config = get_integration_config()

        # Initialize sub-services
        self._jira_monitor = JiraMonitorService(session, jira_client)
        self._slack_service = SlackMessageService(session, slack_client)

    def handle_jira_webhook(self, payload: dict[str, Any]) -> dict[str, Any]:
        """Handle incoming Jira webhook and create Slack thread if needed.

        This is the main entry point for Jira webhook processing.

        Args:
            payload: Raw webhook payload from Jira

        Returns:
            Result dictionary with processing details
        """
        webhook_event = payload.get("webhookEvent", "")
        issue_data = payload.get("issue", {})
        issue_key = issue_data.get("key", "unknown")

        logger.info(f"Handling Jira webhook: event={webhook_event}, issue={issue_key}")

        result: dict[str, Any] = {
            "event": webhook_event,
            "issue_key": issue_key,
            "success": False,
            "ticket_id": None,
            "thread_id": None,
            "link_id": None,
        }

        try:
            # Process the webhook event
            ticket = self._jira_monitor.process_webhook_event(payload)

            if ticket is None:
                result["message"] = f"Event {webhook_event} was ignored"
                result["success"] = True
                return result

            result["ticket_id"] = ticket.id

            # For new tickets, create a Slack thread
            if webhook_event == "jira:issue_created":
                thread, link = self.create_slack_thread_for_ticket(ticket)
                result["thread_id"] = thread.id
                result["link_id"] = link.id
                result["thread_permalink"] = thread.permalink
                result["message"] = f"Created Slack thread for {issue_key}"

            # For updated tickets, post an update to the linked thread
            elif webhook_event == "jira:issue_updated":
                link = self.get_link_for_ticket(ticket.id)
                if link:
                    self._post_ticket_update(ticket, link)
                    result["thread_id"] = link.slack_thread_id
                    result["link_id"] = link.id
                    result["message"] = f"Posted update for {issue_key}"

                    # Check if ticket reached a terminal status
                    if self._config.is_closed_status(ticket.status):
                        closure_result = self.send_closure_notification(ticket, link)
                        result["closure_notification"] = closure_result
                else:
                    # No existing thread, create one
                    thread, link = self.create_slack_thread_for_ticket(ticket)
                    result["thread_id"] = thread.id
                    result["link_id"] = link.id
                    result["message"] = f"Created Slack thread for updated ticket {issue_key}"

            result["success"] = True

        except Exception as e:
            logger.error(f"Error handling Jira webhook: {e}", exc_info=True)
            result["error"] = str(e)

        return result

    def create_slack_thread_for_ticket(
        self,
        ticket: JiraTicket,
        channel: str | None = None,
    ) -> tuple[SlackThread, JiraSlackLink]:
        """Create a Slack thread for a Jira ticket.

        Posts a message to the notification channel and creates a thread.

        Args:
            ticket: JiraTicket to create thread for
            channel: Optional channel override

        Returns:
            Tuple of (SlackThread, JiraSlackLink)
        """
        channel = channel or self._notification_channel

        logger.info(
            f"Creating Slack thread for ticket {ticket.jira_key} in channel {channel}"
        )

        # Format the initial message
        message_text = self._format_ticket_message(ticket)
        blocks = self._format_ticket_blocks(ticket)

        # Post message to Slack
        response = self._slack_client.post_message(
            channel=channel,
            text=message_text,
            blocks=blocks,
        )

        # Create thread record
        thread = self._slack_service.create_thread(
            channel_id=response.channel,
            thread_ts=response.ts,
            permalink=response.permalink,
            created_by_bot=True,
        )

        # Create link between ticket and thread
        link = self._create_link(ticket, thread)

        # Add comment to Jira ticket with Slack thread link
        if thread.permalink:
            try:
                self._jira_client.add_comment(
                    ticket.jira_key,
                    f"Slack thread created: {thread.permalink}",
                )
            except Exception as e:
                logger.warning(f"Could not add Jira comment: {e}")

        return thread, link

    def _format_ticket_message(self, ticket: JiraTicket) -> str:
        """Format a plain text message for a ticket.

        Args:
            ticket: JiraTicket to format

        Returns:
            Formatted message string
        """
        return (
            f"[{ticket.jira_key}] {ticket.summary}\n"
            f"Type: {ticket.issue_type} | Priority: {ticket.priority or 'None'} | "
            f"Status: {ticket.status}\n"
            f"Assignee: {ticket.assignee_name or 'Unassigned'}"
        )

    def _format_ticket_blocks(self, ticket: JiraTicket) -> list[dict[str, Any]]:
        """Format Block Kit blocks for a ticket message.

        Args:
            ticket: JiraTicket to format

        Returns:
            List of Slack Block Kit blocks
        """
        # Build Jira URL (if we have base URL)
        jira_url = f"https://your-jira.atlassian.net/browse/{ticket.jira_key}"

        blocks = [
            {
                "type": "header",
                "text": {
                    "type": "plain_text",
                    "text": f":jira: {ticket.jira_key}: {ticket.summary[:100]}",
                    "emoji": True,
                },
            },
            {
                "type": "section",
                "fields": [
                    {
                        "type": "mrkdwn",
                        "text": f"*Type:*\n{ticket.issue_type}",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Priority:*\n{ticket.priority or 'None'}",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Status:*\n{ticket.status}",
                    },
                    {
                        "type": "mrkdwn",
                        "text": f"*Assignee:*\n{ticket.assignee_name or 'Unassigned'}",
                    },
                ],
            },
        ]

        # Add description if present
        if ticket.description:
            description = ticket.description[:500]
            if len(ticket.description) > 500:
                description += "..."
            blocks.append(
                {
                    "type": "section",
                    "text": {
                        "type": "mrkdwn",
                        "text": f"*Description:*\n{description}",
                    },
                }
            )

        # Add link to Jira
        blocks.append(
            {
                "type": "context",
                "elements": [
                    {
                        "type": "mrkdwn",
                        "text": f"<{jira_url}|View in Jira> | Project: {ticket.project_key}",
                    }
                ],
            }
        )

        return blocks

    def _post_ticket_update(
        self, ticket: JiraTicket, link: JiraSlackLink
    ) -> None:
        """Post a ticket update to the linked Slack thread.

        Args:
            ticket: Updated JiraTicket
            link: Existing link to the Slack thread
        """
        thread = self._slack_service.get_thread_by_id(link.slack_thread_id)
        if not thread:
            logger.warning(f"Thread not found for link {link.id}")
            return

        update_text = (
            f":arrows_counterclockwise: *Ticket Updated*\n"
            f"Status: {ticket.status} | "
            f"Assignee: {ticket.assignee_name or 'Unassigned'}"
        )

        self._slack_client.post_message(
            channel=thread.channel_id,
            text=update_text,
            thread_ts=thread.thread_ts,
        )

        # Update the link's last sync time
        link.last_sync_at = datetime.utcnow()
        self._session.commit()

    def _create_link(
        self,
        ticket: JiraTicket,
        thread: SlackThread,
        link_type: str = LinkType.TICKET_THREAD,
    ) -> JiraSlackLink:
        """Create a link between a ticket and a thread.

        Args:
            ticket: JiraTicket to link
            thread: SlackThread to link
            link_type: Type of link

        Returns:
            Created JiraSlackLink
        """
        # Check for existing link
        existing = (
            self._session.query(JiraSlackLink)
            .filter(
                JiraSlackLink.jira_ticket_id == ticket.id,
                JiraSlackLink.slack_thread_id == thread.id,
                JiraSlackLink.link_type == link_type,
            )
            .first()
        )

        if existing:
            logger.debug(f"Link already exists: id={existing.id}")
            return existing

        link = JiraSlackLink(
            jira_ticket_id=ticket.id,
            slack_thread_id=thread.id,
            link_type=link_type,
            sync_enabled=1,
            sync_status="active",
        )

        self._session.add(link)
        self._session.commit()
        self._session.refresh(link)

        logger.info(
            f"Created link: ticket={ticket.jira_key} -> thread={thread.thread_ts}"
        )
        return link

    def get_link_for_ticket(
        self, ticket_id: int, link_type: str = LinkType.TICKET_THREAD
    ) -> JiraSlackLink | None:
        """Get the link for a ticket.

        Args:
            ticket_id: Local ticket ID
            link_type: Type of link to find

        Returns:
            JiraSlackLink or None if not found
        """
        return (
            self._session.query(JiraSlackLink)
            .filter(
                JiraSlackLink.jira_ticket_id == ticket_id,
                JiraSlackLink.link_type == link_type,
            )
            .first()
        )

    def get_links_for_thread(
        self, thread_id: int
    ) -> list[JiraSlackLink]:
        """Get all links for a thread.

        Args:
            thread_id: Local thread ID

        Returns:
            List of JiraSlackLink entities
        """
        return (
            self._session.query(JiraSlackLink)
            .filter(JiraSlackLink.slack_thread_id == thread_id)
            .all()
        )

    def get_ticket_with_thread(
        self, jira_key: str
    ) -> tuple[JiraTicket, SlackThread] | None:
        """Get a ticket and its linked thread.

        Args:
            jira_key: Jira issue key

        Returns:
            Tuple of (JiraTicket, SlackThread) or None if not found/linked
        """
        ticket = self._jira_monitor.get_ticket_by_key(jira_key)
        if not ticket:
            return None

        link = self.get_link_for_ticket(ticket.id)
        if not link:
            return None

        thread = self._slack_service.get_thread_by_id(link.slack_thread_id)
        if not thread:
            return None

        return ticket, thread

    # =========================================================================
    # Slack Reply Sync Methods (Slack -> Jira)
    # Direction: Slack thread replies synced TO Jira as comments
    # =========================================================================

    def sync_replies_to_jira(self, jira_key: str) -> dict[str, Any]:
        """Sync all Slack thread replies to a Jira ticket as comments.

        Fetches replies from the linked Slack thread, checks which ones haven't
        been synced yet, and adds them as comments to the Jira ticket.

        Args:
            jira_key: Jira issue key (e.g., "PROJ-123")

        Returns:
            Result dictionary with sync details including:
            - success: Whether the operation succeeded
            - synced_count: Number of new replies synced to Jira
            - skipped_count: Number of already synced replies
            - error: Error message if failed
        """
        result: dict[str, Any] = {
            "jira_key": jira_key,
            "success": False,
            "synced_count": 0,
            "skipped_count": 0,
            "failed_count": 0,
            "replies": [],
        }

        try:
            # Get ticket and linked thread
            ticket_thread = self.get_ticket_with_thread(jira_key)
            if not ticket_thread:
                result["error"] = f"No linked thread found for {jira_key}"
                return result

            ticket, thread = ticket_thread

            # Fetch replies from Slack thread
            slack_replies = self._slack_client.get_thread_replies(
                channel=thread.channel_id,
                thread_ts=thread.thread_ts,
            )

            # Filter out the parent message and bot messages
            replies = [
                r for r in slack_replies
                if r.ts != thread.thread_ts and not r.is_bot
            ]

            logger.info(
                f"Found {len(replies)} user replies in thread for {jira_key}"
            )

            for reply in replies:
                sync_result = self._sync_single_reply_to_jira(
                    ticket=ticket,
                    thread=thread,
                    slack_reply=reply,
                )
                result["replies"].append(sync_result)

                if sync_result["status"] == SyncStatus.SYNCED:
                    result["synced_count"] += 1
                elif sync_result["status"] == SyncStatus.SKIPPED:
                    result["skipped_count"] += 1
                elif sync_result["status"] == SyncStatus.FAILED:
                    result["failed_count"] += 1

            result["success"] = True
            logger.info(
                f"Reply sync for {jira_key}: "
                f"synced={result['synced_count']}, "
                f"skipped={result['skipped_count']}, "
                f"failed={result['failed_count']}"
            )

        except Exception as e:
            logger.error(f"Error syncing replies for {jira_key}: {e}", exc_info=True)
            result["error"] = str(e)

        return result

    def _sync_single_reply_to_jira(
        self,
        ticket: JiraTicket,
        thread: SlackThread,
        slack_reply: SlackMessageData,
    ) -> dict[str, Any]:
        """Sync a single Slack reply to Jira as a comment.

        Args:
            ticket: JiraTicket to add the comment to
            thread: SlackThread the reply belongs to
            slack_reply: Slack message data

        Returns:
            Result dictionary with sync status
        """
        result: dict[str, Any] = {
            "slack_message_ts": slack_reply.ts,
            "status": SyncStatus.PENDING,
            "jira_comment_id": None,
        }

        try:
            # Check if reply already synced
            existing = self._get_synced_reply(ticket.id, slack_reply.ts)
            if existing:
                result["status"] = SyncStatus.SKIPPED
                result["jira_comment_id"] = existing.jira_comment_id
                logger.debug(
                    f"Reply {slack_reply.ts} already synced for ticket {ticket.jira_key}"
                )
                return result

            # Format and add comment to Jira
            comment_text = self._format_slack_reply_for_jira(slack_reply)
            jira_response = self._jira_client.add_comment(
                issue_key=ticket.jira_key,
                body=comment_text,
            )

            jira_comment_id = jira_response.get("id")

            # Record the synced reply
            synced_reply = self._create_synced_reply(
                ticket=ticket,
                thread=thread,
                slack_reply=slack_reply,
                jira_comment_id=jira_comment_id,
            )

            result["status"] = SyncStatus.SYNCED
            result["jira_comment_id"] = jira_comment_id
            result["sync_id"] = synced_reply.id

            logger.info(
                f"Synced Slack reply {slack_reply.ts} to Jira {ticket.jira_key}"
            )

        except Exception as e:
            result["status"] = SyncStatus.FAILED
            result["error"] = str(e)
            logger.error(
                f"Failed to sync reply {slack_reply.ts}: {e}", exc_info=True
            )

        return result

    def _get_synced_reply(
        self, ticket_id: int, slack_message_ts: str
    ) -> SlackReplySync | None:
        """Check if a Slack reply has already been synced to Jira.

        Args:
            ticket_id: Local ticket ID
            slack_message_ts: Slack message timestamp

        Returns:
            SlackReplySync if found, None otherwise
        """
        return (
            self._session.query(SlackReplySync)
            .filter(
                SlackReplySync.jira_ticket_id == ticket_id,
                SlackReplySync.slack_message_ts == slack_message_ts,
            )
            .first()
        )

    def _create_synced_reply(
        self,
        ticket: JiraTicket,
        thread: SlackThread,
        slack_reply: SlackMessageData,
        jira_comment_id: str | None,
    ) -> SlackReplySync:
        """Create a record for a synced Slack reply.

        Args:
            ticket: JiraTicket the reply was synced to
            thread: SlackThread the reply belongs to
            slack_reply: Original Slack message data
            jira_comment_id: Jira comment ID after syncing

        Returns:
            Created SlackReplySync record
        """
        synced_reply = SlackReplySync(
            jira_ticket_id=ticket.id,
            slack_thread_id=thread.id,
            slack_message_ts=slack_reply.ts,
            slack_user_id=slack_reply.user_id,
            slack_user_name=slack_reply.user_name,
            body=slack_reply.text,
            jira_comment_id=jira_comment_id,
            sync_status=SyncStatus.SYNCED,
            synced_at=datetime.utcnow(),
            sent_at_slack=slack_reply.sent_at,
        )

        self._session.add(synced_reply)
        self._session.commit()
        self._session.refresh(synced_reply)

        return synced_reply

    def _format_slack_reply_for_jira(self, slack_reply: SlackMessageData) -> str:
        """Format a Slack reply for posting to Jira.

        Args:
            slack_reply: Slack message data

        Returns:
            Formatted comment string for Jira
        """
        author = slack_reply.user_name or slack_reply.user_id or "Unknown"
        body = slack_reply.text or "(empty message)"

        # Format timestamp if available
        timestamp = ""
        if slack_reply.sent_at:
            timestamp = f" at {slack_reply.sent_at.strftime('%Y-%m-%d %H:%M')}"

        return f"[Slack] {author}{timestamp}:\n{body}"

    def get_synced_replies_for_ticket(self, ticket_id: int) -> list[SlackReplySync]:
        """Get all synced replies for a ticket.

        Args:
            ticket_id: Local ticket ID

        Returns:
            List of SlackReplySync records
        """
        return (
            self._session.query(SlackReplySync)
            .filter(SlackReplySync.jira_ticket_id == ticket_id)
            .order_by(SlackReplySync.sent_at_slack)
            .all()
        )

    def sync_replies_for_all_linked_tickets(self) -> dict[str, Any]:
        """Sync Slack replies for all tickets that have linked threads.

        This is useful for batch synchronization.

        Returns:
            Result dictionary with overall sync statistics
        """
        result: dict[str, Any] = {
            "success": False,
            "total_tickets": 0,
            "total_synced": 0,
            "total_skipped": 0,
            "total_failed": 0,
            "ticket_results": [],
        }

        try:
            # Get all active links
            links = (
                self._session.query(JiraSlackLink)
                .filter(JiraSlackLink.sync_enabled == 1)
                .filter(JiraSlackLink.sync_status == "active")
                .all()
            )

            result["total_tickets"] = len(links)
            logger.info(f"Syncing replies for {len(links)} linked tickets")

            for link in links:
                ticket = (
                    self._session.query(JiraTicket)
                    .filter(JiraTicket.id == link.jira_ticket_id)
                    .first()
                )
                if not ticket:
                    continue

                ticket_result = self.sync_replies_to_jira(ticket.jira_key)
                result["ticket_results"].append(ticket_result)
                result["total_synced"] += ticket_result.get("synced_count", 0)
                result["total_skipped"] += ticket_result.get("skipped_count", 0)
                result["total_failed"] += ticket_result.get("failed_count", 0)

            result["success"] = True

        except Exception as e:
            logger.error(f"Error in batch reply sync: {e}", exc_info=True)
            result["error"] = str(e)

        return result

    # =========================================================================
    # Backward Compatibility Aliases
    # These methods maintain backward compatibility with old Jira->Slack sync
    # =========================================================================

    def sync_comments_for_ticket(self, jira_key: str) -> dict[str, Any]:
        """Backward compatible alias for sync_replies_to_jira.

        Note: Direction has changed from Jira->Slack to Slack->Jira.
        """
        return self.sync_replies_to_jira(jira_key)

    def get_synced_comments_for_ticket(self, ticket_id: int) -> list[SlackReplySync]:
        """Backward compatible alias for get_synced_replies_for_ticket."""
        return self.get_synced_replies_for_ticket(ticket_id)

    def sync_comments_for_all_linked_tickets(self) -> dict[str, Any]:
        """Backward compatible alias for sync_replies_for_all_linked_tickets."""
        return self.sync_replies_for_all_linked_tickets()

    # =========================================================================
    # Ticket Closure Notification Methods
    # =========================================================================

    def send_closure_notification(
        self,
        ticket: JiraTicket,
        link: JiraSlackLink,
    ) -> dict[str, Any]:
        """Send a closure notification to the linked Slack thread.

        When a Jira ticket reaches a terminal status:
        1. Send a closing message to the Slack thread
        2. Add an emoji reaction to the original thread message

        Args:
            ticket: JiraTicket that was closed
            link: JiraSlackLink to the Slack thread

        Returns:
            Result dictionary with notification details
        """
        result: dict[str, Any] = {
            "success": False,
            "jira_key": ticket.jira_key,
            "status": ticket.status,
            "message_sent": False,
            "reaction_added": False,
        }

        try:
            # Check if notification already sent
            existing = self._get_closure_notification(ticket.id, link.slack_thread_id)
            if existing:
                result["already_notified"] = True
                result["success"] = True
                logger.debug(
                    f"Closure notification already sent for {ticket.jira_key}"
                )
                return result

            # Get the thread
            thread = self._slack_service.get_thread_by_id(link.slack_thread_id)
            if not thread:
                result["error"] = "Thread not found"
                return result

            # Send closing message
            closure_message = self._format_closure_message(ticket)
            response = self._slack_client.post_message(
                channel=thread.channel_id,
                text=closure_message,
                thread_ts=thread.thread_ts,
            )
            result["message_sent"] = True
            result["message_ts"] = response.ts

            # Add emoji reaction to original message
            emoji = self._config.jira_closed_emoji
            try:
                self._slack_client.add_reaction(
                    channel=thread.channel_id,
                    timestamp=thread.parent_message_ts,
                    name=emoji,
                )
                result["reaction_added"] = True
                result["emoji"] = emoji
            except Exception as e:
                logger.warning(f"Could not add reaction: {e}")
                result["reaction_error"] = str(e)

            # Record the notification
            notification = self._create_closure_notification(
                ticket=ticket,
                thread=thread,
                message_ts=response.ts,
                reaction_added=result["reaction_added"],
                emoji=emoji if result["reaction_added"] else None,
            )
            result["notification_id"] = notification.id
            result["success"] = True

            logger.info(
                f"Sent closure notification for {ticket.jira_key} "
                f"(status: {ticket.status})"
            )

        except Exception as e:
            logger.error(
                f"Error sending closure notification for {ticket.jira_key}: {e}",
                exc_info=True,
            )
            result["error"] = str(e)

        return result

    def _get_closure_notification(
        self, ticket_id: int, thread_id: int
    ) -> TicketClosureNotification | None:
        """Check if a closure notification has already been sent.

        Args:
            ticket_id: Local ticket ID
            thread_id: Local thread ID

        Returns:
            TicketClosureNotification if found, None otherwise
        """
        return (
            self._session.query(TicketClosureNotification)
            .filter(
                TicketClosureNotification.jira_ticket_id == ticket_id,
                TicketClosureNotification.slack_thread_id == thread_id,
            )
            .first()
        )

    def _create_closure_notification(
        self,
        ticket: JiraTicket,
        thread: SlackThread,
        message_ts: str,
        reaction_added: bool,
        emoji: str | None,
    ) -> TicketClosureNotification:
        """Create a record for a closure notification.

        Args:
            ticket: JiraTicket that was closed
            thread: SlackThread the notification was sent to
            message_ts: Slack message timestamp of the notification
            reaction_added: Whether the emoji reaction was added
            emoji: The emoji name that was added (if any)

        Returns:
            Created TicketClosureNotification record
        """
        notification = TicketClosureNotification(
            jira_ticket_id=ticket.id,
            slack_thread_id=thread.id,
            jira_status=ticket.status,
            notification_message_ts=message_ts,
            reaction_added=1 if reaction_added else 0,
            reaction_emoji=emoji,
            notified_at=datetime.utcnow(),
        )

        self._session.add(notification)
        self._session.commit()
        self._session.refresh(notification)

        return notification

    def _format_closure_message(self, ticket: JiraTicket) -> str:
        """Format a closure notification message.

        Args:
            ticket: JiraTicket that was closed

        Returns:
            Formatted message string
        """
        return (
            f":white_check_mark: *Ticket Closed*\n"
            f"*{ticket.jira_key}* has been moved to *{ticket.status}*.\n"
            f"This thread is now closed."
        )
