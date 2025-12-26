"""Tests for SQL Parser module."""

import pytest

from src.parser.sql_parser import TrinoSQLParser


class TestTrinoSQLParser:
    """Test cases for TrinoSQLParser."""

    def setup_method(self):
        """Set up test fixtures."""
        self.parser = TrinoSQLParser()

    def test_parse_simple_select(self):
        """Test parsing a simple SELECT statement."""
        sql = "SELECT col1, col2 FROM table1"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["error"] is None
        assert result["statement_type"] == "SELECT"
        assert "table1" in result["tables"]
        assert "col1" in result["columns"]
        assert "col2" in result["columns"]

    def test_parse_select_with_schema(self):
        """Test parsing SELECT with schema-qualified table."""
        sql = "SELECT col1, col2 FROM schema1.table1 WHERE col3 = 'value'"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "SELECT"
        assert "table1" in result["tables"]
        assert "schema1.table1" in result["schema_qualified_tables"]
        assert all(col in result["columns"] for col in ["col1", "col2", "col3"])

    def test_parse_insert_statement(self):
        """Test parsing INSERT statement."""
        sql = "INSERT INTO table1 (col1, col2) VALUES ('value1', 'value2')"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "INSERT"
        assert "table1" in result["tables"]
        assert all(col in result["columns"] for col in ["col1", "col2"])

    def test_parse_update_statement(self):
        """Test parsing UPDATE statement."""
        sql = "UPDATE table1 SET col1 = 'newvalue' WHERE col2 = 'condition'"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "UPDATE"
        assert "table1" in result["tables"]
        assert all(col in result["columns"] for col in ["col1", "col2"])

    def test_parse_delete_statement(self):
        """Test parsing DELETE statement."""
        sql = "DELETE FROM table1 WHERE col1 = 'value'"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "DELETE"
        assert "table1" in result["tables"]
        assert "col1" in result["columns"]

    def test_parse_create_table(self):
        """Test parsing CREATE TABLE statement."""
        sql = "CREATE TABLE table1 (col1 VARCHAR(100), col2 INTEGER)"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "CREATE"
        assert "table1" in result["tables"]

    def test_parse_join_query(self):
        """Test parsing query with JOINs."""
        sql = """
        SELECT t1.col1, t2.col2
        FROM schema1.table1 t1
        JOIN schema2.table2 t2 ON t1.id = t2.table1_id
        WHERE t1.status = 'active'
        """
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "SELECT"
        assert "table1" in result["tables"]
        assert "table2" in result["tables"]
        assert "schema1.table1" in result["schema_qualified_tables"]
        assert "schema2.table2" in result["schema_qualified_tables"]

    def test_parse_complex_query(self):
        """Test parsing complex query with subqueries."""
        sql = """
        SELECT
            main.customer_id,
            main.order_count,
            agg.total_amount
        FROM (
            SELECT customer_id, COUNT(*) as order_count
            FROM orders.customer_orders
            WHERE order_date >= '2024-01-01'
            GROUP BY customer_id
        ) main
        LEFT JOIN (
            SELECT customer_id, SUM(amount) as total_amount
            FROM payments.customer_payments
            GROUP BY customer_id
        ) agg ON main.customer_id = agg.customer_id
        """
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "SELECT"
        assert "customer_orders" in result["tables"]
        assert "customer_payments" in result["tables"]
        assert "orders.customer_orders" in result["schema_qualified_tables"]
        assert "payments.customer_payments" in result["schema_qualified_tables"]

    def test_parse_invalid_sql(self):
        """Test parsing invalid SQL."""
        sql = "INVALID SQL STATEMENT"
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is False
        assert result["error"] is not None
        assert result["statement_type"] is None

    def test_parse_empty_sql(self):
        """Test parsing empty SQL."""
        sql = ""

        # Empty SQL should raise a ValueError
        with pytest.raises(ValueError, match="SQL cannot be empty"):
            self.parser.parse_sql(sql)

    def test_validate_valid_sql(self):
        """Test SQL validation with valid SQL."""
        sql = "SELECT * FROM table1"
        assert self.parser.validate_sql(sql) is True

    def test_validate_invalid_sql(self):
        """Test SQL validation with invalid SQL."""
        sql = "INVALID SQL"
        assert self.parser.validate_sql(sql) is False

    def test_trino_specific_features(self):
        """Test parsing Trino-specific SQL features."""
        sql = """
        SELECT
            col1,
            ARRAY[1, 2, 3] as array_col,
            MAP(ARRAY['key1', 'key2'], ARRAY['val1', 'val2']) as map_col,
            ROW(1, 'hello') as row_col
        FROM catalog.schema.table1
        WHERE col2 LIKE '%pattern%'
        """
        result = self.parser.parse_sql(sql)

        assert result["parsed"] is True
        assert result["statement_type"] == "SELECT"
        assert "table1" in result["tables"]
        assert "catalog.schema.table1" in result["schema_qualified_tables"]
