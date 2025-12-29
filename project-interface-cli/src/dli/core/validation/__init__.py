"""DLI Core Validation Module.

This module provides local-only validation capabilities for the DLI CLI:
- SpecValidator: YAML spec file validation using Pydantic schemas
- DepValidator: Local dependency checking for depends_on references
- SQL validation: Uses SQLValidator from dli.core.validator

The validation pipeline is LOCAL ONLY - no server interaction.

Validation stages:
1. YAML Schema Validation (Pydantic) - Required fields, type consistency
2. SQL Syntax Validation (SQLGlot) - Dialect-aware parsing, warnings
3. Local Dependency Validation (Optional) - Check depends_on references exist
"""

from __future__ import annotations

from dli.core.validation.dep_validator import (
    DepValidationResult,
    DepValidator,
    ProjectDepSummary,
)
from dli.core.validation.spec_validator import (
    SpecValidationResult,
    SpecValidator,
    ValidationSummary,
)

__all__ = [
    "DepValidationResult",
    "DepValidator",
    "ProjectDepSummary",
    "SpecValidationResult",
    "SpecValidator",
    "ValidationSummary",
]
