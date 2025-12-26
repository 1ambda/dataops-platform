"""Tests for Flask API endpoints."""

import json


class TestAPIEndpoints:
    """Test cases for API endpoints."""

    def test_health_endpoint(self, client):
        """Test health check endpoint."""
        response = client.get("/health")
        assert response.status_code == 200

        data = json.loads(response.data)
        assert data["status"] == "healthy"
        assert data["service"] == "sql-parser"

    def test_parse_sql_valid_select(self, client):
        """Test parse-sql endpoint with valid SELECT statement."""
        payload = {"sql": "SELECT col1, col2 FROM schema.table1 WHERE col3 = 'value'"}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)

        assert data["parsed"] is True
        assert data["error"] is None
        assert data["statement_type"] == "SELECT"
        assert "table1" in data["tables"]
        assert "schema.table1" in data["schema_qualified_tables"]
        assert all(col in data["columns"] for col in ["col1", "col2", "col3"])

    def test_parse_sql_valid_insert(self, client):
        """Test parse-sql endpoint with valid INSERT statement."""
        payload = {"sql": "INSERT INTO table1 (col1, col2) VALUES ('val1', 'val2')"}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)

        assert data["parsed"] is True
        assert data["statement_type"] == "INSERT"
        assert "table1" in data["tables"]

    def test_parse_sql_invalid_statement(self, client):
        """Test parse-sql endpoint with invalid SQL."""
        payload = {"sql": "INVALID SQL STATEMENT"}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)

        assert data["parsed"] is False
        assert data["error"] is not None

    def test_parse_sql_missing_sql_field(self, client):
        """Test parse-sql endpoint without sql field."""
        payload = {}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "Validation error" in data["error"] and "sql" in data["error"] and "required" in data["error"]

    def test_parse_sql_empty_sql(self, client):
        """Test parse-sql endpoint with empty SQL."""
        payload = {"sql": ""}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "non-empty string" in data["error"]

    def test_parse_sql_non_string_sql(self, client):
        """Test parse-sql endpoint with non-string SQL."""
        payload = {"sql": 123}
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "Validation error" in data["error"] and "should be a valid string" in data["error"]

    def test_parse_sql_wrong_content_type(self, client):
        """Test parse-sql endpoint with wrong content type."""
        response = client.post(
            "/parse-sql", data="plain text", content_type="text/plain"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "application/json" in data["error"]

    def test_validate_sql_valid(self, client):
        """Test validate-sql endpoint with valid SQL."""
        payload = {"sql": "SELECT * FROM table1"}
        response = client.post(
            "/validate-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)
        assert data["valid"] is True

    def test_validate_sql_invalid(self, client):
        """Test validate-sql endpoint with invalid SQL."""
        payload = {"sql": "INVALID SQL"}
        response = client.post(
            "/validate-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)
        assert data["valid"] is False

    def test_validate_sql_missing_field(self, client):
        """Test validate-sql endpoint without sql field."""
        payload = {}
        response = client.post(
            "/validate-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "Validation error" in data["error"] and "sql" in data["error"] and "required" in data["error"]

    def test_validate_sql_non_string(self, client):
        """Test validate-sql endpoint with non-string SQL."""
        payload = {"sql": 123}
        response = client.post(
            "/validate-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 400
        data = json.loads(response.data)
        assert "Validation error" in data["error"] and "should be a valid string" in data["error"]

    def test_not_found_endpoint(self, client):
        """Test accessing non-existent endpoint."""
        response = client.get("/non-existent")
        assert response.status_code == 404

        data = json.loads(response.data)
        assert "not found" in data["error"].lower()

    def test_complex_sql_parsing(self, client):
        """Test parsing complex SQL with JOINs and subqueries."""
        payload = {
            "sql": """
            SELECT
                o.order_id,
                c.customer_name,
                COUNT(oi.item_id) as item_count
            FROM orders.customer_orders o
            JOIN customers.customer_info c ON o.customer_id = c.customer_id
            LEFT JOIN order_items.items oi ON o.order_id = oi.order_id
            WHERE o.order_date >= '2024-01-01'
            GROUP BY o.order_id, c.customer_name
            HAVING COUNT(oi.item_id) > 1
            """
        }
        response = client.post(
            "/parse-sql", data=json.dumps(payload), content_type="application/json"
        )

        assert response.status_code == 200
        data = json.loads(response.data)

        assert data["parsed"] is True
        assert data["statement_type"] == "SELECT"
        assert len(data["tables"]) == 3
        assert "customer_orders" in data["tables"]
        assert "customer_info" in data["tables"]
        assert "items" in data["tables"]
        assert "orders.customer_orders" in data["schema_qualified_tables"]
        assert "customers.customer_info" in data["schema_qualified_tables"]
