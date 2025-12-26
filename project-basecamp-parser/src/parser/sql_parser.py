"""SQL Parser module using SQLglot for Trino SQL parsing."""

from __future__ import annotations

import time
from typing import Any, Final

import sqlglot
from sqlglot import exp

from .config import get_parser_config
from .exceptions import SQLParseError, SQLValidationError
from .logging_config import get_logger


class TrinoSQLParser:
    """Parser for Trino SQL statements using SQLglot."""

    def __init__(self) -> None:
        self.config = get_parser_config()
        self.logger = get_logger(__name__)
        self.dialect: Final[str] = self.config.dialect
        self.logger.info(f"Initialized TrinoSQLParser with dialect: {self.dialect}")

    def parse_sql(self, sql: str) -> dict[str, Any]:
        """
        Parse SQL statement and extract information about statement type, tables, and columns.

        Args:
            sql: SQL statement string

        Returns:
            Dictionary containing parsed information:
            - statement_type: Type of SQL statement (SELECT, DML, DDL, etc.)
            - tables: List of tables used in the query
            - columns: List of columns used in the query
            - schema_qualified_tables: List of schema.table references
            - parsed: Whether parsing was successful
            - error: Error message if parsing failed

        Raises:
            SQLParseError: If SQL parsing fails due to syntax errors
            ValueError: If input parameters are invalid
        """
        if not sql or not sql.strip():
            raise ValueError("SQL cannot be empty or whitespace only")
        
        if len(sql) > self.config.max_query_length:
            raise ValueError(f"SQL exceeds maximum length of {self.config.max_query_length} characters")
        
        start_time = time.time()
        
        try:
            self.logger.debug(f"Parsing SQL query of length {len(sql)}")
            
            # Parse the SQL statement
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)

            if not parsed:
                error_msg = "Failed to parse SQL statement - no valid statements found"
                self.logger.warning(error_msg)
                return self._error_result(error_msg)

            # Get statement type
            statement_type = self._get_statement_type(parsed)

            # Extract tables and columns
            tables = self._extract_tables(parsed)
            columns = self._extract_columns(parsed)
            schema_qualified_tables = self._extract_schema_qualified_tables(parsed)

            parse_time = time.time() - start_time
            self.logger.info(
                f"Successfully parsed {statement_type} statement in {parse_time:.3f}s - "
                f"Found {len(tables)} tables, {len(columns)} columns"
            )

            return {
                "statement_type": statement_type,
                "tables": sorted(list(tables)),
                "columns": sorted(list(columns)),
                "schema_qualified_tables": sorted(list(schema_qualified_tables)),
                "parsed": True,
                "error": None,
            }

        except sqlglot.ParseError as e:
            error_msg = f"SQL syntax error: {e!s}"
            self.logger.error(error_msg)
            return self._error_result(error_msg)
        except Exception as e:
            error_msg = f"Unexpected parsing error: {e!s}"
            self.logger.error(error_msg)
            return self._error_result(error_msg)

    def _get_statement_type(self, parsed: exp.Expression) -> str:
        """Determine the type of SQL statement."""
        statement_type_mapping: dict[type[exp.Expression], str] = {
            exp.Select: "SELECT",
            exp.Insert: "INSERT",
            exp.Update: "UPDATE",
            exp.Delete: "DELETE",
            exp.Create: "CREATE",
            exp.Drop: "DROP",
            exp.Alter: "ALTER",
            exp.Merge: "MERGE",
        }

        for expr_type, statement_type in statement_type_mapping.items():
            if isinstance(parsed, expr_type):
                return statement_type

        # Check for general categories
        if isinstance(parsed, (exp.Insert, exp.Update, exp.Delete, exp.Merge)):
            return "DML"
        if isinstance(parsed, (exp.Create, exp.Drop, exp.Alter)):
            return "DDL"
        return "UNKNOWN"

    def _extract_tables(self, parsed: exp.Expression) -> set[str]:
        """Extract table names from the parsed SQL."""
        tables = set()

        for table in parsed.find_all(exp.Table):
            if table.name:
                tables.add(table.name)

        return tables

    def _extract_columns(self, parsed: exp.Expression) -> set[str]:
        """Extract column names from the parsed SQL."""
        columns = set()

        # Extract columns from general SQL parts
        for column in parsed.find_all(exp.Column):
            if column.name and column.name != "*":
                columns.add(column.name)

        # For INSERT statements, extract column names from the column list
        if (
            isinstance(parsed, exp.Insert)
            and parsed.this
            and hasattr(parsed.this, "expressions")
            and parsed.this.expressions
        ):
            # Get columns from INSERT INTO table (col1, col2) part
            for expr in parsed.this.expressions:
                if isinstance(expr, exp.Identifier) and expr.this:
                    columns.add(expr.this)
                elif hasattr(expr, "name") and expr.name:
                    columns.add(expr.name)

        return columns

    def _extract_schema_qualified_tables(self, parsed: exp.Expression) -> set[str]:
        """Extract schema-qualified table names (schema.table)."""
        qualified_tables = set()

        for table in parsed.find_all(exp.Table):
            if table.catalog and table.db and table.name:
                qualified_tables.add(f"{table.catalog}.{table.db}.{table.name}")
            elif table.db and table.name:
                qualified_tables.add(f"{table.db}.{table.name}")

        return qualified_tables

    def _error_result(
        self, error_message: str
    ) -> dict[str, None | bool | str | list[str]]:
        """Return error result structure."""
        return {
            "statement_type": None,
            "tables": [],
            "columns": [],
            "schema_qualified_tables": [],
            "parsed": False,
            "error": error_message,
        }

    def validate_sql(self, sql: str) -> bool:
        """
        Validate if SQL is syntactically correct.

        Args:
            sql: SQL statement string

        Returns:
            True if valid, False otherwise
            
        Raises:
            ValueError: If input parameters are invalid
        """
        if not sql or not sql.strip():
            raise ValueError("SQL cannot be empty or whitespace only")
        
        if len(sql) > self.config.max_query_length:
            raise ValueError(f"SQL exceeds maximum length of {self.config.max_query_length} characters")
        
        try:
            self.logger.debug(f"Validating SQL query of length {len(sql)}")
            
            parsed = sqlglot.parse_one(sql, dialect=self.dialect, read=self.dialect)
            if parsed is None:
                self.logger.debug("SQL validation failed - no valid statements found")
                return False

            # Additional validation: check if the SQL contains valid keywords
            sql_upper = sql.strip().upper()
            valid_keywords = [
                "SELECT",
                "INSERT",
                "UPDATE", 
                "DELETE",
                "CREATE",
                "DROP",
                "ALTER",
                "MERGE",
                "WITH",
                "EXPLAIN",
                "DESCRIBE",
                "SHOW",
                "TRUNCATE",
                "ANALYZE",
            ]

            # Check if SQL starts with a valid keyword
            is_valid = any(sql_upper.startswith(keyword) for keyword in valid_keywords)
            
            if is_valid:
                self.logger.debug("SQL validation successful")
            else:
                self.logger.debug("SQL validation failed - invalid statement type")
                
            return is_valid
            
        except sqlglot.ParseError as e:
            self.logger.debug(f"SQL validation failed with parse error: {e}")
            return False
        except Exception as e:
            self.logger.warning(f"Unexpected error during SQL validation: {e}")
            return False
