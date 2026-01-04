package com.github.lambda.domain.model.query

import com.github.lambda.common.enums.QueryEngine
import com.github.lambda.common.enums.QueryStatus
import com.github.lambda.domain.entity.query.QueryExecutionEntity
import org.assertj.core.api.Assertions.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * QueryExecutionEntity Unit Tests
 *
 * Tests domain logic and business rules for query execution entities.
 * Focuses on status checking methods, duration calculations, and entity equality.
 */
@DisplayName("QueryExecutionEntity Unit Tests")
class QueryExecutionEntityTest {
    private val now = Instant.now()

    @Nested
    @DisplayName("isCancellable method")
    inner class IsCancellable {
        @Test
        @DisplayName("should return true for PENDING status")
        fun `should return true for PENDING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_001",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.PENDING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isCancellable()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for RUNNING status")
        fun `should return true for RUNNING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_002",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isCancellable()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for COMPLETED status")
        fun `should return false for COMPLETED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_003",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isCancellable()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for FAILED status")
        fun `should return false for FAILED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_004",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.FAILED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isCancellable()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for CANCELLED status")
        fun `should return false for CANCELLED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_005",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.CANCELLED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isCancellable()

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("isTerminal method")
    inner class IsTerminal {
        @Test
        @DisplayName("should return false for PENDING status")
        fun `should return false for PENDING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_006",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.PENDING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isTerminal()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for RUNNING status")
        fun `should return false for RUNNING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_007",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isTerminal()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return true for COMPLETED status")
        fun `should return true for COMPLETED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_008",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isTerminal()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for FAILED status")
        fun `should return true for FAILED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_009",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.FAILED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isTerminal()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return true for CANCELLED status")
        fun `should return true for CANCELLED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_010",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.CANCELLED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isTerminal()

            // Then
            assertThat(result).isTrue()
        }
    }

    @Nested
    @DisplayName("isRunning method")
    inner class IsRunning {
        @Test
        @DisplayName("should return false for PENDING status")
        fun `should return false for PENDING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_011",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.PENDING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isRunning()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return true for RUNNING status")
        fun `should return true for RUNNING status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_012",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isRunning()

            // Then
            assertThat(result).isTrue()
        }

        @Test
        @DisplayName("should return false for COMPLETED status")
        fun `should return false for COMPLETED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_013",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isRunning()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for FAILED status")
        fun `should return false for FAILED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_014",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.FAILED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isRunning()

            // Then
            assertThat(result).isFalse()
        }

        @Test
        @DisplayName("should return false for CANCELLED status")
        fun `should return false for CANCELLED status`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_015",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.CANCELLED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.isRunning()

            // Then
            assertThat(result).isFalse()
        }
    }

    @Nested
    @DisplayName("getCalculatedDurationSeconds method")
    inner class GetCalculatedDurationSeconds {
        @Test
        @DisplayName("should calculate duration from timestamps when both are available")
        fun `should calculate duration from timestamps when both are available`() {
            // Given
            val startTime = now.minusSeconds(100)
            val endTime = now.minusSeconds(50)
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_016",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = startTime,
                    completedAt = endTime,
                    durationSeconds = 999.0, // Should be ignored in favor of calculated value
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.getCalculatedDurationSeconds()

            // Then
            assertThat(result).isEqualTo(50.0) // 100 - 50 = 50 seconds
        }

        @Test
        @DisplayName("should handle nanosecond precision in duration calculation")
        fun `should handle nanosecond precision in duration calculation`() {
            // Given
            val startTime = Instant.ofEpochSecond(1000, 100_000_000) // 1000.1 seconds
            val endTime = Instant.ofEpochSecond(1002, 300_000_000) // 1002.3 seconds
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_017",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = startTime,
                    completedAt = endTime,
                    durationSeconds = null,
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.getCalculatedDurationSeconds()

            // Then
            assertThat(result).isEqualTo(2.2) // 1002.3 - 1000.1 = 2.2 seconds
        }

        @Test
        @DisplayName("should return stored duration when startedAt is null")
        fun `should return stored duration when startedAt is null`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_018",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = null,
                    completedAt = now,
                    durationSeconds = 42.5,
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.getCalculatedDurationSeconds()

            // Then
            assertThat(result).isEqualTo(42.5)
        }

        @Test
        @DisplayName("should return stored duration when completedAt is null")
        fun `should return stored duration when completedAt is null`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_019",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = now.minusSeconds(100),
                    completedAt = null,
                    durationSeconds = 15.7,
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.getCalculatedDurationSeconds()

            // Then
            assertThat(result).isEqualTo(15.7)
        }

        @Test
        @DisplayName("should return null when both timestamps and stored duration are null")
        fun `should return null when both timestamps and stored duration are null`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_020",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.PENDING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = null,
                    completedAt = null,
                    durationSeconds = null,
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When
            val result = entity.getCalculatedDurationSeconds()

            // Then
            assertThat(result).isNull()
        }
    }

    @Nested
    @DisplayName("Entity Equality and Hashing")
    inner class EntityEqualityAndHashing {
        @Test
        @DisplayName("should be equal when queryIds are the same")
        fun `should be equal when queryIds are the same`() {
            // Given
            val entity1 =
                QueryExecutionEntity(
                    queryId = "test_query_021",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )
            val entity2 =
                QueryExecutionEntity(
                    queryId = "test_query_021", // Same queryId
                    sql = "SELECT * FROM orders", // Different SQL
                    status = QueryStatus.COMPLETED, // Different status
                    submittedBy = "different@example.com", // Different user
                    submittedAt = now.minusSeconds(100), // Different time
                    engine = QueryEngine.TRINO, // Different engine
                    isSystemQuery = true, // Different system flag
                )

            // When & Then
            assertThat(entity1).isEqualTo(entity2)
            assertThat(entity1.hashCode()).isEqualTo(entity2.hashCode())
        }

        @Test
        @DisplayName("should not be equal when queryIds are different")
        fun `should not be equal when queryIds are different`() {
            // Given
            val entity1 =
                QueryExecutionEntity(
                    queryId = "test_query_022",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )
            val entity2 =
                QueryExecutionEntity(
                    queryId = "test_query_023", // Different queryId
                    sql = "SELECT * FROM users", // Same SQL
                    status = QueryStatus.RUNNING, // Same status
                    submittedBy = "analyst@example.com", // Same user
                    submittedAt = now.minusSeconds(300), // Same time
                    engine = QueryEngine.BIGQUERY, // Same engine
                    isSystemQuery = false, // Same system flag
                )

            // When & Then
            assertThat(entity1).isNotEqualTo(entity2)
            assertThat(entity1.hashCode()).isNotEqualTo(entity2.hashCode())
        }

        @Test
        @DisplayName("should not be equal to different object types")
        fun `should not be equal to different object types`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_024",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )
            val differentObject = "test_query_024"

            // When & Then
            assertThat(entity).isNotEqualTo(differentObject)
        }

        @Test
        @DisplayName("should be equal to itself")
        fun `should be equal to itself`() {
            // Given
            val entity =
                QueryExecutionEntity(
                    queryId = "test_query_025",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(entity).isEqualTo(entity)
        }
    }

    @Nested
    @DisplayName("Entity Validation Scenarios")
    inner class EntityValidationScenarios {
        @Test
        @DisplayName("should handle system query correctly")
        fun `should handle system query correctly`() {
            // Given
            val systemQuery =
                QueryExecutionEntity(
                    queryId = "system_query_001",
                    sql = "SHOW TABLES",
                    status = QueryStatus.RUNNING,
                    submittedBy = "system@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = true,
                )

            // When & Then
            assertThat(systemQuery.isSystemQuery).isTrue()
            assertThat(systemQuery.submittedBy).isEqualTo("system@example.com")
        }

        @Test
        @DisplayName("should handle user query correctly")
        fun `should handle user query correctly`() {
            // Given
            val userQuery =
                QueryExecutionEntity(
                    queryId = "user_query_001",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(userQuery.isSystemQuery).isFalse()
            assertThat(userQuery.submittedBy).isEqualTo("analyst@example.com")
        }

        @Test
        @DisplayName("should handle different query engines")
        fun `should handle different query engines`() {
            // Given
            val bigQueryEntity =
                QueryExecutionEntity(
                    queryId = "bigquery_query_001",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.BIGQUERY,
                    isSystemQuery = false,
                )
            val trinoEntity =
                QueryExecutionEntity(
                    queryId = "trino_query_001",
                    sql = "SELECT * FROM users",
                    status = QueryStatus.RUNNING,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    engine = QueryEngine.TRINO,
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(bigQueryEntity.engine).isEqualTo(QueryEngine.BIGQUERY)
            assertThat(trinoEntity.engine).isEqualTo(QueryEngine.TRINO)
        }

        @Test
        @DisplayName("should handle query with complete execution metadata")
        fun `should handle query with complete execution metadata`() {
            // Given
            val startTime = now.minusSeconds(120)
            val endTime = now.minusSeconds(60)
            val completeQuery =
                QueryExecutionEntity(
                    queryId = "complete_query_001",
                    sql = "SELECT COUNT(*) FROM orders",
                    status = QueryStatus.COMPLETED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = startTime,
                    completedAt = endTime,
                    durationSeconds = 60.0,
                    rowsReturned = 1500000L,
                    bytesScanned = "1.2 GB",
                    engine = QueryEngine.BIGQUERY,
                    costUsd = 0.006,
                    executionDetails = """{"job_id": "bqjob_123456"}""",
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(completeQuery.status).isEqualTo(QueryStatus.COMPLETED)
            assertThat(completeQuery.rowsReturned).isEqualTo(1500000L)
            assertThat(completeQuery.bytesScanned).isEqualTo("1.2 GB")
            assertThat(completeQuery.costUsd).isEqualTo(0.006)
            assertThat(completeQuery.executionDetails).contains("bqjob_123456")
            assertThat(completeQuery.isTerminal()).isTrue()
            assertThat(completeQuery.isCancellable()).isFalse()
        }

        @Test
        @DisplayName("should handle failed query with error details")
        fun `should handle failed query with error details`() {
            // Given
            val failedQuery =
                QueryExecutionEntity(
                    queryId = "failed_query_001",
                    sql = "SELECT * FROM non_existent_table",
                    status = QueryStatus.FAILED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = now.minusSeconds(60),
                    completedAt = now.minusSeconds(30),
                    engine = QueryEngine.BIGQUERY,
                    errorDetails = """{"code": "TABLE_NOT_FOUND", "message": "Table does not exist"}""",
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(failedQuery.status).isEqualTo(QueryStatus.FAILED)
            assertThat(failedQuery.errorDetails).contains("TABLE_NOT_FOUND")
            assertThat(failedQuery.isTerminal()).isTrue()
            assertThat(failedQuery.isCancellable()).isFalse()
            assertThat(failedQuery.isRunning()).isFalse()
        }

        @Test
        @DisplayName("should handle cancelled query with cancellation metadata")
        fun `should handle cancelled query with cancellation metadata`() {
            // Given
            val cancelledQuery =
                QueryExecutionEntity(
                    queryId = "cancelled_query_001",
                    sql = "SELECT * FROM large_table",
                    status = QueryStatus.CANCELLED,
                    submittedBy = "analyst@example.com",
                    submittedAt = now.minusSeconds(300),
                    startedAt = now.minusSeconds(120),
                    completedAt = now.minusSeconds(60),
                    engine = QueryEngine.TRINO,
                    cancelledBy = "analyst@example.com",
                    cancelledAt = now.minusSeconds(60),
                    cancellationReason = "User requested cancellation",
                    isSystemQuery = false,
                )

            // When & Then
            assertThat(cancelledQuery.status).isEqualTo(QueryStatus.CANCELLED)
            assertThat(cancelledQuery.cancelledBy).isEqualTo("analyst@example.com")
            assertThat(cancelledQuery.cancellationReason).isEqualTo("User requested cancellation")
            assertThat(cancelledQuery.cancelledAt).isNotNull()
            assertThat(cancelledQuery.isTerminal()).isTrue()
            assertThat(cancelledQuery.isCancellable()).isFalse()
        }
    }
}
