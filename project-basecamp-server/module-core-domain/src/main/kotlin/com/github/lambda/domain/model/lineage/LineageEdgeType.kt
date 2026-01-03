package com.github.lambda.domain.model.lineage

/**
 * Lineage Edge Type
 *
 * Represents the type of relationship between lineage nodes
 */
enum class LineageEdgeType {
    DIRECT, // Direct dependency (e.g., dataset directly reads from a table)
    INDIRECT, // Indirect dependency (e.g., dataset depends on another dataset)
    MANUAL, // Manually defined dependency
}
