"""SQL Parser Service for Trino SQL using SQLglot."""

from __future__ import annotations

import os
from typing import Any

from dotenv import load_dotenv
from flask import Flask, Response, jsonify, request
from pydantic import BaseModel, ValidationError

from src.parser.config import get_server_config
from src.parser.logging_config import get_logger, setup_logging
from src.parser.sql_parser import TrinoSQLParser

# Load environment variables
load_dotenv()

# Setup logging
setup_logging()


class SQLParseRequest(BaseModel):
    """Request model for SQL parsing."""

    sql: str


class SQLValidateRequest(BaseModel):
    """Request model for SQL validation."""

    sql: str


class HealthResponse(BaseModel):
    """Response model for health check."""

    status: str
    service: str


class SQLParseResponse(BaseModel):
    """Response model for SQL parsing."""

    statement_type: str | None
    tables: list[str]
    columns: list[str]
    schema_qualified_tables: list[str]
    parsed: bool
    error: str | None


class SQLValidateResponse(BaseModel):
    """Response model for SQL validation."""

    valid: bool


class ErrorResponse(BaseModel):
    """Response model for errors."""

    error: str


def create_app() -> Flask:
    """Create and configure the Flask application."""
    config = get_server_config()
    logger = get_logger(__name__)
    
    app = Flask(__name__)
    app.config["DEBUG"] = config.debug
    
    # Initialize parser
    try:
        parser = TrinoSQLParser()
        logger.info("SQL Parser initialized successfully")
    except Exception as e:
        logger.error(f"Failed to initialize SQL Parser: {e}")
        raise

    @app.route("/health", methods=["GET"])
    def health() -> tuple[Response, int]:
        """Health check endpoint."""
        logger.debug("Health check requested")
        response = HealthResponse(status="healthy", service="sql-parser")
        return jsonify(response.model_dump()), 200

    @app.route("/parse-sql", methods=["POST"])
    def parse_sql() -> tuple[Response, int]:
        """
        Parse SQL statement endpoint.

        Expected JSON payload:
        {
            "sql": "SELECT col1, col2 FROM schema.table1 WHERE col3 = 'value'"
        }

        Returns:
        {
            "statement_type": "SELECT",
            "tables": ["table1"],
            "columns": ["col1", "col2", "col3"],
            "schema_qualified_tables": ["schema.table1"],
            "parsed": true,
            "error": null
        }
        """
        logger.debug("Parse SQL request received")
        
        if not request.is_json:
            logger.warning("Parse SQL request with invalid content type")
            error_response = ErrorResponse(
                error="Content-Type must be application/json"
            )
            return jsonify(error_response.model_dump()), 400

        try:
            data = request.get_json()
            if data is None:
                logger.warning("Parse SQL request with invalid JSON")
                error_response = ErrorResponse(error="Invalid JSON payload")
                return jsonify(error_response.model_dump()), 400

            # Validate request using Pydantic
            request_data = SQLParseRequest(**data)

            if not request_data.sql.strip():
                logger.warning("Parse SQL request with empty SQL")
                error_response = ErrorResponse(error="SQL must be a non-empty string")
                return jsonify(error_response.model_dump()), 400

            # Parse the SQL
            logger.info(f"Parsing SQL query of length {len(request_data.sql)}")
            result = parser.parse_sql(request_data.sql.strip())

            # Convert to Pydantic model for consistent response structure
            response = SQLParseResponse(**result)
            status_code = 200 if response.parsed else 400
            
            if response.parsed:
                logger.info("SQL parsing completed successfully")
            else:
                logger.warning(f"SQL parsing failed: {response.error}")
                
            return jsonify(response.model_dump()), status_code

        except ValidationError as e:
            logger.error(f"Validation error in parse SQL: {e}")
            error_response = ErrorResponse(error=f"Validation error: {e!s}")
            return jsonify(error_response.model_dump()), 400
        except ValueError as e:
            logger.warning(f"Value error in parse SQL: {e}")
            error_response = ErrorResponse(error=str(e))
            return jsonify(error_response.model_dump()), 400
        except Exception as e:
            logger.error(f"Unexpected error in parse SQL: {e}", exc_info=True)
            error_response = ErrorResponse(error="Internal server error")
            return jsonify(error_response.model_dump()), 500

    @app.route("/validate-sql", methods=["POST"])
    def validate_sql() -> tuple[Response, int]:
        """
        Validate SQL statement endpoint.

        Expected JSON payload:
        {
            "sql": "SELECT * FROM table1"
        }

        Returns:
        {
            "valid": true
        }
        """
        logger.debug("Validate SQL request received")
        
        if not request.is_json:
            logger.warning("Validate SQL request with invalid content type")
            error_response = ErrorResponse(
                error="Content-Type must be application/json"
            )
            return jsonify(error_response.model_dump()), 400

        try:
            data = request.get_json()
            if data is None:
                logger.warning("Validate SQL request with invalid JSON")
                error_response = ErrorResponse(error="Invalid JSON payload")
                return jsonify(error_response.model_dump()), 400

            # Validate request using Pydantic
            request_data = SQLValidateRequest(**data)

            # Validate the SQL
            logger.info(f"Validating SQL query of length {len(request_data.sql)}")
            is_valid = parser.validate_sql(request_data.sql.strip())
            response = SQLValidateResponse(valid=is_valid)
            
            logger.info(f"SQL validation result: {is_valid}")
            return jsonify(response.model_dump()), 200

        except ValidationError as e:
            logger.error(f"Validation error in validate SQL: {e}")
            error_response = ErrorResponse(error=f"Validation error: {e!s}")
            return jsonify(error_response.model_dump()), 400
        except ValueError as e:
            logger.warning(f"Value error in validate SQL: {e}")
            error_response = ErrorResponse(error=str(e))
            return jsonify(error_response.model_dump()), 400
        except Exception as e:
            logger.error(f"Unexpected error in validate SQL: {e}", exc_info=True)
            error_response = ErrorResponse(error="Internal server error")
            return jsonify(error_response.model_dump()), 500

    @app.errorhandler(404)
    def not_found(error: Any) -> tuple[Response, int]:
        logger.warning(f"404 error: {request.url}")
        error_response = ErrorResponse(error="Endpoint not found")
        return jsonify(error_response.model_dump()), 404

    @app.errorhandler(500)
    def internal_error(error: Any) -> tuple[Response, int]:
        logger.error(f"500 error: {error}", exc_info=True)
        error_response = ErrorResponse(error="Internal server error")
        return jsonify(error_response.model_dump()), 500

    @app.errorhandler(413)
    def request_too_large(error: Any) -> tuple[Response, int]:
        logger.warning("Request payload too large")
        error_response = ErrorResponse(error="Request payload too large")
        return jsonify(error_response.model_dump()), 413

    logger.info("Flask application created successfully")
    return app


def main() -> None:
    """Main entry point for the application."""
    config = get_server_config()
    logger = get_logger(__name__)
    
    logger.info("Starting SQL Parser Service")
    logger.info(f"Configuration: host={config.host}, port={config.port}, debug={config.debug}")
    
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
