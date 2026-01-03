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
        testRootNode =
            LineageNodeEntity(
                name = "iceberg.analytics.users",
                type = LineageNodeType.TABLE,
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User dimension table",
                tags = mutableSetOf("tier::critical", "domain::analytics"),
            )

        // Upstream nodes (dependencies)
        testUpstreamNode1 =
            LineageNodeEntity(
                name = "bigquery.raw.user_events",
                type = LineageNodeType.TABLE,
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "Raw user events",
                tags = mutableSetOf("tier::bronze", "domain::events"),
            )

        testUpstreamNode2 =
            LineageNodeEntity(
                name = "bigquery.raw.user_profiles",
                type = LineageNodeType.TABLE,
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User profile data",
                tags = mutableSetOf("tier::bronze", "domain::users"),
            )

        // Downstream nodes (dependents)
        testDownstreamNode1 =
            LineageNodeEntity(
                name = "iceberg.marts.user_summary",
                type = LineageNodeType.VIEW,
                owner = "analytics@example.com",
                team = "@analytics",
                description = "User summary view",
                tags = mutableSetOf("tier::gold", "domain::analytics"),
            )

        testDownstreamNode2 =
            LineageNodeEntity(
                name = "metrics.active_users_daily",
                type = LineageNodeType.METRIC,
                owner = "analytics@example.com",
                team = "@analytics",
                description = "Daily active users metric",
                tags = mutableSetOf("tier::gold", "domain::metrics"),
            )

        // Edges
        testUpstreamEdge1 =
            LineageEdgeEntity(
                source = testUpstreamNode1.name,
                target = testRootNode.name,
                edgeType = LineageEdgeType.DIRECT,
            )

        testUpstreamEdge2 =
            LineageEdgeEntity(
                source = testUpstreamNode2.name,
                target = testRootNode.name,
                edgeType = LineageEdgeType.DIRECT,
            )

        testDownstreamEdge1 =
            LineageEdgeEntity(
                source = testRootNode.name,
                target = testDownstreamNode1.name,
                edgeType = LineageEdgeType.DIRECT,
            )

        testDownstreamEdge2 =
            LineageEdgeEntity(
                source = testRootNode.name,
                target = testDownstreamNode2.name,
                edgeType = LineageEdgeType.DIRECT,
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
            val upstreamNodes =
                listOf(
                    LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, -1),
                    LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, -1),
                )

            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.of(testRootNode)
            every { lineageEdgeRepositoryDsl.findUpstream(resourceName, -1) } returns upstreamNodes
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode1.name) } returns
                listOf(testUpstreamEdge1)
            every { lineageEdgeRepositoryDsl.findConnectedEdges(testUpstreamNode2.name) } returns
                listOf(testUpstreamEdge2)

            // When
            val result =
                lineageService.getLineageGraph(
                    resourceName = resourceName,
                    direction = LineageDirection.UPSTREAM,
                    depth = -1,
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
        @DisplayName("should throw ResourceNotFoundException when root node not found")
        fun `should throw ResourceNotFoundException when root node not found`() {
            // Given
            val resourceName = "nonexistent.resource"
            every { lineageNodeRepositoryJpa.findById(resourceName) } returns Optional.empty()

            // When & Then
            val exception =
                assertThrows<ResourceNotFoundException> {
                    lineageService.getLineageGraph(resourceName)
                }

            assertThat(exception.message).contains("Lineage resource")
            assertThat(exception.message).contains(resourceName)
            verify(exactly = 1) { lineageNodeRepositoryJpa.findById(resourceName) }
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
            val upstreamNodes =
                listOf(
                    LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode1.name, -1),
                    LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode2.name, -1),
                )
            val downstreamNodes =
                listOf(
                    LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode1.name, 1),
                    LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode2.name, 1),
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
    }
}
