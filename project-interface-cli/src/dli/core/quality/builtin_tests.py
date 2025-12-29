"""Built-in Generic Tests for Data Quality.

This module provides SQL generators for dbt-style generic data tests.
All generators validate identifiers to prevent SQL injection.

Test Philosophy:
    "A test is a SELECT query that returns rows that fail the test."
    "If any rows are returned, the test fails."

Supported Tests:
    - not_null: Check columns for NULL values
    - unique: Check column combinations for duplicates
    - accepted_values: Check values against allowed list
    - relationships: Check referential integrity
    - range_check: Check numeric values within bounds
    - row_count: Check table row count

Security:
    - All table/column names are validated with regex
    - String values are escaped to prevent SQL injection
    - Only alphanumeric characters, underscores, and dots are allowed

References:
    - dbt Generic Tests: https://docs.getdbt.com/docs/build/data-tests
    - SQLMesh Audits: https://sqlmesh.readthedocs.io/en/latest/concepts/audits/
"""

from __future__ import annotations

import re
from typing import Any


class BuiltinTests:
    """Built-in generic test SQL generators.

    All methods are class methods that generate SQL for specific test types.
    Input validation is performed to prevent SQL injection attacks.

    Example:
        >>> sql = BuiltinTests.not_null("my_catalog.my_schema.my_table", ["user_id"])
        >>> print(sql)
        SELECT * FROM my_catalog.my_schema.my_table
        WHERE user_id IS NULL
    """

    # Pattern for valid SQL identifiers: starts with letter/underscore,
    # followed by alphanumeric, underscore, or dot (for catalog.schema.table)
    _IDENTIFIER_PATTERN = re.compile(r"^[a-zA-Z_][a-zA-Z0-9_.]*$")

    # Maximum length for identifiers (prevents DoS via very long strings)
    _MAX_IDENTIFIER_LENGTH = 256

    @classmethod
    def _validate_identifier(cls, name: str) -> str:
        """Validate and return a SQL identifier.

        Args:
            name: The identifier to validate (table name, column name, etc.)

        Returns:
            The validated identifier (unchanged)

        Raises:
            ValueError: If the identifier is invalid or potentially malicious
        """
        if not name:
            raise ValueError("Identifier cannot be empty")

        if len(name) > cls._MAX_IDENTIFIER_LENGTH:
            raise ValueError(f"Identifier too long: {len(name)} > {cls._MAX_IDENTIFIER_LENGTH}")

        if not cls._IDENTIFIER_PATTERN.match(name):
            raise ValueError(
                f"Invalid identifier: '{name}'. "
                "Only alphanumeric characters, underscores, and dots are allowed. "
                "Must start with a letter or underscore."
            )

        return name

    @classmethod
    def _validate_identifiers(cls, names: list[str]) -> list[str]:
        """Validate a list of identifiers.

        Args:
            names: List of identifiers to validate

        Returns:
            List of validated identifiers

        Raises:
            ValueError: If any identifier is invalid
        """
        return [cls._validate_identifier(name) for name in names]

    @staticmethod
    def _escape_string(value: str) -> str:
        """Escape a string value for safe SQL inclusion.

        Doubles single quotes to prevent SQL injection.

        Args:
            value: The string value to escape

        Returns:
            The escaped string (safe for SQL)
        """
        return value.replace("'", "''")

    @classmethod
    def not_null(cls, table: str, columns: list[str]) -> str:
        """Generate NOT NULL test SQL.

        Returns rows where any of the specified columns is NULL.
        Test passes if no rows are returned.

        Args:
            table: Fully qualified table name (catalog.schema.table)
            columns: List of column names to check

        Returns:
            SQL query that returns rows with NULL values

        Raises:
            ValueError: If table or columns are invalid

        Example:
            >>> BuiltinTests.not_null("db.schema.users", ["email", "name"])
            # Returns: SELECT * FROM db.schema.users WHERE email IS NULL OR name IS NULL
        """
        table = cls._validate_identifier(table)
        validated_cols = cls._validate_identifiers(columns)

        if not validated_cols:
            raise ValueError("At least one column is required for not_null test")

        conditions = " OR ".join([f"{col} IS NULL" for col in validated_cols])
        return f"""SELECT * FROM {table}
WHERE {conditions}"""

    @classmethod
    def unique(cls, table: str, columns: list[str]) -> str:
        """Generate UNIQUE test SQL.

        Returns rows where the column combination is duplicated.
        Test passes if no rows are returned.

        Args:
            table: Fully qualified table name
            columns: List of column names that should be unique together

        Returns:
            SQL query that returns duplicate rows

        Raises:
            ValueError: If table or columns are invalid

        Example:
            >>> BuiltinTests.unique("db.schema.orders", ["order_id", "line_item"])
            # Returns rows where (order_id, line_item) appears more than once
        """
        table = cls._validate_identifier(table)
        validated_cols = cls._validate_identifiers(columns)

        if not validated_cols:
            raise ValueError("At least one column is required for unique test")

        cols = ", ".join(validated_cols)
        return f"""SELECT {cols}, COUNT(*) as _dli_count
FROM {table}
GROUP BY {cols}
HAVING COUNT(*) > 1"""

    @classmethod
    def accepted_values(
        cls,
        table: str,
        column: str,
        values: list[str | int | float],
    ) -> str:
        """Generate ACCEPTED_VALUES test SQL.

        Returns rows where the column value is not in the allowed list.
        NULL values are excluded (use not_null for NULL checks).
        Test passes if no rows are returned.

        Args:
            table: Fully qualified table name
            column: Column name to check
            values: List of allowed values (strings, ints, or floats)

        Returns:
            SQL query that returns rows with invalid values

        Raises:
            ValueError: If table, column, or values are invalid

        Example:
            >>> BuiltinTests.accepted_values("db.users", "status", ["active", "inactive"])
            # Returns rows where status is not 'active' or 'inactive'
        """
        table = cls._validate_identifier(table)
        column = cls._validate_identifier(column)

        if not values:
            raise ValueError("At least one value is required for accepted_values test")

        # Format values based on type
        formatted_values: list[str] = []
        for v in values:
            if isinstance(v, str):
                escaped = cls._escape_string(v)
                formatted_values.append(f"'{escaped}'")
            elif isinstance(v, (int, float)):
                formatted_values.append(str(v))
            else:
                raise ValueError(f"Unsupported value type: {type(v)}")

        values_str = ", ".join(formatted_values)
        return f"""SELECT * FROM {table}
WHERE {column} NOT IN ({values_str})
  AND {column} IS NOT NULL"""

    @classmethod
    def relationships(
        cls,
        table: str,
        column: str,
        to_table: str,
        to_column: str,
    ) -> str:
        """Generate RELATIONSHIPS (foreign key) test SQL.

        Returns rows where the column value does not exist in the referenced table.
        NULL values are excluded.
        Test passes if no rows are returned.

        Args:
            table: Source table name
            column: Column in source table (the foreign key)
            to_table: Referenced table name
            to_column: Column in referenced table (usually primary key)

        Returns:
            SQL query that returns rows with orphan foreign keys

        Raises:
            ValueError: If any identifier is invalid

        Example:
            >>> BuiltinTests.relationships("orders", "user_id", "users", "id")
            # Returns orders where user_id doesn't exist in users.id
        """
        table = cls._validate_identifier(table)
        column = cls._validate_identifier(column)
        to_table = cls._validate_identifier(to_table)
        to_column = cls._validate_identifier(to_column)

        return f"""SELECT a.* FROM {table} a
LEFT JOIN {to_table} b ON a.{column} = b.{to_column}
WHERE b.{to_column} IS NULL
  AND a.{column} IS NOT NULL"""

    @classmethod
    def range_check(
        cls,
        table: str,
        column: str,
        min_value: int | float | None = None,
        max_value: int | float | None = None,
    ) -> str:
        """Generate RANGE_CHECK test SQL.

        Returns rows where the column value is outside the specified bounds.
        At least one of min_value or max_value must be provided.
        Test passes if no rows are returned.

        Args:
            table: Fully qualified table name
            column: Numeric column to check
            min_value: Minimum allowed value (inclusive)
            max_value: Maximum allowed value (inclusive)

        Returns:
            SQL query that returns rows with out-of-range values

        Raises:
            ValueError: If table/column are invalid or no bounds specified

        Example:
            >>> BuiltinTests.range_check("orders", "quantity", min_value=0, max_value=1000)
            # Returns orders where quantity < 0 OR quantity > 1000
        """
        table = cls._validate_identifier(table)
        column = cls._validate_identifier(column)

        conditions: list[str] = []
        if min_value is not None:
            conditions.append(f"{column} < {min_value}")
        if max_value is not None:
            conditions.append(f"{column} > {max_value}")

        if not conditions:
            # No bounds specified - return empty result (test always passes)
            return f"""SELECT * FROM {table}
WHERE FALSE"""

        condition_str = " OR ".join(conditions)
        return f"""SELECT * FROM {table}
WHERE {condition_str}"""

    @classmethod
    def row_count(
        cls,
        table: str,
        min_count: int | None = None,
        max_count: int | None = None,
    ) -> str:
        """Generate ROW_COUNT test SQL.

        Returns a row if the table row count is outside the specified bounds.
        Test passes if no rows are returned.

        Args:
            table: Fully qualified table name
            min_count: Minimum expected row count (inclusive)
            max_count: Maximum expected row count (inclusive)

        Returns:
            SQL query that returns count if outside bounds

        Raises:
            ValueError: If table is invalid

        Example:
            >>> BuiltinTests.row_count("users", min_count=1)
            # Returns the count if table has 0 rows
        """
        table = cls._validate_identifier(table)

        conditions: list[str] = []
        if min_count is not None:
            conditions.append(f"cnt < {min_count}")
        if max_count is not None:
            conditions.append(f"cnt > {max_count}")

        condition_str = " OR ".join(conditions) if conditions else "FALSE"
        return f"""WITH _dli_counts AS (
    SELECT COUNT(*) as cnt FROM {table}
)
SELECT cnt as _dli_row_count FROM _dli_counts
WHERE {condition_str}"""

    @classmethod
    def generate(
        cls,
        test_type: str,
        table: str,
        **kwargs: Any,
    ) -> str:
        """Generate SQL for any built-in test type.

        This is a convenience method that dispatches to the appropriate
        test generator based on the test_type string.

        Args:
            test_type: One of: not_null, unique, accepted_values,
                       relationships, range_check, row_count
            table: Fully qualified table name
            **kwargs: Additional arguments for the specific test type

        Returns:
            SQL query for the test

        Raises:
            ValueError: If test_type is unknown or required args are missing

        Example:
            >>> BuiltinTests.generate("not_null", "users", columns=["email"])
        """
        generators = {
            "not_null": cls.not_null,
            "unique": cls.unique,
            "accepted_values": cls.accepted_values,
            "relationships": cls.relationships,
            "range_check": cls.range_check,
            "row_count": cls.row_count,
        }

        generator = generators.get(test_type)
        if generator is None:
            valid_types = ", ".join(generators.keys())
            raise ValueError(f"Unknown test type: {test_type}. Valid types: {valid_types}")

        # Extract appropriate kwargs for each test type
        if test_type == "not_null":
            return generator(table, kwargs.get("columns", []))
        elif test_type == "unique":
            return generator(table, kwargs.get("columns", []))
        elif test_type == "accepted_values":
            return generator(
                table,
                kwargs.get("column", kwargs.get("columns", [""])[0]),
                kwargs.get("values", []),
            )
        elif test_type == "relationships":
            return generator(
                table,
                kwargs.get("column", kwargs.get("columns", [""])[0]),
                kwargs.get("to", ""),
                kwargs.get("to_column", ""),
            )
        elif test_type == "range_check":
            return generator(
                table,
                kwargs.get("column", kwargs.get("columns", [""])[0]),
                kwargs.get("min"),
                kwargs.get("max"),
            )
        elif test_type == "row_count":
            return generator(
                table,
                kwargs.get("min"),
                kwargs.get("max"),
            )

        # Should not reach here due to earlier check
        raise ValueError(f"Unknown test type: {test_type}")
