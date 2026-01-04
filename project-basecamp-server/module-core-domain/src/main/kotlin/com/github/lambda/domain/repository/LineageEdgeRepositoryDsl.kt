package com.github.lambda.domain.repository

import com.github.lambda.domain.entity.lineage.LineageEdgeEntity

/**
 * Lineage Edge Repository DSL Interface (Pure Domain Abstraction)
 *
 * Defines complex graph traversal operations for lineage edges.
 * Provides abstraction for complex lineage graph queries.
 */
interface LineageEdgeRepositoryDsl {
    /**
     * Data class to represent a node with its depth in the lineage graph
     *
     * @param name The node name
     * @param depth The depth from the source node (negative = upstream, positive = downstream, 0 = source)
     */
    data class NodeWithDepth(
        val name: String,
        val depth: Int,
    )

    /**
     * Find upstream dependencies using BFS (Breadth-First Search)
     *
     * @param resourceName The starting resource name
     * @param maxDepth Maximum depth to traverse (-1 = unlimited)
     * @return List of nodes with their depth information (depth will be negative for upstream)
     */
    fun findUpstream(
        resourceName: String,
        maxDepth: Int = -1,
    ): List<NodeWithDepth>

    /**
     * Find downstream dependencies using BFS (Breadth-First Search)
     *
     * @param resourceName The starting resource name
     * @param maxDepth Maximum depth to traverse (-1 = unlimited)
     * @return List of nodes with their depth information (depth will be positive for downstream)
     */
    fun findDownstream(
        resourceName: String,
        maxDepth: Int = -1,
    ): List<NodeWithDepth>

    /**
     * Find both upstream and downstream dependencies
     *
     * @param resourceName The starting resource name
     * @param maxDepth Maximum depth to traverse in each direction (-1 = unlimited)
     * @return List of nodes with their depth information (negative = upstream, positive = downstream, 0 = source)
     */
    fun findBothDirections(
        resourceName: String,
        maxDepth: Int = -1,
    ): List<NodeWithDepth>

    /**
     * Find edges connected to a specific resource (either as source or target)
     */
    fun findConnectedEdges(resourceName: String): List<LineageEdgeEntity>

    /**
     * Find edges by source names
     */
    fun findBySourcesIn(sources: List<String>): List<LineageEdgeEntity>

    /**
     * Find edges by target names
     */
    fun findByTargetsIn(targets: List<String>): List<LineageEdgeEntity>
}
