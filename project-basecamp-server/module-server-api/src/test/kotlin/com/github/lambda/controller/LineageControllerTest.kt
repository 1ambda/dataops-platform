package com.github.lambda.controller

import com.github.lambda.common.enums.LineageDirection
import com.github.lambda.common.enums.LineageEdgeType
import com.github.lambda.common.enums.LineageNodeType
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.domain.entity.lineage.LineageEdgeEntity
import com.github.lambda.domain.entity.lineage.LineageNodeEntity
import com.github.lambda.domain.model.lineage.LineageGraphResult
import com.github.lambda.domain.repository.lineage.LineageEdgeRepositoryDsl
import com.github.lambda.domain.service.LineageService
import com.github.lambda.dto.lineage.LineageEdgeDto
import com.github.lambda.dto.lineage.LineageGraphDto
import com.github.lambda.dto.lineage.LineageNodeDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.LineageMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
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
    private lateinit var testGraphResult: LineageGraphResult
    private lateinit var testLineageGraphDto: LineageGraphDto

    @BeforeEach
    fun setUp() {
        // Domain entities
        testRootNode =
            LineageNodeEntity(
                name = "iceberg.analytics.users",
                type = LineageNodeType.TABLE,
                owner = "data-team@example.com",
                team = "@data-eng",
                description = "User dimension table",
                tags = mutableSetOf("tier::critical", "domain::analytics"),
            )

        val testUpstreamNode =
            LineageNodeEntity(
                name = "bigquery.raw.user_events",
                type = LineageNodeType.TABLE,
                owner = "data-team@example.com",
                team = "@data-eng",
            )

        val testDownstreamNode =
            LineageNodeEntity(
                name = "iceberg.marts.user_summary",
                type = LineageNodeType.VIEW,
                owner = "analytics@example.com",
                team = "@analytics",
            )

        val testUpstreamEdge =
            LineageEdgeEntity(
                source = testUpstreamNode.name,
                target = testRootNode.name,
                edgeType = LineageEdgeType.DIRECT,
            )

        val testDownstreamEdge =
            LineageEdgeEntity(
                source = testRootNode.name,
                target = testDownstreamNode.name,
                edgeType = LineageEdgeType.DIRECT,
            )

        val nodesWithDepth =
            listOf(
                LineageEdgeRepositoryDsl.NodeWithDepth(testUpstreamNode.name, 1),
                LineageEdgeRepositoryDsl.NodeWithDepth(testDownstreamNode.name, 1),
            )

        testGraphResult =
            LineageGraphResult.create(
                rootNode = testRootNode,
                nodes = nodesWithDepth,
                edges = listOf(testUpstreamEdge, testDownstreamEdge),
            )

        // Response DTOs
        val testRootNodeDto =
            LineageNodeDto(
                name = testRootNode.name,
                type = testRootNode.type.name,
                owner = testRootNode.owner ?: "unknown",
                team = testRootNode.team,
                description = testRootNode.description,
                tags = testRootNode.tags.toList(),
                depth = null,
            )

        val testUpstreamNodeDto =
            LineageNodeDto(
                name = testUpstreamNode.name,
                type = testUpstreamNode.type.name,
                owner = testUpstreamNode.owner ?: "unknown",
                team = testUpstreamNode.team,
                description = testUpstreamNode.description,
                tags = testUpstreamNode.tags.toList(),
                depth = 1,
            )

        val testDownstreamNodeDto =
            LineageNodeDto(
                name = testDownstreamNode.name,
                type = testDownstreamNode.type.name,
                owner = testDownstreamNode.owner ?: "unknown",
                team = testDownstreamNode.team,
                description = testDownstreamNode.description,
                tags = testDownstreamNode.tags.toList(),
                depth = 1,
            )

        val testUpstreamEdgeDto =
            LineageEdgeDto(
                source = testUpstreamNode.name,
                target = testRootNode.name,
                edgeType = testUpstreamEdge.edgeType.name,
            )

        val testDownstreamEdgeDto =
            LineageEdgeDto(
                source = testRootNode.name,
                target = testDownstreamNode.name,
                edgeType = testDownstreamEdge.edgeType.name,
            )

        testLineageGraphDto =
            LineageGraphDto(
                root = testRootNodeDto,
                nodes = listOf(testUpstreamNodeDto, testDownstreamNodeDto),
                edges = listOf(testUpstreamEdgeDto, testDownstreamEdgeDto),
                totalUpstream = 1,
                totalDownstream = 1,
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

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, -1) }
            verify(exactly = 1) { lineageMapper.toGraphDto(testGraphResult) }
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
        @DisplayName("should return upstream lineage when direction is upstream")
        fun `should return upstream lineage when direction is upstream`() {
            // Given
            val resourceName = testRootNode.name
            every {
                lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1)
            } returns testGraphResult
            every { lineageMapper.toGraphDto(any()) } returns testLineageGraphDto

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/lineage/$resourceName")
                        .param("direction", "upstream"),
                ).andExpect(status().isOk)

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.UPSTREAM, -1) }
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
                        .param("depth", depth.toString()),
                ).andExpect(status().isOk)
                .andExpect(jsonPath("$.data.root.name").value(testRootNode.name))

            verify(exactly = 1) { lineageService.getLineageGraph(resourceName, LineageDirection.BOTH, depth) }
        }
    }

    @Nested
    @DisplayName("Parameter Validation")
    inner class ParameterValidation {
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
                        .param("direction", invalidDirection),
                ).andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        @DisplayName("should accept valid direction values")
        fun `should accept valid direction values`() {
            // Given
            val resourceName = testRootNode.name
            val validDirections =
                mapOf(
                    "upstream" to LineageDirection.UPSTREAM,
                    "downstream" to LineageDirection.DOWNSTREAM,
                    "both" to LineageDirection.BOTH,
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
                            .param("direction", directionParam),
                    ).andExpect(status().isOk)

                verify { lineageService.getLineageGraph(resourceName, expectedDirection, -1) }
            }
        }

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
                            .param("depth", depth.toString()),
                    ).andExpect(status().isOk)
            }
        }
    }
}
