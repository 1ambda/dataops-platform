package com.dataops.basecamp.domain.service

import com.dataops.basecamp.common.enums.LineageDirection
import com.dataops.basecamp.common.exception.ResourceNotFoundException
import com.dataops.basecamp.domain.entity.lineage.LineageEdgeEntity
import com.dataops.basecamp.domain.projection.lineage.LineageGraphProjection
import com.dataops.basecamp.domain.repository.lineage.LineageEdgeRepositoryDsl
import com.dataops.basecamp.domain.repository.lineage.LineageNodeRepositoryJpa
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Service for lineage graph operations
 *
 * Provides business logic for retrieving and analyzing resource lineage relationships.
 * Supports upstream, downstream, and bidirectional lineage traversal with configurable depth limits.
 */
@Service
@Transactional(readOnly = true)
class LineageService(
    private val lineageNodeRepositoryJpa: LineageNodeRepositoryJpa,
    private val lineageEdgeRepositoryDsl: LineageEdgeRepositoryDsl,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get lineage graph for a resource
     *
     * @param resourceName Fully qualified resource name (e.g., "iceberg.analytics.users")
     * @param direction Direction for lineage traversal (UPSTREAM, DOWNSTREAM, BOTH)
     * @param depth Maximum depth for traversal (-1 for unlimited)
     * @return LineageGraphProjection containing root, nodes, edges, and summary counts
     * @throws ResourceNotFoundException if resource not found in lineage graph
     */
    fun getLineageGraph(
        resourceName: String,
        direction: LineageDirection = LineageDirection.BOTH,
        depth: Int = -1,
    ): LineageGraphProjection {
        logger.info(
            "Getting lineage graph for resource: {}, direction: {}, depth: {}",
            resourceName,
            direction,
            depth,
        )

        // 1. Find the root node
        val rootNode =
            lineageNodeRepositoryJpa.findById(resourceName).orElse(null)
                ?: throw ResourceNotFoundException("Lineage resource", resourceName)

        // 2. Traverse the graph based on direction
        val (nodes, edges) =
            when (direction) {
                LineageDirection.UPSTREAM -> traverseUpstream(resourceName, depth)
                LineageDirection.DOWNSTREAM -> traverseDownstream(resourceName, depth)
                LineageDirection.BOTH -> traverseBoth(resourceName, depth)
            }

        // 3. Create and return the result
        val result =
            LineageGraphProjection.create(
                rootNode = rootNode,
                nodes = nodes,
                edges = edges,
            )

        logger.info(
            "Lineage graph retrieved for {}: {} nodes, {} edges, {} upstream, {} downstream",
            resourceName,
            nodes.size,
            edges.size,
            result.totalUpstream,
            result.totalDownstream,
        )

        return result
    }

    /**
     * Traverse upstream dependencies (resources this resource depends on)
     */
    private fun traverseUpstream(
        resourceName: String,
        depth: Int,
    ): Pair<
        List<LineageEdgeRepositoryDsl.NodeWithDepth>,
        List<LineageEdgeEntity>,
    > {
        val nodes = lineageEdgeRepositoryDsl.findUpstream(resourceName, depth)
        val edges = getRelevantEdges(nodes.map { it.name })
        return Pair(nodes, edges)
    }

    /**
     * Traverse downstream dependents (resources that depend on this resource)
     */
    private fun traverseDownstream(
        resourceName: String,
        depth: Int,
    ): Pair<
        List<LineageEdgeRepositoryDsl.NodeWithDepth>,
        List<LineageEdgeEntity>,
    > {
        val nodes = lineageEdgeRepositoryDsl.findDownstream(resourceName, depth)
        val edges = getRelevantEdges(nodes.map { it.name })
        return Pair(nodes, edges)
    }

    /**
     * Traverse both directions (upstream and downstream)
     */
    private fun traverseBoth(
        resourceName: String,
        depth: Int,
    ): Pair<
        List<LineageEdgeRepositoryDsl.NodeWithDepth>,
        List<LineageEdgeEntity>,
    > {
        val allNodes = lineageEdgeRepositoryDsl.findBothDirections(resourceName, depth)

        val edges = getRelevantEdges(allNodes.map { it.name })
        return Pair(allNodes, edges)
    }

    /**
     * Get edges that connect the given nodes
     */
    private fun getRelevantEdges(nodeNames: List<String>): List<LineageEdgeEntity> {
        if (nodeNames.isEmpty()) return emptyList()

        // Get all connected edges for the nodes
        return nodeNames
            .flatMap { nodeName ->
                lineageEdgeRepositoryDsl.findConnectedEdges(nodeName)
            }.distinct()
    }

    /**
     * Check if a resource exists in the lineage graph
     *
     * @param resourceName Resource name to check
     * @return true if resource exists, false otherwise
     */
    fun existsInLineage(resourceName: String): Boolean = lineageNodeRepositoryJpa.existsById(resourceName)

    /**
     * Get lineage statistics for a resource
     *
     * @param resourceName Resource name
     * @return Map with count statistics
     */
    fun getLineageStatistics(resourceName: String): Map<String, Int> {
        if (!existsInLineage(resourceName)) {
            return emptyMap()
        }

        val upstreamCount = lineageEdgeRepositoryDsl.findUpstream(resourceName, -1).size
        val downstreamCount = lineageEdgeRepositoryDsl.findDownstream(resourceName, -1).size

        return mapOf(
            "upstream_count" to upstreamCount,
            "downstream_count" to downstreamCount,
            "total_connected" to (upstreamCount + downstreamCount),
        )
    }
}
