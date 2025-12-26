"""API endpoint tests."""

import json


class TestHealthEndpoint:
    """Tests for the /health endpoint."""

    def test_health_returns_200(self, client):
        """Test that health check returns 200 OK."""
        response = client.get("/health")
        assert response.status_code == 200

    def test_health_returns_correct_structure(self, client):
        """Test that health check returns expected JSON structure."""
        response = client.get("/health")
        data = json.loads(response.data)

        assert "status" in data
        assert "service" in data
        assert "database" in data
        assert data["status"] == "healthy"
        assert data["service"] == "connect"
        assert data["database"] == "connected"


class TestIntegrationsEndpoint:
    """Tests for the /api/v1/integrations endpoint."""

    def test_create_integration_requires_json(self, client):
        """Test that POST requires JSON content type."""
        response = client.post(
            "/api/v1/integrations",
            data="not json",
            content_type="text/plain",
        )
        assert response.status_code == 400
        data = json.loads(response.data)
        assert "error" in data
        assert "Content-Type" in data["error"]

    def test_create_integration_validates_payload(self, client):
        """Test that POST validates required fields."""
        response = client.post(
            "/api/v1/integrations",
            data=json.dumps({"invalid": "payload"}),
            content_type="application/json",
        )
        assert response.status_code == 400
        data = json.loads(response.data)
        assert "error" in data
        assert "Validation" in data["error"]

    def test_create_integration_accepts_valid_payload(self, client):
        """Test that POST accepts valid integration request."""
        payload = {
            "source_service": "slack",
            "target_service": "jira",
            "event_type": "create_ticket",
            "payload": {"message": "Test message"},
        }
        response = client.post(
            "/api/v1/integrations",
            data=json.dumps(payload),
            content_type="application/json",
        )
        # Should return 202 Accepted (processing pending)
        assert response.status_code == 202
        data = json.loads(response.data)
        assert data["success"] is True

    def test_list_integrations_returns_empty_list(self, client):
        """Test that GET returns empty list initially."""
        response = client.get("/api/v1/integrations")
        assert response.status_code == 200
        data = json.loads(response.data)
        assert "integrations" in data
        assert "total" in data
        assert data["integrations"] == []
        assert data["total"] == 0


class TestMappingsEndpoint:
    """Tests for the /api/v1/mappings endpoint."""

    def test_list_mappings_returns_empty_list(self, client):
        """Test that GET returns empty list initially."""
        response = client.get("/api/v1/mappings")
        assert response.status_code == 200
        data = json.loads(response.data)
        assert "mappings" in data
        assert "total" in data
        assert data["mappings"] == []
        assert data["total"] == 0


class TestErrorHandling:
    """Tests for error handling."""

    def test_404_returns_json_error(self, client):
        """Test that 404 errors return JSON response."""
        response = client.get("/nonexistent/endpoint")
        assert response.status_code == 404
        data = json.loads(response.data)
        assert "error" in data
        assert data["code"] == "NOT_FOUND"
