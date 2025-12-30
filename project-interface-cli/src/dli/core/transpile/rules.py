"""
Table substitution rule application using SQLGlot.

This module provides AST-based table name substitution,
ensuring safe and accurate transformations without string manipulation.
"""

from __future__ import annotations

import sqlglot
from sqlglot import exp
from sqlglot.errors import ParseError

from dli.core.transpile.exceptions import SqlParseError
from dli.core.transpile.models import DIALECT_MAP, Dialect, RuleType, TranspileRule

__all__ = [
    "apply_table_substitutions",
]

# Table path part counts (for clarity in comparisons)
_TABLE_ONLY = 1
_SCHEMA_TABLE = 2
_CATALOG_SCHEMA_TABLE = 3


def apply_table_substitutions(
    sql: str,
    rules: list[TranspileRule],
    dialect: Dialect = Dialect.TRINO,
) -> tuple[str, list[TranspileRule]]:
    """Apply table substitution rules to SQL using SQLGlot AST.

    Parses the SQL into an AST, walks through all table references,
    and applies matching substitution rules. Returns the transformed
    SQL and list of rules that were actually applied.

    Args:
        sql: SQL string to transform.
        rules: List of TranspileRule objects (only TABLE_SUBSTITUTION type used).
        dialect: SQL dialect for parsing/generating.

    Returns:
        Tuple of (transformed_sql, applied_rules):
        - transformed_sql: SQL with table names substituted
        - applied_rules: Rules that matched and were applied

    Raises:
        SqlParseError: If SQL cannot be parsed by SQLGlot.

    Example:
        >>> rules = [TranspileRule(
        ...     id="rule-001",
        ...     type=RuleType.TABLE_SUBSTITUTION,
        ...     source="raw.users",
        ...     target="warehouse.users_v2",
        ... )]
        >>> sql = "SELECT * FROM raw.users"
        >>> transformed, applied = apply_table_substitutions(sql, rules)
        >>> transformed
        'SELECT * FROM warehouse.users_v2'
        >>> len(applied)
        1
    """
    if not sql.strip():
        return sql, []

    # Filter to only table substitution rules that are enabled
    substitution_rules = [
        r for r in rules if r.type == RuleType.TABLE_SUBSTITUTION and r.enabled
    ]

    if not substitution_rules:
        return sql, []

    # Build source -> (target, rule) mapping
    # Handle both "schema.table" and "table" formats
    rule_map: dict[str, tuple[str, TranspileRule]] = {}
    for rule in substitution_rules:
        # Normalize source to lowercase for case-insensitive matching
        source_key = rule.source.lower()
        rule_map[source_key] = (rule.target, rule)

    # Parse SQL
    sqlglot_dialect = DIALECT_MAP.get(dialect, "trino")
    try:
        parsed = sqlglot.parse_one(sql, dialect=sqlglot_dialect)
    except ParseError as e:
        raise SqlParseError(
            sql=sql,
            detail=str(e),
        ) from e

    # Track applied rules
    applied_rules: list[TranspileRule] = []
    applied_rule_ids: set[str] = set()

    # Walk AST and find all table references
    for table in parsed.find_all(exp.Table):
        # Build fully qualified table name
        parts: list[str] = []
        if table.catalog:
            parts.append(table.catalog)
        if table.db:
            parts.append(table.db)
        if table.name:
            parts.append(table.name)

        if not parts:
            continue

        # Try matching with full path first, then progressively shorter
        full_name = ".".join(parts).lower()
        match_result = _find_matching_rule(full_name, parts, rule_map)

        if match_result:
            target, rule = match_result
            _apply_substitution(table, target)

            # Track applied rule (avoid duplicates)
            if rule.id not in applied_rule_ids:
                applied_rules.append(rule)
                applied_rule_ids.add(rule.id)

    # Generate transformed SQL
    transformed = parsed.sql(dialect=sqlglot_dialect)

    return transformed, applied_rules


def _find_matching_rule(
    full_name: str,
    parts: list[str],
    rule_map: dict[str, tuple[str, TranspileRule]],
) -> tuple[str, TranspileRule] | None:
    """Find matching rule for a table reference.

    Tries matching in order of specificity:
    1. Full path (catalog.schema.table or schema.table)
    2. Schema.table (if catalog present)
    3. Table name only

    Args:
        full_name: Lowercase full table name.
        parts: List of name parts [catalog?, schema?, table].
        rule_map: Mapping of source names to (target, rule).

    Returns:
        Tuple of (target, rule) if match found, None otherwise.
    """
    # Try full name first
    if full_name in rule_map:
        return rule_map[full_name]

    # Try without catalog (schema.table)
    if len(parts) >= _SCHEMA_TABLE:
        short_name = ".".join(parts[-_SCHEMA_TABLE:]).lower()
        if short_name in rule_map:
            return rule_map[short_name]

    # Try table name only
    if len(parts) >= _TABLE_ONLY:
        table_only = parts[-1].lower()
        if table_only in rule_map:
            return rule_map[table_only]

    return None


def _apply_substitution(table: exp.Table, target: str) -> None:
    """Apply substitution to a Table AST node.

    Modifies the table node in place with the new target path.

    Args:
        table: SQLGlot Table expression to modify.
        target: Target table path (e.g., "warehouse.users_v2").
    """
    target_parts = target.split(".")

    if len(target_parts) == _TABLE_ONLY:
        # Just table name
        table.set("this", exp.to_identifier(target_parts[0]))
        table.set("db", None)
        table.set("catalog", None)
    elif len(target_parts) == _SCHEMA_TABLE:
        # schema.table
        table.set("this", exp.to_identifier(target_parts[1]))
        table.set("db", exp.to_identifier(target_parts[0]))
        table.set("catalog", None)
    elif len(target_parts) >= _CATALOG_SCHEMA_TABLE:
        # catalog.schema.table
        table.set("this", exp.to_identifier(target_parts[2]))
        table.set("db", exp.to_identifier(target_parts[1]))
        table.set("catalog", exp.to_identifier(target_parts[0]))
