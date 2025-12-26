"""SQLGlot-based SQL validator for the DLI Core Engine.

This module provides the SQLValidator class which validates SQL syntax,
extracts metadata, and formats SQL using SQLGlot.
"""

from __future__ import annotations

import sqlglot
from sqlglot import exp
from sqlglot.errors import ParseError

from dli.core.models import ValidationResult


class SQLValidator:
    """SQLGlot-based SQL validator for syntax checking and metadata extraction.

    Attributes:
        dialect: SQL dialect for parsing (e.g., 'trino', 'bigquery', 'postgres')
    """

    def __init__(self, dialect: str = "trino"):
        """Initialize the validator with a SQL dialect.

        Args:
            dialect: SQL dialect for parsing
        """
        self.dialect = dialect

    def validate(self, sql: str, phase: str = "main") -> ValidationResult:
        """Validate SQL syntax and check for common issues.

        Args:
            sql: SQL string to validate
            phase: Execution phase (pre, main, post)

        Returns:
            ValidationResult with is_valid flag, errors, and warnings
        """
        errors: list[str] = []
        warnings: list[str] = []

        if not sql or not sql.strip():
            errors.append("Empty or invalid SQL")
            return ValidationResult(is_valid=False, errors=errors, phase=phase)

        try:
            parsed = sqlglot.parse(sql, dialect=self.dialect)

            if not parsed or all(stmt is None for stmt in parsed):
                errors.append("Empty or invalid SQL")
                return ValidationResult(is_valid=False, errors=errors, phase=phase)

            # Check for common issues
            for stmt in parsed:
                if stmt is None:
                    continue

                # Warn about SELECT without LIMIT (only main SELECT, not subqueries)
                select_node = stmt.find(exp.Select)
                if (
                    select_node
                    and not stmt.find(exp.Limit)
                    and isinstance(stmt, exp.Select)
                ):
                    warnings.append(
                        "SELECT without LIMIT may return large result sets"
                    )

                # Warn about SELECT *
                if select_node:
                    for star in stmt.find_all(exp.Star):
                        if star:
                            warnings.append("SELECT * may include unnecessary columns")
                            break

            return ValidationResult(
                is_valid=True,
                warnings=warnings,
                rendered_sql=sql,
                phase=phase,
            )

        except ParseError as e:
            errors.append(f"SQL syntax error: {e}")
            return ValidationResult(is_valid=False, errors=errors, phase=phase)

    def validate_multiple(
        self, sqls: list[tuple[str, str]]
    ) -> list[ValidationResult]:
        """Validate multiple SQL statements.

        Args:
            sqls: List of (sql, phase) tuples

        Returns:
            List of ValidationResult objects
        """
        return [self.validate(sql, phase) for sql, phase in sqls]

    def extract_tables(self, sql: str) -> list[str]:
        """Extract table names referenced in the SQL.

        Args:
            sql: SQL string to analyze

        Returns:
            List of unique table names
        """
        try:
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)
            tables: set[str] = set()

            for table in parsed.find_all(exp.Table):
                # Build fully qualified name if available
                parts = []
                if table.catalog:
                    parts.append(table.catalog)
                if table.db:
                    parts.append(table.db)
                if table.name:
                    parts.append(table.name)

                if parts:
                    tables.add(".".join(parts))

            return sorted(tables)
        except ParseError:
            return []

    def extract_columns(self, sql: str) -> list[str]:
        """Extract column names from SELECT clause.

        Args:
            sql: SQL string to analyze

        Returns:
            List of column names or aliases
        """
        try:
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)
        except ParseError:
            return []

        columns: list[str] = []
        for expression in parsed.find_all(exp.Select):
            for col_expr in expression.expressions:
                if isinstance(col_expr, exp.Alias):
                    columns.append(col_expr.alias)
                elif isinstance(col_expr, exp.Column):
                    columns.append(col_expr.name)
                elif isinstance(col_expr, exp.Star):
                    columns.append("*")
                else:
                    # For complex expressions, try to get the name
                    columns.append(str(col_expr))

        return columns

    def format_sql(self, sql: str, pretty: bool = True) -> str:
        """Format SQL for readability.

        Args:
            sql: SQL string to format
            pretty: Whether to use multi-line formatting

        Returns:
            Formatted SQL string (returns original if parsing fails)
        """
        try:
            result = sqlglot.transpile(
                sql,
                read=self.dialect,
                write=self.dialect,
                pretty=pretty,
            )
            return result[0] if result else sql
        except ParseError:
            return sql

    def transpile(
        self, sql: str, target_dialect: str, pretty: bool = True
    ) -> str:
        """Transpile SQL from source dialect to target dialect.

        Args:
            sql: SQL string to transpile
            target_dialect: Target SQL dialect
            pretty: Whether to use multi-line formatting

        Returns:
            Transpiled SQL string (returns original if parsing fails)
        """
        try:
            result = sqlglot.transpile(
                sql,
                read=self.dialect,
                write=target_dialect,
                pretty=pretty,
            )
            return result[0] if result else sql
        except ParseError:
            return sql

    def get_query_type(self, sql: str) -> str | None:  # noqa: PLR0911 - Pattern matching on SQL types
        """Determine the type of SQL statement.

        Args:
            sql: SQL string to analyze

        Returns:
            Query type ('SELECT', 'INSERT', 'UPDATE', 'DELETE', 'MERGE', etc.)
            or None if unable to determine
        """
        try:
            parsed = sqlglot.parse_one(sql, dialect=self.dialect)
        except ParseError:
            return None

        match parsed:
            case exp.Select():
                return "SELECT"
            case exp.Insert():
                return "INSERT"
            case exp.Update():
                return "UPDATE"
            case exp.Delete():
                return "DELETE"
            case exp.Merge():
                return "MERGE"
            case exp.Create():
                return "CREATE"
            case exp.Drop():
                return "DROP"
            case exp.Alter():
                return "ALTER"
            case _:
                return parsed.__class__.__name__.upper()

    def is_dml(self, sql: str) -> bool:
        """Check if the SQL is a DML statement.

        Args:
            sql: SQL string to check

        Returns:
            True if the SQL is INSERT, UPDATE, DELETE, or MERGE
        """
        query_type = self.get_query_type(sql)
        return query_type in ("INSERT", "UPDATE", "DELETE", "MERGE")

    def is_select(self, sql: str) -> bool:
        """Check if the SQL is a SELECT statement.

        Args:
            sql: SQL string to check

        Returns:
            True if the SQL is a SELECT statement
        """
        return self.get_query_type(sql) == "SELECT"
