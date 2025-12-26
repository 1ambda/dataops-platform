"""Connect Service - GitHub, Jira, and Slack integration for DataOps Platform."""

from __future__ import annotations

from typing import Any

from dotenv import load_dotenv
from flask import Flask, Response, g, jsonify, request
from pydantic import BaseModel, ValidationError

from src.connect.clients.jira import JiraClientInterface, MockJiraClient
from src.connect.clients.slack import MockSlackClient, SlackClientInterface
from src.connect.config import get_integration_config, get_server_config
from src.connect.database import get_session, init_db
from src.connect.logging_config import get_logger, setup_logging
from src.connect.services.jira_monitor import JiraMonitorService
from src.connect.services.jira_slack_integration import JiraSlackIntegrationService
from src.connect.services.slack_message import SlackMessageService

# Load environment variables
load_dotenv()

# Setup logging
setup_logging()


# --- Pydantic Models for Request/Response ---


class HealthResponse(BaseModel):
    """Response model for health check."""

    status: str
    service: str
    database: str


class ErrorResponse(BaseModel):
    """Response model for errors."""

    error: str
    code: str | None = None


class IntegrationRequest(BaseModel):
    """Base request model for integration operations."""

    source_service: str
    target_service: str
    event_type: str
    payload: dict[str, Any] | None = None


class IntegrationResponse(BaseModel):
    """Response model for integration operations."""

    success: bool
    message: str
    integration_id: int | None = None


class JiraWebhookResponse(BaseModel):
    """Response model for Jira webhook processing."""

    success: bool
    event: str
    issue_key: str
    message: str | None = None
    ticket_id: int | None = None
    thread_id: int | None = None
    link_id: int | None = None
    thread_permalink: str | None = None
    error: str | None = None


class TicketResponse(BaseModel):
    """Response model for ticket data."""

    id: int
    jira_key: str
    project_key: str
    summary: str
    status: str
    priority: str | None
    issue_type: str
    assignee_name: str | None
    thread_id: int | None = None
    thread_permalink: str | None = None


class TicketListResponse(BaseModel):
    """Response model for ticket list."""

    tickets: list[TicketResponse]
    total: int


# --- Client Factory ---


def get_jira_client() -> JiraClientInterface:
    """Get Jira client (real or mock based on configuration)."""
    config = get_integration_config()
    if config.jira_api_token and config.jira_base_url:
        # Use real client when configured
        from src.connect.clients.jira import JiraClient

        return JiraClient()
    # Fall back to mock client
    return MockJiraClient()


def get_slack_client() -> SlackClientInterface:
    """Get Slack client (real or mock based on configuration)."""
    config = get_integration_config()
    if config.slack_bot_token:
        # Use real client when configured
        from src.connect.clients.slack import SlackClient

        return SlackClient()
    # Fall back to mock client
    return MockSlackClient()


# --- Flask Application ---


def create_app() -> Flask:
    """Create and configure the Flask application."""
    config = get_server_config()
    logger = get_logger(__name__)

    app = Flask(__name__)
    app.config["DEBUG"] = config.debug

    # Initialize database
    try:
        init_db()
        logger.info("Database initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize database: {e}")
        raise

    # --- Request Lifecycle ---

    @app.before_request
    def before_request() -> None:
        """Set up request context."""
        g.db_session = get_session()
        g.jira_client = get_jira_client()
        g.slack_client = get_slack_client()

    @app.teardown_request
    def teardown_request(exception: BaseException | None = None) -> None:
        """Clean up request context."""
        session = g.pop("db_session", None)
        if session is not None:
            if exception:
                session.rollback()
            session.close()

    # --- Health Check ---

    @app.route("/health", methods=["GET"])
    def health() -> tuple[Response, int]:
        """Health check endpoint."""
        logger.debug("Health check requested")
        response = HealthResponse(
            status="healthy",
            service="connect",
            database="connected",
        )
        return jsonify(response.model_dump()), 200

    # --- Legacy Integration Endpoints ---

    @app.route("/api/v1/integrations", methods=["POST"])
    def create_integration() -> tuple[Response, int]:
        """Create a new integration event (legacy endpoint)."""
        logger.debug("Create integration request received")

        if not request.is_json:
            logger.warning("Request with invalid content type")
            error_response = ErrorResponse(
                error="Content-Type must be application/json",
                code="INVALID_CONTENT_TYPE",
            )
            return jsonify(error_response.model_dump()), 400

        try:
            data = request.get_json()
            if data is None:
                logger.warning("Request with invalid JSON")
                error_response = ErrorResponse(
                    error="Invalid JSON payload", code="INVALID_JSON"
                )
                return jsonify(error_response.model_dump()), 400

            request_data = IntegrationRequest(**data)

            logger.info(
                f"Integration request: {request_data.source_service} -> "
                f"{request_data.target_service} ({request_data.event_type})"
            )

            response = IntegrationResponse(
                success=True,
                message="Integration request received",
                integration_id=None,
            )
            return jsonify(response.model_dump()), 202

        except ValidationError as e:
            logger.error(f"Validation error: {e}")
            error_response = ErrorResponse(
                error=f"Validation error: {e!s}", code="VALIDATION_ERROR"
            )
            return jsonify(error_response.model_dump()), 400
        except Exception as e:
            logger.error(f"Unexpected error: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    @app.route("/api/v1/integrations", methods=["GET"])
    def list_integrations() -> tuple[Response, int]:
        """List integration events (legacy endpoint)."""
        logger.debug("List integrations request received")
        return jsonify({"integrations": [], "total": 0}), 200

    @app.route("/api/v1/mappings", methods=["GET"])
    def list_mappings() -> tuple[Response, int]:
        """List service mappings (legacy endpoint)."""
        logger.debug("List mappings request received")
        return jsonify({"mappings": [], "total": 0}), 200

    # --- Jira Webhook Endpoint ---

    @app.route("/api/v1/jira/webhook", methods=["POST"])
    def jira_webhook() -> tuple[Response, int]:
        """
        Handle incoming Jira webhooks.

        Processes issue created/updated events and creates Slack threads.

        Expected payload: Standard Jira webhook payload
        """
        logger.info("Jira webhook received")

        if not request.is_json:
            error_response = ErrorResponse(
                error="Content-Type must be application/json",
                code="INVALID_CONTENT_TYPE",
            )
            return jsonify(error_response.model_dump()), 400

        try:
            payload = request.get_json()
            if payload is None:
                error_response = ErrorResponse(
                    error="Invalid JSON payload", code="INVALID_JSON"
                )
                return jsonify(error_response.model_dump()), 400

            # Process the webhook
            integration_service = JiraSlackIntegrationService(
                session=g.db_session,
                jira_client=g.jira_client,
                slack_client=g.slack_client,
            )

            result = integration_service.handle_jira_webhook(payload)

            response = JiraWebhookResponse(**result)
            status_code = 200 if result.get("success") else 500

            return jsonify(response.model_dump()), status_code

        except Exception as e:
            logger.error(f"Error processing Jira webhook: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    # --- Jira Ticket Endpoints ---

    @app.route("/api/v1/jira/tickets", methods=["GET"])
    def list_tickets() -> tuple[Response, int]:
        """
        List Jira tickets from local database.

        Query parameters:
        - project: Filter by project key
        - status: Filter by status
        - limit: Maximum number of results (default 100)
        """
        logger.debug("List tickets request received")

        try:
            project = request.args.get("project")
            status = request.args.get("status")
            limit = int(request.args.get("limit", 100))

            jira_service = JiraMonitorService(
                session=g.db_session,
                jira_client=g.jira_client,
            )

            if project:
                tickets = jira_service.get_tickets_by_project(project, status)
            else:
                from src.connect.models.jira import JiraTicket

                query = g.db_session.query(JiraTicket)
                if status:
                    query = query.filter(JiraTicket.status == status)
                tickets = query.order_by(JiraTicket.created_at.desc()).limit(limit).all()

            ticket_responses = []
            for ticket in tickets[:limit]:
                ticket_responses.append(
                    TicketResponse(
                        id=ticket.id,
                        jira_key=ticket.jira_key,
                        project_key=ticket.project_key,
                        summary=ticket.summary,
                        status=ticket.status,
                        priority=ticket.priority,
                        issue_type=ticket.issue_type,
                        assignee_name=ticket.assignee_name,
                    )
                )

            response = TicketListResponse(
                tickets=ticket_responses,
                total=len(ticket_responses),
            )
            return jsonify(response.model_dump()), 200

        except Exception as e:
            logger.error(f"Error listing tickets: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    @app.route("/api/v1/jira/tickets/<jira_key>", methods=["GET"])
    def get_ticket(jira_key: str) -> tuple[Response, int]:
        """Get a specific ticket by Jira key."""
        logger.debug(f"Get ticket request: {jira_key}")

        try:
            integration_service = JiraSlackIntegrationService(
                session=g.db_session,
                jira_client=g.jira_client,
                slack_client=g.slack_client,
            )

            result = integration_service.get_ticket_with_thread(jira_key)

            if result is None:
                # Try to get just the ticket
                jira_service = JiraMonitorService(
                    session=g.db_session,
                    jira_client=g.jira_client,
                )
                ticket = jira_service.get_ticket_by_key(jira_key)
                if ticket is None:
                    error_response = ErrorResponse(
                        error=f"Ticket not found: {jira_key}",
                        code="NOT_FOUND",
                    )
                    return jsonify(error_response.model_dump()), 404

                response = TicketResponse(
                    id=ticket.id,
                    jira_key=ticket.jira_key,
                    project_key=ticket.project_key,
                    summary=ticket.summary,
                    status=ticket.status,
                    priority=ticket.priority,
                    issue_type=ticket.issue_type,
                    assignee_name=ticket.assignee_name,
                )
            else:
                ticket, thread = result
                response = TicketResponse(
                    id=ticket.id,
                    jira_key=ticket.jira_key,
                    project_key=ticket.project_key,
                    summary=ticket.summary,
                    status=ticket.status,
                    priority=ticket.priority,
                    issue_type=ticket.issue_type,
                    assignee_name=ticket.assignee_name,
                    thread_id=thread.id,
                    thread_permalink=thread.permalink,
                )

            return jsonify(response.model_dump()), 200

        except Exception as e:
            logger.error(f"Error getting ticket: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    @app.route("/api/v1/jira/tickets/<jira_key>/sync", methods=["POST"])
    def sync_ticket(jira_key: str) -> tuple[Response, int]:
        """Sync a ticket from Jira API."""
        logger.info(f"Sync ticket request: {jira_key}")

        try:
            jira_service = JiraMonitorService(
                session=g.db_session,
                jira_client=g.jira_client,
            )

            ticket = jira_service.sync_ticket(jira_key)

            response = TicketResponse(
                id=ticket.id,
                jira_key=ticket.jira_key,
                project_key=ticket.project_key,
                summary=ticket.summary,
                status=ticket.status,
                priority=ticket.priority,
                issue_type=ticket.issue_type,
                assignee_name=ticket.assignee_name,
            )
            return jsonify(response.model_dump()), 200

        except Exception as e:
            logger.error(f"Error syncing ticket: {e}", exc_info=True)
            error_response = ErrorResponse(
                error=f"Failed to sync ticket: {e!s}",
                code="SYNC_ERROR",
            )
            return jsonify(error_response.model_dump()), 500

    # --- Slack Endpoints ---

    @app.route("/api/v1/slack/channels/<channel_id>/messages", methods=["GET"])
    def get_channel_messages(channel_id: str) -> tuple[Response, int]:
        """Get messages from a Slack channel."""
        logger.debug(f"Get channel messages: {channel_id}")

        try:
            limit = int(request.args.get("limit", 100))

            slack_service = SlackMessageService(
                session=g.db_session,
                slack_client=g.slack_client,
            )

            messages = slack_service.get_channel_messages(channel_id, limit=limit)

            return jsonify({
                "messages": [msg.to_dict() for msg in messages],
                "total": len(messages),
            }), 200

        except Exception as e:
            logger.error(f"Error getting channel messages: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    @app.route("/api/v1/slack/channels/<channel_id>/sync", methods=["POST"])
    def sync_channel_messages(channel_id: str) -> tuple[Response, int]:
        """Sync messages from Slack API."""
        logger.info(f"Sync channel messages: {channel_id}")

        try:
            limit = int(request.args.get("limit", 100))

            slack_service = SlackMessageService(
                session=g.db_session,
                slack_client=g.slack_client,
            )

            messages = slack_service.sync_channel_history(channel_id, limit=limit)

            return jsonify({
                "synced": len(messages),
                "messages": [msg.to_dict() for msg in messages],
            }), 200

        except Exception as e:
            logger.error(f"Error syncing channel: {e}", exc_info=True)
            error_response = ErrorResponse(
                error=f"Failed to sync channel: {e!s}",
                code="SYNC_ERROR",
            )
            return jsonify(error_response.model_dump()), 500

    @app.route("/api/v1/slack/threads/<int:thread_id>", methods=["GET"])
    def get_thread(thread_id: int) -> tuple[Response, int]:
        """Get a thread and its messages."""
        logger.debug(f"Get thread: {thread_id}")

        try:
            slack_service = SlackMessageService(
                session=g.db_session,
                slack_client=g.slack_client,
            )

            thread = slack_service.get_thread_by_id(thread_id)
            if thread is None:
                error_response = ErrorResponse(
                    error=f"Thread not found: {thread_id}",
                    code="NOT_FOUND",
                )
                return jsonify(error_response.model_dump()), 404

            messages = slack_service.get_thread_messages(
                thread.channel_id, thread.thread_ts
            )

            return jsonify({
                "thread": thread.to_dict(),
                "messages": [msg.to_dict() for msg in messages],
            }), 200

        except Exception as e:
            logger.error(f"Error getting thread: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    # --- Link Endpoints ---

    @app.route("/api/v1/links", methods=["GET"])
    def list_links() -> tuple[Response, int]:
        """List all Jira-Slack links."""
        logger.debug("List links request received")

        try:
            from src.connect.models.linking import JiraSlackLink

            links = g.db_session.query(JiraSlackLink).all()

            return jsonify({
                "links": [link.to_dict() for link in links],
                "total": len(links),
            }), 200

        except Exception as e:
            logger.error(f"Error listing links: {e}", exc_info=True)
            error_response = ErrorResponse(
                error="Internal server error", code="INTERNAL_ERROR"
            )
            return jsonify(error_response.model_dump()), 500

    # --- Error Handlers ---

    @app.errorhandler(404)
    def not_found(error: Any) -> tuple[Response, int]:
        logger.warning(f"404 error: {request.url}")
        error_response = ErrorResponse(error="Endpoint not found", code="NOT_FOUND")
        return jsonify(error_response.model_dump()), 404

    @app.errorhandler(500)
    def internal_error(error: Any) -> tuple[Response, int]:
        logger.error(f"500 error: {error}", exc_info=True)
        error_response = ErrorResponse(
            error="Internal server error", code="INTERNAL_ERROR"
        )
        return jsonify(error_response.model_dump()), 500

    @app.errorhandler(413)
    def request_too_large(error: Any) -> tuple[Response, int]:
        logger.warning("Request payload too large")
        error_response = ErrorResponse(
            error="Request payload too large", code="PAYLOAD_TOO_LARGE"
        )
        return jsonify(error_response.model_dump()), 413

    logger.info("Flask application created successfully")
    return app


def main() -> None:
    """Main entry point for the application."""
    config = get_server_config()
    logger = get_logger(__name__)

    logger.info("Starting Connect Service")
    logger.info(
        f"Configuration: host={config.host}, port={config.port}, debug={config.debug}"
    )

    try:
        app = create_app()
        app.run(
            host=config.host,
            port=config.port,
            debug=config.debug,
            use_reloader=config.debug,
        )
    except Exception as e:
        logger.error(f"Failed to start application: {e}", exc_info=True)
        raise


if __name__ == "__main__":
    main()
