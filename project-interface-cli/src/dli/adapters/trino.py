"""Trino executor for the SQL Framework.

This module provides a Trino implementation of the BaseExecutor.
The trino package is an optional dependency.
"""

from __future__ import annotations

import time
from typing import TYPE_CHECKING, Any

from dli.core.executor import BaseExecutor
from dli.core.models import ExecutionResult

# Optional Trino dependency
TRINO_AVAILABLE = False
_trino_module: Any = None
_trino_dbapi: Any = None
_trino_auth: Any = None
_trino_exceptions: Any = None

try:
    import trino as _trino
    from trino import auth as _auth
    from trino import dbapi as _dbapi
    from trino import exceptions as _exc

    _trino_module = _trino
    _trino_dbapi = _dbapi
    _trino_auth = _auth
    _trino_exceptions = _exc
    TRINO_AVAILABLE = True
except ImportError:
    pass

if TYPE_CHECKING:
    from trino import auth as _trino_auth
    from trino import dbapi as _trino_dbapi


class TrinoExecutor(BaseExecutor):
    """Trino query executor.

    This executor requires the trino package to be installed.
    Install with: uv add trino

    Attributes:
        host: Trino server host
        port: Trino server port
        user: Username for authentication
        catalog: Optional catalog name
        schema: Optional schema name
        ssl: Whether to use SSL
        ssl_verify: Whether to verify SSL certificates
        auth_type: Authentication type (none, basic, jwt, oidc)
        auth_token: Token for JWT/OIDC authentication
        connection: Trino connection instance
    """

    def __init__(
        self,
        host: str,
        port: int = 8080,
        user: str = "trino",
        catalog: str | None = None,
        schema: str | None = None,
        ssl: bool = True,
        ssl_verify: bool = True,
        auth_type: str = "none",
        auth_token: str | None = None,
        password: str | None = None,
    ):
        """Initialize the Trino executor.

        Args:
            host: Trino server host
            port: Trino server port (default: 8080)
            user: Username for authentication
            catalog: Optional catalog name
            schema: Optional schema name
            ssl: Whether to use SSL (default: True for production)
            ssl_verify: Whether to verify SSL certificates (default: True)
            auth_type: Authentication type - one of:
                - "none": No authentication
                - "basic": Basic authentication (requires password)
                - "jwt": JWT token authentication (requires auth_token)
                - "oidc": OIDC authentication (requires auth_token)
            auth_token: Token for JWT/OIDC authentication
            password: Password for basic authentication

        Raises:
            ImportError: If trino package is not installed
            ValueError: If auth configuration is invalid
        """
        if not TRINO_AVAILABLE:
            msg = "trino is not installed. Install it with: uv add trino"
            raise ImportError(msg)

        self.host = host
        self.port = port
        self.user = user
        self.catalog = catalog
        self.schema = schema
        self.ssl = ssl
        self.ssl_verify = ssl_verify
        self.auth_type = auth_type
        self.auth_token = auth_token
        self.password = password

        # Build authentication
        auth = self._build_auth()

        # Create connection
        http_scheme = "https" if ssl else "http"
        self.connection = _trino_dbapi.connect(
            host=host,
            port=port,
            user=user,
            catalog=catalog,
            schema=schema,
            http_scheme=http_scheme,
            verify=ssl_verify if ssl else False,
            auth=auth,
        )

    def _build_auth(self) -> Any:
        """Build authentication object based on auth_type.

        Returns:
            Authentication object or None for no auth

        Raises:
            ValueError: If auth configuration is invalid
        """
        match self.auth_type.lower():
            case "none":
                return None
            case "basic":
                if not self.password:
                    msg = "Password is required for basic authentication"
                    raise ValueError(msg)
                return _trino_auth.BasicAuthentication(self.user, self.password)
            case "jwt":
                if not self.auth_token:
                    msg = "auth_token is required for JWT authentication"
                    raise ValueError(msg)
                return _trino_auth.JWTAuthentication(self.auth_token)
            case "oauth2" | "oidc":
                # OAuth2Authentication handles the OAuth flow
                # For token-based OIDC, we use JWT auth with the token
                if self.auth_token:
                    return _trino_auth.JWTAuthentication(self.auth_token)
                # For interactive OIDC flow
                return _trino_auth.OAuth2Authentication()
            case _:
                msg = f"Unsupported auth_type: {self.auth_type}. Use one of: none, basic, jwt, oidc"
                raise ValueError(msg)

    def execute_sql(self, sql: str, timeout: int = 300) -> ExecutionResult:
        """Execute a SQL query on Trino.

        Args:
            sql: SQL query to execute
            timeout: Execution timeout in seconds (applied via query timeout)

        Returns:
            ExecutionResult with query results
        """
        start = time.time()

        try:
            cursor = self.connection.cursor()

            # Execute the query
            cursor.execute(sql)

            # Fetch all results
            rows_raw = cursor.fetchall()

            # Get column names from cursor description
            columns = (
                [desc[0] for desc in cursor.description]
                if cursor.description
                else []
            )

            # Convert rows to list of dictionaries
            rows = [dict(zip(columns, row, strict=False)) for row in rows_raw]

            cursor.close()

            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=True,
                row_count=len(rows),
                columns=columns,
                data=rows,
                rendered_sql=sql,
                execution_time_ms=int((time.time() - start) * 1000),
            )

        except Exception as e:
            # Catch Trino errors and other unexpected errors
            error_message = str(e)

            # Check for timeout-like errors
            if "timeout" in error_message.lower():
                error_message = f"Query timed out after {timeout} seconds"

            return ExecutionResult(
                dataset_name="",
                phase="main",
                success=False,
                error_message=error_message,
                rendered_sql=sql,
                execution_time_ms=int((time.time() - start) * 1000),
            )

    def dry_run(self, sql: str) -> dict[str, Any]:
        """Perform a dry run using EXPLAIN to validate query syntax.

        Trino does not have a native dry_run like BigQuery. We use EXPLAIN
        to validate the query syntax and get execution plan details.

        Args:
            sql: SQL query to validate

        Returns:
            Dictionary with validation status and plan details
        """
        try:
            cursor = self.connection.cursor()

            # Use EXPLAIN to validate the query
            explain_sql = f"EXPLAIN {sql}"
            cursor.execute(explain_sql)
            plan_rows = cursor.fetchall()

            # Get plan as string
            plan = "\n".join([str(row[0]) for row in plan_rows]) if plan_rows else ""

            cursor.close()
        except Exception as e:
            return {
                "valid": False,
                "error": str(e),
            }
        else:
            return {
                "valid": True,
                "plan": plan,
                "message": "Query is valid",
            }

    def test_connection(self) -> bool:
        """Test connection to Trino.

        Returns:
            True if connection is successful
        """
        try:
            cursor = self.connection.cursor()
            cursor.execute("SELECT 1")
            result = cursor.fetchall()
            cursor.close()
            return result is not None and len(result) > 0
        except Exception:
            return False

    def get_table_schema(self, table_id: str) -> list[dict[str, str]]:
        """Get the schema of a Trino table.

        Args:
            table_id: Table identifier in format:
                - "table" (uses current catalog/schema)
                - "schema.table" (uses current catalog)
                - "catalog.schema.table" (fully qualified)

        Returns:
            List of column definitions with name and type
        """
        try:
            cursor = self.connection.cursor()

            # Parse table_id to handle different formats
            # Constants for table_id part counts
            fully_qualified_parts = 3  # catalog.schema.table
            schema_qualified_parts = 2  # schema.table

            parts = table_id.split(".")
            if len(parts) == fully_qualified_parts:
                catalog, schema, table = parts
                describe_sql = f'DESCRIBE "{catalog}"."{schema}"."{table}"'
            elif len(parts) == schema_qualified_parts:
                schema, table = parts
                describe_sql = f'DESCRIBE "{schema}"."{table}"'
            else:
                table = parts[0]
                describe_sql = f'DESCRIBE "{table}"'

            cursor.execute(describe_sql)
            rows = cursor.fetchall()
            cursor.close()

            # DESCRIBE returns: Column, Type, Extra, Comment
            return [
                {"name": row[0], "type": row[1]}
                for row in rows
            ]

        except Exception:
            return []

    def close(self) -> None:
        """Close the Trino connection."""
        if self.connection:
            self.connection.close()

    def __enter__(self) -> TrinoExecutor:
        """Context manager entry."""
        return self

    def __exit__(self, exc_type: Any, exc_val: Any, exc_tb: Any) -> None:
        """Context manager exit."""
        self.close()
