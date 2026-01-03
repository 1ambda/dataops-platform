package com.github.lambda.domain.service

import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.domain.model.lineage.LineageDirection
import com.github.lambda.domain.model.lineage.LineageEdgeEntity
import com.github.lambda.domain.model.lineage.LineageEdgeType
import com.github.lambda.domain.model.lineage.LineageNodeEntity
import com.github.lambda.domain.model.lineage.LineageNodeType
import com.github.lambda.domain.repository.LineageEdgeRepositoryDsl
import com.github.lambda.domain.repository.LineageNodeRepositoryJpa
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.Optional

/**
 * LineageService Unit Tests
 *
 * Uses MockK to isolate dependencies and test pure business logic.
 */
@DisplayName("LineageService Unit Tests")
class LineageServiceTest {
    private val lineageNodeRepositoryJpa: LineageNodeRepositoryJpa = mockk()
    private val lineageEdgeRepositoryDsl: LineageEdgeRepositoryDsl = mockk()
    private val lineageService = LineageService(lineageNodeRepositoryJpa, lineageEdgeRepositoryDsl)

    private lateinit var testRootNode: LineageNodeEntity
    private lateinit var testUpstreamNode1: LineageNodeEntity
    private lateinit var testUpstreamNode2: LineageNodeEntity
    private lateinit var testDownstreamNode1: LineageNodeEntity
    private lateinit var testDownstreamNode2: LineageNodeEntity

    private lateinit var testUpstreamEdge1: LineageEdgeEntity
    private lateinit var testUpstreamEdge2: LineageEdgeEntity
    private lateinit var testDownstreamEdge1: LineageEdgeEntity
    private lateinit var testDownstreamEdge2: LineageEdgeEntity

    @BeforeEach
    fun setUp() {
        // Root node (the target resource)
        testRootNode = LineageNodeEntity(
            name = "iceberg.analytics.users",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "User dimension table",
            tags = mutableSetOf("tier::critical", "domain::analytics")
        )

        // Upstream nodes (dependencies)
        testUpstreamNode1 = LineageNodeEntity(
            name = "bigquery.raw.user_events",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "Raw user events",
            tags = mutableSetOf("tier::bronze", "domain::events")
        )

        testUpstreamNode2 = LineageNodeEntity(
            name = "bigquery.raw.user_profiles",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "User profile data",
            tags = mutableSetOf("tier::bronze", "domain::users")
        )

        // Downstream nodes (dependents)
        testDownstreamNode1 = LineageNodeEntity(
            name = "iceberg.marts.user_summary",
            type = LineageNodeType.VIEW,
            owner = "analytics@example.com",
            team = "@analytics",
            description = "User summary view",
            tags = mutableSetOf("tier::gold", "domain::analytics")
        )

        testDownstreamNode2 = LineageNodeEntity(
            name = "metrics.active_users_daily",
            type = LineageNodeType.METRIC,
            owner = "analytics@example.com",
            team = "@analytics",
            description = "Daily active users metric",
            tags = mutableSetOf("tier::gold", "domain::metrics")
        )

        // Edges
        testUpstreamEdge1 = LineageEdgeEntity(
            source = testUpstreamNode1.name,
            target = testRootNode.name,
            edgeType = LineageEdgeType.DIRECT
        )

        testUpstreamEdge2 = LineageEdgeEntity(
            source = testUpstreamNode2.name,
            target = testRootNode.name,
            edgeType = LineageEdgeType.DIRECT
        )

        testDownstreamEdge1 = LineageEdgeEntity(
            source = testRootNode.name,
            target = testDownstreamNode1.name,
            edgeType = LineageEdgeType.DIRECT
        )

        testDownstreamEdge2 = LineageEdgeEntity(
            source = testRootNode.name,
            target = testDownstreamNode2.name,
            edgeType = LineageEdgeType.DIRECT
        )
    }

    @Nested
    @DisplayName("getLineageGraph")
    inner class GetLineageGraph {

        @Test
        @DisplayName("should return upstream lineage graph when direction is UPSTREAM")
        fun `should return upstream lineage graph when direction is UPSTREAM`() {
            // Given
            val resourceName = testRootNode.name
            val upstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, 1)
            )
            val upstreamEdges = listOf(testUpstreamEdge1, testUpstreamEdge2)

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns upstreamNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode1.name) } returns listOf(testUpstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode2.name) } returns listOf(testUpstreamEdge2)

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.UPSTREAM,
                depth = -1
            )

            // Then
            assertThat(result.rootNode).isEqualTo(testRootNode)
            assertThat(result.nodes).hasSize(2)
            assertThat(result.edges).hasSize(2)
            assertThat(result.totalUpstream).isEqualTo(2)
            assertThat(result.totalDownstream).isEqualTo(0)

            verify(exactly = 1) { lineageNodeRepositoryJpa.findById(resourceName) }
            verify(exactly = 1) { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) }
        }

        @Test
        @DisplayName("should return downstream lineage graph when direction is DOWNSTREAM")
        fun `should return downstream lineage graph when direction is DOWNSTREAM`() {
            // Given
            val resourceName = testRootNode.name
            val downstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode2.name, 1)
            )
            val downstreamEdges = listOf(testDownstreamEdge1, testDownstreamEdge2)

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findDownstream(resourceName, -1) } returns downstreamNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testDownstreamNode1.name) } returns listOf(testDownstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testDownstreamNode2.name) } returns listOf(testDownstreamEdge2)

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.DOWNSTREAM,
                depth = -1
            )

            // Then
            assertThat(result.rootNode).isEqualTo(testRootNode)
            assertThat(result.nodes).hasSize(2)
            assertThat(result.edges).hasSize(2)
            assertThat(result.totalUpstream).isEqualTo(0)
            assertThat(result.totalDownstream).isEqualTo(2)

            verify(exactly = 1) { lineageNodeRepositoryJpa.findById(resourceName) }
            verify(exactly = 1) { lineageEdgeRepositoryDsl.findDownstream(resourceName, -1) }
        }

        @Test
        @DisplayName("should return bidirectional lineage graph when direction is BOTH")
        fun `should return bidirectional lineage graph when direction is BOTH`() {
            // Given
            val resourceName = testRootNode.name
            val allNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode2.name, 1)
            )
            val allEdges = listOf(testUpstreamEdge1, testUpstreamEdge2, testDownstreamEdge1, testDownstreamEdge2)

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findBothDirections(resourceName, -1) } returns allNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode1.name) } returns listOf(testUpstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode2.name) } returns listOf(testUpstreamEdge2)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testDownstreamNode1.name) } returns listOf(testDownstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testDownstreamNode2.name) } returns listOf(testDownstreamEdge2)

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.BOTH,
                depth = -1
            )

            // Then
            assertThat(result.rootNode).isEqualTo(testRootNode)
            assertThat(result.nodes).hasSize(4)
            assertThat(result.edges).hasSize(4)
            assertThat(result.totalUpstream).isEqualTo(2)
            assertThat(result.totalDownstream).isEqualTo(2)

            verify(exactly = 1) { lineageNodeRepositoryJpa.findById(resourceName) }
            verify(exactly = 1) { lineageEdgeRepositoryDsl.findBothDirections(resourceName, -1) }
        }

        @Test
        @DisplayName("should respect depth limit for upstream traversal")
        fun `should respect depth limit for upstream traversal`() {
            // Given
            val resourceName = testRootNode.name
            val depth = 2
            val upstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, 2)
            )

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, depth) } returns upstreamNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(any()) } returns emptyList()

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.UPSTREAM,
                depth = depth
            )

            // Then
            assertThat(result.nodes).hasSize(2)
            verify(exactly = 1) { lineageEdgeRepositoryDsl.findUpstream(resourceName, depth) }
        }

        @Test
        @DisplayName("should handle empty lineage graph")
        fun `should handle empty lineage graph`() {
            // Given
            val resourceName = testRootNode.name

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findBothDirections(resourceName, -1) } returns emptyList()

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.BOTH,
                depth = -1
            )

            // Then
            assertThat(result.rootNode).isEqualTo(testRootNode)
            assertThat(result.nodes).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.totalUpstream).isEqualTo(0)
            assertThat(result.totalDownstream).isEqualTo(0)
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when root node not found")
        fun `should throw ResourceNotFoundException when root node not found`() {
            // Given
            val resourceName = "nonexistent.resource"
            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.empty()

            // When & Then
            val exception = assertThrows<ResourceNotFoundException> {
                lineageService.getLineageGraph(resourceName)
            }

            assertThat(exception.message).contains("Lineage resource")
            assertThat(exception.message).contains(resourceName)
            verify(exactly = 1) { lineageNodeRepositoryJpa.findById(resourceName) }
        }

        @Test
        @DisplayName("should handle duplicate edges correctly")
        fun `should handle duplicate edges correctly`() {
            // Given
            val resourceName = testRootNode.name
            val nodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1)
            )
            // Simulate duplicate edges from different connected nodes
            val duplicateEdge = testUpstreamEdge1

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns nodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode1.name) } returns listOf(duplicateEdge, duplicateEdge)

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.UPSTREAM,
                depth = -1
            )

            // Then
            assertThat(result.edges).hasSize(1) // Should deduplicate
            assertThat(result.edges[0]).isEqualTo(duplicateEdge)
        }
    }

    @Nested
    @DisplayName("existsInLineage")
    inner class ExistsInLineage {

        @Test
        @DisplayName("should return true when resource exists in lineage graph")
        fun `should return true when resource exists in lineage graph`() {
            // Given
            val resourceName = testRootNode.name
            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns true

            // When
            val result = lineageService.existsInLineage(resourceName)

            // Then
            assertThat(result).isTrue()
            verify(exactly = 1) { lineageNodeRepositoryJpa.existsById(resourceName) }
        }

        @Test
        @DisplayName("should return false when resource does not exist in lineage graph")
        fun `should return false when resource does not exist in lineage graph`() {
            // Given
            val resourceName = "nonexistent.resource"
            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns false

            // When
            val result = lineageService.existsInLineage(resourceName)

            // Then
            assertThat(result).isFalse()
            verify(exactly = 1) { lineageNodeRepositoryJpa.existsById(resourceName) }
        }
    }

    @Nested
    @DisplayName("getLineageStatistics")
    inner class GetLineageStatistics {

        @Test
        @DisplayName("should return statistics when resource exists")
        fun `should return statistics when resource exists`() {
            // Given
            val resourceName = testRootNode.name
            val upstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, 1)
            )
            val downstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode2.name, 1)
            )

            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns true
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns upstreamNodes
            every { lineageEdgeRepositoryDsl.findDownstream(resourceName, -1) } returns downstreamNodes

            // When
            val result = lineageService.getLineageStatistics(resourceName)

            // Then
            assertThat(result["upstream_count"]).isEqualTo(2)
            assertThat(result["downstream_count"]).isEqualTo(2)
            assertThat(result["total_connected"]).isEqualTo(4)
            verify(exactly = 1) { lineageNodeRepositoryJpa.existsById(resourceName) }
        }

        @Test
        @DisplayName("should return empty map when resource does not exist")
        fun `should return empty map when resource does not exist`() {
            // Given
            val resourceName = "nonexistent.resource"
            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns false

            // When
            val result = lineageService.getLineageStatistics(resourceName)

            // Then
            assertThat(result).isEmpty()
            verify(exactly = 1) { lineageNodeRepositoryJpa.existsById(resourceName) }
        }

        @Test
        @DisplayName("should return zero counts when resource has no connections")
        fun `should return zero counts when resource has no connections`() {
            // Given
            val resourceName = testRootNode.name
            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns true
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns emptyList()
            every { lineageEdgeRepositoryDsl.findDownstream(resourceName, -1) } returns emptyList()

            // When
            val result = lineageService.getLineageStatistics(resourceName)

            // Then
            assertThat(result["upstream_count"]).isEqualTo(0)
            assertThat(result["downstream_count"]).isEqualTo(0)
            assertThat(result["total_connected"]).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle asymmetric lineage (only upstream or downstream)")
        fun `should handle asymmetric lineage (only upstream or downstream)`() {
            // Given
            val resourceName = testRootNode.name
            val upstreamNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1)
            )

            every { lineageNodeRepositoryJpa.existsById(resourceName) } returns true
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns upstreamNodes
            every { lineageEdgeRepositoryDsl.findDownstream(resourceName, -1) } returns emptyList()

            // When
            val result = lineageService.getLineageStatistics(resourceName)

            // Then
            assertThat(result["upstream_count"]).isEqualTo(1)
            assertThat(result["downstream_count"]).isEqualTo(0)
            assertThat(result["total_connected"]).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle circular references without infinite loops")
        fun `should handle circular references without infinite loops`() {
            // Given
            val resourceName = testRootNode.name
            // Simulate a circular reference in the graph data
            val circularNodes = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(resourceName, 2) // Circular back to root
            )

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns circularNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode1.name) } returns listOf(testUpstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(resourceName) } returns listOf(testUpstreamEdge1)

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.UPSTREAM,
                depth = -1
            )

            // Then
            assertThat(result.nodes).hasSize(2)
            assertThat(result.edges).hasSize(1) // Should deduplicate
        }

        @Test
        @DisplayName("should handle large depth values gracefully")
        fun `should handle large depth values gracefully`() {
            // Given
            val resourceName = testRootNode.name
            val largeDepth = 1000

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findBothDirections(resourceName, largeDepth) } returns emptyList()

            // When
            val result = lineageService.getLineageGraph(
                resourceName = resourceName,
                direction = LineageDirection.BOTH,
                depth = largeDepth
            )

            // Then
            assertThat(result.rootNode).isEqualTo(testRootNode)
            assertThat(result.nodes).isEmpty()
            verify(exactly = 1) { lineageEdgeRepositoryDsl.findBothDirections(resourceName, largeDepth) }
        }
    }
}