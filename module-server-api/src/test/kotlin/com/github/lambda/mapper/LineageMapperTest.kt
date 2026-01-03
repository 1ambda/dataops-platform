package com.github.lambda.mapper

import com.github.lambda.domain.model.lineage.LineageEdgeEntity
import com.github.lambda.domain.model.lineage.LineageEdgeType
import com.github.lambda.domain.model.lineage.LineageGraphResult
import com.github.lambda.domain.model.lineage.LineageNodeEntity
import com.github.lambda.domain.model.lineage.LineageNodeType
import com.github.lambda.domain.repository.LineageEdgeRepositoryDsl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

/**
 * LineageMapper Unit Tests
 *
 * Tests all mapping functions between domain models and DTOs.
 */
@DisplayName("LineageMapper Unit Tests")
class LineageMapperTest {

    private val lineageMapper = LineageMapper()

    private lateinit var testNodeEntity: LineageNodeEntity
    private lateinit var testEdgeEntity: LineageEdgeEntity
    private lateinit var testGraphResult: LineageGraphResult
    private lateinit var testNodeWithDepth: LineageEdgeRepositoryDsl.NodeWithDepth

    @BeforeEach
    fun setUp() {
        testNodeEntity = LineageNodeEntity(
            name = "iceberg.analytics.users",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "User dimension table with profile information",
            tags = mutableSetOf("tier::critical", "domain::analytics", "pii::contains")
        ).apply {
            createdAt = LocalDateTime.of(2024, 1, 1, 10, 0, 0)
            updatedAt = LocalDateTime.of(2024, 1, 15, 14, 30, 0)
        }

        testEdgeEntity = LineageEdgeEntity(
            source = "bigquery.raw.user_events",
            target = "iceberg.analytics.users",
            edgeType = LineageEdgeType.DIRECT
        )

        testNodeWithDepth = LineageEdgeRepositoryDsl.NodeWithDepth(
            name = "iceberg.analytics.users",
            depth = 2
        )

        val nodesWithDepth = listOf(
            LineageEdgeRepositoryDsl.NodeWithDepth("upstream.table1", 1),
            LineageEdgeRepositoryDsl.NodeWithDepth("downstream.view1", 1)
        )

        testGraphResult = LineageGraphResult.create(
            rootNode = testNodeEntity,
            nodes = nodesWithDepth,
            edges = listOf(testEdgeEntity)
        )
    }

    @Nested
    @DisplayName("toNodeDto - Entity conversion")
    inner class ToNodeDtoEntity {

        @Test
        @DisplayName("should convert LineageNodeEntity to LineageNodeDto correctly")
        fun `should convert LineageNodeEntity to LineageNodeDto correctly`() {
            // When
            val result = lineageMapper.toNodeDto(testNodeEntity)

            // Then
            assertThat(result.name).isEqualTo("iceberg.analytics.users")
            assertThat(result.type).isEqualTo("TABLE")
            assertThat(result.owner).isEqualTo("data-team@example.com")
            assertThat(result.team).isEqualTo("@data-eng")
            assertThat(result.description).isEqualTo("User dimension table with profile information")
            assertThat(result.tags).containsExactly("domain::analytics", "pii::contains", "tier::critical") // Sorted
            assertThat(result.depth).isNull() // No depth for direct entity conversion
        }

        @Test
        @DisplayName("should handle null optional fields")
        fun `should handle null optional fields`() {
            // Given
            val nodeWithNulls = LineageNodeEntity(
                name = "minimal.table",
                type = LineageNodeType.VIEW,
                owner = null,
                team = null,
                description = null,
                tags = mutableSetOf()
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithNulls)

            // Then
            assertThat(result.name).isEqualTo("minimal.table")
            assertThat(result.type).isEqualTo("VIEW")
            assertThat(result.owner).isEqualTo("unknown") // Default for null owner
            assertThat(result.team).isNull()
            assertThat(result.description).isNull()
            assertThat(result.tags).isEmpty()
            assertThat(result.depth).isNull()
        }

        @Test
        @DisplayName("should sort tags alphabetically")
        fun `should sort tags alphabetically`() {
            // Given
            val nodeWithUnsortedTags = LineageNodeEntity(
                name = "test.table",
                type = LineageNodeType.TABLE,
                owner = "test@example.com",
                tags = mutableSetOf("zzz::last", "aaa::first", "mmm::middle")
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithUnsortedTags)

            // Then
            assertThat(result.tags).containsExactly("aaa::first", "mmm::middle", "zzz::last")
        }

        @Test
        @DisplayName("should handle all node types correctly")
        fun `should handle all node types correctly`() {
            // Given & When & Then
            LineageNodeType.values().forEach { nodeType ->
                val node = LineageNodeEntity(
                    name = "test.${nodeType.name.lowercase()}",
                    type = nodeType,
                    owner = "test@example.com"
                )

                val result = lineageMapper.toNodeDto(node)
                assertThat(result.type).isEqualTo(nodeType.name)
            }
        }
    }

    @Nested
    @DisplayName("toNodeDto - NodeWithDepth conversion")
    inner class ToNodeDtoWithDepth {

        @Test
        @DisplayName("should convert NodeWithDepth to LineageNodeDto with depth information")
        fun `should convert NodeWithDepth to LineageNodeDto with depth information`() {
            // When
            val result = lineageMapper.toNodeDto(testNodeWithDepth)

            // Then
            assertThat(result.name).isEqualTo("iceberg.analytics.users")
            assertThat(result.depth).isEqualTo(2)
            assertThat(result.type).isEqualTo("UNKNOWN") // Placeholder implementation
            assertThat(result.owner).isEqualTo("system") // Placeholder implementation
            assertThat(result.team).isNull()
            assertThat(result.description).isNull()
            assertThat(result.tags).isEmpty()
        }

        @Test
        @DisplayName("should handle zero depth")
        fun `should handle zero depth`() {
            // Given
            val nodeWithZeroDepth = LineageEdgeRepositoryDsl.NodeWithDepth("root.node", 0)

            // When
            val result = lineageMapper.toNodeDto(nodeWithZeroDepth)

            // Then
            assertThat(result.name).isEqualTo("root.node")
            assertThat(result.depth).isEqualTo(0)
        }

        @Test
        @DisplayName("should handle deep depth values")
        fun `should handle deep depth values`() {
            // Given
            val nodeWithDeepDepth = LineageEdgeRepositoryDsl.NodeWithDepth("deep.node", 10)

            // When
            val result = lineageMapper.toNodeDto(nodeWithDeepDepth)

            // Then
            assertThat(result.name).isEqualTo("deep.node")
            assertThat(result.depth).isEqualTo(10)
        }
    }

    @Nested
    @DisplayName("toEdgeDto")
    inner class ToEdgeDto {

        @Test
        @DisplayName("should convert LineageEdgeEntity to LineageEdgeDto correctly")
        fun `should convert LineageEdgeEntity to LineageEdgeDto correctly`() {
            // When
            val result = lineageMapper.toEdgeDto(testEdgeEntity)

            // Then
            assertThat(result.source).isEqualTo("bigquery.raw.user_events")
            assertThat(result.target).isEqualTo("iceberg.analytics.users")
            assertThat(result.edgeType).isEqualTo("DIRECT")
        }

        @Test
        @DisplayName("should handle all edge types correctly")
        fun `should handle all edge types correctly`() {
            // Given & When & Then
            LineageEdgeType.values().forEach { edgeType ->
                val edge = LineageEdgeEntity(
                    source = "source.table",
                    target = "target.table",
                    edgeType = edgeType
                )

                val result = lineageMapper.toEdgeDto(edge)
                assertThat(result.edgeType).isEqualTo(edgeType.name)
            }
        }

        @Test
        @DisplayName("should handle complex resource names")
        fun `should handle complex resource names`() {
            // Given
            val edgeWithComplexNames = LineageEdgeEntity(
                source = "project-123.dataset_name.source-table-with-dashes",
                target = "another-project.analytics_dataset.target_view",
                edgeType = LineageEdgeType.DERIVED
            )

            // When
            val result = lineageMapper.toEdgeDto(edgeWithComplexNames)

            // Then
            assertThat(result.source).isEqualTo("project-123.dataset_name.source-table-with-dashes")
            assertThat(result.target).isEqualTo("another-project.analytics_dataset.target_view")
            assertThat(result.edgeType).isEqualTo("DERIVED")
        }
    }

    @Nested
    @DisplayName("toGraphDto")
    inner class ToGraphDto {

        @Test
        @DisplayName("should convert LineageGraphResult to LineageGraphDto correctly")
        fun `should convert LineageGraphResult to LineageGraphDto correctly`() {
            // When
            val result = lineageMapper.toGraphDto(testGraphResult)

            // Then
            assertThat(result.root.name).isEqualTo(testNodeEntity.name)
            assertThat(result.nodes).hasSize(2)
            assertThat(result.edges).hasSize(1)
            assertThat(result.totalUpstream).isEqualTo(testGraphResult.totalUpstream)
            assertThat(result.totalDownstream).isEqualTo(testGraphResult.totalDownstream)
            assertThat(result.totalNodes).isEqualTo(2)
            assertThat(result.totalEdges).isEqualTo(1)
            assertThat(result.totalRelated).isEqualTo(testGraphResult.totalUpstream + testGraphResult.totalDownstream)
        }

        @Test
        @DisplayName("should handle empty graph correctly")
        fun `should handle empty graph correctly`() {
            // Given
            val emptyGraphResult = LineageGraphResult.create(
                rootNode = testNodeEntity,
                nodes = emptyList(),
                edges = emptyList()
            )

            // When
            val result = lineageMapper.toGraphDto(emptyGraphResult)

            // Then
            assertThat(result.root.name).isEqualTo(testNodeEntity.name)
            assertThat(result.nodes).isEmpty()
            assertThat(result.edges).isEmpty()
            assertThat(result.totalUpstream).isEqualTo(0)
            assertThat(result.totalDownstream).isEqualTo(0)
            assertThat(result.totalNodes).isEqualTo(0)
            assertThat(result.totalEdges).isEqualTo(0)
            assertThat(result.totalRelated).isEqualTo(0)
        }

        @Test
        @DisplayName("should preserve depth information in nodes")
        fun `should preserve depth information in nodes`() {
            // Given
            val nodesWithDepth = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth("upstream.table", 1),
                LineageEdgeRepositoryDsl.NodeWithDepth("far.upstream", 3)
            )
            val graphWithDepth = LineageGraphResult.create(
                rootNode = testNodeEntity,
                nodes = nodesWithDepth,
                edges = emptyList()
            )

            // When
            val result = lineageMapper.toGraphDto(graphWithDepth)

            // Then
            assertThat(result.nodes).hasSize(2)
            assertThat(result.nodes[0].depth).isEqualTo(1)
            assertThat(result.nodes[1].depth).isEqualTo(3)
        }

        @Test
        @DisplayName("should handle large graph with many nodes and edges")
        fun `should handle large graph with many nodes and edges`() {
            // Given
            val manyNodes = (1..10).map { 
                LineageEdgeRepositoryDsl.NodeWithDepth("node$it", it % 3) 
            }
            val manyEdges = (1..5).map { 
                LineageEdgeEntity(
                    source = "source$it",
                    target = "target$it",
                    edgeType = LineageEdgeType.DIRECT
                )
            }
            val largeGraph = LineageGraphResult.create(
                rootNode = testNodeEntity,
                nodes = manyNodes,
                edges = manyEdges
            )

            // When
            val result = lineageMapper.toGraphDto(largeGraph)

            // Then
            assertThat(result.nodes).hasSize(10)
            assertThat(result.edges).hasSize(5)
            assertThat(result.totalNodes).isEqualTo(10)
            assertThat(result.totalEdges).isEqualTo(5)
        }
    }

    @Nested
    @DisplayName("List conversion methods")
    inner class ListConversions {

        @Test
        @DisplayName("toNodeDtoList should convert list of entities correctly")
        fun `toNodeDtoList should convert list of entities correctly`() {
            // Given
            val entities = listOf(
                testNodeEntity,
                LineageNodeEntity(
                    name = "another.table",
                    type = LineageNodeType.VIEW,
                    owner = "other@example.com"
                )
            )

            // When
            val result = lineageMapper.toNodeDtoList(entities)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo(testNodeEntity.name)
            assertThat(result[1].name).isEqualTo("another.table")
            assertThat(result[1].type).isEqualTo("VIEW")
        }

        @Test
        @DisplayName("toNodeDtoList should handle empty list")
        fun `toNodeDtoList should handle empty list`() {
            // When
            val result = lineageMapper.toNodeDtoList(emptyList())

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("toNodeDtoListWithDepth should convert list with depth correctly")
        fun `toNodeDtoListWithDepth should convert list with depth correctly`() {
            // Given
            val nodesWithDepth = listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth("node1", 1),
                LineageEdgeRepositoryDsl.NodeWithDepth("node2", 2)
            )

            // When
            val result = lineageMapper.toNodeDtoListWithDepth(nodesWithDepth)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].name).isEqualTo("node1")
            assertThat(result[0].depth).isEqualTo(1)
            assertThat(result[1].name).isEqualTo("node2")
            assertThat(result[1].depth).isEqualTo(2)
        }

        @Test
        @DisplayName("toNodeDtoListWithDepth should handle empty list")
        fun `toNodeDtoListWithDepth should handle empty list`() {
            // When
            val result = lineageMapper.toNodeDtoListWithDepth(emptyList())

            // Then
            assertThat(result).isEmpty()
        }

        @Test
        @DisplayName("toEdgeDtoList should convert list of edges correctly")
        fun `toEdgeDtoList should convert list of edges correctly`() {
            // Given
            val edges = listOf(
                testEdgeEntity,
                LineageEdgeEntity(
                    source = "source2",
                    target = "target2",
                    edgeType = LineageEdgeType.DERIVED
                )
            )

            // When
            val result = lineageMapper.toEdgeDtoList(edges)

            // Then
            assertThat(result).hasSize(2)
            assertThat(result[0].source).isEqualTo(testEdgeEntity.source)
            assertThat(result[0].target).isEqualTo(testEdgeEntity.target)
            assertThat(result[0].edgeType).isEqualTo("DIRECT")
            assertThat(result[1].edgeType).isEqualTo("DERIVED")
        }

        @Test
        @DisplayName("toEdgeDtoList should handle empty list")
        fun `toEdgeDtoList should handle empty list`() {
            // When
            val result = lineageMapper.toEdgeDtoList(emptyList())

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("Edge Cases and Data Integrity")
    inner class EdgeCasesAndDataIntegrity {

        @Test
        @DisplayName("should handle very long resource names")
        fun `should handle very long resource names`() {
            // Given
            val longName = "very.long.project.name.with.many.segments.and.descriptive.dataset.name.final.table"
            val nodeWithLongName = LineageNodeEntity(
                name = longName,
                type = LineageNodeType.TABLE,
                owner = "test@example.com"
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithLongName)

            // Then
            assertThat(result.name).isEqualTo(longName)
            assertThat(result.type).isEqualTo("TABLE")
        }

        @Test
        @DisplayName("should handle special characters in resource names")
        fun `should handle special characters in resource names`() {
            // Given
            val nameWithSpecialChars = "project-123.dataset_name.table-with-dashes_and_underscores"
            val nodeWithSpecialChars = LineageNodeEntity(
                name = nameWithSpecialChars,
                type = LineageNodeType.TABLE,
                owner = "test@example.com"
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithSpecialChars)

            // Then
            assertThat(result.name).isEqualTo(nameWithSpecialChars)
        }

        @Test
        @DisplayName("should handle empty tags set")
        fun `should handle empty tags set`() {
            // Given
            val nodeWithEmptyTags = LineageNodeEntity(
                name = "test.table",
                type = LineageNodeType.TABLE,
                owner = "test@example.com",
                tags = mutableSetOf()
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithEmptyTags)

            // Then
            assertThat(result.tags).isEmpty()
        }

        @Test
        @DisplayName("should handle very long descriptions")
        fun `should handle very long descriptions`() {
            // Given
            val longDescription = "This is a very long description that contains multiple sentences and provides detailed information about the table structure, its usage patterns, data lineage, quality metrics, and business context. It may span several lines and include technical details about transformations, data sources, update frequencies, and ownership responsibilities."
            val nodeWithLongDescription = LineageNodeEntity(
                name = "test.table",
                type = LineageNodeType.TABLE,
                owner = "test@example.com",
                description = longDescription
            )

            // When
            val result = lineageMapper.toNodeDto(nodeWithLongDescription)

            // Then
            assertThat(result.description).isEqualTo(longDescription)
        }

        @Test
        @DisplayName("should preserve case sensitivity in names and values")
        fun `should preserve case sensitivity in names and values`() {
            // Given
            val nodeCaseSensitive = LineageNodeEntity(
                name = "Project.DataSet.TableName",
                type = LineageNodeType.TABLE,
                owner = "User@Example.Com",
                team = "@Data-Engineering-Team"
            )

            // When
            val result = lineageMapper.toNodeDto(nodeCaseSensitive)

            // Then
            assertThat(result.name).isEqualTo("Project.DataSet.TableName")
            assertThat(result.owner).isEqualTo("User@Example.Com")
            assertThat(result.team).isEqualTo("@Data-Engineering-Team")
        }
    }
}