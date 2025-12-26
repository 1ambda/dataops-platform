"""Database model tests."""



class TestIntegrationLog:
    """Tests for IntegrationLog model."""

    def test_create_integration_log(self, db_session):
        """Test creating an integration log entry."""
        from src.connect.database import IntegrationLog

        log = IntegrationLog(
            source_service="slack",
            target_service="jira",
            event_type="create_ticket",
            source_id="C12345678",
            target_id="PROJ-123",
            payload='{"message": "Test"}',
            status="completed",
        )
        db_session.add(log)
        db_session.commit()

        assert log.id is not None
        assert log.source_service == "slack"
        assert log.target_service == "jira"
        assert log.status == "completed"
        assert log.created_at is not None

    def test_integration_log_repr(self, db_session):
        """Test IntegrationLog string representation."""
        from src.connect.database import IntegrationLog

        log = IntegrationLog(
            source_service="github",
            target_service="slack",
            event_type="pr_merged",
            status="pending",
        )
        db_session.add(log)
        db_session.commit()

        repr_str = repr(log)
        assert "IntegrationLog" in repr_str
        assert "github->slack" in repr_str
        assert "pr_merged" in repr_str
        assert "pending" in repr_str


class TestServiceMapping:
    """Tests for ServiceMapping model."""

    def test_create_service_mapping(self, db_session):
        """Test creating a service mapping."""
        from src.connect.database import ServiceMapping

        mapping = ServiceMapping(
            source_service="jira",
            source_id="PROJ-123",
            target_service="slack",
            target_id="C12345678:1234567890.123456",
            mapping_type="ticket_to_thread",
            extra_data='{"channel": "engineering"}',
        )
        db_session.add(mapping)
        db_session.commit()

        assert mapping.id is not None
        assert mapping.source_service == "jira"
        assert mapping.target_service == "slack"
        assert mapping.mapping_type == "ticket_to_thread"
        assert mapping.created_at is not None

    def test_service_mapping_repr(self, db_session):
        """Test ServiceMapping string representation."""
        from src.connect.database import ServiceMapping

        mapping = ServiceMapping(
            source_service="jira",
            source_id="PROJ-456",
            target_service="slack",
            target_id="thread123",
            mapping_type="ticket_to_thread",
        )
        db_session.add(mapping)
        db_session.commit()

        repr_str = repr(mapping)
        assert "ServiceMapping" in repr_str
        assert "jira:PROJ-456" in repr_str
        assert "slack:thread123" in repr_str
