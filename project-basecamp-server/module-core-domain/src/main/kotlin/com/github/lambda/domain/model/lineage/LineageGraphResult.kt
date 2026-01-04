package com.github.lambda.domain.model.lineage

import com.github.lambda.domain.entity.lineage.LineageEdgeEntity
import com.github.lambda.domain.entity.lineage.LineageNodeEntity
import com.github.lambda.domain.repository.lineage.LineageEdgeRepositoryDsl.NodeWithDepth

/**
 * Result of lineage graph traversal
 *
 * Contains the root node, all discovered nodes with their depths,
 * edges connecting them, and summary counts.
 *
 * @param rootNode The starting node for the lineage traversal
 * @param nodes All nodes found during traversal (including root)
 * @param edges All edges connecting the nodes
 * @param totalUpstream Count of upstream nodes (negative depth)
 * @param totalDownstream Count of downstream nodes (positive depth)
 */
data class LineageGraphResult(
    val rootNode: LineageNodeEntity,
    val nodes: List<NodeWithDepth>,
    val edges: List<LineageEdgeEntity>,
    val totalUpstream: Int,
    val totalDownstream: Int,
) {
    companion object {
        /**
         * Create LineageGraphResult from traversal results
         */
        fun create(
            rootNode: LineageNodeEntity,
            nodes: List<NodeWithDepth>,
            edges: List<LineageEdgeEntity>,
        ): LineageGraphResult {
            val upstreamCount = nodes.count { it.depth < 0 }
            val downstreamCount = nodes.count { it.depth > 0 }

            return LineageGraphResult(
                rootNode = rootNode,
                nodes = nodes,
                edges = edges,
                totalUpstream = upstreamCount,
                totalDownstream = downstreamCount,
            )
        }
    }
}
