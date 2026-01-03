package com.github.lambda.domain.model.lineage

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
