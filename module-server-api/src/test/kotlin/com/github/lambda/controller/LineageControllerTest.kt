package com.github.lambda.controller

import com.github.lambda.common.exception.InvalidParameterException
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.domain.model.lineage.LineageDirection
import com.github.lambda.domain.model.lineage.LineageEdgeEntity
import com.github.lambda.domain.model.lineage.LineageEdgeType
import com.github.lambda.domain.model.lineage.LineageGraphResult
import com.github.lambda.domain.model.lineage.LineageNodeEntity
import com.github.lambda.domain.model.lineage.LineageNodeType
import com.github.lambda.domain.repository.LineageEdgeRepositoryDsl
import com.github.lambda.domain.service.LineageService
import com.github.lambda.dto.lineage.LineageEdgeDto
import com.github.lambda.dto.lineage.LineageGraphDto
import com.github.lambda.dto.lineage.LineageNodeDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.LineageMapper
import com.github.lambda.security.SecurityConfig
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import com.ninjasquad.springmockk.MockkBean

/**
 * LineageController REST API Tests
 *
 * Spring Boot 4.x patterns:
 * - @WebMvcTest: Slice test for web layer only (faster than full integration test)
 * - @MockkBean: springmockk 5.0.1 (Spring Boot 4 compatible)
 * - @Import: Include SecurityConfig and GlobalExceptionHandler for proper security and exception handling
 */
@WebMvcTest(LineageController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    LineageControllerTest.ValidationConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
class LineageControllerTest {

    /**
     * Test configuration to enable method-level validation for @Min, @Max, @Size annotations
     * on controller method parameters. Required for @WebMvcTest since it doesn't auto-configure this.
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean(relaxed = true)
    private lateinit var lineageService: LineageService

    @MockkBean(relaxed = true)
    private lateinit var lineageMapper: LineageMapper

    // Test data
    private lateinit var testRootNode: LineageNodeEntity
    private lateinit var testUpstreamNode: LineageNodeEntity
    private lateinit var testDownstreamNode: LineageNodeEntity
    private lateinit var testGraphResult: LineageGraphResult

    // Response DTOs
    private lateinit var testRootNodeDto: LineageNodeDto
    private lateinit var testUpstreamNodeDto: LineageNodeDto
    private lateinit var testDownstreamNodeDto: LineageNodeDto
    private lateinit var testUpstreamEdgeDto: LineageEdgeDto
    private lateinit var testDownstreamEdgeDto: LineageEdgeDto
    private lateinit var testLineageGraphDto: LineageGraphDto

    @BeforeEach
    fun setUp() {
        // Domain entities
        testRootNode = LineageNodeEntity(
            name = "iceberg.analytics.users",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "User dimension table",
            tags = mutableSetOf("tier::critical", "domain::analytics")
        )

        testUpstreamNode = LineageNodeEntity(
            name = "bigquery.raw.user_events",
            type = LineageNodeType.TABLE,
            owner = "data-team@example.com",
            team = "@data-eng",
            description = "Raw user events",
            tags = mutableSetOf("tier::bronze", "domain::events")
        )

        testDownstreamNode = LineageNodeEntity(
            name = "iceberg.marts.user_summary",
            type = LineageNodeType.VIEW,
            owner = "analytics@example.com",
            team = "@analytics",
            description = "User summary view",
            tags = mutableSetOf("tier::gold", "domain::analytics")
        )

        val testUpstreamEdge = LineageEdgeEntity(
            source = testUpstreamNode.name,
            target = testRootNode.name,
            edgeType = LineageEdgeType.DIRECT
        )

        val testDownstreamEdge = LineageEdgeEntity(
            source = testRootNode.name,
            target = testDownstreamNode.name,
            edgeType = LineageEdgeType.DIRECT
        )

        val nodesWithDepth = listOf(
            LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode.name, 1),
            LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode.name, 1)
        )

        testGraphResult = LineageGraphResult.create(
            rootNode = testRootNode,
            nodes = nodesWithDepth,
            edges = listOf(testUpstreamEdge, testDownstreamEdge)
        )

        // Response DTOs
        testRootNodeDto = LineageNodeDto(
            name = testRootNode.name,
            type = testRootNode.type.name.lowercase(),
            owner = testRootNode.owner,
            team = testRootNode.team,
            description = testRootNode.description,
            tags = testRootNode.tags.toList(),
            depth = null
        )

        testUpstreamNodeDto = LineageNodeDto(
            name = testUpstreamNode.name,
            type = testUpstreamNode.type.name.lowercase(),
            owner = testUpstreamNode.owner,
            team = testUpstreamNode.team,
            description = testUpstreamNode.description,
            tags = testUpstreamNode.tags.toList(),
            depth = 1
        )

        testDownstreamNodeDto = LineageNodeDto(
            name = testDownstreamNode.name,
            type = testDownstreamNode.type.name.lowercase(),
            owner = testDownstreamNode.owner,
            team = testDownstreamNode.team,
            description = testDownstreamNode.description,
            tags = testDownstreamNode.tags.toList(),
            depth = 1
        )

        testUpstreamEdgeDto = LineageEdgeDto(
            source = testUpstreamNode.name,
            target = testRootNode.name,
            type = testUpstreamEdge.edgeType.name.lowercase()
        )

        testDownstreamEdgeDto = LineageEdgeDto(
            source = testRootNode.name,
            target = testDownstreamNode.name,
            type = testDownstreamEdge.edgeType.name.lowercase()
        )

        testLineageGraphDto = LineageGraphDto(
            root = testRootNodeDto,
            nodes = listOf(testUpstreamNodeDto, testDownstreamNodeDto),
            edges = listOf(testUpstreamEdgeDto, testDownstreamEdgeDto),
            totalUpstream = 1,
            totalDownstream = 1
        )
    }

    @Nested
    @DisplayName("GET /api/v1/lineage/{resourceName}")
    inner class GetLineageGraph {

        @Test
        @DisplayName("should return lineage graph with default parameters")
        fun `should return lineage graph with default parameters`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, -1)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.root.name").value(testRootNode.name))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.nodes.length()").value(2))
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.edges.length()").value(2))
                .andExpect(jsonPath("$.data.total_upstream").value(1))
                .andExpect(jsonPath("$.data.total_downstream").value(1))
                .andExpect(jsonPath("$.data.total_nodes").value(2))
                .andExpect(jsonPath("$.data.total_edges").value(2))

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, -1) }
            verify(exactly = 1) { lineageMapper.toGraphDto(testGraphResult) }
        }

        @Test
        @DisplayName("should return upstream lineage when direction is upstream")
        fun `should return upstream lineage when direction is upstream`() {
            // Given
            val resourceName = testRootNode.name
            val upstreamGraphDto = testLineageGraphDto.copy(
                nodes = listOf(testUpstreamNodeDto),
                edges = listOf(testUpstreamEdgeDto),
                totalUpstream = 1,
                totalDownstream = 0
            )

            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(any()) } returns upstreamGraphDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("direction", "upstream")
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.total_upstream").value(1))
                .andExpect(jsonPath("$.data.total_downstream").value(0))

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1) }
        }

        @Test
        @DisplayName("should return downstream lineage when direction is downstream")
        fun `should return downstream lineage when direction is downstream`() {
            // Given
            val resourceName = testRootNode.name
            val downstreamGraphDto = testLineageGraphDto.copy(
                nodes = listOf(testDownstreamNodeDto),
                edges = listOf(testDownstreamEdgeDto),
                totalUpstream = 0,
                totalDownstream = 1
            )

            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.DOWNSTREAM, -1)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(any()) } returns downstreamGraphDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("direction", "downstream")
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.total_upstream").value(0))
                .andExpect(jsonPath("$.data.total_downstream").value(1))

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.DOWNSTREAM, -1) }
        }

        @Test
        @DisplayName("should respect depth parameter")
        fun `should respect depth parameter`() {
            // Given
            val resourceName = testRootNode.name
            val depth = 3

            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, depth)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("depth", depth.toString())
                )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.root.name").value(testRootNode.name))

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, depth) }
        }

        @Test
        @DisplayName("should handle empty lineage graph")
        fun `should handle empty lineage graph`() {
            // Given
            val resourceName = testRootNode.name
            val emptyGraphResult = LineageGraphResult.create(
                rootNode = testRootNode,
                nodes = emptyList(),
                edges = emptyList()
            )
            val emptyGraphDto = LineageGraphDto(
                root = testRootNodeDto,
                nodes = emptyList(),
                edges = emptyList(),
                totalUpstream = 0,
                totalDownstream = 0
            )

            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, -1)
            } returns emptyGraphResult
            every { lineageMapper.toGraphDto(emptyGraphResult) } returns emptyGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.nodes.length()").value(0))
                .andExpected(jsonPath("$.data.edges.length()").value(0))
                .andExpected(jsonPath("$.data.total_upstream").value(0))
                .andExpected(jsonPath("$.data.total_downstream").value(0))
        }

        @Test
        @DisplayName("should return 404 when resource not found")
        fun `should return 404 when resource not found`() {
            // Given
            val resourceName = "nonexistent.resource"
            every {
                lineageService.getLineageGraph(resourceName, any(), any())
            } throws ResourceNotFoundException("Lineage resource", resourceName)

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpect(status().isNotFound)

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, any(), any()) }
        }

        @Test
        @DisplayName("should return 400 for invalid direction parameter")
        fun `should return 400 for invalid direction parameter`() {
            // Given
            val resourceName = testRootNode.name
            val invalidDirection = "invalid"

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("direction", invalidDirection)
                )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("LINEAGE_INVALID_PARAMETER"))
                .andExpected(jsonPath("$.error.details.direction").value(invalidDirection))
        }

        @Test
        @DisplayName("should return 400 for invalid depth parameter (negative)")
        fun `should return 400 for invalid depth parameter (negative)`() {
            // Given
            val resourceName = testRootNode.name
            val invalidDepth = -5

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("depth", invalidDepth.toString())
                )
                .andExpected(status().isBadRequest)
        }

        @Test
        @DisplayName("should return 400 for depth parameter exceeding maximum")
        fun `should return 400 for depth parameter exceeding maximum`() {
            // Given
            val resourceName = testRootNode.name
            val invalidDepth = 15 // Max is 10

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("depth", invalidDepth.toString())
                )
                .andExpected(status().isBadRequest)
        }

        @Test
        @DisplayName("should handle case insensitive direction parameter")
        fun `should handle case insensitive direction parameter`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("direction", "UPSTREAM") // Test uppercase
                )
                .andExpect(status().isOk)

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1) }
        }

        @Test
        @DisplayName("should return 500 for internal service errors")
        fun `should return 500 for internal service errors`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, any(), any())
            } throws RuntimeException("Database connection failed")

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpect(status().isInternalServerError)
                .andExpect(jsonPath("$.success").value(false))
                .andExpected(jsonPath("$.error.code").value("LINEAGE_INTERNAL_ERROR"))
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    inner class ParameterValidation {

        @Test
        @DisplayName("should accept valid depth values")
        fun `should accept valid depth values`() {
            // Given
            val resourceName = testRootNode.name
            val validDepths = listOf(-1, 0, 1, 5, 10)

            every {
                lineageService.getLineageGraph(eq(resourceName), any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            validDepths.forEach { depth ->
                mockMvc
                    .perform(
                        get("/api/v1/lineage/$resourceName")
                            .param("depth", depth.toString())
                    )
                    .andExpected(status().isOk)
            }
        }

        @Test
        @DisplayName("should accept valid direction values")
        fun `should accept valid direction values`() {
            // Given
            val resourceName = testRootNode.name
            val validDirections = mapOf(
                "upstream" to LineageDirection.UPSTREAM,
                "downstream" to LineageDirection.DOWNSTREAM,
                "both" to LineageDirection.BOTH
            )

            every {
                lineageService.getLineageGraph(eq(resourceName), any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            validDirections.forEach { (directionParam, expectedDirection) ->
                mockMvc
                    .perform(
                        get("/api/v1/lineage/$resourceName")
                            .param("direction", directionParam)
                    )
                    .andExpected(status().isOk)

                verify { lineageService.getLineageGraph(resourceName, expectedDirection, -1) }
            }
        }

        @Test
        @DisplayName("should handle boundary depth values")
        fun `should handle boundary depth values`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(eq(resourceName), any(), eq(0))
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then - Test depth = 0 (should only return the resource itself)
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("depth", "0")
                )
                .andExpected(status().isOk)

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, 0) }
        }

        @Test
        @DisplayName("should handle special characters in resource name")
        fun `should handle special characters in resource name`() {
            // Given
            val resourceNameWithSpecialChars = "project-123.dataset_name.table-with-dashes"
            every {
                lineageService.getLineageGraph(resourceNameWithSpecialChars, any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceNameWithSpecialChars"))
                .andExpected(status().isOk)
                .andExpected(jsonPath("$.data.root.name").value(testRootNode.name))
        }
    }

    @Nested
    @DisplayName("Response Format")
    inner class ResponseFormat {

        @Test
        @DisplayName("should return properly structured success response")
        fun `should return properly structured success response`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpected(status().isOk)
                .andExpected(jsonPath("$.success").value(true))
                .andExpected(jsonPath("$.message").value("Lineage graph retrieved successfully"))
                .andExpected(jsonPath("$.data").exists())
                .andExpected(jsonPath("$.data.root").exists())
                .andExpected(jsonPath("$.data.nodes").isArray())
                .andExpected(jsonPath("$.data.edges").isArray())
                .andExpected(jsonPath("$.data.total_upstream").isNumber())
                .andExpected(jsonPath("$.data.total_downstream").isNumber())
                .andExpected(jsonPath("$.data.total_nodes").isNumber())
                .andExpected(jsonPath("$.data.total_edges").isNumber())
                .andExpected(jsonPath("$.data.total_related").isNumber())
        }

        @Test
        @DisplayName("should include all node properties in response")
        fun `should include all node properties in response`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpected(status().isOk)
                .andExpected(jsonPath("$.data.root.name").value(testRootNode.name))
                .andExpected(jsonPath("$.data.root.type").value("table"))
                .andExpected(jsonPath("$.data.root.owner").value(testRootNode.owner))
                .andExpected(jsonPath("$.data.root.team").value(testRootNode.team))
                .andExpected(jsonPath("$.data.root.description").value(testRootNode.description))
                .andExpected(jsonPath("$.data.root.tags").isArray())
        }

        @Test
        @DisplayName("should include all edge properties in response")
        fun `should include all edge properties in response`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, any(), any())
            } returns testGraphResult
            every { lineageMapper.toGraphDto(testGraphResult) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/lineage/$resourceName"))
                .andExpected(status().isOk)
                .andExpected(jsonPath("$.data.edges[0].source").exists())
                .andExpected(jsonPath("$.data.edges[0].target").exists())
                .andExpected(jsonPath("$.data.edges[0].type").exists())
        }
    }
}