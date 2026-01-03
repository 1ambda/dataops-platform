package com.github.lambda.infra.repository

import com.github.lambda.domain.model.lineage.LineageEdgeEntity
import com.github.lambda.domain.repository.LineageEdgeRepositoryDsl
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

/**
 * Lineage Edge DSL Repository Implementation
 *
 * Implements complex graph traversal operations for lineage edges using JPQL and BFS algorithms.
 */
@Repository("lineageEdgeRepositoryDsl")
class LineageEdgeRepositoryDslImpl : LineageEdgeRepositoryDsl {
    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun findUpstream(
        resourceName: String,
        maxDepth: Int,
    ): List<LineageEdgeRepositoryDsl.NodeWithDepth> =
        traverseGraph(
            startNode = resourceName,
            direction = TraversalDirection.UPSTREAM,
            maxDepth = maxDepth,
        )

    override fun findDownstream(
        resourceName: String,
        maxDepth: Int,
    ): List<LineageEdgeRepositoryDsl.NodeWithDepth> =
        traverseGraph(
            startNode = resourceName,
            direction = TraversalDirection.DOWNSTREAM,
            maxDepth = maxDepth,
        )

    override fun findBothDirections(
        resourceName: String,
        maxDepth: Int,
    ): List<LineageEdgeRepositoryDsl.NodeWithDepth> {
        val upstream =
            traverseGraph(
                startNode = resourceName,
                direction = TraversalDirection.UPSTREAM,
                maxDepth = maxDepth,
            )

        val downstream =
            traverseGraph(
                startNode = resourceName,
                direction = TraversalDirection.DOWNSTREAM,
                maxDepth = maxDepth,
            )

        val source = LineageEdgeRepositoryDsl.NodeWithDepth(resourceName, 0)

        return (upstream + source + downstream).distinctBy { it.name }
    }

    override fun findConnectedEdges(resourceName: String): List<LineageEdgeEntity> {
        val jpql = """
            SELECT e FROM LineageEdgeEntity e
            WHERE e.source = :resourceName OR e.target = :resourceName
            AND e.deletedAt IS NULL
            ORDER BY e.source, e.target
        """

        return entityManager
            .createQuery(jpql, LineageEdgeEntity::class.java)
            .setParameter("resourceName", resourceName)
            .resultList
    }

    override fun findBySourcesIn(sources: List<String>): List<LineageEdgeEntity> {
        if (sources.isEmpty()) return emptyList()

        val jpql = """
            SELECT e FROM LineageEdgeEntity e
            WHERE e.source IN :sources
            AND e.deletedAt IS NULL
            ORDER BY e.source, e.target
        """

        return entityManager
            .createQuery(jpql, LineageEdgeEntity::class.java)
            .setParameter("sources", sources)
            .resultList
    }

    override fun findByTargetsIn(targets: List<String>): List<LineageEdgeEntity> {
        if (targets.isEmpty()) return emptyList()

        val jpql = """
            SELECT e FROM LineageEdgeEntity e
            WHERE e.target IN :targets
            AND e.deletedAt IS NULL
            ORDER BY e.source, e.target
        """

        return entityManager
            .createQuery(jpql, LineageEdgeEntity::class.java)
            .setParameter("targets", targets)
            .resultList
    }

    /**
     * Enum for traversal direction
     */
    private enum class TraversalDirection {
        UPSTREAM, // Follow edges where current node is target (source -> target, we want source)
        DOWNSTREAM, // Follow edges where current node is source (source -> target, we want target)
    }

    /**
     * Generic graph traversal using BFS (Breadth-First Search)
     *
     * @param startNode The starting node for traversal
     * @param direction The direction to traverse (upstream or downstream)
     * @param maxDepth Maximum depth to traverse (-1 = unlimited)
     * @return List of nodes with their depth information
     */
    private fun traverseGraph(
        startNode: String,
        direction: TraversalDirection,
        maxDepth: Int,
    ): List<LineageEdgeRepositoryDsl.NodeWithDepth> {
        val visited = mutableSetOf<String>()
        val result = mutableListOf<LineageEdgeRepositoryDsl.NodeWithDepth>()
        val queue = mutableListOf<Pair<String, Int>>() // Pair of (nodeName, depth)

        // Start traversal
        queue.add(Pair(startNode, 0))
        visited.add(startNode)

        while (queue.isNotEmpty()) {
            val (currentNode, currentDepth) = queue.removeFirst()

            // Skip if we've reached max depth (but include the starting node at depth 0)
            if (maxDepth != -1 && kotlin.math.abs(currentDepth) >= maxDepth && currentDepth != 0) {
                continue
            }

            // Find connected edges based on direction
            val connectedNodes =
                when (direction) {
                    TraversalDirection.UPSTREAM -> {
                        // For upstream: current node is target, we want sources
                        val upstreamEdges = findByTargetsIn(listOf(currentNode))
                        upstreamEdges.map { it.source }
                    }
                    TraversalDirection.DOWNSTREAM -> {
                        // For downstream: current node is source, we want targets
                        val downstreamEdges = findBySourcesIn(listOf(currentNode))
                        downstreamEdges.map { it.target }
                    }
                }

            // Add unvisited connected nodes to queue and result
            for (connectedNode in connectedNodes) {
                if (connectedNode !in visited) {
                    visited.add(connectedNode)

                    val nextDepth =
                        when (direction) {
                            TraversalDirection.UPSTREAM -> currentDepth - 1 // Negative depth for upstream
                            TraversalDirection.DOWNSTREAM -> currentDepth + 1 // Positive depth for downstream
                        }

                    queue.add(Pair(connectedNode, nextDepth))
                    result.add(LineageEdgeRepositoryDsl.NodeWithDepth(connectedNode, nextDepth))
                }
            }
        }

        // Sort by depth (upstream first, then downstream)
        return result.sortedBy { it.depth }
    }
}
