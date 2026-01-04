package com.github.lambda.domain.service

import com.github.lambda.domain.command.query.CancelQueryCommand
import com.github.lambda.domain.entity.query.QueryExecutionEntity
import com.github.lambda.domain.model.query.QueryEngine
import com.github.lambda.domain.model.query.QueryScope
import com.github.lambda.domain.model.query.QueryStatus
import com.github.lambda.domain.query.query.ListQueriesQuery
import com.github.lambda.domain.query.query.QueryScopeFilter
import com.github.lambda.domain.repository.QueryExecutionRepositoryDsl
import com.github.lambda.domain.repository.QueryExecutionRepositoryJpa
import io.mockk.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.*

/**
 * QueryMetadataService Unit Tests
 *
 * Tests business logic for query metadata management including access control,
 * query filtering, and cancellation logic. Uses MockK to isolate dependencies.
 */
@DisplayName("QueryMetadataService Unit Tests")
class QueryMetadataServiceTest {
    private lateinit var queryExecutionRepositoryJpa: QueryExecutionRepositoryJpa
    private lateinit var queryExecutionRepositoryDsl: QueryExecutionRepositoryDsl
    private lateinit var queryMetadataService: QueryMetadataService

    // Test data
    private lateinit var testQuery: QueryExecutionEntity
    private lateinit var testSystemQuery: QueryExecutionEntity
    private lateinit var testCompletedQuery: QueryExecutionEntity
    private lateinit var testRunningQuery: QueryExecutionEntity

    @BeforeEach
    fun setUp() {
        // Create fresh mocks for each test to avoid parallel test interference
        queryExecutionRepositoryJpa = mockk()
        queryExecutionRepositoryDsl = mockk()
        queryMetadataService =
            QueryMetadataService(
                queryExecutionRepositoryJpa = queryExecutionRepositoryJpa,
                queryExecutionRepositoryDsl = queryExecutionRepositoryDsl,
            )

        val now = Instant.now()

        testQuery =
            QueryExecutionEntity(
                queryId = "query_test_001",
                sql = "SELECT * FROM users",
                status = QueryStatus.RUNNING,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(300),
                startedAt = now.minusSeconds(250),
                engine = QueryEngine.BIGQUERY,
                isSystemQuery = false,
            )

        testSystemQuery =
            QueryExecutionEntity(
                queryId = "query_system_001",
                sql = "SHOW TABLES",
                status = QueryStatus.RUNNING,
                submittedBy = "system@example.com",
                submittedAt = now.minusSeconds(100),
                startedAt = now.minusSeconds(90),
                engine = QueryEngine.BIGQUERY,
                isSystemQuery = true,
            )

        testCompletedQuery =
            QueryExecutionEntity(
                queryId = "query_completed_001",
                sql = "SELECT COUNT(*) FROM orders",
                status = QueryStatus.COMPLETED,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(600),
                startedAt = now.minusSeconds(550),
                completedAt = now.minusSeconds(500),
                engine = QueryEngine.BIGQUERY,
                isSystemQuery = false,
                rowsReturned = 1,
                durationSeconds = 50.0,
            )

        testRunningQuery =
            QueryExecutionEntity(
                queryId = "query_running_001",
                sql = "SELECT * FROM large_table",
                status = QueryStatus.RUNNING,
                submittedBy = "analyst@example.com",
                submittedAt = now.minusSeconds(180),
                startedAt = now.minusSeconds(120),
                engine = QueryEngine.BIGQUERY,
                isSystemQuery = false,
            )
    }

    @Nested
    @DisplayName("cancelQuery method")
    inner class CancelQuery {
        @Test
        @DisplayName("should cancel a running query successfully")
        fun `should cancel a running query successfully`() {
            // Given
            val command =
                CancelQueryCommand(
                    queryId = "query_test_001",
                    reason = "User requested cancellation",
                )
            val currentUser = "analyst@example.com"
            val now = Instant.now()

            every { queryExecutionRepositoryJpa.findById("query_test_001") } returns Optional.of(testQuery)
            every { queryExecutionRepositoryJpa.save(any()) } returnsArgument 0

            // When
            val result = queryMetadataService.cancelQuery(command, currentUser)

            // Then
            assertThat(result.status).isEqualTo(QueryStatus.CANCELLED)
            assertThat(result.cancelledBy).isEqualTo(currentUser)
            assertThat(result.cancellationReason).isEqualTo("User requested cancellation")
            assertThat(result.cancelledAt).isNotNull()
            assertThat(result.completedAt).isNotNull()

            verify(exactly = 1) { queryExecutionRepositoryJpa.findById("query_test_001") }
            verify(exactly = 1) { queryExecutionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw QueryNotFoundException when query does not exist")
        fun `should throw QueryNotFoundException when query does not exist`() {
            // Given
            val command = CancelQueryCommand(queryId = "non_existent_query")
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById("non_existent_query") } returns Optional.empty()

            // When & Then
            val exception =
                assertThrows<QueryNotFoundException> {
                    queryMetadataService.cancelQuery(command, currentUser)
                }

            assertThat(exception.message).contains("non_existent_query")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById("non_existent_query") }
            verify(exactly = 0) { queryExecutionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw QueryNotCancellableException for completed query")
        fun `should throw QueryNotCancellableException for completed query`() {
            // Given
            val command = CancelQueryCommand(queryId = "query_completed_001")
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById("query_completed_001") } returns
                Optional.of(testCompletedQuery)

            // When & Then
            val exception =
                assertThrows<QueryNotCancellableException> {
                    queryMetadataService.cancelQuery(command, currentUser)
                }

            assertThat(exception.message).contains("query_completed_001")
            assertThat(exception.message).contains("COMPLETED")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById("query_completed_001") }
            verify(exactly = 0) { queryExecutionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user tries to cancel other user's query")
        fun `should throw AccessDeniedException when user tries to cancel other user's query`() {
            // Given
            val command = CancelQueryCommand(queryId = "query_test_001")
            val currentUser = "other@example.com" // Different user

            every { queryExecutionRepositoryJpa.findById("query_test_001") } returns Optional.of(testQuery)

            // When & Then
            val exception =
                assertThrows<AccessDeniedException> {
                    queryMetadataService.cancelQuery(command, currentUser)
                }

            assertThat(exception.message).contains("Cannot cancel other users' queries")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById("query_test_001") }
            verify(exactly = 0) { queryExecutionRepositoryJpa.save(any()) }
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user tries to cancel system query")
        fun `should throw AccessDeniedException when user tries to cancel system query`() {
            // Given
            val command = CancelQueryCommand(queryId = "query_system_001")
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById("query_system_001") } returns Optional.of(testSystemQuery)

            // When & Then
            val exception =
                assertThrows<AccessDeniedException> {
                    queryMetadataService.cancelQuery(command, currentUser)
                }

            assertThat(exception.message).contains("Cannot cancel system queries")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById("query_system_001") }
            verify(exactly = 0) { queryExecutionRepositoryJpa.save(any()) }
        }
    }

    @Nested
    @DisplayName("listQueries method")
    inner class ListQueries {
        @Test
        @DisplayName("should list queries with MY scope successfully")
        fun `should list queries with MY scope successfully`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.MY, limit = 10, offset = 0)
            val currentUser = "analyst@example.com"
            val expectedQueries = listOf(testQuery, testCompletedQuery)

            val expectedFilter =
                QueryScopeFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                )

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 10,
                    offset = 0,
                )
            } returns expectedQueries

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 10,
                    offset = 0,
                )
            }
        }

        @Test
        @DisplayName("should list queries with SYSTEM scope successfully")
        fun `should list queries with SYSTEM scope successfully`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.SYSTEM, limit = 20, offset = 0)
            val currentUser = "analyst@example.com"
            val expectedQueries = listOf(testSystemQuery)

            val expectedFilter = QueryScopeFilter(isSystemQuery = true)

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 20,
                    offset = 0,
                )
            } returns expectedQueries

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 20,
                    offset = 0,
                )
            }
        }

        @Test
        @DisplayName("should list queries with status and date filters")
        fun `should list queries with status and date filters`() {
            // Given
            val startDate = LocalDate.of(2026, 1, 1)
            val endDate = LocalDate.of(2026, 1, 2)
            val query =
                ListQueriesQuery(
                    scope = QueryScope.MY,
                    status = QueryStatus.COMPLETED,
                    startDate = startDate,
                    endDate = endDate,
                    limit = 5,
                    offset = 10,
                )
            val currentUser = "analyst@example.com"
            val expectedQueries = listOf(testCompletedQuery)

            val expectedFilter =
                QueryScopeFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                )

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = QueryStatus.COMPLETED,
                    startDate = startDate,
                    endDate = endDate,
                    limit = 5,
                    offset = 10,
                )
            } returns expectedQueries

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = QueryStatus.COMPLETED,
                    startDate = startDate,
                    endDate = endDate,
                    limit = 5,
                    offset = 10,
                )
            }
        }

        @Test
        @DisplayName("should list queries with ALL scope (no filter)")
        fun `should list queries with ALL scope (no filter)`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.ALL, limit = 50, offset = 0)
            val currentUser = "admin@example.com"
            val expectedQueries = listOf(testQuery, testSystemQuery, testCompletedQuery)

            val expectedFilter = QueryScopeFilter() // Empty filter

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            } returns expectedQueries

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            }
        }
    }

    @Nested
    @DisplayName("getQueryDetails method")
    inner class GetQueryDetails {
        @Test
        @DisplayName("should return query details for owner")
        fun `should return query details for owner`() {
            // Given
            val queryId = "query_test_001"
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.of(testQuery)

            // When
            val result = queryMetadataService.getQueryDetails(queryId, currentUser)

            // Then
            assertThat(result).isEqualTo(testQuery)
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }

        @Test
        @DisplayName("should return null when query does not exist")
        fun `should return null when query does not exist`() {
            // Given
            val queryId = "non_existent_query"
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.empty()

            // When
            val result = queryMetadataService.getQueryDetails(queryId, currentUser)

            // Then
            assertThat(result).isNull()
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }

        @Test
        @DisplayName("should return system query for any user")
        fun `should return system query for any user`() {
            // Given
            val queryId = "query_system_001"
            val currentUser = "any@example.com"

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.of(testSystemQuery)

            // When
            val result = queryMetadataService.getQueryDetails(queryId, currentUser)

            // Then
            assertThat(result).isEqualTo(testSystemQuery)
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }

        @Test
        @DisplayName("should throw AccessDeniedException when user tries to view other user's query")
        fun `should throw AccessDeniedException when user tries to view other user's query`() {
            // Given
            val queryId = "query_test_001"
            val currentUser = "other@example.com" // Different user

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.of(testQuery)

            // When & Then
            val exception =
                assertThrows<AccessDeniedException> {
                    queryMetadataService.getQueryDetails(queryId, currentUser)
                }

            assertThat(exception.message).contains("Cannot view other users' queries")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }
    }

    @Nested
    @DisplayName("getQueryDetailsOrThrow method")
    inner class GetQueryDetailsOrThrow {
        @Test
        @DisplayName("should return query when found")
        fun `should return query when found`() {
            // Given
            val queryId = "query_test_001"
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.of(testQuery)

            // When
            val result = queryMetadataService.getQueryDetailsOrThrow(queryId, currentUser)

            // Then
            assertThat(result).isEqualTo(testQuery)
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }

        @Test
        @DisplayName("should throw QueryNotFoundException when query not found")
        fun `should throw QueryNotFoundException when query not found`() {
            // Given
            val queryId = "non_existent_query"
            val currentUser = "analyst@example.com"

            every { queryExecutionRepositoryJpa.findById(queryId) } returns Optional.empty()

            // When & Then
            val exception =
                assertThrows<QueryNotFoundException> {
                    queryMetadataService.getQueryDetailsOrThrow(queryId, currentUser)
                }

            assertThat(exception.message).contains("non_existent_query")
            verify(exactly = 1) { queryExecutionRepositoryJpa.findById(queryId) }
        }
    }

    @Nested
    @DisplayName("countQueries method")
    inner class CountQueries {
        @Test
        @DisplayName("should count queries with filters correctly")
        fun `should count queries with filters correctly`() {
            // Given
            val query =
                ListQueriesQuery(
                    scope = QueryScope.MY,
                    status = QueryStatus.RUNNING,
                    limit = 10,
                    offset = 0,
                )
            val currentUser = "analyst@example.com"
            val expectedCount = 5L

            every {
                queryExecutionRepositoryDsl.countByFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                    status = QueryStatus.RUNNING,
                    startDate = null,
                    endDate = null,
                )
            } returns expectedCount

            // When
            val result = queryMetadataService.countQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedCount)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.countByFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                    status = QueryStatus.RUNNING,
                    startDate = null,
                    endDate = null,
                )
            }
        }

        @Test
        @DisplayName("should count system queries correctly")
        fun `should count system queries correctly`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.SYSTEM)
            val currentUser = "analyst@example.com"
            val expectedCount = 3L

            every {
                queryExecutionRepositoryDsl.countByFilter(
                    submittedBy = null,
                    isSystemQuery = true,
                    status = null,
                    startDate = null,
                    endDate = null,
                )
            } returns expectedCount

            // When
            val result = queryMetadataService.countQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedCount)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.countByFilter(
                    submittedBy = null,
                    isSystemQuery = true,
                    status = null,
                    startDate = null,
                    endDate = null,
                )
            }
        }
    }

    @Nested
    @DisplayName("getRunningQueries method")
    inner class GetRunningQueries {
        @Test
        @DisplayName("should return running queries for specific user")
        fun `should return running queries for specific user`() {
            // Given
            val currentUser = "analyst@example.com"
            val expectedQueries = listOf(testRunningQuery)

            every { queryExecutionRepositoryDsl.findRunningQueries(currentUser) } returns expectedQueries

            // When
            val result = queryMetadataService.getRunningQueries(currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) { queryExecutionRepositoryDsl.findRunningQueries(currentUser) }
        }

        @Test
        @DisplayName("should return all running queries when currentUser is null")
        fun `should return all running queries when currentUser is null`() {
            // Given
            val currentUser: String? = null
            val expectedQueries = listOf(testRunningQuery, testQuery)

            every { queryExecutionRepositoryDsl.findRunningQueries(currentUser) } returns expectedQueries

            // When
            val result = queryMetadataService.getRunningQueries(currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) { queryExecutionRepositoryDsl.findRunningQueries(currentUser) }
        }
    }

    @Nested
    @DisplayName("Edge Cases and Validation")
    inner class EdgeCasesAndValidation {
        @Test
        @DisplayName("should handle empty query list")
        fun `should handle empty query list`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.MY)
            val currentUser = "analyst@example.com"

            val expectedFilter =
                QueryScopeFilter(
                    submittedBy = currentUser,
                    isSystemQuery = false,
                )

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            } returns emptyList()

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEmpty()
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            }
        }

        @Test
        @DisplayName("should handle USER scope same as MY scope")
        fun `should handle USER scope same as MY scope`() {
            // Given
            val query = ListQueriesQuery(scope = QueryScope.USER)
            val currentUser = "analyst@example.com"
            val expectedQueries = listOf(testQuery)

            val expectedFilter =
                QueryScopeFilter(
                    submittedBy = currentUser,
                    isSystemQuery = null, // USER scope doesn't set isSystemQuery, so it's null
                )

            every {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            } returns expectedQueries

            // When
            val result = queryMetadataService.listQueries(query, currentUser)

            // Then
            assertThat(result).isEqualTo(expectedQueries)
            verify(exactly = 1) {
                queryExecutionRepositoryDsl.findByScope(
                    filter = expectedFilter,
                    status = null,
                    startDate = null,
                    endDate = null,
                    limit = 50,
                    offset = 0,
                )
            }
        }
    }
}
