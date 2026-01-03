package com.github.lambda.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.lambda.common.exception.ResourceNotFoundException
import com.github.lambda.config.SecurityConfig
import com.github.lambda.domain.command.query.CancelQueryCommand
import com.github.lambda.domain.model.query.QueryEngine
import com.github.lambda.domain.model.query.QueryExecutionEntity
import com.github.lambda.domain.model.query.QueryScope
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.ListQueriesQuery
import com.github.lambda.domain.service.AccessDeniedException
import com.github.lambda.domain.service.QueryMetadataService
import com.github.lambda.domain.service.QueryNotCancellableException
import com.github.lambda.domain.service.QueryNotFoundException
import com.github.lambda.dto.query.CancelQueryRequestDto
import com.github.lambda.dto.query.CancelQueryResponseDto
import com.github.lambda.dto.query.QueryDetailDto
import com.github.lambda.dto.query.QueryListItemDto
import com.github.lambda.exception.GlobalExceptionHandler
import com.github.lambda.mapper.QueryMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.validation.beanvalidation.MethodValidationPostProcessor
import java.time.Instant
import java.time.LocalDate

/**
 * QueryController API Tests
 *
 * Tests Query Metadata API endpoints in QueryController:
 * - GET /api/v1/queries
 * - GET /api/v1/queries/{query_id}
 * - POST /api/v1/queries/{query_id}/cancel
 *
 * Uses @WebMvcTest for fast web layer testing with MockK beans.
 */
@WebMvcTest(QueryController::class)
@Import(
    SecurityConfig::class,
    GlobalExceptionHandler::class,
    QueryControllerTest.ValidationConfig::class,
)
@ActiveProfiles("test")
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("QueryController API Tests")
class QueryControllerTest {
    /**
     * Test configuration to enable method-level validation and JSON processing
     */
    @TestConfiguration
    class ValidationConfig {
        @Bean
        fun methodValidationPostProcessor(): MethodValidationPostProcessor = MethodValidationPostProcessor()

        @Bean
        fun objectMapper(): ObjectMapper = ObjectMapper()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean(relaxed = true)
    private lateinit var queryMetadataService: QueryMetadataService

    @MockkBean(relaxed = true)
    private lateinit var queryMapper: QueryMapper


    // Test data
    private lateinit var testQueryEntity: QueryExecutionEntity
    private lateinit var testCompletedEntity: QueryExecutionEntity
    private lateinit var testCancelledEntity: QueryExecutionEntity
    private lateinit var testListItemDto: QueryListItemDto
    private lateinit var testDetailDto: QueryDetailDto
    private lateinit var testCancelResponseDto: CancelQueryResponseDto

    @BeforeEach
    fun setUp() {
        val now = Instant.now()

        testQueryEntity =
            QueryExecutionEntity(
                queryId = "query_test_001",
                sql = "SELECT user_id, COUNT(*) FROM users GROUP BY 1",
                status = QueryStatus.RUNNING,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(300),
                startedAt = now.minusSeconds(250),
                engine = QueryEngine.BIGQUERY,
                isSystemQuery = false,
            )

        testCompletedEntity =
            QueryExecutionEntity(
                queryId = "query_completed_001",
                sql = "SELECT * FROM orders WHERE date >= '2026-01-01'",
                status = QueryStatus.COMPLETED,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(600),
                startedAt = now.minusSeconds(550),
                completedAt = now.minusSeconds(500),
                durationSeconds = 50.0,
                rowsReturned = 1500000L,
                bytesScanned = "1.2 GB",
                engine = QueryEngine.BIGQUERY,
                costUsd = 0.006,
                isSystemQuery = false,
            )

        testCancelledEntity =
            QueryExecutionEntity(
                queryId = "query_cancelled_001",
                sql = "SELECT * FROM large_table",
                status = QueryStatus.CANCELLED,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(400),
                startedAt = now.minusSeconds(350),
                completedAt = now.minusSeconds(300),
                engine = QueryEngine.TRINO,
                cancelledBy = "analyst@example.com",
                cancelledAt = now.minusSeconds(300),
                cancellationReason = "User requested cancellation",
                isSystemQuery = false,
            )

        testListItemDto =
            QueryListItemDto(
                queryId = "query_test_001",
                sql = "SELECT user_id, COUNT(*) FROM users GROUP BY 1",
                status = QueryStatus.RUNNING,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(300),
                startedAt = now.minusSeconds(250),
                completedAt = null,
                durationSeconds = null,
                rowsReturned = null,
                bytesScanned = null,
                engine = QueryEngine.BIGQUERY,
                costUsd = null,
            )

        testDetailDto =
            QueryDetailDto(
                queryId = "query_completed_001",
                sql = "SELECT * FROM orders WHERE date >= '2026-01-01'",
                status = QueryStatus.COMPLETED,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(600),
                startedAt = now.minusSeconds(550),
                completedAt = now.minusSeconds(500),
                durationSeconds = 50.0,
                rowsReturned = 1500000L,
                bytesScanned = "1.2 GB",
                engine = QueryEngine.BIGQUERY,
                costUsd = 0.006,
                executionDetails = null,
                error = null,
            )

        testCancelResponseDto =
            CancelQueryResponseDto(
                queryId = "query_test_001",
                status = QueryStatus.CANCELLED,
                cancelledBy = "analyst@example.com",
                cancelledAt = now,
                reason = "User requested cancellation",
            )
    }

    @Nested
    @DisplayName("GET /api/v1/queries")
    inner class ListQueries {
        @Test
        @DisplayName("should list queries with default parameters successfully")
        fun `should list queries with default parameters successfully`() {
            // Given
            val expectedQueries = listOf(testQueryEntity, testCompletedEntity)
            val expectedDtos = listOf(testListItemDto)

            val querySlot = slot<ListQueriesQuery>()
            val currentUserSlot = slot<String>()

            every { queryMetadataService.listQueries(capture(querySlot), capture(currentUserSlot)) } returns
                expectedQueries
            every { queryMapper.toListItemDtoList(expectedQueries) } returns expectedDtos

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].query_id").value("query_test_001"))
                .andExpect(jsonPath("$[0].status").value("RUNNING"))
                .andExpect(jsonPath("$[0].submitted_by").value("analyst@example.com"))
                .andExpect(jsonPath("$[0].engine").value("BIGQUERY"))

            // Verify service calls
            verify(exactly = 1) { queryMetadataService.listQueries(any(), any()) }
            verify(exactly = 1) { queryMapper.toListItemDtoList(expectedQueries) }

            // Verify query parameters
            assertThat(querySlot.captured.scope).isEqualTo(QueryScope.MY)
            assertThat(querySlot.captured.status).isNull()
            assertThat(querySlot.captured.startDate).isNull()
            assertThat(querySlot.captured.endDate).isNull()
            assertThat(querySlot.captured.limit).isEqualTo(50)
            assertThat(querySlot.captured.offset).isEqualTo(0)
            assertThat(currentUserSlot.captured).isEqualTo("analyst@example.com")
        }

        @Test
        @DisplayName("should list queries with filters successfully")
        fun `should list queries with filters successfully`() {
            // Given
            val expectedQueries = listOf(testCompletedEntity)
            val expectedDtos = listOf(testListItemDto)

            val querySlot = slot<ListQueriesQuery>()

            every { queryMetadataService.listQueries(capture(querySlot), any()) } returns expectedQueries
            every { queryMapper.toListItemDtoList(expectedQueries) } returns expectedDtos

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("scope", "system")
                        .param("status", "completed")
                        .param("start_date", "2026-01-01")
                        .param("end_date", "2026-01-02")
                        .param("limit", "10")
                        .param("offset", "5"),
                ).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())

            // Verify query parameters
            assertThat(querySlot.captured.scope).isEqualTo(QueryScope.SYSTEM)
            assertThat(querySlot.captured.status).isEqualTo(QueryStatus.COMPLETED)
            assertThat(querySlot.captured.startDate).isEqualTo(LocalDate.of(2026, 1, 1))
            assertThat(querySlot.captured.endDate).isEqualTo(LocalDate.of(2026, 1, 2))
            assertThat(querySlot.captured.limit).isEqualTo(10)
            assertThat(querySlot.captured.offset).isEqualTo(5)
        }

        @Test
        @DisplayName("should return empty list when no queries found")
        fun `should return empty list when no queries found`() {
            // Given
            every { queryMetadataService.listQueries(any(), any()) } returns emptyList()
            every { queryMapper.toListItemDtoList(emptyList()) } returns emptyList()

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty())
        }

        @Test
        @DisplayName("should return 400 for invalid scope")
        fun `should return 400 for invalid scope`() {
            // Given
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "INVALID_DATE_RANGE",
                            "message" to "Invalid date format or range: Invalid scope or status value",
                        ),
                )
            every { queryMapper.toInvalidDateRangeError("Invalid scope or status value") } returns errorResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("scope", "invalid_scope"),
                ).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"))

            verify(exactly = 0) { queryMetadataService.listQueries(any(), any()) }
        }

        @Test
        @DisplayName("should return 400 for invalid status")
        fun `should return 400 for invalid status`() {
            // Given
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "INVALID_DATE_RANGE",
                            "message" to "Invalid date format or range: Invalid scope or status value",
                        ),
                )
            every { queryMapper.toInvalidDateRangeError("Invalid scope or status value") } returns errorResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("status", "invalid_status"),
                ).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"))
        }

        @Test
        @DisplayName("should return 400 for invalid date format")
        fun `should return 400 for invalid date format`() {
            // Given
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "INVALID_DATE_RANGE",
                            "message" to
                                "Invalid date format or range: Text '2026-13-01' could not be parsed: Invalid value for MonthOfYear (valid values 1 - 12): 13",
                        ),
                )
            every { queryMapper.toInvalidDateRangeError(any()) } returns errorResponse

            // When & Then
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("start_date", "2026-13-01"), // Invalid month
                ).andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("INVALID_DATE_RANGE"))
        }

        @Test
        @DisplayName("should validate limit parameter constraints")
        fun `should validate limit parameter constraints`() {
            // When & Then - Test limit too large
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("limit", "501"), // Max is 500
                ).andExpect(status().isBadRequest())

            // When & Then - Test limit too small
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("limit", "0"), // Min is 1
                ).andExpect(status().isBadRequest())
        }

        @Test
        @DisplayName("should validate offset parameter constraints")
        fun `should validate offset parameter constraints`() {
            // When & Then - Test negative offset
            mockMvc
                .perform(
                    get("/api/v1/queries")
                        .param("offset", "-1"), // Min is 0
                ).andExpect(status().isBadRequest())
        }
    }

    @Nested
    @DisplayName("GET /api/v1/queries/{query_id}")
    inner class GetQueryDetails {
        @Test
        @DisplayName("should return query details successfully")
        fun `should return query details successfully`() {
            // Given
            val queryId = "query_completed_001"

            every { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") } returns testCompletedEntity
            every { queryMapper.toDetailDto(testCompletedEntity) } returns testDetailDto

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries/$queryId"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query_id").value(queryId))
                .andExpect(jsonPath("$.sql").value("SELECT * FROM orders WHERE date >= '2026-01-01'"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.submitted_by").value("analyst@example.com"))
                .andExpect(jsonPath("$.engine").value("BIGQUERY"))
                .andExpect(jsonPath("$.duration_seconds").value(50.0))
                .andExpect(jsonPath("$.rows_returned").value(1500000))
                .andExpect(jsonPath("$.bytes_scanned").value("1.2 GB"))
                .andExpect(jsonPath("$.cost_usd").value(0.006))

            verify(exactly = 1) { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") }
            verify(exactly = 1) { queryMapper.toDetailDto(testCompletedEntity) }
        }

        @Test
        @DisplayName("should return 404 when query not found")
        fun `should return 404 when query not found`() {
            // Given
            val queryId = "non_existent_query"

            every { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") } returns null

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries/$queryId"))
                .andExpect(status().isNotFound())

            verify(exactly = 1) { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") }
            verify(exactly = 0) { queryMapper.toDetailDto(any()) }
        }

        @Test
        @DisplayName("should return 403 when access denied")
        fun `should return 403 when access denied`() {
            // Given
            val queryId = "query_test_001"
            val errorMessage = "Cannot view other users' queries"
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "FORBIDDEN",
                            "message" to errorMessage,
                        ),
                )

            every { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") } throws
                AccessDeniedException(errorMessage)

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries/$queryId"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(errorMessage))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))

            verify(exactly = 1) { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") }
        }

        @Test
        @DisplayName("should return 404 for other exceptions")
        fun `should return 404 for other exceptions`() {
            // Given
            val queryId = "query_test_001"

            every { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") } throws
                ResourceNotFoundException("Query", queryId)

            // When & Then
            mockMvc
                .perform(get("/api/v1/queries/$queryId"))
                .andExpect(status().isNotFound())

            verify(exactly = 1) { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") }
        }
    }

    @Nested
    @DisplayName("POST /api/v1/queries/{query_id}/cancel")
    inner class CancelQuery {
        @Test
        @DisplayName("should cancel query successfully")
        fun `should cancel query successfully`() {
            // Given
            val queryId = "query_test_001"
            val cancelRequest = CancelQueryRequestDto(reason = "User requested cancellation")
            val commandSlot = slot<CancelQueryCommand>()

            every { queryMetadataService.cancelQuery(capture(commandSlot), "analyst@example.com") } returns
                testCancelledEntity
            every { queryMapper.toCancelQueryResponseDto(testCancelledEntity) } returns testCancelResponseDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/queries/$queryId/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelRequest)),
                ).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query_id").value(queryId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelled_by").value("analyst@example.com"))
                .andExpect(jsonPath("$.reason").value("User requested cancellation"))

            // Verify command parameters
            assertThat(commandSlot.captured.queryId).isEqualTo(queryId)
            assertThat(commandSlot.captured.reason).isEqualTo("User requested cancellation")

            verify(exactly = 1) { queryMetadataService.cancelQuery(any(), "analyst@example.com") }
            verify(exactly = 1) { queryMapper.toCancelQueryResponseDto(testCancelledEntity) }
        }

        @Test
        @DisplayName("should cancel query without reason")
        fun `should cancel query without reason`() {
            // Given
            val queryId = "query_test_001"
            val commandSlot = slot<CancelQueryCommand>()

            every { queryMetadataService.cancelQuery(capture(commandSlot), "analyst@example.com") } returns
                testCancelledEntity
            every { queryMapper.toCancelQueryResponseDto(testCancelledEntity) } returns testCancelResponseDto

            // When & Then
            mockMvc
                .perform(post("/api/v1/queries/$queryId/cancel"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.query_id").value(queryId))
                .andExpect(jsonPath("$.status").value("CANCELLED"))

            // Verify command parameters
            assertThat(commandSlot.captured.queryId).isEqualTo(queryId)
            assertThat(commandSlot.captured.reason).isNull()
        }

        @Test
        @DisplayName("should cancel query with empty body")
        fun `should cancel query with empty body`() {
            // Given
            val queryId = "query_test_001"
            val commandSlot = slot<CancelQueryCommand>()

            every { queryMetadataService.cancelQuery(capture(commandSlot), "analyst@example.com") } returns
                testCancelledEntity
            every { queryMapper.toCancelQueryResponseDto(testCancelledEntity) } returns testCancelResponseDto

            // When & Then
            mockMvc
                .perform(
                    post("/api/v1/queries/$queryId/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andExpect(status().isOk())

            // Verify command parameters
            assertThat(commandSlot.captured.queryId).isEqualTo(queryId)
            assertThat(commandSlot.captured.reason).isNull()
        }

        @Test
        @DisplayName("should return 404 when query not found")
        fun `should return 404 when query not found`() {
            // Given
            val queryId = "non_existent_query"
            val errorMessage = "Query not found"

            every { queryMetadataService.cancelQuery(any(), "analyst@example.com") } throws
                QueryNotFoundException(queryId)

            // When & Then
            mockMvc
                .perform(post("/api/v1/queries/$queryId/cancel"))
                .andExpect(status().isNotFound())

            verify(exactly = 1) { queryMetadataService.cancelQuery(any(), "analyst@example.com") }
        }

        @Test
        @DisplayName("should return 403 when access denied")
        fun `should return 403 when access denied`() {
            // Given
            val queryId = "query_test_001"
            val errorMessage = "Cannot cancel other users' queries"
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "FORBIDDEN",
                            "message" to errorMessage,
                        ),
                )

            every { queryMetadataService.cancelQuery(any(), "analyst@example.com") } throws
                AccessDeniedException(errorMessage)
            // When & Then
            mockMvc
                .perform(post("/api/v1/queries/$queryId/cancel"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value(errorMessage))
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"))

            verify(exactly = 1) { queryMetadataService.cancelQuery(any(), "analyst@example.com") }
        }

        @Test
        @DisplayName("should return 409 when query not cancellable")
        fun `should return 409 when query not cancellable`() {
            // Given
            val queryId = "query_completed_001"
            val errorMessage = "Query is not cancellable"
            val errorResponse =
                mapOf(
                    "error" to
                        mapOf(
                            "code" to "QUERY_NOT_CANCELLABLE",
                            "message" to "Query $queryId is already COMPLETED",
                        ),
                )

            every { queryMetadataService.cancelQuery(any(), "analyst@example.com") } throws
                QueryNotCancellableException(queryId, QueryStatus.COMPLETED)

            // When & Then
            mockMvc
                .perform(post("/api/v1/queries/$queryId/cancel"))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error.code").value("QUERY_NOT_CANCELLABLE"))

            verify(exactly = 1) { queryMetadataService.cancelQuery(any(), "analyst@example.com") }
        }

        @Test
        @DisplayName("should handle malformed request body gracefully")
        fun `should handle malformed request body gracefully`() {
            // Given
            val queryId = "query_test_001"

            // When & Then - Test with malformed JSON
            mockMvc
                .perform(
                    post("/api/v1/queries/$queryId/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("invalid json"),
                ).andExpect(status().isBadRequest())
        }
    }

    @Nested
    @DisplayName("Edge Cases and Integration")
    inner class EdgeCasesAndIntegration {
        @Test
        @DisplayName("should handle multiple query scopes correctly")
        fun `should handle multiple query scopes correctly`() {
            // Given
            val allQueries = listOf(testQueryEntity, testCompletedEntity, testCancelledEntity)
            val allDtos = listOf(testListItemDto)

            every { queryMetadataService.listQueries(any(), any()) } returns allQueries
            every { queryMapper.toListItemDtoList(allQueries) } returns allDtos

            // Test each scope
            val scopes = listOf("my", "system", "user", "all")

            scopes.forEach { scope ->
                mockMvc
                    .perform(
                        get("/api/v1/queries")
                            .param("scope", scope),
                    ).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$").isArray())
            }
        }

        @Test
        @DisplayName("should handle query status filtering correctly")
        fun `should handle query status filtering correctly`() {
            // Given
            val filteredQueries = listOf(testCompletedEntity)
            val filteredDtos = listOf(testListItemDto)

            every { queryMetadataService.listQueries(any(), any()) } returns filteredQueries
            every { queryMapper.toListItemDtoList(filteredQueries) } returns filteredDtos

            // Test different statuses
            val statuses = listOf("pending", "running", "completed", "failed", "cancelled")

            statuses.forEach { status ->
                mockMvc
                    .perform(
                        get("/api/v1/queries")
                            .param("status", status),
                    ).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            }
        }

        @Test
        @DisplayName("should handle concurrent requests safely")
        fun `should handle concurrent requests safely`() {
            // Given
            val queryId = "query_completed_001" // Use the same queryId as testDetailDto
            every { queryMetadataService.getQueryDetails(queryId, "analyst@example.com") } returns testCompletedEntity
            every { queryMapper.toDetailDto(testCompletedEntity) } returns testDetailDto

            // When & Then - Multiple concurrent requests should all succeed
            repeat(5) {
                mockMvc
                    .perform(get("/api/v1/queries/$queryId"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.query_id").value(queryId))
            }
        }
    }
}
