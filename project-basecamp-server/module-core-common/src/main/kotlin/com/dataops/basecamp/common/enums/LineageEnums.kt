package com.dataops.basecamp.common.enums

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

/**
 * Lineage Node Type
 *
 * Represents the type of a lineage node
 */
enum class LineageNodeType {
    DATASET,
    METRIC,
    TABLE,
    VIEW,
}

/**
 * Direction for lineage graph traversal
 *
 * UPSTREAM: Find resources that this resource depends on (sources)
 * DOWNSTREAM: Find resources that depend on this resource (consumers)
 * BOTH: Find both upstream and downstream resources
 */
enum class LineageDirection {
    UPSTREAM,
    DOWNSTREAM,
    BOTH,
}
