package com.dataops.basecamp.mapper

import com.dataops.basecamp.common.enums.QueryEngine
import com.dataops.basecamp.common.enums.QueryStatus
import com.dataops.basecamp.domain.entity.query.QueryExecutionEntity
import com.dataops.basecamp.dto.query.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * QueryMapper Unit Tests
 *
 * Tests Domain Model → Response DTO mapping functionality for Query API responses.
 * Tests JSON parsing for execution details and error details.
 */
@DisplayName("QueryMapper Unit Tests")
class QueryMapperTest {
    private lateinit var mapper: QueryMapper
    private lateinit var testQueryEntity: QueryExecutionEntity
    private lateinit var testCompletedEntity: QueryExecutionEntity
    private lateinit var testCancelledEntity: QueryExecutionEntity
    private lateinit var testFailedEntity: QueryExecutionEntity

    @BeforeEach
    fun setUp() {
        mapper = QueryMapper

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
                executionDetails =
                    """
                    {
                        "job_id": "bqjob_r1234567890_000001_project",
                        "query_plan": [
                            {
                                "stage": "Stage 1",
                                "operation": "Scan",
                                "input_rows": 2000000,
                                "output_rows": 1500000
                            }
                        ],
                        "tables_accessed": [
                            "my-project.analytics.orders"
                        ]
                    }
                    """.trimIndent(),
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

        testFailedEntity =
            QueryExecutionEntity(
                queryId = "query_failed_001",
                sql = "SELECT * FROM non_existent_table",
                status = QueryStatus.FAILED,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(200),
                startedAt = now.minusSeconds(150),
                completedAt = now.minusSeconds(140),
                engine = QueryEngine.BIGQUERY,
                errorDetails =
                    """
                    {
                        "code": "TABLE_NOT_FOUND",
                        "message": "Table 'my-project.analytics.non_existent_table' was not found",
                        "details": {
                            "table": "my-project.analytics.non_existent_table",
                            "project": "my-project"
                        }
                    }
                    """.trimIndent(),
                isSystemQuery = false,
            )
    }

    @Nested
    @DisplayName("toListItemDto method")
    inner class ToListItemDto {
        @Test
        @DisplayName("should convert running query entity to list item DTO")
        fun `should convert running query entity to list item DTO`() {
            // When
            val result = mapper.toListItemDto(testQueryEntity)

            // Then
            assertThat(result.queryId).isEqualTo("query_test_001")
            assertThat(result.sql).isEqualTo("SELECT user_id, COUNT(*) FROM users GROUP BY 1")
            assertThat(result.status).isEqualTo(QueryStatus.RUNNING)
            assertThat(result.submittedBy).isEqualTo("analyst@example.com")
            assertThat(result.submittedAt).isEqualTo(testQueryEntity.submittedAt)
            assertThat(result.startedAt).isEqualTo(testQueryEntity.startedAt)
            assertThat(result.completedAt).isNull()
            assertThat(result.durationSeconds).isNull()
            assertThat(result.rowsReturned).isNull()
            assertThat(result.bytesScanned).isNull()
            assertThat(result.engine).isEqualTo(QueryEngine.BIGQUERY)
            assertThat(result.costUsd).isNull()
        }

        @Test
        @DisplayName("should convert completed query entity to list item DTO with all fields")
        fun `should convert completed query entity to list item DTO with all fields`() {
            // When
            val result = mapper.toListItemDto(testCompletedEntity)

            // Then
            assertThat(result.queryId).isEqualTo("query_completed_001")
            assertThat(result.sql).isEqualTo("SELECT * FROM orders WHERE date >= '2026-01-01'")
            assertThat(result.status).isEqualTo(QueryStatus.COMPLETED)
            assertThat(result.submittedBy).isEqualTo("analyst@example.com")
            assertThat(result.submittedAt).isEqualTo(testCompletedEntity.submittedAt)
            assertThat(result.startedAt).isEqualTo(testCompletedEntity.startedAt)
            assertThat(result.completedAt).isEqualTo(testCompletedEntity.completedAt)
            assertThat(result.durationSeconds).isEqualTo(50.0)
            assertThat(result.rowsReturned).isEqualTo(1500000L)
            assertThat(result.bytesScanned).isEqualTo("1.2 GB")
            assertThat(result.engine).isEqualTo(QueryEngine.BIGQUERY)
            assertThat(result.costUsd).isEqualTo(0.006)
        }
    }

    @Nested
    @DisplayName("toDetailDto method")
    inner class ToDetailDto {
        @Test
        @DisplayName("should convert completed entity to detail DTO with execution details")
        fun `should convert completed entity to detail DTO with execution details`() {
            // When
            val result = mapper.toDetailDto(testCompletedEntity)

            // Then
            assertThat(result.queryId).isEqualTo("query_completed_001")
            assertThat(result.sql).isEqualTo("SELECT * FROM orders WHERE date >= '2026-01-01'")
            assertThat(result.status).isEqualTo(QueryStatus.COMPLETED)
            assertThat(result.submittedBy).isEqualTo("analyst@example.com")
            assertThat(result.submittedAt).isEqualTo(testCompletedEntity.submittedAt)
            assertThat(result.startedAt).isEqualTo(testCompletedEntity.startedAt)
            assertThat(result.completedAt).isEqualTo(testCompletedEntity.completedAt)
            assertThat(result.durationSeconds).isEqualTo(50.0)
            assertThat(result.rowsReturned).isEqualTo(1500000L)
            assertThat(result.bytesScanned).isEqualTo("1.2 GB")
            assertThat(result.engine).isEqualTo(QueryEngine.BIGQUERY)
            assertThat(result.costUsd).isEqualTo(0.006)

            // Execution details should be parsed
            assertThat(result.executionDetails).isNotNull()
            assertThat(result.executionDetails?.jobId).isEqualTo("bqjob_r1234567890_000001_project")
            assertThat(result.executionDetails?.queryPlan).hasSize(1)
            assertThat(
                result.executionDetails
                    ?.queryPlan
                    ?.get(0)
                    ?.stage,
            ).isEqualTo("Stage 1")
            assertThat(
                result.executionDetails
                    ?.queryPlan
                    ?.get(0)
                    ?.operation,
            ).isEqualTo("Scan")
            assertThat(result.executionDetails?.tablesAccessed).containsExactly("my-project.analytics.orders")

            assertThat(result.error).isNull()
        }

        @Test
        @DisplayName("should convert failed entity to detail DTO with error details")
        fun `should convert failed entity to detail DTO with error details`() {
            // When
            val result = mapper.toDetailDto(testFailedEntity)

            // Then
            assertThat(result.queryId).isEqualTo("query_failed_001")
            assertThat(result.sql).isEqualTo("SELECT * FROM non_existent_table")
            assertThat(result.status).isEqualTo(QueryStatus.FAILED)
            assertThat(result.executionDetails).isNull()

            // Error details should be parsed
            assertThat(result.error).isNotNull()
            assertThat(result.error?.code).isEqualTo("TABLE_NOT_FOUND")
            assertThat(result.error?.message).isEqualTo("Table 'my-project.analytics.non_existent_table' was not found")
            assertThat(result.error?.details).isNotNull()
            assertThat(result.error?.details?.get("table")).isEqualTo("my-project.analytics.non_existent_table")
            assertThat(result.error?.details?.get("project")).isEqualTo("my-project")
        }

        @Test
        @DisplayName("should handle entity with null execution and error details")
        fun `should handle entity with null execution and error details`() {
            // When
            val result = mapper.toDetailDto(testQueryEntity)

            // Then
            assertThat(result.executionDetails).isNull()
            assertThat(result.error).isNull()
        }
    }

    @Nested
    @DisplayName("toCancelQueryResponseDto method")
    inner class ToCancelQueryResponseDto {
        @Test
        @DisplayName("should convert cancelled entity to cancel response DTO")
        fun `should convert cancelled entity to cancel response DTO`() {
            // When
            val result = mapper.toCancelQueryResponseDto(testCancelledEntity)

            // Then
            assertThat(result.queryId).isEqualTo("query_cancelled_001")
            assertThat(result.status).isEqualTo(QueryStatus.CANCELLED)
            assertThat(result.cancelledBy).isEqualTo("analyst@example.com")
            assertThat(result.cancelledAt).isEqualTo(testCancelledEntity.cancelledAt)
            assertThat(result.reason).isEqualTo("User requested cancellation")
        }

        @Test
        @DisplayName("should throw IllegalStateException when cancelledBy is null")
        fun `should throw IllegalStateException when cancelledBy is null`() {
            // Given
            val entityWithNullCancelledBy =
                QueryExecutionEntity(
                    queryId = testCancelledEntity.queryId,
                    sql = testCancelledEntity.sql,
                    status = testCancelledEntity.status,
                    submittedBy = testCancelledEntity.submittedBy,
                    submittedAt = testCancelledEntity.submittedAt,
                    startedAt = testCancelledEntity.startedAt,
                    completedAt = testCancelledEntity.completedAt,
                    engine = testCancelledEntity.engine,
                    cancelledBy = null, // Null cancelledBy
                    cancelledAt = testCancelledEntity.cancelledAt,
                    cancellationReason = testCancelledEntity.cancellationReason,
                    isSystemQuery = testCancelledEntity.isSystemQuery,
                )

            // When & Then
            val exception =
                org.junit.jupiter.api.assertThrows<IllegalStateException> {
                    mapper.toCancelQueryResponseDto(entityWithNullCancelledBy)
                }

            assertThat(exception.message).contains("Cancelled query must have cancelledBy field")
        }
    }

    @Nested
    @DisplayName("toListItemDtoList method")
    inner class ToListItemDtoList {
        @Test
        @DisplayName("should convert list of entities to list of DTOs")
        fun `should convert list of entities to list of DTOs`() {
            // Given
            val entities = listOf(testQueryEntity, testCompletedEntity, testCancelledEntity)

            // When
            val result = mapper.toListItemDtoList(entities)

            // Then
            assertThat(result).hasSize(3)
            assertThat(result[0].queryId).isEqualTo("query_test_001")
            assertThat(result[1].queryId).isEqualTo("query_completed_001")
            assertThat(result[2].queryId).isEqualTo("query_cancelled_001")
        }

        @Test
        @DisplayName("should handle empty list")
        fun `should handle empty list`() {
            // Given
            val entities = emptyList<QueryExecutionEntity>()

            // When
            val result = mapper.toListItemDtoList(entities)

            // Then
            assertThat(result).isEmpty()
        }
    }

    @Nested
    @DisplayName("Error Response Helper Methods")
    inner class ErrorResponseHelperMethods {
        @Test
        @DisplayName("should create query not found error response")
        fun `should create query not found error response`() {
            // Given
            val queryId = "non_existent_query"

            // When
            val result = mapper.toQueryNotFoundError(queryId)

            // Then
            assertThat(result).containsKey("error")

            val errorMap = result["error"] as Map<*, *>
            assertThat(errorMap["code"]).isEqualTo("QUERY_NOT_FOUND")
            assertThat(errorMap["message"]).isEqualTo("Query $queryId does not exist")

            val detailsMap = errorMap["details"] as Map<*, *>
            assertThat(detailsMap["query_id"]).isEqualTo(queryId)
        }

        @Test
        @DisplayName("should create query not cancellable error response")
        fun `should create query not cancellable error response`() {
            // Given
            val queryId = "query_completed_001"
            val currentStatus = "COMPLETED"

            // When
            val result = mapper.toQueryNotCancellableError(queryId, currentStatus)

            // Then
            assertThat(result).containsKey("error")

            val errorMap = result["error"] as Map<*, *>
            assertThat(errorMap["code"]).isEqualTo("QUERY_NOT_CANCELLABLE")
            assertThat(errorMap["message"]).isEqualTo("Query $queryId is already $currentStatus")

            val detailsMap = errorMap["details"] as Map<*, *>
            assertThat(detailsMap["query_id"]).isEqualTo(queryId)
            assertThat(detailsMap["current_status"]).isEqualTo(currentStatus)
        }

        @Test
        @DisplayName("should create access denied error response")
        fun `should create access denied error response`() {
            // Given
            val message = "Cannot view other users' queries"

            // When
            val result = mapper.toAccessDeniedError(message)

            // Then
            assertThat(result).containsKey("error")

            val errorMap = result["error"] as Map<*, *>
            assertThat(errorMap["code"]).isEqualTo("FORBIDDEN")
            assertThat(errorMap["message"]).isEqualTo(message)
        }

        @Test
        @DisplayName("should create invalid date range error response")
        fun `should create invalid date range error response`() {
            // Given
            val reason = "End date must be after start date"

            // When
            val result = mapper.toInvalidDateRangeError(reason)

            // Then
            assertThat(result).containsKey("error")

            val errorMap = result["error"] as Map<*, *>
            assertThat(errorMap["code"]).isEqualTo("INVALID_DATE_RANGE")
            assertThat(errorMap["message"]).isEqualTo("Invalid date format or range: $reason")
        }
    }
}
