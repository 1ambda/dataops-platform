package com.github.lambda.domain.model.lineage

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
